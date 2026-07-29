package com.primalapp.viewmodel

import com.primalapp.model.Hunter
import com.primalapp.model.campaign.Achievement
import com.primalapp.model.campaign.Campaign
import com.primalapp.model.campaign.CampaignHunter
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.Quest
import com.primalapp.model.campaign.ResourceType
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.model.campaign.SkillNode
import com.primalapp.model.campaign.Trophy
import com.primalapp.repository.CampaignRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AppScreen {
    data object MainMenu : AppScreen()
    data object CampaignSetup : AppScreen()
    data object CampaignList : AppScreen()
    data class CampaignSheet(val campaignId: Long) : AppScreen()
    data class CampaignBattle(val campaignId: Long) : AppScreen()
    data object QuickBattle : AppScreen()
}

data class CampaignUiState(
    val screen: AppScreen = AppScreen.MainMenu,
    val campaigns: List<Campaign> = emptyList(),
    val campaignName: String = "",
    val selectedClasses: List<HunterClass> = emptyList(),
    val currentCampaign: Campaign? = null,
    val hunters: List<CampaignHunter> = emptyList(),
    val selectedHunterIndex: Int = 0,
    val skills: List<SkillNode> = emptyList(),
    val materials: Map<Material, Int> = emptyMap(),
    val plants: Map<Plant, Int> = emptyMap(),
    val elements: Map<Element, Int> = emptyMap(),
    val notes: String = "",
    val availableSkillBranches: List<SkillBranch> = emptyList(),
    val showExchangeDialog: Boolean = false,
    val exchangeResourceType: ResourceType? = null,
    val exchangeResourceName: String = "",
    val exchangeAmount: String = "0",
    val showPostVictory: Boolean = false,
    val bossName: String = "",
    val bossElement: Element? = null,
    val completedQuestId: String = "",
    val availableQuestsForNext: List<Quest> = emptyList(),
    val selectedNextQuestId: String? = null,
    val isSaving: Boolean = false,
    val saveMessage: String = "",
    val error: String? = null
)

class CampaignViewModel(
    private val repository: CampaignRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _state = MutableStateFlow(CampaignUiState())
    val state: StateFlow<CampaignUiState> = _state.asStateFlow()

    private var battleViewModel: BattleViewModel? = null
    val battleState: StateFlow<BattleScreenState>?
        get() = battleViewModel?.state

    private var saveJob: Job? = null
    private var battleCollectJob: Job? = null
    private var lastSavedRound: Int = 0

    fun onQuickBattleSelected() {
        _state.update { it.copy(screen = AppScreen.QuickBattle) }
    }

    fun onCampaignModeSelected() {
        scope.launch {
            val count = repository.getCampaignCount()
            if (count == 0) {
                _state.update { it.copy(screen = AppScreen.CampaignSetup) }
            } else {
                val campaigns = repository.getAllCampaigns()
                _state.update { it.copy(screen = AppScreen.CampaignList, campaigns = campaigns) }
            }
        }
    }

    fun onNewCampaignRequested() {
        _state.update { it.copy(screen = AppScreen.CampaignSetup, campaignName = "", selectedClasses = emptyList()) }
    }

    fun onCampaignNameChanged(name: String) {
        _state.update { it.copy(campaignName = name) }
    }

    fun onClassToggled(cls: HunterClass) {
        _state.update { current ->
            val classes = current.selectedClasses.toMutableList()
            if (classes.contains(cls)) {
                classes.remove(cls)
            } else {
                classes.add(cls)
            }
            current.copy(selectedClasses = classes)
        }
    }

    fun onStartCampaign() {
        val name = _state.value.campaignName.ifBlank {
            _state.update { it.copy(error = "Введите название кампании") }
            return
        }
        val classes = _state.value.selectedClasses
        if (classes.isEmpty()) {
            _state.update { it.copy(error = "Выберите хотя бы один класс") }
            return
        }
        scope.launch {
            val campaignId = repository.createCampaign(name)
            val hunters = classes.map { CampaignHunter(campaignId = campaignId, playerName = it.displayName, className = it) }
            repository.addHunters(campaignId, hunters)
            loadCampaignSheet(campaignId)
        }
    }

    fun onCampaignSelected(campaignId: Long) {
        scope.launch { loadCampaignSheet(campaignId) }
    }

    fun onDeleteCampaign(campaignId: Long) {
        scope.launch {
            repository.deleteCampaign(campaignId)
            val campaigns = repository.getAllCampaigns()
            _state.update { it.copy(campaigns = campaigns) }
        }
    }

    fun onHunterSelected(index: Int) {
        scope.launch {
            _state.update { it.copy(selectedHunterIndex = index) }
            val hunter = _state.value.hunters.getOrNull(index) ?: return@launch
            loadHunterResources(hunter.id)
            loadHunterSkills(hunter.id)
        }
    }

    fun onUnlockSkill(branch: SkillBranch, tier: Int) {
        scope.launch {
            val hunter = _state.value.hunters.getOrNull(_state.value.selectedHunterIndex) ?: return@launch
            repository.unlockSkill(hunter.id, branch, tier)
            loadHunterSkills(hunter.id)
        }
    }

    fun onNotesChanged(notes: String) {
        _state.update { it.copy(notes = notes) }
    }

    fun onSaveNotes() {
        scope.launch {
            val campaign = _state.value.currentCampaign ?: return@launch
            repository.saveCampaign(campaign.copy(notes = _state.value.notes))
        }
    }

    fun onUpdateChapter(chapter: Int) {
        scope.launch {
            val campaign = _state.value.currentCampaign ?: return@launch
            repository.updateChapter(campaign.id, chapter)
            val updated = repository.getCampaign(campaign.id) ?: return@launch
            _state.update { it.copy(currentCampaign = updated) }
        }
    }

    fun onOpenExchange(resourceType: ResourceType, resourceName: String) {
        _state.update {
            it.copy(
                showExchangeDialog = true,
                exchangeResourceType = resourceType,
                exchangeResourceName = resourceName,
                exchangeAmount = "0"
            )
        }
    }

    fun onCloseExchange() {
        _state.update { it.copy(showExchangeDialog = false) }
    }

    fun onExchangeAmountChanged(amount: String) {
        _state.update { it.copy(exchangeAmount = amount) }
    }

    fun onStartCampaignBattle() {
        val campaignId = _state.value.currentCampaign?.id ?: return
        val hunters = _state.value.hunters
        if (hunters.isEmpty()) return

        battleViewModel = BattleViewModel(scope)
        battleViewModel?.startBattleWithHunters(
            hunters = hunters.map { Hunter(name = "${it.playerName} (${it.className.displayName})") },
            damageForWound = 4,
            healthForStanceChange = 7
        )

        observeBattleForAutoSave(campaignId)
        _state.update { it.copy(screen = AppScreen.CampaignBattle(campaignId)) }
    }

    fun getBattleViewModel(): BattleViewModel? = battleViewModel

    fun onBattleFinished() {
        battleCollectJob?.cancel()
        val campaignId = _state.value.currentCampaign?.id ?: return
        scope.launch {
            saveCampaign()
            _state.update { it.copy(screen = AppScreen.CampaignSheet(campaignId)) }
        }
    }

    fun onVictory() {
        val campaignId = _state.value.currentCampaign?.id ?: return
        scope.launch {
            val quests = repository.getQuests(campaignId)
            _state.update {
                it.copy(
                    showPostVictory = true,
                    bossName = "",
                    bossElement = null,
                    completedQuestId = quests.firstOrNull { it.isAvailable && !it.isCompleted }?.id ?: "",
                    availableQuestsForNext = quests.filter { !it.isCompleted && it.isAvailable }
                )
            }
        }
    }

    fun onVictoryBossNameChanged(name: String) {
        _state.update { it.copy(bossName = name) }
    }

    fun onVictoryBossElementChanged(element: Element) {
        _state.update { it.copy(bossElement = element) }
    }

    fun onVictoryNextQuestSelected(questId: String) {
        _state.update { it.copy(selectedNextQuestId = questId) }
    }

    fun onConfirmVictory() {
        val state = _state.value
        val campaignId = state.currentCampaign?.id ?: return
        val element = state.bossElement ?: return
        val bossName = state.bossName.ifBlank {
            _state.update { it.copy(error = "Введите имя босса") }
            return
        }
        scope.launch {
            val trophy = Trophy(
                bossName = bossName,
                element = element,
                chapter = state.currentCampaign?.currentChapter ?: 1
            )
            repository.saveVictory(
                campaignId = campaignId,
                trophy = trophy,
                completedQuestId = state.completedQuestId,
                nextQuestId = state.selectedNextQuestId
            )
            val hunters = state.hunters
            hunters.forEach { hunter ->
                addResourceToAll(hunter.id, "ELEMENT", element.name, 1)
            }
            _state.update { it.copy(showPostVictory = false, error = null) }
            loadCampaignSheet(campaignId)
        }
    }

    fun onBackToMenu() {
        battleCollectJob?.cancel()
        battleViewModel = null
        _state.update { CampaignUiState() }
    }

    fun onErrorDismissed() {
        _state.update { it.copy(error = null) }
    }

    private fun observeBattleForAutoSave(campaignId: Long) {
        battleCollectJob?.cancel()
        val vm = battleViewModel ?: return
        battleCollectJob = scope.launch {
            vm.state.collect { battleState ->
                val current = _state.value
                if (battleState.phase == FightPhase.VICTORY || battleState.phase == FightPhase.DEFEAT) {
                    saveCampaign()
                    _state.update {
                        it.copy(
                            saveMessage = if (battleState.phase == FightPhase.VICTORY) "Победа сохранена" else "Поражение сохранено"
                        )
                    }
                } else if (battleState.currentRound != lastSavedRound) {
                    lastSavedRound = battleState.currentRound
                    saveCampaign()
                }
            }
        }
    }

    private suspend fun saveCampaign() {
        val campaign = _state.value.currentCampaign ?: return
        _state.update { it.copy(isSaving = true) }
        repository.saveCampaign(campaign)
        _state.update { it.copy(isSaving = false, saveMessage = "Сохранено") }
    }

    private suspend fun loadCampaignSheet(campaignId: Long) {
        val campaign = repository.getCampaign(campaignId) ?: run {
            _state.update { it.copy(error = "Кампания не найдена") }
            return
        }
        val hunters = repository.getHunters(campaignId)
        _state.update {
            it.copy(
                screen = AppScreen.CampaignSheet(campaignId),
                currentCampaign = campaign,
                hunters = hunters,
                selectedHunterIndex = if (hunters.isNotEmpty()) 0 else it.selectedHunterIndex,
                notes = campaign.notes
            )
        }
        if (hunters.isNotEmpty()) {
            loadHunterResources(hunters[0].id)
            loadHunterSkills(hunters[0].id)
        }
    }

    private suspend fun loadHunterResources(hunterId: Long) {
        val materials = repository.getMaterials(hunterId)
        val plants = repository.getPlants(hunterId)
        val elements = repository.getElements(hunterId)
        _state.update {
            it.copy(materials = materials, plants = plants, elements = elements)
        }
    }

    private suspend fun loadHunterSkills(hunterId: Long) {
        val skills = repository.getSkills(hunterId)
        val branches = repository.getAvailableSkillBranches(hunterId)
        _state.update {
            it.copy(skills = skills, availableSkillBranches = branches)
        }
    }

    private suspend fun addResourceToAll(hunterId: Long, resourceType: String, resourceName: String, amount: Int) {
        repository.addResource(hunterId, ResourceType.valueOf(resourceType), resourceName, amount)
    }
}
