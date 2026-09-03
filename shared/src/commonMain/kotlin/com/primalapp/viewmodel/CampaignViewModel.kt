package com.primalapp.viewmodel

import com.primalapp.model.Hunter
import com.primalapp.model.campaign.Achievement
import com.primalapp.model.campaign.Boss
import com.primalapp.model.campaign.Campaign
import com.primalapp.model.campaign.CampaignHunter
import com.primalapp.model.campaign.ChapterInfo
import com.primalapp.model.campaign.ConditionalMessage
import com.primalapp.model.campaign.ConditionalQuestOpen
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.Quest
import com.primalapp.model.campaign.ResourceType
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.model.campaign.SkillNode
import com.primalapp.model.campaign.TaskCondition
import com.primalapp.model.campaign.TaskConditionKind
import com.primalapp.model.campaign.TaskInfo
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

enum class QuestRewardsMode {
    VICTORY,
    DEFEAT
}

data class CampaignUiState(
    val screen: AppScreen = AppScreen.MainMenu,
    val campaigns: List<Campaign> = emptyList(),
    val campaignName: String = "",
    val selectedClasses: List<HunterClass> = emptyList(),
    val hunterPlayerNames: Map<HunterClass, String> = emptyMap(),
    val currentCampaign: Campaign? = null,
    val hunters: List<CampaignHunter> = emptyList(),
    val selectedHunterIndex: Int = 0,
    val skills: List<SkillNode> = emptyList(),
    val materials: Map<Material, Int> = emptyMap(),
    val plants: Map<Plant, Int> = emptyMap(),
    val elements: Map<Element, Int> = emptyMap(),
    val victoryMaterials: Map<Material, Int> = emptyMap(),
    val victoryPlants: Map<Plant, Int> = emptyMap(),
    val notes: String = "",
    val availableSkillBranches: List<SkillBranch> = emptyList(),
    val showPostVictory: Boolean = false,
    val bossName: String = "",
    val bossElement: Element? = null,
    val completedQuestId: String = "",
    val availableQuestsForNext: List<Quest> = emptyList(),
    val selectedNextQuestId: String? = null,
    val showQuestSelectDialog: Boolean = false,
    val activeQuestId: String? = null,
    val selectedQuestNumbers: Set<Int> = emptySet(),
    val selectedDefeatQuestNumbers: Set<Int> = emptySet(),
    val isSaving: Boolean = false,
    val saveMessage: String = "",
    val error: String? = null,
    val lastActiveBattle: AppScreen? = null,
    val fatalError: String? = null,
    val preBattleHunters: List<Hunter> = emptyList(),
    val isPrologue: Boolean = false,
    val defeatedBosses: List<String> = emptyList(),
    val campaignQuests: List<Quest> = emptyList(),
    val taskInfoByQuestNumber: Map<Int, TaskInfo> = emptyMap(),
    val showQuestRewards: Boolean = false,
    val questRewardsMode: QuestRewardsMode? = null,
    val activeQuestNumber: Int? = null,
    val editDefeatMode: Boolean = false,
    val showQuestEditDialog: Boolean = false,
    val editedQuestNumbers: Set<Int> = emptySet(),
    val showChapterRewards: Boolean = false,
    val chapterInfoByNumber: Map<Int, ChapterInfo> = emptyMap(),
    val chapterRewardsMessage: String = "",
    val campaignTrophies: List<Trophy> = emptyList(),
    val campaignAchievements: List<Achievement> = emptyList(),
    val showAchievementEditor: Boolean = false,
    val newAchievementName: String = "",
    val availableBosses: List<Boss> = emptyList(),
    val selectedPreBattleBoss: Boss? = null,
    val selectedPreBattleBossName: String? = null,
    val preBattleDifficulty: Int = 0,
    val preBattleDamageForWound: String = "4",
    val preBattleHealthForStance: String = "7",
    val preBattleHunterCount: Int = 4,
    val bossHasNoElement: Boolean = false
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
            "Фелаксир", "Юром", "Таррагуа", "Моркраас", "Озев", "Иекорос", "Пробуждённый",
            "Тараск", "Кситерос", "Зекат", "Зекалит", "Пазис", "Нагарджас"
        )
    }

    fun onQuickBattleSelected() {
        if (battleViewModel == null) {
            battleViewModel = BattleViewModel(scope)
        }
        _state.update { it.copy(screen = AppScreen.QuickBattle) }
        scope.launch { loadBosses() }
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
            val names = current.hunterPlayerNames.toMutableMap()
            if (classes.contains(cls)) {
                classes.remove(cls)
                names.remove(cls)
            } else {
                classes.add(cls)
            }
            current.copy(selectedClasses = classes, hunterPlayerNames = names)
        }
    }

    fun onHunterPlayerNameChanged(cls: HunterClass, name: String) {
        _state.update { it.copy(hunterPlayerNames = it.hunterPlayerNames + (cls to name)) }
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
            val names = _state.value.hunterPlayerNames
            val hunters = classes.map { cls ->
                val playerName = names[cls]?.takeIf { it.isNotBlank() } ?: cls.displayName
                CampaignHunter(campaignId = campaignId, playerName = playerName, className = cls)
            }
            repository.addHunters(campaignId, hunters)
            val persistedHunters = repository.getHunters(campaignId).ifEmpty { hunters }
            val campaign = repository.getCampaign(campaignId)
            _state.update { it.copy(currentCampaign = campaign, hunters = persistedHunters, isPrologue = true) }
            startBattleInternal(campaignId, persistedHunters)
        }
    }

    private fun startBattleInternal(campaignId: Long, hunters: List<CampaignHunter>) {
        val battleHunters = hunters.map { Hunter(name = "${it.playerName} (${it.className.displayName})") }
        battleViewModel = BattleViewModel(scope)
        val difficulty = getDifficultyForChapter(_state.value.currentCampaign?.currentChapter ?: 1)
        val isPrologue = _state.value.isPrologue
        _state.update {
            it.copy(
                preBattleHunters = battleHunters,
                screen = AppScreen.CampaignBattle(campaignId),
                preBattleDifficulty = if (isPrologue) 0 else difficulty,
                selectedPreBattleBossName = if (isPrologue) "Вираксен" else it.selectedPreBattleBossName
            )
        }
        scope.launch {
            loadBosses()
            if (_state.value.isPrologue) {
                resolvePreBattleBoss()
            }
        }
        observeBattleForAutoSave(campaignId)
    }

    fun onConfirmCampaignBattleStart(damageForWound: Int?, healthForStanceChange: Int?) {
        val hunters = _state.value.preBattleHunters
        if (hunters.isEmpty()) return
        val boss = _state.value.selectedPreBattleBoss
        val difficulty = _state.value.preBattleDifficulty
        battleViewModel?.startBattleWithHunters(
            hunters = hunters,
            damageForWound = damageForWound,
            healthForStanceChange = healthForStanceChange,
            boss = boss,
            difficulty = difficulty
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

    fun onResourceIncrement(type: ResourceType, name: String) {
        val hunter = _state.value.hunters.getOrNull(_state.value.selectedHunterIndex) ?: return
        scope.launch {
            repository.addResource(hunter.id, type, name, 1)
            loadHunterResources(hunter.id)
        }
    }

    fun onResourceDecrement(type: ResourceType, name: String) {
        val hunter = _state.value.hunters.getOrNull(_state.value.selectedHunterIndex) ?: return
        val current = when (type) {
            ResourceType.MATERIAL -> _state.value.materials[Material.valueOf(name)] ?: 0
            ResourceType.PLANT -> _state.value.plants[Plant.valueOf(name)] ?: 0
            ResourceType.ELEMENT -> _state.value.elements[Element.valueOf(name)] ?: 0
        }
        if (current <= 0) return
        scope.launch {
            repository.updateResource(hunter.id, type, name, current - 1)
            loadHunterResources(hunter.id)
        }
    }

    fun onStartCampaignBattle() {
        val campaignId = _state.value.currentCampaign?.id ?: return
        val hunters = _state.value.hunters
        if (hunters.isEmpty()) return
        scope.launch {
            val quests = repository.getAvailableQuests(campaignId).sortedBy { it.questNumber }
            val taskInfo = repository.getAllTaskInfo().associateBy { it.questNumber }
            _state.update {
                it.copy(
                    showQuestSelectDialog = true,
                    availableQuestsForNext = quests,
                    taskInfoByQuestNumber = taskInfo
                )
            }
        }
    }

    fun onActiveQuestSelected(questId: String?) {
        val campaignId = _state.value.currentCampaign?.id ?: return
        val hunters = _state.value.hunters
        if (hunters.isEmpty()) return
        battleCollectJob?.cancel()
        _state.update { it.copy(activeQuestId = questId, showQuestSelectDialog = false) }
            // предзаполняем босса из каталога заданий
            val questNumber = questId?.toIntOrNull()
            val taskInfo = questNumber?.let { _state.value.taskInfoByQuestNumber[it] }
            if (taskInfo != null) {
                _state.update {
                    it.copy(
                        selectedPreBattleBossName = taskInfo.bossName,
                        bossElement = taskInfo.bossElement,
                        bossHasNoElement = taskInfo.bossElement == null
                    )
                }
            }
        startBattleInternal(campaignId, hunters)
    }

    fun onCloseQuestSelectDialog() {
        _state.update { it.copy(showQuestSelectDialog = false) }
    }

    fun getBattleViewModel(): BattleViewModel? = battleViewModel

    fun onBattleFinished() {
        battleCollectJob?.cancel()
        val campaignId = _state.value.currentCampaign?.id ?: return
        val state = _state.value
        val questNumbers = state.selectedDefeatQuestNumbers
        scope.launch {
            try {
                saveCampaign()
                questNumbers.forEach { number ->
                    val quest = Quest(
                        id = number.toString(),
                        name = "Задание $number",
                        chapter = state.currentCampaign?.currentChapter ?: 1,
                        questNumber = number,
                        isAvailable = true
                    )
                    repository.saveQuest(campaignId, quest)
                }
                _state.update { it.copy(screen = AppScreen.CampaignSheet(campaignId), selectedDefeatQuestNumbers = emptySet()) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка сохранения: ${e.message}") }
            }
        }
    }

    fun onDefeatQuestToggled(number: Int) {
        _state.update { state ->
            val current = state.selectedDefeatQuestNumbers.toMutableSet()
            if (current.contains(number)) {
                current.remove(number)
            } else {
                current.add(number)
            }
            state.copy(selectedDefeatQuestNumbers = current)
        }
    }

    fun onVictory() {
        val campaignId = _state.value.currentCampaign?.id ?: return
        scope.launch {
            val trophies = repository.getTrophies(campaignId)
            val isPrologue = _state.value.isPrologue
            val selectedBoss = _state.value.selectedPreBattleBoss
            val taskInfo = _state.value.activeQuestId?.toIntOrNull()
                ?.let { repository.getTaskInfo(it) }
            val bossNames = if (isPrologue) {
                listOf("Вираксен")
            } else {
                ALL_BOSS_NAMES
            }
            val allTaskInfo = repository.getAllTaskInfo().associateBy { it.questNumber }
            _state.update {
                it.copy(
                    showQuestRewards = !isPrologue,
                    questRewardsMode = if (isPrologue) null else QuestRewardsMode.VICTORY,
                    activeQuestNumber = if (isPrologue) null else it.activeQuestId?.toIntOrNull(),
                    taskInfoByQuestNumber = allTaskInfo,
                    showPostVictory = isPrologue,
                    bossName = selectedBoss?.name ?: if (isPrologue) "Вираксен" else "",
                    bossElement = if (selectedBoss != null) selectedBoss.element else if (isPrologue) Element.FIRE else null,
                    bossHasNoElement = selectedBoss != null && selectedBoss.element == null,
                    defeatedBosses = bossNames,
                    completedQuestId = "",
                    availableQuestsForNext = emptyList(),
                    selectedQuestNumbers = emptySet(),
                    victoryMaterials = emptyMap(),
                    victoryPlants = emptyMap(),
                    error = null
                )
            }
        }
    }

    fun onDefeat() {
        val campaignId = _state.value.currentCampaign?.id ?: return
        scope.launch {
            val allTaskInfo = repository.getAllTaskInfo().associateBy { it.questNumber }
            _state.update {
                it.copy(
                    showQuestRewards = true,
                    questRewardsMode = QuestRewardsMode.DEFEAT,
                    activeQuestNumber = it.activeQuestId?.toIntOrNull(),
                    taskInfoByQuestNumber = allTaskInfo,
                    selectedDefeatQuestNumbers = emptySet(),
                    editDefeatMode = false,
                    error = null
                )
            }
        }
    }

    fun onQuestRewardsEdit() {
        val mode = _state.value.questRewardsMode ?: return
        if (mode == QuestRewardsMode.DEFEAT) {
            _state.update { it.copy(editDefeatMode = true, showQuestRewards = false) }
            return
        }
        val state = _state.value
        val campaignId = state.currentCampaign?.id ?: return
        val taskInfo = state.activeQuestNumber?.let { state.taskInfoByQuestNumber[it] }
        scope.launch {
            val openQuestNumbers = repository.getQuests(campaignId)
                .filter { it.isAvailable }
                .map { it.questNumber }
                .toSet()
            val mergedQuestNumbers = openQuestNumbers + (taskInfo?.victoryOpenQuests?.toSet() ?: emptySet())
            _state.update {
                it.copy(
                    showQuestRewards = false,
                    showPostVictory = true,
                    bossName = taskInfo?.bossName ?: it.bossName,
                    bossElement = taskInfo?.bossElement ?: it.bossElement,
                    bossHasNoElement = taskInfo?.bossElement == null,
                    victoryMaterials = taskInfo?.victoryMaterials ?: it.victoryMaterials,
                    victoryPlants = taskInfo?.victoryPlants ?: it.victoryPlants,
                    selectedQuestNumbers = mergedQuestNumbers,
                    error = null
                )
            }
        }
    }

    fun onQuestRewardsDismiss() {
        _state.update {
            it.copy(
                showQuestRewards = false,
                questRewardsMode = null,
                activeQuestNumber = null,
                editDefeatMode = false
            )
        }
    }

    fun onDefeatRewardsAccept() {
        val campaignId = _state.value.currentCampaign?.id ?: return
        val state = _state.value
        val taskInfo = state.activeQuestNumber?.let { state.taskInfoByQuestNumber[it] }
        val questNumbers = taskInfo?.defeatOpenQuests ?: emptyList()
        scope.launch {
            try {
                saveCampaign()
                val achievements = repository.getAchievements(campaignId).map { it.name }.toSet()
                val currentChapter = state.currentCampaign?.currentChapter ?: 1
                questNumbers.forEach { number ->
                    val quest = Quest(
                        id = number.toString(),
                        name = "Задание $number",
                        chapter = currentChapter,
                        questNumber = number,
                        isAvailable = true
                    )
                    repository.saveQuest(campaignId, quest)
                }
                (taskInfo?.defeatOpenQuestConditions ?: emptyList()).let { conditions ->
                    if (conditions.isNotEmpty()) {
                        applyQuestOpenConditions(campaignId, conditions, achievements, currentChapter)
                    }
                }
                _state.update {
                    it.copy(
                        screen = AppScreen.CampaignSheet(campaignId),
                        selectedDefeatQuestNumbers = emptySet(),
                        showQuestRewards = false,
                        questRewardsMode = null,
                        activeQuestNumber = null,
                        editDefeatMode = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка сохранения: ${e.message}") }
            }
        }
    }

    fun onQuestRewardsAccept() {
        if (_state.value.isSaving) return
        val state = _state.value
        val campaignId = state.currentCampaign?.id ?: return
        val taskInfo = state.activeQuestNumber?.let { state.taskInfoByQuestNumber[it] }
        val element = taskInfo?.bossElement
        if (element == null && taskInfo != null) {
            _state.update { it.copy(error = "У задания не указана стихия босса") }
            return
        }
        val bossName = taskInfo?.bossName?.ifBlank { null } ?: run {
            _state.update { it.copy(error = "У задания не указан босс") }
            return
        }
        val questNumbers = taskInfo?.victoryOpenQuests.orEmpty().toSet()
        _state.update { it.copy(isSaving = true) }
        scope.launch {
            try {
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
                val achievements = repository.getAchievements(campaignId).map { it.name }.toSet()
                val currentChapter = state.currentCampaign?.currentChapter ?: 1
                (taskInfo?.victoryOpenQuestConditions ?: emptyList()).let { conditions ->
                    if (conditions.isNotEmpty()) {
                        applyQuestOpenConditions(campaignId, conditions, achievements, currentChapter, element)
                    }
                }
                val completedQuestId = state.activeQuestId ?: state.activeQuestNumber?.toString() ?: ""
                repository.saveVictory(
                    campaignId = campaignId,
                    trophy = trophy,
                    completedQuestId = completedQuestId,
                    nextQuestId = null
                )
                val hunters = state.hunters
                if (element != null) {
                    hunters.forEach { hunter ->
                        addResourceToAll(hunter.id, "ELEMENT", element.name, 2)
                    }
                }
                (taskInfo?.victoryMaterials ?: emptyMap()).forEach { (material, qty) ->
                    if (qty > 0) {
                        hunters.forEach { hunter ->
                            addResourceToAll(hunter.id, "MATERIAL", material.name, qty)
                        }
                    }
                }
                (taskInfo?.victoryPlants ?: emptyMap()).forEach { (plant, qty) ->
                    if (qty > 0) {
                        hunters.forEach { hunter ->
                            addResourceToAll(hunter.id, "PLANT", plant.name, qty)
                        }
                    }
                }
                (taskInfo?.victoryAchievements ?: emptyList()).forEach { achievementName ->
                    repository.saveAchievement(
                        campaignId = campaignId,
                        achievement = Achievement(id = achievementName, name = achievementName, unlocked = true)
                    )
                }
                _state.update {
                    it.copy(
                        showQuestRewards = false,
                        questRewardsMode = null,
                        activeQuestNumber = null,
                        editDefeatMode = false,
                        error = null,
                        selectedQuestNumbers = emptySet(),
                        victoryMaterials = emptyMap(),
                        victoryPlants = emptyMap(),
                        isPrologue = false,
                        activeQuestId = null
                    )
                }
                openChapterRewards(campaignId)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка сохранения победы: ${e.message}") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onVictoryBossNameChanged(name: String) {
        _state.update { it.copy(bossName = name) }
    }

    fun onVictoryBossSelected(name: String) {
        _state.update { it.copy(bossName = name) }
    }

    fun onPreBattleBossSelected(name: String?) {
        if (name != null) {
            val difficulties = _state.value.availableBosses
                .filter { it.name == name }
                .map { it.difficulty }
                .distinct()
            val forcedDifficulty = if (difficulties.size == 1) difficulties.first() else null
            _state.update {
                it.copy(
                    selectedPreBattleBossName = name,
                    preBattleDifficulty = forcedDifficulty ?: it.preBattleDifficulty
                )
            }
        } else {
            _state.update {
                it.copy(
                    selectedPreBattleBossName = null,
                    selectedPreBattleBoss = null,
                    preBattleDamageForWound = "4",
                    preBattleHealthForStance = "7"
                )
            }
            return
        }
        resolvePreBattleBoss()
    }

    private fun resolvePreBattleBoss() {
        val state = _state.value
        val name = state.selectedPreBattleBossName ?: return
        val difficulty = state.preBattleDifficulty
        val boss = state.availableBosses.find { it.name == name && it.difficulty == difficulty }
        if (boss != null) {
            val stance = boss.stances.firstOrNull()
            _state.update {
                it.copy(
                    selectedPreBattleBoss = boss,
                    preBattleDamageForWound = stance?.damageForWound?.toString() ?: "4",
                    preBattleHealthForStance = stance?.healthForStanceChange?.toString() ?: ""
                )
            }
        }
    }

    fun onPreBattleDifficultySelected(difficulty: Int) {
        _state.update { it.copy(preBattleDifficulty = difficulty) }
        resolvePreBattleBoss()
    }

    fun onPreBattleDfwChanged(value: String) {
        _state.update { it.copy(preBattleDamageForWound = value) }
    }

    fun onPreBattleHscChanged(value: String) {
        _state.update { it.copy(preBattleHealthForStance = value) }
    }

    fun onPreBattleHunterCountChanged(count: Int) {
        _state.update { it.copy(preBattleHunterCount = count.coerceIn(1, 6)) }
    }

    private suspend fun loadBosses() {
        val bosses = repository.getAllBosses()
        val sorted = bosses.sortedWith(
            compareBy<Boss> { it.element == null }
                .thenBy { it.element?.displayName ?: "" }
                .thenBy { it.name }
        )
        _state.update { it.copy(availableBosses = sorted) }
    }

    fun getDifficultyForChapter(chapter: Int): Int = when {
        chapter <= 1 -> 0
        chapter in 2..4 -> 1
        chapter in 5..8 -> 2
        else -> 3
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

    fun onVictoryResourceChanged(type: ResourceType, name: String, delta: Int) {
        _state.update { state ->
            when (type) {
                ResourceType.MATERIAL -> {
                    val material = Material.valueOf(name)
                    val current = state.victoryMaterials[material] ?: 0
                    state.copy(victoryMaterials = state.victoryMaterials + (material to (current + delta).coerceAtLeast(0)))
                }
                ResourceType.PLANT -> {
                    val plant = Plant.valueOf(name)
                    val current = state.victoryPlants[plant] ?: 0
                    state.copy(victoryPlants = state.victoryPlants + (plant to (current + delta).coerceAtLeast(0)))
                }
                ResourceType.ELEMENT -> state
            }
        }
    }

    fun onConfirmVictory() {
        if (_state.value.isSaving) return
        val state = _state.value
        val campaignId = state.currentCampaign?.id ?: return
        val element = state.bossElement
        if (element == null && !state.bossHasNoElement) {
            _state.update { it.copy(error = "Выберите стихию босса") }
            return
        }
        val bossName = state.bossName.ifBlank {
            _state.update { it.copy(error = "Введите имя босса") }
            return
        }
        val questNumbers = state.selectedQuestNumbers
        _state.update { it.copy(isSaving = true) }
        scope.launch {
            try {
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
                val completedQuestId = state.activeQuestId ?: questNumbers.first().toString()
                repository.saveVictory(
                    campaignId = campaignId,
                    trophy = trophy,
                    completedQuestId = completedQuestId,
                    nextQuestId = null
                )
                val hunters = state.hunters
                if (element != null) {
                    hunters.forEach { hunter ->
                        addResourceToAll(hunter.id, "ELEMENT", element.name, 2)
                    }
                }
                state.victoryMaterials.forEach { (material, qty) ->
                    if (qty > 0) {
                        hunters.forEach { hunter ->
                            addResourceToAll(hunter.id, "MATERIAL", material.name, qty)
                        }
                    }
                }
                state.victoryPlants.forEach { (plant, qty) ->
                    if (qty > 0) {
                        hunters.forEach { hunter ->
                            addResourceToAll(hunter.id, "PLANT", plant.name, qty)
                        }
                    }
                }
                _state.update { it.copy(showPostVictory = false, error = null, selectedQuestNumbers = emptySet(), victoryMaterials = emptyMap(), victoryPlants = emptyMap(), isPrologue = false, activeQuestId = null) }
                openChapterRewards(campaignId)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка сохранения победы: ${e.message}") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun openChapterRewards(campaignId: Long) {
        val currentChapter = _state.value.currentCampaign?.currentChapter ?: return
        val chapterInfo = repository.getChapterInfo(currentChapter)
        val allChapterInfo = repository.getAllChapterInfo().associateBy { it.chapter }
        val message = buildChapterRewardsMessage(chapterInfo, campaignId)
        _state.update {
            it.copy(
                showChapterRewards = true,
                chapterInfoByNumber = allChapterInfo,
                chapterRewardsMessage = message
            )
        }
    }

    private suspend fun buildChapterRewardsMessage(chapterInfo: ChapterInfo?, campaignId: Long): String {
        if (chapterInfo == null) return ""
        val achievements = repository.getAchievements(campaignId).map { it.name }.toSet()
        val parts = mutableListOf<String>()
        if (chapterInfo.hunterKitUpgrade) parts.add("Улучшение набора охотника")
        if (chapterInfo.forgeUpgrade) parts.add("Повышение уровня кузни")
        if (chapterInfo.labUpgrade) parts.add("Повышение уровня лаборатории")
        chapterInfo.messages.forEach { parts.add(it) }
        chapterInfo.conditionalMessages.forEach { cm ->
            if (cm.achievementName in achievements) parts.add(cm.message)
        }
        return parts.joinToString("\n")
    }

    fun onChapterRewardsAccept() {
        if (_state.value.isSaving) return
        val state = _state.value
        val campaignId = state.currentCampaign?.id ?: return
        val currentChapter = state.currentCampaign?.currentChapter ?: return
        val chapterInfo = state.chapterInfoByNumber[currentChapter]
        _state.update { it.copy(isSaving = true) }
        scope.launch {
            try {
                val campaign = repository.getCampaign(campaignId) ?: return@launch
                val hunters = state.hunters
                val achievements = repository.getAchievements(campaignId).map { it.name }.toSet()
                if (chapterInfo != null) {
                    (chapterInfo.rewards).forEach { (material, qty) ->
                        if (qty > 0) {
                            hunters.forEach { hunter ->
                                addResourceToAll(hunter.id, "MATERIAL", material.name, qty)
                            }
                        }
                    }
                    (chapterInfo.rewardPlants).forEach { (plant, qty) ->
                        if (qty > 0) {
                            hunters.forEach { hunter ->
                                addResourceToAll(hunter.id, "PLANT", plant.name, qty)
                            }
                        }
                    }
                    chapterInfo.openQuests.forEach { number ->
                        repository.saveQuest(
                            campaignId = campaignId,
                            quest = Quest(
                                id = number.toString(),
                                name = "Задание $number",
                                chapter = currentChapter,
                                questNumber = number,
                                isAvailable = true
                            )
                        )
                    }
                    chapterInfo.conditionalOpenQuests.forEach { condition ->
                        val matched = if (condition.negated) {
                            !matchesAchievements(condition.achievements, achievements, condition.requireAll)
                        } else {
                            matchesAchievements(condition.achievements, achievements, condition.requireAll)
                        }
                        val target = if (matched) condition.questNumber else condition.elseQuestNumber
                        if (target != null) {
                            repository.saveQuest(
                                campaignId = campaignId,
                                quest = Quest(
                                    id = target.toString(),
                                    name = "Задание $target",
                                    chapter = currentChapter,
                                    questNumber = target,
                                    isAvailable = true
                                )
                            )
                        }
                    }
                    chapterInfo.expireQuests.forEach { number ->
                        repository.setQuestUnavailable(campaignId, number.toString())
                    }
                    val forgeLevel = repository.getForgeLevel(campaignId)
                    val labLevel = repository.getLabLevel(campaignId)
                    if (chapterInfo.forgeUpgrade) repository.updateForgeLevel(campaignId, forgeLevel + 1)
                    if (chapterInfo.labUpgrade) repository.updateLabLevel(campaignId, labLevel + 1)
                }
                repository.updateChapter(campaignId, currentChapter + 1)
                _state.update { it.copy(showChapterRewards = false, chapterRewardsMessage = "", error = null) }
                loadCampaignSheet(campaignId)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка применения наград главы: ${e.message}") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun matchesAchievements(
        required: List<String>,
        owned: Set<String>,
        requireAll: Boolean
    ): Boolean {
        if (required.isEmpty()) return true
        return if (requireAll) {
            required.all { it in owned }
        } else {
            required.any { it in owned }
        }
    }

    private fun resolveConditionTarget(
        condition: TaskCondition,
        achievements: Set<String>,
        currentChapter: Int
    ): Int? {
        val matched = when (condition.kind) {
            TaskConditionKind.CHAPTER_IN -> currentChapter in condition.chapterSet
            TaskConditionKind.ACHIEVEMENT_OWNED -> condition.achievementName != null && condition.achievementName in achievements
            TaskConditionKind.ACHIEVEMENT_NOT_OWNED -> condition.achievementName != null && condition.achievementName !in achievements
            TaskConditionKind.ACHIEVEMENT_OWNED_IN_CHAPTER ->
                condition.achievementName != null &&
                    condition.achievementName in achievements &&
                    currentChapter in condition.chapterSet
        }
        return if (matched) condition.questNumber else condition.elseQuestNumber
    }

    /**
     * Применяет условные правила задания (зад. 34.3/34.4):
     * - условия с [TaskCondition.rewardAchievement] выдают достижение при его выполнении;
     * - условия открытия заданий вычисляются по очереди; открывается ПЕРВОЕ условие, давшее целевое
     *   задание (для составного условия зад. 25: задание 34 вместо 27 при главе 8).
     */
    private suspend fun applyQuestOpenConditions(
        campaignId: Long,
        conditions: List<TaskCondition>,
        achievements: Set<String>,
        currentChapter: Int,
        element: Element? = null
    ) {
        var questOpened = false
        for (condition in conditions) {
            val reward = condition.rewardAchievement
            if (reward != null) {
                val hasRewardSource = condition.achievementName != null && condition.achievementName in achievements
                if (hasRewardSource) {
                    repository.saveAchievement(
                        campaignId = campaignId,
                        achievement = Achievement(id = reward, name = reward, unlocked = true)
                    )
                }
                continue
            }
            if (questOpened) continue
            val target = resolveConditionTarget(condition, achievements, currentChapter)
            if (target != null) {
                repository.saveQuest(
                    campaignId = campaignId,
                    quest = Quest(
                        id = target.toString(),
                        name = "Задание $target",
                        chapter = currentChapter,
                        element = element,
                        questNumber = target,
                        isAvailable = true
                    )
                )
                questOpened = true
            }
        }
    }

    fun onChapterRewardsReject() {
        val campaignId = _state.value.currentCampaign?.id ?: return
        _state.update { it.copy(showChapterRewards = false, chapterRewardsMessage = "", error = null) }
        scope.launch { loadCampaignSheet(campaignId) }
    }

    fun onChapterDecisionSelected(option: String, achievementName: String?) {
        val campaignId = _state.value.currentCampaign?.id ?: return
        if (achievementName != null && option.isNotEmpty()) {
            scope.launch {
                repository.saveAchievement(
                    campaignId = campaignId,
                    achievement = Achievement(id = achievementName, name = achievementName, unlocked = true)
                )
            }
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
        val quests = repository.getQuests(campaignId)
        val openQuests = quests.filter { it.isAvailable }.sortedBy { it.questNumber }
        val taskInfo = repository.getAllTaskInfo().associateBy { it.questNumber }
        val trophies = repository.getTrophies(campaignId)
        val achievements = repository.getAchievements(campaignId)
        _state.update {
            it.copy(
                screen = AppScreen.CampaignSheet(campaignId),
                currentCampaign = campaign,
                hunters = hunters,
                selectedHunterIndex = if (hunters.isNotEmpty()) 0 else it.selectedHunterIndex,
                notes = campaign.notes,
                campaignQuests = openQuests,
                taskInfoByQuestNumber = taskInfo,
                campaignTrophies = trophies,
                campaignAchievements = achievements
            )
        }
        if (hunters.isNotEmpty()) {
            loadHunterResources(hunters[0].id)
            loadHunterSkills(hunters[0].id)
        }
    }

    fun onToggleQuestCompleted(questId: String) {
        val campaignId = _state.value.currentCampaign?.id ?: return
        val quest = _state.value.campaignQuests.find { it.id == questId } ?: return
        scope.launch {
            try {
                if (quest.isCompleted) {
                    repository.uncompleteQuest(campaignId, questId)
                } else {
                    repository.completeQuest(campaignId, questId)
                    if (quest.questNumber > 0) {
                        openDependentQuests(campaignId, quest.questNumber)
                    }
                }
                loadCampaignSheet(campaignId)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка обновления задания: ${e.message}") }
            }
        }
    }

    /**
     * Открывает задания, зависимые от завершённого задания (зад. 34.4):
     * безусловный список [TaskInfo.victoryOpenQuests] + условные правила
     * [TaskInfo.victoryOpenQuestConditions] (глава/достижения). Материи/растения/достижения
     * при ручном «Выполнено» не начисляются (решение пользователя, qa 70).
     */
    private suspend fun openDependentQuests(campaignId: Long, questNumber: Int) {
        val taskInfo = _state.value.taskInfoByQuestNumber[questNumber] ?: return
        val currentChapter = _state.value.currentCampaign?.currentChapter ?: 1
        val achievements = repository.getAchievements(campaignId).map { it.name }.toSet()
        val element = taskInfo.bossElement
        taskInfo.victoryOpenQuests.forEach { number ->
            repository.saveQuest(
                campaignId = campaignId,
                quest = Quest(
                    id = number.toString(),
                    name = "Задание $number",
                    chapter = currentChapter,
                    element = element,
                    questNumber = number,
                    isAvailable = true
                )
            )
        }
        if (taskInfo.victoryOpenQuestConditions.isNotEmpty()) {
            applyQuestOpenConditions(
                campaignId = campaignId,
                conditions = taskInfo.victoryOpenQuestConditions,
                achievements = achievements,
                currentChapter = currentChapter,
                element = element
            )
        }
    }

    fun onOpenQuestEditor() {
        val questNumbers = _state.value.campaignQuests.map { it.questNumber }.toSet()
        _state.update { it.copy(showQuestEditDialog = true, editedQuestNumbers = questNumbers) }
    }

    fun onToggleEditedQuest(questNumber: Int) {
        val current = _state.value.editedQuestNumbers
        val updated = if (questNumber in current) current - questNumber else current + questNumber
        _state.update { it.copy(editedQuestNumbers = updated) }
    }

    fun onSaveQuestEdits() {
        val campaignId = _state.value.currentCampaign?.id ?: return
        val edited = _state.value.editedQuestNumbers
        scope.launch {
            try {
                val currentOpen = _state.value.campaignQuests.map { it.questNumber }.toSet()
                val toOpen = edited - currentOpen
                val toClose = currentOpen - edited
                val chapter = _state.value.currentCampaign?.currentChapter ?: 1
                toOpen.forEach { number ->
                    repository.saveQuest(
                        campaignId = campaignId,
                        quest = Quest(
                            id = number.toString(),
                            name = "Задание $number",
                            chapter = chapter,
                            questNumber = number,
                            isAvailable = true
                        )
                    )
                }
                toClose.forEach { number ->
                    repository.saveQuest(
                        campaignId = campaignId,
                        quest = Quest(
                            id = number.toString(),
                            name = "Задание $number",
                            chapter = chapter,
                            questNumber = number,
                            isAvailable = false
                        )
                    )
                }
                _state.update { it.copy(showQuestEditDialog = false) }
                loadCampaignSheet(campaignId)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка сохранения заданий: ${e.message}") }
            }
        }
    }

    fun onCancelQuestEdits() {
        _state.update { it.copy(showQuestEditDialog = false, editedQuestNumbers = emptySet()) }
    }

    fun onOpenAchievementEditor() {
        _state.update { it.copy(showAchievementEditor = true, newAchievementName = "") }
    }

    fun onCloseAchievementEditor() {
        _state.update { it.copy(showAchievementEditor = false, newAchievementName = "", error = null) }
    }

    fun onNewAchievementNameChanged(name: String) {
        _state.update { it.copy(newAchievementName = name) }
    }

    fun onAddAchievement() {
        val campaignId = _state.value.currentCampaign?.id ?: return
        val name = _state.value.newAchievementName.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = "Введите название достижения") }
            return
        }
        scope.launch {
            try {
                repository.deleteAchievement(campaignId, name)
                repository.saveAchievement(
                    campaignId = campaignId,
                    achievement = Achievement(id = name, name = name, unlocked = true)
                )
                reloadAchievements(campaignId)
                _state.update { it.copy(newAchievementName = "", error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка добавления достижения: ${e.message}") }
            }
        }
    }

    fun onDeleteAchievement(achievementId: String) {
        val campaignId = _state.value.currentCampaign?.id ?: return
        scope.launch {
            try {
                repository.deleteAchievement(campaignId, achievementId)
                reloadAchievements(campaignId)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка удаления достижения: ${e.message}") }
            }
        }
    }

    private suspend fun reloadAchievements(campaignId: Long) {
        val achievements = repository.getAchievements(campaignId)
        _state.update { it.copy(campaignAchievements = achievements) }
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
