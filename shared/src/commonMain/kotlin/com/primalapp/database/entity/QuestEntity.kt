package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quests",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaign_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaign_id")]
)
data class QuestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "campaign_id")
    val campaignId: Long,
    @ColumnInfo(name = "quest_id")
    val questId: String,
    val name: String,
    val chapter: Int,
    val element: String? = null,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "is_available")
    val isAvailable: Boolean = false
)
