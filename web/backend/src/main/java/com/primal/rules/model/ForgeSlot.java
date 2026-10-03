package com.primal.rules.model;

/**
 * Место предмета на планшете кузни: оружие одного класса охотника, шлем, доспех или предмет. Оружие может
 * создать только охотник своего класса, остальное — любой (правила, «Кузня»).
 */
public enum ForgeSlot {
    GREATSWORD("Большой меч", HunterClass.DAREON),
    GREATBOW("Большой лук", HunterClass.MIRA),
    HAMMER("Молот", HunterClass.TOREG),
    SWORD_AND_SHIELD("Щит и меч", HunterClass.LIONAR),
    DUAL_BLADES("Парные клинки", HunterClass.KARA),
    GUN("Пушка", HunterClass.HELEREN),
    SPEAR("Копьё", HunterClass.ZARAIA),
    DRUM("Барабан", HunterClass.DRUSK),
    HELMET("Шлем", null),
    ARMOR("Доспех", null),
    ITEM("Предмет", null);

    private final String displayName;
    private final HunterClass hunterClass;

    ForgeSlot(String displayName, HunterClass hunterClass) {
        this.displayName = displayName;
        this.hunterClass = hunterClass;
    }

    public String displayName() {
        return displayName;
    }

    /** Класс, которому принадлежит оружие; {@code null} — шлем, доспех и предметы доступны всем. */
    public HunterClass hunterClass() {
        return hunterClass;
    }

    public boolean availableTo(HunterClass hunter) {
        return hunterClass == null || hunterClass == hunter;
    }

    /** Оружие класса {@code hunter}. */
    public static ForgeSlot weaponOf(HunterClass hunter) {
        for (ForgeSlot slot : values()) {
            if (slot.hunterClass == hunter) {
                return slot;
            }
        }
        throw new IllegalArgumentException("Нет оружия для класса " + hunter);
    }
}
