package com.primalapp.catalog

import com.primalapp.catalog.EffectDescriber.Context
import com.primalapp.model.campaign.Expansion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Язык эффектов каталога: формулировки условных правил и результат их проверки (как на сервере). */
class EffectRulesTest {

    private val catalog = TestCatalog.catalog

    private fun facts(
        achievements: Set<String> = emptySet(),
        chapter: Int = 1,
        available: Set<Int> = emptySet(),
        expansions: Set<Expansion> = Expansion.entries.toSet()
    ) = CampaignFacts(achievements, chapter, available, expansions)

    private fun questRules(number: Int, facts: CampaignFacts): List<RuleExplanation> =
        catalog.planner.plan(catalog.quest(number)!!.victory, facts, Context.QUEST).explanations

    private fun chapterRules(number: Int, facts: CampaignFacts): List<RuleExplanation> =
        catalog.planner.plan(catalog.chapter(number)!!.effects, facts, Context.CHAPTER).explanations

    @Test
    fun `каталог по умолчанию разобран целиком`() {
        // Проверка
        assertEquals(23, catalog.bosses.size)
        assertEquals(49, catalog.quests.size)
        assertEquals(11, catalog.chapters.size)
        assertEquals(9 * 12, catalog.forge.size)
        assertEquals(6, catalog.lab.size)
        assertEquals(listOf(3), catalog.boss("AWAKENED")!!.difficulties)
    }

    @Test
    fun `задание 1 — условие по главе с «иначе»`() {
        // Вызов проверяемого кода
        val inChapter = questRules(1, facts(chapter = 1)).single()
        val otherChapter = questRules(1, facts(chapter = 3)).single()

        // Проверка
        assertEquals(RuleKind.QUEST, inChapter.kind)
        assertEquals("Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6", inChapter.description)
        assertEquals("Добавлено задание 4.", inChapter.result)
        assertEquals("Добавлено задание 6.", otherChapter.result)
    }

    @Test
    fun `невыполненное условие без альтернативы не добавляет задание`() {
        // Вызов проверяемого кода
        val rule = questRules(2, facts(chapter = 4)).single()

        // Проверка
        assertEquals("Если текущая глава 1 или 2, то добавить задание 5", rule.description)
        assertEquals("Условие не выполнено, задание не добавляется.", rule.result)
    }

    @Test
    fun `задание 25 — вложенное условие с дополнением «Кошмар»`() {
        // Вызов проверяемого кода
        val withNightmare = questRules(25, facts(setOf("GORYASHCHIY_UGOLEK"), chapter = 8)).single()
        val withoutNightmare = questRules(25, facts(setOf("GORYASHCHIY_UGOLEK"), chapter = 8, expansions = emptySet())).single()
        val withoutAchievement = questRules(25, facts(chapter = 8)).single()

        // Проверка
        assertEquals(
            "Если есть достижение «Горящий уголёк»: если текущая глава 8 и есть дополнение «Кошмар», то добавить задание 34, " +
                "иначе добавить задание 27",
            withNightmare.description
        )
        assertEquals("Добавлено задание 34.", withNightmare.result)
        assertEquals("Добавлено задание 27.", withoutNightmare.result)
        assertEquals("Достижения нет, задание не добавляется.", withoutAchievement.result)
    }

    @Test
    fun `задание 29 — условное достижение`() {
        // Вызов проверяемого кода
        val has = questRules(29, facts(setOf("GOLOS_VOLTYARA"))).single()
        val hasNot = questRules(29, facts()).single()

        // Проверка
        assertEquals(RuleKind.ACHIEVEMENT, has.kind)
        assertEquals("Если есть достижение «Голос Волтьяра», добавить достижение «Уробборос»", has.description)
        assertEquals("Достижение есть, добавлено достижение «Уробборос».", has.result)
        assertEquals("Достижения нет.", hasNot.result)
    }

    @Test
    fun `задание 42 — задание 45, если задание 18 ещё не доступно`() {
        // Вызов проверяемого кода
        val notAvailable = questRules(42, facts(available = setOf(30))).single()
        val available = questRules(42, facts(available = setOf(18))).single()

        // Проверка
        assertEquals("Если задание 18 ещё не доступно, добавить задание 45", notAvailable.description)
        assertEquals("Добавлено задание 45.", notAvailable.result)
        assertEquals("Условие не выполнено, задание не добавляется.", available.result)
    }

    @Test
    fun `глава 5 — «открыть задание, иначе добавить задание»`() {
        // Вызов проверяемого кода
        val rule = chapterRules(5, facts()).single { it.description.contains("Упавшая звезда") }

        // Проверка
        assertEquals("Если есть достижение «Упавшая звезда», открыть задание 15, иначе добавить задание 47", rule.description)
        assertEquals("Добавлено задание 47.", rule.result)
    }

    @Test
    fun `глава 10 — «нет хотя бы одного из достижений»`() {
        // Вызов проверяемого кода
        val rule = chapterRules(10, facts(setOf("TRI_KOPYA"))).single { it.description.endsWith("задание 30") }

        // Проверка
        assertEquals("Если нет хотя бы одного из достижений «Три копья» и «Эхо водопада», добавить задание 30", rule.description)
        assertEquals("Добавлено задание 30.", rule.result)
    }

    @Test
    fun `глава 8 — условное улучшение набора охотника, глава 4 — условное сообщение`() {
        // Вызов проверяемого кода
        val kit = chapterRules(8, facts(setOf("GOLOS_VOLTYARA"))).single { it.kind == RuleKind.KIT }
        val noKit = chapterRules(8, facts()).single { it.kind == RuleKind.KIT }
        val message = chapterRules(4, facts(setOf("YAD_PAZISA"))).single { it.kind == RuleKind.MESSAGE }

        // Проверка
        assertEquals("Если есть достижение «Голос Волтьяра» — улучшение набора охотника", kit.description)
        assertEquals("Достижение есть, улучшите набор охотника.", kit.result)
        assertEquals("Достижения нет.", noKit.result)
        assertEquals("Если есть достижение «Яд Пазиса»: Получите награду 25", message.description)
        assertEquals("Достижение есть: Получите награду 25.", message.result)
    }

    @Test
    fun `все правила каталога имеют формулировку «Если …»`() {
        // Вызов проверяемого кода
        val descriptions = catalog.quests.flatMap { catalog.describer.rules(it.victory + it.expired, Context.QUEST) } +
            catalog.chapters.flatMap { catalog.describer.rules(it.effects, Context.CHAPTER) }

        // Проверка
        assertTrue(descriptions.isNotEmpty())
        descriptions.forEach { assertTrue(it.startsWith("Если ") && "«»" !in it, it) }
    }
}
