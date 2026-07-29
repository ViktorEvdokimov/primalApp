package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "resources",
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
data class ResourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "hunter_id")
    val hunterId: Long,
    @ColumnInfo(name = "resource_type")
    val resourceType: String,
    @ColumnInfo(name = "resource_name")
    val resourceName: String,
    val quantity: Int = 0
)
