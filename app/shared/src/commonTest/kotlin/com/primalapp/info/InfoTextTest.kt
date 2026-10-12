package com.primalapp.info

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Разметка «Инфо»: абзацы, жирный, ссылки «См. также», поиск — как на сайте. */
class InfoTextTest {

    private val defence = InfoEntry(1, InfoSectionCode.KEYWORDS, "Защита / жетон защиты ( )", "", null)
    private val stamina = InfoEntry(2, InfoSectionCode.KEYWORDS, "Выносливость (выработка выносливости)", "", null)
    private val knockdown = InfoEntry(3, InfoSectionCode.KEYWORDS, "Потерять сознание", "Охотник падает.", null)
    private val resolve = InfoText.resolver(listOf(defence, stamina, knockdown))

    @Test
    fun `ссылка находит статью по точному названию, первой части и началу названия`() {
        // Проверка
        assertEquals(knockdown, resolve("Потерять сознание"))
        assertEquals(defence, resolve("Защита"))
        assertEquals(stamina, resolve("Выносливость"))
        assertEquals(null, resolve("Нет такой"))
    }

    @Test
    fun `абзац с жирным и ссылками «См также» делится на куски`() {
        // Подготовка
        val paragraph = "Текст **важно**. (См. также **«Потерять сознание»** , «Защита» .)"

        // Вызов проверяемого кода
        val segments = InfoText.segments(paragraph, resolve)

        // Проверка
        assertTrue(segments.contains(InfoSegment.Bold("важно")))
        assertEquals(listOf(knockdown, defence), segments.filterIsInstance<InfoSegment.Link>().map { it.entry })
        assertEquals("«Потерять сознание»", segments.filterIsInstance<InfoSegment.Link>().first().text)
    }

    @Test
    fun `абзацы — через пустую строку, поиск — по всем словам без учёта «ё»`() {
        // Проверка
        assertEquals(listOf("Первый абзац.", "Второй"), InfoText.paragraphs("Первый\nабзац.\n\nВторой"))
        assertTrue(InfoText.matches(knockdown, "охотник ПАДАЕТ"))
        assertTrue(InfoText.matches(InfoEntry(4, InfoSectionCode.TOKENS, "Ёж", "", null), "еж"))
    }

    @Test
    fun `у символа реакции без названия подпись — начало описания`() {
        // Подготовка
        val reaction = InfoEntry(5, InfoSectionCode.REACTIONS, null, "**Активация:** в конце раунда", null)

        // Проверка
        assertEquals("Активация: в конце раунда", InfoText.label(reaction))
    }
}
