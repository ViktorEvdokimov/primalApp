package com.primal.rules.model;

/**
 * Дополнения к игре. Какие есть у игрока — настройка аккаунта (qa № 138): задания отключённых дополнений не
 * показываются, а условие «есть дополнение» проверяется по дополнениям владельца кампании.
 */
public enum Expansion {
    NIGHTMARE("Кошмар"),
    FEATHER("Перо"),
    POISON("Яд"),
    ICE("Лёд");

    private final String displayName;

    Expansion(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
