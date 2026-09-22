package com.primalapp.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_info")
data class TaskInfoEntity(
    @PrimaryKey
    @ColumnInfo(name = "quest_number")
    val questNumber: Int,
    val name: String,
    @ColumnInfo(name = "boss_name")
    val bossName: String,
    @ColumnInfo(name = "boss_element")
    val bossElement: String? = null,
    @ColumnInfo(name = "victory_materials")
    val victoryMaterials: String = "",
    @ColumnInfo(name = "victory_plants")
    val victoryPlants: String = "",
    @ColumnInfo(name = "victory_open_quests")
    val victoryOpenQuests: String = "",
    @ColumnInfo(name = "victory_open_quest_conditions")
    val victoryOpenQuestConditions: String = "",
    @ColumnInfo(name = "victory_achievements")
    val victoryAchievements: String = "",
    @ColumnInfo(name = "victory_reward_cards")
    val victoryRewardCards: String = "",
    @ColumnInfo(name = "victory_special")
    val victorySpecial: String = "",
    @ColumnInfo(name = "defeat_open_quests")
    val defeatOpenQuests: String = "",
    @ColumnInfo(name = "defeat_open_quest_conditions")
    val defeatOpenQuestConditions: String = "",
    @ColumnInfo(name = "defeat_achievements")
    val defeatAchievements: String = ""
)
