package com.primalapp.model

data class Monster(
    val name: String,
    val maxPhases: Int = 9,
    var currentPhase: Int = 1,
    var currentHealth: Int = 10,
    var accumulatedDamage: Int = 0,
    var damageForWound: Int = 4,
    var healthForStanceChange: Int = 7,
    var rage: Int = 0,
    var isHardened: Boolean = false,
    var isDefeated: Boolean = false
) {
    val isLastPhase: Boolean get() = currentPhase >= maxPhases

    companion object {
        const val DEFAULT_HEALTH = 10
        const val DEFAULT_PHASES = 9
    }
}
