package com.primalapp.campaign

import com.primalapp.catalog.TestCatalog
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.model.campaign.SkillBranch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Правила кампании приложения — те же, что у сервера (`web/doc/api.md` §5–8). */
class CampaignRulesTest {

    private val rules = CampaignRules(TestCatalog.catalog)
    private var key = 0
    private val ctx = RuleContext(Expansion.entries.toSet(), now = 1_000_000, actor = Actor(ActorKind.USER, "Алиса")) { "k${key++}" }

    private fun newCampaign(): CampaignState =
        rules.create("Кампания", listOf(HunterClass.DAREON to "Алиса", HunterClass.MIRA to ""), ctx)

    private fun report(state: CampaignState, purpose: BattlePurpose, quest: Int? = null, boss: String? = null,
                       result: BattleResult = BattleResult.VICTORY, id: String = "b1") = BattleReport(
        battleId = id, purpose = purpose, questNumber = quest, bossCode = boss, difficulty = 1, chapter = state.chapter,
        progressSeq = state.progressSeq, result = result, defeatReason = if (result == BattleResult.DEFEAT) DefeatReason.ROUNDS else null,
        roundsPlayed = 5, startedAt = 1, finishedAt = 2
    )

    /** Кампания после пролога: глава 1, задания 1, 2, 36 открыты. */
    private fun afterPrologue(): CampaignState {
        val start = newCampaign()
        val won = rules.submitResult(start, report(start, BattlePurpose.PROLOGUE, id = "prologue"), true, null, emptyList(), ctx)
        return rules.acceptTransition(won, emptyMap(), ctx)
    }

    // region Создание и охотники

    @Test
    fun `новая кампания — пролог, отряд в порядке выбора, стартовые предметы`() {
        // Вызов проверяемого кода
        val state = newCampaign()

        // Проверка
        assertEquals(0, state.chapter)
        assertEquals(listOf("Алиса", "Мира"), state.squad.map { it.playerName })
        assertEquals(listOf("Большой меч", "Основной шлем", "Основной доспех", "Алемор"), state.squad.first().items.map { it.name })
        assertFailsWith<CampaignRuleException> { rules.create("Один", listOf(HunterClass.DAREON to ""), ctx) }
    }

    @Test
    fun `ступень 2 — только после ступени 1, ступень 1 не снимается при открытой 2`() {
        // Подготовка
        val state = newCampaign()

        // Вызов проверяемого кода и проверка
        assertFailsWith<CampaignRuleException> { rules.unlockSkill(state, HunterClass.DAREON, SkillBranch.A, 2) }
        val both = rules.unlockSkill(rules.unlockSkill(state, HunterClass.DAREON, SkillBranch.A, 1), HunterClass.DAREON, SkillBranch.A, 2)
        assertEquals(2, both.hunter(HunterClass.DAREON)!!.skills.size)
        assertFailsWith<CampaignRuleException> { rules.lockSkill(both, HunterClass.DAREON, SkillBranch.A, 1) }
    }

    @Test
    fun `ресурсы — всё или ничего`() {
        // Подготовка
        val state = rules.adjustResources(newCampaign(), HunterClass.DAREON, mapOf(ResourceCode.BONES to 2))

        // Вызов проверяемого кода и проверка
        val error = assertFailsWith<CampaignRuleException> {
            rules.adjustResources(state, HunterClass.DAREON, mapOf(ResourceCode.BONES to -1, ResourceCode.BLOOD to -1))
        }
        assertEquals("Не хватает ресурсов: Кровь.", error.message)
        assertEquals(2, state.hunter(HunterClass.DAREON)!!.quantity(ResourceCode.BONES))
    }

    // endregion

    // region Кузня, лаборатория, обмен

    @Test
    fun `кузня — только открытая стихия и оружие своего класса`() {
        // Подготовка: после пролога открыта кузня огня (трофей Вираксена) и есть 2 огня
        val state = rules.adjustResources(afterPrologue(), HunterClass.DAREON, mapOf(ResourceCode.BLOOD to 1))

        // Вызов проверяемого кода
        val crafted = rules.craft(state, HunterClass.DAREON, "FIRE_01", ctx)

        // Проверка
        assertEquals(listOf(Element.FIRE), rules.openForges(state))
        assertEquals("Язык пламени", crafted.hunter(HunterClass.DAREON)!!.items.last().name)
        assertEquals(Element.FIRE, crafted.hunter(HunterClass.DAREON)!!.items.last().element)
        assertEquals(1, crafted.hunter(HunterClass.DAREON)!!.quantity(ResourceCode.FIRE))
        assertFailsWith<CampaignRuleException> { rules.craft(state, HunterClass.MIRA, "FIRE_01", ctx) }
        assertFailsWith<CampaignRuleException> { rules.craft(state, HunterClass.DAREON, "HORN_01", ctx) }
    }

    @Test
    fun `лаборатория — растение на выбор и «любое»`() {
        // Подготовка
        val state = rules.adjustResources(newCampaign(), HunterClass.MIRA,
            mapOf(ResourceCode.TARMARET to 1, ResourceCode.SELICORNIA to 1))

        // Вызов проверяемого кода
        val brewed = rules.brew(state, HunterClass.MIRA, "LAB_03", listOf(Plant.TARMARET, Plant.SELICORNIA), ctx)

        // Проверка
        assertEquals("Ирден", brewed.hunter(HunterClass.MIRA)!!.items.last().name)
        assertTrue(brewed.hunter(HunterClass.MIRA)!!.resources.isEmpty())
        assertFailsWith<CampaignRuleException> { rules.brew(state, HunterClass.MIRA, "LAB_03", listOf(Plant.TARMARET, Plant.NILLEA), ctx) }
    }

    @Test
    fun `обмен 1 к 1 внутри типа, преобразование и продажа`() {
        // Подготовка
        var state = rules.adjustResources(newCampaign(), HunterClass.DAREON, mapOf(ResourceCode.BLOOD to 1, ResourceCode.FIRE to 1))
        state = rules.adjustResources(state, HunterClass.MIRA, mapOf(ResourceCode.SCALES to 1))

        // Вызов проверяемого кода
        val exchanged = rules.exchange(state, HunterClass.DAREON, HunterClass.MIRA, mapOf(ResourceCode.BLOOD to 1), mapOf(ResourceCode.SCALES to 1))
        val converted = rules.convert(exchanged, HunterClass.DAREON, listOf(ResourceCode.FIRE), ResourceCode.BONES)
        val sword = converted.hunter(HunterClass.DAREON)!!.items.first()
        val sold = rules.sell(converted, HunterClass.DAREON, sword.key, ResourceCode.ZLATIA)

        // Проверка
        assertEquals(1, exchanged.hunter(HunterClass.DAREON)!!.quantity(ResourceCode.SCALES))
        assertEquals(1, exchanged.hunter(HunterClass.MIRA)!!.quantity(ResourceCode.BLOOD))
        assertEquals(1, converted.hunter(HunterClass.DAREON)!!.quantity(ResourceCode.BONES))
        assertEquals(1, sold.hunter(HunterClass.DAREON)!!.quantity(ResourceCode.ZLATIA))
        assertEquals(3, sold.hunter(HunterClass.DAREON)!!.items.size)
        assertFailsWith<CampaignRuleException> {
            rules.exchange(state, HunterClass.DAREON, HunterClass.MIRA, mapOf(ResourceCode.FIRE to 1), mapOf(ResourceCode.SCALES to 1))
        }
    }

    // endregion

    // region Задания и достижения

    @Test
    fun `«Выполнено» вручную открывает только задания, «Отмена» возвращает в открытые`() {
        // Подготовка
        val state = afterPrologue()

        // Вызов проверяемого кода
        val (completed, result) = rules.completeQuest(state, 1, ctx)
        val reopened = rules.reopenQuest(completed, 1)

        // Проверка
        assertEquals(listOf(4), result.opened)
        assertEquals("Добавлено задание 4.", result.rules.single().result)
        assertEquals(QuestStatus.COMPLETED, completed.quest(1)!!.status)
        assertEquals(state.hunters, completed.hunters, "Ресурсы при «Выполнено» не начисляются")
        assertEquals(QuestStatus.OPEN, reopened.quest(1)!!.status)
        assertEquals(QuestStatus.OPEN, reopened.quest(4)!!.status, "Открытое задание остаётся")
    }

    @Test
    fun `редактор не трогает выполненные, достижение приводится к каталогу и не дублируется`() {
        // Подготовка
        val (completed, _) = rules.completeQuest(afterPrologue(), 1, ctx)

        // Вызов проверяемого кода
        val edited = rules.setOpenQuests(completed, setOf(2, 10))
        val withAchievement = rules.addAchievement(rules.addAchievement(edited, "голос  волтьяра", "a1"), "Голос Волтьяра", "a2")

        // Проверка
        assertEquals(listOf(1 to QuestStatus.COMPLETED, 2 to QuestStatus.OPEN, 10 to QuestStatus.OPEN),
            edited.quests.map { it.number to it.status })
        assertEquals(listOf("GOLOS_VOLTYARA" to "Голос Волтьяра"), withAchievement.achievements.map { it.code to it.name })
    }

    // endregion

    // region Итог боя и переход главы

    @Test
    fun `победа в задании 1 — трофей, стихии босса, ресурсы, задание 4, глава ждёт перехода`() {
        // Подготовка
        val state = afterPrologue()
        val quest1 = report(state, BattlePurpose.QUEST, quest = 1, boss = "TORAMAT")

        // Вызов проверяемого кода
        val preview = rules.previewResult(state, quest1, ctx)
        val after = rules.submitResult(state, quest1, true, null, emptyList(), ctx)

        // Проверка
        assertEquals("TORAMAT", preview.rewards.trophyBoss)
        assertEquals(ResourceCode.HORN, preview.rewards.perHunter.keys.first(), "Стихии босса — первыми")
        assertEquals(2, preview.rewards.perHunter[ResourceCode.HORN])
        assertEquals(listOf(4), preview.rewards.openQuests)
        assertEquals(NextStep.CHAPTER_TRANSITION, preview.next)
        assertTrue(preview.dismissConsequences.contains("Не будет добавлено задание 4."))
        assertEquals(CampaignStatus.CHAPTER_TRANSITION, after.status)
        assertEquals(state.progressSeq + 1, after.progressSeq)
        assertEquals(QuestStatus.COMPLETED, after.quest(1)!!.status)
        assertEquals(BattleStatus.APPLIED, after.battle("b1")!!.status)
        assertEquals("TORAMAT", after.trophies.last().bossCode)
    }

    @Test
    fun `первая принятая победа закрывает главу — результат другого боя не принимается`() {
        // Подготовка: два боя начаты при одном progressSeq, первый принят
        val state = afterPrologue()
        val first = report(state, BattlePurpose.QUEST, quest = 1, boss = "TORAMAT", id = "first")
        val second = report(state, BattlePurpose.QUEST, quest = 2, boss = "OZEV", id = "second")
        val closed = rules.submitResult(state, first, true, null, emptyList(), ctx)

        // Вызов проверяемого кода и проверка
        val error = assertFailsWith<CampaignChangedException> { rules.previewResult(closed, second, ctx) }
        assertTrue(error.reasons.first().startsWith("Глава 1 уже завершена: принята победа в бою с боссом «Торамат»"))
        val dismissed = rules.submitResult(closed, second, false, null, emptyList(), ctx)
        assertEquals(BattleStatus.DISMISSED, dismissed.battle("second")!!.status)
    }

    @Test
    fun `карты наград задания — выбранному охотнику`() {
        // Подготовка: задание 25 выдаёт карту награды 17
        val state = rules.setOpenQuests(afterPrologue(), setOf(25))
        val quest25 = report(state, BattlePurpose.QUEST, quest = 25, boss = "KHARJA")

        // Вызов проверяемого кода
        val after = rules.submitResult(state, quest25, true, null, listOf(HunterClass.MIRA), ctx)

        // Проверка
        val card = after.hunter(HunterClass.MIRA)!!.items.last()
        assertEquals(ItemKind.REWARD, card.kind)
        assertEquals("Карта награды №17", card.name)
        assertNull(card.level)
    }

    @Test
    fun `поражение записывается в историю без наград`() {
        // Подготовка
        val state = afterPrologue()
        val defeat = report(state, BattlePurpose.QUEST, quest = 1, boss = "TORAMAT", result = BattleResult.DEFEAT)

        // Вызов проверяемого кода
        val after = rules.submitResult(state, defeat, true, null, emptyList(), ctx)

        // Проверка
        assertEquals(state.copy(battles = after.battles), after)
        assertEquals(DefeatReason.ROUNDS, after.battle("b1")!!.defeatReason)
    }

    @Test
    fun `переход в главу 7 — решение главы и последствия истекающих заданий`() {
        // Подготовка: глава 6 ждёт перехода, открыты задания 7 (последствие — задание 26) и 11
        var state = afterPrologue().copy(chapter = 6, status = CampaignStatus.CHAPTER_TRANSITION)
        state = rules.setOpenQuests(state, setOf(7, 11))

        // Вызов проверяемого кода
        val undecided = rules.previewTransition(state, emptyMap(), ctx)
        val decided = rules.previewTransition(state, mapOf("TRAIN_WITH_VOLTYAR" to "YES"), ctx)
        val accepted = rules.acceptTransition(state, mapOf("TRAIN_WITH_VOLTYAR" to "YES"), ctx)

        // Проверка
        assertTrue(!undecided.decisionsComplete)
        assertFailsWith<CampaignRuleException> { rules.acceptTransition(state, emptyMap(), ctx) }
        assertEquals(listOf("GOLOS_VOLTYARA"), decided.achievements.map { it.code })
        val quest7 = decided.expireQuests.single { it.number == 7 }
        assertTrue(quest7.wasOpen)
        assertEquals(catalogConsequences(7), quest7.consequences)
        assertTrue(decided.rejectConsequences.first().startsWith("Глава останется 6."))
        assertEquals(7, accepted.chapter)
        assertEquals(CampaignStatus.ACTIVE, accepted.status)
        assertEquals(QuestStatus.EXPIRED, accepted.quest(7)!!.status)
        assertEquals(AchievementSource.DECISION, accepted.achievements.single { it.code == "GOLOS_VOLTYARA" }.source)
    }

    @Test
    fun `«Отклонить» переход — глава прежняя, кампания снова активна`() {
        // Подготовка
        val state = afterPrologue().copy(status = CampaignStatus.CHAPTER_TRANSITION)

        // Вызов проверяемого кода
        val rejected = rules.rejectTransition(state)

        // Проверка
        assertEquals(state.chapter, rejected.chapter)
        assertEquals(CampaignStatus.ACTIVE, rejected.status)
    }

    // endregion

    private fun catalogConsequences(number: Int): List<String> {
        val catalog = TestCatalog.catalog
        return catalog.quest(number)!!.expired.filterIsInstance<com.primalapp.catalog.Effect.OpenQuest>()
            .map { "добавить задание ${it.quest}" }
    }
}
