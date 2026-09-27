package com.primalapp.viewmodel

import com.primalapp.model.ext.DamageResult
import com.primalapp.model.Hunter
import com.primalapp.model.Monster
import com.primalapp.model.campaign.Boss
import com.primalapp.model.ext.addRagePerHunter
import com.primalapp.model.ext.endRound
import com.primalapp.model.ext.healWound
import com.primalapp.model.ext.removeRage
import com.primalapp.model.ext.resetPhase
import com.primalapp.model.ext.takeDamage
import com.primalapp.model.ext.toggleHardened
import com.primalapp.model.ext.toggleResilient
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
    HARDENED,
    RESILIENT
}

enum class BattleVibrationEvent {
    SHORT,
    DOUBLE
}

/** Статусы монстра с описанием для кнопки «i» на экране боя (R-3). */
enum class MonsterStatusInfo(val title: String, val description: String) {
    HARDENED(
        "Затвердевший",
        "Состояние монстра (например, торамат и дигоракс при затвердевании 3). После нанесения ран весь " +
            "оставшийся урон, которого не хватило на очередную рану, сбрасывается."
    ),
    RESILIENT(
        "Устойчивость стойки",
        "Ключевое слово карты стойки. Когда монстр переходит на следующую стойку, накопленный урон " +
            "не переносится на новую карту стойки, а сбрасывается."
    )
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
    /** Поля диалога «Смена стойки!» заполнены данными стойки из базы боссов. */
    val phaseChangeFromBossData: Boolean = false,
    val damageInputText: String = "",
    val inputMode: InputMode = InputMode.NONE,
    val canUndo: Boolean = false,
    val showRageSurgeDialog: Boolean = false,
    val selectedBoss: Boss? = null,
    val selectedDifficulty: Int = 0,
    val highlightedParams: Set<BattleParam> = emptySet(),
    /** Поражение наступило из-за «Сдаться», а не из-за окончания раундов (D-14). */
    val surrendered: Boolean = false,
    /** Открытое описание статуса монстра (кнопка «i»). */
    val statusInfo: MonsterStatusInfo? = null
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

    /**
     * Начинает бой. Прочность и порог смены стойки берутся из переданных значений полей подготовки
     * (предзаполнены стойкой I выбранного босса и могут быть изменены вручную — D-2).
     */
    fun startBattleWithHunters(
        hunters: List<Hunter>,
        damageForWound: Int?,
        healthForStanceChange: Int?,
        boss: Boss? = null,
        difficulty: Int = 0
    ) {
        val monsterName = boss?.name ?: "Монстр"
        val monster = Monster(
            name = monsterName,
            currentHealth = 10,
            rage = hunters.size,
            damageForWound = damageForWound?.let { hunters.size * it },
            healthForStanceChange = healthForStanceChange
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
                highlightedParams = emptySet(),
                surrendered = false,
                statusInfo = null,
                showPhaseChangeDialog = false,
                showRageSurgeDialog = false
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

    /** Результат применения урона с учётом смены стойки. */
    private data class StanceOutcome(
        val result: DamageResult,
        val victory: Boolean,
        /** Монстр перешёл на новую стойку — нужен диалог «Смена стойки!». */
        val needsDialog: Boolean
    )

    /**
     * Применяет урон (R-2): цикл ран останавливается на смене стойки, остаток урона переносится
     * и наносится с прочностью новой стойки после подтверждения её параметров в диалоге.
     * Победа — по флагу `isDefeated` и по тексту результата (D-18).
     */
    private fun applyDamage(monster: Monster, amount: Int): StanceOutcome {
        val result = monster.takeDamage(amount)
        val victory = monster.isDefeated || result.message.contains("побеждён")
        return StanceOutcome(
            result = if (victory) result.copy(message = "Монстр побеждён! Нанесено ран: ${result.woundsInflicted}") else result,
            victory = victory,
            needsDialog = result.phaseChanged && !victory
        )
    }

    /** Значения полей диалога «Смена стойки!»: из базы боссов, если у босса есть такая стойка. */
    private data class StancePrefill(val damageForWound: String, val healthForStanceChange: String, val fromBossData: Boolean)

    private fun stancePrefill(boss: Boss?, phase: Int): StancePrefill {
        val stance = boss?.getStance(phase - 1) ?: return StancePrefill("", "", false)
        return StancePrefill(
            damageForWound = stance.damageForWound?.toString().orEmpty(),
            healthForStanceChange = stance.healthForStanceChange?.toString().orEmpty(),
            fromBossData = true
        )
    }

    /** Поля состояния для открытия диалога «Смена стойки!» на стойке [phase]. */
    private fun BattleScreenState.withPhaseChangeDialog(show: Boolean, phase: Int): BattleScreenState {
        if (!show) return copy(showPhaseChangeDialog = false)
        val prefill = stancePrefill(selectedBoss, phase)
        return copy(
            showPhaseChangeDialog = true,
            pendingDamageForWound = prefill.damageForWound,
            pendingHealthForStanceChange = prefill.healthForStanceChange,
            phaseChangeFromBossData = prefill.fromBossData
        )
    }

    private fun resolvePhase(outcome: StanceOutcome, current: FightPhase): FightPhase = when {
        outcome.victory -> FightPhase.VICTORY
        outcome.result.phaseChanged -> phaseForNumber(outcome.result.newPhase)
        else -> current
    }

    fun onManualStanceChange() {
        val current = _state.value
        val before = current.toBattleValues()
        val monster = current.monster
        if (monster.healthForStanceChange != null) return
        if (monster.currentPhase >= monster.maxPhases) return

        saveSnapshot(ActionType.PHASE_CHANGE, "смена на стойку ${monster.currentPhase + 1}")
        monster.currentPhase++
        _state.update {
            it.copy(
                phase = phaseForNumber(monster.currentPhase),
                monster = monster,
                canUndo = actionHistory.isNotEmpty()
            ).withPhaseChangeDialog(show = true, phase = monster.currentPhase)
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
        actionHistory.add(0, ActionSnapshot(snapshot, current.phase, actionType, description, current.currentRound))
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
        val hardened: Boolean,
        val resilient: Boolean
    )

    private fun BattleScreenState.toBattleValues(): BattleValues = BattleValues(
        phase = phase,
        round = currentRound,
        health = monster.currentHealth,
        rage = monster.rage,
        accumulatedDamage = monster.accumulatedDamage,
        damageForWound = monster.damageForWound,
        healthForStanceChange = monster.healthForStanceChange,
        hardened = monster.isHardened,
        resilient = monster.isResilient
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
        if (before.resilient != after.monster.isResilient) changed += BattleParam.RESILIENT
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
            phaseForNumber(snapshot.monster.currentPhase)
        }

        _state.update {
            it.copy(
                monster = current.monster,
                currentRound = snapshot.round,
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
            "снижение накопленного урона на ${-current.pendingDamage}"
        }
        saveSnapshot(ActionType.DAMAGE, damageDescription)

        val outcome = applyDamage(monster, current.pendingDamage)
        val newPhase = resolvePhase(outcome, current.phase)

        _state.update {
            it.copy(
                isTimerRunning = false,
                message = outcome.result.message,
                lastDamageResult = outcome.result,
                pendingDamage = 0,
                damageInputText = "",
                inputMode = InputMode.NONE,
                phase = newPhase,
                monster = monster,
                canUndo = actionHistory.isNotEmpty()
            ).withPhaseChangeDialog(show = outcome.needsDialog && newPhase != FightPhase.VICTORY, phase = monster.currentPhase)
        }
        highlightChanged(before)
        emitDamageVibration(outcome.result)
    }

    /** Кнопка «Заживить рану»: здоровье монстра +1, накопленный урон не меняется (R-4). */
    fun healWound() {
        val current = _state.value
        if (!isBattlePhase(current.phase)) return
        val before = current.toBattleValues()
        saveSnapshot(ActionType.HEAL, "заживление раны")
        val healed = current.monster.healWound()
        if (!healed) actionHistory.removeAt(0)
        _state.update {
            it.copy(
                monster = current.monster,
                message = if (healed) "Рана заживлена. Здоровье: ${current.monster.currentHealth}"
                    else "Здоровье уже максимальное",
                canUndo = actionHistory.isNotEmpty()
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    /**
     * Подтверждение параметров новой стойки (поля предзаполнены из базы боссов, если стойка известна).
     * Перенесённый урон сразу наносится с новой прочностью (правила: «нанесите рану прежде, чем…»);
     * если он снова доводит до порога — открывается диалог следующей стойки.
     */
    fun confirmPhaseChange(damageForWound: Int?, healthForStanceChange: Int?, bossHealth: Int = 0) {
        val current = _state.value
        val before = current.toBattleValues()
        saveSnapshot(ActionType.PHASE_CHANGE, "смена на стойку ${current.monster.currentPhase}")
        val totalDamageForWound = damageForWound?.let { it * current.hunterCount }
        current.monster.resetPhase(totalDamageForWound, healthForStanceChange)
        if (bossHealth > 0) {
            current.monster.currentHealth = bossHealth
        }

        val outcome = if (totalDamageForWound != null && current.monster.accumulatedDamage > 0) {
            applyDamage(current.monster, 0)
        } else {
            null
        }

        val newPhase = if (outcome != null) resolvePhase(outcome, current.phase) else current.phase

        val hscText = healthForStanceChange?.let { "$it HP" } ?: "по запросу"
        val dfwText = totalDamageForWound?.let { "Урон для раны: $it" } ?: "Порог раны отсутствует"
        val message = buildString {
            if (outcome != null && (outcome.result.woundsInflicted > 0 || outcome.victory)) {
                append(outcome.result.message).append(' ')
            }
            append("Стойка ${current.monster.currentPhase}. ").append(dfwText).append(", смена: ").append(hscText)
        }

        _state.update {
            it.copy(
                phase = newPhase,
                monster = current.monster,
                lastDamageResult = outcome?.result,
                message = message,
                canUndo = actionHistory.isNotEmpty()
            ).withPhaseChangeDialog(
                show = outcome?.needsDialog == true && newPhase != FightPhase.VICTORY,
                phase = current.monster.currentPhase
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
                message = "Выплеск ярости! Каждый охотник получил урон, равный силе монстра. " +
                    "Ярость сброшена до ${current.hunterCount}"
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

    /** «Затвердевший»: остаток урона после ран сгорает. */
    fun toggleHardened() {
        val before = _state.value.toBattleValues()
        _state.update { current ->
            val hardened = current.monster.toggleHardened()
            current.copy(
                monster = current.monster,
                message = if (hardened) "Монстр затвердевший" else "Монстр не затвердевший"
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    /** «Устойчивость» стойки: при смене стойки накопленный урон сбрасывается. */
    fun toggleResilient() {
        val before = _state.value.toBattleValues()
        _state.update { current ->
            val resilient = current.monster.toggleResilient()
            current.copy(
                monster = current.monster,
                message = if (resilient) "Стойка с устойчивостью" else "Стойка без устойчивости"
            )
        }
        highlightChanged(before)
        emitVibration(BattleVibrationEvent.SHORT)
    }

    fun onStatusInfoRequested(info: MonsterStatusInfo) {
        _state.update { it.copy(statusInfo = info) }
    }

    fun onStatusInfoDismissed() {
        _state.update { it.copy(statusInfo = null) }
    }

    fun endRound() {
        val current = _state.value
        val before = current.toBattleValues()
        saveSnapshot(ActionType.ROUND_END, "завершение раунда ${current.currentRound}")
        var newFightPhase = current.phase
        var needsDialog = false
        var appliedDamageResult: DamageResult? = null

        if (current.pendingDamage > 0) {
            val outcome = applyDamage(current.monster, current.pendingDamage)

            if (outcome.victory) {
                _state.update {
                    it.copy(
                        phase = FightPhase.VICTORY,
                        message = outcome.result.message,
                        monster = current.monster,
                        pendingDamage = 0,
                        isTimerRunning = false,
                        damageInputText = "",
                        inputMode = InputMode.NONE,
                        canUndo = actionHistory.isNotEmpty()
                    )
                }
                highlightChanged(before)
                emitDamageVibration(outcome.result)
                return
            }

            newFightPhase = resolvePhase(outcome, newFightPhase)
            needsDialog = outcome.needsDialog
            appliedDamageResult = outcome.result
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
                showRageSurgeDialog = finalPhase != FightPhase.DEFEAT &&
                    current.monster.rage >= current.hunterCount * 3,
                message = if (defeatByRounds) {
                    "Поражение! Прошло ${current.maxRounds} раундов."
                } else {
                    "Раунд $nextRound. Ярость: ${current.monster.rage}"
                }
            ).withPhaseChangeDialog(show = needsDialog && finalPhase != FightPhase.DEFEAT, phase = current.monster.currentPhase)
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
                surrendered = true,
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
