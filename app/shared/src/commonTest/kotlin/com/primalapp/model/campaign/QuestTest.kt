package com.primalapp.model.campaign

import com.primalapp.database.entity.QuestEntity
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.mapper.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class QuestTest {

    //region Модель Quest — questNumber

    @Test
    fun `Quest questNumber имеет значение по умолчанию 0`() {
        // Подготовка: создаём Quest без указания questNumber

        // Вызов проверяемого кода
        val quest = Quest(id = "1", name = "Задание 1", chapter = 1)

        // Проверка: questNumber = 0 по умолчанию
        assertEquals(0, quest.questNumber, "questNumber должен быть 0 по умолчанию")
    }

    @Test
    fun `Quest questNumber сохраняет переданное значение`() {
        // Подготовка: создаём Quest с явным questNumber

        // Вызов проверяемого кода
        val quest = Quest(id = "42", name = "Задание 42", chapter = 3, questNumber = 42)

        // Проверка: questNumber = 42
        assertEquals(42, quest.questNumber, "questNumber должен сохранять переданное значение")
    }

    //endregion

    //region QuestEntity — questNumber

    @Test
    fun `QuestEntity questNumber поле присутствует и сохраняет значение`() {
        // Подготовка: создаём QuestEntity с questNumber

        // Вызов проверяемого кода
        val entity = QuestEntity(
            campaignId = 1,
            questId = "5",
            name = "Задание 5",
            chapter = 2,
            questNumber = 5
        )

        // Проверка: questNumber = 5
        assertEquals(5, entity.questNumber, "questNumber должен сохранять переданное значение")
    }

    @Test
    fun `QuestEntity questNumber имеет значение по умолчанию 0`() {
        // Подготовка: создаём QuestEntity без questNumber

        // Вызов проверяемого кода
        val entity = QuestEntity(
            campaignId = 1,
            questId = "1",
            name = "Задание 1",
            chapter = 1
        )

        // Проверка: questNumber = 0 по умолчанию
        assertEquals(0, entity.questNumber, "questNumber должен быть 0 по умолчанию")
    }

    //endregion

    //region CampaignMapper — QuestEntity ↔ Quest questNumber

    @Test
    fun `CampaignMapper QuestEntity toDomain пробрасывает questNumber`() {
        // Подготовка: создаём QuestEntity с questNumber = 17
        val entity = QuestEntity(
            campaignId = 1,
            questId = "17",
            name = "Задание 17",
            chapter = 4,
            element = "FIRE",
            questNumber = 17,
            isCompleted = true,
            isAvailable = false
        )

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: questNumber проброшен
        assertEquals(17, domain.questNumber, "questNumber должен быть проброшен в domain")
    }

    @Test
    fun `CampaignMapper Quest toEntity пробрасывает questNumber`() {
        // Подготовка: создаём Quest с questNumber = 23
        val quest = Quest(
            id = "23",
            name = "Задание 23",
            chapter = 5,
            element = Element.ICE,
            questNumber = 23,
            isCompleted = false,
            isAvailable = true
        )

        // Вызов проверяемого кода
        val entity = quest.toEntity(campaignId = 2)

        // Проверка: questNumber проброшен
        assertEquals(23, entity.questNumber, "questNumber должен быть проброшен в entity")
    }

    @Test
    fun `CampaignMapper roundtrip Quest toEntity toDomain сохраняет questNumber`() {
        // Подготовка: создаём Quest
        val original = Quest(
            id = "49",
            name = "Задание 49",
            chapter = 10,
            element = Element.LIGHTNING,
            questNumber = 49,
            isCompleted = false,
            isAvailable = false
        )

        // Вызов проверяемого кода: туда-обратно через маппер
        val entity = original.toEntity(campaignId = 1)
        val restored = entity.toDomain()

        // Проверка: questNumber сохранился после roundtrip
        assertEquals(original.questNumber, restored.questNumber,
            "questNumber должен сохраниться после преобразования domain → entity → domain")
    }

    //endregion
}
