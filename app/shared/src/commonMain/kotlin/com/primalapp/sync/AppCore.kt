package com.primalapp.sync

import com.primalapp.database.PlatformContext
import com.primalapp.database.PrimalDatabase
import com.primalapp.database.createPrimalDatabase
import com.primalapp.model.campaign.Expansion
import com.primalapp.net.ApiException
import com.primalapp.net.CookieJar
import com.primalapp.net.JoinResultDto
import com.primalapp.net.PrimalApi
import com.primalapp.net.ShareInfoDto
import com.primalapp.net.createHttpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Корень приложения: БД, настройки, справочники, кампании, сервер и синхронизация. Создаётся один раз на процесс;
 * [start] загружает настройки и справочники (из кэша или файлов приложения) и запускает синхронизацию.
 */
class AppCore(
    context: PlatformContext,
    val database: PrimalDatabase = createPrimalDatabase(context),
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val settings = SettingsStore(database.keyValueDao())
    val catalog = CatalogRepository(context, database.cacheBlobDao())
    val info = InfoRepository(context, database.cacheBlobDao())
    val store = CampaignStore(database.campaignStoreDao(), { catalog.current }, { settings.current.expansions })

    private val cookies = CookieJar(emptyList()) { saved -> scope.launch { settings.saveCookies(saved) } }
    private var cookieJarLoaded = false
    val api = PrimalApi(createHttpEngine(), { settings.current.serverUrl }, cookies)

    val sync = SyncEngine(api, settings, store, catalog, info, scope, onSignedOut = { forgetAccount() })

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var periodic: Job? = null

    suspend fun start() {
        if (_ready.value) return
        settings.load()
        if (!cookieJarLoaded) {
            settings.cookies().let { saved -> if (saved.isNotEmpty()) cookies.receiveStored(saved) }
            cookieJarLoaded = true
        }
        catalog.load()
        info.load()
        _ready.value = true
        startPeriodicSync()
    }

    /** Онлайн — синхронизация сразу и затем раз в минуту; изменения кампаний уходят сразу после действия. */
    private fun startPeriodicSync() {
        periodic?.cancel()
        periodic = scope.launch {
            while (isActive) {
                if (settings.current.online) sync.sync(references = true)
                delay(PERIODIC_SYNC_MILLIS)
            }
        }
    }

    /** Приложение снова на экране — сразу синхронизироваться. */
    fun onResume() {
        if (_ready.value && settings.current.online) sync.requestSync()
    }

    // region Настройки

    suspend fun setMode(mode: ConnectionMode) {
        settings.setMode(mode)
        if (mode == ConnectionMode.ONLINE) sync.requestSync() else sync.sync()
    }

    /**
     * Другой сервер: вход на прежнем сервере больше не действует. Копии его кампаний остаются на устройстве и
     * синхронизируются снова после входа в тот же аккаунт на том сервере.
     */
    suspend fun setServer(url: String) {
        val normalized = PrimalApi.normalizeServerUrl(url)
        if (normalized == PrimalApi.normalizeServerUrl(settings.current.serverUrl)) return
        forgetAccount()
        settings.setServer(normalized)
        catalog.load()
        info.load()
        sync.requestSync()
    }

    suspend fun setExpansions(expansions: Set<Expansion>) {
        val account = settings.current.activeAccount
        if (account != null && account.isUser && settings.current.online) {
            val me = api.setExpansions(expansions.map { it.name })
            settings.setAccount(SyncEngine.accountInfo(account.server, me))
        }
        settings.setLocalExpansions(expansions)
    }

    suspend fun rename(displayName: String) {
        val account = settings.current.activeAccount ?: return
        val me = api.rename(displayName.trim())
        settings.setAccount(SyncEngine.accountInfo(account.server, me))
    }

    // endregion

    // region Вход

    suspend fun login(login: String, password: String) {
        val previous = settings.current.activeAccount
        api.login(login.trim(), password)
        signedIn(previous, SyncEngine.accountInfo(settings.current.serverUrl, api.me()))
    }

    suspend fun register(login: String, password: String, displayName: String) {
        val previous = settings.current.activeAccount
        api.register(login.trim(), password, displayName.trim())
        signedIn(previous, SyncEngine.accountInfo(settings.current.serverUrl, api.me()))
    }

    /**
     * Вошли. Гость по ссылке, вошедший в аккаунт, передаёт ему свои доступы (сервер переносит их на аккаунт) — копии
     * гостя переходят к аккаунту вместе с неотправленными изменениями. Копии другого аккаунта остаются как есть.
     */
    private suspend fun signedIn(previous: AccountInfo?, account: AccountInfo) {
        if (previous != null && !previous.isUser && previous.key != account.key) store.reassign(previous.key, account.key)
        settings.setAccount(account)
        sync.sync(references = false)
    }

    /**
     * Выйти: устройство на сервере отзывается. Кампании остаются на устройстве и работают без входа; изменения копий
     * с сервера уйдут на сервер после входа в тот же аккаунт.
     */
    suspend fun logout() {
        runCatching { api.logout() }
        forgetAccount()
    }

    /** Кампании аккаунта с изменениями, ещё не отправленными на сервер. */
    suspend fun unsyncedCampaigns(): List<StoredCampaign> {
        val key = settings.current.activeAccount?.key ?: return emptyList()
        return store.all().filter { it.account == key && it.onServer && it.hasUnsynced }
    }

    /** Вход больше не действует (выход, другой сервер, отзыв устройства): кампании на устройстве сохраняются. */
    private suspend fun forgetAccount() {
        settings.setAccount(null)
        cookies.clear()
    }

    // endregion

    // region Ссылки-приглашения

    suspend fun shareInfo(token: String): ShareInfoDto = api.shareInfo(token)

    /** Присоединиться по ссылке: без входа — гостем (имя — в истории боёв). Возвращает id кампании на сервере. */
    suspend fun join(token: String, displayName: String?): JoinResultDto {
        val result = api.join(token, displayName)
        if (settings.current.activeAccount == null) {
            settings.setAccount(SyncEngine.accountInfo(settings.current.serverUrl, api.me()))
        }
        sync.sync(references = false)
        return result
    }

    /** Копия кампании с сервера по её id (после перехода по ссылке). */
    suspend fun localIdForServer(serverId: Long): Long? {
        val key = settings.current.activeAccount?.key ?: return null
        return store.all().firstOrNull { it.account == key && it.serverId == serverId }?.localId
    }

    // endregion

    companion object {
        const val PERIODIC_SYNC_MILLIS = 60_000L

        fun isNotFound(error: Throwable): Boolean = error is ApiException && error.status == 404
    }
}
