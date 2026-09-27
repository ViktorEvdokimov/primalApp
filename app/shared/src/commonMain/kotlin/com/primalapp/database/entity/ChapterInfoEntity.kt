package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapter_info")
data class ChapterInfoEntity(
    @PrimaryKey
    val chapter: Int,
    val rewards: String = "",
    @ColumnInfo(name = "reward_plants")
    val rewardPlants: String = "",
    @ColumnInfo(name = "open_quests")
    val openQuests: String = "",
    @ColumnInfo(name = "conditional_open_quests")
    val conditionalOpenQuests: String = "",
    @ColumnInfo(name = "expire_quests")
    val expireQuests: String = "",
    @ColumnInfo(name = "forge_upgrade")
    val forgeUpgrade: Boolean = false,
    @ColumnInfo(name = "lab_upgrade")
    val labUpgrade: Boolean = false,
    @ColumnInfo(name = "hunter_kit_upgrade")
    val hunterKitUpgrade: Boolean = false,
    val decisions: String = "",
    val messages: String = "",
    @ColumnInfo(name = "conditional_messages")
    val conditionalMessages: String = "",
    @ColumnInfo(name = "hunter_kit_upgrade_achievement", defaultValue = "")
    val hunterKitUpgradeAchievement: String = "",
    @ColumnInfo(name = "expire_all_quests", defaultValue = "0")
    val expireAllQuests: Boolean = false,
    @ColumnInfo(name = "final_boss", defaultValue = "")
    val finalBoss: String = ""
)
