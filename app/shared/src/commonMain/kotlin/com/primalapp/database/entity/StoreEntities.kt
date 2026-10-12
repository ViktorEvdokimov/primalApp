package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кампания на устройстве — документ JSON (`CampaignState`) и всё, что нужно для синхронизации с сервером.
 *
 * @param serverId идентификатор кампании на сервере; `null` — кампания только на устройстве
 * @param account сервер и учётная запись, которым принадлежит копия (`<адрес>|<id пользователя>`)
 * @param access LOCAL — только на устройстве, OWNER — своя на сервере, LINK — открыта по ссылке
 * @param baseVersion версия последнего полученного снимка сервера; [baseJson] — сам снимок
 * @param stateJson текущее состояние: снимок сервера и действия из [pendingOps] поверх него
 * @param pendingOps журнал действий, ещё не отправленных на сервер (JSON-массив `CampaignOp`)
 * @param pendingDelete удаление ждёт отправки на сервер
 * @param upload кампанию нужно загрузить на сервер (создана после входа или игрок попросил)
 * @param syncMessage последнее сообщение синхронизации для листа кампании
 */
@Entity(tableName = "campaign_store")
data class CampaignStoreEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id")
    val localId: Long = 0,
    @ColumnInfo(name = "server_id")
    val serverId: Long? = null,
    @ColumnInfo(name = "account")
    val account: String? = null,
    @ColumnInfo(name = "access")
    val access: String,
    @ColumnInfo(name = "owner_name")
    val ownerName: String? = null,
    @ColumnInfo(name = "owner_expansions")
    val ownerExpansions: String? = null,
    @ColumnInfo(name = "base_version")
    val baseVersion: Int? = null,
    @ColumnInfo(name = "base_json")
    val baseJson: String? = null,
    @ColumnInfo(name = "state_json")
    val stateJson: String,
    @ColumnInfo(name = "pending_ops")
    val pendingOps: String = "[]",
    @ColumnInfo(name = "pending_delete")
    val pendingDelete: Boolean = false,
    @ColumnInfo(name = "upload")
    val upload: Boolean = false,
    @ColumnInfo(name = "sync_message")
    val syncMessage: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

/** Настройки приложения: режим, адрес сервера, сессия, дополнения — ключ и значение (JSON или текст). */
@Entity(tableName = "kv_store")
data class KeyValueEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String
)

/** Кэш справочников с сервера: каталог, «Инфо», картинки «Инфо»; [etag] — для условного запроса. */
@Entity(tableName = "cache_blob")
data class CacheBlobEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "etag")
    val etag: String?,
    @ColumnInfo(name = "data")
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is CacheBlobEntity && other.key == key && other.etag == etag && other.data.contentEquals(data)

    override fun hashCode(): Int = key.hashCode() * 31 + data.contentHashCode()
}
