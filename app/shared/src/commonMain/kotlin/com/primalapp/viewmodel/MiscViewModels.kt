package com.primalapp.viewmodel

import com.primalapp.info.InfoData
import com.primalapp.info.InfoEntry
import com.primalapp.info.InfoSectionCode
import com.primalapp.info.InfoText
import com.primalapp.model.campaign.Expansion
import com.primalapp.net.ApiException
import com.primalapp.net.OfflineException
import com.primalapp.net.PrimalApi
import com.primalapp.sync.ConnectionMode
import com.primalapp.sync.ReferenceSource
import com.primalapp.sync.StoredCampaign
import com.primalapp.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun errorText(error: Throwable): String = when (error) {
    is ApiException -> error.userMessage
    is OfflineException -> "Сервер недоступен. Проверьте подключение и адрес сервера в настройках."
    else -> error.message ?: "Ошибка."
}

// region Выбор задания

data class QuestSelectState(val campaignName: String = "", val quests: List<QuestRow> = emptyList())

/** Выбор задания для боя: открытые задания с боссами и «Продолжить без задания». */
class QuestSelectViewModel(app: AppViewModel, localId: Long, scope: CoroutineScope) : CampaignScopedViewModel(app, localId, scope) {

    private val _state = MutableStateFlow(QuestSelectState())
    val state: StateFlow<QuestSelectState> = _state.asStateFlow()

    init {
        observe()
    }

    override fun onCampaign(campaign: StoredCampaign) {
        _state.value = QuestSelectState(
            campaignName = campaign.state.name,
            quests = campaign.state.openQuests.filter { questVisible(it.number) }.map { quest ->
                val def = catalog.quest(quest.number)
                val boss = catalog.boss(def?.bossCode)
                QuestRow(quest.number, def?.name ?: "Задание ${quest.number}", boss?.name.orEmpty(), boss?.element,
                    def?.expansion ?: Expansion.ofQuest(quest.number), null)
            }
        )
    }

    fun onQuestSelected(number: Int?) = app.navigate(Screen.CampaignBattleSetup(localId, number))
}

// endregion

// region Инфо

data class InfoState(
    val sections: List<Pair<InfoSectionCode, Int>> = emptyList(),
    val section: InfoSectionCode? = null,
    val entries: List<InfoEntry> = emptyList(),
    val query: String = "",
    val results: List<Pair<InfoEntry, String>> = emptyList(),
    val filter: String = "",
    val expanded: Set<Long> = emptySet(),
    /** Статья, к которой нужно прокрутить (ссылка «См. также» или результат поиска). */
    val focus: Long? = null,
    val fromServer: Boolean = false
)

/**
 * «Инфо»: разделы «Ключевые слова», «Символы реакций монстров», «Жетоны окружения», поиск по всем разделам и ссылки
 * «См. также». Без сети — последняя загруженная версия.
 */
class InfoViewModel(
    private val app: AppViewModel,
    initialSection: InfoSectionCode?,
    initialEntry: Long?,
    private val scope: CoroutineScope
) {
    private val core = app.core
    private val _state = MutableStateFlow(InfoState(section = initialSection, focus = initialEntry,
        expanded = setOfNotNull(initialEntry)))
    val state: StateFlow<InfoState> = _state.asStateFlow()

    private var data: InfoData? = null
    private var resolve: (String) -> InfoEntry? = { null }

    init {
        scope.launch {
            core.info.info.collect { info ->
                data = info
                resolve = InfoText.resolver(info?.entries.orEmpty())
                rebuild()
            }
        }
    }

    private fun rebuild() = _state.update { state ->
        val info = data
        val section = state.section
        val entries = section?.let { code -> info?.section(code)?.entries }.orEmpty()
            .filter { state.filter.isBlank() || InfoText.matches(it, state.filter) }
        state.copy(
            sections = InfoSectionCode.entries.map { code -> code to (info?.section(code)?.entries?.size ?: 0) },
            entries = entries,
            results = if (state.query.isBlank()) emptyList() else info?.entries.orEmpty()
                .filter { InfoText.matches(it, state.query) }
                .map { it to "${InfoText.label(it)} · ${it.section.title}" },
            fromServer = core.info.source == ReferenceSource.SERVER
        )
    }

    fun onQuery(text: String) {
        _state.update { it.copy(query = text) }
        rebuild()
    }

    fun onFilter(text: String) {
        _state.update { it.copy(filter = text) }
        rebuild()
    }

    fun onSection(code: InfoSectionCode?) {
        _state.update { it.copy(section = code, filter = "", focus = null) }
        rebuild()
    }

    fun onToggle(entry: InfoEntry) = _state.update { state ->
        state.copy(expanded = if (entry.id in state.expanded) state.expanded - entry.id else state.expanded + entry.id, focus = null)
    }

    /** Ссылка «См. также» или результат поиска: раздел статьи, статья раскрыта и прокручена к началу. */
    fun onOpenEntry(entry: InfoEntry) {
        _state.update { it.copy(section = entry.section, query = "", filter = "", expanded = it.expanded + entry.id, focus = entry.id) }
        rebuild()
    }

    fun onFocusHandled() = _state.update { it.copy(focus = null) }

    fun resolver(): (String) -> InfoEntry? = resolve

    /** Картинка статьи: из файла приложения, кэша или (онлайн) с сервера. */
    suspend fun image(entry: InfoEntry): ByteArray? {
        val key = entry.imageKey ?: return null
        val online = core.settings.current.online
        return core.info.image(key, if (online) { path -> runCatching { core.api.infoImage(path) }.getOrNull() } else null)
    }

    /** «Назад»: из раздела — к списку разделов, иначе — откуда пришли. */
    fun onBack(): Boolean {
        if (_state.value.section != null) {
            onSection(null)
            return true
        }
        return false
    }
}

// endregion

// region Настройки и вход

enum class AuthMode { LOGIN, REGISTER }

data class SettingsState(
    val mode: ConnectionMode = ConnectionMode.ONLINE,
    val serverInput: String = PrimalApi.DEFAULT_SERVER,
    val server: String = PrimalApi.DEFAULT_SERVER,
    val accountName: String? = null,
    val accountLogin: String? = null,
    val isUser: Boolean = false,
    val isGuest: Boolean = false,
    val displayNameInput: String = "",
    val expansions: Set<Expansion> = Expansion.entries.toSet(),
    val syncText: String = "",
    val lastSync: String? = null,
    val catalogSource: String = "",
    val authMode: AuthMode = AuthMode.LOGIN,
    val login: String = "",
    val password: String = "",
    val password2: String = "",
    val newDisplayName: String = "",
    val busy: Boolean = false,
    val error: String? = null
)

/**
 * Настройки: режим работы (онлайн/офлайн), адрес сервера (по умолчанию https://primalhelper.ru/), вход и регистрация,
 * имя, дополнения, синхронизация.
 */
class SettingsViewModel(private val app: AppViewModel, private val scope: CoroutineScope) {

    private val core = app.core
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(core.settings.settings, core.sync.status) { settings, status -> settings to status }.collect { (settings, status) ->
                val account = settings.activeAccount
                _state.update { state ->
                    state.copy(
                        mode = settings.mode,
                        server = settings.serverUrl,
                        serverInput = if (state.serverInput == state.server) settings.serverUrl else state.serverInput,
                        accountName = account?.name,
                        accountLogin = account?.login,
                        isUser = account?.isUser == true,
                        isGuest = account != null && !account.isUser,
                        displayNameInput = if (state.displayNameInput.isEmpty()) account?.displayName.orEmpty() else state.displayNameInput,
                        expansions = settings.expansions,
                        syncText = when (status) {
                            SyncStatus.OfflineMode -> "Офлайн-режим: справочники и кампании — на устройстве."
                            SyncStatus.Idle -> if (settings.mode == ConnectionMode.OFFLINE) "Офлайн-режим." else "Синхронизировано."
                            SyncStatus.Syncing -> "Синхронизация…"
                            is SyncStatus.Unreachable -> "Нет связи с сервером: изменения сохранены на устройстве и уйдут позже."
                            is SyncStatus.Failed -> "Ошибка синхронизации: ${status.message}"
                        },
                        lastSync = settings.lastSync?.let { dateTime(it) },
                        catalogSource = if (core.catalog.source == ReferenceSource.SERVER) "Справочники — с сервера." else "Справочники — встроенные в приложение."
                    )
                }
            }
        }
    }

    fun onModeChanged(online: Boolean) {
        scope.launch { core.setMode(if (online) ConnectionMode.ONLINE else ConnectionMode.OFFLINE) }
    }

    fun onServerInput(text: String) = _state.update { it.copy(serverInput = text, error = null) }

    /** «Сохранить адрес»: вход на прежнем сервере не действует; кампании остаются на устройстве. */
    fun onSaveServer() {
        val input = _state.value.serverInput.trim()
        if (input.isEmpty()) {
            _state.update { it.copy(error = "Укажите адрес сервера.") }
            return
        }
        scope.launch { applyServer(input) }
    }

    fun onDefaultServer() = _state.update { it.copy(serverInput = PrimalApi.DEFAULT_SERVER) }

    private suspend fun applyServer(input: String) {
        core.setServer(input)
        _state.update { it.copy(serverInput = core.settings.current.serverUrl) }
        app.showMessage("Сервер: ${core.settings.current.serverUrl}")
    }

    fun onSyncNow() {
        if (!core.settings.current.online) {
            app.showMessage("Офлайн-режим: включите онлайн-режим, чтобы синхронизироваться.")
            return
        }
        scope.launch { core.sync.sync(references = true) }
    }

    // region Вход

    fun onAuthMode(mode: AuthMode) = _state.update { it.copy(authMode = mode, error = null) }
    fun onLogin(text: String) = _state.update { it.copy(login = text.trim().take(32), error = null) }
    fun onPassword(text: String) = _state.update { it.copy(password = text.take(64), error = null) }
    fun onPassword2(text: String) = _state.update { it.copy(password2 = text.take(64), error = null) }
    fun onNewDisplayName(text: String) = _state.update { it.copy(newDisplayName = text.take(60), error = null) }

    fun onSubmitAuth() {
        val state = _state.value
        if (!core.settings.current.online) {
            _state.update { it.copy(error = "Вход доступен в онлайн-режиме.") }
            return
        }
        val error = when {
            state.login.isBlank() || state.password.isBlank() -> "Заполните логин и пароль."
            state.authMode == AuthMode.REGISTER && !Regex("[a-zA-Z0-9._-]{3,32}").matches(state.login) ->
                "Логин — латиница, цифры, «.», «_», «-», от 3 до 32 символов."
            state.authMode == AuthMode.REGISTER && state.password.length < 8 -> "Пароль — от 8 до 64 символов."
            state.authMode == AuthMode.REGISTER && state.password != state.password2 -> "Пароли не совпадают."
            state.authMode == AuthMode.REGISTER && state.newDisplayName.isBlank() -> "Укажите имя — его видят участники кампаний."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                if (state.authMode == AuthMode.LOGIN) {
                    core.login(state.login, state.password)
                } else {
                    core.register(state.login, state.password, state.newDisplayName)
                }
                _state.update { it.copy(busy = false, password = "", password2 = "", displayNameInput = core.settings.current.activeAccount?.displayName.orEmpty()) }
                app.showMessage("Вход выполнен: ${core.settings.current.activeAccount?.name}")
            } catch (exception: Exception) {
                val message = if (exception is ApiException && exception.code == "INVALID_CREDENTIALS") "Неверный логин или пароль."
                else if (exception is ApiException && exception.code == "LOGIN_TAKEN") "Логин ${state.login} уже зарегистрирован. Войдите по нему."
                else errorText(exception)
                _state.update { it.copy(busy = false, error = message) }
            }
        }
    }

    /** «Выйти»: кампании остаются на устройстве, изменения уйдут на сервер после входа в этот же аккаунт. */
    fun onLogout() {
        scope.launch {
            val unsynced = core.unsyncedCampaigns().isNotEmpty()
            core.logout()
            app.showMessage(
                if (unsynced) "Вы вышли из аккаунта. Кампании сохранены; изменения уйдут на сервер после входа в этот аккаунт."
                else "Вы вышли из аккаунта. Кампании сохранены на устройстве."
            )
        }
    }

    // endregion

    fun onDisplayNameInput(text: String) = _state.update { it.copy(displayNameInput = text.take(60)) }

    fun onSaveDisplayName() {
        scope.launch {
            try {
                core.rename(_state.value.displayNameInput)
                app.showMessage("Имя сохранено.")
            } catch (exception: Exception) {
                app.showMessage(errorText(exception))
            }
        }
    }

    /** Дополнения: у аккаунта сохраняются на сервере, иначе — в приложении. */
    fun onExpansionToggled(expansion: Expansion) {
        val current = _state.value.expansions
        val next = if (expansion in current) current - expansion else current + expansion
        scope.launch {
            try {
                core.setExpansions(next)
                app.showMessage("Дополнения сохранены.")
            } catch (exception: Exception) {
                app.showMessage(errorText(exception))
            }
        }
    }

    private fun dateTime(millis: Long): String {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.dayOfMonth.toString().padStart(2, '0')}.${local.monthNumber.toString().padStart(2, '0')}.${local.year} " +
            "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }
}

// endregion

// region Приглашение

data class JoinState(
    val loading: Boolean = true,
    val campaignName: String? = null,
    val ownerName: String? = null,
    val needsName: Boolean = true,
    val displayName: String = "",
    val error: String? = null,
    val busy: Boolean = false
)

/** Ссылка-приглашение: «Вас пригласили в кампанию «…»», имя гостя и «Присоединиться». Нужен онлайн-режим. */
class JoinViewModel(private val app: AppViewModel, private val token: String, private val scope: CoroutineScope) {

    private val core = app.core
    private val _state = MutableStateFlow(JoinState(needsName = core.settings.current.activeAccount?.isUser != true))
    val state: StateFlow<JoinState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (!core.settings.current.online) {
            _state.update { it.copy(loading = false, error = "Приглашение открывается в онлайн-режиме: включите его в настройках.") }
            return
        }
        scope.launch {
            try {
                val info = core.shareInfo(token)
                _state.update { it.copy(loading = false, campaignName = info.name, ownerName = info.ownerName, error = null) }
            } catch (exception: Exception) {
                val message = if (exception is ApiException && exception.status == 404) {
                    "Ссылка недействительна: её отозвали или перевыпустили. Попросите владельца кампании прислать новую."
                } else {
                    errorText(exception)
                }
                _state.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun onName(text: String) = _state.update { it.copy(displayName = text.take(60)) }

    fun onJoin() {
        _state.update { it.copy(busy = true) }
        scope.launch {
            try {
                val result = core.join(token, _state.value.displayName)
                val localId = core.localIdForServer(result.id)
                if (localId != null) {
                    app.replace(Screen.CampaignSheet(localId))
                } else {
                    app.replace(Screen.CampaignList)
                }
            } catch (exception: Exception) {
                _state.update { it.copy(busy = false, error = errorText(exception)) }
            }
        }
    }
}

// endregion
