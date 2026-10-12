package com.primalapp.database.entity


/** Строка каталога старых версий (до 1.3): нужна только миграциям 9–17. */
data class ChapterInfoEntity(
    val chapter: Int,
    val rewards: String = "",
    val rewardPlants: String = "",
    val openQuests: String = "",
    val conditionalOpenQuests: String = "",
    val expireQuests: String = "",
    val forgeUpgrade: Boolean = false,
    val labUpgrade: Boolean = false,
    val hunterKitUpgrade: Boolean = false,
    val decisions: String = "",
    val messages: String = "",
    val conditionalMessages: String = "",
    val hunterKitUpgradeAchievement: String = "",
    val expireAllQuests: Boolean = false,
    val finalBoss: String = ""
)
