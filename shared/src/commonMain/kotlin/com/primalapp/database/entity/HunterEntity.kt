package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hunters",
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
data class HunterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "campaign_id")
    val campaignId: Long,
    @ColumnInfo(name = "player_name")
    val playerName: String,
    @ColumnInfo(name = "class_name")
    val className: String
)
