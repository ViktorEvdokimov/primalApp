package com.primalapp.viewmodel

import com.primalapp.campaign.BattlePurpose
import com.primalapp.campaign.BattleRecord
import com.primalapp.campaign.BattleResult
import com.primalapp.campaign.BattleStatus
import com.primalapp.campaign.CampaignOp
import com.primalapp.campaign.CampaignRules
import com.primalapp.campaign.CampaignStatus
import com.primalapp.campaign.HunterState
import com.primalapp.campaign.InventoryItem
import com.primalapp.campaign.ItemKind
import com.primalapp.campaign.SkillStep
import com.primalapp.campaign.newLocalKey
import com.primalapp.catalog.RuleExplanation
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.net.ApiException
import com.primalapp.net.OfflineException
import com.primalapp.net.ShareLinkDto
import com.primalapp.sync.CampaignAccess
import com.primalapp.sync.StoredCampaign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class SheetTab(val title: String) {
    HUNTERS("Охотники"),
    QUESTS("Задания"),
    ACHIEVEMENTS("Достижения"),
    TROPHIES("Трофеи"),
    HISTORY("История"),
    NOTES("Заметки")
}

/** Задание в листе: номер, название, босс (стихия), дополнение. */
data class QuestRow(
    val number: Int,
    val name: String,
    val bossName: String,
    val element: Element?,
    val expansion: Expansion?,
    val closedInChapter: Int?
) {
    val label: String get() = "$number. $name — $bossName"
}

data class AchievementRow(val key: String, val name: String, val custom: Boolean, val chapter: Int)

data class TrophyRow(val bossName: String, val element: Element?, val chapters: List<Int>)

/** Бой в истории: итог, задание, глава, кто и когда начал и отправил итог. */
data class BattleRow(
    val id: String,
    val title: String,
    val details: String,
    val status: String,
    val inProgress: Boolean
)

data class ShareState(
    val loading: Boolean = false,
    val link: ShareLinkDto? = null,
    val error: String? = null,
    val confirmReissue: Boolean = false,
    val confirmRevoke: Boolean = false
)

data class CampaignSheetState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val name: String = "",
    val chapter: Int = 0,
    val chapterTitle: String = "",
    val difficulty: Int = 0,
    val forgeLevel: Int = 1,
    val labLevel: Int = 1,
    val status: CampaignStatus = CampaignStatus.ACTIVE,
    val finalBossName: String? = null,
    val access: CampaignAccess = CampaignAccess.LOCAL,
    val ownerName: String? = null,
    val onServer: Boolean = false,
    val unsynced: Boolean = false,
    val syncMessage: String? = null,
    val tab: SheetTab = SheetTab.HUNTERS,
    val hunters: List<HunterState> = emptyList(),
    val selectedHunter: HunterClass? = null,
    val openQuests: List<QuestRow> = emptyList(),
    val completedQuests: List<QuestRow> = emptyList(),
    val expiredQuests: List<QuestRow> = emptyList(),
    val achievements: List<AchievementRow> = emptyList(),
    val trophies: List<TrophyRow> = emptyList(),
    val battles: List<BattleRow> = emptyList(),
    val activeBattles: List<String> = emptyList(),
    val notes: String = "",
    val notesStatus: String? = null,
    /** Редактор открытых заданий: отмеченные номера; `null` — закрыт. */
    val questEditor: Set<Int>? = null,
    val questEditorLocked: Set<Int> = emptySet(),
    val allQuests: List<QuestRow> = emptyList(),
    val achievementInput: String = "",
    val achievementSuggestions: List<String> = emptyList(),
    /** Итог «Выполнено» вручную: открытые задания и правила. */
    val completedResult: Pair<String, List<RuleExplanation>>? = null,
    val chapterDialog: Boolean = false,
    val chapterInput: String = "",
    val confirmDelete: Boolean = false,
    val itemEditor: ItemEditorState? = null,
    /** Окно «Имя игрока» выбранного охотника; `null` — закрыто. */
    val hunterNameInput: String? = null,
    val share: ShareState? = null
) {
    val hunter: HunterState? get() = hunters.firstOrNull { it.hunterClass == selectedHunter } ?: hunters.firstOrNull()
    val canShare: Boolean get() = onServer && access == CampaignAccess.OWNER
    val canDelete: Boolean get() = access != CampaignAccess.LINK
    val pendingTransition: Boolean get() = status == CampaignStatus.CHAPTER_TRANSITION
    val completed: Boolean get() = status == CampaignStatus.COMPLETED

    fun unlockable(branch: SkillBranch, tier: Int): Boolean =
        hunter?.let { CampaignRules.canUnlock(it.skills.toSet(), branch, tier) } ?: false

    fun lockable(branch: SkillBranch, tier: Int): Boolean =
        hunter?.let { CampaignRules.canLock(it.skills.toSet(), branch, tier) } ?: false
}

/** Окно предмета инвентаря: добавить (key = null) или изменить. */
data class ItemEditorState(val key: String?, val kind: ItemKind, val name: String, val level: Int?)

/**
 * Лист кампании: охотники (навыки, ресурсы, инвентарь), задания, достижения, трофеи, история боёв, заметки, глава.
 * Онлайн — правки уходят на сервер, а правки других участников приходят сами (поток событий кампании).
 */
class CampaignSheetViewModel(app: AppViewModel, localId: Long, scope: CoroutineScope) : CampaignScopedViewModel(app, localId, scope) {

    private val _state = MutableStateFlow(CampaignSheetState())
    val state: StateFlow<CampaignSheetState> = _state.asStateFlow()

    private var notesJob: Job? = null
    private var eventsJob: Job? = null
    private var pendingNotes: String? = null

    init {
        observe()
        scope.launch {
            delay(300)
            if (_campaign().value == null) _state.update { it.copy(loaded = true, missing = true) }
        }
    }

    private fun _campaign() = campaign

    override fun onCampaign(campaign: StoredCampaign) {
        val state = campaign.state
        val now = now()
        _state.update { current ->
            current.copy(
                loaded = true,
                missing = false,
                name = state.name,
                chapter = state.chapter,
                chapterTitle = CampaignRules.chapterTitle(state.chapter),
                difficulty = com.primalapp.catalog.Catalog.difficultyForChapter(state.chapter),
                forgeLevel = state.forgeLevel,
                labLevel = state.labLevel,
                status = state.status,
                finalBossName = state.finalBossCode?.let { catalog.bossName(it) },
                access = campaign.access,
                ownerName = campaign.ownerName,
                onServer = campaign.onServer,
                unsynced = campaign.hasUnsynced,
                syncMessage = campaign.syncMessage,
                hunters = state.squad,
                selectedHunter = current.selectedHunter?.takeIf { selected -> state.hunter(selected) != null }
                    ?: state.squad.firstOrNull()?.hunterClass,
                openQuests = state.openQuests.filter { questVisible(it.number) }.map { questRow(it.number, null) },
                completedQuests = state.completedQuests.filter { questVisible(it.number) }.map { questRow(it.number, it.closedInChapter) },
                expiredQuests = state.expiredQuests.filter { questVisible(it.number) }.map { questRow(it.number, it.closedInChapter) },
                achievements = state.achievements.map { AchievementRow(it.key, it.name, it.code == null, it.grantedInChapter) },
                trophies = state.trophies.groupBy { it.bossCode }.map { (code, list) ->
                    TrophyRow(catalog.bossName(code), catalog.boss(code)?.element, list.map { it.chapter }.sorted())
                },
                battles = state.battles.sortedByDescending { it.startedAt }.map { battleRow(it) },
                activeBattles = rules.activeBattles(state, now).map { battle ->
                    "Идёт бой: ${battle.startedBy?.name ?: "участник"}${battle.questNumber?.let { ", задание $it «${catalog.quest(it)?.name.orEmpty()}»" }.orEmpty()}, с ${time(battle.startedAt)}"
                },
                notes = if (pendingNotes != null) current.notes else state.notes,
                allQuests = catalog.quests.filter { questVisible(it.number) }.map { questRow(it.number, null) },
                questEditorLocked = state.quests.filter { it.status != com.primalapp.campaign.QuestStatus.OPEN }.map { it.number }.toSet()
            )
        }
        watchEvents(campaign)
    }

    // region Живые обновления

    /** Онлайн-кампания с сервера: события кампании (SSE) — при новой версии лист обновляется сам. */
    private fun watchEvents(campaign: StoredCampaign) {
        val serverId = campaign.serverId
        if (serverId == null || !core.settings.current.online) {
            eventsJob?.cancel()
            eventsJob = null
            return
        }
        if (eventsJob?.isActive == true) return
        eventsJob = scope.launch {
            core.sync.syncCampaign(localId)
            while (isActive && core.settings.current.online) {
                try {
                    core.api.events(serverId).collect { event ->
                        when (event.type) {
                            "campaign.updated" -> core.sync.syncCampaign(localId)
                            "access.revoked" -> core.sync.sync(references = false)
                        }
                    }
                } catch (error: ApiException) {
                    if (error.status == 404) break
                } catch (error: OfflineException) {
                    // переподключение ниже
                }
                delay(RECONNECT_MILLIS)
            }
        }
    }

    /** Экран листа закрыт — поток событий больше не нужен. */
    fun onClose() {
        eventsJob?.cancel()
        flushNotes()
    }

    // endregion

    fun onTabSelected(tab: SheetTab) = _state.update { it.copy(tab = tab) }

    fun onHunterSelected(hunterClass: HunterClass) = _state.update { it.copy(selectedHunter = hunterClass) }

    fun onRefresh() {
        if (!core.settings.current.online) {
            app.showMessage("Офлайн-режим: синхронизация выключена в настройках.")
            return
        }
        scope.launch { core.sync.syncCampaign(localId) }
    }

    // region Охотники

    fun onSkillClicked(branch: SkillBranch, tier: Int) {
        val hunter = _state.value.hunter ?: return
        val unlocked = SkillStep(branch, tier) in hunter.skills
        perform(CampaignOp.SetSkill(hunter.hunterClass, branch, tier, !unlocked, now(), actor))
    }

    fun onResourceChanged(code: ResourceCode, delta: Int) {
        val hunter = _state.value.hunter ?: return
        if (delta < 0 && hunter.quantity(code) <= 0) return
        perform(CampaignOp.AdjustResources(hunter.hunterClass, mapOf(code to delta), now(), actor))
    }

    fun onRenameHunterRequested() = _state.update { it.copy(hunterNameInput = it.hunter?.playerName.orEmpty()) }

    fun onHunterNameInput(text: String) = _state.update { it.copy(hunterNameInput = text.take(60)) }

    fun onHunterNameDismissed() = _state.update { it.copy(hunterNameInput = null) }

    /** Имя игрока; пустое — название класса. */
    fun onHunterNameSaved() {
        val hunter = _state.value.hunter ?: return
        val name = _state.value.hunterNameInput ?: return
        perform(CampaignOp.RenameHunter(hunter.hunterClass, name, now(), actor)) { _state.update { it.copy(hunterNameInput = null) } }
    }

    fun onAddItem() = _state.update { it.copy(itemEditor = ItemEditorState(null, ItemKind.EQUIPMENT, "", 1)) }

    fun onEditItem(item: InventoryItem) = _state.update { it.copy(itemEditor = ItemEditorState(item.key, item.kind, item.name, item.level)) }

    fun onItemEditorChanged(editor: ItemEditorState) = _state.update { it.copy(itemEditor = editor) }

    fun onItemEditorDismissed() = _state.update { it.copy(itemEditor = null) }

    fun onItemEditorSaved() {
        val editor = _state.value.itemEditor ?: return
        val hunter = _state.value.hunter ?: return
        val op = if (editor.key == null) {
            CampaignOp.AddItem(hunter.hunterClass, newLocalKey(), editor.kind, editor.name, editor.level, now(), actor)
        } else {
            CampaignOp.EditItem(hunter.hunterClass, editor.key, editor.name, editor.level, now(), actor)
        }
        perform(op) { _state.update { it.copy(itemEditor = null) } }
    }

    fun onRemoveItem(item: InventoryItem) {
        val hunter = _state.value.hunter ?: return
        perform(CampaignOp.RemoveItem(hunter.hunterClass, item.key, now(), actor))
    }

    // endregion

    // region Задания

    /** «Выполнено» вручную: открываются задания из наград победы, ресурсы и достижения не начисляются. */
    fun onCompleteQuest(number: Int) {
        val campaign = campaign.value ?: return
        val result = runCatching {
            rules.completeQuest(campaign.state, number, core.store.context(campaign, actor)).second
        }.getOrNull()
        perform(CampaignOp.CompleteQuest(number, now(), actor)) {
            if (result != null) {
                val opened = if (result.opened.isEmpty()) "" else " Добавлены задания: ${result.opened.joinToString(", ")}."
                _state.update { it.copy(completedResult = ("Задание $number выполнено.$opened") to result.rules) }
            }
        }
    }

    fun onCompletedResultDismissed() = _state.update { it.copy(completedResult = null) }

    /** «Отмена» у выполненного: снова открыто; открытые им задания и награды не меняются. */
    fun onReopenQuest(number: Int) = perform(CampaignOp.ReopenQuest(number, now(), actor))

    fun onOpenQuestEditor() = _state.update { state ->
        state.copy(questEditor = campaign.value?.state?.openQuests?.map { it.number }?.toSet().orEmpty())
    }

    fun onQuestEditorToggled(number: Int) = _state.update { state ->
        val selected = state.questEditor ?: return@update state
        if (number in state.questEditorLocked) return@update state
        state.copy(questEditor = if (number in selected) selected - number else selected + number)
    }

    fun onQuestEditorSaved() {
        val selected = _state.value.questEditor ?: return
        // Скрытые задания отключённых дополнений остаются открытыми, как были
        val hidden = campaign.value?.state?.openQuests?.map { it.number }?.filterNot { questVisible(it) }.orEmpty()
        perform(CampaignOp.SetOpenQuests(selected + hidden, now(), actor)) { _state.update { it.copy(questEditor = null) } }
    }

    fun onQuestEditorDismissed() = _state.update { it.copy(questEditor = null) }

    /** «Начать бой»: пролог и финал — сразу подготовка, иначе — выбор задания. */
    fun onStartBattle() {
        val state = campaign.value?.state ?: return
        when {
            state.status == CampaignStatus.CHAPTER_TRANSITION -> app.navigate(Screen.Transition(localId))
            state.status == CampaignStatus.COMPLETED -> app.showMessage("Кампания пройдена! Пробуждённый повержен.")
            state.chapter == 0 || state.finalBossCode != null -> app.navigate(Screen.CampaignBattleSetup(localId, null))
            else -> app.navigate(Screen.QuestSelect(localId))
        }
    }

    // endregion

    // region Достижения

    fun onAchievementInput(text: String) = _state.update { state ->
        val query = text.trim()
        state.copy(
            achievementInput = text.take(100),
            achievementSuggestions = if (query.isEmpty()) emptyList() else catalog.achievements
                .map { it.name }
                .filter { name -> name.contains(query, ignoreCase = true) && state.achievements.none { it.name == name } }
                .take(6)
        )
    }

    fun onAddAchievement(name: String = _state.value.achievementInput) {
        if (name.isBlank()) return
        perform(CampaignOp.AddAchievement(name, newLocalKey(), now(), actor)) {
            _state.update { it.copy(achievementInput = "", achievementSuggestions = emptyList()) }
        }
    }

    fun onDeleteAchievement(row: AchievementRow) = perform(CampaignOp.DeleteAchievement(row.key, row.name, now(), actor))

    // endregion

    // region Заметки, глава, удаление

    /** Заметки сохраняются сами — через 0,8 с после последнего изменения. */
    fun onNotesChanged(text: String) {
        val notes = text.take(CampaignRules.MAX_NOTES)
        pendingNotes = notes
        _state.update { it.copy(notes = notes, notesStatus = "Сохраняю…") }
        notesJob?.cancel()
        notesJob = scope.launch {
            delay(NOTES_DELAY)
            flushNotes()
        }
    }

    fun flushNotes() {
        val notes = pendingNotes ?: return
        pendingNotes = null
        notesJob?.cancel()
        if (notes == campaign.value?.state?.notes) {
            _state.update { it.copy(notesStatus = "Сохранено") }
            return
        }
        // Экран мог уже закрыться (его корутины отменены) — заметки сохраняет корень приложения
        val op = CampaignOp.SetNotes(notes, now(), actor)
        core.scope.launch {
            try {
                core.store.perform(localId, op)
                _state.update { it.copy(notesStatus = "Сохранено") }
            } catch (error: com.primalapp.campaign.CampaignRuleException) {
                app.showMessage(error.message ?: "Заметки не сохранены.")
            }
        }
    }

    fun onChangeChapterRequested() = _state.update { it.copy(chapterDialog = true, chapterInput = it.chapter.toString()) }

    fun onChapterInput(text: String) = _state.update { it.copy(chapterInput = text.filter { c -> c.isDigit() }.take(2)) }

    fun onChapterDialogDismissed() = _state.update { it.copy(chapterDialog = false) }

    /** Ручная правка главы: бои, начатые раньше, после этого не принимаются. */
    fun onChapterConfirmed() {
        val chapter = _state.value.chapterInput.toIntOrNull()
        if (chapter == null || chapter !in 0..11) {
            app.showMessage("Глава — от 0 до 11.")
            return
        }
        perform(CampaignOp.ChangeChapter(chapter, now(), actor)) { _state.update { it.copy(chapterDialog = false) } }
    }

    fun onDeleteRequested() = _state.update { it.copy(confirmDelete = true) }

    fun onDeleteDismissed() = _state.update { it.copy(confirmDelete = false) }

    fun onDeleteConfirmed() {
        val access = campaign.value?.access ?: return
        _state.update { it.copy(confirmDelete = false) }
        scope.launch {
            if (access == CampaignAccess.LOCAL) core.store.delete(localId) else core.store.markDeleted(localId)
            app.backTo(Screen.CampaignList)
        }
    }

    // endregion

    // region История боёв

    /** «Снять отметку» у идущего боя (забытого или брошенного). */
    fun onAbandonBattle(id: String) = perform(CampaignOp.AbandonBattle(id, now(), actor))

    private fun battleRow(battle: BattleRecord): BattleRow {
        val result = when (battle.result) {
            BattleResult.VICTORY -> "Победа"
            BattleResult.DEFEAT -> "Поражение"
            null -> "Бой"
        }
        val target = when (battle.purpose) {
            BattlePurpose.QUEST -> "задание ${battle.questNumber} «${catalog.quest(battle.questNumber ?: 0)?.name.orEmpty()}»"
            BattlePurpose.PROLOGUE -> "пролог"
            BattlePurpose.FINAL -> "финальный бой"
            BattlePurpose.FREE -> "без задания" + battle.bossCode?.let { ", ${catalog.bossName(it)}" }.orEmpty()
        }
        val started = "начало: ${battle.startedBy?.name ?: "—"}, ${dateTime(battle.startedAt)}"
        val submitted = battle.submittedAt?.let { "; итог: ${battle.submittedBy?.name ?: "—"}, ${dateTime(it)}" }.orEmpty()
        val status = when (battle.status) {
            BattleStatus.IN_PROGRESS -> "идёт"
            BattleStatus.APPLIED -> "принят"
            BattleStatus.DISMISSED -> "отклонён"
            BattleStatus.ABANDONED -> "брошен"
        }
        return BattleRow(battle.id, "$result · $target", "${CampaignRules.chapterTitle(battle.chapter)}. $started$submitted", status,
            battle.status == BattleStatus.IN_PROGRESS)
    }

    // endregion

    // region Совместная игра

    /** «Поделиться»: ссылка-приглашение на кампанию (только владельцу, онлайн). */
    fun onShareOpened() {
        if (!core.settings.current.online) {
            app.showMessage("Поделиться можно в онлайн-режиме.")
            return
        }
        val serverId = campaign.value?.serverId ?: return
        _state.update { it.copy(share = ShareState(loading = true)) }
        scope.launch {
            try {
                val link = core.api.shareLink(serverId)
                _state.update { it.copy(share = ShareState(link = link)) }
            } catch (error: Exception) {
                _state.update { it.copy(share = ShareState(error = message(error))) }
            }
        }
    }

    fun onShareCreate() {
        val serverId = campaign.value?.serverId ?: return
        _state.update { it.copy(share = (it.share ?: ShareState()).copy(loading = true, confirmReissue = false, error = null)) }
        scope.launch {
            try {
                val link = core.api.createShareLink(serverId)
                _state.update { it.copy(share = ShareState(link = link)) }
            } catch (error: Exception) {
                _state.update { it.copy(share = (it.share ?: ShareState()).copy(loading = false, error = message(error))) }
            }
        }
    }

    fun onShareReissueRequested() = _state.update { it.copy(share = it.share?.copy(confirmReissue = true)) }

    fun onShareRevokeRequested() = _state.update { it.copy(share = it.share?.copy(confirmRevoke = true)) }

    fun onShareConfirmDismissed() = _state.update { it.copy(share = it.share?.copy(confirmReissue = false, confirmRevoke = false)) }

    /** Отозвать: доступ по ссылке сразу пропадает у всех. */
    fun onShareRevoke() {
        val serverId = campaign.value?.serverId ?: return
        _state.update { it.copy(share = it.share?.copy(loading = true, confirmRevoke = false)) }
        scope.launch {
            try {
                core.api.revokeShareLink(serverId)
                _state.update { it.copy(share = ShareState()) }
            } catch (error: Exception) {
                _state.update { it.copy(share = (it.share ?: ShareState()).copy(loading = false, error = message(error))) }
            }
        }
    }

    fun onShareClosed() = _state.update { it.copy(share = null) }

    /** Ссылка на кампанию на сайте — участнику, у которого уже есть доступ. */
    fun campaignUrl(): String? = campaign.value?.serverId?.let { app.campaignUrl(it) }

    // endregion

    private fun questRow(number: Int, closedInChapter: Int?): QuestRow {
        val quest = catalog.quest(number)
        val boss = catalog.boss(quest?.bossCode)
        return QuestRow(number, quest?.name ?: "Задание $number", boss?.name.orEmpty(), boss?.element,
            quest?.expansion ?: Expansion.ofQuest(number), closedInChapter)
    }

    private fun message(error: Exception): String = when (error) {
        is ApiException -> error.userMessage
        is OfflineException -> "Сервер недоступен. Проверьте подключение."
        else -> error.message ?: "Ошибка."
    }

    private fun time(millis: Long): String {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    private fun dateTime(millis: Long): String {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.dayOfMonth.toString().padStart(2, '0')}.${local.monthNumber.toString().padStart(2, '0')} " +
            "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    private companion object {
        const val NOTES_DELAY = 800L
        const val RECONNECT_MILLIS = 5_000L
    }
}
