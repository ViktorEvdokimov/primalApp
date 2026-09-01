package com.primalapp.model.campaign

enum class TaskConditionKind {
    CHAPTER_IN,
    ACHIEVEMENT_OWNED,
    ACHIEVEMENT_NOT_OWNED
}

data class TaskCondition(
    val kind: TaskConditionKind,
    val achievementName: String? = null,
    val chapterSet: List<Int> = emptyList(),
    val questNumber: Int? = null,
    val elseQuestNumber: Int? = null
)
