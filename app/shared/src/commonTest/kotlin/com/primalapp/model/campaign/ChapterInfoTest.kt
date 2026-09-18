package com.primalapp.model.campaign

import com.primalapp.database.entity.ChapterInfoEntity
import com.primalapp.database.entity.TaskInfoEntity
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.mapper.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChapterInfoTest {

    //region 31.2T. Маппинг TaskCondition

    @Test
    fun `TaskCondition CHAPTER_IN кодируется и декодируется с chapterSet и else`() {
        // Подготовка: условие по главам с else-заданием
        val condition = TaskCondition(
            kind = TaskConditionKind.CHAPTER_IN,
            chapterSet = listOf(1, 2),
            questNumber = 4,
            elseQuestNumber = 6
        )
        val entity = TaskInfo(
            questNumber = 1,
            name = "Память пустыни",
            bossName = "Торамат",
            bossElement = Element.HORN,
            victoryOpenQuestConditions = listOf(condition)
        ).toEntity()

        // Вызов проверяемого кода
        val decoded = entity.toDomain().victoryOpenQuestConditions

        // Проверка: вид, список глав, quest/else декодированы
        assertEquals(1, decoded.size)
        assertEquals(TaskConditionKind.CHAPTER_IN, decoded[0].kind)
        assertEquals(listOf(1, 2), decoded[0].chapterSet)
        assertEquals(4, decoded[0].questNumber)
        assertEquals(6, decoded[0].elseQuestNumber)
    }

    @Test
    fun `TaskCondition ACHIEVEMENT_OWNED кодируется и декодируется`() {
        // Подготовка: условие по достижению
        val condition = TaskCondition(
            kind = TaskConditionKind.ACHIEVEMENT_OWNED,
            achievementName = "Грибной лес",
            questNumber = 32,
            elseQuestNumber = 17
        )
        val entity = TaskInfo(
            questNumber = 11,
            name = "Город памяти",
            bossName = "Харджа",
            bossElement = Element.FIRE,
            victoryOpenQuestConditions = listOf(condition)
        ).toEntity()

        // Вызов проверяемого кода
        val decoded = entity.toDomain().victoryOpenQuestConditions

        // Проверка: вид и имя достижения
        assertEquals(1, decoded.size)
        assertEquals(TaskConditionKind.ACHIEVEMENT_OWNED, decoded[0].kind)
        assertEquals("Грибной лес", decoded[0].achievementName)
        assertEquals(32, decoded[0].questNumber)
        assertEquals(17, decoded[0].elseQuestNumber)
    }

    @Test
    fun `TaskCondition пустые значения декодируются корректно`() {
        // Подготовка: условие без chapterSet и else
        val condition = TaskCondition(kind = TaskConditionKind.ACHIEVEMENT_NOT_OWNED, achievementName = "Затишье")
        val entity = TaskInfo(
            questNumber = 25,
            name = "Горящее солнце",
            bossName = "Харджа",
            bossElement = Element.FIRE,
            victoryOpenQuestConditions = listOf(condition)
        ).toEntity()

        // Вызов проверяемого кода
        val decoded = entity.toDomain().victoryOpenQuestConditions

        // Проверка: пустые chapterSet и null quest/else
        assertEquals(1, decoded.size)
        assertEquals(TaskConditionKind.ACHIEVEMENT_NOT_OWNED, decoded[0].kind)
        assertEquals("Затишье", decoded[0].achievementName)
        assertTrue(decoded[0].chapterSet.isEmpty())
        assertEquals(null, decoded[0].questNumber)
        assertEquals(null, decoded[0].elseQuestNumber)
    }

    @Test
    fun `TaskCondition некорректная строка игнорируется`() {
        // Подготовка: сущность с повреждённой строкой условия
        val entity = TaskInfoEntity(
            questNumber = 3,
            name = "Рёв моря",
            bossName = "Коровон",
            bossElement = "CORAL",
            victoryOpenQuestConditions = "BADFORMAT;CHAPTER_IN|Затишье|1,2|5|"
        )

        // Вызов проверяемого кода
        val decoded = entity.toDomain().victoryOpenQuestConditions

        // Проверка: некорректная запись отброшена, корректная сохранена
        assertEquals(1, decoded.size, "Некорректное условие должно игнорироваться")
        assertEquals(TaskConditionKind.CHAPTER_IN, decoded[0].kind)
    }

    //endregion

    //region 31.2T. Маппинг ChapterInfo

    @Test
    fun `ChapterInfo с наградами, открытием, истечением и флагами декодируется`() {
        // Подготовка: сущность главы с заполненными полями
        val entity = ChapterInfoEntity(
            chapter = 1,
            rewards = "BONES:1;SCALES:1;BLOOD:2",
            rewardPlants = "ALBALACEA:1;ANTHEMON:1;MELLIS:1;NILLEA:2",
            openQuests = "1,2,36",
            expireQuests = "2,36",
            forgeUpgrade = false,
            labUpgrade = false,
            hunterKitUpgrade = true
        )

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: награды, списки заданий и флаг набора охотника
        assertEquals(1, domain.chapter)
        assertEquals(mapOf(Material.BONES to 1, Material.SCALES to 1, Material.BLOOD to 2), domain.rewards)
        assertEquals(mapOf(Plant.ALBALACEA to 1, Plant.ANTHEMON to 1, Plant.MELLIS to 1, Plant.NILLEA to 2), domain.rewardPlants)
        assertEquals(listOf(1, 2, 36), domain.openQuests)
        assertEquals(listOf(2, 36), domain.expireQuests)
        assertTrue(domain.hunterKitUpgrade)
    }

    @Test
    fun `ChapterInfo с условными открытиями декодируется`() {
        // Подготовка: сущность главы 4 с условным открытием (Народ Золотых гор → 7/8)
        val entity = ChapterInfoEntity(
            chapter = 4,
            conditionalOpenQuests = "Народ Золотых гор:7:8:0:0"
        )

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: условное открытие с achievements, else, requireAll/negated
        assertEquals(1, domain.conditionalOpenQuests.size)
        val condition = domain.conditionalOpenQuests[0]
        assertEquals(listOf("Народ Золотых гор"), condition.achievements)
        assertEquals(7, condition.questNumber)
        assertEquals(8, condition.elseQuestNumber)
        assertEquals(false, condition.requireAll)
        assertEquals(false, condition.negated)
    }

    @Test
    fun `ChapterDecision гл 7 декодируется`() {
        // Подготовка: сущность главы 7 с решением «Тренироваться у подножья Волтьяра»
        // (формат маппера: Вопрос?варианты|через?достижение?вариантДостижения)
        val entity = ChapterInfoEntity(
            chapter = 7,
            decisions = "Тренироваться у подножья Волтьяра?Да|Нет?Голос Волтьяра?Да"
        )

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: вопрос, варианты, достижение и вариант для достижения
        assertEquals(1, domain.decisions.size)
        val decision = domain.decisions[0]
        assertEquals("Тренироваться у подножья Волтьяра", decision.question)
        assertEquals(listOf("Да", "Нет"), decision.options)
        assertEquals("Голос Волтьяра", decision.achievementOnOption)
        assertEquals("Да", decision.optionForAchievement)
    }

    @Test
    fun `ConditionalMessage декодируется`() {
        // Подготовка: сущность главы 4 с условным сообщением
        val entity = ChapterInfoEntity(
            chapter = 4,
            conditionalMessages = "Яд Пазиса:Получите награду 25"
        )

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: имя достижения и текст сообщения
        assertEquals(1, domain.conditionalMessages.size)
        val message = domain.conditionalMessages[0]
        assertEquals("Яд Пазиса", message.achievementName)
        assertEquals("Получите награду 25", message.message)
    }

    @Test
    fun `ChapterInfo пустые значения декодируются в пустые коллекции`() {
        // Подготовка: сущность главы без необязательных полей
        val entity = ChapterInfoEntity(chapter = 2)

        // Вызов проверяемого кода
        val domain = entity.toDomain()

        // Проверка: пустые коллекции и сброшенные флаги
        assertTrue(domain.rewards.isEmpty())
        assertTrue(domain.rewardPlants.isEmpty())
        assertTrue(domain.openQuests.isEmpty())
        assertTrue(domain.expireQuests.isEmpty())
        assertTrue(domain.conditionalOpenQuests.isEmpty())
        assertTrue(domain.decisions.isEmpty())
        assertTrue(domain.messages.isEmpty())
        assertTrue(domain.conditionalMessages.isEmpty())
    }

    //endregion
}
