package com.primalapp.viewmodel

import com.primalapp.model.ext.DamageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BattleViewModelTest {

    private fun createViewModel(
        damageForWound: Int = 4,
        healthForStanceChange: Int = 7,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ): BattleViewModel {
        val viewModel = BattleViewModel(scope)
        viewModel.startBattle(
            hunterCount = 4,
            damageForWound = damageForWound,
            healthForStanceChange = healthForStanceChange
        )
        return viewModel
    }

    //region 1. Расчёт нанесённого урона

    //region 1.1. Нанесение урона при прямом вводе

    @Test
    fun `Прямой ввод — нанесение урона без раны`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)
        viewModel.onDamageInputChanged("3")

        // Вызов проверяемого кода
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertEquals(0, state.pendingDamage)
        assertEquals("", state.damageInputText)
        assertEquals(3, state.monster.accumulatedDamage)
        assertEquals(10, state.monster.currentHealth)
        assertFalse(state.monster.isDefeated)
        assertEquals("Урон накоплен, но рана не нанесена.", state.message)
        assertEquals(InputMode.NONE, state.inputMode)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Прямой ввод — нанесение урона с раной`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)
        viewModel.onDamageInputChanged("4")

        // Вызов проверяемого кода
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertEquals(0, state.pendingDamage)
        assertEquals(9, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertFalse(state.monster.isDefeated)
        assertTrue(state.message.contains("Нанесено ран"))
        val result = state.lastDamageResult
        assertNotNull(result)
        assertEquals(1, result.woundsInflicted)
        assertEquals(0, result.remainingDamage)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Прямой ввод — фатальный урон`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)
        viewModel.onDamageInputChanged("100")

        // Вызов проверяемого кода
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertTrue(state.monster.isDefeated)
        assertEquals(0, state.monster.currentHealth)
        assertEquals(FightPhase.VICTORY, state.phase)
        assertTrue(state.message.contains("побеждён"))
        assertTrue(state.canUndo)
    }

    @Test
    fun `Прямой ввод — переход на другую стойку`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4, healthForStanceChange = 7)
        viewModel.onDamageInputChanged("12")

        // Вызов проверяемого кода
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertEquals(7, state.monster.currentHealth)
        assertEquals(2, state.monster.currentPhase)
        assertTrue(state.showPhaseChangeDialog)
        val result = state.lastDamageResult
        assertNotNull(result)
        assertEquals(3, result.woundsInflicted)
        assertTrue(result.phaseChanged)
        assertEquals(2, result.newPhase)
        assertTrue(state.message.contains("Нанесено ран"))
        assertTrue(state.message.contains("перешёл на стойку 2"))
        assertTrue(state.canUndo)
    }

    //endregion

    //region 1.2. Нанесение урона при нажатии кнопок +1, +10, +50

    @Test
    fun `Кнопки быстрого ввода — нанесение урона без раны`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: три нажатия +1 = 3 урона (< damageForWound)
        viewModel.onQuickButtonPress(1)
        viewModel.onQuickButtonPress(1)
        viewModel.onQuickButtonPress(1)
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertEquals(0, state.pendingDamage)
        assertEquals(3, state.monster.accumulatedDamage)
        assertEquals(10, state.monster.currentHealth)
        assertFalse(state.monster.isDefeated)
        assertEquals("Урон накоплен, но рана не нанесена.", state.message)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Кнопки быстрого ввода — нанесение урона с раной`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: +10 (= 2 раны, 2 accumulatedDamage)
        viewModel.onQuickButtonPress(10)
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertEquals(8, state.monster.currentHealth)
        assertEquals(2, state.monster.accumulatedDamage)
        assertFalse(state.monster.isDefeated)
        assertTrue(state.message.contains("Нанесено ран"))
        val result = state.lastDamageResult
        assertNotNull(result)
        assertEquals(2, result.woundsInflicted)
        assertEquals(2, result.remainingDamage)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Кнопки быстрого ввода — фатальный урон`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: два нажатия +50 = 100 урона (фатально)
        viewModel.onQuickButtonPress(50)
        viewModel.onQuickButtonPress(50)
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertTrue(state.monster.isDefeated)
        assertEquals(0, state.monster.currentHealth)
        assertEquals(FightPhase.VICTORY, state.phase)
        assertTrue(state.message.contains("побеждён"))
        assertTrue(state.canUndo)
    }

    @Test
    fun `Кнопки быстрого ввода — переход на другую стойку`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4, healthForStanceChange = 7)

        // Вызов проверяемого кода: +10, +1, +1 = 12 урона (3 раны, переход на стойку 2)
        viewModel.onQuickButtonPress(10)
        viewModel.onQuickButtonPress(1)
        viewModel.onQuickButtonPress(1)
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertEquals(7, state.monster.currentHealth)
        assertEquals(2, state.monster.currentPhase)
        assertTrue(state.showPhaseChangeDialog)
        val result = state.lastDamageResult
        assertNotNull(result)
        assertEquals(3, result.woundsInflicted)
        assertTrue(result.phaseChanged)
        assertTrue(state.canUndo)
    }

    //endregion

    //region 1.3. Прямой ввод, потом изменение прямым вводом

    @Test
    fun `Изменение прямым вводом — нанесение урона без раны`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: сначала 8, потом исправлено на 3
        viewModel.onDamageInputChanged("8")
        viewModel.onDamageInputChanged("3")
        viewModel.onOkPress()

        // Проверка: финальный pending = 3, раны нет
        val state = viewModel.state.value
        assertEquals(0, state.pendingDamage)
        assertEquals(3, state.monster.accumulatedDamage)
        assertEquals(10, state.monster.currentHealth)
        assertEquals("Урон накоплен, но рана не нанесена.", state.message)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Изменение прямым вводом — нанесение урона с раной`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: сначала 3, потом исправлено на 4
        viewModel.onDamageInputChanged("3")
        viewModel.onDamageInputChanged("4")
        viewModel.onOkPress()

        // Проверка: финальный pending = 4, одна рана
        val state = viewModel.state.value
        assertEquals(9, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertTrue(state.message.contains("Нанесено ран"))
        val result = state.lastDamageResult
        assertNotNull(result)
        assertEquals(1, result.woundsInflicted)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Изменение прямым вводом — фатальный урон`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: сначала 3, потом 100 (фатально)
        viewModel.onDamageInputChanged("3")
        viewModel.onDamageInputChanged("100")
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertTrue(state.monster.isDefeated)
        assertEquals(0, state.monster.currentHealth)
        assertEquals(FightPhase.VICTORY, state.phase)
        assertTrue(state.message.contains("побеждён"))
        assertTrue(state.canUndo)
    }

    @Test
    fun `Изменение прямым вводом — переход на другую стойку`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4, healthForStanceChange = 7)

        // Вызов проверяемого кода: сначала 3, потом 12 (переход на стойку 2)
        viewModel.onDamageInputChanged("3")
        viewModel.onDamageInputChanged("12")
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertEquals(7, state.monster.currentHealth)
        assertEquals(2, state.monster.currentPhase)
        assertTrue(state.showPhaseChangeDialog)
        val result = state.lastDamageResult
        assertNotNull(result)
        assertTrue(result.phaseChanged)
        assertEquals(2, result.newPhase)
        assertTrue(state.canUndo)
    }

    //endregion

    //region 1.4. Прямой ввод, потом изменение кнопками +1, +10, +50

    @Test
    fun `Прямой ввод плюс кнопки — нанесение урона без раны`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: ввод 2, потом +1 = 3 (без раны)
        viewModel.onDamageInputChanged("2")
        viewModel.onQuickButtonPress(1)
        viewModel.onOkPress()

        // Проверка: итого 3 урона, раны нет
        val state = viewModel.state.value
        assertEquals(0, state.pendingDamage)
        assertEquals(3, state.monster.accumulatedDamage)
        assertEquals(10, state.monster.currentHealth)
        assertEquals("Урон накоплен, но рана не нанесена.", state.message)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Прямой ввод плюс кнопки — нанесение урона с раной`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: ввод 3, потом +1 = 4 (одна рана)
        viewModel.onDamageInputChanged("3")
        viewModel.onQuickButtonPress(1)
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertEquals(9, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertTrue(state.message.contains("Нанесено ран"))
        val result = state.lastDamageResult
        assertNotNull(result)
        assertEquals(1, result.woundsInflicted)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Прямой ввод плюс кнопки — фатальный урон`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: ввод 2, потом +50 = 52 (фатально)
        viewModel.onDamageInputChanged("2")
        viewModel.onQuickButtonPress(50)
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertTrue(state.monster.isDefeated)
        assertEquals(0, state.monster.currentHealth)
        assertEquals(FightPhase.VICTORY, state.phase)
        assertTrue(state.message.contains("побеждён"))
        assertTrue(state.canUndo)
    }

    @Test
    fun `Прямой ввод плюс кнопки — переход на другую стойку`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4, healthForStanceChange = 7)

        // Вызов проверяемого кода: ввод 5, потом +10 = 15 (переход на стойку 2)
        viewModel.onDamageInputChanged("5")
        viewModel.onQuickButtonPress(10)
        viewModel.onOkPress()

        // Проверка
        val state = viewModel.state.value
        assertEquals(2, state.monster.currentPhase)
        assertTrue(state.showPhaseChangeDialog)
        val result = state.lastDamageResult
        assertNotNull(result)
        assertTrue(result.phaseChanged)
        assertEquals(2, result.newPhase)
        assertTrue(state.canUndo)
    }

    //endregion

    //region 1.5. Прямой ввод и нажатие кнопки «Отмена»

    @Test
    fun `Прямой ввод с отменой — сброс pendingDamage и состояния ввода`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода: ввод 10, потом отмена
        viewModel.onDamageInputChanged("10")
        viewModel.onCancelPress()

        // Проверка: pendingDamage сброшен, поле очищено, canUndo = false
        val state = viewModel.state.value
        assertEquals(0, state.pendingDamage)
        assertEquals("", state.damageInputText)
        assertEquals(InputMode.NONE, state.inputMode)
        assertFalse(state.isTimerRunning)
        assertEquals(10, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertFalse(state.canUndo)
    }

    //endregion

    //endregion

    //region 2. Проверка таймера

    //region 2.1. Проверка запуска таймера после нажатия кнопок +1, +10, +50

    @Test
    fun `Таймер запускается после нажатия кнопки +1`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода
        viewModel.onQuickButtonPress(1)

        // Проверка
        val state = viewModel.state.value
        assertTrue(state.isTimerRunning)
        assertEquals(InputMode.QUICK_BUTTON, state.inputMode)
        assertEquals(1, state.pendingDamage)
        assertEquals("1", state.damageInputText)
    }

    @Test
    fun `Таймер запускается после нажатия кнопки +10`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода
        viewModel.onQuickButtonPress(10)

        // Проверка
        val state = viewModel.state.value
        assertTrue(state.isTimerRunning)
        assertEquals(InputMode.QUICK_BUTTON, state.inputMode)
        assertEquals(10, state.pendingDamage)
        assertEquals("10", state.damageInputText)
    }

    @Test
    fun `Таймер запускается после нажатия кнопки +50`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Вызов проверяемого кода
        viewModel.onQuickButtonPress(50)

        // Проверка
        val state = viewModel.state.value
        assertTrue(state.isTimerRunning)
        assertEquals(InputMode.QUICK_BUTTON, state.inputMode)
        assertEquals(50, state.pendingDamage)
        assertEquals("50", state.damageInputText)
    }

    //endregion

    //region 2.2. Проверка сброса таймера при повторном нажатии (параметризованный)

    @Test
    fun `Сброс таймера — повторное нажатие через 0_1с накапливает урон`() = runBlocking {
        // Подготовка
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = createViewModel(damageForWound = 4, scope = scope)

        // Вызов проверяемого кода: нажатие +4, задержка 0.1с, повторное +4
        viewModel.onQuickButtonPress(4)
        delay(100)
        viewModel.onQuickButtonPress(4)
        delay(2500)

        // Проверка: оба нажатия учтены — итого 8 урона = 2 раны, здоровье 8
        val state = viewModel.state.value
        assertEquals(0, state.pendingDamage)
        assertEquals(8, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertFalse(state.isTimerRunning)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Сброс таймера — повторное нажатие через 1с накапливает урон`() = runBlocking {
        // Подготовка
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = createViewModel(damageForWound = 4, scope = scope)

        // Вызов проверяемого кода: нажатие +4, задержка 1с, повторное +4
        viewModel.onQuickButtonPress(4)
        delay(1000)
        viewModel.onQuickButtonPress(4)
        delay(2500)

        // Проверка: оба нажатия учтены — итого 8 урона = 2 раны, здоровье 8
        val state = viewModel.state.value
        assertEquals(0, state.pendingDamage)
        assertEquals(8, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertFalse(state.isTimerRunning)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Сброс таймера — повторное нажатие через 1_5с накапливает урон`() = runBlocking {
        // Подготовка
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = createViewModel(damageForWound = 4, scope = scope)

        // Вызов проверяемого кода: нажатие +4, задержка 1.5с, повторное +4
        viewModel.onQuickButtonPress(4)
        delay(1500)
        viewModel.onQuickButtonPress(4)
        delay(2500)

        // Проверка: оба нажатия учтены — итого 8 урона = 2 раны, здоровье 8
        val state = viewModel.state.value
        assertEquals(0, state.pendingDamage)
        assertEquals(8, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertFalse(state.isTimerRunning)
        assertTrue(state.canUndo)
    }

    //endregion

    //region 2.3. Проверка остановки таймера после выбора окна прямого ввода

    @Test
    fun `Таймер останавливается при фокусе поля прямого ввода`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)
        viewModel.onQuickButtonPress(1)
        assertTrue(viewModel.state.value.isTimerRunning)

        // Вызов проверяемого кода
        viewModel.onInputFieldFocused()

        // Проверка
        val state = viewModel.state.value
        assertFalse(state.isTimerRunning)
        assertEquals(InputMode.MANUAL, state.inputMode)
        assertEquals(1, state.pendingDamage)
    }

    //endregion

    //region 2.4. Проверка остановки таймера после нажатия кнопки «Отмена»

    @Test
    fun `Таймер останавливается при нажатии кнопки Отмена`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)
        viewModel.onQuickButtonPress(1)
        assertTrue(viewModel.state.value.isTimerRunning)

        // Вызов проверяемого кода
        viewModel.onCancelPress()

        // Проверка
        val state = viewModel.state.value
        assertFalse(state.isTimerRunning)
        assertEquals(InputMode.NONE, state.inputMode)
        assertEquals(0, state.pendingDamage)
        assertEquals("", state.damageInputText)
    }

    //endregion

    //endregion

    //region 3. Проверка кнопки «Отмена предыдущего действия»

    //region 3.1. Проверка, что кнопка заблокирована после начала боя

    @Test
    fun `Кнопка отмены заблокирована после начала боя`() {
        // Подготовка
        val viewModel = createViewModel(damageForWound = 4)

        // Проверка: canUndo = false сразу после startBattle
        assertFalse(viewModel.state.value.canUndo)

        // Вызов проверяемого кода: попытка отмены без предварительного действия
        viewModel.onUndoPress()

        // Проверка: состояние не изменилось
        val state = viewModel.state.value
        assertFalse(state.canUndo)
        assertEquals(10, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertEquals(1, state.monster.currentPhase)
    }

    //endregion

    //region 3.2. Проверка отмены нанесённого урона

    @Test
    fun `Отмена действия — восстановление после урона без раны`() {
        // Подготовка: наносим урон без раны
        val viewModel = createViewModel(damageForWound = 4)
        viewModel.onDamageInputChanged("3")
        viewModel.onOkPress()
        assertEquals(3, viewModel.state.value.monster.accumulatedDamage)
        assertTrue(viewModel.state.value.canUndo)

        // Вызов проверяемого кода: отмена
        viewModel.onUndoPress()

        // Проверка: accumulatedDamage восстановлен до 0
        val state = viewModel.state.value
        assertEquals(0, state.monster.accumulatedDamage)
        assertEquals(10, state.monster.currentHealth)
        assertFalse(state.canUndo)
        assertTrue(state.message.contains("Отменено"))
        assertTrue(state.message.contains("3"))
    }

    @Test
    fun `Отмена действия — восстановление после урона с раной`() {
        // Подготовка: наносим урон с раной (4 урона = 1 рана)
        val viewModel = createViewModel(damageForWound = 4)
        viewModel.onDamageInputChanged("4")
        viewModel.onOkPress()
        assertEquals(9, viewModel.state.value.monster.currentHealth)
        assertEquals(0, viewModel.state.value.monster.accumulatedDamage)
        assertTrue(viewModel.state.value.canUndo)

        // Вызов проверяемого кода: отмена
        viewModel.onUndoPress()

        // Проверка: здоровье и accumulatedDamage восстановлены
        val state = viewModel.state.value
        assertEquals(10, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertFalse(state.canUndo)
        assertTrue(state.message.contains("Отменено"))
    }

    @Test
    fun `Отмена действия — восстановление после урона с переходом на другую стойку`() {
        // Подготовка: наносим урон с переходом на стойку 2
        val viewModel = createViewModel(damageForWound = 4, healthForStanceChange = 7)
        viewModel.onDamageInputChanged("12")
        viewModel.onOkPress()
        assertEquals(7, viewModel.state.value.monster.currentHealth)
        assertEquals(2, viewModel.state.value.monster.currentPhase)
        assertEquals(FightPhase.PHASE_II, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.canUndo)

        // Вызов проверяемого кода: отмена
        viewModel.onUndoPress()

        // Проверка: здоровье, стойка и фаза восстановлены
        val state = viewModel.state.value
        assertEquals(10, state.monster.currentHealth)
        assertEquals(1, state.monster.currentPhase)
        assertEquals(FightPhase.PHASE_I, state.phase)
        assertFalse(state.canUndo)
        assertTrue(state.message.contains("Отменено"))
    }

    //endregion

    //endregion
}
