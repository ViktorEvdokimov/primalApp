package com.primalapp.viewmodel

import com.primalapp.model.Hunter
import com.primalapp.model.campaign.Boss
import com.primalapp.model.campaign.BossStance
import com.primalapp.model.campaign.Element
import com.primalapp.model.ext.DamageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleViewModelTest {

    private fun createViewModel(
        hunterCount: Int = 4,
        damageForWound: Int = 1,
        healthForStanceChange: Int = 7,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ): BattleViewModel {
        val viewModel = BattleViewModel(scope)
        viewModel.startBattle(
            hunterCount = hunterCount,
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
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()
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
        val viewModel = createViewModel(hunterCount = 2, damageForWound = 2, healthForStanceChange = 7)
        viewModel.onDamageInputChanged("15")

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel(hunterCount = 2, damageForWound = 2, healthForStanceChange = 7)

        // Вызов проверяемого кода: +5, +5, +5 = 15 урона (3 раны, переход на стойку 2)
        viewModel.onQuickButtonPress(5)
        viewModel.onQuickButtonPress(5)
        viewModel.onQuickButtonPress(5)
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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel(hunterCount = 2, damageForWound = 2, healthForStanceChange = 7)

        // Вызов проверяемого кода: сначала 3, потом 15 (переход на стойку 2)
        viewModel.onDamageInputChanged("3")
        viewModel.onDamageInputChanged("15")
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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

        // Вызов проверяемого кода: ввод 14, потом +50 = 64 (фатально, 16 ран)
        viewModel.onDamageInputChanged("14")
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
        val viewModel = createViewModel(hunterCount = 2, damageForWound = 2, healthForStanceChange = 7)

        // Вызов проверяемого кода: ввод 5, потом +10 = 15 (3 раны, переход на стойку 2)
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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel(scope = scope)

        // Вызов проверяемого кода: нажатие +4, задержка 0.1с, повторное +4
        viewModel.onQuickButtonPress(4)
        delay(100)
        viewModel.onQuickButtonPress(4)
        delay(2500)

        // Проверка: оба нажатия учтены — итого 8 урона = 2 раны, здоровье 14
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
        val viewModel = createViewModel(scope = scope)

        // Вызов проверяемого кода: нажатие +4, задержка 1с, повторное +4
        viewModel.onQuickButtonPress(4)
        delay(1000)
        viewModel.onQuickButtonPress(4)
        delay(2500)

        // Проверка: оба нажатия учтены — итого 8 урона = 2 раны, здоровье 14
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
        val viewModel = createViewModel(scope = scope)

        // Вызов проверяемого кода: нажатие +4, задержка 1.5с, повторное +4
        viewModel.onQuickButtonPress(4)
        delay(1500)
        viewModel.onQuickButtonPress(4)
        delay(2500)

        // Проверка: оба нажатия учтены — итого 8 урона = 2 раны, здоровье 14
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
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()
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
        val viewModel = createViewModel(hunterCount = 2, damageForWound = 2, healthForStanceChange = 7)
        viewModel.onDamageInputChanged("15")
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

    //region 4. Проверка метода endRound()

    @Test
    fun `endRound — по��еда через pendingDamage убивающим босса`() {
        // Подготовка: health=10, damageForWound=4, 40 урона = 10 ран → здоровье 0
        val viewModel = createViewModel()
        viewModel.onDamageInputChanged("40")

        // Вызов проверяемого кода
        viewModel.endRound()

        // Проверка: VICTORY, босс повержен, раунд не инкрементирован, ярость не добавлена
        val state = viewModel.state.value
        assertEquals(FightPhase.VICTORY, state.phase)
        assertTrue(state.monster.isDefeated)
        assertEquals(0, state.monster.currentHealth)
        assertEquals(1, state.currentRound)
        assertEquals(0, state.monster.rage)
    }

    @Test
    fun `endRound — смена стойки через pendingDamage без hardened`() {
        // Подготовка: health=10, damageForWound=4, healthForStanceChange=7
        // 12 урона = 3 раны → здоровье 7 ≤ 7 → смена на стойку 2
        val viewModel = createViewModel(healthForStanceChange = 7)
        viewModel.onDamageInputChanged("12")

        // Вызов проверяемого кода
        viewModel.endRound()

        // Проверка: фаза обновлена, диалог показан, раунд инкрементирован, ярость добавлена
        val state = viewModel.state.value
        assertEquals(FightPhase.PHASE_II, state.phase)
        assertTrue(state.showPhaseChangeDialog)
        assertEquals(7, state.monster.currentHealth)
        assertEquals(2, state.monster.currentPhase)
        assertEquals(2, state.currentRound)
        assertEquals(4, state.monster.rage)
    }

    @Test
    fun `endRound — смена стойки через pendingDamage при hardened`() {
        // Подготовка: health=10, damageForWound=4, healthForStanceChange=7, hardened=true
        // 12 урона = 3 раны, остаток сгорает, но стойка должна смениться
        val viewModel = createViewModel(healthForStanceChange = 7)
        viewModel.toggleHardened()
        viewModel.onDamageInputChanged("12")

        // Вызов проверяемого кода
        viewModel.endRound()

        // Проверка: hardened не блокирует смену стойки — результат как без hardened
        val state = viewModel.state.value
        assertEquals(FightPhase.PHASE_II, state.phase)
        assertTrue(state.showPhaseChangeDialog)
        assertEquals(7, state.monster.currentHealth)
        assertEquals(2, state.monster.currentPhase)
        assertEquals(2, state.currentRound)
        assertEquals(4, state.monster.rage)
        assertEquals(0, state.monster.accumulatedDamage)
    }

    @Test
    fun `endRound — урон без смены стойки`() {
        // Подготовка: 3 урона < damageForWound=4 → без раны, без смены стойки
        val viewModel = createViewModel()
        viewModel.onDamageInputChanged("3")

        // Вызов проверяемого кода
        viewModel.endRound()

        // Проверка: фаза не изменилась, диалог не показан, раунд завершён
        val state = viewModel.state.value
        assertEquals(FightPhase.PHASE_I, state.phase)
        assertFalse(state.showPhaseChangeDialog)
        assertEquals(10, state.monster.currentHealth)
        assertEquals(3, state.monster.accumulatedDamage)
        assertEquals(2, state.currentRound)
        assertEquals(4, state.monster.rage)
    }

    @Test
    fun `endRound — без pendingDamage`() {
        // Подготовка: бой начат, pendingDamage = 0
        val viewModel = createViewModel()

        // Вызов проверяемого кода
        viewModel.endRound()

        // Проверка: раунд инкрементирован, ярость добавлена, фаза не изменилась
        val state = viewModel.state.value
        assertEquals(FightPhase.PHASE_I, state.phase)
        assertEquals(2, state.currentRound)
        assertEquals(4, state.monster.rage)
    }

    @Test
    fun `endRound — победа через pendingDamage не добавляет ярость`() {
        // Подготовка: 40 урона = 10 ран → здоровье 0
        val viewModel = createViewModel()
        viewModel.onDamageInputChanged("40")

        // Вызов проверяемого кода
        viewModel.endRound()

        // Проверка: ярость не изменилась (ранний выход при победе)
        val state = viewModel.state.value
        assertEquals(FightPhase.VICTORY, state.phase)
        assertEquals(0, state.monster.rage)
    }

    @Test
    fun `endRound — VICTORY приоритетнее DEFEAT по раундам`() {
        // Подготовка: доводим до 10-го раунда
        val viewModel = createViewModel()
        repeat(9) { viewModel.endRound() }
        assertEquals(10, viewModel.state.value.currentRound)

        // Ставим фатальный урон и завершаем раунд
        viewModel.onDamageInputChanged("40")
        viewModel.endRound()

        // Проверка: VICTORY, несмотря на исчерпание раундов
        val state = viewModel.state.value
        assertEquals(FightPhase.VICTORY, state.phase)
        assertTrue(state.monster.isDefeated)
    }

    //endregion

    //region 5. Проверка сброса боя (кнопка «Выход в меню»)

    @Test
    fun `Сброс боя переводит состояние в PRE_BATTLE`() {
        // Подготовка: начинаем бой, наносим урон, завершаем раунд
        val viewModel = createViewModel()
        viewModel.onDamageInputChanged("12")
        viewModel.commitDamage()
        viewModel.endRound()
        val stateBefore = viewModel.state.value
        assertTrue(stateBefore.canUndo)

        // Вызов проверяемого кода
        viewModel.resetBattle()

        // Проверка: состояние полностью сброшено
        val state = viewModel.state.value
        assertEquals(FightPhase.PRE_BATTLE, state.phase)
        assertFalse(state.canUndo)
        assertEquals(1, state.currentRound)
        assertEquals(0, state.monster.rage)
        assertEquals(0, state.pendingDamage)
    }

    //endregion

    //region 6. Проверка многошаговой отмены (undo до 10 действий)

    @Test
    fun `Одиночная отмена урона восстанавливает здоровье`() {
        // Подготовка: 4 урона = 1 рана, health 10 → 9
        val viewModel = createViewModel()
        viewModel.onDamageInputChanged("4")
        viewModel.commitDamage()
        val stateBefore = viewModel.state.value
        assertEquals(9, stateBefore.monster.currentHealth)
        assertTrue(stateBefore.canUndo)

        // Вызов проверяемого кода
        viewModel.onUndoPress()

        // Проверка: здоровье восстановлено, canUndo = false
        val state = viewModel.state.value
        assertEquals(10, state.monster.currentHealth)
        assertEquals(0, state.monster.accumulatedDamage)
        assertFalse(state.canUndo)
        assertTrue(state.message.contains("Отменено"))
    }

    @Test
    fun `Две отмены подряд восстанавливают состояние шаг за шагом`() {
        // Подготовка: два commitDamage по 4 урона — 2 раны, health 10 → 9 → 8
        val viewModel = createViewModel()
        viewModel.onDamageInputChanged("4")
        viewModel.commitDamage()
        viewModel.onDamageInputChanged("4")
        viewModel.commitDamage()
        val stateBefore = viewModel.state.value
        assertEquals(8, stateBefore.monster.currentHealth)

        // Вызов проверяемого кода: первая отмена
        viewModel.onUndoPress()
        val state1 = viewModel.state.value
        assertEquals(9, state1.monster.currentHealth)
        assertTrue(state1.canUndo)

        // Вызов проверяемого кода: вторая отмена
        viewModel.onUndoPress()
        val state2 = viewModel.state.value
        assertEquals(10, state2.monster.currentHealth)
        assertEquals(0, state2.monster.accumulatedDamage)
        assertFalse(state2.canUndo)
    }

    @Test
    fun `Отмена ярости восстанавливает предыдущее значение`() {
        // Подготовка: добавляем ярость
        val viewModel = createViewModel()
        viewModel.addRage(3)
        val stateBefore = viewModel.state.value
        assertEquals(3, stateBefore.monster.rage)
        assertTrue(stateBefore.canUndo)

        // Вызов проверяемого кода
        viewModel.onUndoPress()

        // Проверка: ярость восстановлена до 0
        val state = viewModel.state.value
        assertEquals(0, state.monster.rage)
        assertFalse(state.canUndo)
    }

    @Test
    fun `Отмена завершения раунда восстанавливает ярость`() {
        // Подготовка: завершаем раунд
        val viewModel = createViewModel()
        viewModel.endRound()
        val stateBefore = viewModel.state.value
        assertEquals(2, stateBefore.currentRound)
        assertEquals(4, stateBefore.monster.rage)
        assertTrue(stateBefore.canUndo)

        // Вызов проверяемого кода
        viewModel.onUndoPress()

        // Проверка: ярость восстановлена (currentRound в BattleScreenState не откатывается)
        val state = viewModel.state.value
        assertEquals(0, state.monster.rage)
        assertFalse(state.canUndo)
    }

    @Test
    fun `Отмена смены стойки восстанавливает параметры фазы`() {
        // Подготовка: 12 урона = 3 раны, health 10 → 7, смена на стойку 2
        val viewModel = createViewModel()
        viewModel.onDamageInputChanged("12")
        viewModel.commitDamage()
        val stateAfterDamage = viewModel.state.value
        assertEquals(7, stateAfterDamage.monster.currentHealth)
        assertEquals(FightPhase.PHASE_II, stateAfterDamage.phase)

        // Подтверждаем смену стойки с новыми параметрами
        viewModel.confirmPhaseChange(damageForWound = 1, healthForStanceChange = 5)
        val stateAfterPhase = viewModel.state.value
        assertEquals(5, stateAfterPhase.monster.healthForStanceChange)
        assertTrue(stateAfterPhase.canUndo)

        // Вызов проверяемого кода: отмена смены стойки
        viewModel.onUndoPress()

        // Проверка: healthForStanceChange восстановлен до исходного (7)
        val state = viewModel.state.value
        assertEquals(7, state.monster.healthForStanceChange)
        assertEquals(7, state.monster.currentHealth)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Лимит истории — более 10 действий теряет самое старое`() {
        // Подготовка: 11 commitDamage по 1 урону (без ран, damageForWound=4)
        val viewModel = createViewModel()
        repeat(11) {
            viewModel.onDamageInputChanged("1")
            viewModel.commitDamage()
        }
        val stateBefore = viewModel.state.value
        assertTrue(stateBefore.canUndo)

        // Вызов проверяемого кода: 10 отмен
        repeat(10) { viewModel.onUndoPress() }

        // Проверка: canUndo = false, история исчерпана
        val state = viewModel.state.value
        assertFalse(state.canUndo)
        // После 10 отмен должно быть состояние после 1-го commitDamage (accum=1)
        assertEquals(1, state.monster.accumulatedDamage)
        assertEquals(10, state.monster.currentHealth)
    }

    @Test
    fun `Смешанная отмена — урон, ярость, раунд в обратном порядке`() {
        // Подготовка: урон → ярость → завершение раунда
        val viewModel = createViewModel()
        viewModel.onDamageInputChanged("4")
        viewModel.commitDamage()
        viewModel.addRage(3)
        viewModel.endRound()

        // Вызов: отмена раунда (rage: 7 → 3)
        viewModel.onUndoPress()
        val state1 = viewModel.state.value
        assertEquals(3, state1.monster.rage)
        assertTrue(state1.canUndo)

        // Вызов: отмена ярости (rage: 3 → 0)
        viewModel.onUndoPress()
        val state2 = viewModel.state.value
        assertEquals(0, state2.monster.rage)
        assertTrue(state2.canUndo)

        // Вызов: отмена урона (health: 9 → 10)
        viewModel.onUndoPress()
        val state3 = viewModel.state.value
        assertEquals(10, state3.monster.currentHealth)
        assertEquals(0, state3.monster.accumulatedDamage)
        assertFalse(state3.canUndo)
    }

    //endregion

    //region 15.x. Ручная смена стойки + Сдаться

    @Test
    fun `onManualStanceChange инкрементирует фазу и показывает диалог при hsc null`() {
        // Подготовка: бой с монстром hsc = null (смена по запросу)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = BattleViewModel(scope)
        viewModel.startBattleWithHunters(
            hunters = listOf(com.primalapp.model.Hunter(name = "Охотник 1")),
            damageForWound = 4,
            healthForStanceChange = null
        )

        // Вызов проверяемого кода
        viewModel.onManualStanceChange()

        // Проверка: фаза инкрементирована до II, диалог смены стойки показан
        val state = viewModel.state.value
        assertEquals(FightPhase.PHASE_II, state.phase, "Фаза должна смениться на II")
        assertEquals(2, state.monster.currentPhase)
        assertTrue(state.showPhaseChangeDialog, "Должен показаться диалог смены стойки")

        scope.cancel()
    }

    @Test
    fun `onManualStanceChange не работает при hsc не null`() {
        // Подготовка: бой с монстром hsc = 7 (авто-смена)
        val viewModel = createViewModel(healthForStanceChange = 7)

        // Вызов проверяемого кода
        viewModel.onManualStanceChange()

        // Проверка: фаза не изменилась, диалог не показан
        val state = viewModel.state.value
        assertEquals(FightPhase.PHASE_I, state.phase, "Фаза не должна измениться")
        assertFalse(state.showPhaseChangeDialog, "Диалог не должен показаться")
    }

    @Test
    fun `onSurrender переводит бой в DEFEAT`() {
        // Подготовка: активный бой
        val viewModel = createViewModel()

        // Вызов проверяемого кода
        viewModel.onSurrender()

        // Проверка: фаза DEFEAT, сообщение о сдаче
        val state = viewModel.state.value
        assertEquals(FightPhase.DEFEAT, state.phase, "Фаза должна стать DEFEAT")
        assertTrue(state.message.contains("сдались"), "Сообщение должно упоминать сдачу")
    }

    //endregion

    //region 22.5. Коровон — стойка без порога раны (nullable dfw)

    @Test
    fun `confirmPhaseChange немедленно наносит накопленный урон при переходе на стойку с порогом`() = runBlocking {
        // Подготовка: бой против Коровона (2-я стойка без порога раны) с одним охотником
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = BattleViewModel(scope)
        val korovon = Boss(
            name = "Коровон",
            element = Element.CORAL,
            difficulty = 0,
            stances = listOf(
                BossStance(2, 6),
                BossStance(null, null),
                BossStance(4, 0)
            )
        )
        viewModel.startBattleWithHunters(
            hunters = listOf(Hunter(name = "Охотник 1")),
            damageForWound = 4,
            healthForStanceChange = 7,
            boss = korovon
        )

        // Переход на стойку 2 (без порога раны): 8 урона = 4 раны (dfw=2), здоровье 10 → 6 → смена стойки
        viewModel.onDamageInputChanged("8")
        viewModel.commitDamage()
        viewModel.confirmPhaseChange(damageForWound = null, healthForStanceChange = null)

        // На стойке без порога раны урон только накапливается
        viewModel.onDamageInputChanged("20")
        viewModel.commitDamage()
        assertEquals(20, viewModel.state.value.monster.accumulatedDamage,
            "На стойке без порога раны урон должен накапливаться")

        // Ручная смена стойки 2 → 3
        viewModel.onManualStanceChange()

        // Вызов проверяемого кода: подтверждение перехода на стойку 3 (dfw=4)
        viewModel.confirmPhaseChange(damageForWound = 4, healthForStanceChange = 0)

        // Проверка: накопленный урон (20) немедленно пересчитан в раны (5 ран, здоровье 6 → 1)
        val state = viewModel.state.value
        assertEquals(4, state.monster.damageForWound, "Прочность стойки 3 должна быть 4")
        assertEquals(0, state.monster.accumulatedDamage, "Накопленный урон должен быть израсходован")
        assertEquals(1, state.monster.currentHealth, "Здоровье должно снизиться на 5 ран (6 → 1)")
        val result = state.lastDamageResult
        assertNotNull(result, "Должен быть результат немедленного нанесения урона")
        assertEquals(5, result.woundsInflicted, "Должно быть нанесено 5 ран")

        scope.cancel()
    }

    //endregion

    //region 25.5. Опциональный dfw (null = нет порога раны)

    @Test
    fun `startBattleWithHunters с null damageForWound запускает бой без порога раны`() {
        // Подготовка
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = BattleViewModel(scope)

        // Вызов проверяемого кода
        viewModel.startBattleWithHunters(
            hunters = listOf(Hunter(name = "Охотник 1")),
            damageForWound = null,
            healthForStanceChange = 7
        )

        // Проверка: у монстра нет порога раны
        val monster = viewModel.state.value.monster
        assertNull(monster.damageForWound, "damageForWound должен быть null (нет порога раны)")

        scope.cancel()
    }

    @Test
    fun `startBattle с null damageForWound запускает бой без порога раны`() {
        // Подготовка
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = BattleViewModel(scope)

        // Вызов проверяемого кода
        viewModel.startBattle(hunterCount = 2, damageForWound = null, healthForStanceChange = 7)

        // Проверка: 2 охотника, у монстра нет порога раны
        val state = viewModel.state.value
        assertEquals(2, state.hunterCount, "Должно быть 2 охотника")
        assertNull(state.monster.damageForWound, "damageForWound должен быть null (нет порога раны)")

        scope.cancel()
    }

    //endregion

    //region 26.3. Кнопка +5 урона

    @Test
    fun `onQuickButtonPress с 5 запускает таймер и накапливает урон`() {
        // Подготовка
        val viewModel = createViewModel()

        // Вызов проверяемого кода
        viewModel.onQuickButtonPress(5)

        // Проверка: pendingDamage = 5, таймер запущен
        val state = viewModel.state.value
        assertTrue(state.isTimerRunning, "Таймер должен запуститься")
        assertEquals(5, state.pendingDamage, "pendingDamage должен быть 5")
        assertEquals("5", state.damageInputText, "Поле ввода должно содержать 5")
    }

    @Test
    fun `onQuickButtonPress с 5 дважды накапливает урон до 10`() {
        // Подготовка
        val viewModel = createViewModel()

        // Вызов проверяемого кода: два нажатия +5
        viewModel.onQuickButtonPress(5)
        viewModel.onQuickButtonPress(5)

        // Проверка: pendingDamage = 10
        val state = viewModel.state.value
        assertEquals(10, state.pendingDamage, "pendingDamage должен быть 10 после двух нажатий")
        assertEquals("10", state.damageInputText, "Поле ввода должно содержать 10")
    }

    //endregion
}
