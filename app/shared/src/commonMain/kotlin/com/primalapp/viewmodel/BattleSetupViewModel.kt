package com.primalapp.viewmodel

import com.primalapp.campaign.BattlePurpose
import com.primalapp.campaign.CampaignRuleException
import com.primalapp.campaign.CampaignRules
import com.primalapp.catalog.BossDef
import com.primalapp.catalog.Catalog
import com.primalapp.catalog.StanceChangeMode
import com.primalapp.catalog.toBattleBoss
import com.primalapp.database.currentTimeMillis
import com.primalapp.model.Hunter
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Expansion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Босс в списке выбора: «Огонь - Вираксен». */
data class BossOption(val code: String, val label: String, val element: Element?, val difficulties: List<Int>)

data class BattleSetupState(
    val ready: Boolean = false,
    val isCampaign: Boolean = false,
    val subtitle: String? = null,
    val bosses: List<BossOption> = emptyList(),
    val selectedBossCode: String? = null,
    val bossLocked: Boolean = false,
    val difficulty: Int = 0,
    val availableDifficulties: List<Int> = listOf(0, 1, 2, 3),
    val hunterCountText: String = "4",
    val hunterCountEditable: Boolean = true,
    val damageForWoundText: String = "",
    val healthForStanceText: String = "",
    val canChooseQuest: Boolean = false,
    /** Идущие бои других участников: «Вадим, задание 1 «Память пустыни», с 18:30». */
    val activeBattles: List<String> = emptyList(),
    val showActiveWarning: Boolean = false,
    /** В приложении уже есть незаконченный бой: «Текущий бой будет потерян. Начать новый бой?». */
    val showReplaceConfirm: Boolean = false,
    val error: String? = null
) {
    val selectedBossLabel: String
        get() = bosses.firstOrNull { it.code == selectedBossCode }?.label ?: "Ввести данные вручную"
}

/**
 * Подготовка к бою: экспедиция (бой без кампании) или бой кампании — та же форма с префиллом по заданию, главе
 * и отряду. Стойка I выбранного босса заполняет прочность и порог смены стойки; поля можно изменить вручную.
 */
class BattleSetupViewModel(
    private val app: AppViewModel,
    private val localId: Long?,
    private val questNumber: Int?,
    private val scope: CoroutineScope
) {
    private val core = app.core
    private val _state = MutableStateFlow(BattleSetupState(isCampaign = localId != null))
    val state: StateFlow<BattleSetupState> = _state.asStateFlow()

    private var purpose: BattlePurpose? = null
    private var chapter = 0
    private var progressSeq = 0
    private var hunters: List<Hunter> = emptyList()

    init {
        scope.launch { load() }
    }

    private val catalog: Catalog get() = core.catalog.current

    private suspend fun load() {
        val bosses = catalog.sortedBosses.map { boss ->
            BossOption(boss.code, boss.element?.let { "${it.displayName} - ${boss.name}" } ?: boss.name, boss.element, boss.difficulties)
        }
        if (localId == null) {
            _state.value = BattleSetupState(ready = true, bosses = bosses)
            return
        }
        val campaign = core.store.get(localId) ?: return
        val rules = core.store.rules
        val target = try {
            rules.battleTarget(campaign.state, questNumber)
        } catch (error: CampaignRuleException) {
            _state.value = BattleSetupState(ready = true, isCampaign = true, bosses = bosses, error = error.message)
            return
        }
        purpose = target.purpose
        chapter = campaign.state.chapter
        progressSeq = campaign.state.progressSeq
        hunters = campaign.state.squad.map { Hunter(name = "${it.playerName} (${it.hunterClass.displayName})") }
        val subtitle = when (target.purpose) {
            BattlePurpose.PROLOGUE -> "Пролог"
            BattlePurpose.FINAL -> "Финальный бой"
            BattlePurpose.FREE -> "Бой без задания"
            BattlePurpose.QUEST -> "Задание $questNumber «${catalog.quest(questNumber ?: 0)?.name.orEmpty()}»"
        }
        val active = rules.activeBattles(campaign.state, currentTimeMillis()).map { battle ->
            val who = battle.startedBy?.name ?: "Участник"
            val quest = battle.questNumber?.let { ", задание $it «${catalog.quest(it)?.name.orEmpty()}»" }
                ?: battle.bossCode?.let { ", босс «${catalog.bossName(it)}»" }.orEmpty()
            "$who$quest, с ${time(battle.startedAt)}"
        }
        _state.value = BattleSetupState(
            ready = true,
            isCampaign = true,
            subtitle = "${campaign.state.name} · $subtitle",
            bosses = bosses,
            bossLocked = target.forced,
            difficulty = target.difficulty,
            hunterCountText = hunters.size.toString(),
            hunterCountEditable = false,
            canChooseQuest = target.purpose == BattlePurpose.QUEST || target.purpose == BattlePurpose.FREE,
            activeBattles = active
        )
        target.bossCode?.let { applyBoss(it, keepDifficulty = true) }
    }

    fun onBossSelected(code: String?) {
        if (_state.value.bossLocked) return
        if (code == null) {
            _state.update { it.copy(selectedBossCode = null, availableDifficulties = listOf(0, 1, 2, 3)) }
        } else {
            applyBoss(code, keepDifficulty = false)
        }
    }

    /** Префилл из стойки I; нет текущего уровня у босса — единственный доступный (Пробуждённый — 3). */
    private fun applyBoss(code: String, keepDifficulty: Boolean) {
        val boss = catalog.boss(code) ?: return
        val levels = boss.difficulties
        val current = _state.value.difficulty
        val difficulty = when {
            current in levels -> current
            levels.size == 1 -> levels.first()
            keepDifficulty -> current
            else -> levels.minByOrNull { kotlin.math.abs(it - current) } ?: current
        }
        _state.update { it.copy(selectedBossCode = code, difficulty = difficulty, availableDifficulties = levels.ifEmpty { listOf(0, 1, 2, 3) }) }
        prefill(boss, difficulty)
    }

    fun onDifficultySelected(difficulty: Int) {
        _state.update { it.copy(difficulty = difficulty) }
        catalog.boss(_state.value.selectedBossCode)?.let { prefill(it, difficulty) }
    }

    private fun prefill(boss: BossDef, difficulty: Int) {
        val first = boss.stances(difficulty).minByOrNull { it.stance } ?: return
        _state.update {
            it.copy(
                damageForWoundText = first.toughnessPerHunter?.toString().orEmpty(),
                healthForStanceText = when (first.changeMode) {
                    StanceChangeMode.HEALTH -> first.changeAtHealth?.toString().orEmpty()
                    StanceChangeMode.FINAL -> "0"
                    StanceChangeMode.ON_DEMAND -> ""
                }
            )
        }
    }

    fun onHunterCountChanged(text: String) = _state.update { it.copy(hunterCountText = text.filter { c -> c.isDigit() }.take(2), error = null) }
    fun onDamageForWoundChanged(text: String) = _state.update { it.copy(damageForWoundText = text.filter { c -> c.isDigit() }.take(3), error = null) }
    fun onHealthForStanceChanged(text: String) = _state.update { it.copy(healthForStanceText = text.filter { c -> c.isDigit() }.take(2), error = null) }

    /** «Начать бой»: проверка полей, предупреждения об идущих боях и о замене незаконченного боя. */
    fun onStart() {
        val current = _state.value
        val count = current.hunterCountText.toIntOrNull()
        if (count == null || count <= 0) {
            _state.update { it.copy(error = "Количество охотников — целое число больше 0.") }
            return
        }
        val health = current.healthForStanceText.toIntOrNull()
        if (health != null && health > 9) {
            _state.update { it.copy(error = "Здоровье для смены стойки — от 0 до 9.") }
            return
        }
        when {
            current.activeBattles.isNotEmpty() && !current.showActiveWarning -> _state.update { it.copy(showActiveWarning = true) }
            app.hasBattle() -> _state.update { it.copy(showReplaceConfirm = true, showActiveWarning = false) }
            else -> launchBattle()
        }
    }

    /** «Начать бой» в окне «Уже идёт бой». */
    fun onConfirmActiveWarning() {
        _state.update { it.copy(showActiveWarning = false) }
        if (app.hasBattle()) _state.update { it.copy(showReplaceConfirm = true) } else launchBattle()
    }

    fun onConfirmReplace() {
        _state.update { it.copy(showReplaceConfirm = false) }
        launchBattle()
    }

    fun onDismissDialogs() = _state.update { it.copy(showActiveWarning = false, showReplaceConfirm = false) }

    private fun launchBattle() {
        val current = _state.value
        val dfw = current.damageForWoundText.toIntOrNull()
        val hsc = current.healthForStanceText.toIntOrNull()
        val boss = catalog.boss(current.selectedBossCode)?.toBattleBoss(current.difficulty)
        if (localId == null) {
            app.startExpedition(current.hunterCountText.toInt(), dfw, hsc, boss, current.difficulty)
            return
        }
        val battlePurpose = purpose ?: return
        val context = app.newBattleContext(localId, battlePurpose, questNumber.takeIf { battlePurpose == BattlePurpose.QUEST },
            current.selectedBossCode, current.difficulty, chapter, progressSeq)
        app.startCampaignBattle(context, hunters, dfw, hsc, boss)
    }

    /** Задания дополнений, отключённых в настройках, при выборе не показываются. */
    fun hiddenExpansions(): Set<Expansion> = Expansion.entries.toSet() - core.settings.current.expansions

    private fun time(millis: Long): String {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    companion object {
        const val MAX_HUNTERS = CampaignRules.MAX_HUNTERS
    }
}
