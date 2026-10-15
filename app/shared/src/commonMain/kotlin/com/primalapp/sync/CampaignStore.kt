package com.primalapp.sync

import com.primalapp.campaign.CampaignChangedException
import com.primalapp.campaign.CampaignOp
import com.primalapp.campaign.CampaignRuleException
import com.primalapp.campaign.CampaignRules
import com.primalapp.campaign.CampaignState
import com.primalapp.campaign.RuleContext
import com.primalapp.campaign.newLocalKey
import com.primalapp.catalog.Catalog
import com.primalapp.database.currentTimeMillis
import com.primalapp.database.dao.CampaignStoreDao
import com.primalapp.database.entity.CampaignStoreEntity
import com.primalapp.model.campaign.Expansion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Где кампания: только на устройстве, своя на сервере или открыта по ссылке-приглашению. */
enum class CampaignAccess { LOCAL, OWNER, LINK }

/** Кампания на устройстве с состоянием синхронизации. */
data class StoredCampaign(
    val localId: Long,
    val serverId: Long?,
    val account: String?,
    val access: CampaignAccess,
    val ownerName: String?,
    val ownerExpansions: Set<Expansion>?,
    val baseVersion: Int?,
    val state: CampaignState,
    val pendingOps: List<CampaignOp>,
    val pendingDelete: Boolean,
    val upload: Boolean,
    val syncMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    val onServer: Boolean get() = serverId != null

    /** Есть изменения, которых ещё нет на сервере. */
    val hasUnsynced: Boolean get() = pendingOps.isNotEmpty() || (serverId == null && upload) || pendingDelete
}

/**
 * Кампании на устройстве: документы JSON в БД. Каждое действие игрока выполняется по правилам ([CampaignRules]) над
 * текущим состоянием; у кампаний с сервера действие ещё и записывается в журнал — синхронизация отправит его
 * ([SyncEngine]). Записи кампаний идут по одной ([mutex]): действие игрока и ответ сервера не перетирают друг друга.
 */
class CampaignStore(
    private val dao: CampaignStoreDao,
    private val catalog: () -> Catalog,
    private val playerExpansions: () -> Set<Expansion>
) {
    val mutex = Mutex()

    /** Изменение кампании, которое нужно отправить на сервер. */
    var onLocalChange: (Long) -> Unit = {}

    private var rulesCache: Pair<String, CampaignRules>? = null

    val rules: CampaignRules
        get() {
            val current = catalog()
            rulesCache?.let { (version, rules) -> if (version == current.version) return rules }
            return CampaignRules(current).also { rulesCache = current.version to it }
        }

    fun observeAll(): Flow<List<StoredCampaign>> = dao.observeAll().map { list -> list.mapNotNull { decodeOrNull(it) } }

    fun observe(localId: Long): Flow<StoredCampaign?> = dao.observe(localId).map { entity -> entity?.let { decodeOrNull(it) } }

    suspend fun get(localId: Long): StoredCampaign? = dao.get(localId)?.let { decodeOrNull(it) }

    suspend fun all(): List<StoredCampaign> = dao.all().mapNotNull { decodeOrNull(it) }

    /** Условия правил для кампании: дополнения владельца (с сервера) или игрока. */
    fun context(campaign: StoredCampaign?, actor: com.primalapp.campaign.Actor?, now: Long = currentTimeMillis()): RuleContext =
        RuleContext(campaign?.ownerExpansions ?: playerExpansions(), now, actor, ::newLocalKey)

    /** Новая кампания на устройстве; [upload] — загрузить на сервер при синхронизации. */
    suspend fun create(state: CampaignState, upload: Boolean, account: String?): Long = mutex.withLock {
        val now = currentTimeMillis()
        dao.insert(
            CampaignStoreEntity(
                access = CampaignAccess.LOCAL.name,
                account = account.takeIf { upload },
                stateJson = encodeState(state),
                upload = upload,
                createdAt = now,
                updatedAt = now
            )
        )
    }.also { onLocalChange(it) }

    /**
     * Действие игрока: выполняется сразу; у кампании с сервера — ещё и в журнал для отправки. Недопустимое
     * действие — [CampaignRuleException], кампания не меняется.
     */
    suspend fun perform(localId: Long, op: CampaignOp): CampaignState {
        val result = mutex.withLock {
            val entity = dao.get(localId) ?: throw CampaignRuleException("Кампания не найдена.")
            val stored = decode(entity)
            val next = op.apply(rules, stored.state, context(stored, op.actor, op.at))
            val ops = if (stored.serverId != null) stored.pendingOps + op else emptyList()
            dao.update(entity.copy(stateJson = encodeState(next), pendingOps = encodeOps(ops), updatedAt = currentTimeMillis(), syncMessage = null))
            next
        }
        onLocalChange(localId)
        return result
    }

    /** Загрузить кампанию на сервер при следующей синхронизации (нужен вход). */
    suspend fun requestUpload(localId: Long, account: String) = mutex.withLock {
        dao.get(localId)?.let { dao.update(it.copy(upload = true, account = account, syncMessage = null)) }
    }.also { onLocalChange(localId) }

    suspend fun setSyncMessage(localId: Long, message: String?) = mutex.withLock {
        dao.get(localId)?.let { dao.update(it.copy(syncMessage = message)) }
    }

    suspend fun delete(localId: Long) = mutex.withLock { dao.delete(localId) }

    /** Копии одного входа — другому (гость вошёл в аккаунт: его доступы переходят к аккаунту). */
    suspend fun reassign(fromAccount: String, toAccount: String) = mutex.withLock {
        dao.all().filter { it.account == fromAccount }.forEach { entity ->
            val clash = entity.serverId?.let { dao.byServerId(toAccount, it) }
            if (clash == null) dao.update(entity.copy(account = toAccount)) else dao.delete(entity.localId)
        }
    }

    suspend fun markDeleted(localId: Long) = mutex.withLock {
        dao.get(localId)?.let { dao.update(it.copy(pendingDelete = true)) }
    }.also { onLocalChange(localId) }

    // region Для синхронизации

    internal suspend fun entity(localId: Long): CampaignStoreEntity? = dao.get(localId)

    internal suspend fun entityByServerId(account: String, serverId: Long): CampaignStoreEntity? = dao.byServerId(account, serverId)

    internal suspend fun insertEntity(entity: CampaignStoreEntity): Long = dao.insert(entity)

    internal suspend fun updateEntity(entity: CampaignStoreEntity) = dao.update(entity)

    internal suspend fun allEntities(): List<CampaignStoreEntity> = dao.all()

    /**
     * Повтор журнала поверх нового снимка сервера. Итог боя, который больше не согласуется с кампанией, записывается
     * отклонённым; остальные недопустимые действия отбрасываются. Возвращает состояние, оставшийся журнал и сообщения.
     */
    fun replay(base: CampaignState, ops: List<CampaignOp>, ownerExpansions: Set<Expansion>?): Triple<CampaignState, List<CampaignOp>, List<String>> {
        var state = base
        val kept = mutableListOf<CampaignOp>()
        val messages = mutableListOf<String>()
        for (op in ops) {
            val ctx = RuleContext(ownerExpansions ?: playerExpansions(), op.at, op.actor, ::newLocalKey)
            try {
                state = op.apply(rules, state, ctx)
                kept += op
            } catch (changed: CampaignChangedException) {
                if (op is CampaignOp.SubmitResult && op.accept) {
                    val dismissed = op.copy(accept = false, overrides = null)
                    runCatching { dismissed.apply(rules, state, ctx) }.onSuccess {
                        state = it
                        kept += dismissed
                    }
                    messages += "Результат боя не принят: ${changed.reasons.joinToString(" ")} Награды можно внести вручную."
                } else {
                    messages += "Изменение не применено: ${changed.message}"
                }
            } catch (rule: CampaignRuleException) {
                messages += "Изменение не применено: ${rule.message}"
            }
        }
        return Triple(state, kept, messages)
    }

    fun decode(entity: CampaignStoreEntity): StoredCampaign = StoredCampaign(
        localId = entity.localId,
        serverId = entity.serverId,
        account = entity.account,
        access = CampaignAccess.entries.firstOrNull { it.name == entity.access } ?: CampaignAccess.LOCAL,
        ownerName = entity.ownerName,
        ownerExpansions = entity.ownerExpansions?.let { value ->
            value.split(',').mapNotNull { code -> Expansion.entries.firstOrNull { it.name == code } }.toSet()
        },
        baseVersion = entity.baseVersion,
        state = decodeState(entity.stateJson),
        pendingOps = decodeOps(entity.pendingOps),
        pendingDelete = entity.pendingDelete,
        upload = entity.upload,
        syncMessage = entity.syncMessage,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    private fun decodeOrNull(entity: CampaignStoreEntity): StoredCampaign? = runCatching { decode(entity) }.getOrNull()

    fun encodeState(state: CampaignState): String = json.encodeToString(CampaignState.serializer(), state)

    fun decodeState(text: String): CampaignState = json.decodeFromString(CampaignState.serializer(), text)

    fun encodeOps(ops: List<CampaignOp>): String = json.encodeToString(opsSerializer, ops)

    fun decodeOps(text: String): List<CampaignOp> = runCatching { json.decodeFromString(opsSerializer, text) }.getOrDefault(emptyList())

    // endregion

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val opsSerializer = ListSerializer(CampaignOp.serializer())
    }
}
