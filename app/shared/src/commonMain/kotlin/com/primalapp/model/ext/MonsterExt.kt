package com.primalapp.model.ext

import com.primalapp.model.Monster

data class DamageResult(
    val woundsInflicted: Int,
    val remainingDamage: Int,
    val phaseChanged: Boolean,
    val newPhase: Int,
    val message: String
)

/**
 * Наносит урон. Раны наносятся по одной; как только здоровье достигло порога смены стойки,
 * цикл ран останавливается — оставшийся урон переносится на новую стойку и применяется
 * с её прочностью после [resetPhase] (правила, «Урон монстру и раны монстра»).
 * Отрицательный урон уменьшает накопленный урон (не ниже 0) и не заживляет раны.
 */
fun Monster.takeDamage(amount: Int): DamageResult {
    if (isDefeated) {
        return DamageResult(0, amount, false, currentPhase, "Монстр уже побеждён.")
    }

    if (amount < 0) {
        return reduceAccumulatedDamage(-amount)
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
            // Следующие раны — уже с прочностью новой стойки
            break
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

private fun Monster.reduceAccumulatedDamage(amount: Int): DamageResult {
    val reduced = minOf(amount, accumulatedDamage)
    accumulatedDamage -= reduced
    return DamageResult(
        woundsInflicted = 0,
        remainingDamage = accumulatedDamage,
        phaseChanged = false,
        newPhase = currentPhase,
        message = if (reduced > 0) "Накопленный урон уменьшен на $reduced." else "Накопленного урона нет."
    )
}

/** «Заживить рану»: здоровье +1 (не выше начального), накопленный урон не меняется. */
fun Monster.healWound(): Boolean {
    if (isDefeated || currentHealth >= Monster.DEFAULT_HEALTH) return false
    currentHealth += 1
    return true
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

fun Monster.toggleResilient(): Boolean {
    isResilient = !isResilient
    return isResilient
}

/** Параметры новой стойки. Накопленный урон переносится, кроме стойки с «Устойчивостью». */
fun Monster.resetPhase(damageForWound: Int?, healthForStanceChange: Int?) {
    this.damageForWound = damageForWound
    this.healthForStanceChange = healthForStanceChange
    if (this.isResilient) {
        this.accumulatedDamage = 0
    }
}
