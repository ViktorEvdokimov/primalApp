package com.primalapp.model.campaign

import com.primalapp.database.entity.TrophyEntity
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.mapper.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrophyTest {

    //region 18.x. Trophy с null element

    @Test
    fun `Trophy допускает element null`() {
        // Подготовка: трофей без стихии (Пробуждённый)

        // Вызов проверяемого кода
        val trophy = Trophy(bossName = "Пробуждённый", element = null, chapter = 9)

        // Проверка
        assertEquals("Пробуждённый", trophy.bossName)
        assertNull(trophy.element, "element должен быть null")
    }

    @Test
    fun `TrophyEntity toDomain маппит null element`() {
        // Подготовка: TrophyEntity с element = null
        val entity = TrophyEntity(
            campaignId = 1,
            bossName = "Пробуждённый",
            element = null,
            chapter = 9,
            acquiredAt = 100L
        )

        // Вызов проверяемого кода
        val trophy = entity.toDomain()

        // Проверка
        assertEquals("Пробуждённый", trophy.bossName)
        assertNull(trophy.element, "element должен остаться null после toDomain")
        assertEquals(9, trophy.chapter)
    }

    @Test
    fun `Trophy toEntity маппит null element`() {
        // Подготовка: Trophy без стихии
        val trophy = Trophy(bossName = "Пробуждённый", element = null, chapter = 9, acquiredAt = 100L)

        // Вызов проверяемого кода
        val entity = trophy.toEntity(campaignId = 1)

        // Проверка
        assertNull(entity.element, "element должен остаться null после toEntity")
        assertEquals("Пробуждённый", entity.bossName)
    }

    @Test
    fun `Trophy roundtrip сохраняет null element`() {
        // Подготовка: Trophy без стихии
        val original = Trophy(bossName = "Пробуждённый", element = null, chapter = 9)

        // Вызов проверяемого кода: туда-обратно
        val entity = original.toEntity(campaignId = 1)
        val restored = entity.toDomain()

        // Проверка
        assertNull(restored.element, "element должен остаться null после roundtrip")
        assertEquals(original.bossName, restored.bossName)
        assertEquals(original.chapter, restored.chapter)
    }

    //endregion
}
