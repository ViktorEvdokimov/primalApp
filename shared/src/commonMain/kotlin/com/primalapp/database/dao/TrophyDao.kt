package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.primalapp.database.entity.TrophyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrophyDao {
    @Query("SELECT * FROM trophies WHERE campaign_id = :campaignId")
    fun getTrophies(campaignId: Long): Flow<List<TrophyEntity>>

    @Query("SELECT * FROM trophies WHERE campaign_id = :campaignId")
    suspend fun getTrophiesList(campaignId: Long): List<TrophyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrophy(trophy: TrophyEntity): Long

    @Query("DELETE FROM trophies WHERE campaign_id = :campaignId")
    suspend fun deleteTrophiesByCampaign(campaignId: Long)
}
