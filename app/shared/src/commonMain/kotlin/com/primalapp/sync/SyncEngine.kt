package com.primalapp.sync

import com.primalapp.database.currentTimeMillis
import com.primalapp.database.entity.CampaignStoreEntity
import com.primalapp.net.ApiException
import com.primalapp.net.CampaignSnapshotDto
import com.primalapp.net.Fetched
import com.primalapp.net.MeDto
import com.primalapp.net.OfflineException
import com.primalapp.net.PrimalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Состояние связи с сервером для интерфейса. */
sealed interface SyncStatus {
    /** Офлайн-режим в настройках: сервер не используется. */
    data object OfflineMode : SyncStatus
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    /** Онлайн-режим, но сервер недоступен: изменения ждут отправки. */
    data class Unreachable(val message: String) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * Синхронизация с сервером (`app/doc/sync.md`): справочники по ETag, вход, кампании пользователя — снимками.
 * Изменения кампании отправляются снимком с версией, на которой они сделаны; если кампанию тем временем изменил
 * другой участник (`409`), приложение берёт свежий снимок, повторяет поверх него свой журнал действий и отправляет
 * снова. Кампании, созданные после входа, загружаются на сервер.
 */
class SyncEngine(
    private val api: PrimalApi,
    private val settings: SettingsStore,
    private val store: CampaignStore,
    private val catalog: CatalogRepository,
    private val info: InfoRepository,
    private val scope: CoroutineScope,
    private val onSignedOut: suspend () -> Unit = {}
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Сообщения для игрока: результат боя не принят, кампания удалена на сервере и т. п. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val syncMutex = Mutex()
    private var pushJob: Job? = null

    init {
        store.onLocalChange = { requestPush() }
    }

    /** Отправить изменения вскоре (несколько действий подряд уходят одним запросом). */
    fun requestPush(delayMillis: Long = 1_500) {
        if (!settings.current.online) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(delayMillis)
            sync(references = false)
        }
    }

    fun requestSync() {
        scope.launch { sync(references = true) }
    }

    /**
     * Полная синхронизация: справочники, «кто я», список кампаний (новые копируются, изменённые обновляются, свои
     * изменения отправляются), загрузка новых кампаний и удаления.
     */
    suspend fun sync(references: Boolean = true) {
        if (!settings.current.online) {
            _status.value = SyncStatus.OfflineMode
            return
        }
        syncMutex.withLock {
            _status.value = SyncStatus.Syncing
            try {
                if (references) {
                    runCatching { catalog.refresh(api) }.onFailure { if (it is OfflineException) throw it }
                    runCatching { info.refresh(api) }.onFailure { if (it is OfflineException) throw it }
                }
                syncCampaigns()
                settings.setLastSync(currentTimeMillis())
                _status.value = SyncStatus.Idle
            } catch (offline: OfflineException) {
                _status.value = SyncStatus.Unreachable(offline.message ?: "Сервер недоступен.")
            } catch (error: ApiException) {
                _status.value = SyncStatus.Failed(error.userMessage)
            } catch (error: Exception) {
                _status.value = SyncStatus.Failed(error.message ?: "Ошибка синхронизации.")
            }
        }
    }

    /** Одна кампания: свежий снимок с сервера и отправка своих изменений (по событию с сервера, открытию листа). */
    suspend fun syncCampaign(localId: Long) {
        if (!settings.current.online) return
        syncMutex.withLock {
            try {
                val entity = store.entity(localId) ?: return
                val serverId = entity.serverId ?: return
                syncOne(entity.account ?: return, serverId)
                _status.value = SyncStatus.Idle
            } catch (offline: OfflineException) {
                _status.value = SyncStatus.Unreachable(offline.message ?: "Сервер недоступен.")
            } catch (error: ApiException) {
                _status.value = SyncStatus.Failed(error.userMessage)
            }
        }
    }

    private suspend fun syncCampaigns() {
        val account = settings.current.activeAccount ?: return
        val me = try {
            api.me()
        } catch (error: ApiException) {
            if (error.status == 401) {
                _messages.tryEmit("Вход на сервере больше не действует — войдите снова.")
                onSignedOut()
                return
            }
            throw error
        }
        val refreshed = accountInfo(account.server, me)
        if (refreshed != account) settings.setAccount(refreshed)
        val key = refreshed.key

        val summaries = api.campaigns()
        val serverIds = summaries.map { it.id }.toSet()
        // Удаления, ожидающие отправки, и копии кампаний, которых на сервере больше нет (удалены или доступ закрыт)
        for (entity in store.allEntities().filter { it.account == key && it.serverId != null }) {
            val serverId = entity.serverId ?: continue
            if (entity.pendingDelete) {
                if (serverId in serverIds) {
                    try {
                        api.deleteCampaign(serverId)
                    } catch (error: ApiException) {
                        if (error.status != 404) throw error
                    }
                }
                store.delete(entity.localId)
            } else if (serverId !in serverIds) {
                store.delete(entity.localId)
                _messages.tryEmit("Кампания «${store.decode(entity).state.name}» больше недоступна: её удалили или закрыли доступ.")
            }
        }
        for (summary in summaries) {
            syncOne(key, summary.id)
        }
        // Кампании, созданные в приложении, — на сервер (только у пользователя с аккаунтом)
        if (refreshed.isUser) {
            // Только кампании этого аккаунта: созданные под другим входом ждут своего аккаунта
            for (entity in store.allEntities().filter {
                it.serverId == null && it.upload && !it.pendingDelete && (it.account == null || it.account == key)
            }) {
                upload(entity, key)
            }
        }
        for (entity in store.allEntities().filter { it.serverId == null && it.pendingDelete }) {
            store.delete(entity.localId)
        }
    }

    /** Копия кампании с сервера: новая — создаётся, без своих изменений — обновляется, с изменениями — отправляется. */
    private suspend fun syncOne(account: String, serverId: Long) {
        val entity = store.entityByServerId(account, serverId)
        if (entity == null) {
            val fetched = api.snapshot(serverId, null) as? Fetched.Body ?: return
            store.mutex.withLock {
                if (store.entityByServerId(account, serverId) == null) {
                    val now = currentTimeMillis()
                    store.insertEntity(fromSnapshot(fetched.value, account, now).copy(createdAt = now))
                }
            }
            return
        }
        if (entity.pendingDelete) return
        val ops = store.decodeOps(entity.pendingOps)
        if (ops.isEmpty()) {
            val fetched = try {
                api.snapshot(serverId, entity.baseVersion)
            } catch (error: ApiException) {
                if (error.status == 404) return removeLost(entity)
                throw error
            }
            if (fetched is Fetched.Body) {
                store.mutex.withLock {
                    val current = store.entity(entity.localId) ?: return
                    if (store.decodeOps(current.pendingOps).isEmpty()) {
                        store.updateEntity(fromSnapshot(fetched.value, account, current.updatedAt).copy(
                            localId = current.localId, createdAt = current.createdAt, syncMessage = current.syncMessage))
                    }
                }
            }
            return
        }
        push(entity, account)
    }

    /**
     * Отправка изменений: снимок с версией, на которой они сделаны. Конфликт версий — свежий снимок, повтор журнала
     * поверх него и новая попытка. Действия, сделанные во время отправки, остаются в журнале.
     */
    private suspend fun push(initial: CampaignStoreEntity, account: String) {
        var entity = initial
        repeat(MAX_ATTEMPTS) {
            val serverId = entity.serverId ?: return
            val ops = store.decodeOps(entity.pendingOps)
            if (ops.isEmpty()) return
            val state = store.decodeState(entity.stateJson)
            try {
                val saved = api.putSnapshot(serverId, entity.baseVersion ?: 0, SnapshotMapper.toDto(state))
                store.mutex.withLock {
                    val current = store.entity(entity.localId) ?: return
                    val remaining = store.decodeOps(current.pendingOps).drop(ops.size)
                    val fresh = fromSnapshot(saved, account, currentTimeMillis())
                    val (replayed, kept, messages) = store.replay(SnapshotMapper.toState(saved.state), remaining, expansionsOf(saved))
                    messages.forEach { _messages.tryEmit(it) }
                    store.updateEntity(fresh.copy(
                        localId = current.localId,
                        createdAt = current.createdAt,
                        stateJson = store.encodeState(replayed),
                        pendingOps = store.encodeOps(kept)
                    ))
                    if (kept.isEmpty()) return
                    entity = store.entity(current.localId) ?: return
                }
                return@repeat
            } catch (error: ApiException) {
                when {
                    error.status == 409 -> {
                        val fetched = api.snapshot(serverId, null) as? Fetched.Body ?: return
                        rebase(entity, fetched.value, account)
                        entity = store.entity(entity.localId) ?: return
                    }
                    error.status == 404 -> return removeLost(entity)
                    error.status == 400 || error.status == 422 -> {
                        // Сервер не принял состояние: журнал не отправить — берём версию сервера, сообщаем игроку
                        val fetched = api.snapshot(serverId, null) as? Fetched.Body ?: return
                        store.mutex.withLock {
                            val current = store.entity(entity.localId) ?: return
                            store.updateEntity(fromSnapshot(fetched.value, account, currentTimeMillis()).copy(
                                localId = current.localId, createdAt = current.createdAt,
                                syncMessage = "Сервер не принял изменения: ${error.userMessage}"))
                        }
                        _messages.tryEmit("Сервер не принял изменения кампании: ${error.userMessage}")
                        return
                    }
                    else -> throw error
                }
            }
        }
    }

    /** Свежий снимок сервера и повтор журнала поверх него. */
    private suspend fun rebase(entity: CampaignStoreEntity, snapshot: CampaignSnapshotDto, account: String) {
        store.mutex.withLock {
            val current = store.entity(entity.localId) ?: return
            val ops = store.decodeOps(current.pendingOps)
            val (state, kept, messages) = store.replay(SnapshotMapper.toState(snapshot.state), ops, expansionsOf(snapshot))
            messages.forEach { _messages.tryEmit(it) }
            store.updateEntity(fromSnapshot(snapshot, account, currentTimeMillis()).copy(
                localId = current.localId,
                createdAt = current.createdAt,
                stateJson = store.encodeState(state),
                pendingOps = store.encodeOps(kept),
                syncMessage = messages.lastOrNull()
            ))
        }
    }

    /** Кампания, созданная в приложении, — на сервер; изменения, сделанные во время загрузки, отправляются следом. */
    private suspend fun upload(entity: CampaignStoreEntity, account: String) {
        val state = store.decodeState(entity.stateJson)
        val saved = try {
            api.importCampaign(SnapshotMapper.toDto(state))
        } catch (error: ApiException) {
            if (error.status in 400..499) {
                store.setSyncMessage(entity.localId, "Не загружена на сервер: ${error.userMessage}")
                return
            }
            throw error
        }
        val changedMeanwhile = store.mutex.withLock {
            val current = store.entity(entity.localId) ?: return
            val latest = current.stateJson
            store.updateEntity(fromSnapshot(saved, account, currentTimeMillis()).copy(
                localId = current.localId, createdAt = current.createdAt, upload = false))
            if (latest != entity.stateJson) latest else null
        }
        if (changedMeanwhile != null) {
            // Кампанию только что создали — других участников нет: отправляется последнее состояние приложения
            val pushed = api.putSnapshot(saved.id, saved.version, SnapshotMapper.toDto(store.decodeState(changedMeanwhile)))
            store.mutex.withLock {
                val current = store.entity(entity.localId) ?: return
                store.updateEntity(fromSnapshot(pushed, account, currentTimeMillis()).copy(
                    localId = current.localId, createdAt = current.createdAt))
            }
        }
    }

    private suspend fun removeLost(entity: CampaignStoreEntity) {
        store.delete(entity.localId)
        _messages.tryEmit("Кампания «${store.decode(entity).state.name}» больше недоступна: её удалили или закрыли доступ.")
    }

    private fun fromSnapshot(snapshot: CampaignSnapshotDto, account: String, now: Long): CampaignStoreEntity {
        val state = SnapshotMapper.toState(snapshot.state)
        return CampaignStoreEntity(
            serverId = snapshot.id,
            account = account,
            access = if (snapshot.access == "OWNER") CampaignAccess.OWNER.name else CampaignAccess.LINK.name,
            ownerName = snapshot.ownerName,
            ownerExpansions = snapshot.ownerExpansions.joinToString(","),
            baseVersion = snapshot.version,
            baseJson = store.encodeState(state),
            stateJson = store.encodeState(state),
            pendingOps = "[]",
            pendingDelete = false,
            upload = false,
            syncMessage = null,
            createdAt = SnapshotMapper.millis(snapshot.createdAt) ?: now,
            updatedAt = SnapshotMapper.millis(snapshot.updatedAt) ?: now
        )
    }

    private fun expansionsOf(snapshot: CampaignSnapshotDto) = SnapshotMapper.expansions(snapshot.ownerExpansions)

    companion object {
        private const val MAX_ATTEMPTS = 3

        fun accountInfo(server: String, me: MeDto): AccountInfo = AccountInfo(
            server = PrimalApi.normalizeServerUrl(server),
            kind = if (me.user != null) "USER" else "GUEST",
            userId = me.user?.id,
            login = me.user?.login,
            displayName = me.user?.displayName ?: me.device?.displayName,
            deviceId = me.device?.id,
            expansions = me.user?.expansions
        )
    }
}
