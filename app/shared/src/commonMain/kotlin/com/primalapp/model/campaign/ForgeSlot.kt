package com.primalapp.model.campaign

/**
 * Место предмета на планшете кузни: оружие одного класса охотника, шлем, доспех или предмет. Оружие может
 * создать только охотник своего класса, остальное — любой (правила, «Кузня»).
 */
enum class ForgeSlot(val displayName: String, val hunterClass: HunterClass?) {
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

    val isWeapon: Boolean get() = hunterClass != null

    fun availableTo(hunter: HunterClass): Boolean = hunterClass == null || hunterClass == hunter

    companion object {
        fun weaponOf(hunter: HunterClass): ForgeSlot = entries.first { it.hunterClass == hunter }
    }
}
