package com.primalapp.domain

data class ChapterState(
    val chapter: Int,
    val forgeLevel: Int,
    val labLevel: Int
)

interface ChapterProgression {
    fun getForgeUpgradeChapter(level: Int): Int
    fun getLabUpgradeChapter(level: Int): Int
    fun advanceChapter(current: ChapterState, bossDefeated: Boolean): ChapterState
    fun shouldShowForgeUpgradeNotification(current: ChapterState, previous: ChapterState): Boolean
    fun shouldShowLabUpgradeNotification(current: ChapterState, previous: ChapterState): Boolean
}
