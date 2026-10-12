package com.primalapp.viewmodel

import com.primalapp.campaign.CampaignRuleException
import com.primalapp.campaign.CampaignRules
import com.primalapp.campaign.CampaignStatus
import com.primalapp.model.campaign.HunterClass
import com.primalapp.sync.CampaignAccess
import com.primalapp.sync.StoredCampaign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Строка списка кампаний. */
data class CampaignListItem(
    val localId: Long,
    val name: String,
    val chapterTitle: String,
    val squad: String,
    val access: CampaignAccess,
    val ownerName: String?,
    val pendingTransition: Boolean,
    val completed: Boolean,
    val unsynced: Boolean,
    val uploadRequested: Boolean,
    val syncMessage: String?
) {
    val canDelete: Boolean get() = access != CampaignAccess.LINK
    val accessLabel: String
        get() = when (access) {
            CampaignAccess.LOCAL -> if (uploadRequested) "Только на устройстве · будет загружена на сервер" else "Только на устройстве"
            CampaignAccess.OWNER -> "На сервере"
            CampaignAccess.LINK -> "По ссылке · владелец ${ownerName.orEmpty()}"
        }
}

data class CampaignListState(
    val items: List<CampaignListItem> = emptyList(),
    val canUpload: Boolean = false,
    val confirmDelete: CampaignListItem? = null
)

/** Список кампаний: свои на устройстве и копии кампаний аккаунта с сервера (свои и открытые по ссылке). */
class CampaignListViewModel(private val app: AppViewModel, private val scope: CoroutineScope) {

    private val core = app.core
    private val _state = MutableStateFlow(CampaignListState())
    val state: StateFlow<CampaignListState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(core.store.observeAll(), core.settings.settings) { campaigns, settings ->
                val key = settings.activeAccount?.key
                val visible = campaigns.filter { it.access == CampaignAccess.LOCAL || it.account == key }
                visible.map { item(it) } to (settings.activeAccount?.isUser == true)
            }.collect { (items, canUpload) -> _state.update { it.copy(items = items, canUpload = canUpload) } }
        }
        if (core.settings.current.online) core.sync.requestSync()
    }

    private fun item(campaign: StoredCampaign) = CampaignListItem(
        localId = campaign.localId,
        name = campaign.state.name,
        chapterTitle = CampaignRules.chapterTitle(campaign.state.chapter),
        squad = campaign.state.squad.joinToString(", ") { "${it.hunterClass.displayName} — ${it.playerName}" },
        access = campaign.access,
        ownerName = campaign.ownerName,
        pendingTransition = campaign.state.status == CampaignStatus.CHAPTER_TRANSITION,
        completed = campaign.state.status == CampaignStatus.COMPLETED,
        unsynced = campaign.onServer && campaign.hasUnsynced,
        uploadRequested = campaign.upload,
        syncMessage = campaign.syncMessage
    )

    fun onOpen(localId: Long) = app.navigate(Screen.CampaignSheet(localId))

    fun onNew() = app.navigate(Screen.CampaignCreate)

    fun onDeleteRequested(item: CampaignListItem) = _state.update { it.copy(confirmDelete = item) }

    fun onDeleteDismissed() = _state.update { it.copy(confirmDelete = null) }

    /**
     * Удалить: кампания только на устройстве удаляется сразу; своя на сервере — и на сервере (без сети — при
     * следующей синхронизации). Кампанию по ссылке удалить нельзя.
     */
    fun onDeleteConfirmed() {
        val item = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null) }
        scope.launch {
            when (item.access) {
                CampaignAccess.LOCAL -> core.store.delete(item.localId)
                CampaignAccess.OWNER -> core.store.markDeleted(item.localId)
                CampaignAccess.LINK -> app.showMessage("Кампанию по ссылке может удалить только владелец.")
            }
        }
    }

    /** «Загрузить на сервер»: кампания станет доступна на сайте и других устройствах аккаунта. */
    fun onUpload(item: CampaignListItem) {
        val account = core.settings.current.activeAccount
        if (account == null || !account.isUser) {
            app.showMessage("Чтобы загрузить кампанию на сервер, войдите в аккаунт в настройках.")
            return
        }
        scope.launch {
            core.store.requestUpload(item.localId, account.key)
            if (!core.settings.current.online) app.showMessage("Кампания будет загружена, когда приложение будет в онлайн-режиме.")
        }
    }
}

/** Выбранный охотник отряда при создании кампании. */
data class SquadMember(val hunterClass: HunterClass, val playerName: String)

data class CampaignCreateState(
    val name: String = "",
    val squad: List<SquadMember> = emptyList(),
    val error: String? = null,
    val creating: Boolean = false,
    val willUpload: Boolean = false
)

/**
 * Новая кампания: название и отряд из 2–5 охотников (порядок выбора — порядок в отряде, пустое имя игрока —
 * название класса). После входа в аккаунт кампания загружается на сервер. Сразу открывается подготовка пролога.
 */
class CampaignCreateViewModel(private val app: AppViewModel, private val scope: CoroutineScope) {

    private val core = app.core
    private val _state = MutableStateFlow(CampaignCreateState(willUpload = core.settings.current.activeAccount?.isUser == true))
    val state: StateFlow<CampaignCreateState> = _state.asStateFlow()

    fun onNameChanged(name: String) = _state.update { it.copy(name = name.take(100), error = null) }

    fun onClassToggled(hunterClass: HunterClass) = _state.update { state ->
        val selected = state.squad.any { it.hunterClass == hunterClass }
        when {
            selected -> state.copy(squad = state.squad.filterNot { it.hunterClass == hunterClass }, error = null)
            state.squad.size >= CampaignRules.MAX_HUNTERS -> state.copy(error = "В отряде не больше ${CampaignRules.MAX_HUNTERS} охотников.")
            else -> state.copy(squad = state.squad + SquadMember(hunterClass, ""), error = null)
        }
    }

    fun onPlayerNameChanged(hunterClass: HunterClass, name: String) = _state.update { state ->
        state.copy(squad = state.squad.map { if (it.hunterClass == hunterClass) it.copy(playerName = name.take(60)) else it })
    }

    fun onCreate() {
        val current = _state.value
        if (current.creating) return
        scope.launch {
            _state.update { it.copy(creating = true) }
            try {
                val account = core.settings.current.activeAccount?.takeIf { it.isUser }
                val created = core.store.rules.create(current.name, current.squad.map { it.hunterClass to it.playerName },
                    core.store.context(null, account?.actor()))
                val localId = core.store.create(created, upload = account != null, account = account?.key)
                app.replace(Screen.CampaignSheet(localId))
                app.navigate(Screen.CampaignBattleSetup(localId, null))
            } catch (error: CampaignRuleException) {
                _state.update { it.copy(error = error.message, creating = false) }
            }
        }
    }
}
