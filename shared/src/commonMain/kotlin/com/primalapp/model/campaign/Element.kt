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
    ICE("Лёд", isExpansion = true)
}
