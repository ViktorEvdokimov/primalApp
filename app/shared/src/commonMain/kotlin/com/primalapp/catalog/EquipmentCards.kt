package com.primalapp.catalog

import com.primalapp.database.PlatformContext
import com.primalapp.database.readBundledText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Карта снаряжения из файлов игры (web/cards/split_cards.py, web qa № 151, 152): [code] — код предмета кузни
 * (`FIRE_05`), зелья (`LAB_01`) или вида базового снаряжения (`DUAL_BLADES`, `HELMET`), [asset] — картинка в файлах
 * приложения.
 */
data class EquipmentCard(val code: String, val level: Int, val name: String, val asset: String) {
    val title: String get() = "$name · $level-й уровень"
}

/** Нарезанные карты снаряжения — для кнопки «Описание» в кузнице и лаборатории; без сети тоже (карты в файлах приложения). */
class EquipmentCards(cards: List<EquipmentCard> = emptyList()) {

    private val byKey = cards.associateBy { it.code to it.level }

    /** Коды предметов, у которых есть карты, — без повторов по уровням. */
    val codes: Set<String> = cards.mapTo(LinkedHashSet()) { it.code }

    /** Карта этого уровня; карты нет в файлах игры — `null`. */
    fun card(code: String, level: Int): EquipmentCard? = byKey[code to level]

    @Serializable
    private data class Manifest(val cards: List<Entry>)

    @Serializable
    private data class Entry(val code: String, val level: Int, val name: String, val file: String)

    companion object {
        private const val FOLDER = "cards"
        const val MANIFEST = "$FOLDER/cards.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): EquipmentCards = EquipmentCards(
            json.decodeFromString(Manifest.serializer(), text).cards.map { EquipmentCard(it.code, it.level, it.name, "$FOLDER/${it.file}") }
        )

        /** Список карт из файлов приложения; его нет или он не разобрался — карт нет. */
        fun load(context: PlatformContext): EquipmentCards =
            readBundledText(context, MANIFEST)?.let { text -> runCatching { parse(text) }.getOrNull() } ?: EquipmentCards()
    }
}
