package com.primal.rules.model;

/** Растения — ресурсы для лаборатории. */
public enum Plant {
    NILLEA("Ниллея"),
    TARMARET("Тармарет"),
    ALBALACEA("Альбалацея"),
    MELLIS("Меллис"),
    ANTHEMON("Антемон"),
    SELICORNIA("Селикорния");

    private final String displayName;

    Plant(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
