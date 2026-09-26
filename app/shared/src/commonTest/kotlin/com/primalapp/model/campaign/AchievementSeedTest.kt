package com.primalapp.model.campaign

import com.primalapp.database.chapterInfoSeedEntities
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.taskInfoSeedEntities
import com.primalapp.domain.normalizeAchievementName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Сквозная проверка реальных seed-каталогов (задача 42.1): достижение выдаётся каталогом заданий
 * (или решением главы), а проверяется условиями каталога глав и заданий — написание должно совпадать.
 */
class AchievementSeedTest {

    private val tasks = taskInfoSeedEntities().map { it.toDomain() }
    private val chapters = chapterInfoSeedEntities().map { it.toDomain() }

    /** Достижения, которые можно получить: награды заданий, условные награды, решения глав. */
    private fun grantedAchievements(): Set<String> = buildSet {
        tasks.forEach { task ->
            addAll(task.victoryAchievements)
            addAll(task.defeatAchievements)
            (task.victoryOpenQuestConditions + task.defeatOpenQuestConditions)
                .mapNotNullTo(this) { it.rewardAchievement }
        }
        chapters.forEach { chapter -> chapter.decisions.mapNotNullTo(this) { it.achievementOnOption } }
    }

    /** Достижения, которые проверяются условиями глав и заданий. */
    private fun checkedAchievements(): Set<String> = buildSet {
        chapters.forEach { chapter ->
            chapter.conditionalOpenQuests.forEach { addAll(it.achievements) }
            chapter.conditionalMessages.forEach { add(it.achievementName) }
        }
        tasks.forEach { task ->
            (task.victoryOpenQuestConditions + task.defeatOpenQuestConditions)
                .mapNotNullTo(this) { it.achievementName }
        }
    }

    //region 42.1T. Согласованность названий достижений в seed

    @Test
    fun `каждое проверяемое условиями достижение выдаётся в том же написании`() {
        // Подготовка: выдаваемые достижения из seed
        val granted = grantedAchievements()

        // Вызов проверяемого кода: поиск проверяемых достижений без точного совпадения
        val missing = checkedAchievements().filterNot { it in granted }

        // Проверка
        assertTrue(missing.isEmpty(), "Достижения из условий не совпадают с выдаваемыми: $missing")
    }

    @Test
    fun `одно достижение не записано в разных написаниях`() {
        // Подготовка: все названия достижений из seed
        val names = grantedAchievements() + checkedAchievements()

        // Вызов проверяемого кода: группировка по нормализованному названию
        val conflicts = names.groupBy { normalizeAchievementName(it) }.filterValues { it.size > 1 }

        // Проверка
        assertTrue(conflicts.isEmpty(), "Достижения с разным написанием: $conflicts")
    }

    @Test
    fun `достижения заданий 5 и 36 совпадают с условиями главы 4`() {
        // Подготовка: задания 5, 36 и глава 4 из seed
        val quest5 = tasks.single { it.questNumber == 5 }
        val quest36 = tasks.single { it.questNumber == 36 }
        val chapter4 = chapters.single { it.chapter == 4 }

        // Вызов проверяемого кода: достижения, которые проверяет глава 4
        val conditionAchievements = chapter4.conditionalOpenQuests.flatMap { it.achievements }
        val messageAchievements = chapter4.conditionalMessages.map { it.achievementName }

        // Проверка: «Народ Золотых гор» (зад. 5 → гл. 4, задание 7) и «Яд Пазиса» (зад. 36 → «Получите награду 25»)
        assertTrue("Народ Золотых гор" in quest5.victoryAchievements)
        assertTrue("Народ Золотых гор" in conditionAchievements)
        assertTrue("Яд Пазиса" in quest36.victoryAchievements)
        assertEquals(listOf("Яд Пазиса"), messageAchievements)
    }

    //endregion
}
