package com.primalapp.sync

import com.primalapp.campaign.Actor
import com.primalapp.campaign.ActorKind
import com.primalapp.database.dao.KeyValueDao
import com.primalapp.database.entity.KeyValueEntity
import com.primalapp.model.campaign.Expansion
import com.primalapp.net.PrimalApi
import com.primalapp.net.StoredCookie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Режим работы: онлайн — с сервером (справочники и кампании синхронизируются), офлайн — только устройство. */
enum class ConnectionMode { ONLINE, OFFLINE }

/**
 * Вход на сервере: пользователь (`USER`) или гость по ссылке-приглашению (`GUEST`).
 *
 * @param server адрес сервера, на котором выполнен вход
 */
@Serializable
data class AccountInfo(
    val server: String,
    val kind: String,
    val userId: Long? = null,
    val login: String? = null,
    val displayName: String? = null,
    val deviceId: String? = null,
    val expansions: List<String>? = null
) {
    val isUser: Boolean get() = kind == "USER" && userId != null

    /** Чьи копии кампаний на устройстве: сервер и пользователь (или гостевое устройство). */
    val key: String get() = if (isUser) "$server|u$userId" else "$server|d$deviceId"

    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: if (isUser) (login ?: "Игрок") else "Гость"

    fun actor(): Actor = Actor(if (isUser) ActorKind.USER else ActorKind.GUEST, name)
}

data class AppSettings(
    val mode: ConnectionMode = ConnectionMode.ONLINE,
    val serverUrl: String = PrimalApi.DEFAULT_SERVER,
    val account: AccountInfo? = null,
    val localExpansions: Set<Expansion> = Expansion.entries.toSet(),
    val lastSync: Long? = null
) {
    val online: Boolean get() = mode == ConnectionMode.ONLINE

    /** Вход относится к текущему серверу. */
    val activeAccount: AccountInfo?
        get() = account?.takeIf { PrimalApi.normalizeServerUrl(it.server) == PrimalApi.normalizeServerUrl(serverUrl) }

    /** Дополнения игрока: у аккаунта — с сервера, иначе — настройка приложения. */
    val expansions: Set<Expansion>
        get() = activeAccount?.takeIf { it.isUser }?.expansions
            ?.mapNotNull { code -> Expansion.entries.firstOrNull { it.name == code } }?.toSet()
            ?: localExpansions
}

/** Настройки приложения в БД (`kv_store`). */
class SettingsStore(private val dao: KeyValueDao) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    suspend fun load() {
        _settings.value = AppSettings(
            mode = dao.get(MODE)?.let { value -> ConnectionMode.entries.firstOrNull { it.name == value } } ?: ConnectionMode.ONLINE,
            serverUrl = dao.get(SERVER) ?: PrimalApi.DEFAULT_SERVER,
            account = dao.get(ACCOUNT)?.let { runCatching { json.decodeFromString(AccountInfo.serializer(), it) }.getOrNull() },
            localExpansions = dao.get(EXPANSIONS)?.let { value ->
                value.split(',').mapNotNull { code -> Expansion.entries.firstOrNull { it.name == code } }.toSet()
            } ?: Expansion.entries.toSet(),
            lastSync = dao.get(LAST_SYNC)?.toLongOrNull()
        )
    }

    suspend fun setMode(mode: ConnectionMode) {
        dao.put(KeyValueEntity(MODE, mode.name))
        _settings.update { it.copy(mode = mode) }
    }

    suspend fun setServer(url: String) {
        val normalized = PrimalApi.normalizeServerUrl(url) + "/"
        dao.put(KeyValueEntity(SERVER, normalized))
        _settings.update { it.copy(serverUrl = normalized) }
    }

    suspend fun setAccount(account: AccountInfo?) {
        if (account == null) dao.delete(ACCOUNT) else dao.put(KeyValueEntity(ACCOUNT, json.encodeToString(AccountInfo.serializer(), account)))
        _settings.update { it.copy(account = account) }
    }

    suspend fun setLocalExpansions(expansions: Set<Expansion>) {
        dao.put(KeyValueEntity(EXPANSIONS, expansions.joinToString(",") { it.name }))
        _settings.update { it.copy(localExpansions = expansions) }
    }

    suspend fun setLastSync(time: Long) {
        dao.put(KeyValueEntity(LAST_SYNC, time.toString()))
        _settings.update { it.copy(lastSync = time) }
    }

    suspend fun cookies(): List<StoredCookie> = dao.get(COOKIES)?.let {
        runCatching { json.decodeFromString(ListSerializer(StoredCookie.serializer()), it) }.getOrNull()
    }.orEmpty()

    suspend fun saveCookies(cookies: List<StoredCookie>) {
        dao.put(KeyValueEntity(COOKIES, json.encodeToString(ListSerializer(StoredCookie.serializer()), cookies)))
    }

    suspend fun getValue(key: String): String? = dao.get(key)

    suspend fun putValue(key: String, value: String?) {
        if (value == null) dao.delete(key) else dao.put(KeyValueEntity(key, value))
    }

    private companion object {
        const val MODE = "mode"
        const val SERVER = "server"
        const val ACCOUNT = "account"
        const val EXPANSIONS = "localExpansions"
        const val COOKIES = "cookies"
        const val LAST_SYNC = "lastSync"
    }
}
