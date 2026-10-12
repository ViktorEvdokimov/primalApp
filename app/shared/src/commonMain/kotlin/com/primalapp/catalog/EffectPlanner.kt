package com.primalapp.catalog

import com.primalapp.catalog.EffectDescriber.Context
import com.primalapp.model.campaign.Expansion

/**
 * Снимок кампании до применения набора эффектов: по нему вычисляются условия.
 *
 * @param achievements коды достижений отряда
 * @param chapter текущая глава книги
 * @param availableQuests добавленные задания: открытые и выполненные
 * @param expansions дополнения владельца кампании
 */
data class CampaignFacts(
    val achievements: Set<String>,
    val chapter: Int,
    val availableQuests: Set<Int>,
    val expansions: Set<Expansion> = Expansion.entries.toSet()
)

/**
 * Условное правило для окна наград: вид, формулировка и результат проверки для этой кампании
 * («Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6» — «Добавлено задание 4.»).
 */
data class RuleExplanation(val kind: RuleKind, val description: String, val result: String)

/**
 * Вид условного правила — от него зависит раздел окна наград: «Условные задания» — [QUEST],
 * «Условные награды» — остальные.
 */
enum class RuleKind { QUEST, ACHIEVEMENT, KIT, MESSAGE, OTHER }

/** Результат планировщика: действия без условий (условия уже вычислены) и пояснения к условным правилам. */
data class Plan(val actions: List<Effect>, val explanations: List<RuleExplanation>) {

    /** Задания, которые откроет план, по порядку. */
    val openedQuests: List<Int> get() = actions.filterIsInstance<Effect.OpenQuest>().map { it.quest }

    companion object {
        val EMPTY = Plan(emptyList(), emptyList())
    }
}

/**
 * Планировщик эффектов — перенос `EffectPlanner` сервера: какие действия будут выполнены и почему. Чистая функция:
 * условия вычисляются по снимку кампании до применения набора, поэтому достижения и задания, которые выдаёт этот
 * же набор, на его условия не влияют. Превью и применение используют один и тот же план.
 */
class EffectPlanner(
    private val describer: EffectDescriber,
    private val achievementName: (String) -> String
) {

    fun plan(effects: List<Effect>, facts: CampaignFacts, context: Context): Plan {
        val actions = mutableListOf<Effect>()
        val explanations = mutableListOf<RuleExplanation>()
        for (effect in effects) {
            if (effect is Effect.Conditional) {
                val chosen = mutableListOf<Effect>()
                collect(effect, facts, chosen)
                actions += chosen
                explanations += RuleExplanation(kind(effect), describer.describe(effect, context), result(effect, chosen, facts, context))
            } else {
                actions += effect
            }
        }
        return Plan(actions, explanations)
    }

    private fun result(conditional: Effect.Conditional, chosen: List<Effect>, facts: CampaignFacts, context: Context): String {
        val holds = holds(conditional.condition, facts)
        return when (kind(conditional)) {
            RuleKind.QUEST -> chosen.filterIsInstance<Effect.OpenQuest>().firstOrNull()?.let { "Добавлено задание ${it.quest}." }
                ?: if (context == Context.QUEST && conditional.condition is Condition.HasAchievement && !holds) {
                    "Достижения нет, задание не добавляется."
                } else {
                    "Условие не выполнено, задание не добавляется."
                }
            RuleKind.ACHIEVEMENT -> chosen.filterIsInstance<Effect.GrantAchievement>().firstOrNull()?.achievement?.let { code ->
                if (code in facts.achievements) {
                    "Достижение ${quoted(code)} уже получено."
                } else {
                    "Достижение есть, добавлено достижение ${quoted(code)}."
                }
            } ?: "Достижения нет."
            RuleKind.KIT -> if (holds) "Достижение есть, улучшите набор охотника." else "Достижения нет."
            RuleKind.MESSAGE -> chosen.filterIsInstance<Effect.Message>().firstOrNull()?.let { "Достижение есть: ${it.text}." }
                ?: "Достижения нет."
            RuleKind.OTHER -> if (chosen.isEmpty()) "Условие не выполнено." else "Условие выполнено."
        }
    }

    private fun quoted(code: String): String = "«${achievementName(code)}»"

    companion object {
        /** Условие по снимку кампании. */
        fun holds(condition: Condition, facts: CampaignFacts): Boolean = when (condition) {
            is Condition.HasAchievement -> condition.achievement in facts.achievements
            is Condition.ChapterIn -> facts.chapter in condition.chapters
            is Condition.QuestAvailable -> condition.quest in facts.availableQuests
            is Condition.HasExpansion -> condition.expansion in facts.expansions
            is Condition.Not -> !holds(condition.condition, facts)
            is Condition.All -> condition.conditions.all { holds(it, facts) }
            is Condition.Any -> condition.conditions.any { holds(it, facts) }
        }

        /** Действия выбранной ветви; вложенные условия вычисляются по тому же снимку. */
        private fun collect(effect: Effect, facts: CampaignFacts, out: MutableList<Effect>) {
            if (effect is Effect.Conditional) {
                val branch = if (holds(effect.condition, facts)) effect.then else effect.otherwise
                branch.forEach { collect(it, facts, out) }
            } else {
                out += effect
            }
        }

        /** Вид правила по действиям обеих ветвей: задания важнее достижений, достижения — улучшения набора. */
        fun kind(conditional: Effect.Conditional): RuleKind {
            val all = mutableListOf<Effect>()
            flatten(conditional, all)
            return when {
                all.any { it is Effect.OpenQuest } -> RuleKind.QUEST
                all.any { it is Effect.GrantAchievement } -> RuleKind.ACHIEVEMENT
                all.any { it is Effect.HunterKitUpgrade } -> RuleKind.KIT
                all.any { it is Effect.Message } -> RuleKind.MESSAGE
                else -> RuleKind.OTHER
            }
        }

        /** Правило открывает задания хотя бы в одной ветви — такие пояснения показываются при «Выполнено» вручную. */
        fun opensQuests(conditional: Effect.Conditional): Boolean = kind(conditional) == RuleKind.QUEST

        private fun flatten(effect: Effect, out: MutableList<Effect>) {
            if (effect is Effect.Conditional) {
                effect.then.forEach { flatten(it, out) }
                effect.otherwise.forEach { flatten(it, out) }
            } else {
                out += effect
            }
        }
    }
}
