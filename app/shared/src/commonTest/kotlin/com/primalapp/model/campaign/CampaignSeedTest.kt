package com.primalapp.model.campaign

import com.primalapp.database.chapterInfoSeedEntities
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.taskInfoSeedEntities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Проверка seed-каталогов после решений defects.md (C-5 – C-9, R-6). */
class CampaignSeedTest {

    private val tasks = taskInfoSeedEntities().map { it.toDomain() }.associateBy { it.questNumber }
    private val chapters = chapterInfoSeedEntities().map { it.toDomain() }.associateBy { it.chapter }

    //region C-5 – C-7. Каталог заданий

    @Test
    fun `C-5 поражение в задании 2 открывает задание 5 только в главе книги 1 или 2`() {
        // Подготовка
        val task = tasks.getValue(2)

        // Вызов проверяемого кода
        val conditions = task.defeatOpenQuestConditions

        // Проверка
        assertEquals(1, conditions.size)
        val condition = conditions.single()
        assertEquals(TaskConditionKind.CHAPTER_IN, condition.kind)
        assertEquals(listOf(1, 2), condition.chapterSet)
        assertEquals(5, condition.questNumber)
        assertNull(condition.elseQuestNumber)
        assertTrue(task.defeatOpenQuests.isEmpty(), "Задание 5 не открывается безусловно")
    }

    @Test
    fun `C-6 «Иридия» в заданиях 28 и 35 — материя, а не растение`() {
        // Подготовка
        val quest28 = tasks.getValue(28)
        val quest35 = tasks.getValue(35)

        // Проверка
        assertEquals(3, quest28.victoryMaterials[Material.IRIDIA])
        assertEquals(2, quest35.victoryMaterials[Material.IRIDIA])
    }

    @Test
    fun `C-7 количество ресурса задано в заданиях 2 и 14`() {
        // Проверка
        assertEquals(1, tasks.getValue(2).victoryMaterials[Material.IRIDIA])
        assertEquals(2, tasks.getValue(14).victoryMaterials[Material.ZLATIA])
    }

    //endregion

    //region C-8, C-9, R-6. Каталог глав

    @Test
    fun `C-8 глава 10 открывает задание 30 при отсутствии хотя бы одного из двух достижений`() {
        // Подготовка
        val chapter = chapters.getValue(10)

        // Вызов проверяемого кода
        val condition = chapter.conditionalOpenQuests.single { it.questNumber == 30 }

        // Проверка: requireAll + negated = «нет хотя бы одного»
        assertEquals(listOf("Три копья", "Эхо водопада"), condition.achievements)
        assertTrue(condition.requireAll)
        assertTrue(condition.negated)
    }

    @Test
    fun `C-9 улучшение набора охотника в главах 8 и 10 требует «Голос Волтьяра»`() {
        // Проверка
        listOf(8, 10).forEach { number ->
            val chapter = chapters.getValue(number)
            assertTrue(chapter.hunterKitUpgrade, "Глава $number")
            assertEquals("Голос Волтьяра", chapter.hunterKitUpgradeAchievement, "Глава $number")
        }
        assertNull(chapters.getValue(3).hunterKitUpgradeAchievement, "Остальные главы — без условия")
    }

    @Test
    fun `R-6 глава 11 истекает все задания и объявляет финальный бой с Пробуждённым`() {
        // Подготовка
        val chapter = chapters.getValue(11)

        // Проверка
        assertTrue(chapter.expireAllQuests)
        assertEquals("Пробуждённый", chapter.finalBossName)
        assertTrue(chapter.messages.any { it.contains("Пробуждённый") })
        chapters.values.filter { it.chapter != 11 }.forEach {
            assertFalse(it.expireAllQuests, "Глава ${it.chapter}")
            assertNull(it.finalBossName, "Глава ${it.chapter}")
        }
    }

    //endregion
}
