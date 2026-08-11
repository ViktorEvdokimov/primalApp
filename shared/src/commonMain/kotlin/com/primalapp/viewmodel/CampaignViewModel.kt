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
    val selectedQuestNumbers: Set<Int> = emptySet(),
    val isSaving: Boolean = false,
    val saveMessage: String = "",
    val error: String? = null,
    val lastActiveBattle: AppScreen? = null,
    val fatalError: String? = null,
    val preBattleHunters: List<Hunter> = emptyList(),
    val isPrologue: Boolean = false,
    val defeatedBosses: List<String> = emptyList()
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

    companion object {
        val ALL_BOSS_NAMES = listOf(
            "Вираксен", "Торамат", "Коровон", "Харджа", "Дигоракс", "Оруксен",
            "Фелаксир", "Юром", "Таррагуа", "Моркраас", "Озев", "Иекорос", "Пробуждённый"
        )
    }

    fun onQuickBattleSelected() {
        if (battleViewModel == null) {
            battleViewModel = BattleViewModel(scope)
        }
        _state.update { it.copy(screen = AppScreen.QuickBattle) }
    }

    fun onCampaignModeSelected() {
        scope.launch {
            try {
                val count = repository.getCampaignCount()
                if (count == 0) {
                    _state.update { it.copy(screen = AppScreen.CampaignSetup) }
                } else {
                    val campaigns = repository.getAllCampaigns()
                    _state.update { it.copy(screen = AppScreen.CampaignList, campaigns = campaigns) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(fatalError = "Ошибка базы данных. Перезапустите приложение.") }
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
            val campaign = repository.getCampaign(campaignId)
            _state.update { it.copy(currentCampaign = campaign, hunters = hunters, isPrologue = true) }
            startBattleInternal(campaignId, hunters)
        }
    }

    private fun startBattleInternal(campaignId: Long, hunters: List<CampaignHunter>) {
        val battleHunters = hunters.map { Hunter(name = "${it.playerName} (${it.className.displayName})") }
        battleViewModel = BattleViewModel(scope)
        _state.update { it.copy(preBattleHunters = battleHunters, screen = AppScreen.CampaignBattle(campaignId)) }
        observeBattleForAutoSave(campaignId)
    }

    fun onConfirmCampaignBattleStart(damageForWound: Int, healthForStanceChange: Int) {
        val hunters = _state.value.preBattleHunters
        if (hunters.isEmpty()) return
        battleViewModel?.startBattleWithHunters(
            hunters = hunters,
            damageForWound = damageForWound,
            healthForStanceChange = healthForStanceChange
        )
        _state.update { it.copy(preBattleHunters = emptyList()) }
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
        battleCollectJob?.cancel()
        startBattleInternal(campaignId, hunters)
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
            val trophies = repository.getTrophies(campaignId)
            val isPrologue = _state.value.isPrologue
            val bossNames = if (isPrologue) {
                listOf("Вираксен")
            } else {
                ALL_BOSS_NAMES
            }
            _state.update {
                it.copy(
                    showPostVictory = true,
                    bossName = if (isPrologue) "Вираксен" else "",
                    bossElement = if (isPrologue) Element.FIRE else null,
                    defeatedBosses = bossNames,
                    completedQuestId = "",
                    availableQuestsForNext = emptyList(),
                    selectedQuestNumbers = emptySet(),
                    error = null
                )
            }
        }
    }

    fun onVictoryBossNameChanged(name: String) {
        _state.update { it.copy(bossName = name) }
    }

    fun onVictoryBossSelected(name: String) {
        _state.update { it.copy(bossName = name) }
    }

    fun onVictoryBossElementChanged(element: Element) {
        _state.update { it.copy(bossElement = element) }
    }

    fun onVictoryQuestToggled(number: Int) {
        _state.update { state ->
            val current = state.selectedQuestNumbers.toMutableSet()
            if (current.contains(number)) {
                current.remove(number)
            } else {
                current.add(number)
            }
            state.copy(selectedQuestNumbers = current)
        }
    }

    fun onConfirmVictory() {
        val state = _state.value
        val campaignId = state.currentCampaign?.id ?: return
        val element = state.bossElement
        if (element == null) {
            _state.update { it.copy(error = "Выберите стихию босса") }
            return
        }
        val bossName = state.bossName.ifBlank {
            _state.update { it.copy(error = "Введите имя босса") }
            return
        }
        val questNumbers = state.selectedQuestNumbers
        if (questNumbers.isEmpty()) {
            _state.update { it.copy(error = "Выберите хотя бы одно задание") }
            return
        }
        scope.launch {
            val trophy = Trophy(
                bossName = bossName,
                element = element,
                chapter = state.currentCampaign?.currentChapter ?: 1
            )
            questNumbers.forEach { number ->
                val quest = Quest(
                    id = number.toString(),
                    name = "Задание $number",
                    chapter = state.currentCampaign?.currentChapter ?: 1,
                    element = element,
                    questNumber = number,
                    isAvailable = true
                )
                repository.saveQuest(campaignId, quest)
            }
            val firstQuestId = questNumbers.first().toString()
            repository.saveVictory(
                campaignId = campaignId,
                trophy = trophy,
                completedQuestId = firstQuestId,
                nextQuestId = questNumbers.joinToString(",") { it.toString() }
            )
            val hunters = state.hunters
            hunters.forEach { hunter ->
                addResourceToAll(hunter.id, "ELEMENT", element.name, 1)
            }
            _state.update { it.copy(showPostVictory = false, error = null, selectedQuestNumbers = emptySet(), isPrologue = false) }
            loadCampaignSheet(campaignId)
        }
    }

    fun onBackToMenu() {
        battleCollectJob?.cancel()
        battleViewModel = null
        lastSavedRound = 0
        _state.update { CampaignUiState() }
    }

    fun onPauseBattle() {
        val currentScreen = _state.value.screen
        if (currentScreen !is AppScreen.CampaignBattle && currentScreen != AppScreen.QuickBattle) return
        _state.update {
            it.copy(screen = AppScreen.MainMenu, lastActiveBattle = currentScreen)
        }
    }

    fun onResumeBattle() {
        val battle = _state.value.lastActiveBattle ?: return
        _state.update { it.copy(screen = battle, lastActiveBattle = null) }
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
