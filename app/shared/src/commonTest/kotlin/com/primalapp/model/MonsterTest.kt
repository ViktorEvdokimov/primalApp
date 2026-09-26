package com.primalapp.model

import com.primalapp.model.ext.endRound
import com.primalapp.model.ext.healWound
import com.primalapp.model.ext.resetPhase
import com.primalapp.model.ext.takeDamage
import com.primalapp.model.ext.toggleHardened
import com.primalapp.model.ext.toggleResilient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    //region 20.1. Перенос накопленного урона при смене стойки

    @Test
    fun `resetPhase переносит накопленный урон для не-затвердевшего монстра`() {
        // Подготовка: не-затвердевший монстр с накопленным уроном 5
        val monster = Monster(
            name = "Test",
            currentHealth = 7,
            accumulatedDamage = 5,
            damageForWound = 4,
            healthForStanceChange = 3,
            currentPhase = 2
        )

        // Вызов проверяемого кода: смена стойки
        monster.resetPhase(damageForWound = 6, healthForStanceChange = 2)

        // Проверка: пороги обновлены, остаток урона перенесён на новую стойку
        assertEquals(6, monster.damageForWound)
        assertEquals(2, monster.healthForStanceChange)
        assertEquals(5, monster.accumulatedDamage,
            "Для не-затвердевшего монстра остаток урона должен переноситься на новую стойку")
    }

    @Test
    fun `resetPhase обнуляет накопленный урон при «Устойчивости стойки»`() {
        // Подготовка: монстр с «Устойчивостью стойки» и накопленным уроном 5 (R-3)
        val monster = Monster(
            name = "Test",
            currentHealth = 7,
            accumulatedDamage = 5,
            damageForWound = 4,
            healthForStanceChange = 3,
            currentPhase = 2,
            isResilient = true
        )

        // Вызов проверяемого кода: смена стойки
        monster.resetPhase(damageForWound = 6, healthForStanceChange = 2)

        // Проверка: пороги обновлены, урон не переносится на новую стойку
        assertEquals(6, monster.damageForWound)
        assertEquals(2, monster.healthForStanceChange)
        assertEquals(0, monster.accumulatedDamage,
            "При «Устойчивости стойки» урон не переносится на новую стойку")
    }

    @Test
    fun `resetPhase переносит накопленный урон для затвердевшего монстра без «Устойчивости стойки»`() {
        // Подготовка: «Затвердевший» влияет только на остаток после раны (R-3)
        val monster = Monster(
            name = "Test",
            currentHealth = 7,
            accumulatedDamage = 5,
            damageForWound = 4,
            healthForStanceChange = 3,
            currentPhase = 2,
            isHardened = true
        )

        // Вызов проверяемого кода
        monster.resetPhase(damageForWound = 6, healthForStanceChange = 2)

        // Проверка
        assertEquals(5, monster.accumulatedDamage)
    }

    @Test
    fun `toggleResilient переключает «Устойчивость стойки» независимо от «Затвердевшего»`() {
        // Подготовка
        val monster = Monster(name = "Test")

        // Вызов проверяемого кода
        monster.toggleResilient()

        // Проверка
        assertTrue(monster.isResilient)
        assertFalse(monster.isHardened)
    }

    @Test
    fun `takeDamage останавливает цикл ран на смене стойки и переносит остаток`() {
        // Подготовка: пример книги — прочность 4, порог 7, здоровье 9 (R-2)
        val monster = Monster(name = "Вираксен", currentHealth = 9, damageForWound = 4, healthForStanceChange = 7)

        // Вызов проверяемого кода: 13 урона
        val result = monster.takeDamage(13)

        // Проверка: 2 раны, 5 урона перенесено на новую стойку
        assertEquals(2, result.woundsInflicted)
        assertTrue(result.phaseChanged)
        assertEquals(7, monster.currentHealth)
        assertEquals(5, monster.accumulatedDamage)
        assertEquals(2, monster.currentPhase)
    }

    @Test
    fun `healWound заживляет одну рану и не трогает накопленный урон`() {
        // Подготовка (R-4)
        val monster = Monster(name = "Test", currentHealth = 6, accumulatedDamage = 3, damageForWound = 4)

        // Вызов проверяемого кода
        val healed = monster.healWound()

        // Проверка
        assertTrue(healed)
        assertEquals(7, monster.currentHealth)
        assertEquals(3, monster.accumulatedDamage)
    }

    @Test
    fun `healWound не поднимает здоровье выше начального`() {
        // Подготовка
        val monster = Monster(name = "Test", currentHealth = 10)

        // Вызов проверяемого кода
        val healed = monster.healWound()

        // Проверка
        assertFalse(healed)
        assertEquals(10, monster.currentHealth)
    }

    @Test
    fun `отрицательный урон больше накопленного обнуляет накопленный урон и не лечит`() {
        // Подготовка (D-3)
        val monster = Monster(name = "Test", currentHealth = 8, accumulatedDamage = 2, damageForWound = 4)

        // Вызов проверяемого кода
        val result = monster.takeDamage(-7)

        // Проверка
        assertEquals(0, monster.accumulatedDamage)
        assertEquals(8, monster.currentHealth)
        assertEquals(0, result.woundsInflicted)
    }

    //endregion

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

    //region 15.x. Null healthForStanceChange — смена по запросу

    @Test
    fun `takeDamage не меняет стойку при healthForStanceChange null`() {
        // Подготовка: монстр с null hsc (смена по запросу)
        val monster = Monster(
            name = "Иекорос",
            currentHealth = 10,
            damageForWound = 4,
            healthForStanceChange = null
        )

        // Вызов проверяемого кода: урон, который снизил бы здоровье ниже порога
        val result = monster.takeDamage(8)

        // Проверка: раны нанесены, но стойка не сменилась (фаза осталась 1)
        assertEquals(2, result.woundsInflicted, "Должно быть нанесено 2 раны")
        assertEquals(8, monster.currentHealth)
        assertFalse(result.phaseChanged,
            "Стойка не должна смениться при null healthForStanceChange")
        assertEquals(1, monster.currentPhase, "Фаза должна остаться 1")
    }

    @Test
    fun `resetPhase принимает null healthForStanceChange`() {
        // Подготовка: монстр
        val monster = Monster(
            name = "Иекорос",
            currentHealth = 10,
            damageForWound = 4,
            healthForStanceChange = null
        )

        // Вызов проверяемого кода
        monster.resetPhase(damageForWound = 6, healthForStanceChange = null)

        // Проверка: damageForWound обновлён, healthForStanceChange остался null
        assertEquals(6, monster.damageForWound)
        assertNull(monster.healthForStanceChange,
            "healthForStanceChange должен остаться null после resetPhase")
        assertEquals(0, monster.accumulatedDamage)
    }

    //endregion

    //region 22.5. Коровон — стойка без порога раны (nullable dfw)

    @Test
    fun `takeDamage при damageForWound null накапливает урон без ран`() {
        // Подготовка: монстр без порога раны
        val monster = Monster(
            name = "Коровон",
            currentHealth = 10,
            damageForWound = null
        )

        // Вызов проверяемого кода
        val result = monster.takeDamage(15)

        // Проверка: урон накоплен, ран нет, монстр не побеждён, фаза не сменилась
        assertEquals(15, monster.accumulatedDamage, "Накопленный урон должен быть 15")
        assertEquals(10, monster.currentHealth, "Здоровье не должно снизиться")
        assertFalse(monster.isDefeated, "Монстр не должен быть побеждён")
        assertEquals(0, result.woundsInflicted, "Ран не должно быть")
        assertFalse(result.phaseChanged, "Фаза не должна смениться")
    }

    @Test
    fun `healWound при damageForWound null уменьшает накопленный урон без заживления ран`() {
        // Подготовка: монстр без порога раны с накопленным уроном
        val monster = Monster(
            name = "Коровон",
            currentHealth = 10,
            accumulatedDamage = 8,
            damageForWound = null
        )

        // Вызов проверяемого кода: лечение 3 урона (takeDamage с отрицательным значением)
        val result = monster.takeDamage(-3)

        // Проверка: accumulatedDamage уменьшен, здоровье не изменилось
        assertEquals(5, monster.accumulatedDamage, "Накопленный урон должен уменьшиться на 3")
        assertEquals(10, monster.currentHealth, "Здоровье не должно измениться")
        assertEquals(0, result.woundsInflicted, "Раны не должны заживляться")
    }

    @Test
    fun `resetPhase с damageForWound null устанавливает порог и переносит накопленный урон`() {
        // Подготовка: не-затвердевший монстр с накопленным уроном
        val monster = Monster(
            name = "Коровон",
            currentHealth = 6,
            accumulatedDamage = 5,
            damageForWound = 2,
            healthForStanceChange = 6
        )

        // Вызов проверяемого кода: переход на стойку без порога раны
        monster.resetPhase(damageForWound = null, healthForStanceChange = null)

        // Проверка: порог раны null, остаток урона перенесён (не обнулён)
        assertNull(monster.damageForWound, "damageForWound должен быть null")
        assertNull(monster.healthForStanceChange, "healthForStanceChange должен быть null")
        assertEquals(5, monster.accumulatedDamage,
            "Остаток урона должен переноситься для не-затвердевшего монстра")
    }

    //endregion
}
