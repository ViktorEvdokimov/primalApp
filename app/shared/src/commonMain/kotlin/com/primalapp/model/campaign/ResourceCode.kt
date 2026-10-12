package com.primalapp.model.campaign

/** Все ресурсы охотника одним списком: 6 материй, 6 растений, 9 стихий (коды не пересекаются, как на сервере). */
enum class ResourceCode(val type: ResourceType) {
    SCALES(ResourceType.MATERIAL), BONES(ResourceType.MATERIAL), BLOOD(ResourceType.MATERIAL),
    ZIMIA(ResourceType.MATERIAL), IRIDIA(ResourceType.MATERIAL), ZLATIA(ResourceType.MATERIAL),
    NILLEA(ResourceType.PLANT), TARMARET(ResourceType.PLANT), ALBALACEA(ResourceType.PLANT),
    MELLIS(ResourceType.PLANT), ANTHEMON(ResourceType.PLANT), SELICORNIA(ResourceType.PLANT),
    FIRE(ResourceType.ELEMENT), HORN(ResourceType.ELEMENT), CORAL(ResourceType.ELEMENT),
    CRYSTAL(ResourceType.ELEMENT), LIGHTNING(ResourceType.ELEMENT), METAL(ResourceType.ELEMENT),
    FEATHER(ResourceType.ELEMENT), POISON(ResourceType.ELEMENT), ICE(ResourceType.ELEMENT);

    val displayName: String
        get() = when (type) {
            ResourceType.MATERIAL -> Material.valueOf(name).displayName
            ResourceType.PLANT -> Plant.valueOf(name).displayName
            ResourceType.ELEMENT -> Element.valueOf(name).displayName
        }

    companion object {
        fun of(material: Material): ResourceCode = valueOf(material.name)
        fun of(plant: Plant): ResourceCode = valueOf(plant.name)
        fun of(element: Element): ResourceCode = valueOf(element.name)
        fun parse(code: String): ResourceCode? = entries.firstOrNull { it.name == code }

        val materials: List<ResourceCode> = entries.filter { it.type == ResourceType.MATERIAL }
        val plants: List<ResourceCode> = entries.filter { it.type == ResourceType.PLANT }
        val elements: List<ResourceCode> = entries.filter { it.type == ResourceType.ELEMENT }
    }
}
