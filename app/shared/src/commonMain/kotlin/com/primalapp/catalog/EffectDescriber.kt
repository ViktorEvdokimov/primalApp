package com.primalapp.catalog

/**
 * Формулировки правил для игроков: «Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6».
 * Перенос `EffectDescriber` сервера: в главах условие с «иначе» открывает задание («открыть задание 7, иначе
 * добавить задание 8»), в заданиях — добавляет. Вложенное условие описывается после двоеточия (задание 25).
 */
class EffectDescriber(
    private val achievementName: (String) -> String,
    private val bossName: (String) -> String
) {

    /** Где описывается правило: у задания или у главы — формулировки немного различаются. */
    enum class Context { QUEST, CHAPTER }

    /** Формулировки всех условий верхнего уровня в порядке списка. */
    fun rules(effects: List<Effect>, context: Context): List<String> =
        effects.filterIsInstance<Effect.Conditional>().map { describe(it, context) }

    fun describe(conditional: Effect.Conditional, context: Context): String {
        val condition = condition(conditional.condition)
        val then = conditional.then
        val otherwise = conditional.otherwise
        if (otherwise.isEmpty() && then.size == 1) {
            when (val only = then.first()) {
                is Effect.HunterKitUpgrade -> return "Если $condition — улучшение набора охотника"
                is Effect.Message -> return "Если $condition: ${only.text}"
                is Effect.Conditional -> return "Если $condition: ${lowerFirst(describe(only, context))}"
                else -> Unit
            }
        }
        val separator = if (mentionsChapter(conditional.condition)) ", то " else ", "
        val chapterWithElse = context == Context.CHAPTER && otherwise.isNotEmpty()
        val text = StringBuilder("Если ").append(condition).append(separator).append(effects(then, context, chapterWithElse))
        if (otherwise.isNotEmpty()) {
            text.append(", иначе ").append(effects(otherwise, context, false))
        }
        return text.toString()
    }

    fun condition(condition: Condition): String = when (condition) {
        is Condition.HasAchievement -> "есть достижение ${quoted(condition.achievement)}"
        is Condition.ChapterIn -> "текущая глава " + joinWords(condition.chapters.map { it.toString() }, "или")
        is Condition.QuestAvailable -> "задание ${condition.quest} доступно"
        is Condition.HasExpansion -> "есть дополнение «${condition.expansion.displayName}»"
        is Condition.Not -> negated(condition.condition)
        is Condition.All -> if (onlyAchievements(condition.conditions)) {
            "есть достижения " + joinWords(quotedAll(condition.conditions), "и")
        } else {
            condition.conditions.joinToString(" и ") { condition(it) }
        }
        is Condition.Any -> if (onlyAchievements(condition.conditions)) {
            "есть достижение " + joinWords(quotedAll(condition.conditions), "или")
        } else {
            condition.conditions.joinToString(" или ") { condition(it) }
        }
    }

    private fun negated(condition: Condition): String = when {
        condition is Condition.HasAchievement -> "нет достижения ${quoted(condition.achievement)}"
        condition is Condition.QuestAvailable -> "задание ${condition.quest} ещё не доступно"
        condition is Condition.HasExpansion -> "нет дополнения «${condition.expansion.displayName}»"
        condition is Condition.All && onlyAchievements(condition.conditions) ->
            "нет хотя бы одного из достижений " + joinWords(quotedAll(condition.conditions), "и")
        condition is Condition.Any && onlyAchievements(condition.conditions) ->
            "нет ни одного из достижений " + joinWords(quotedAll(condition.conditions), "и")
        else -> "не выполнено условие «${condition(condition)}»"
    }

    /** Одно действие плана: «добавить задание 6», «добавить достижение «Оледенение»». */
    fun action(effect: Effect): String = effect(effect, Context.QUEST, false)

    private fun effects(effects: List<Effect>, context: Context, openVerb: Boolean): String =
        effects.joinToString(", ") { effect(it, context, openVerb) }

    private fun effect(effect: Effect, context: Context, openVerb: Boolean): String = when (effect) {
        is Effect.OpenQuest -> (if (openVerb) "открыть задание " else "добавить задание ") + effect.quest
        is Effect.GrantAchievement -> "добавить достижение ${quoted(effect.achievement)}"
        is Effect.ExpireQuests -> "истекло время заданий " + effect.quests.joinToString(", ")
        is Effect.ExpireAllQuests -> "истекло время всех заданий"
        is Effect.HunterKitUpgrade -> "улучшение набора охотника"
        is Effect.ForgeLevelUp -> "повышение уровня кузни"
        is Effect.LabLevelUp -> "повышение уровня лаборатории"
        is Effect.RewardCards -> "карты наград " + effect.cards.joinToString(", ")
        is Effect.Message -> effect.text
        is Effect.FinalBattle -> "следующий бой — финальный: " + bossName(effect.boss)
        is Effect.Resources -> "каждый охотник получает " +
            effect.items.entries.joinToString(", ") { "${it.key.displayName} ${it.value}" }
        is Effect.Conditional -> lowerFirst(describe(effect, context))
    }

    private fun mentionsChapter(condition: Condition): Boolean = when (condition) {
        is Condition.ChapterIn -> true
        is Condition.Not -> mentionsChapter(condition.condition)
        is Condition.All -> condition.conditions.any { mentionsChapter(it) }
        is Condition.Any -> condition.conditions.any { mentionsChapter(it) }
        else -> false
    }

    private fun onlyAchievements(conditions: List<Condition>): Boolean = conditions.all { it is Condition.HasAchievement }

    private fun quotedAll(conditions: List<Condition>): List<String> =
        conditions.map { quoted((it as Condition.HasAchievement).achievement) }

    private fun quoted(achievementCode: String): String = "«${achievementName(achievementCode)}»"

    companion object {
        /** «1, 2 или 3». */
        fun joinWords(items: List<String>, conjunction: String): String =
            if (items.size <= 1) items.joinToString("") else items.dropLast(1).joinToString(", ") + " $conjunction " + items.last()

        fun lowerFirst(text: String): String = if (text.isEmpty()) text else text[0].lowercaseChar() + text.substring(1)
    }
}
