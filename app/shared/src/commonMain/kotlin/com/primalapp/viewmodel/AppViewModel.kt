package com.primalapp.viewmodel

import com.primalapp.campaign.BattlePurpose
import com.primalapp.campaign.BattleRecord
import com.primalapp.campaign.CampaignOp
import com.primalapp.campaign.CampaignRuleException
import com.primalapp.campaign.newUuid
import com.primalapp.database.currentTimeMillis
import com.primalapp.info.InfoSectionCode
import com.primalapp.model.Hunter
import com.primalapp.model.campaign.Boss
import com.primalapp.net.PrimalApi
import com.primalapp.sync.AppCore
import com.primalapp.sync.ConnectionMode
import com.primalapp.sync.SyncStatus
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Экраны приложения. */
sealed interface Screen {
    data object MainMenu : Screen
    data object ExpeditionSetup : Screen
    data object Battle : Screen
    data object CampaignList : Screen
    data object CampaignCreate : Screen
    data class CampaignSheet(val localId: Long) : Screen
    data class Forge(val localId: Long) : Screen
    data class Lab(val localId: Long) : Screen
    data class QuestSelect(val localId: Long) : Screen
    data class CampaignBattleSetup(val localId: Long, val questNumber: Int?) : Screen
    data class Outcome(val localId: Long) : Screen
    data class Transition(val localId: Long) : Screen
    data class Info(val section: InfoSectionCode? = null, val entryId: Long? = null) : Screen
    data object Settings : Screen
    /** Вход и регистрация; после входа — назад, откуда пришли. */
    data object Login : Screen
    data class Join(val token: String) : Screen
}

/**
 * Бой кампании, который сейчас идёт или закончился, но итог ещё не принят: снимок подготовки — для записи в
 * историю и проверки, что глава с тех пор не закрыта.
 */
data class CampaignBattleContext(
    val localId: Long,
    val battleId: String,
    val purpose: BattlePurpose,
    val questNumber: Int?,
    val bossCode: String?,
    val difficulty: Int,
    val chapter: Int,
    val progressSeq: Int,
    val startedAt: Long
)

/** Состояние связи для шапки главного меню и настроек. */
data class ConnectionState(
    val mode: ConnectionMode = ConnectionMode.ONLINE,
    val server: String = PrimalApi.DEFAULT_SERVER,
    val accountName: String? = null,
    val isUser: Boolean = false,
    val sync: SyncStatus = SyncStatus.Idle
) {
    val label: String
        get() = when {
            mode == ConnectionMode.OFFLINE -> "Офлайн-режим"
            sync is SyncStatus.Unreachable -> "Нет связи с сервером"
            accountName != null -> "Онлайн · $accountName"
            else -> "Онлайн · вход не выполнен"
        }
}

/**
 * Корневая ViewModel: навигация, связь с сервером, текущий бой (экспедиция или бой кампании), ссылки на сайт.
 * Экраны со своей логикой получают свои ViewModel; эта хранит то, что общее для них.
 */
class AppViewModel(
    val core: AppCore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.MainMenu))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _connection = MutableStateFlow(ConnectionState())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    val battleViewModel = BattleViewModel(scope)

    private val _campaignBattle = MutableStateFlow<CampaignBattleContext?>(null)

    /** Бой кампании в этом приложении (идёт или ждёт итога). */
    val campaignBattle: StateFlow<CampaignBattleContext?> = _campaignBattle.asStateFlow()

    /** Ссылка, открытая до готовности приложения. */
    private var pendingLink: String? = null

    init {
        scope.launch {
            combine(core.settings.settings, core.sync.status) { settings, status ->
                val account = settings.activeAccount
                ConnectionState(settings.mode, settings.serverUrl, account?.name, account?.isUser == true, status)
            }.collect { _connection.value = it }
        }
        scope.launch { core.sync.messages.collect { _messages.emit(it) } }
        scope.launch {
            core.start()
            pendingLink?.let { link ->
                pendingLink = null
                openLink(link)
            }
        }
    }

    val screen: Screen get() = _stack.value.last()

    fun navigate(screen: Screen) {
        _stack.update { it + screen }
    }

    /** Заменить текущий экран (например, подготовку боя — боем). */
    fun replace(screen: Screen) {
        _stack.update { it.dropLast(1) + screen }
    }

    /** Вернуться к экрану, если он есть в стеке, иначе открыть поверх главного меню. */
    fun backTo(screen: Screen) {
        _stack.update { stack ->
            val index = stack.indexOfLast { it == screen }
            if (index >= 0) stack.take(index + 1) else listOf(Screen.MainMenu, screen)
        }
    }

    /** «Назад»; `false` — уже главное меню (приложение закрывается). */
    fun back(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.update { it.dropLast(1) }
        return true
    }

    fun toMainMenu() {
        _stack.value = listOf(Screen.MainMenu)
    }

    fun showMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun onResume() = core.onResume()

    // region Бой

    /** В приложении есть бой, к которому можно вернуться (идёт или закончен, но итог кампании не принят). */
    fun hasBattle(): Boolean {
        val phase = battleViewModel.state.value.phase
        return phase != FightPhase.PRE_BATTLE && phase != FightPhase.SETUP
    }

    fun resumeBattle() {
        if (hasBattle()) navigate(Screen.Battle)
    }

    /** Экспедиция: бой без кампании. Незаконченный бой кампании заменяется — его отметка снимается. */
    fun startExpedition(hunterCount: Int, damageForWound: Int?, healthForStanceChange: Int?, boss: Boss?, difficulty: Int) {
        abandonCampaignBattle()
        battleViewModel.resetBattle()
        val hunters = (1..hunterCount).map { Hunter(name = "Охотник $it") }
        battleViewModel.startBattleWithHunters(hunters, damageForWound, healthForStanceChange, boss, difficulty)
        replace(Screen.Battle)
    }

    /**
     * Бой кампании: отметка о начале записывается в историю кампании (онлайн она уходит на сервер — другие участники
     * видят «Идёт бой»), дальше — экран боя.
     */
    fun startCampaignBattle(
        context: CampaignBattleContext,
        hunters: List<Hunter>,
        damageForWound: Int?,
        healthForStanceChange: Int?,
        boss: Boss?
    ) {
        abandonCampaignBattle()
        scope.launch {
            val campaign = core.store.get(context.localId) ?: return@launch
            val actor = core.settings.current.activeAccount?.actor()
            val record = BattleRecord(
                id = context.battleId,
                purpose = context.purpose,
                questNumber = context.questNumber,
                bossCode = context.bossCode,
                difficulty = context.difficulty,
                chapter = context.chapter,
                progressSeq = context.progressSeq,
                startedBy = actor,
                startedAt = context.startedAt
            )
            try {
                core.store.perform(campaign.localId, CampaignOp.StartBattle(record, context.startedAt, actor))
            } catch (error: CampaignRuleException) {
                showMessage(error.message ?: "Бой не начат.")
                return@launch
            }
            _campaignBattle.value = context
            battleViewModel.resetBattle()
            battleViewModel.startBattleWithHunters(hunters, damageForWound, healthForStanceChange, boss, context.difficulty)
            replace(Screen.Battle)
        }
    }

    /** Новый идентификатор и снимок кампании для боя. */
    fun newBattleContext(localId: Long, purpose: BattlePurpose, questNumber: Int?, bossCode: String?, difficulty: Int,
                         chapter: Int, progressSeq: Int): CampaignBattleContext =
        CampaignBattleContext(localId, newUuid(), purpose, questNumber, bossCode, difficulty, chapter, progressSeq, currentTimeMillis())

    /** Итог боя кампании принят или отклонён: бой больше не нужен. */
    fun finishCampaignBattle() {
        _campaignBattle.value = null
        battleViewModel.resetBattle()
    }

    /** Бой кампании брошен или заменён новым: отметка о начале снимается. */
    fun abandonCampaignBattle() {
        val context = _campaignBattle.value ?: return
        _campaignBattle.value = null
        scope.launch {
            runCatching {
                core.store.perform(context.localId, CampaignOp.AbandonBattle(context.battleId, currentTimeMillis(),
                    core.settings.current.activeAccount?.actor()))
            }
        }
    }

    /** Экспедиция закончена: «Выход в меню» — бой удаляется. */
    fun leaveExpedition() {
        battleViewModel.resetBattle()
        toMainMenu()
    }

    // endregion

    // region Ссылки на сайт

    /**
     * Ссылка на сайт (App Links): `/s/<токен>` — приглашение в кампанию, `/campaigns/<id>` — лист кампании, остальное —
     * главное меню. Ссылка другого сервера — подсказка указать его в настройках.
     */
    fun openLink(link: String) {
        if (!core.ready.value) {
            pendingLink = link
            return
        }
        val url = runCatching { Url(link) }.getOrNull() ?: return
        val server = runCatching { Url(PrimalApi.normalizeServerUrl(core.settings.current.serverUrl)) }.getOrNull()
        val segments = url.encodedPath.split('/').filter { it.isNotEmpty() }
        if (server != null && !url.host.equals(server.host, ignoreCase = true)) {
            showMessage("Ссылка ведёт на сервер ${url.host}. Укажите его в настройках, чтобы открыть ссылку в приложении.")
            return
        }
        when {
            segments.size >= 2 && segments[0] == "s" -> {
                _stack.value = listOf(Screen.MainMenu, Screen.Join(segments[1]))
            }
            segments.size >= 2 && segments[0] == "campaigns" && segments[1].toLongOrNull() != null -> {
                val serverId = segments[1].toLong()
                scope.launch {
                    if (core.settings.current.online) core.sync.sync(references = false)
                    val localId = core.localIdForServer(serverId)
                    if (localId != null) {
                        _stack.value = listOf(Screen.MainMenu, Screen.CampaignList, Screen.CampaignSheet(localId))
                    } else {
                        showMessage("Кампания недоступна: войдите в аккаунт, в котором она есть, или откройте ссылку-приглашение.")
                    }
                }
            }
            segments.firstOrNull() == "info" -> _stack.value = listOf(Screen.MainMenu, Screen.Info())
            else -> toMainMenu()
        }
    }

    /** Ссылка на кампанию на сайте; [server] — сервер копии кампании (по умолчанию — текущий). */
    fun campaignUrl(serverId: Long, server: String? = null): String =
        PrimalApi.normalizeServerUrl(server ?: core.settings.current.serverUrl) + "/campaigns/$serverId"

    // endregion
}
