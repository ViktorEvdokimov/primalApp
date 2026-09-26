package com.primalapp.domain

import com.primalapp.model.campaign.ChapterInfo
import com.primalapp.model.campaign.ConditionalQuestOpen
import com.primalapp.model.campaign.TaskCondition
import com.primalapp.model.campaign.TaskConditionKind

/** Вид условного правила: условное задание, условное достижение или условная награда главы. */
enum class ConditionOutcomeType { QUEST, ACHIEVEMENT, REWARD }

/**
 * Условное правило задания или главы в формулировке правил ([description]) и результат его проверки
 * для текущей кампании ([result]). [openQuest] и [grantAchievement] — действие, которое выполняется
 * при принятии наград: окна наград и применение наград используют один и тот же расчёт.
 */
data class ConditionOutcome(
    val type: ConditionOutcomeType,
    val description: String,
    val result: String,
    val openQuest: Int? = null,
    val grantAchievement: String? = null
)

/** Условия каталога заданий (победа или поражение) — в порядке применения. */
fun evaluateTaskConditions(
    conditions: List<TaskCondition>,
    achievements: Set<String>,
    bookChapter: Int,
    availableQuestNumbers: Set<Int>
): List<ConditionOutcome> {
    // Открывается задание первого выполненного условия (зад. 25: при главе 8 задание 34 вместо 27)
    var openedQuest: Int? = null
    return conditions.map { condition ->
        val reward = condition.rewardAchievement
        if (reward != null) {
            val source = condition.achievementName
            val hasSource = source != null && achievements.containsAchievement(source)
            ConditionOutcome(
                type = ConditionOutcomeType.ACHIEVEMENT,
                description = "Если есть достижение ${quoted(source)}, добавить достижение ${quoted(reward)}",
                result = when {
                    !hasSource -> "Достижения нет."
                    achievements.containsAchievement(reward) -> "Достижение ${quoted(reward)} уже получено."
                    else -> "Достижение есть, добавлено достижение ${quoted(reward)}."
                },
                grantAchievement = reward.takeIf { hasSource }
            )
        } else {
            val description = describeTaskCondition(condition)
            val alreadyOpened = openedQuest
            val target = if (alreadyOpened == null) {
                resolveTaskCondition(condition, achievements, bookChapter, availableQuestNumbers)
            } else {
                null
            }
            if (target != null) openedQuest = target
            ConditionOutcome(
                type = ConditionOutcomeType.QUEST,
                description = description,
                result = when {
                    alreadyOpened != null -> "Не применяется: уже добавлено задание $alreadyOpened."
                    target != null -> "Добавлено задание $target."
                    condition.kind.isAchievementCheck() && condition.achievementName != null &&
                        !achievements.containsAchievement(condition.achievementName) -> "Достижения нет, задание не добавляется."
                    else -> "Условие не выполнено, задание не добавляется."
                },
                openQuest = target
            )
        }
    }
}

/** Условные задания главы: каждое правило применяется независимо. */
fun evaluateChapterConditionalQuests(
    conditions: List<ConditionalQuestOpen>,
    achievements: Set<String>
): List<ConditionOutcome> = conditions.map { condition ->
    val matched = if (condition.requireAll) {
        condition.achievements.all { achievements.containsAchievement(it) }
    } else {
        condition.achievements.any { achievements.containsAchievement(it) }
    }
    val target = if (matched != condition.negated) condition.questNumber else condition.elseQuestNumber
    ConditionOutcome(
        type = ConditionOutcomeType.QUEST,
        description = describeChapterCondition(condition),
        result = if (target != null) "Добавлено задание $target." else "Условие не выполнено, задание не добавляется.",
        openQuest = target
    )
}

/**
 * Условные награды главы: улучшение набора охотника при достижении (главы 8 и 10) и сообщения
 * при достижении (гл. 4 — «Получите награду 25»).
 */
fun evaluateChapterConditionalRewards(chapterInfo: ChapterInfo, achievements: Set<String>): List<ConditionOutcome> = buildList {
    val kitAchievement = chapterInfo.hunterKitUpgradeAchievement
    if (chapterInfo.hunterKitUpgrade && kitAchievement != null) {
        val has = achievements.containsAchievement(kitAchievement)
        add(
            ConditionOutcome(
                type = ConditionOutcomeType.REWARD,
                description = "Если есть достижение ${quoted(kitAchievement)} — улучшение набора охотника",
                result = if (has) "Достижение есть, улучшите набор охотника." else "Достижения нет."
            )
        )
    }
    chapterInfo.conditionalMessages.forEach { conditional ->
        val has = achievements.containsAchievement(conditional.achievementName)
        add(
            ConditionOutcome(
                type = ConditionOutcomeType.REWARD,
                description = "Если есть достижение ${quoted(conditional.achievementName)}: ${conditional.message}",
                result = if (has) "Достижение есть: ${conditional.message}." else "Достижения нет."
            )
        )
    }
}

private fun resolveTaskCondition(
    condition: TaskCondition,
    achievements: Set<String>,
    bookChapter: Int,
    availableQuestNumbers: Set<Int>
): Int? {
    val matched = when (condition.kind) {
        TaskConditionKind.CHAPTER_IN -> bookChapter in condition.chapterSet
        TaskConditionKind.ACHIEVEMENT_OWNED ->
            condition.achievementName != null && achievements.containsAchievement(condition.achievementName)
        TaskConditionKind.ACHIEVEMENT_NOT_OWNED ->
            condition.achievementName != null && !achievements.containsAchievement(condition.achievementName)
        TaskConditionKind.ACHIEVEMENT_OWNED_IN_CHAPTER ->
            condition.achievementName != null &&
                achievements.containsAchievement(condition.achievementName) &&
                bookChapter in condition.chapterSet
        TaskConditionKind.QUEST_NOT_AVAILABLE -> {
            val checkQuest = condition.chapterSet.firstOrNull()
            checkQuest != null && checkQuest !in availableQuestNumbers
        }
    }
    return if (matched) condition.questNumber else condition.elseQuestNumber
}

private fun TaskConditionKind.isAchievementCheck(): Boolean =
    this == TaskConditionKind.ACHIEVEMENT_OWNED || this == TaskConditionKind.ACHIEVEMENT_OWNED_IN_CHAPTER

/** Формулировка условия задания — как в `doc/taskInfo.md`. */
private fun describeTaskCondition(condition: TaskCondition): String {
    val quest = condition.questNumber?.let { "добавить задание $it" } ?: "задание не добавляется"
    val otherwise = condition.elseQuestNumber?.let { ", иначе добавить задание $it" }.orEmpty()
    val achievement = quoted(condition.achievementName)
    return when (condition.kind) {
        TaskConditionKind.CHAPTER_IN -> "Если текущая глава ${chapterList(condition.chapterSet)}, то $quest$otherwise"
        TaskConditionKind.ACHIEVEMENT_OWNED -> "Если есть достижение $achievement, $quest$otherwise"
        TaskConditionKind.ACHIEVEMENT_NOT_OWNED -> "Если нет достижения $achievement, $quest$otherwise"
        TaskConditionKind.ACHIEVEMENT_OWNED_IN_CHAPTER ->
            "Если есть достижение $achievement и текущая глава ${chapterList(condition.chapterSet)}, то $quest$otherwise"
        TaskConditionKind.QUEST_NOT_AVAILABLE ->
            "Если задание ${condition.chapterSet.firstOrNull() ?: "?"} ещё не доступно, $quest$otherwise"
    }
}

/** Формулировка условного задания главы — как в `doc/compainInfo.md`. */
private fun describeChapterCondition(condition: ConditionalQuestOpen): String {
    val names = condition.achievements.map { quoted(it) }
    val subject = when {
        names.size == 1 && !condition.negated -> "Если есть достижение ${names.single()}"
        names.size == 1 -> "Если нет достижения ${names.single()}"
        condition.negated && condition.requireAll -> "Если нет хотя бы одного из достижений ${joinAnd(names)}"
        condition.negated -> "Если нет ни одного из достижений ${joinAnd(names)}"
        condition.requireAll -> "Если есть достижения ${joinAnd(names)}"
        else -> "Если есть достижение ${names.joinToString(" или ")}"
    }
    return if (condition.elseQuestNumber != null) {
        "$subject, открыть задание ${condition.questNumber}, иначе добавить задание ${condition.elseQuestNumber}"
    } else {
        "$subject, добавить задание ${condition.questNumber}"
    }
}

private fun quoted(name: String?): String = "«${name.orEmpty()}»"

private fun joinAnd(items: List<String>): String =
    if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " и " + items.last()

private fun chapterList(chapters: List<Int>): String =
    if (chapters.size <= 1) chapters.joinToString() else chapters.dropLast(1).joinToString(", ") + " или " + chapters.last()
