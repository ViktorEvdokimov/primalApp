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
    /** Улучшение набора охотника выдаётся только при этом достижении (главы 8 и 10 — «Голос Волтьяра», C-9). */
    val hunterKitUpgradeAchievement: String? = null,
    /** Истекает время всех открытых заданий (глава 11, R-6). */
    val expireAllQuests: Boolean = false,
    /** Следующий бой — финальный, единственный вариант — этот босс (глава 11 → «Пробуждённый», R-6). */
    val finalBossName: String? = null,
    val decisions: List<ChapterDecision> = emptyList(),
    val messages: List<String> = emptyList(),
    val conditionalMessages: List<ConditionalMessage> = emptyList()
)
