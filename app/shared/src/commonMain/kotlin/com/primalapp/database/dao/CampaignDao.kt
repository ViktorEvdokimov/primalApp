package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.primalapp.database.entity.CampaignEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns ORDER BY updated_at DESC")
    fun getAllCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns ORDER BY updated_at DESC")
    suspend fun getAllCampaignsList(): List<CampaignEntity>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun getCampaign(id: Long): CampaignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaign(campaign: CampaignEntity): Long

    @Update
    suspend fun updateCampaign(campaign: CampaignEntity)

    @Delete
    suspend fun deleteCampaign(campaign: CampaignEntity)

    @Query("DELETE FROM campaigns WHERE id = :id")
    suspend fun deleteCampaignById(id: Long)

    @Query("SELECT COUNT(*) FROM campaigns")
    suspend fun getCount(): Int
}
