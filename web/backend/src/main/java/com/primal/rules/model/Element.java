package com.primal.rules.model;

/** Стихии: 6 базовых и 3 из дополнений. */
public enum Element {
    FIRE("Огонь", null),
    HORN("Рог", null),
    CORAL("Коралл", null),
    CRYSTAL("Кристалл", null),
    LIGHTNING("Молния", null),
    METAL("Металл", null),
    FEATHER("Перо", Expansion.FEATHER),
    POISON("Яд", Expansion.POISON),
    ICE("Лёд", Expansion.ICE);

    private final String displayName;
    private final Expansion expansion;

    Element(String displayName, Expansion expansion) {
        this.displayName = displayName;
        this.expansion = expansion;
    }

    public String displayName() {
        return displayName;
    }

    /** Дополнение, из которого стихия; {@code null} — базовая игра. */
    public Expansion expansion() {
        return expansion;
    }
}
