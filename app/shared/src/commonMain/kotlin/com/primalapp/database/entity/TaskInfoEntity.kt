package com.primalapp.database.entity


/** Строка каталога старых версий (до 1.3): нужна только миграциям 9–17. */
data class TaskInfoEntity(
    val questNumber: Int,
    val name: String,
    val bossName: String,
    val bossElement: String? = null,
    val victoryMaterials: String = "",
    val victoryPlants: String = "",
    val victoryOpenQuests: String = "",
    val victoryOpenQuestConditions: String = "",
    val victoryAchievements: String = "",
    val victoryRewardCards: String = "",
    val victorySpecial: String = "",
    val defeatOpenQuests: String = "",
    val defeatOpenQuestConditions: String = "",
    val defeatAchievements: String = ""
)
