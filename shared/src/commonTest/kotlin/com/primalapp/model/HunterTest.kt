package com.primalapp.model

import com.primalapp.model.ext.heal
import com.primalapp.model.ext.isCritical
import com.primalapp.model.ext.revive
import com.primalapp.model.ext.takeDamage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HunterTest {

    @Test
    fun `takeDamage reduces health`() {
        val hunter = Hunter(name = "Test", maxHealth = 20)
        val unconscious = hunter.takeDamage(5)
        assertFalse(unconscious)
        assertEquals(15, hunter.currentHealth)
    }

    @Test
    fun `takeDamage to zero causes unconscious`() {
        val hunter = Hunter(name = "Test", maxHealth = 20)
        val unconscious = hunter.takeDamage(25)
        assertTrue(unconscious)
        assertEquals(0, hunter.currentHealth)
    }

    @Test
    fun `heal restores health`() {
        val hunter = Hunter(name = "Test", maxHealth = 20, currentHealth = 5)
        val newHp = hunter.heal(10)
        assertEquals(15, newHp)
        assertTrue(hunter.isAlive)
    }

    @Test
    fun `heal does not exceed maxHealth`() {
        val hunter = Hunter(name = "Test", maxHealth = 20, currentHealth = 18)
        val newHp = hunter.heal(10)
        assertEquals(20, newHp)
    }

    @Test
    fun `heal revives unconscious hunter`() {
        val hunter = Hunter(name = "Test", maxHealth = 20, currentHealth = 0, isUnconscious = true)
        hunter.heal(5)
        assertEquals(5, hunter.currentHealth)
        assertFalse(hunter.isUnconscious)
        assertTrue(hunter.isAlive)
    }

    @Test
    fun `revive restores full health`() {
        val hunter = Hunter(name = "Test", maxHealth = 20, currentHealth = 3, isUnconscious = true)
        val revived = hunter.revive()
        assertEquals(20, revived.currentHealth)
        assertFalse(revived.isUnconscious)
    }

    @Test
    fun `isCritical returns true at 25 percent or less`() {
        val hunter = Hunter(name = "Test", maxHealth = 20, currentHealth = 5)
        assertTrue(hunter.isCritical())
    }

    @Test
    fun `isCritical returns false above 25 percent`() {
        val hunter = Hunter(name = "Test", maxHealth = 20, currentHealth = 6)
        assertFalse(hunter.isCritical())
    }

    @Test
    fun `isAlive returns true when health above zero`() {
        val hunter = Hunter(name = "Test", maxHealth = 20, currentHealth = 1)
        assertTrue(hunter.isAlive)
    }

    @Test
    fun `isAlive returns false when health is zero`() {
        val hunter = Hunter(name = "Test", maxHealth = 20, currentHealth = 0)
        assertFalse(hunter.isAlive)
    }

    @Test
    fun `healthPercentage calculates correctly`() {
        val hunter = Hunter(name = "Test", maxHealth = 20, currentHealth = 10)
        assertEquals(0.5f, hunter.healthPercentage)
    }
}
