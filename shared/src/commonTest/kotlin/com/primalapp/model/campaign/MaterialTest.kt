package com.primalapp.model.campaign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MaterialTest {

    //region 28.6. Материи — состав, порядок, отсутствие CRYSTAL

    @Test
    fun `Material содержит 6 значений в заданном порядке без CRYSTAL`() {
        // Подготовка: ожидаемый состав материй по правилам и задаче 28.6
        val expectedNames = listOf(
            Material.SCALES,
            Material.BONES,
            Material.BLOOD,
            Material.ZIMIA,
            Material.IRIDIA,
            Material.ZLATIA
        )
        val expectedDisplayNames = listOf(
            "Чешуя", "Кости", "Кровь", "Зимия", "Иридия", "Златия"
        )

        // Вызов проверяемого кода
        val actual = Material.entries.toList()

        // Проверка: 6 значений, порядок объявления, русские названия, отсутствие CRYSTAL
        assertEquals(6, actual.size, "Должно быть ровно 6 материй (без CRYSTAL)")
        assertEquals(expectedNames, actual, "Порядок материй должен быть заданным")
        assertEquals(expectedDisplayNames, actual.map { it.displayName },
            "Русские названия материй должны соответствовать порядку")
        assertFalse(actual.any { it.name == "CRYSTAL" },
            "Материя CRYSTAL (Кристалл) должна быть удалена")
    }

    @Test
    fun `Material entries содержат уникальные имена`() {
        // Подготовка
        val materialNames = Material.entries.map { it.name }

        // Вызов проверяемого кода
        val uniqueNames = materialNames.toSet()

        // Проверка: все имена уникальны
        assertEquals(materialNames.size, uniqueNames.size,
            "Имена материй не должны повторяться")
    }

    //endregion

    //region 28.6. Растения — состав и порядок

    @Test
    fun `Plant содержит 6 значений в заданном порядке`() {
        // Подготовка: ожидаемый состав растений по правилам и задаче 28.6
        val expectedNames = listOf(
            Plant.NILLEA,
            Plant.TARMARET,
            Plant.ALBALACEA,
            Plant.MELLIS,
            Plant.ANTHEMON,
            Plant.SELICORNIA
        )
        val expectedDisplayNames = listOf(
            "Ниллея", "Тармарет", "Альбалацея", "Меллис", "Антемон", "Селикорния"
        )

        // Вызов проверяемого кода
        val actual = Plant.entries.toList()

        // Проверка: 6 значений, порядок объявления, русские названия
        assertEquals(6, actual.size, "Должно быть ровно 6 растений")
        assertEquals(expectedNames, actual, "Порядок растений должен быть заданным")
        assertEquals(expectedDisplayNames, actual.map { it.displayName },
            "Русские названия растений должны соответствовать порядку")
    }

    @Test
    fun `Plant entries содержат уникальные имена`() {
        // Подготовка
        val plantNames = Plant.entries.map { it.name }

        // Вызов проверяемого кода
        val uniqueNames = plantNames.toSet()

        // Проверка: все имена уникальны
        assertEquals(plantNames.size, uniqueNames.size,
            "Имена растений не должны повторяться")
    }

    //endregion
}
