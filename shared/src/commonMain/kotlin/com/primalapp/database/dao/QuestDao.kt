package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.primalapp.database.entity.QuestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests WHERE campaign_id = :campaignId")
    fun getQuests(campaignId: Long): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE campaign_id = :campaignId")
    suspend fun getQuestsList(campaignId: Long): List<QuestEntity>

    @Query("SELECT * FROM quests WHERE campaign_id = :campaignId AND is_available = 1 AND is_completed = 0")
    fun getAvailableQuests(campaignId: Long): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE campaign_id = :campaignId AND is_available = 1 AND is_completed = 0")
    suspend fun getAvailableQuestsList(campaignId: Long): List<QuestEntity>

    @Query("SELECT * FROM quests WHERE campaign_id = :campaignId AND is_completed = 1")
    fun getCompletedQuests(campaignId: Long): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE campaign_id = :campaignId AND is_completed = 1")
    suspend fun getCompletedQuestsList(campaignId: Long): List<QuestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuest(quest: QuestEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuests(quests: List<QuestEntity>)

    @Query("UPDATE quests SET is_completed = 1 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun completeQuest(campaignId: Long, questId: String)

    @Query("UPDATE quests SET is_available = 1 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun makeQuestAvailable(campaignId: Long, questId: String)

    @Query("DELETE FROM quests WHERE campaign_id = :campaignId")
    suspend fun deleteQuestsByCampaign(campaignId: Long)
}
