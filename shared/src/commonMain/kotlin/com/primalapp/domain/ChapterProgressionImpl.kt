package com.primalapp.domain

class ChapterProgressionImpl : ChapterProgression {

    override fun getForgeUpgradeChapter(level: Int): Int = when (level) {
        1 -> 4
        2 -> 8
        else -> Int.MAX_VALUE
    }

    override fun getLabUpgradeChapter(level: Int): Int = when (level) {
        1 -> 4
        2 -> 8
        else -> Int.MAX_VALUE
    }

    override fun advanceChapter(current: ChapterState, bossDefeated: Boolean): ChapterState {
        if (!bossDefeated) return current
        val nextChapter = current.chapter + 1
        val nextForgeLevel = if (current.forgeLevel < 3 && nextChapter >= getForgeUpgradeChapter(current.forgeLevel)) {
            current.forgeLevel + 1
        } else {
            current.forgeLevel
        }
        val nextLabLevel = if (current.labLevel < 3 && nextChapter >= getLabUpgradeChapter(current.labLevel)) {
            current.labLevel + 1
        } else {
            current.labLevel
        }
        return ChapterState(
            chapter = nextChapter,
            forgeLevel = nextForgeLevel,
            labLevel = nextLabLevel
        )
    }

    override fun shouldShowForgeUpgradeNotification(current: ChapterState, previous: ChapterState): Boolean =
        current.forgeLevel > previous.forgeLevel

    override fun shouldShowLabUpgradeNotification(current: ChapterState, previous: ChapterState): Boolean =
        current.labLevel > previous.labLevel
}
