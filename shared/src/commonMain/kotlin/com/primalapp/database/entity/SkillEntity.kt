package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "skills",
    foreignKeys = [
        ForeignKey(
            entity = HunterEntity::class,
            parentColumns = ["id"],
            childColumns = ["hunter_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("hunter_id")]
)
data class SkillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "hunter_id")
    val hunterId: Long,
    val branch: String,
    val tier: Int,
    val unlocked: Boolean = false
)
