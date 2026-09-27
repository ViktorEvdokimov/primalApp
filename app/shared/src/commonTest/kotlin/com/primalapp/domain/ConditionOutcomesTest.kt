package com.primalapp.domain

import com.primalapp.database.chapterInfoSeedEntities
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.taskInfoSeedEntities
import com.primalapp.model.campaign.ChapterInfo
import com.primalapp.model.campaign.ConditionalMessage
import com.primalapp.model.campaign.ConditionalQuestOpen
import com.primalapp.model.campaign.TaskCondition
import com.primalapp.model.campaign.TaskConditionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Формулировки условных правил и результат их проверки для окон наград. */
class ConditionOutcomesTest {

    private val tasks = taskInfoSeedEntities().map { it.toDomain() }.associateBy { it.questNumber }
    private val chapters = chapterInfoSeedEntities().map { it.toDomain() }.associateBy { it.chapter }

    //region Условия заданий

    @Test
    fun `условие по главе задания 1 описано как в правилах, результат — добавленное задание`() {
        // Подготовка
        val conditions = tasks.getValue(1).victoryOpenQuestConditions

        // Вызов проверяемого кода: глава книги 1 и 3
        val inChapter = evaluateTaskConditions(conditions, emptySet(), bookChapter = 1, availableQuestNumbers = emptySet())
        val otherChapter = evaluateTaskConditions(conditions, emptySet(), bookChapter = 3, availableQuestNumbers = emptySet())

        // Проверка
        val outcome = inChapter.single()
        assertEquals(ConditionOutcomeType.QUEST, outcome.type)
        assertEquals("Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6", outcome.description)
        assertEquals("Добавлено задание 4.", outcome.result)
        assertEquals(4, outcome.openQuest)
        assertEquals("Добавлено задание 6.", otherChapter.single().result)
    }

    @Test
    fun `невыполненное условие без альтернативы не добавляет задание`() {
        // Подготовка: зад. 2 — «если текущая глава 1 или 2, то добавить задание 5»
        val conditions = tasks.getValue(2).victoryOpenQuestConditions

        // Вызов проверяемого кода
        val outcome = evaluateTaskConditions(conditions, emptySet(), bookChapter = 4, availableQuestNumbers = emptySet()).single()

        // Проверка
        assertEquals("Если текущая глава 1 или 2, то добавить задание 5", outcome.description)
        assertEquals("Условие не выполнено, задание не добавляется.", outcome.result)
        assertNull(outcome.openQuest)
    }

    @Test
    fun `задание 25 — применяется первое выполненное условие, второе отмечено как неприменимое`() {
        // Подготовка
        val conditions = tasks.getValue(25).victoryOpenQuestConditions

        // Вызов проверяемого кода: «Горящий уголёк» есть, глава книги 8
        val outcomes = evaluateTaskConditions(conditions, setOf("Горящий уголёк"), bookChapter = 8, availableQuestNumbers = emptySet())

        // Проверка
        assertEquals("Если есть достижение «Горящий уголёк» и текущая глава 8, то добавить задание 34", outcomes[0].description)
        assertEquals("Добавлено задание 34.", outcomes[0].result)
        assertEquals("Если есть достижение «Горящий уголёк», добавить задание 27", outcomes[1].description)
        assertEquals("Не применяется: уже добавлено задание 34.", outcomes[1].result)
        assertNull(outcomes[1].openQuest)
    }

    @Test
    fun `условие по достижению без достижения сообщает, что достижения нет`() {
        // Подготовка: зад. 11 — «Грибной лес» → 32, иначе 17
        val conditions = tasks.getValue(11).victoryOpenQuestConditions

        // Вызов проверяемого кода
        val withAchievement = evaluateTaskConditions(conditions, setOf("грибной лес"), 5, emptySet()).single()
        val without = evaluateTaskConditions(conditions, emptySet(), 5, emptySet()).single()

        // Проверка
        assertEquals("Если есть достижение «Грибной лес», добавить задание 32, иначе добавить задание 17", withAchievement.description)
        assertEquals("Добавлено задание 32.", withAchievement.result)
        assertEquals("Добавлено задание 17.", without.result)
    }

    @Test
    fun `условное достижение задания 29 описано и выдаётся только при исходном достижении`() {
        // Подготовка
        val conditions = tasks.getValue(29).victoryOpenQuestConditions

        // Вызов проверяемого кода
        val has = evaluateTaskConditions(conditions, setOf("Голос Волтьяра"), 9, emptySet()).single()
        val hasNot = evaluateTaskConditions(conditions, emptySet(), 9, emptySet()).single()

        // Проверка
        assertEquals(ConditionOutcomeType.ACHIEVEMENT, has.type)
        assertEquals("Если есть достижение «Голос Волтьяра», добавить достижение «Уробборос»", has.description)
        assertEquals("Достижение есть, добавлено достижение «Уробборос».", has.result)
        assertEquals("Уробборос", has.grantAchievement)
        assertEquals("Достижения нет.", hasNot.result)
        assertNull(hasNot.grantAchievement)
    }

    @Test
    fun `задание 42 — задание 45 добавляется, если задание 18 ещё не доступно`() {
        // Подготовка
        val conditions = tasks.getValue(42).victoryOpenQuestConditions

        // Вызов проверяемого кода
        val notAvailable = evaluateTaskConditions(conditions, emptySet(), 6, availableQuestNumbers = setOf(30, 31)).single()
        val available = evaluateTaskConditions(conditions, emptySet(), 6, availableQuestNumbers = setOf(18)).single()

        // Проверка
        assertEquals("Если задание 18 ещё не доступно, добавить задание 45", notAvailable.description)
        assertEquals("Добавлено задание 45.", notAvailable.result)
        assertEquals("Условие не выполнено, задание не добавляется.", available.result)
    }

    //endregion

    //region Условия глав

    @Test
    fun `условное задание главы 5 без достижения добавляет альтернативное задание`() {
        // Подготовка: «Если есть достижение "Упавшая звезда" открыть задание 15, иначе добавить задание 47»
        val condition = chapters.getValue(5).conditionalOpenQuests.single { it.questNumber == 15 }

        // Вызов проверяемого кода
        val outcome = evaluateChapterConditionalQuests(listOf(condition), emptySet()).single()

        // Проверка
        assertEquals("Если есть достижение «Упавшая звезда», открыть задание 15, иначе добавить задание 47", outcome.description)
        assertEquals("Добавлено задание 47.", outcome.result)
        assertEquals(47, outcome.openQuest)
    }

    @Test
    fun `формулировки составных условий главы 9 и 10`() {
        // Вызов проверяемого кода
        val chapter9 = evaluateChapterConditionalQuests(chapters.getValue(9).conditionalOpenQuests, setOf("Упавшая звезда"))
        val chapter10 = evaluateChapterConditionalQuests(chapters.getValue(10).conditionalOpenQuests, setOf("Три копья"))

        // Проверка
        assertEquals("Если нет достижения «Звезда дракона», добавить задание 28", chapter9[0].description)
        assertEquals("Добавлено задание 28.", chapter9[0].result)
        assertEquals("Если есть достижения «Упавшая звезда» и «Звезда дракона», добавить задание 49", chapter9[1].description)
        assertEquals("Условие не выполнено, задание не добавляется.", chapter9[1].result)
        val quest30 = chapter10.single { it.description.endsWith("задание 30") }
        assertEquals("Если нет хотя бы одного из достижений «Три копья» и «Эхо водопада», добавить задание 30", quest30.description)
        assertEquals("Добавлено задание 30.", quest30.result)
    }

    @Test
    fun `условное улучшение набора охотника главы 8 сообщает о наличии достижения`() {
        // Подготовка
        val chapter = chapters.getValue(8)

        // Вызов проверяемого кода
        val has = evaluateChapterConditionalRewards(chapter, setOf("Голос Волтьяра")).single()
        val hasNot = evaluateChapterConditionalRewards(chapter, emptySet()).single()

        // Проверка
        assertEquals(ConditionOutcomeType.REWARD, has.type)
        assertEquals("Если есть достижение «Голос Волтьяра» — улучшение набора охотника", has.description)
        assertEquals("Достижение есть, улучшите набор охотника.", has.result)
        assertEquals("Достижения нет.", hasNot.result)
    }

    @Test
    fun `условное сообщение главы 4 показывается как условие с результатом`() {
        // Подготовка
        val chapter = ChapterInfo(chapter = 4, conditionalMessages = listOf(ConditionalMessage("Яд Пазиса", "Получите награду 25")))

        // Вызов проверяемого кода
        val has = evaluateChapterConditionalRewards(chapter, setOf("яд пазиса")).single()
        val hasNot = evaluateChapterConditionalRewards(chapter, emptySet()).single()

        // Проверка
        assertEquals("Если есть достижение «Яд Пазиса»: Получите награду 25", has.description)
        assertEquals("Достижение есть: Получите награду 25.", has.result)
        assertEquals("Достижения нет.", hasNot.result)
    }

    @Test
    fun `безусловное улучшение набора не попадает в условные награды`() {
        // Вызов проверяемого кода: глава 3 — улучшение набора без условия
        val outcomes = evaluateChapterConditionalRewards(chapters.getValue(3), emptySet())

        // Проверка
        assertTrue(outcomes.isEmpty())
    }

    @Test
    fun `все условия seed-каталогов имеют формулировку`() {
        // Вызов проверяемого кода
        val taskDescriptions = tasks.values.flatMap { task ->
            evaluateTaskConditions(task.victoryOpenQuestConditions + task.defeatOpenQuestConditions, emptySet(), 1, emptySet())
        }.map { it.description }
        val chapterDescriptions = chapters.values.flatMap { chapter ->
            evaluateChapterConditionalQuests(chapter.conditionalOpenQuests, emptySet()) +
                evaluateChapterConditionalRewards(chapter, emptySet())
        }.map { it.description }

        // Проверка
        (taskDescriptions + chapterDescriptions).forEach { description ->
            assertTrue(description.startsWith("Если "), description)
            assertFalse("«»" in description || "?" in description, description)
        }
    }

    @Test
    fun `нет ни одного из достижений — формулировка для отрицания без requireAll`() {
        // Подготовка
        val condition = ConditionalQuestOpen(listOf("А", "Б"), questNumber = 7, negated = true)

        // Вызов проверяемого кода
        val outcome = evaluateChapterConditionalQuests(listOf(condition), emptySet()).single()

        // Проверка
        assertEquals("Если нет ни одного из достижений «А» и «Б», добавить задание 7", outcome.description)
        assertEquals("Добавлено задание 7.", outcome.result)
    }

    @Test
    fun `условие без достижения в исходном правиле не ломает формулировку`() {
        // Подготовка
        val condition = TaskCondition(kind = TaskConditionKind.CHAPTER_IN, chapterSet = listOf(5, 6, 7), questNumber = 20)

        // Вызов проверяемого кода
        val outcome = evaluateTaskConditions(listOf(condition), emptySet(), 6, emptySet()).single()

        // Проверка
        assertEquals("Если текущая глава 5, 6 или 7, то добавить задание 20", outcome.description)
        assertEquals("Добавлено задание 20.", outcome.result)
    }

    //endregion
}
