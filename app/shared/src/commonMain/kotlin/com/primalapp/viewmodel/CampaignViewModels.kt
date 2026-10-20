package com.primalapp.viewmodel

import com.primalapp.campaign.CampaignOp
import com.primalapp.campaign.CampaignRuleException
import com.primalapp.campaign.CampaignRules
import com.primalapp.campaign.CampaignState
import com.primalapp.catalog.Catalog
import com.primalapp.database.currentTimeMillis
import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.HunterClass
import com.primalapp.sync.StoredCampaign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Общее для экранов одной кампании (лист, кузница, лаборатория, итог боя, переход главы): наблюдение за
 * кампанией в хранилище и выполнение действий с сообщением об ошибке правил.
 */
abstract class CampaignScopedViewModel(
    protected val app: AppViewModel,
    protected val localId: Long,
    protected val scope: CoroutineScope
) {
    protected val core = app.core

    private val _campaign = MutableStateFlow<StoredCampaign?>(null)
    val campaign: StateFlow<StoredCampaign?> = _campaign.asStateFlow()

    protected val catalog: Catalog get() = core.catalog.current
    protected val rules: CampaignRules get() = core.store.rules

    /** Начать наблюдение — в конце `init` наследника, когда его поля уже готовы. */
    protected fun observe() {
        scope.launch {
            core.store.observe(localId).collect { stored ->
                _campaign.value = stored
                if (stored != null) onCampaign(stored)
            }
        }
    }

    /** Новое состояние кампании (после действия, синхронизации или правки другим участником). */
    protected abstract fun onCampaign(campaign: StoredCampaign)

    protected val actor get() = core.settings.current.activeAccount?.actor()

    /**
     * Охотник по умолчанию: выбранный игроком ([picked]), иначе основной персонаж, иначе первый в отряде
     * (web qa № 150).
     */
    protected fun defaultHunter(state: CampaignState, campaign: StoredCampaign, picked: HunterClass?): HunterClass? =
        picked?.takeIf { state.hunter(it) != null }
            ?: campaign.mainHunter?.takeIf { state.hunter(it) != null }
            ?: state.squad.firstOrNull()?.hunterClass

    protected fun now(): Long = currentTimeMillis()

    /** Дополнения, задания которых показываются игроку. */
    protected val visibleExpansions: Set<Expansion> get() = core.settings.current.expansions

    protected fun questVisible(number: Int): Boolean {
        val expansion = catalog.quest(number)?.expansion ?: Expansion.ofQuest(number)
        return expansion == null || expansion in visibleExpansions
    }

    /** Выполнить действие; недопустимое — сообщение игроку. */
    protected fun perform(op: CampaignOp, onDone: (CampaignState) -> Unit = {}) {
        scope.launch {
            try {
                onDone(core.store.perform(localId, op))
            } catch (error: CampaignRuleException) {
                app.showMessage(error.message ?: "Действие недоступно.")
            }
        }
    }
}
