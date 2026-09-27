package com.primalapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
        INSERT OR IGNORE INTO quests (campaign_id, quest_id, name, chapter, element, quest_number, is_completed, is_available)
        VALUES (:campaignId, :questId, :name, :chapter, :element, :questNumber, :isCompleted, :isAvailable)
        """
    )
    suspend fun insertQuestIfAbsent(
        campaignId: Long,
        questId: String,
        name: String,
        chapter: Int,
        element: String?,
        questNumber: Int,
        isCompleted: Boolean,
        isAvailable: Boolean
    )

    /** Выполненное задание остаётся выполненным (D-5); снять отметку можно только [uncompleteQuest]. */
    @Query(
        """
        UPDATE quests SET
            name = :name,
            chapter = :chapter,
            element = :element,
            quest_number = :questNumber,
            is_completed = CASE WHEN is_completed = 1 THEN 1 ELSE :isCompleted END,
            is_available = :isAvailable
        WHERE campaign_id = :campaignId AND quest_id = :questId
        """
    )
    suspend fun updateQuestFields(
        campaignId: Long,
        questId: String,
        name: String,
        chapter: Int,
        element: String?,
        questNumber: Int,
        isCompleted: Boolean,
        isAvailable: Boolean
    )

    /**
     * Апсерт по уникальному индексу `(campaign_id, quest_id)`. Вместо `INSERT … ON CONFLICT DO UPDATE`
     * (нужен SQLite 3.24+, а на Android 8–10 при minSdk 26 — SQLite 3.18–3.22) — вставка без замены
     * и обновление в одной транзакции.
     */
    @Transaction
    suspend fun upsertQuest(
        campaignId: Long,
        questId: String,
        name: String,
        chapter: Int,
        element: String?,
        questNumber: Int,
        isCompleted: Boolean,
        isAvailable: Boolean
    ) {
        insertQuestIfAbsent(campaignId, questId, name, chapter, element, questNumber, isCompleted, isAvailable)
        updateQuestFields(campaignId, questId, name, chapter, element, questNumber, isCompleted, isAvailable)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuests(quests: List<QuestEntity>)

    @Query("UPDATE quests SET is_completed = 1 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun completeQuest(campaignId: Long, questId: String)

    /** Кнопка «Отмена» у выполненного задания: задание снова открыто и не выполнено. */
    @Query("UPDATE quests SET is_completed = 0, is_available = 1 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun uncompleteQuest(campaignId: Long, questId: String)

    @Query("UPDATE quests SET is_available = 1 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun makeQuestAvailable(campaignId: Long, questId: String)

    @Query("UPDATE quests SET is_available = 0 WHERE campaign_id = :campaignId AND quest_id = :questId")
    suspend fun setQuestUnavailable(campaignId: Long, questId: String)

    @Query("DELETE FROM quests WHERE campaign_id = :campaignId")
    suspend fun deleteQuestsByCampaign(campaignId: Long)
}
