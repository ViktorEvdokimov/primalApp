package com.primalapp.model.campaign

import com.primalapp.database.entity.TaskInfoEntity
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.mapper.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskInfoTest {

    //region 31.1T. Маппинг TaskInfoEntity ↔ TaskInfo

    @Test
    fun `toDomain декодирует материи, растения, списки и особую награду`() {
        // Подготовка: сущность с заполненными текстовыми полями
        val entity = TaskInfoEntity(
            questNumber = 1,
            name = "Память пустыни",
            bossName = "Торамат",
            bossElement = "HORN",
            victoryMaterials = "BONES:2;ZLATIA:2",
            victoryPlants = "NILLEA:2;TARMARET:1",
            victoryOpenQuests = "4,6",
            victoryAchievements = "Затишье;Тайны прошлого",
            victoryRewardCards = "1;2",
            victorySpecial = "Каждый охотник получает 2 карты крови изначального",
            defeatOpenQuests = "6"
        )

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: мапы материй/растений, списки и особая награда декодированы корректно
        assertEquals(1, domain.questNumber)
        assertEquals("Память пустыни", domain.name)
        assertEquals("Торамат", domain.bossName)
        assertEquals(Element.HORN, domain.bossElement)
        assertEquals(mapOf(Material.BONES to 2, Material.ZLATIA to 2), domain.victoryMaterials)
        assertEquals(mapOf(Plant.NILLEA to 2, Plant.TARMARET to 1), domain.victoryPlants)
        assertEquals(listOf(4, 6), domain.victoryOpenQuests)
        assertEquals(listOf("Затишье", "Тайны прошлого"), domain.victoryAchievements)
        assertEquals(listOf("1", "2"), domain.victoryRewardCards)
        assertEquals("Каждый охотник получает 2 карты крови изначального", domain.victorySpecial)
        assertEquals(listOf(6), domain.defeatOpenQuests)
    }

    @Test
    fun `toEntity кодирует мапы и списки в текстовый формат`() {
        // Подготовка: доменная модель с материями, растениями, списками
        val domain = TaskInfo(
            questNumber = 33,
            name = "Последняя страница",
            bossName = "Кситерос",
            bossElement = Element.FEATHER,
            victoryMaterials = mapOf(Material.BONES to 1, Material.ZIMIA to 2),
            victoryPlants = mapOf(Plant.NILLEA to 2, Plant.MELLIS to 2),
            victoryOpenQuests = listOf(7),
            victoryAchievements = listOf("Грибной лес"),
            victoryRewardCards = listOf("7"),
            victorySpecial = "Каждый охотник получает 2 карты крови изначального",
            defeatOpenQuests = listOf(3)
        )

        // Вызов проверяемого кода
        val entity = domain.toEntity()

        // Проверка: кодирование в строковый формат NAME:qty через ';' и списков через ','
        assertEquals(33, entity.questNumber)
        assertEquals("BONES:1;ZIMIA:2", entity.victoryMaterials)
        assertEquals("NILLEA:2;MELLIS:2", entity.victoryPlants)
        assertEquals("7", entity.victoryOpenQuests)
        assertEquals("Грибной лес", entity.victoryAchievements)
        assertEquals("7", entity.victoryRewardCards)
        assertEquals("3", entity.defeatOpenQuests)
    }

    @Test
    fun `toDomain для пустых строк возвращает пустые коллекции`() {
        // Подготовка: сущность только с обязательными полями
        val entity = TaskInfoEntity(
            questNumber = 2,
            name = "Полёт в вечную бурю",
            bossName = "Озев",
            bossElement = "LIGHTNING"
        )

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: пустые коллекции при пустых строках
        assertTrue(domain.victoryMaterials.isEmpty())
        assertTrue(domain.victoryPlants.isEmpty())
        assertTrue(domain.victoryOpenQuests.isEmpty())
        assertTrue(domain.victoryAchievements.isEmpty())
        assertTrue(domain.victoryRewardCards.isEmpty())
        assertEquals("", domain.victorySpecial)
        assertTrue(domain.defeatOpenQuests.isEmpty())
    }

    @Test
    fun `toDomain игнорирует некорректные значения в мапах и списках`() {
        // Подготовка: сущность с некорректными/частично неверными значениями
        val entity = TaskInfoEntity(
            questNumber = 3,
            name = "Рёв моря",
            bossName = "Коровон",
            bossElement = "CORAL",
            victoryMaterials = "SCALES:1;BOGUS:5;ZLATIA:abc",
            victoryPlants = "NILLEA:2;:3",
            victoryOpenQuests = "10,abc,15,,",
            victoryAchievements = "Затишье;;Тайны прошлого"
        )

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: некорректные пары игнорируются, корректные сохраняются
        assertEquals(mapOf(Material.SCALES to 1), domain.victoryMaterials,
            "Некорректные материи (неизвестное имя, нечисловое количество) должны игнорироваться")
        assertEquals(mapOf(Plant.NILLEA to 2), domain.victoryPlants,
            "Некорректные растения должны игнорироваться")
        assertEquals(listOf(10, 15), domain.victoryOpenQuests,
            "Некорректные и пустые номера заданий должны игнорироваться")
        assertEquals(listOf("Затишье", "Тайны прошлого"), domain.victoryAchievements,
            "Пустые элементы списка достижений должны игнорироваться")
    }

    @Test
    fun `toDomain поддерживает null bossElement`() {
        // Подготовка: задание без стихии босса
        val entity = TaskInfoEntity(
            questNumber = 40,
            name = "Бездна под водопадом",
            bossName = "Иекорос",
            bossElement = null
        )

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: bossElement = null
        assertEquals(null, domain.bossElement)
    }

    //endregion
}
