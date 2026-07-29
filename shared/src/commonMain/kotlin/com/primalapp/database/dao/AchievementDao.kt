package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.primalapp.database.entity.AchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE campaign_id = :campaignId")
    fun getAchievements(campaignId: Long): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE campaign_id = :campaignId")
    suspend fun getAchievementsList(campaignId: Long): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: AchievementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    @Query("UPDATE achievements SET unlocked = :unlocked WHERE campaign_id = :campaignId AND achievement_id = :achievementId")
    suspend fun setUnlocked(campaignId: Long, achievementId: String, unlocked: Boolean)

    @Query("DELETE FROM achievements WHERE campaign_id = :campaignId")
    suspend fun deleteAchievementsByCampaign(campaignId: Long)
}
