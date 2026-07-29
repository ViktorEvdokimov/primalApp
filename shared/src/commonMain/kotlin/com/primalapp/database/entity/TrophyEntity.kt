package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.primalapp.database.currentTimeMillis

@Entity(
    tableName = "trophies",
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
data class TrophyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "campaign_id")
    val campaignId: Long,
    @ColumnInfo(name = "boss_name")
    val bossName: String,
    val element: String,
    val chapter: Int,
    @ColumnInfo(name = "acquired_at")
    val acquiredAt: Long = currentTimeMillis()
)
