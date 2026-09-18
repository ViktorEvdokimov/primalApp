package com.primalapp.model.ext

import com.primalapp.model.Monster

data class DamageResult(
    val woundsInflicted: Int,
    val remainingDamage: Int,
    val phaseChanged: Boolean,
    val newPhase: Int,
    val message: String
)

fun Monster.takeDamage(amount: Int): DamageResult {
    if (isDefeated) {
        return DamageResult(0, amount, false, currentPhase, "Монстр уже побеждён.")
    }

    if (amount < 0) {
        return healWound(-amount)
    }

    accumulatedDamage += amount

    val dfw = damageForWound
    if (dfw == null) {
        return DamageResult(
            woundsInflicted = 0,
            remainingDamage = accumulatedDamage,
            phaseChanged = false,
            newPhase = currentPhase,
            message = "Урон накоплен, но рана не нанесена (нет порога раны)."
        )
    }

    var wounds = 0
    var phaseChanged = false

    while (accumulatedDamage >= dfw) {
        accumulatedDamage -= dfw
        currentHealth -= 1
        wounds++

        if (currentHealth <= 0) {
            currentHealth = 0
            isDefeated = true
            return DamageResult(
                woundsInflicted = wounds,
                remainingDamage = accumulatedDamage,
                phaseChanged = false,
                newPhase = currentPhase,
                message = "Монстр побеждён! Нанесено ран: $wounds"
            )
        }

        val hsc = healthForStanceChange
        if (hsc != null && currentHealth <= hsc && currentPhase < maxPhases) {
            currentPhase++
            phaseChanged = true
        }
    }

    val remaining = if (isHardened && wounds > 0) {
        val leftover = accumulatedDamage
        accumulatedDamage = 0
        leftover
    } else {
        accumulatedDamage
    }

    return DamageResult(
        woundsInflicted = wounds,
        remainingDamage = remaining,
        phaseChanged = phaseChanged,
        newPhase = currentPhase,
        message = buildString {
            if (wounds > 0) append("Нанесено ран: $wounds. ")
            if (phaseChanged) append("Монстр перешёл на стойку $currentPhase! ")
        }.trimEnd().ifEmpty { "Урон накоплен, но рана не нанесена." }
    )
}

private fun Monster.healWound(amount: Int): DamageResult {
    var healRemaining = amount
    var woundsHealed = 0

    if (accumulatedDamage > 0) {
        val reduceBy = minOf(healRemaining, accumulatedDamage)
        accumulatedDamage -= reduceBy
        healRemaining -= reduceBy
    }

    val dfw = damageForWound
    if (dfw != null) {
        while (healRemaining >= dfw) {
            currentHealth += 1
            woundsHealed++
            healRemaining -= dfw
        }

        if (healRemaining > 0) {
            accumulatedDamage = dfw - healRemaining
        }
    }

    return DamageResult(
        woundsInflicted = 0,
        remainingDamage = accumulatedDamage,
        phaseChanged = false,
        newPhase = currentPhase,
        message = buildString {
            if (woundsHealed > 0) append("Заживлено ран: $woundsHealed. ")
            append("Босс восстановил здоровье.")
        }
    )
}

fun Monster.addRage(amount: Int): Int {
    rage += amount
    return rage
}

fun Monster.removeRage(amount: Int): Int {
    rage = (rage - amount).coerceAtLeast(0)
    return rage
}

fun Monster.addRagePerHunter(hunterCount: Int, multiplier: Int = 1): Int {
    rage += hunterCount * multiplier
    return rage
}

fun Monster.endRound(hunterCount: Int): Int {
    addRagePerHunter(hunterCount)
    return rage
}

fun Monster.toggleHardened(): Boolean {
    isHardened = !isHardened
    return isHardened
}

fun Monster.resetPhase(damageForWound: Int?, healthForStanceChange: Int?) {
    this.damageForWound = damageForWound
    this.healthForStanceChange = healthForStanceChange
    if (this.isHardened) {
        this.accumulatedDamage = 0
    }
}
