package com.primalapp.model

data class Hunter(
    val name: String,
    val maxHealth: Int = 20,
    var currentHealth: Int = maxHealth,
    var isUnconscious: Boolean = false
) {
    val isAlive: Boolean get() = currentHealth > 0

    val healthPercentage: Float get() = currentHealth.toFloat() / maxHealth.toFloat()

    companion object {
        const val DEFAULT_MAX_HEALTH = 20
    }
}
