package com.primalapp.catalog

import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.ResourceCode

/**
 * Язык эффектов каталога (как на сервере, `web/doc/data-model.md` §4.3): награды заданий, последствия
 * невыполненных заданий, инструкции глав и вариантов решений.
 */
sealed interface Effect {
    /** Из какого дополнения инструкция — пометка, на расчёт не влияет. */
    val expansion: Expansion?

    /** Каждый охотник получает материи и растения. */
    data class Resources(val items: Map<ResourceCode, Int>, override val expansion: Expansion? = null) : Effect

    /** Добавить задание. */
    data class OpenQuest(val quest: Int, override val expansion: Expansion? = null) : Effect

    /** Истекло время заданий (только открытых). */
    data class ExpireQuests(val quests: List<Int>, override val expansion: Expansion? = null) : Effect

    /** Истекло время всех заданий (глава 11). */
    data class ExpireAllQuests(override val expansion: Expansion? = null) : Effect

    data class GrantAchievement(val achievement: String, override val expansion: Expansion? = null) : Effect

    data class ForgeLevelUp(override val expansion: Expansion? = null) : Effect

    data class LabLevelUp(override val expansion: Expansion? = null) : Effect

    /** «Каждый охотник улучшает свой набор». */
    data class HunterKitUpgrade(override val expansion: Expansion? = null) : Effect

    /** Карты наград — игроки берут их из коробки. */
    data class RewardCards(val cards: List<String>, override val expansion: Expansion? = null) : Effect

    /** Инструкция игрокам текстом. */
    data class Message(val text: String, override val expansion: Expansion? = null) : Effect

    /** Следующий бой — только с этим боссом. */
    data class FinalBattle(val boss: String, override val expansion: Expansion? = null) : Effect

    /** Если [condition], то [then], иначе [otherwise] (может быть пустым). */
    data class Conditional(
        val condition: Condition,
        val then: List<Effect>,
        val otherwise: List<Effect> = emptyList(),
        override val expansion: Expansion? = null
    ) : Effect
}

/** Условие эффекта. */
sealed interface Condition {
    /** У отряда есть достижение (код каталога). */
    data class HasAchievement(val achievement: String) : Condition

    /** Текущая глава книги — одна из перечисленных. */
    data class ChapterIn(val chapters: List<Int>) : Condition

    /** Задание добавлено: открыто или выполнено. */
    data class QuestAvailable(val quest: Int) : Condition

    /** У владельца кампании есть дополнение. */
    data class HasExpansion(val expansion: Expansion) : Condition

    data class Not(val condition: Condition) : Condition

    data class All(val conditions: List<Condition>) : Condition

    data class Any(val conditions: List<Condition>) : Condition
}

/** Решение главы: вопрос и варианты; у варианта могут быть свои эффекты (глава 7). */
data class Decision(val code: String, val question: String, val options: List<DecisionOption>)

data class DecisionOption(val code: String, val label: String, val effects: List<Effect>)
