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

    @Query(
        """
        INSERT INTO quests (campaign_id, quest_id, name, chapter, element, quest_number, is_completed, is_available)
        VALUES (:campaignId, :questId, :name, :chapter, :element, :questNumber, :isCompleted, :isAvailable)
        ON CONFLICT(campaign_id, quest_id) DO UPDATE SET
            name = excluded.name,
            chapter = excluded.chapter,
            element = excluded.element,
            quest_number = excluded.quest_number,
            is_completed = excluded.is_completed,
            is_available = excluded.is_available
        """
    )
    suspend fun upsertQuest(
        campaignId: Long,
        questId: String,
        name: String,
        chapter: Int,
        element: String?,
        questNumber: Int,
        isCompleted: Boolean,
        isAvailable: Boolean
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuests(quests: List<QuestEntity>)

    @Query("UPDATE quests SET is_completed = 1 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun completeQuest(campaignId: Long, questId: String)

    @Query("UPDATE quests SET is_completed = 0 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun uncompleteQuest(campaignId: Long, questId: String)

    @Query("UPDATE quests SET is_available = 1 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun makeQuestAvailable(campaignId: Long, questId: String)

    @Query("UPDATE quests SET is_available = 0 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun setQuestUnavailable(campaignId: Long, questId: String)

    @Query("DELETE FROM quests WHERE campaign_id = :campaignId")
    suspend fun deleteQuestsByCampaign(campaignId: Long)
}
