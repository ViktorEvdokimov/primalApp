package com.primal.rules.model;

/** Классы охотников: 4 базовых и 4 из дополнений (как HunterClass в app). */
public enum HunterClass {
    DAREON("Дареон"),
    MIRA("Мира"),
    TOREG("Торег"),
    LIONAR("Льонар"),
    KARA("Кара"),
    HELEREN("Хелерен"),
    DRUSK("Друск"),
    ZARAIA("Зарайа");

    private final String displayName;

    HunterClass(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
