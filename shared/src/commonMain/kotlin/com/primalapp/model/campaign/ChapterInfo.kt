package com.primalapp.model.campaign

data class ChapterDecision(
    val question: String,
    val options: List<String>,
    val achievementOnOption: String? = null,
    val optionForAchievement: String? = null
)

data class ConditionalQuestOpen(
    val achievements: List<String>,
    val questNumber: Int,
    val elseQuestNumber: Int? = null,
    val requireAll: Boolean = false,
    val negated: Boolean = false
)

data class ConditionalMessage(
    val achievementName: String,
    val message: String
)

data class ChapterInfo(
    val chapter: Int,
    val rewards: Map<Material, Int> = emptyMap(),
    val rewardPlants: Map<Plant, Int> = emptyMap(),
    val openQuests: List<Int> = emptyList(),
    val conditionalOpenQuests: List<ConditionalQuestOpen> = emptyList(),
    val expireQuests: List<Int> = emptyList(),
    val forgeUpgrade: Boolean = false,
    val labUpgrade: Boolean = false,
    val hunterKitUpgrade: Boolean = false,
    val decisions: List<ChapterDecision> = emptyList(),
    val messages: List<String> = emptyList(),
    val conditionalMessages: List<ConditionalMessage> = emptyList()
)
