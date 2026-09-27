package com.primalapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementNamesTest {

    //region 42.1T. Сравнение названий достижений

    @Test
    fun `normalizeAchievementName убирает регистр, ё и лишние пробелы`() {
        // Подготовка: одно название в разных написаниях
        val variants = listOf("Копьё драконоборца", "копье  драконоборца", "  КОПЬЕ ДРАКОНОБОРЦА ")

        // Вызов проверяемого кода
        val normalized = variants.map { normalizeAchievementName(it) }.toSet()

        // Проверка: все варианты сводятся к одной форме
        assertEquals(setOf("копье драконоборца"), normalized)
    }

    @Test
    fun `achievementMatches сравнивает названия без учёта регистра`() {
        // Подготовка: написания из каталога заданий и каталога глав (зад. 36 / гл. 4)

        // Вызов проверяемого кода и проверка
        assertTrue(achievementMatches("Яд пазиса", "Яд Пазиса"))
        assertTrue(achievementMatches("Народ золотых гор", "Народ Золотых гор"))
        assertFalse(achievementMatches("Звезда дракона", "Упавшая звезда"),
            "Разные достижения не должны совпадать")
    }

    @Test
    fun `containsAchievement ищет достижение в другом написании`() {
        // Подготовка: достижения кампании
        val owned = listOf("Затишье", "яд пазиса")

        // Вызов проверяемого кода и проверка
        assertTrue(owned.containsAchievement("Яд Пазиса"))
        assertFalse(owned.containsAchievement("Народ Золотых гор"))
    }

    //endregion
}
