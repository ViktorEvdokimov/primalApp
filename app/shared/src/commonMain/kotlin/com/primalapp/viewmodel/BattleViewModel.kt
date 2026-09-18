package com.primalapp.viewmodel

import com.primalapp.model.ext.DamageResult
import com.primalapp.model.Hunter
import com.primalapp.model.Monster
import com.primalapp.model.campaign.Boss
import com.primalapp.model.campaign.BossStance
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FightPhase {
    PRE_BATTLE,
    SETUP,
    PHASE_I,
    PHASE_II,
    PHASE_III,
    PHASE_IV,
    PHASE_V,
    PHASE_VI,
    PHASE_VII,
    PHASE_VIII,
    PHASE_IX,
    VICTORY,
    DEFEAT
}

enum class InputMode {
    NONE,
    MANUAL,
    QUICK_BUTTON
}

enum class BattleParam {
    PHASE,
    ROUND,
    HEALTH,
    RAGE,
    ACCUMULATED_DAMAGE,
    DAMAGE_FOR_WOUND,
    HEALTH_FOR_STANCE_CHANGE,
    HARDENED
}

enum class BattleVibrationEvent {
    SHORT,
    DOUBLE
}

data class MonsterSnapshot(
    val currentHealth: Int,
    val accumulatedDamage: Int,
    val currentPhase: Int,
    val isDefeated: Boolean,
    val rage: Int,
    val damageForWound: Int?,
    val healthForStanceChange: Int?
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
    val showRageSurgeDialog: Boolean = false,
    val selectedBoss: Boss? = null,
    val selectedDifficulty: Int = 0,
    val highlightedParams: Set<BattleParam> = emptySet()
)

class BattleViewModel(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _state = MutableStateFlow(BattleScreenState())
    val state: StateFlow<BattleScreenState> = _state.asStateFlow()

    private val _vibrationEvents = MutableSharedFlow<BattleVibrationEvent>(extraBufferCapacity = 8)
    val vibrationEvents: SharedFlow<BattleVibrationEvent> = _vibrationEvents.asSharedFlow()

    private fun emitVibration(event: BattleVibrationEvent) {
        _vibrationEvents.tryEmit(event)
    }

    private fun emitDamageVibration(result: DamageResult?) {
        if (result != null && result.woundsInflicted > 0) {
            emitVibration(BattleVibrationEvent.DOUBLE)
        } else {
            emitVibration(BattleVibrationEvent.SHORT)
        }
    }

    private var timerJob: Job? = null
    private val actionHistory = mutableListOf<ActionSnapshot>()

    fun startBattle(
        hunterCount: Int,
        damageForWound: Int?,
        healthForStanceChange: Int?
    ) {
        val hunters = (1..hunterCount).map { i ->
            Hunter(name = "Охотник $i")
        }
        startBattleWithHunters(hunters, damageForWound, healthForStanceChange)
    }

    fun startBattleWithHunters(
        hunters: List<Hunter>,
        damageForWound: Int?,
        healthForStanceChange: Int?,
        boss: Boss? = null,
        difficulty: Int = 0
    ) {
        val stance = boss?.getStance(0)
        val monsterName = boss?.name ?: "Монстр"
        val monster = Monster(
            name = monsterName,
            currentHealth = 10,
            rage = hunters.size,
            damageForWound = if (stance != null) stance.damageForWound?.let { hunters.size * it } else damageForWound?.let { hunters.size * it },
            healthForStanceChange = if (stance != null) stance.healthForStanceChange else healthForStanceChange
        )
        actionHistory.clear()
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
                message = "Бой начался! Фаза I",
                selectedBoss = boss,
                selectedDifficulty = difficulty,
                highlightedParams = emptySet()
            )
        }
    }

    private fun isBattlePhase(phase: FightPhase): Boolean {
        return phase == FightPhase.PHASE_I ||
            phase == FightPhase.PHASE_II ||
            phase == FightPhase.PHASE_III ||
            phase == FightPhase.PHASE_IV ||
            phase == FightPhase.PHASE_V ||
            phase == FightPhase.PHASE_VI ||
            phase == FightPhase.PHASE_VII ||
            phase == FightPhase.PHASE_VIII ||
            phase == FightPhase.PHASE_IX
    }

    private fun phaseForNumber(number: Int): FightPhase = when (number) {
        2 -> FightPhase.PHASE_II
        3 -> FightPhase.PHASE_III
        4 -> FightPhase.PHASE_IV
        5 -> FightPhase.PHASE_V
        6 -> FightPhase.PHASE_VI
        7 -> FightPhase.PHASE_VII
        8 -> FightPhase.PHASE_VIII
        9 -> FightPhase.PHASE_IX
        else -> FightPhase.PHASE_I
    }

    fun onManualStanceChange() {
        val current = _state.value
        val before = current.toBattleValues()
        val monster = current.monster
        if (monster.healthForStanceChange != null) return
        if (monster.currentPhase >= monster.maxPhases) return

        monster.currentPhase++
        val newPhase = phaseForNumber(monster.currentPhase)

        _state.update {
            it.copy(
                phase = newPhase,
                monster = monster,
                showPhaseChangeDialog = true,
                pendingDamageForWound = current.selectedBoss?.getStance(monster.currentPhase - 1)?.damageForWound?.toString() ?: "",
                pendingHealthForStanceChange = current.selectedBoss?.getStance(monster.currentPhase - 1)?.healthForStanceChange?.toString() ?: ""
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    private fun saveSnapshot(actionType: ActionType, description: String) {
        val current = _state.value
        val monster = current.monster
        val snapshot = MonsterSnapshot(
            currentHealth = monster.currentHealth,
            accumulatedDamage = monster.accumulatedDamage,
            currentPhase = monster.currentPhase,
            isDefeated = monster.isDefeated,
            rage = monster.rage,
            damageForWound = monster.damageForWound,
            healthForStanceChange = monster.healthForStanceChange
        )
        actionHistory.add(0, ActionSnapshot(snapshot, current.phase, actionType, description))
        if (actionHistory.size > 10) {
            actionHistory.removeAt(actionHistory.lastIndex)
        }
    }

    private data class BattleValues(
        val phase: FightPhase,
        val round: Int,
        val health: Int,
        val rage: Int,
        val accumulatedDamage: Int,
        val damageForWound: Int?,
        val healthForStanceChange: Int?,
        val hardened: Boolean
    )

    private fun BattleScreenState.toBattleValues(): BattleValues = BattleValues(
        phase = phase,
        round = currentRound,
        health = monster.currentHealth,
        rage = monster.rage,
        accumulatedDamage = monster.accumulatedDamage,
        damageForWound = monster.damageForWound,
        healthForStanceChange = monster.healthForStanceChange,
        hardened = monster.isHardened
    )

    private fun changedParams(before: BattleValues, after: BattleScreenState): Set<BattleParam> {
        val changed = mutableSetOf<BattleParam>()
        if (before.phase != after.phase) changed += BattleParam.PHASE
        if (before.round != after.currentRound) changed += BattleParam.ROUND
        if (before.health != after.monster.currentHealth) changed += BattleParam.HEALTH
        if (before.rage != after.monster.rage) changed += BattleParam.RAGE
        if (before.accumulatedDamage != after.monster.accumulatedDamage) changed += BattleParam.ACCUMULATED_DAMAGE
        if (before.damageForWound != after.monster.damageForWound) changed += BattleParam.DAMAGE_FOR_WOUND
        if (before.healthForStanceChange != after.monster.healthForStanceChange) changed += BattleParam.HEALTH_FOR_STANCE_CHANGE
        if (before.hardened != after.monster.isHardened) changed += BattleParam.HARDENED
        return changed
    }

    private val highlightJobs = mutableMapOf<BattleParam, Job>()

    private fun highlightParams(params: Set<BattleParam>) {
        if (params.isEmpty()) return
        _state.update { it.copy(highlightedParams = it.highlightedParams + params) }
        params.forEach { param ->
            highlightJobs[param]?.cancel()
            highlightJobs[param] = scope.launch {
                delay(1000)
                _state.update { it.copy(highlightedParams = it.highlightedParams - param) }
                highlightJobs.remove(param)
            }
        }
    }

    private fun highlightChanged(before: BattleValues) {
        highlightParams(changedParams(before, _state.value))
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
        emitVibration(BattleVibrationEvent.SHORT)

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
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun onUndoPress() {
        if (actionHistory.isEmpty()) return
        val snapshot = actionHistory.removeAt(0)
        val current = _state.value
        val before = current.toBattleValues()

        current.monster.currentHealth = snapshot.monster.currentHealth
        current.monster.accumulatedDamage = snapshot.monster.accumulatedDamage
        current.monster.currentPhase = snapshot.monster.currentPhase
        current.monster.isDefeated = snapshot.monster.isDefeated
        current.monster.rage = snapshot.monster.rage
        current.monster.damageForWound = snapshot.monster.damageForWound
        current.monster.healthForStanceChange = snapshot.monster.healthForStanceChange

        val newPhase = if (snapshot.monster.isDefeated) {
            current.phase
        } else {
            when (snapshot.monster.currentPhase) {
                1 -> FightPhase.PHASE_I
                2 -> FightPhase.PHASE_II
                3 -> FightPhase.PHASE_III
                4 -> FightPhase.PHASE_IV
                5 -> FightPhase.PHASE_V
                6 -> FightPhase.PHASE_VI
                7 -> FightPhase.PHASE_VII
                8 -> FightPhase.PHASE_VIII
                9 -> FightPhase.PHASE_IX
                else -> current.phase
            }
        }

        _state.update {
            it.copy(
                monster = current.monster,
                message = "Отменено: ${snapshot.description}",
                canUndo = actionHistory.isNotEmpty(),
                phase = newPhase
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun commitDamage() {
        timerJob?.cancel()
        val current = _state.value
        val before = current.toBattleValues()
        if (current.pendingDamage == 0) {
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
        val damageDescription = if (current.pendingDamage >= 0) {
            "урон +${current.pendingDamage}"
        } else {
            "лечение ${-current.pendingDamage}"
        }
        saveSnapshot(ActionType.DAMAGE, damageDescription)

        val result = monster.takeDamage(current.pendingDamage)

        val newPhase = when {
            result.message.contains("побеждён") -> FightPhase.VICTORY
            result.phaseChanged -> {
                when (result.newPhase) {
                    2 -> FightPhase.PHASE_II
                    3 -> FightPhase.PHASE_III
                    4 -> FightPhase.PHASE_IV
                    5 -> FightPhase.PHASE_V
                    6 -> FightPhase.PHASE_VI
                    7 -> FightPhase.PHASE_VII
                    8 -> FightPhase.PHASE_VIII
                    9 -> FightPhase.PHASE_IX
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
                pendingDamageForWound = current.selectedBoss?.getStance(monster.currentPhase - 1)?.damageForWound?.toString() ?: "",
                pendingHealthForStanceChange = current.selectedBoss?.getStance(monster.currentPhase - 1)?.healthForStanceChange?.toString() ?: "",
                canUndo = actionHistory.isNotEmpty()
            )
        }
        highlightChanged(before)
        emitDamageVibration(result)
    }

    fun confirmPhaseChange(damageForWound: Int?, healthForStanceChange: Int?, bossHealth: Int = 0) {
        val current = _state.value
        val before = current.toBattleValues()
        val previousDfw = current.monster.damageForWound
        saveSnapshot(ActionType.PHASE_CHANGE, "смена на стойку ${current.monster.currentPhase + 1}")
        val totalDamageForWound = damageForWound?.let { it * current.hunterCount }
        current.monster.resetPhase(totalDamageForWound, healthForStanceChange)
        if (bossHealth > 0) {
            current.monster.currentHealth = bossHealth
        }

        val immediateResult = if (previousDfw == null && totalDamageForWound != null && current.monster.accumulatedDamage > 0) {
            current.monster.takeDamage(0)
        } else {
            null
        }

        val newPhase = when {
            immediateResult?.message?.contains("побеждён") == true -> FightPhase.VICTORY
            immediateResult?.phaseChanged == true -> phaseForNumber(immediateResult.newPhase)
            else -> current.phase
        }

        val hscText = healthForStanceChange?.let { "$it HP" } ?: "по запросу"
        val dfwText = totalDamageForWound?.let { "Урон для раны: $it" } ?: "Порог раны отсутствует"
        val message = buildString {
            if (immediateResult != null) append(immediateResult.message).append(' ')
            append("Стойка ${current.monster.currentPhase}. ").append(dfwText).append(", смена: ").append(hscText)
        }

        _state.update {
            it.copy(
                showPhaseChangeDialog = false,
                phase = newPhase,
                monster = current.monster,
                lastDamageResult = immediateResult,
                message = message,
                canUndo = actionHistory.isNotEmpty()
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun dismissPhaseChangeDialog() {
        onUndoPress()
        _state.update { it.copy(showPhaseChangeDialog = false) }
    }

    fun confirmRageSurge() {
        val current = _state.value
        val before = current.toBattleValues()
        current.monster.rage = current.hunterCount
        _state.update {
            it.copy(
                monster = current.monster,
                showRageSurgeDialog = false,
                message = "Всплеск ярости! Ярость сброшена до ${current.hunterCount}"
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun addRage(amount: Int) {
        val sign = if (amount >= 0) "+" else ""
        val before = _state.value.toBattleValues()
        saveSnapshot(ActionType.RAGE, "ярость $sign$amount")
        _state.update { current ->
            val monster = current.monster
            monster.rage += amount
            current.copy(
                monster = monster,
                message = "Ярость: ${monster.rage}",
                showRageSurgeDialog = monster.rage >= current.hunterCount * 3,
                canUndo = actionHistory.isNotEmpty()
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun removeRage(amount: Int) {
        val before = _state.value.toBattleValues()
        saveSnapshot(ActionType.RAGE, "ярость -$amount")
        _state.update { current ->
            current.copy(
                monster = current.monster.apply { removeRage(amount) },
                message = "Ярость: ${current.monster.rage}",
                canUndo = actionHistory.isNotEmpty()
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun addRagePerHunter() {
        val before = _state.value.toBattleValues()
        saveSnapshot(ActionType.RAGE, "ярость +1/охот")
        _state.update { current ->
            current.copy(
                monster = current.monster.apply {
                    addRagePerHunter(current.hunterCount)
                },
                message = "Ярость: ${current.monster.rage}",
                showRageSurgeDialog = current.monster.rage >= current.hunterCount * 3,
                canUndo = actionHistory.isNotEmpty()
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun addRagePerHunterMinusOne() {
        val before = _state.value.toBattleValues()
        saveSnapshot(ActionType.RAGE, "ярость +1/охот-1")
        _state.update { current ->
            val count = (current.hunterCount - 1).coerceAtLeast(0)
            current.copy(
                monster = current.monster.apply {
                    addRagePerHunter(count)
                },
                message = "Ярость: ${current.monster.rage}",
                showRageSurgeDialog = current.monster.rage >= current.hunterCount * 3,
                canUndo = actionHistory.isNotEmpty()
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun toggleHardened() {
        val before = _state.value.toBattleValues()
        _state.update { current ->
            val hardened = current.monster.toggleHardened()
            current.copy(
                monster = current.monster,
                message = if (hardened) "Монстр устойчивый" else "Монстр неустойчивый"
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun endRound() {
        val current = _state.value
        val before = current.toBattleValues()
        saveSnapshot(ActionType.ROUND_END, "завершение раунда ${current.currentRound}")
        var phaseUpdated = false
        var newFightPhase = current.phase
        var defeatMessage: String? = null
        var appliedDamageResult: DamageResult? = null

        if (current.pendingDamage > 0) {
            val result = current.monster.takeDamage(current.pendingDamage)

            if (result.message.contains("побеждён")) {
                _state.update {
                    it.copy(
                        phase = FightPhase.VICTORY,
                        message = result.message,
                        monster = current.monster,
                        pendingDamage = 0,
                        isTimerRunning = false,
                        damageInputText = "",
                        inputMode = InputMode.NONE,
                        canUndo = actionHistory.isNotEmpty()
                    )
                }
                highlightChanged(before)
                emitDamageVibration(result)
                return
            }

            if (result.phaseChanged) {
                newFightPhase = when (result.newPhase) {
                    2 -> FightPhase.PHASE_II
                    3 -> FightPhase.PHASE_III
                    4 -> FightPhase.PHASE_IV
                    5 -> FightPhase.PHASE_V
                    6 -> FightPhase.PHASE_VI
                    7 -> FightPhase.PHASE_VII
                    8 -> FightPhase.PHASE_VIII
                    9 -> FightPhase.PHASE_IX
                    else -> newFightPhase
                }
                phaseUpdated = true
            }
            appliedDamageResult = result
        }

        current.monster.endRound(current.hunterCount)

        val nextRound = current.currentRound + 1
        val defeatByRounds = nextRound > current.maxRounds
        val finalPhase = if (defeatByRounds) FightPhase.DEFEAT else newFightPhase

        _state.update {
            it.copy(
                monster = current.monster,
                currentRound = nextRound,
                phase = finalPhase,
                pendingDamage = 0,
                damageInputText = "",
                inputMode = InputMode.NONE,
                isTimerRunning = false,
                canUndo = actionHistory.isNotEmpty(),
                showPhaseChangeDialog = phaseUpdated && finalPhase != FightPhase.DEFEAT,
                pendingDamageForWound = current.selectedBoss?.getStance(current.monster.currentPhase - 1)?.damageForWound?.toString() ?: "",
                pendingHealthForStanceChange = current.selectedBoss?.getStance(current.monster.currentPhase - 1)?.healthForStanceChange?.toString() ?: "",
                showRageSurgeDialog = finalPhase != FightPhase.DEFEAT &&
                    current.monster.rage >= current.hunterCount * 3,
                message = if (defeatByRounds) {
                    "Поражение! Прошло ${current.maxRounds} раундов."
                } else {
                    "Раунд $nextRound. Ярость: ${current.monster.rage}"
                }
            )
        }
        highlightChanged(before)
        if (appliedDamageResult != null) {
            emitDamageVibration(appliedDamageResult)
        } else {
            emitVibration(BattleVibrationEvent.SHORT)
        }
    }

    fun onSurrender() {
        timerJob?.cancel()
        _state.update {
            it.copy(
                phase = FightPhase.DEFEAT,
                showPhaseChangeDialog = false,
                showRageSurgeDialog = false,
                isTimerRunning = false,
                pendingDamage = 0,
                damageInputText = "",
                message = "Поражение! Вы сдались."
            )
        }
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun resetBattle() {
        timerJob?.cancel()
        actionHistory.clear()
        _state.update { BattleScreenState() }
    }
}
