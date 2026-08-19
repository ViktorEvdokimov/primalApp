package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bosses")
data class BossEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val element: String?,
    val difficulty: Int,
    @ColumnInfo(name = "stance1_dfw")
    val stance1Dfw: Int?,
    @ColumnInfo(name = "stance1_hsc")
    val stance1Hsc: Int? = null,
    @ColumnInfo(name = "stance2_dfw")
    val stance2Dfw: Int?,
    @ColumnInfo(name = "stance2_hsc")
    val stance2Hsc: Int? = null,
    @ColumnInfo(name = "stance3_dfw")
    val stance3Dfw: Int?,
    @ColumnInfo(name = "stance3_hsc")
    val stance3Hsc: Int? = null,
    @ColumnInfo(name = "stance4_dfw")
    val stance4Dfw: Int = 0,
    @ColumnInfo(name = "stance4_hsc")
    val stance4Hsc: Int? = null,
    @ColumnInfo(name = "stance5_dfw")
    val stance5Dfw: Int = 0,
    @ColumnInfo(name = "stance5_hsc")
    val stance5Hsc: Int? = null
)
