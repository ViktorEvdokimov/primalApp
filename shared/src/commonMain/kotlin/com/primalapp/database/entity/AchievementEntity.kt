package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "achievements",
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
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "campaign_id")
    val campaignId: Long,
    @ColumnInfo(name = "achievement_id")
    val achievementId: String,
    val name: String,
    val description: String = "",
    val unlocked: Boolean = false
)
