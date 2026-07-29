package com.primalapp.viewmodel

import com.primalapp.model.ext.DamageResult
import com.primalapp.model.Hunter
import com.primalapp.model.Monster
import com.primalapp.model.ext.addRagePerHunter
import com.primalapp.model.ext.endRound
import com.primalapp.model.ext.removeRage
import com.primalapp.model.ext.resetPhase
import com.primalapp.model.ext.takeDamage
import com.primalapp.model.ext.toggleHardened
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FightPhase {
    PRE_BATTLE,
    SETUP,
    PHASE_I,
    PHASE_II,
    PHASE_III,
    VICTORY,
    DEFEAT
}

enum class InputMode {
    NONE,
    MANUAL,
    QUICK_BUTTON
}

data class MonsterSnapshot(
    val currentHealth: Int,
    val accumulatedDamage: Int,
    val currentPhase: Int,
    val isDefeated: Boolean,
    val rage: Int
)

data class BattleScreenState(
    val phase: FightPhase = FightPhase.PRE_BATTLE,
    val monster: Monster = Monster(name = "Вираксен"),
    val hunters: List<Hunter> = emptyList(),
    val hunterCount: Int = 0,
    val currentRound: Int = 1,
    val maxRounds: Int = 10,
    val pendingDamage: Int = 0,
    val isTimerRunning: Boolean = false,
    val lastDamageResult: DamageResult? = null,
    val message: String = "",
    val showPhaseChangeDialog: Boolean = false,
    val pendingDamageForWound: String = "",
    val pendingHealthForStanceChange: String = "",
    val damageInputText: String = "",
    val inputMode: InputMode = InputMode.NONE,
    val canUndo: Boolean = false,
    val showRageSurgeDialog: Boolean = false
)

class BattleViewModel(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _state = MutableStateFlow(BattleScreenState())
    val state: StateFlow<BattleScreenState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var lastSnapshot: MonsterSnapshot? = null
    private var lastAppliedDamage: Int = 0

    fun startBattle(
        hunterCount: Int,
        damageForWound: Int,
        healthForStanceChange: Int
    ) {
        val hunters = (1..hunterCount).map { i ->
            Hunter(name = "Охотник $i")
        }
        startBattleWithHunters(hunters, damageForWound, healthForStanceChange)
    }

    fun startBattleWithHunters(
        hunters: List<Hunter>,
        damageForWound: Int,
        healthForStanceChange: Int
    ) {
        val monster = Monster(
            name = "Вираксен",
            currentHealth = 10,
            damageForWound = damageForWound,
            healthForStanceChange = healthForStanceChange
        )
        lastSnapshot = null
        lastAppliedDamage = 0
        _state.update {
            it.copy(
                phase = FightPhase.PHASE_I,
                monster = monster,
                hunters = hunters,
                hunterCount = hunters.size,
                currentRound = 1,
                pendingDamage = 0,
                damageInputText = "",
                inputMode = InputMode.NONE,
                canUndo = false,
                isTimerRunning = false,
                message = "Бой начался! Фаза I"
            )
        }
    }

    private fun isBattlePhase(phase: FightPhase): Boolean {
        return phase == FightPhase.PHASE_I ||
            phase == FightPhase.PHASE_II ||
            phase == FightPhase.PHASE_III
    }

    fun onInputFieldFocused() {
        val current = _state.value
        if (!isBattlePhase(current.phase)) return
        if (current.inputMode != InputMode.QUICK_BUTTON) return

        timerJob?.cancel()
        _state.update {
            it.copy(
                inputMode = InputMode.MANUAL,
                isTimerRunning = false
            )
        }
    }

    fun onDamageInputChanged(text: String) {
        val current = _state.value
        if (!isBattlePhase(current.phase)) return

        timerJob?.cancel()
        val damage = text.toIntOrNull() ?: 0
        _state.update {
            it.copy(
                damageInputText = text,
                inputMode = InputMode.MANUAL,
                pendingDamage = damage,
                isTimerRunning = false
            )
        }
    }

    fun onQuickButtonPress(amount: Int) {
        val current = _state.value
        if (!isBattlePhase(current.phase)) return

        val newPending = current.pendingDamage + amount
        val newMode = if (current.inputMode == InputMode.MANUAL) {
            InputMode.MANUAL
        } else {
            InputMode.QUICK_BUTTON
        }

        timerJob?.cancel()

        if (newMode == InputMode.QUICK_BUTTON) {
            _state.update {
                it.copy(
                    pendingDamage = newPending,
                    damageInputText = newPending.toString(),
                    inputMode = InputMode.QUICK_BUTTON,
                    isTimerRunning = true
                )
            }
            timerJob = scope.launch {
                delay(2000)
                commitDamage()
            }
        } else {
            _state.update {
                it.copy(
                    pendingDamage = newPending,
                    damageInputText = newPending.toString(),
                    isTimerRunning = false
                )
            }
        }
    }

    fun onOkPress() {
        commitDamage()
    }

    fun onCancelPress() {
        timerJob?.cancel()
        _state.update {
            it.copy(
                pendingDamage = 0,
                damageInputText = "",
                inputMode = InputMode.NONE,
                isTimerRunning = false
            )
        }
    }

    fun onUndoPress() {
        val snapshot = lastSnapshot ?: return
        val current = _state.value

        current.monster.currentHealth = snapshot.currentHealth
        current.monster.accumulatedDamage = snapshot.accumulatedDamage
        current.monster.currentPhase = snapshot.currentPhase
        current.monster.isDefeated = snapshot.isDefeated
        current.monster.rage = snapshot.rage

        val newPhase = if (snapshot.isDefeated) {
            current.phase
        } else {
            when (snapshot.currentPhase) {
                1 -> FightPhase.PHASE_I
                2 -> FightPhase.PHASE_II
                3 -> FightPhase.PHASE_III
                else -> current.phase
            }
        }

        _state.update {
            it.copy(
                monster = current.monster,
                message = "Отменено: $lastAppliedDamage урона",
                canUndo = false,
                phase = newPhase
            )
        }
        lastSnapshot = null
        lastAppliedDamage = 0
    }

    fun commitDamage() {
        timerJob?.cancel()
        val current = _state.value
        if (current.pendingDamage <= 0) {
            _state.update {
                it.copy(
                    isTimerRunning = false,
                    damageInputText = "",
                    inputMode = InputMode.NONE
                )
            }
            return
        }

        val monster = current.monster
        lastSnapshot = MonsterSnapshot(
            currentHealth = monster.currentHealth,
            accumulatedDamage = monster.accumulatedDamage,
            currentPhase = monster.currentPhase,
            isDefeated = monster.isDefeated,
            rage = monster.rage
        )
        lastAppliedDamage = current.pendingDamage

        val result = monster.takeDamage(current.pendingDamage)

        val newPhase = when {
            result.message.contains("побеждён") -> FightPhase.VICTORY
            result.phaseChanged -> {
                when (result.newPhase) {
                    2 -> FightPhase.PHASE_II
                    3 -> FightPhase.PHASE_III
                    else -> current.phase
                }
            }
            else -> current.phase
        }

        _state.update {
            it.copy(
                isTimerRunning = false,
                message = result.message,
                lastDamageResult = result,
                pendingDamage = 0,
                damageInputText = "",
                inputMode = InputMode.NONE,
                phase = newPhase,
                monster = monster,
                showPhaseChangeDialog = result.phaseChanged && newPhase != FightPhase.VICTORY,
                canUndo = true
            )
        }
    }

    fun confirmPhaseChange(damageForWound: Int, healthForStanceChange: Int) {
        val current = _state.value
        current.monster.resetPhase(damageForWound, healthForStanceChange)
        _state.update {
            it.copy(
                showPhaseChangeDialog = false,
                message = "Стойка ${current.monster.currentPhase}. " +
                    "Урон для раны: $damageForWound, смена при: $healthForStanceChange HP"
            )
        }
    }

    fun confirmRageSurge() {
        val current = _state.value
        current.monster.rage = current.hunterCount
        _state.update {
            it.copy(
                monster = current.monster,
                showRageSurgeDialog = false,
                message = "Всплеск ярости! Ярость сброшена до ${current.hunterCount}"
            )
        }
    }

    fun addRage(amount: Int) {
        _state.update { current ->
            val monster = current.monster
            monster.rage += amount
            current.copy(
                monster = monster,
                message = "Ярость: ${monster.rage}",
                showRageSurgeDialog = monster.rage >= current.hunterCount * 3
            )
        }
    }

    fun removeRage(amount: Int) {
        _state.update { current ->
            current.copy(
                monster = current.monster.apply { removeRage(amount) },
                message = "Ярость: ${current.monster.rage}"
            )
        }
    }

    fun addRagePerHunter() {
        _state.update { current ->
            current.copy(
                monster = current.monster.apply {
                    addRagePerHunter(current.hunterCount)
                },
                message = "Ярость: ${current.monster.rage}",
                showRageSurgeDialog = current.monster.rage >= current.hunterCount * 3
            )
        }
    }

    fun addRagePerHunterMinusOne() {
        _state.update { current ->
            val count = (current.hunterCount - 1).coerceAtLeast(0)
            current.copy(
                monster = current.monster.apply {
                    addRagePerHunter(count)
                },
                message = "Ярость: ${current.monster.rage}",
                showRageSurgeDialog = current.monster.rage >= current.hunterCount * 3
            )
        }
    }

    fun toggleHardened() {
        _state.update { current ->
            val hardened = current.monster.toggleHardened()
            current.copy(
                monster = current.monster,
                message = if (hardened) "Монстр затвердевший" else "Монстр незатвердевший"
            )
        }
    }

    fun endRound() {
        val current = _state.value
        if (current.pendingDamage > 0) {
            current.monster.takeDamage(current.pendingDamage)
            _state.update { it.copy(pendingDamage = 0) }
        }
        current.monster.endRound(current.hunterCount)

        val nextRound = current.currentRound + 1
        val newPhase = if (nextRound > current.maxRounds) {
            FightPhase.DEFEAT
        } else {
            current.phase
        }

        _state.update {
            it.copy(
                monster = current.monster,
                currentRound = nextRound,
                phase = newPhase,
                damageInputText = "",
                inputMode = InputMode.NONE,
                isTimerRunning = false,
                canUndo = false,
                showRageSurgeDialog = newPhase != FightPhase.DEFEAT &&
                    current.monster.rage >= current.hunterCount * 3,
                message = if (newPhase == FightPhase.DEFEAT) {
                    "Поражение! Прошло ${current.maxRounds} раундов."
                } else {
                    "Раунд $nextRound. Ярость: ${current.monster.rage}"
                }
            )
        }
    }

    fun resetBattle() {
        timerJob?.cancel()
        lastSnapshot = null
        lastAppliedDamage = 0
        _state.update { BattleScreenState() }
    }
}
