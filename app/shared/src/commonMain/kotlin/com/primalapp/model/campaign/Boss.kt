package com.primalapp.model.campaign

data class BossStance(
    val damageForWound: Int?,
    val healthForStanceChange: Int?
)

data class Boss(
    val id: Long = 0,
    val name: String,
    val element: Element?,
    val difficulty: Int,
    val stances: List<BossStance>
) {
    fun getStance(index: Int): BossStance? =
        if (index in stances.indices) stances[index] else null
}
