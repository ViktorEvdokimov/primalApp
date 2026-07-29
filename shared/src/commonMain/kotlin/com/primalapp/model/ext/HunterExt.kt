package com.primalapp.model.ext

import com.primalapp.model.Hunter

fun Hunter.takeDamage(amount: Int): Boolean {
    currentHealth = (currentHealth - amount).coerceAtLeast(0)
    if (currentHealth <= 0) {
        isUnconscious = true
    }
    return isUnconscious
}

fun Hunter.heal(amount: Int): Int {
    currentHealth = (currentHealth + amount).coerceAtMost(maxHealth)
    if (currentHealth > 0) {
        isUnconscious = false
    }
    return currentHealth
}

fun Hunter.revive(): Hunter {
    currentHealth = maxHealth
    isUnconscious = false
    return this
}

fun Hunter.isCritical(): Boolean =
    currentHealth > 0 && currentHealth <= maxHealth / 4
