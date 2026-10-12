package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.primalapp.database.entity.CacheBlobEntity
import com.primalapp.database.entity.CampaignStoreEntity
import com.primalapp.database.entity.KeyValueEntity
import kotlinx.coroutines.flow.Flow

/** Кампании на устройстве: документы JSON и состояние синхронизации. */
@Dao
interface CampaignStoreDao {

    @Query("SELECT * FROM campaign_store WHERE pending_delete = 0 ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<CampaignStoreEntity>>

    @Query("SELECT * FROM campaign_store ORDER BY updated_at DESC")
    suspend fun all(): List<CampaignStoreEntity>

    @Query("SELECT * FROM campaign_store WHERE local_id = :localId")
    fun observe(localId: Long): Flow<CampaignStoreEntity?>

    @Query("SELECT * FROM campaign_store WHERE local_id = :localId")
    suspend fun get(localId: Long): CampaignStoreEntity?

    @Query("SELECT * FROM campaign_store WHERE account = :account AND server_id = :serverId")
    suspend fun byServerId(account: String, serverId: Long): CampaignStoreEntity?

    @Insert
    suspend fun insert(entity: CampaignStoreEntity): Long

    @Update
    suspend fun update(entity: CampaignStoreEntity)

    @Query("DELETE FROM campaign_store WHERE local_id = :localId")
    suspend fun delete(localId: Long)
}

/** Настройки ключ — значение. */
@Dao
interface KeyValueDao {

    @Query("SELECT value FROM kv_store WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: KeyValueEntity)

    @Query("DELETE FROM kv_store WHERE `key` = :key")
    suspend fun delete(key: String)
}

/** Кэш справочников и картинок. */
@Dao
interface CacheBlobDao {

    @Query("SELECT * FROM cache_blob WHERE `key` = :key")
    suspend fun get(key: String): CacheBlobEntity?

    @Query("SELECT etag FROM cache_blob WHERE `key` = :key")
    suspend fun etag(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: CacheBlobEntity)

    @Query("SELECT `key` FROM cache_blob WHERE `key` LIKE :prefix || '%'")
    suspend fun keys(prefix: String): List<String>

    @Query("DELETE FROM cache_blob WHERE `key` = :key")
    suspend fun delete(key: String)
}
