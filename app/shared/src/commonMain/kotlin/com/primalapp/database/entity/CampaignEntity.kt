package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.primalapp.database.currentTimeMillis

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "current_chapter")
    val currentChapter: Int = 1,
    @ColumnInfo(name = "forge_level")
    val forgeLevel: Int = 1,
    @ColumnInfo(name = "lab_level")
    val labLevel: Int = 1,
    val notes: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = currentTimeMillis()
)
