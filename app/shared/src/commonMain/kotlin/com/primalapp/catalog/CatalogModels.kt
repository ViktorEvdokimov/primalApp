package com.primalapp.catalog

import com.primalapp.domain.achievementMatches
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.ForgeSlot
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant

/** Когда монстр переходит на следующую стойку. */
enum class StanceChangeMode { HEALTH, ON_DEMAND, FINAL }

/**
 * Стойка босса на одном уровне враждебности.
 *
 * @param toughnessPerHunter прочность за охотника; `null` — нет порога раны, урон только копится
 * @param changeAtHealth здоровье перехода для [StanceChangeMode.HEALTH], иначе `null`
 */
data class StanceDef(
    val stance: Int,
    val toughnessPerHunter: Int?,
    val changeMode: StanceChangeMode,
    val changeAtHealth: Int?
)

/**
 * Босс каталога.
 *
 * @param element стихия; `null` — без стихии (Пробуждённый)
 * @param stances стойки по уровням враждебности 0–3; есть только доступные уровни
 */
data class BossDef(
    val code: String,
    val name: String,
    val element: Element?,
    val expansion: Expansion?,
    val sortOrder: Int,
    val stances: Map<Int, List<StanceDef>>
) {
    val difficulties: List<Int> get() = stances.keys.sorted()

    fun stances(difficulty: Int): List<StanceDef> = stances[difficulty].orEmpty()
}

/** Достижение каталога: код (на него ссылаются эффекты и кампании) и каноническое название. */
data class AchievementDef(val code: String, val name: String)

/**
 * Задание каталога.
 *
 * @param victory эффекты победы (без общих правил: трофей, 2 стихии босса, выполнение задания)
 * @param expired последствия невыполненного задания — когда истекает его время
 */
data class QuestDef(
    val number: Int,
    val name: String,
    val bossCode: String,
    val expansion: Expansion?,
    val victory: List<Effect>,
    val expired: List<Effect>
)

/** Глава кампании (книжная нумерация 1–11): эффекты применяются при переходе в главу. */
data class ChapterDef(val chapter: Int, val effects: List<Effect>, val decisions: List<Decision>)

/**
 * Предмет планшета кузни.
 *
 * @param code `FIRE_01`…`FIRE_12`: стихия и место на планшете — одинаковы на всех уровнях
 * @param costs материи по уровням кузни: элемент 0 — 1-й уровень; кроме них, создание стоит 1 стихию кузни
 */
data class ForgeItemDef(
    val code: String,
    val element: Element,
    val name: String,
    val slot: ForgeSlot,
    val costs: List<Map<Material, Int>>
) {
    fun cost(level: Int): Map<Material, Int> = costs.getOrElse(level - 1) { emptyMap() }
}

/** Растение цены зелья: допустимые растения (одно на выбор); [any] — любое. */
data class LabUnit(val options: List<Plant>, val any: Boolean)

/** Зелье планшета лаборатории; цена одинакова на всех уровнях. */
data class LabPotionDef(val code: String, val name: String, val units: List<LabUnit>) {
    fun accepts(index: Int, plant: Plant): Boolean {
        val unit = units.getOrNull(index) ?: return false
        return unit.any || plant in unit.options
    }
}

/**
 * Каталог игры: боссы, достижения, задания, главы, кузня и лаборатория. Загружается из файла по умолчанию
 * (assets) или с сервера; [version] — контрольная сумма каталога сервера (ETag).
 */
class Catalog(
    val version: String,
    val bosses: List<BossDef>,
    val achievements: List<AchievementDef>,
    val quests: List<QuestDef>,
    val chapters: List<ChapterDef>,
    val forge: List<ForgeItemDef>,
    val lab: List<LabPotionDef>
) {
    private val bossByCode = bosses.associateBy { it.code }
    private val achievementByCode = achievements.associateBy { it.code }
    private val questByNumber = quests.associateBy { it.number }
    private val chapterByNumber = chapters.associateBy { it.chapter }
    private val forgeByCode = forge.associateBy { it.code }
    private val labByCode = lab.associateBy { it.code }

    val describer = EffectDescriber(::achievementName, ::bossName)
    val planner = EffectPlanner(describer, ::achievementName)

    /** Боссы в порядке списка выбора. */
    val sortedBosses: List<BossDef> get() = bosses.sortedBy { it.sortOrder }

    fun boss(code: String?): BossDef? = code?.let { bossByCode[it] }
    fun bossByName(name: String): BossDef? = bosses.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    fun quest(number: Int): QuestDef? = questByNumber[number]
    fun chapter(number: Int): ChapterDef? = chapterByNumber[number]
    fun forgeItem(code: String): ForgeItemDef? = forgeByCode[code]
    fun forge(element: Element): List<ForgeItemDef> = forge.filter { it.element == element }
    fun labPotion(code: String): LabPotionDef? = labByCode[code]
    fun achievement(code: String): AchievementDef? = achievementByCode[code]

    /** Достижение каталога по названию в любом написании (регистр, ё/е, пробелы). */
    fun achievementByName(name: String): AchievementDef? = achievements.firstOrNull { achievementMatches(it.name, name) }

    fun achievementName(code: String): String = achievementByCode[code]?.name ?: code
    fun bossName(code: String): String = bossByCode[code]?.name ?: code

    /** Финальная глава кампании: бой только с финальным боссом. */
    companion object {
        const val FINAL_CHAPTER = 11
        const val PROLOGUE_BOSS = "VIRAXEN"

        /** Уровень враждебности по главе книги: 0; 1–3 → 1; 4–7 → 2; 8–11 → 3. */
        fun difficultyForChapter(chapter: Int): Int = when {
            chapter <= 0 -> 0
            chapter <= 3 -> 1
            chapter <= 7 -> 2
            else -> 3
        }
    }
}
