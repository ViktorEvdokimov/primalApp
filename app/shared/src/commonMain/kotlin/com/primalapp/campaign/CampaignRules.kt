package com.primalapp.campaign

import com.primalapp.catalog.Catalog
import com.primalapp.catalog.CampaignFacts
import com.primalapp.catalog.ChapterDef
import com.primalapp.catalog.Effect
import com.primalapp.catalog.EffectDescriber.Context
import com.primalapp.catalog.EffectPlanner
import com.primalapp.catalog.Plan
import com.primalapp.catalog.RuleExplanation
import com.primalapp.domain.normalizeAchievementName
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.ForgeSlot
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.model.campaign.ResourceType
import com.primalapp.model.campaign.SkillBranch

/**
 * Правила кампании — перенос сервисов сервера (`web/backend`: CampaignService, HunterService, ForgeService,
 * LabService, ExchangeService, InventoryService, QuestService, AchievementService, BattleResultService,
 * ChapterTransitionService). Чистые функции над [CampaignState]: приложение работает по ним и без сети, а сервер
 * получает готовое состояние. Недопустимое действие — [CampaignRuleException] с текстом для игрока.
 */
class CampaignRules(private val catalog: Catalog) {

    // region Создание и правка кампании

    /** Новая кампания: глава 0 «Пролог», отряд в порядке выбора, стартовые предметы по правилам. */
    fun create(name: String, squad: List<Pair<HunterClass, String>>, ctx: RuleContext): CampaignState {
        val title = name.trim()
        require(title.isNotEmpty(), "Введите название кампании.")
        require(title.length <= 100, "Название — не длиннее 100 символов.")
        require(squad.size in MIN_HUNTERS..MAX_HUNTERS, "В отряде от $MIN_HUNTERS до $MAX_HUNTERS охотников.")
        require(squad.map { it.first }.toSet().size == squad.size, "Классы охотников не должны повторяться.")
        val hunters = squad.mapIndexed { index, (hunterClass, playerName) ->
            HunterState(
                hunterClass = hunterClass,
                playerName = playerName.trim().ifEmpty { hunterClass.displayName }.take(60),
                position = index + 1,
                items = startingKit(hunterClass, ctx)
            )
        }
        return CampaignState(name = title, hunters = hunters)
    }

    /** Стартовый набор охотника: основное оружие класса, шлем, доспех (1-й уровень) и зелье «Алемор». */
    fun startingKit(hunterClass: HunterClass, ctx: RuleContext): List<InventoryItem> = listOf(
        InventoryItem(ctx.newKey(), kind = ItemKind.EQUIPMENT, name = ForgeSlot.weaponOf(hunterClass).displayName, level = 1),
        InventoryItem(ctx.newKey(), kind = ItemKind.EQUIPMENT, name = BASIC_HELMET, level = 1),
        InventoryItem(ctx.newKey(), kind = ItemKind.EQUIPMENT, name = BASIC_ARMOR, level = 1),
        InventoryItem(ctx.newKey(), kind = ItemKind.POTION, name = STARTING_POTION, level = 1, source = STARTING_POTION_SOURCE)
    )

    fun rename(state: CampaignState, name: String): CampaignState {
        val title = name.trim()
        require(title.isNotEmpty() && title.length <= 100, "Название — от 1 до 100 символов.")
        return state.copy(name = title)
    }

    fun setNotes(state: CampaignState, notes: String): CampaignState {
        require(notes.length <= MAX_NOTES, "Заметки — не длиннее 20 000 символов.")
        return state.copy(notes = notes)
    }

    /** Ручная правка главы (исправление ошибки): бои, начатые раньше, устаревают. */
    fun changeChapter(state: CampaignState, chapter: Int): CampaignState {
        require(chapter in 0..Catalog.FINAL_CHAPTER, "Глава — от 0 до 11.")
        if (chapter == state.chapter) return state
        require(state.status != CampaignStatus.CHAPTER_TRANSITION, "Сначала завершите переход главы.")
        return state.copy(chapter = chapter, progressSeq = state.progressSeq + 1)
    }

    // endregion

    // region Охотники: имя, навыки, ресурсы

    fun renameHunter(state: CampaignState, hunterClass: HunterClass, playerName: String): CampaignState =
        updateHunter(state, hunterClass) { it.copy(playerName = playerName.trim().ifEmpty { hunterClass.displayName }.take(60)) }

    /** Открыть ступень: ступень 2 — только после ступени 1; открытую повторно не открыть. */
    fun unlockSkill(state: CampaignState, hunterClass: HunterClass, branch: SkillBranch, tier: Int): CampaignState =
        updateHunter(state, hunterClass) { hunter ->
            val unlocked = hunter.skills.toSet()
            require(canUnlock(unlocked, branch, tier), if (SkillStep(branch, tier) in unlocked) {
                "Эта ступень уже открыта."
            } else {
                "Ступень 2 открывается только после ступени 1 той же ветви."
            })
            hunter.copy(skills = sortedSkills(unlocked + SkillStep(branch, tier)))
        }

    /** Снять ступень (исправление ошибки): ступень 1 не снимается, пока открыта ступень 2. */
    fun lockSkill(state: CampaignState, hunterClass: HunterClass, branch: SkillBranch, tier: Int): CampaignState =
        updateHunter(state, hunterClass) { hunter ->
            val unlocked = hunter.skills.toSet()
            require(canLock(unlocked, branch, tier), if (SkillStep(branch, tier) in unlocked) {
                "Сначала снимите ступень 2 этой ветви."
            } else {
                "Эта ступень не открыта."
            })
            hunter.copy(skills = sortedSkills(unlocked - SkillStep(branch, tier)))
        }

    /** Изменение ресурсов «всё или ничего»: если хоть один ушёл бы ниже нуля, не меняется ничего. */
    fun adjustResources(state: CampaignState, hunterClass: HunterClass, changes: Map<ResourceCode, Int>): CampaignState =
        updateHunter(state, hunterClass) { hunter -> hunter.copy(resources = adjusted(hunter.resources, changes)) }

    private fun adjusted(current: Map<ResourceCode, Int>, changes: Map<ResourceCode, Int>): Map<ResourceCode, Int> {
        val missing = changes.filter { (code, delta) -> (current[code] ?: 0) + delta < 0 }.keys
        if (missing.isNotEmpty()) {
            throw CampaignRuleException("Не хватает ресурсов: ${missing.joinToString(", ") { it.displayName }}.")
        }
        val result = current.toMutableMap()
        changes.forEach { (code, delta) -> result[code] = (result[code] ?: 0) + delta }
        return ResourceCode.entries.mapNotNull { code -> result[code]?.takeIf { it > 0 }?.let { code to it } }.toMap()
    }

    // endregion

    // region Кузня, лаборатория, инвентарь, обмен

    /** Кузни открытых стихий — стихии побеждённых боссов (трофеи) в порядке справочника. */
    fun openForges(state: CampaignState): List<Element> {
        val elements = state.trophies.mapNotNull { catalog.boss(it.bossCode)?.element }.toSet()
        return Element.entries.filter { it in elements }
    }

    /** Цена предмета кузни на текущем уровне: 1 стихия кузни и материи планшета. */
    fun forgeCost(state: CampaignState, itemCode: String): Map<ResourceCode, Int> {
        val item = catalog.forgeItem(itemCode) ?: throw CampaignRuleException("Снаряжения $itemCode нет в кузне.")
        val cost = linkedMapOf(ResourceCode.of(item.element) to 1)
        item.cost(state.forgeLevel).forEach { (material, quantity) ->
            val code = ResourceCode.of(material)
            cost[code] = (cost[code] ?: 0) + quantity
        }
        return cost
    }

    /** Создать снаряжение: кузня стихии открыта, оружие — только своего класса; карта — в инвентарь. */
    fun craft(state: CampaignState, hunterClass: HunterClass, itemCode: String, ctx: RuleContext): CampaignState {
        val item = catalog.forgeItem(itemCode) ?: throw CampaignRuleException("Снаряжения $itemCode нет в кузне.")
        require(item.element in openForges(state),
            "Кузня стихии «${item.element.displayName}» ещё не открыта: она откроется после победы над боссом этой стихии.")
        require(item.slot.availableTo(hunterClass),
            "«${item.name}» — оружие класса ${item.slot.hunterClass?.displayName}: его может создать только этот охотник.")
        val cost = forgeCost(state, itemCode).mapValues { -it.value }
        return updateHunter(state, hunterClass) { hunter ->
            hunter.copy(
                resources = adjusted(hunter.resources, cost),
                items = hunter.items + InventoryItem(ctx.newKey(), kind = ItemKind.EQUIPMENT, name = item.name,
                    level = state.forgeLevel, element = item.element, source = item.code)
            )
        }
    }

    /** Приготовить зелье: [plants] — растения по порядку единиц цены; уровень карты — уровень лаборатории. */
    fun brew(state: CampaignState, hunterClass: HunterClass, potionCode: String, plants: List<Plant>, ctx: RuleContext): CampaignState {
        val potion = catalog.labPotion(potionCode) ?: throw CampaignRuleException("Зелья $potionCode нет в лаборатории.")
        require(plants.size == potion.units.size, "Для «${potion.name}» нужно ${potion.units.size} растения.")
        val cost = mutableMapOf<ResourceCode, Int>()
        plants.forEachIndexed { index, plant ->
            require(potion.accepts(index, plant), "«${plant.displayName}» не подходит для «${potion.name}».")
            val code = ResourceCode.of(plant)
            cost[code] = (cost[code] ?: 0) - 1
        }
        return updateHunter(state, hunterClass) { hunter ->
            hunter.copy(
                resources = adjusted(hunter.resources, cost),
                items = hunter.items + InventoryItem(ctx.newKey(), kind = ItemKind.POTION, name = potion.name,
                    level = state.labLevel, source = potion.code)
            )
        }
    }

    /** Предмет вручную — без оплаты, чтобы список совпадал с картами на столе. */
    fun addItem(state: CampaignState, hunterClass: HunterClass, kind: ItemKind, name: String, level: Int?, key: String): CampaignState {
        val title = itemName(name)
        return updateHunter(state, hunterClass) { hunter ->
            hunter.copy(items = hunter.items + InventoryItem(key, kind = kind, name = title, level = itemLevel(kind, level)))
        }
    }

    fun editItem(state: CampaignState, hunterClass: HunterClass, itemKey: String, name: String, level: Int?): CampaignState {
        val title = itemName(name)
        return updateHunter(state, hunterClass) { hunter ->
            val item = hunter.items.firstOrNull { it.key == itemKey } ?: throw CampaignRuleException("Предмет не найден.")
            hunter.copy(items = hunter.items.map { if (it.key == itemKey) it.copy(name = title, level = itemLevel(item.kind, level)) else it })
        }
    }

    fun removeItem(state: CampaignState, hunterClass: HunterClass, itemKey: String): CampaignState =
        updateHunter(state, hunterClass) { hunter ->
            require(hunter.items.any { it.key == itemKey }, "Предмет не найден.")
            hunter.copy(items = hunter.items.filterNot { it.key == itemKey })
        }

    /** Обмен между охотниками 1 к 1 внутри типа: стихии на стихии, материи на материи, растения на растения. */
    fun exchange(
        state: CampaignState,
        from: HunterClass,
        to: HunterClass,
        give: Map<ResourceCode, Int>,
        receive: Map<ResourceCode, Int>
    ): CampaignState {
        require(from != to, "Обмен — между двумя разными охотниками.")
        val given = byType(give)
        val received = byType(receive)
        require(given.isNotEmpty() || received.isNotEmpty(), "Выберите, чем обменяться.")
        require(given == received, "Обмен — 1 к 1 внутри типа: стихию на стихию, материю на материю, растение на растение.")
        val fromChanges = mutableMapOf<ResourceCode, Int>()
        val toChanges = mutableMapOf<ResourceCode, Int>()
        give.forEach { (code, quantity) ->
            fromChanges[code] = (fromChanges[code] ?: 0) - quantity
            toChanges[code] = (toChanges[code] ?: 0) + quantity
        }
        receive.forEach { (code, quantity) ->
            toChanges[code] = (toChanges[code] ?: 0) - quantity
            fromChanges[code] = (fromChanges[code] ?: 0) + quantity
        }
        val afterFrom = adjustResources(state, from, fromChanges.filterValues { it != 0 })
        return adjustResources(afterFrom, to, toChanges.filterValues { it != 0 })
    }

    /** Преобразование: 1 стихия или 2 материи вместо 1 материи. */
    fun convert(state: CampaignState, hunterClass: HunterClass, spend: List<ResourceCode>, gain: ResourceCode): CampaignState {
        require(gain.type == ResourceType.MATERIAL, "Преобразование даёт материю.")
        val element = spend.size == 1 && spend.first().type == ResourceType.ELEMENT
        val materials = spend.size == 2 && spend.all { it.type == ResourceType.MATERIAL }
        require(element || materials, "Преобразование: 1 стихия или 2 материи вместо 1 материи.")
        val changes = mutableMapOf<ResourceCode, Int>()
        spend.forEach { changes[it] = (changes[it] ?: 0) - 1 }
        changes[gain] = (changes[gain] ?: 0) + 1
        return adjustResources(state, hunterClass, changes.filterValues { it != 0 })
    }

    /** Продажа: карта снаряжения или награды сбрасывается вместо 1 материи или 1 стихии кузни этой карты. */
    fun sell(state: CampaignState, hunterClass: HunterClass, itemKey: String, gain: ResourceCode): CampaignState =
        updateHunter(state, hunterClass) { hunter ->
            val item = hunter.items.firstOrNull { it.key == itemKey } ?: throw CampaignRuleException("Предмет не найден.")
            require(item.kind != ItemKind.POTION, "Зелье не сбрасывается вместо ресурса — только карта из колоды снаряжения.")
            val material = gain.type == ResourceType.MATERIAL
            val ownElement = item.element != null && gain.name == item.element.name
            require(material || ownElement, if (item.element == null) {
                "За «${item.name}» можно получить только материю."
            } else {
                "За «${item.name}» можно получить материю или стихию «${item.element.displayName}»."
            })
            hunter.copy(
                items = hunter.items.filterNot { it.key == itemKey },
                resources = adjusted(hunter.resources, mapOf(gain to 1))
            )
        }

    // endregion

    // region Задания и достижения

    /** Снимок кампании для планировщика эффектов. */
    fun facts(state: CampaignState, ctx: RuleContext): CampaignFacts = CampaignFacts(
        achievements = state.achievements.mapNotNull { it.code }.toSet(),
        chapter = state.chapter,
        availableQuests = state.quests.filter { it.status != QuestStatus.EXPIRED }.map { it.number }.toSet(),
        expansions = ctx.expansions
    )

    /**
     * «Выполнено» вручную: задание выполнено; из наград победы открываются только задания (безусловные и по
     * условиям), материи, растения и достижения не начисляются.
     */
    fun completeQuest(state: CampaignState, number: Int, ctx: RuleContext): Pair<CampaignState, CompletedQuest> {
        require(state.quest(number)?.status == QuestStatus.OPEN, "Задание $number не открыто.")
        val victory = catalog.quest(number)?.victory.orEmpty()
        val plan = catalog.planner.plan(victory, facts(state, ctx), Context.QUEST)
        val closed = state.copy(quests = state.quests.map {
            if (it.number == number) it.copy(status = QuestStatus.COMPLETED, closedInChapter = state.chapter) else it
        })
        val applied = applyPlan(closed, Plan(plan.actions.filterIsInstance<Effect.OpenQuest>(), emptyList()), AchievementSource.QUEST, ctx)
        val opened = plan.openedQuests.filter { opened -> state.quest(opened).let { it == null || it.status == QuestStatus.EXPIRED } }
        return applied to CompletedQuest(number, opened.distinct(), plan.explanations.filter { it.kind == com.primalapp.catalog.RuleKind.QUEST })
    }

    /** «Отмена» у выполненного: снова открыто; открытые им задания и награды не меняются. */
    fun reopenQuest(state: CampaignState, number: Int): CampaignState {
        require(state.quest(number)?.status == QuestStatus.COMPLETED, "Отменить можно только выполненное задание.")
        return state.copy(quests = state.quests.map {
            if (it.number == number) it.copy(status = QuestStatus.OPEN, closedInChapter = null) else it
        })
    }

    /**
     * Редактор: итоговый набор открытых заданий. Недостающие добавляются открытыми, открытые вне списка удаляются;
     * выполненные и истёкшие не меняются.
     */
    fun setOpenQuests(state: CampaignState, numbers: Set<Int>): CampaignState {
        val unknown = numbers.filter { catalog.quest(it) == null }
        require(unknown.isEmpty(), "Нет таких заданий: ${unknown.sorted()}.")
        val kept = state.quests.filter { it.status != QuestStatus.OPEN || it.number in numbers }
        val present = kept.map { it.number }.toSet()
        val added = numbers.filter { it !in present }.sorted().map { QuestState(it, QuestStatus.OPEN, state.chapter) }
        return state.copy(quests = (kept + added).sortedBy { it.number })
    }

    /**
     * Достижение вручную: название нормализуется и сопоставляется с каталогом; равнозначное достижение (то же
     * название в другом написании или тот же код) заменяется новым.
     */
    fun addAchievement(state: CampaignState, rawName: String, key: String): CampaignState {
        val typed = rawName.trim().replace(Regex("\\s+"), " ")
        require(typed.isNotEmpty() && typed.length <= 100, "Название достижения — от 1 до 100 символов.")
        val known = catalog.achievementByName(typed)
        val name = known?.name ?: typed
        val normalized = normalizeAchievementName(name)
        val kept = state.achievements.filterNot {
            normalizeAchievementName(it.name) == normalized || (known != null && it.code == known.code)
        }
        return state.copy(achievements = kept + AchievementState(key, code = known?.code, name = name,
            source = AchievementSource.MANUAL, grantedInChapter = state.chapter))
    }

    fun deleteAchievement(state: CampaignState, key: String): CampaignState {
        require(state.achievements.any { it.key == key }, "Достижение не найдено.")
        return state.copy(achievements = state.achievements.filterNot { it.key == key })
    }

    // endregion

    // region Бой кампании: подготовка и старт

    /** Цель боя: [bossCode] — известный заранее босс; [forced] — выбрать другого нельзя. */
    data class BattleTarget(val purpose: BattlePurpose, val bossCode: String?, val forced: Boolean, val difficulty: Int)

    /**
     * Цель боя по состоянию кампании: финальный бой главы 11 — только с финальным боссом; в прологе — Вираксен;
     * задание должно быть открыто. Уровень враждебности — по главе (в прологе 0); если у босса такого нет, а
     * доступен один (Пробуждённый — 3), берётся он.
     */
    fun battleTarget(state: CampaignState, questNumber: Int?): BattleTarget {
        require(state.status != CampaignStatus.CHAPTER_TRANSITION,
            "${chapterTitle(state.chapter)} завершена: сначала завершите переход главы.")
        require(state.status != CampaignStatus.COMPLETED, "Кампания пройдена: новых боёв в ней нет.")
        val target = when {
            state.finalBossCode != null -> {
                require(questNumber == null, "Доступен только финальный бой: ${catalog.bossName(state.finalBossCode)}.")
                Triple(BattlePurpose.FINAL, state.finalBossCode, true)
            }
            state.chapter == 0 -> {
                require(questNumber == null, "В прологе заданий нет: бой с Вираксеном.")
                Triple(BattlePurpose.PROLOGUE, Catalog.PROLOGUE_BOSS, true)
            }
            questNumber != null -> {
                require(state.quest(questNumber)?.status == QuestStatus.OPEN, "Задание $questNumber не открыто.")
                Triple(BattlePurpose.QUEST, catalog.quest(questNumber)?.bossCode, false)
            }
            else -> Triple(BattlePurpose.FREE, null, false)
        }
        var difficulty = if (target.first == BattlePurpose.PROLOGUE) 0 else Catalog.difficultyForChapter(state.chapter)
        val boss = catalog.boss(target.second)
        if (boss != null && boss.stances(difficulty).isEmpty() && boss.stances.size == 1) {
            difficulty = boss.difficulties.first()
        }
        return BattleTarget(target.first, target.second, target.third, difficulty)
    }

    /** Отметка о начале боя: запись в истории; идущие бои других участников старт не блокируют. */
    fun startBattle(state: CampaignState, record: BattleRecord): CampaignState {
        if (state.battle(record.id) != null) return state
        return state.copy(battles = listOf(record) + state.battles)
    }

    /** «Бросить бой» или снять забытую отметку: меняются только предупреждения. */
    fun abandonBattle(state: CampaignState, battleId: String): CampaignState = state.copy(battles = state.battles.map {
        if (it.id == battleId && it.status == BattleStatus.IN_PROGRESS) it.copy(status = BattleStatus.ABANDONED) else it
    })

    /** Идущие бои для предупреждений: начаты меньше 24 часов назад и при текущем progressSeq. */
    fun activeBattles(state: CampaignState, now: Long, except: String? = null): List<BattleRecord> = state.battles.filter {
        it.status == BattleStatus.IN_PROGRESS && it.id != except && it.progressSeq == state.progressSeq &&
            it.startedAt >= now - BATTLE_WARNING_MILLIS
    }

    // endregion

    // region Итог боя

    private data class Computation(
        val questNumber: Int?,
        val trophyBoss: String?,
        val completedQuest: Int?,
        val plan: Plan,
        val ending: Ending,
        val facts: CampaignFacts
    )

    private enum class Ending { NONE, CHAPTER, CAMPAIGN }

    /** Превью наград: ничего не меняет. Несогласованный с кампанией результат — [CampaignChangedException]. */
    fun previewResult(state: CampaignState, report: BattleReport, ctx: RuleContext): ResultPreview {
        if (state.battle(report.battleId)?.isFinished == true) throw CampaignChangedException(listOf("Итог этого боя уже сохранён."))
        requireConsistent(state, report)
        val computation = compute(state, report, null, ctx)
        val rewards = rewards(computation)
        return ResultPreview(
            result = report.result,
            purpose = report.purpose,
            questNumber = computation.questNumber,
            questName = computation.questNumber?.let { catalog.quest(it)?.name },
            rewards = rewards,
            rules = computation.plan.explanations,
            next = next(report),
            otherActiveBattles = activeBattles(state, ctx.now, except = report.battleId),
            dismissConsequences = dismissConsequences(computation, rewards, report.chapter)
        )
    }

    /**
     * Итог боя. «Отклонить» принимается всегда и только записывает бой в историю; «Принять» проверяет
     * согласованность и применяет награды: трофей, ресурсы, задания, достижения, карты наград, закрытие главы.
     */
    fun submitResult(
        state: CampaignState,
        report: BattleReport,
        accept: Boolean,
        overrides: ResultOverrides?,
        rewardCardHolders: RewardCardHolders,
        ctx: RuleContext
    ): CampaignState {
        val existing = state.battle(report.battleId)
        if (existing?.isFinished == true) return state
        val record = (existing ?: battleRecord(report, ctx)).copy(
            purpose = report.purpose,
            questNumber = report.questNumber.takeIf { report.purpose == BattlePurpose.QUEST },
            bossCode = report.bossCode,
            difficulty = report.difficulty,
            chapter = report.chapter,
            progressSeq = report.progressSeq,
            status = if (accept) BattleStatus.APPLIED else BattleStatus.DISMISSED,
            result = report.result,
            defeatReason = if (report.result == BattleResult.DEFEAT) report.defeatReason ?: DefeatReason.ROUNDS else null,
            roundsPlayed = report.roundsPlayed,
            submittedBy = ctx.actor,
            submittedAt = ctx.now,
            finishedAt = report.finishedAt
        )
        val withRecord = state.copy(battles = if (existing == null) listOf(record) + state.battles else state.battles.map {
            if (it.id == record.id) record else it
        })
        if (!accept) return withRecord

        requireConsistent(state, report)
        overrides?.let { validate(it) }
        val computation = compute(state, report, overrides, ctx)
        if (report.victory && computation.trophyBoss == null) {
            throw CampaignRuleException("Босс не выбран — укажите его через «Редактировать».")
        }
        var result = withRecord
        computation.trophyBoss?.let { boss ->
            result = result.copy(trophies = result.trophies + TrophyState(boss, state.chapter, report.battleId))
        }
        computation.completedQuest?.let { number ->
            result = result.copy(quests = result.quests.map {
                if (it.number == number && it.status == QuestStatus.OPEN) {
                    it.copy(status = QuestStatus.COMPLETED, closedInChapter = state.chapter)
                } else {
                    it
                }
            })
        }
        result = when (computation.ending) {
            Ending.CHAPTER -> result.copy(status = CampaignStatus.CHAPTER_TRANSITION, progressSeq = result.progressSeq + 1)
            Ending.CAMPAIGN -> result.copy(status = CampaignStatus.COMPLETED, progressSeq = result.progressSeq + 1)
            Ending.NONE -> result
        }
        result = applyPlan(result, computation.plan, AchievementSource.QUEST, ctx)
        // Карты наград — по правилам задания (и при «Редактировать»): это карты из коробки, а не начисления
        val byRules = if (overrides == null) computation else compute(state, report, null, ctx)
        val cards = byRules.plan.actions.filterIsInstance<Effect.RewardCards>().flatMap { it.cards }
        if (cards.isNotEmpty()) result = giveRewardCards(result, cards, rewardCardHolders, ctx)
        return result
    }

    private fun battleRecord(report: BattleReport, ctx: RuleContext) = BattleRecord(
        id = report.battleId,
        purpose = report.purpose,
        questNumber = report.questNumber,
        bossCode = report.bossCode,
        difficulty = report.difficulty,
        chapter = report.chapter,
        progressSeq = report.progressSeq,
        startedBy = ctx.actor,
        startedAt = report.startedAt
    )

    /** Карта `cards[i]` — охотнику `holders[i]`; не указан — первому охотнику отряда. */
    private fun giveRewardCards(state: CampaignState, cards: List<String>, holders: RewardCardHolders, ctx: RuleContext): CampaignState {
        var result = state
        val first = state.squad.firstOrNull()?.hunterClass ?: return state
        cards.forEachIndexed { index, card ->
            val holder = holders.getOrNull(index)?.takeIf { state.hunter(it) != null } ?: first
            result = updateHunter(result, holder) { hunter ->
                hunter.copy(items = hunter.items + InventoryItem(ctx.newKey(), kind = ItemKind.REWARD,
                    name = rewardCardName(card), source = card))
            }
        }
        return result
    }

    /**
     * Согласованность с кампанией: глава не закрыта другой победой и не изменена, кампания не ждёт перехода и не
     * пройдена, задание открыто, в главе 11 — финальный босс.
     */
    fun requireConsistent(state: CampaignState, report: BattleReport) {
        val reasons = mutableListOf<String>()
        if (state.status == CampaignStatus.COMPLETED) {
            reasons += "Кампания уже пройдена."
        } else if (report.progressSeq != state.progressSeq) {
            val closing = state.battles.filter {
                it.progressSeq == report.progressSeq && it.status == BattleStatus.APPLIED && it.result == BattleResult.VICTORY &&
                    it.id != report.battleId
            }.maxByOrNull { it.submittedAt ?: 0 }
            reasons += if (closing != null) {
                val boss = closing.bossCode?.let { " в бою с боссом «${catalog.bossName(it)}»" }.orEmpty()
                val author = closing.submittedBy?.name?.let { " (результат от: $it)" }.orEmpty()
                "${chapterClosed(report.chapter)}: принята победа$boss$author. Результаты боёв, начатых раньше, не принимаются."
            } else {
                "Глава изменилась, пока шёл бой. Результаты боёв, начатых раньше, не принимаются."
            }
        } else if (state.status == CampaignStatus.CHAPTER_TRANSITION) {
            reasons += "${chapterClosed(state.chapter)} и ждёт перехода главы."
        }
        if (report.purpose == BattlePurpose.QUEST && state.quest(report.questNumber ?: -1)?.status != QuestStatus.OPEN) {
            reasons += "Задание ${report.questNumber} больше не открыто: его выполнили или время истекло."
        }
        if (report.purpose == BattlePurpose.FINAL && state.finalBossCode != null && state.finalBossCode != report.bossCode) {
            reasons += "В главе 11 принимается только бой с боссом «${catalog.bossName(state.finalBossCode)}»."
        }
        if (reasons.isNotEmpty()) throw CampaignChangedException(reasons)
    }

    /**
     * Общие правила: победа — трофей и 2 стихии босса каждому охотнику, по заданию — задание выполнено и его награды
     * за победу. Поражение ничего не даёт: последствия невыполненного задания применяются, когда истекает его время.
     */
    private fun compute(state: CampaignState, report: BattleReport, overrides: ResultOverrides?, ctx: RuleContext): Computation {
        val victory = report.victory
        val quest = if (report.purpose == BattlePurpose.QUEST) {
            catalog.quest(report.questNumber ?: -1) ?: throw CampaignRuleException("Нет задания ${report.questNumber}.")
        } else {
            null
        }
        val facts = facts(state, ctx)
        val ending = when {
            !victory -> Ending.NONE
            report.purpose == BattlePurpose.FINAL -> Ending.CAMPAIGN
            else -> Ending.CHAPTER
        }
        val completed = if (victory) quest?.number else null
        if (overrides != null) {
            val actions = mutableListOf<Effect>()
            val given = overrides.perHunter.filterValues { it != 0 }
            if (given.isNotEmpty()) actions += Effect.Resources(given)
            overrides.openQuests.forEach { actions += Effect.OpenQuest(it) }
            overrides.achievements.forEach { actions += Effect.GrantAchievement(it) }
            return Computation(quest?.number, if (victory) overrides.bossCode else null, completed,
                Plan(actions, emptyList()), ending, facts)
        }
        val boss = if (!victory) null else when (report.purpose) {
            BattlePurpose.PROLOGUE -> Catalog.PROLOGUE_BOSS
            BattlePurpose.FINAL -> state.finalBossCode ?: report.bossCode
            BattlePurpose.QUEST -> quest?.bossCode
            BattlePurpose.FREE -> report.bossCode
        }
        val effects = if (quest != null && victory) quest.victory else emptyList()
        val planned = catalog.planner.plan(effects, facts, Context.QUEST)
        val actions = mutableListOf<Effect>()
        catalog.boss(boss)?.element?.let { element -> actions += Effect.Resources(mapOf(ResourceCode.of(element) to 2)) }
        actions += planned.actions
        return Computation(quest?.number, boss, completed, Plan(actions, planned.explanations), ending, facts)
    }

    /** Награды для окна: стихии босса первыми, уже добавленные задания и полученные достижения не повторяются. */
    private fun rewards(computation: Computation): BattleRewards {
        val perHunter = linkedMapOf<ResourceCode, Int>()
        val achievements = mutableListOf<AchievementRef>()
        val openQuests = mutableListOf<Int>()
        val rewardCards = mutableListOf<String>()
        val messages = mutableListOf<String>()
        val facts = computation.facts
        for (action in computation.plan.actions) {
            when (action) {
                is Effect.Resources -> action.items.entries.sortedBy { it.key.ordinal }
                    .forEach { (code, quantity) -> perHunter[code] = (perHunter[code] ?: 0) + quantity }
                is Effect.OpenQuest -> if (action.quest !in facts.availableQuests && action.quest !in openQuests) openQuests += action.quest
                is Effect.GrantAchievement -> if (action.achievement !in facts.achievements &&
                    achievements.none { it.code == action.achievement }
                ) {
                    achievements += AchievementRef(action.achievement, catalog.achievementName(action.achievement))
                }
                is Effect.RewardCards -> rewardCards += action.cards
                is Effect.Message -> messages += action.text
                is Effect.HunterKitUpgrade -> messages += KIT_UPGRADE
                else -> Unit
            }
        }
        if (computation.ending == Ending.CAMPAIGN && computation.trophyBoss != null) {
            messages += "Кампания пройдена! ${catalog.bossName(computation.trophyBoss)} повержен."
        }
        return BattleRewards(computation.trophyBoss, perHunter, achievements, openQuests, rewardCards, messages)
    }

    private fun validate(overrides: ResultOverrides) {
        require(overrides.bossCode == null || catalog.boss(overrides.bossCode) != null, "Неизвестный босс: ${overrides.bossCode}.")
        overrides.openQuests.firstOrNull { catalog.quest(it) == null }?.let { throw CampaignRuleException("Нет задания $it.") }
        overrides.achievements.firstOrNull { catalog.achievement(it) == null }?.let { throw CampaignRuleException("Нет достижения $it.") }
    }

    private fun next(report: BattleReport): NextStep = when {
        !report.victory -> NextStep.CAMPAIGN_SHEET
        report.purpose == BattlePurpose.FINAL -> NextStep.CAMPAIGN_COMPLETED
        else -> NextStep.CHAPTER_TRANSITION
    }

    /** Текст окна «Отклонить» итог боя: что не произойдёт. */
    private fun dismissConsequences(computation: Computation, rewards: BattleRewards, chapter: Int): List<String> {
        val lines = mutableListOf<String>()
        computation.completedQuest?.let { lines += "Задание $it «${catalog.quest(it)?.name.orEmpty()}» не будет отмечено выполненным." }
        rewards.trophyBoss?.let { lines += "Трофей «${catalog.bossName(it)}» не будет получен." }
        resourcesLine(rewards.perHunter)?.let { lines += it }
        openQuestsLine(rewards.openQuests)?.let { lines += it }
        achievementsLine(rewards.achievements, "")?.let { lines += it }
        when (computation.ending) {
            Ending.CHAPTER -> lines += if (chapter == 0) {
                "Переход в главу 1 не состоится, останется пролог."
            } else {
                "Переход в главу ${chapter + 1} не состоится, глава останется $chapter."
            }
            Ending.CAMPAIGN -> lines += "Кампания не будет завершена."
            Ending.NONE -> Unit
        }
        lines += if (lines.isEmpty()) "Бой будет записан в историю, кампания не изменится." else "Награды можно внести вручную на листе кампании."
        return lines
    }

    // endregion

    // region Переход главы

    private data class TransitionComputation(
        val fromChapter: Int,
        val chapter: ChapterDef,
        val selected: Map<String, String>,
        val complete: Boolean,
        val decisionEffects: List<Effect>,
        val decisionPlan: Plan,
        val chapterPlan: Plan,
        val facts: CampaignFacts,
        val openNow: Set<Int>,
        val expiries: Map<Int, Plan>
    ) {
        val expiryPlan: Plan
            get() = Plan(expiries.values.flatMap { it.actions }, expiries.values.flatMap { it.explanations })
    }

    /** Превью перехода главы с учётом ответов на решения; перехода нет — [CampaignChangedException]. */
    fun previewTransition(state: CampaignState, decisions: Map<String, String>, ctx: RuleContext): TransitionPreview {
        val computation = computeTransition(state, decisions, ctx)
        val facts = computation.facts
        val perHunter = linkedMapOf<ResourceCode, Int>()
        val openQuests = mutableListOf<Int>()
        val expireQuests = mutableListOf<ExpiringQuest>()
        val decisionAchievements = mutableListOf<AchievementRef>()
        val chapterAchievements = mutableListOf<AchievementRef>()
        val rewardCards = mutableListOf<String>()
        val messages = mutableListOf<String>()
        var forge = false
        var lab = false
        var kit = false
        var finalBattle: String? = null
        val all = computation.decisionPlan.actions + computation.chapterPlan.actions
        val decisionCount = computation.decisionPlan.actions.size
        all.forEachIndexed { index, action ->
            when (action) {
                is Effect.Resources -> action.items.entries.sortedBy { it.key.ordinal }
                    .forEach { (code, quantity) -> perHunter[code] = (perHunter[code] ?: 0) + quantity }
                is Effect.OpenQuest -> if (action.quest !in facts.availableQuests && action.quest !in openQuests) openQuests += action.quest
                is Effect.ExpireQuests -> action.quests.forEach { expireQuests += expiring(computation, it, it in computation.openNow) }
                is Effect.ExpireAllQuests -> computation.openNow.sorted().forEach { expireQuests += expiring(computation, it, true) }
                is Effect.GrantAchievement -> if (action.achievement !in facts.achievements) {
                    val target = if (index < decisionCount) decisionAchievements else chapterAchievements
                    target += AchievementRef(action.achievement, catalog.achievementName(action.achievement))
                }
                is Effect.ForgeLevelUp -> forge = true
                is Effect.LabLevelUp -> lab = true
                is Effect.HunterKitUpgrade -> kit = true
                is Effect.RewardCards -> rewardCards += action.cards
                is Effect.Message -> messages += action.text
                is Effect.FinalBattle -> finalBattle = action.boss
                is Effect.Conditional -> Unit
            }
        }
        val rules = computation.decisionPlan.explanations + computation.chapterPlan.explanations
        val reject = mutableListOf<String>()
        reject += (if (computation.fromChapter == 0) "Останется пролог." else "Глава останется ${computation.fromChapter}.") +
            " После следующей победы переход в главу ${computation.chapter.chapter} будет предложен снова."
        achievementsLine(decisionAchievements, " (решение главы)")?.let { reject += it }
        resourcesLine(perHunter)?.let { reject += it }
        openQuestsLine(openQuests)?.let { reject += it }
        achievementsLine(chapterAchievements, "")?.let { reject += it }
        val expiringOpen = expireQuests.filter { it.wasOpen }.map { it.number.toString() }
        if (expiringOpen.size == 1) reject += "Не истечёт время задания ${expiringOpen.first()}."
        if (expiringOpen.size > 1) reject += "Не истечёт время заданий ${expiringOpen.joinToString(", ")}."
        if (forge) reject += "Кузня не улучшится."
        if (lab) reject += "Лаборатория не улучшится."
        if (kit) reject += "Не будет улучшения набора охотника."
        if (rewardCards.isNotEmpty()) reject += "Не будут выданы карты наград: ${rewardCards.joinToString(", ")}."
        finalBattle?.let { reject += "Финальный бой с боссом «${catalog.bossName(it)}» не будет назначен." }
        reject += "Главу, задания и достижения можно изменить вручную на листе кампании."

        return TransitionPreview(
            fromChapter = computation.fromChapter,
            toChapter = computation.chapter.chapter,
            decisions = computation.chapter.decisions.map { decision ->
                TransitionDecisionView(decision.code, decision.question, decision.options.map { it.code to it.label },
                    computation.selected[decision.code])
            },
            decisionsComplete = computation.complete,
            perHunter = perHunter,
            openQuests = openQuests,
            expireQuests = expireQuests,
            achievements = decisionAchievements + chapterAchievements,
            forgeLevelUp = forge,
            labLevelUp = lab,
            hunterKitUpgrade = kit,
            rewardCards = rewardCards,
            messages = messages,
            finalBattle = finalBattle,
            rules = rules,
            rejectConsequences = reject
        )
    }

    /**
     * «Принять» переход: новая глава, затем эффекты выбранных вариантов решений, эффекты главы и последствия
     * истекающих заданий — уже в новой главе. Без ответа на решение — ошибка.
     */
    fun acceptTransition(state: CampaignState, decisions: Map<String, String>, ctx: RuleContext): CampaignState {
        val computation = computeTransition(state, decisions, ctx)
        if (!computation.complete) {
            val questions = computation.chapter.decisions.filter { it.code !in computation.selected }.joinToString(" ") { it.question }
            throw CampaignRuleException("Ответьте на решение главы: $questions")
        }
        var result = state.copy(chapter = computation.chapter.chapter, status = CampaignStatus.ACTIVE, progressSeq = state.progressSeq + 1)
        if (computation.decisionPlan.actions.isNotEmpty()) result = applyPlan(result, computation.decisionPlan, AchievementSource.DECISION, ctx)
        result = applyPlan(result, computation.chapterPlan, AchievementSource.CHAPTER, ctx)
        if (computation.expiryPlan.actions.isNotEmpty()) result = applyPlan(result, computation.expiryPlan, AchievementSource.QUEST, ctx)
        return result
    }

    /** «Отклонить»: эффекты не применяются, глава прежняя; после следующей победы переход будет предложен снова. */
    fun rejectTransition(state: CampaignState): CampaignState {
        if (state.status != CampaignStatus.CHAPTER_TRANSITION) {
            throw CampaignChangedException(listOf("Переход главы не ожидается: сначала нужна победа в бою."))
        }
        return state.copy(status = CampaignStatus.ACTIVE)
    }

    private fun computeTransition(state: CampaignState, decisions: Map<String, String>, ctx: RuleContext): TransitionComputation {
        if (state.status != CampaignStatus.CHAPTER_TRANSITION) {
            throw CampaignChangedException(listOf("Переход главы не ожидается: сначала нужна победа в бою."))
        }
        val to = state.chapter + 1
        val def = catalog.chapter(to) ?: throw CampaignChangedException(listOf("Главы $to нет: кампания пройдена."))
        val selected = linkedMapOf<String, String>()
        val decisionEffects = mutableListOf<Effect>()
        decisions.forEach { (code, option) ->
            val decision = def.decisions.firstOrNull { it.code == code } ?: return@forEach
            val chosen = decision.options.firstOrNull { it.code == option } ?: return@forEach
            selected[code] = option
            decisionEffects += chosen.effects
        }
        val complete = def.decisions.all { it.code in selected }
        val facts = facts(state, ctx)
        val openNow = state.openQuests.map { it.number }.toSet()
        val decisionPlan = catalog.planner.plan(decisionEffects, facts, Context.CHAPTER)
        val chapterPlan = catalog.planner.plan(def.effects, facts, Context.CHAPTER)
        return TransitionComputation(state.chapter, def, selected, complete, decisionEffects, decisionPlan, chapterPlan, facts,
            openNow, expiries(decisionPlan, chapterPlan, openNow, facts))
    }

    /**
     * Последствия невыполненных заданий: для каждого открытого задания, у которого истекает время, — его эффекты
     * `expired` по снимку кампании до перехода. Задание, истекающее в этом же переходе, последствием не добавляется.
     */
    private fun expiries(decisionPlan: Plan, chapterPlan: Plan, openNow: Set<Int>, facts: CampaignFacts): Map<Int, Plan> {
        val expiring = linkedSetOf<Int>()
        for (action in decisionPlan.actions + chapterPlan.actions) {
            when (action) {
                is Effect.ExpireQuests -> action.quests.filter { it in openNow }.forEach { expiring += it }
                is Effect.ExpireAllQuests -> openNow.sorted().forEach { expiring += it }
                else -> Unit
            }
        }
        val result = linkedMapOf<Int, Plan>()
        for (number in expiring) {
            val quest = catalog.quest(number) ?: continue
            val plan = catalog.planner.plan(quest.expired, facts, Context.QUEST)
            val kept = plan.actions.filterNot { it is Effect.OpenQuest && it.quest in expiring }
            result[number] = Plan(kept, plan.explanations)
        }
        return result
    }

    private fun expiring(computation: TransitionComputation, number: Int, wasOpen: Boolean): ExpiringQuest {
        val plan = computation.expiries[number]
        val consequences = if (wasOpen && plan != null) {
            plan.actions
                .filterNot { it is Effect.OpenQuest && it.quest in computation.facts.availableQuests }
                .filterNot { it is Effect.GrantAchievement && it.achievement in computation.facts.achievements }
                .map { catalog.describer.action(it) }
        } else {
            emptyList()
        }
        return ExpiringQuest(number, catalog.quest(number)?.name.orEmpty(), wasOpen, consequences)
    }

    // endregion

    // region Выполнение плана

    /**
     * Выполняет план наград: ресурсы каждому охотнику, задания, истечение заданий, достижения, кузня, лаборатория,
     * финальный бой. Повторы безопасны: открыть открытое задание или выдать полученное достижение — ничего не меняет.
     */
    fun applyPlan(state: CampaignState, plan: Plan, source: AchievementSource, ctx: RuleContext): CampaignState {
        var result = state
        for (action in plan.actions) {
            result = when (action) {
                is Effect.Resources -> result.copy(hunters = result.hunters.map { hunter ->
                    val stock = hunter.resources.toMutableMap()
                    action.items.forEach { (code, quantity) -> if (quantity > 0) stock[code] = (stock[code] ?: 0) + quantity }
                    hunter.copy(resources = ResourceCode.entries.mapNotNull { code -> stock[code]?.takeIf { it > 0 }?.let { code to it } }.toMap())
                })
                is Effect.OpenQuest -> openQuest(result, action.quest)
                is Effect.ExpireQuests -> result.copy(quests = result.quests.map {
                    if (it.number in action.quests && it.status == QuestStatus.OPEN) {
                        it.copy(status = QuestStatus.EXPIRED, closedInChapter = result.chapter)
                    } else {
                        it
                    }
                })
                is Effect.ExpireAllQuests -> result.copy(quests = result.quests.map {
                    if (it.status == QuestStatus.OPEN) it.copy(status = QuestStatus.EXPIRED, closedInChapter = result.chapter) else it
                })
                is Effect.GrantAchievement -> grantAchievement(result, action.achievement, source, ctx)
                is Effect.ForgeLevelUp -> result.copy(forgeLevel = minOf(3, result.forgeLevel + 1))
                is Effect.LabLevelUp -> result.copy(labLevel = minOf(3, result.labLevel + 1))
                is Effect.FinalBattle -> result.copy(finalBossCode = action.boss)
                is Effect.HunterKitUpgrade, is Effect.RewardCards, is Effect.Message -> result
                is Effect.Conditional -> result
            }
        }
        return result
    }

    /** Новое задание — открыто; истёкшее — открыто снова; открытое и выполненное не меняются. */
    private fun openQuest(state: CampaignState, number: Int): CampaignState {
        val existing = state.quest(number)
        return when {
            existing == null -> state.copy(quests = (state.quests + QuestState(number, QuestStatus.OPEN, state.chapter)).sortedBy { it.number })
            existing.status == QuestStatus.EXPIRED -> state.copy(quests = state.quests.map {
                if (it.number == number) it.copy(status = QuestStatus.OPEN, closedInChapter = null) else it
            })
            else -> state
        }
    }

    /** Достижение без дублей — ни по коду, ни в другом написании. */
    private fun grantAchievement(state: CampaignState, code: String, source: AchievementSource, ctx: RuleContext): CampaignState {
        val name = catalog.achievementName(code)
        val normalized = normalizeAchievementName(name)
        if (state.achievements.any { it.code == code || normalizeAchievementName(it.name) == normalized }) return state
        return state.copy(achievements = state.achievements + AchievementState(ctx.newKey(), code = code, name = name,
            source = source, grantedInChapter = state.chapter))
    }

    // endregion

    // region Вспомогательное

    private fun updateHunter(state: CampaignState, hunterClass: HunterClass, change: (HunterState) -> HunterState): CampaignState {
        val hunter = state.hunter(hunterClass) ?: throw CampaignRuleException("Охотник не найден.")
        val updated = change(hunter)
        return state.copy(hunters = state.hunters.map { if (it.hunterClass == hunterClass) updated else it })
    }

    private fun byType(resources: Map<ResourceCode, Int>): Map<ResourceType, Int> {
        val counts = mutableMapOf<ResourceType, Int>()
        resources.forEach { (code, quantity) ->
            require(quantity >= 0, "Количество не может быть отрицательным.")
            if (quantity > 0) counts[code.type] = (counts[code.type] ?: 0) + quantity
        }
        return counts
    }

    private fun itemName(name: String): String {
        val title = name.trim()
        require(title.isNotEmpty() && title.length <= 100, "Название предмета — от 1 до 100 символов.")
        return title
    }

    private fun itemLevel(kind: ItemKind, level: Int?): Int? {
        if (kind == ItemKind.REWARD) return null
        val value = level ?: 1
        require(value in 1..3, "Уровень — от 1 до 3.")
        return value
    }

    private fun resourcesLine(perHunter: Map<ResourceCode, Int>): String? =
        if (perHunter.isEmpty()) null
        else "Охотники не получат ресурсы: " + perHunter.entries.joinToString(", ") { "${it.key.displayName} ${it.value}" } + "."

    private fun openQuestsLine(quests: List<Int>): String? = when {
        quests.size == 1 -> "Не будет добавлено задание ${quests.first()}."
        quests.size > 1 -> "Не будут добавлены задания ${quests.joinToString(", ")}."
        else -> null
    }

    private fun achievementsLine(achievements: List<AchievementRef>, note: String): String? {
        val names = achievements.map { "«${it.name}»" }
        return when {
            names.size == 1 -> "Не будет получено достижение ${names.first()}$note."
            names.size > 1 -> "Не будут получены достижения ${names.joinToString(", ")}$note."
            else -> null
        }
    }

    private fun require(condition: Boolean, message: String) {
        if (!condition) throw CampaignRuleException(message)
    }

    companion object {
        const val MIN_HUNTERS = 2
        const val MAX_HUNTERS = 5
        const val MAX_NOTES = 20_000
        const val BASIC_HELMET = "Основной шлем"
        const val BASIC_ARMOR = "Основной доспех"
        const val STARTING_POTION = "Алемор"
        const val STARTING_POTION_SOURCE = "LAB_01"
        const val KIT_UPGRADE = "Каждый охотник улучшает свой набор."
        const val BATTLE_WARNING_MILLIS = 24L * 60 * 60 * 1000

        fun rewardCardName(card: String): String = "Карта награды №$card"

        fun chapterTitle(chapter: Int): String = if (chapter == 0) "Пролог" else "Глава $chapter"

        private fun chapterClosed(chapter: Int): String = if (chapter == 0) "Пролог уже завершён" else "Глава $chapter уже завершена"

        fun canUnlock(unlocked: Set<SkillStep>, branch: SkillBranch, tier: Int): Boolean {
            if (tier !in 1..2 || SkillStep(branch, tier) in unlocked) return false
            return tier == 1 || SkillStep(branch, 1) in unlocked
        }

        fun canLock(unlocked: Set<SkillStep>, branch: SkillBranch, tier: Int): Boolean {
            if (SkillStep(branch, tier) !in unlocked) return false
            return tier != 1 || SkillStep(branch, 2) !in unlocked
        }

        private fun sortedSkills(skills: Set<SkillStep>): List<SkillStep> =
            skills.sortedWith(compareBy({ it.branch.ordinal }, { it.tier }))
    }
}
