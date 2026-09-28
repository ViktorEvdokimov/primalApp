package com.primal.rules.model;

/** Все ресурсы охотника одним списком: 6 материй, 6 растений, 9 стихий (коды не пересекаются). */
public enum ResourceCode {
    SCALES(ResourceKind.MATERIAL), BONES(ResourceKind.MATERIAL), BLOOD(ResourceKind.MATERIAL),
    ZIMIA(ResourceKind.MATERIAL), IRIDIA(ResourceKind.MATERIAL), ZLATIA(ResourceKind.MATERIAL),
    NILLEA(ResourceKind.PLANT), TARMARET(ResourceKind.PLANT), ALBALACEA(ResourceKind.PLANT),
    MELLIS(ResourceKind.PLANT), ANTHEMON(ResourceKind.PLANT), SELICORNIA(ResourceKind.PLANT),
    FIRE(ResourceKind.ELEMENT), HORN(ResourceKind.ELEMENT), CORAL(ResourceKind.ELEMENT),
    CRYSTAL(ResourceKind.ELEMENT), LIGHTNING(ResourceKind.ELEMENT), METAL(ResourceKind.ELEMENT),
    FEATHER(ResourceKind.ELEMENT), POISON(ResourceKind.ELEMENT), ICE(ResourceKind.ELEMENT);

    private final ResourceKind kind;

    ResourceCode(ResourceKind kind) {
        this.kind = kind;
    }

    public ResourceKind kind() {
        return kind;
    }

    public String displayName() {
        return switch (kind) {
            case MATERIAL -> Material.valueOf(name()).displayName();
            case PLANT -> Plant.valueOf(name()).displayName();
            case ELEMENT -> Element.valueOf(name()).displayName();
        };
    }
}
