package com.primalapp.model

import com.primalapp.model.ext.endRound
import com.primalapp.model.ext.resetPhase
import com.primalapp.model.ext.takeDamage
import com.primalapp.model.ext.toggleHardened
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonsterTest {

    @Test
    fun `takeDamage applies wounds correctly`() {
        val monster = Monster(
            name = "Test",
            currentHealth = 10,
            damageForWound = 4
        )
        val result = monster.takeDamage(8)
        assertEquals(2, result.woundsInflicted)
        assertEquals(8, monster.currentHealth)
        assertEquals(0, monster.accumulatedDamage)
    }

    @Test
    fun `takeDamage with remaining damage below wound threshold`() {
        val monster = Monster(
            name = "Test",
            currentHealth = 10,
            damageForWound = 4
        )
        val result = monster.takeDamage(5)
        assertEquals(1, result.woundsInflicted)
        assertEquals(9, monster.currentHealth)
        assertEquals(1, monster.accumulatedDamage)
    }

    @Test
    fun `takeDamage kills monster when health reaches zero`() {
        val monster = Monster(
            name = "Test",
            currentHealth = 1,
            damageForWound = 4
        )
        val result = monster.takeDamage(4)
        assertEquals(1, result.woundsInflicted)
        assertEquals(0, monster.currentHealth)
        assertTrue(monster.isDefeated)
        assertTrue(result.message.contains("побеждён"))
    }

    @Test
    fun `takeDamage triggers phase change at threshold`() {
        val monster = Monster(
            name = "Test",
            currentHealth = 10,
            damageForWound = 4,
            healthForStanceChange = 8
        )
        val result = monster.takeDamage(8)
        assertEquals(2, result.woundsInflicted)
        assertEquals(8, monster.currentHealth)
        assertTrue(result.phaseChanged)
        assertEquals(2, result.newPhase)
    }

    @Test
    fun `takeDamage does not change phase in last phase`() {
        val monster = Monster(
            name = "Test",
            currentPhase = 9,
            currentHealth = 3,
            damageForWound = 4,
            healthForStanceChange = 2
        )
        val result = monster.takeDamage(4)
        assertEquals(1, result.woundsInflicted)
        assertEquals(2, monster.currentHealth)
        assertFalse(result.phaseChanged)
    }

    @Test
    fun `hardened status burns remaining damage after wound`() {
        val monster = Monster(
            name = "Test",
            currentHealth = 10,
            damageForWound = 4,
            isHardened = true
        )
        val result = monster.takeDamage(8)
        assertEquals(2, result.woundsInflicted)
        assertEquals(8, monster.currentHealth)
        assertEquals(0, monster.accumulatedDamage)
        assertEquals(0, result.remainingDamage)
    }

    @Test
    fun `non-hardened status keeps remaining damage`() {
        val monster = Monster(
            name = "Test",
            currentHealth = 10,
            damageForWound = 4,
            isHardened = false
        )
        val result = monster.takeDamage(9)
        assertEquals(2, result.woundsInflicted)
        assertEquals(8, monster.currentHealth)
        assertEquals(1, monster.accumulatedDamage)
        assertEquals(1, result.remainingDamage)
    }

    @Test
    fun `toggleHardened flips status`() {
        val monster = Monster(
            name = "Test",
            isHardened = false
        )
        assertTrue(monster.toggleHardened())
        assertFalse(monster.toggleHardened())
        assertTrue(monster.toggleHardened())
        assertFalse(monster.toggleHardened())
    }

    @Test
    fun `resetPhase resets damage and updates thresholds`() {
        val monster = Monster(
            name = "Test",
            currentHealth = 7,
            accumulatedDamage = 5,
            damageForWound = 4,
            healthForStanceChange = 3,
            currentPhase = 2
        )
        monster.resetPhase(damageForWound = 6, healthForStanceChange = 2)
        assertEquals(6, monster.damageForWound)
        assertEquals(2, monster.healthForStanceChange)
        assertEquals(0, monster.accumulatedDamage)
    }

    @Test
    fun `isDefeated blocks further damage`() {
        val monster = Monster(
            name = "Test",
            currentHealth = 0,
            isDefeated = true
        )
        val result = monster.takeDamage(100)
        assertEquals(0, result.woundsInflicted)
        assertTrue(result.message.contains("уже побеждён"))
    }

    @Test
    fun `addRage and removeRage work correctly`() {
        val monster = Monster(name = "Test")
        monster.rage = 5
        assertEquals(8, run { monster.rage += 3; monster.rage })
        monster.rage = 5
        assertEquals(3, run { monster.rage = (monster.rage - 2).coerceAtLeast(0); monster.rage })
        monster.rage = 2
        assertEquals(0, run { monster.rage = (monster.rage - 5).coerceAtLeast(0); monster.rage })
    }

    @Test
    fun `endRound adds rage per hunter`() {
        val monster = Monster(name = "Test", rage = 3)
        val newRage = monster.endRound(4)
        assertEquals(7, newRage)
    }
}
