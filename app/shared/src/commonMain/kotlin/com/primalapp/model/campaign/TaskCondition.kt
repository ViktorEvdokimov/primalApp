package com.primalapp.model.campaign

enum class TaskConditionKind {
    CHAPTER_IN,
    ACHIEVEMENT_OWNED,
    ACHIEVEMENT_NOT_OWNED,
    ACHIEVEMENT_OWNED_IN_CHAPTER,
    QUEST_NOT_AVAILABLE
}

/**
 * Условие открытия задания / выдачи достижения.
 * - [kind] — тип условия. `ACHIEVEMENT_OWNED_IN_CHAPTER` — комбинированное:
 *   «достижение получено И текущая глава в [chapterSet]» (составное условие, зад. 25).
 * - [achievementName] — проверяемое достижение.
 * - [chapterSet] — главы для CHAPTER_IN / ACHIEVEMENT_OWNED_IN_CHAPTER.
 * - [questNumber] — задание при выполнении условия.
 * - [elseQuestNumber] — задание, если условие не выполнено.
 * - [rewardAchievement] — если задано, условие выдаёт достижение [rewardAchievement]
 *   при выполнении условия вместо открытия задания (условные достижения зад. 29/40).
 */
data class TaskCondition(
    val kind: TaskConditionKind,
    val achievementName: String? = null,
    val chapterSet: List<Int> = emptyList(),
    val questNumber: Int? = null,
    val elseQuestNumber: Int? = null,
    val rewardAchievement: String? = null
)
