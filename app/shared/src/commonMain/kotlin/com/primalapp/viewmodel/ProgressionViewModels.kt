package com.primalapp.viewmodel

import com.primalapp.campaign.BattlePurpose
import com.primalapp.campaign.BattleReport
import com.primalapp.campaign.BattleResult
import com.primalapp.campaign.CampaignChangedException
import com.primalapp.campaign.CampaignOp
import com.primalapp.campaign.CampaignRuleException
import com.primalapp.campaign.CampaignRules
import com.primalapp.campaign.DefeatReason
import com.primalapp.campaign.NextStep
import com.primalapp.campaign.ResultOverrides
import com.primalapp.campaign.ResultPreview
import com.primalapp.campaign.TransitionPreview
import com.primalapp.catalog.RuleExplanation
import com.primalapp.catalog.RuleKind
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.sync.StoredCampaign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Правки наград («Редактировать»): применяются ровно они. */
data class OverridesForm(
    val bossCode: String?,
    val perHunter: Map<ResourceCode, Int>,
    val openQuests: Set<Int>,
    val achievements: Set<String>
)

data class OutcomeState(
    val loading: Boolean = true,
    val campaignName: String = "",
    val preview: ResultPreview? = null,
    /** Кампания изменилась, пока шёл бой: причины; доступно только «Отклонить результат». */
    val changedReasons: List<String> = emptyList(),
    val editing: OverridesForm? = null,
    val rewardCardHolders: List<HunterClass?> = emptyList(),
    val hunters: List<Pair<HunterClass, String>> = emptyList(),
    val confirmDismiss: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false
) {
    val conditionalQuests: List<RuleExplanation> get() = preview?.rules?.filter { it.kind == RuleKind.QUEST }.orEmpty()
    val conditionalRewards: List<RuleExplanation> get() = preview?.rules?.filter { it.kind != RuleKind.QUEST }.orEmpty()
}

/**
 * Итог боя кампании: награды за победу с условными правилами, «Принять», «Редактировать», «Отклонить». Поражение
 * записывается в историю сразу; победа в прологе принимается без окна наград. Онлайн перед показом наград
 * кампания обновляется с сервера: если главу уже закрыла чужая победа, итог можно только отклонить.
 */
class OutcomeViewModel(app: AppViewModel, localId: Long, scope: CoroutineScope) : CampaignScopedViewModel(app, localId, scope) {

    private val _state = MutableStateFlow(OutcomeState())
    val state: StateFlow<OutcomeState> = _state.asStateFlow()

    private var report: BattleReport? = null
    private var started = false

    init {
        observe()
    }

    override fun onCampaign(campaign: StoredCampaign) {
        if (started) return
        started = true
        scope.launch { prepare() }
    }

    private suspend fun prepare() {
        val context = app.campaignBattle.value?.takeIf { it.localId == localId }
        val battle = app.battleViewModel.state.value
        val result = when (battle.phase) {
            FightPhase.VICTORY -> BattleResult.VICTORY
            FightPhase.DEFEAT -> BattleResult.DEFEAT
            else -> null
        }
        if (context == null || result == null) {
            app.showMessage("Нет итога боя для этой кампании.")
            app.back()
            return
        }
        report = BattleReport(
            battleId = context.battleId,
            purpose = context.purpose,
            questNumber = context.questNumber,
            bossCode = context.bossCode,
            difficulty = context.difficulty,
            chapter = context.chapter,
            progressSeq = context.progressSeq,
            result = result,
            defeatReason = if (result == BattleResult.DEFEAT) (if (battle.surrendered) DefeatReason.SURRENDER else DefeatReason.ROUNDS) else null,
            roundsPlayed = battle.currentRound.coerceIn(1, battle.maxRounds),
            startedAt = context.startedAt,
            finishedAt = now()
        )
        if (core.settings.current.online) core.sync.syncCampaign(localId)
        val campaign = core.store.get(localId) ?: return
        val reportValue = report ?: return
        // Поражение — без окна наград; победа в прологе принимается сама (правила 36.1)
        if (result == BattleResult.DEFEAT || context.purpose == BattlePurpose.PROLOGUE) {
            try {
                rules.previewResult(campaign.state, reportValue, core.store.context(campaign, actor))
                submit(accept = true, overrides = null)
                return
            } catch (changed: CampaignChangedException) {
                if (result == BattleResult.DEFEAT) {
                    submit(accept = false, overrides = null)
                    return
                }
                show(campaign, null, changed.reasons)
                return
            } catch (error: CampaignRuleException) {
                show(campaign, null, listOf(error.message.orEmpty()))
                return
            }
        }
        try {
            val preview = rules.previewResult(campaign.state, reportValue, core.store.context(campaign, actor))
            show(campaign, preview, emptyList())
        } catch (changed: CampaignChangedException) {
            show(campaign, null, changed.reasons)
        }
    }

    private fun show(campaign: StoredCampaign, preview: ResultPreview?, reasons: List<String>) {
        val squad = campaign.state.squad
        _state.value = OutcomeState(
            loading = false,
            campaignName = campaign.state.name,
            preview = preview,
            changedReasons = reasons,
            rewardCardHolders = preview?.rewards?.rewardCards?.map { squad.firstOrNull()?.hunterClass }.orEmpty(),
            hunters = squad.map { it.hunterClass to "${it.playerName} (${it.hunterClass.displayName})" }
        )
    }

    fun onAccept() = submit(accept = true, overrides = _state.value.editing?.let {
        ResultOverrides(it.bossCode, it.perHunter.filterValues { quantity -> quantity > 0 }, it.openQuests.sorted(), it.achievements.toList())
    })

    fun onDismissRequested() = _state.update { it.copy(confirmDismiss = true) }

    fun onDismissCancelled() = _state.update { it.copy(confirmDismiss = false) }

    fun onDismissConfirmed() {
        _state.update { it.copy(confirmDismiss = false) }
        submit(accept = false, overrides = null)
    }

    fun onRewardCardHolder(index: Int, hunter: HunterClass) = _state.update { state ->
        state.copy(rewardCardHolders = state.rewardCardHolders.mapIndexed { i, old -> if (i == index) hunter else old })
    }

    // region «Редактировать»

    fun onEdit() {
        val preview = _state.value.preview ?: return
        _state.update {
            it.copy(editing = OverridesForm(
                bossCode = preview.rewards.trophyBoss,
                perHunter = preview.rewards.perHunter,
                openQuests = preview.rewards.openQuests.toSet(),
                achievements = preview.rewards.achievements.map { a -> a.code }.toSet()
            ))
        }
    }

    fun onEditCancelled() = _state.update { it.copy(editing = null) }

    /** Босс трофея: стихии меняются вместе с ним. */
    fun onEditBoss(code: String?) = _state.update { state ->
        val form = state.editing ?: return@update state
        val oldElement = catalog.boss(form.bossCode)?.element?.let { ResourceCode.of(it) }
        val newElement = catalog.boss(code)?.element?.let { ResourceCode.of(it) }
        val resources = form.perHunter.toMutableMap()
        if (oldElement != null) resources.remove(oldElement)
        if (newElement != null) resources[newElement] = (resources[newElement] ?: 0) + 2
        state.copy(editing = form.copy(bossCode = code, perHunter = resources))
    }

    fun onEditResource(code: ResourceCode, delta: Int) = _state.update { state ->
        val form = state.editing ?: return@update state
        val value = ((form.perHunter[code] ?: 0) + delta).coerceAtLeast(0)
        state.copy(editing = form.copy(perHunter = (form.perHunter + (code to value)).filterValues { it > 0 }))
    }

    fun onEditQuestToggled(number: Int) = _state.update { state ->
        val form = state.editing ?: return@update state
        state.copy(editing = form.copy(openQuests = if (number in form.openQuests) form.openQuests - number else form.openQuests + number))
    }

    fun onEditAchievementToggled(code: String) = _state.update { state ->
        val form = state.editing ?: return@update state
        state.copy(editing = form.copy(achievements = if (code in form.achievements) form.achievements - code else form.achievements + code))
    }

    // endregion

    private fun submit(accept: Boolean, overrides: ResultOverrides?) {
        val report = report ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                core.store.perform(localId, CampaignOp.SubmitResult(report, accept, overrides, _state.value.rewardCardHolders, now(), actor))
                app.finishCampaignBattle()
                val next = when {
                    !accept || !report.victory -> NextStep.CAMPAIGN_SHEET
                    report.purpose == BattlePurpose.FINAL -> NextStep.CAMPAIGN_COMPLETED
                    else -> NextStep.CHAPTER_TRANSITION
                }
                app.backTo(Screen.CampaignSheet(localId))
                when (next) {
                    NextStep.CHAPTER_TRANSITION -> app.navigate(Screen.Transition(localId))
                    NextStep.CAMPAIGN_COMPLETED -> app.showMessage("Кампания пройдена! Пробуждённый повержен.")
                    NextStep.CAMPAIGN_SHEET -> app.showMessage(
                        if (report.victory) "Итог боя отклонён и записан в историю." else "Поражение. Бой записан в историю."
                    )
                }
            } catch (changed: CampaignChangedException) {
                _state.update { it.copy(busy = false, loading = false, preview = null, changedReasons = changed.reasons) }
            } catch (error: CampaignRuleException) {
                _state.update { it.copy(busy = false, loading = false, error = error.message) }
            }
        }
    }

    /** Списки для формы правки: все боссы, задания каталога, достижения каталога. */
    fun bossOptions(): List<Option<String>> = catalog.sortedBosses.map { Option(it.code, it.element?.let { e -> "${e.displayName} - ${it.name}" } ?: it.name) }
    fun questOptions(): List<Option<Int>> = catalog.quests.filter { questVisible(it.number) }.map { Option(it.number, "${it.number}. ${it.name}") }
    fun achievementOptions(): List<Option<String>> = catalog.achievements.map { Option(it.code, it.name) }
    fun questName(number: Int): String = catalog.quest(number)?.name.orEmpty()
    fun bossName(code: String): String = catalog.bossName(code)
}

data class TransitionState(
    val loading: Boolean = true,
    val preview: TransitionPreview? = null,
    val decisions: Map<String, String> = emptyMap(),
    /** Решение главы без ответа — окно, которое закрывается только ответом. */
    val pendingDecision: com.primalapp.campaign.TransitionDecisionView? = null,
    val confirmReject: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false
) {
    val conditionalQuests: List<RuleExplanation> get() = preview?.rules?.filter { it.kind == RuleKind.QUEST }.orEmpty()
    val conditionalRewards: List<RuleExplanation> get() = preview?.rules?.filter { it.kind != RuleKind.QUEST }.orEmpty()
}

/**
 * Переход главы после принятой победы: сначала решения главы (глава 7), затем награды — «Принять» (новая глава) или
 * «Отклонить» (глава прежняя; окно со списком того, что не будет применено).
 */
class TransitionViewModel(app: AppViewModel, localId: Long, scope: CoroutineScope) : CampaignScopedViewModel(app, localId, scope) {

    private val _state = MutableStateFlow(TransitionState())
    val state: StateFlow<TransitionState> = _state.asStateFlow()

    init {
        observe()
    }

    override fun onCampaign(campaign: StoredCampaign) {
        if (_state.value.busy) return
        recompute(campaign)
    }

    private fun recompute(campaign: StoredCampaign) {
        try {
            val preview = rules.previewTransition(campaign.state, _state.value.decisions, core.store.context(campaign, actor))
            _state.update {
                it.copy(loading = false, preview = preview, error = null,
                    pendingDecision = preview.decisions.firstOrNull { decision -> decision.selected == null })
            }
        } catch (changed: CampaignChangedException) {
            _state.update { it.copy(loading = false, preview = null, error = changed.reasons.joinToString(" ")) }
        }
    }

    fun onDecision(code: String, option: String) {
        _state.update { it.copy(decisions = it.decisions + (code to option)) }
        campaign.value?.let { recompute(it) }
    }

    fun onAccept() {
        val preview = _state.value.preview ?: return
        if (!preview.decisionsComplete) {
            _state.update { it.copy(pendingDecision = preview.decisions.firstOrNull { d -> d.selected == null }) }
            return
        }
        submit(CampaignOp.Transition(true, _state.value.decisions, now(), actor),
            "${CampaignRules.chapterTitle(preview.toChapter)}: награды получены.")
    }

    fun onRejectRequested() = _state.update { it.copy(confirmReject = true) }

    fun onRejectCancelled() = _state.update { it.copy(confirmReject = false) }

    fun onRejectConfirmed() {
        _state.update { it.copy(confirmReject = false) }
        submit(CampaignOp.Transition(false, emptyMap(), now(), actor), "Награды главы отклонены — глава прежняя.")
    }

    private fun submit(op: CampaignOp, message: String) {
        _state.update { it.copy(busy = true) }
        scope.launch {
            try {
                core.store.perform(localId, op)
                app.showMessage(message)
                app.backTo(Screen.CampaignSheet(localId))
            } catch (error: CampaignRuleException) {
                _state.update { it.copy(busy = false, error = error.message) }
            }
        }
    }

    fun questName(number: Int): String = catalog.quest(number)?.name.orEmpty()
    fun bossName(code: String): String = catalog.bossName(code)
}
