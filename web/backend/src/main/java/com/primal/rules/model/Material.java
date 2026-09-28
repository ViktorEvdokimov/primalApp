package com.primal.rules.model;

/** Материи — ресурсы для кузни. */
public enum Material {
    SCALES("Чешуя"),
    BONES("Кости"),
    BLOOD("Кровь"),
    ZIMIA("Зимия"),
    IRIDIA("Иридия"),
    ZLATIA("Златия");

    private final String displayName;

    Material(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
