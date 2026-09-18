package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.primalapp.database.entity.HunterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HunterDao {
    @Query("SELECT * FROM hunters WHERE campaign_id = :campaignId")
    fun getHunters(campaignId: Long): Flow<List<HunterEntity>>

    @Query("SELECT * FROM hunters WHERE campaign_id = :campaignId")
    suspend fun getHuntersList(campaignId: Long): List<HunterEntity>

    @Query("SELECT * FROM hunters WHERE id = :id")
    suspend fun getHunter(id: Long): HunterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHunter(hunter: HunterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHunters(hunters: List<HunterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHunterReturningId(hunter: HunterEntity): Long

    @Update
    suspend fun updateHunter(hunter: HunterEntity)

    @Delete
    suspend fun deleteHunter(hunter: HunterEntity)

    @Query("DELETE FROM hunters WHERE campaign_id = :campaignId")
    suspend fun deleteHuntersByCampaign(campaignId: Long)
}
