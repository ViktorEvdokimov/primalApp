package com.primalapp.model.campaign

enum class Element(val displayName: String, val isExpansion: Boolean = false) {
    FIRE("Огонь"),
    HORN("Рог"),
    CORAL("Коралл"),
    CRYSTAL("Кристалл"),
    LIGHTNING("Молния"),
    METAL("Металл"),
    FEATHER("Перо", isExpansion = true),
    POISON("Яд", isExpansion = true),
    ICE("Лёд", isExpansion = true);

    /** Дополнение, из которого стихия; `null` — базовая игра. */
    val expansion: Expansion?
        get() = when (this) {
            FEATHER -> Expansion.FEATHER
            POISON -> Expansion.POISON
            ICE -> Expansion.ICE
            else -> null
        }

    companion object {
        fun parse(code: String?): Element? = entries.firstOrNull { it.name == code }
    }
}
