package com.primalapp.model.campaign

data class TaskInfo(
    val questNumber: Int,
    val name: String,
    val bossName: String,
    val bossElement: Element? = null,
    val victoryMaterials: Map<Material, Int> = emptyMap(),
    val victoryPlants: Map<Plant, Int> = emptyMap(),
    val victoryOpenQuests: List<Int> = emptyList(),
    val victoryOpenQuestConditions: List<TaskCondition> = emptyList(),
    val victoryAchievements: List<String> = emptyList(),
    val victoryRewardCards: List<String> = emptyList(),
    val victorySpecial: String = "",
    val defeatOpenQuests: List<Int> = emptyList(),
    val defeatOpenQuestConditions: List<TaskCondition> = emptyList()
)
