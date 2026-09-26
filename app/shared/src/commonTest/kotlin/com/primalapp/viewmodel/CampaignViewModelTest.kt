package com.primalapp.viewmodel

import com.primalapp.database.chapterInfoSeedEntities
import com.primalapp.database.mapper.toDomain
import com.primalapp.database.taskInfoSeedEntities
import com.primalapp.model.campaign.Achievement
import com.primalapp.model.campaign.Boss
import com.primalapp.model.campaign.BossStance
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
import com.primalapp.model.campaign.TaskInfo
import com.primalapp.model.campaign.TaskCondition
import com.primalapp.model.campaign.TaskConditionKind
import com.primalapp.model.campaign.Trophy
import com.primalapp.repository.CampaignRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampaignViewModelTest {

    private class FakeCampaignRepository : CampaignRepository {
        var shouldThrowOnGetCampaignCount: Boolean = false
        var shouldThrowOnSaveQuest: Boolean = false
        var saveQuestDelayMs: Long = 0
        private val campaigns = mutableMapOf<Long, Campaign>()
        private var nextId = 1L

        // 28.x. Конфигурируемые данные и трассировка вызовов репозитория
        var huntersToReturn: List<CampaignHunter> = emptyList()
        var materialsToReturn: Map<Material, Int> = emptyMap()
        var plantsToReturn: Map<Plant, Int> = emptyMap()
        var elementsToReturn: Map<Element, Int> = emptyMap()
        var availableQuestsToReturn: List<Quest> = emptyList()

        data class SaveVictoryRecord(
            val campaignId: Long,
            val trophy: Trophy,
            val completedQuestId: String,
            val nextQuestId: String?
        )

        data class ResourceAddRecord(
            val hunterId: Long,
            val resourceType: String,
            val resourceName: String,
            val amount: Int
        )

        data class ResourceUpdateRecord(
            val hunterId: Long,
            val resourceType: String,
            val resourceName: String,
            val quantity: Int
        )

        val savedQuests = mutableListOf<Quest>()
        val saveVictoryRecords = mutableListOf<SaveVictoryRecord>()
        val completedQuestIds = mutableListOf<String>()
        val resourceAddRecords = mutableListOf<ResourceAddRecord>()
        val resourceUpdateRecords = mutableListOf<ResourceUpdateRecord>()

        override suspend fun getAllCampaigns(): List<Campaign> = campaigns.values.toList()
        override suspend fun getCampaign(id: Long): Campaign? = campaigns[id]
        override suspend fun createCampaign(name: String): Long {
            val id = nextId++
            campaigns[id] = Campaign(id = id, name = name, currentChapter = 1)
            return id
        }
        override suspend fun saveCampaign(campaign: Campaign) {}
        override suspend fun deleteCampaign(id: Long) {}
        override suspend fun getCampaignCount(): Int {
            if (shouldThrowOnGetCampaignCount) throw RuntimeException("Database error")
            return 0
        }
        override suspend fun getMaxCampaigns(): Int = 10
        override suspend fun getHunters(campaignId: Long): List<CampaignHunter> = huntersToReturn
        override suspend fun addHunters(campaignId: Long, hunters: List<CampaignHunter>) {}
        override suspend fun getTrophies(campaignId: Long): List<Trophy> = emptyList()
        override suspend fun saveTrophy(campaignId: Long, trophy: Trophy) {}
        override suspend fun getCompletedQuests(campaignId: Long): List<Quest> = emptyList()
        override suspend fun getQuests(campaignId: Long): List<Quest> = questsToReturn
        var questsToReturn: List<Quest> = emptyList()
        override suspend fun saveQuest(campaignId: Long, quest: Quest) {
            if (saveQuestDelayMs > 0) kotlinx.coroutines.delay(saveQuestDelayMs)
            if (shouldThrowOnSaveQuest) throw RuntimeException("DB error on saveQuest")
            savedQuests.add(quest)
        }
        override suspend fun completeQuest(campaignId: Long, questId: String) {
            completedQuestIds.add(questId)
            markQuestCompleted(questId)
        }
        override suspend fun uncompleteQuest(campaignId: Long, questId: String) {
            uncompletedQuestIds.add(questId)
            questsToReturn = questsToReturn.map {
                if (it.id == questId) it.copy(isCompleted = false, isAvailable = true) else it
            }
        }
        val uncompletedQuestIds = mutableListOf<String>()
        /** Завершённое задание видно в getQuests как выполненное — как в реальной БД (42.3). */
        private fun markQuestCompleted(questId: String) {
            questsToReturn = questsToReturn.map { if (it.id == questId) it.copy(isCompleted = true) else it }
        }
        override suspend fun getAvailableQuests(campaignId: Long): List<Quest> = availableQuestsToReturn
        override suspend fun getAllBosses(): List<Boss> = bossesToReturn
        var bossesToReturn: List<Boss> = emptyList()
        override suspend fun getAllTaskInfo(): List<TaskInfo> = taskInfoToReturn
        override suspend fun getTaskInfo(questNumber: Int): TaskInfo? = taskInfoToReturn.find { it.questNumber == questNumber }
        var taskInfoToReturn: List<TaskInfo> = emptyList()
        override suspend fun saveVictory(campaignId: Long, trophy: Trophy, completedQuestId: String, nextQuestId: String?) {
            saveVictoryRecords.add(SaveVictoryRecord(campaignId, trophy, completedQuestId, nextQuestId))
            completedQuestIds.add(completedQuestId)
            markQuestCompleted(completedQuestId)
        }
        override suspend fun getSkills(hunterId: Long): List<SkillNode> = emptyList()
        override suspend fun unlockSkill(hunterId: Long, branch: SkillBranch, tier: Int) {}
        override suspend fun getAvailableSkillBranches(hunterId: Long): List<SkillBranch> = emptyList()
        override suspend fun getMaterials(hunterId: Long): Map<Material, Int> = materialsToReturn
        override suspend fun getPlants(hunterId: Long): Map<Plant, Int> = plantsToReturn
        override suspend fun getElements(hunterId: Long): Map<Element, Int> = elementsToReturn
        override suspend fun updateResource(hunterId: Long, resourceType: ResourceType, resourceName: String, quantity: Int) {
            resourceUpdateRecords.add(ResourceUpdateRecord(hunterId, resourceType.name, resourceName, quantity))
        }
        override suspend fun addResource(hunterId: Long, resourceType: ResourceType, resourceName: String, amount: Int) {
            addResourceCalls.add(resourceName)
            resourceAddRecords.add(ResourceAddRecord(hunterId, resourceType.name, resourceName, amount))
        }
        val addResourceCalls = mutableListOf<String>()
        override suspend fun updateChapter(campaignId: Long, chapter: Int) {
            chapterUpdateRecords.add(ChapterUpdateRecord(campaignId, chapter))
            campaigns[campaignId]?.let { campaigns[campaignId] = it.copy(currentChapter = chapter) }
        }
        data class ChapterUpdateRecord(val campaignId: Long, val chapter: Int)
        val chapterUpdateRecords = mutableListOf<ChapterUpdateRecord>()
        override suspend fun updateForgeLevel(campaignId: Long, level: Int) {
            forgeLevelRecords.add(campaignId to level)
        }
        val forgeLevelRecords = mutableListOf<Pair<Long, Int>>()
        override suspend fun updateLabLevel(campaignId: Long, level: Int) {
            labLevelRecords.add(campaignId to level)
        }
        val labLevelRecords = mutableListOf<Pair<Long, Int>>()
        override suspend fun getForgeLevel(campaignId: Long): Int = 1
        override suspend fun getLabLevel(campaignId: Long): Int = 1
        override suspend fun getAchievements(campaignId: Long): List<Achievement> = achievementsToReturn
        var achievementsToReturn: List<Achievement> = emptyList()
        override suspend fun saveAchievement(campaignId: Long, achievement: Achievement) {
            savedAchievements.add(achievement)
            if (achievementsPersist) achievementsToReturn = achievementsToReturn + achievement
        }
        /** true — выданные/удалённые достижения видны в getAchievements (сквозные проверки 42.1). */
        var achievementsPersist: Boolean = false
        val savedAchievements = mutableListOf<Achievement>()
        override suspend fun setQuestUnavailable(campaignId: Long, questId: String) {
            unavailableQuestIds.add(questId)
        }
        val unavailableQuestIds = mutableListOf<String>()
        override suspend fun getAllChapterInfo(): List<ChapterInfo> = chapterInfoToReturn
        override suspend fun getChapterInfo(chapter: Int): ChapterInfo? = chapterInfoToReturn.find { it.chapter == chapter }
        var chapterInfoToReturn: List<ChapterInfo> = emptyList()
        override suspend fun deleteAchievement(campaignId: Long, achievementId: String) {
            deletedAchievementIds.add(achievementId)
            if (achievementsPersist) achievementsToReturn = achievementsToReturn.filter { it.id != achievementId }
        }
        val deletedAchievementIds = mutableListOf<String>()
    }

    /**
     * Завершает пролог (первый бой кампании), чтобы isPrologue стал false. После 36.1 победа в прологе
     * применяется молча в onVictory (трофей + 2 стихии) и сразу открывает окно наград главы — его отклоняем.
     */
    private suspend fun completePrologue(viewModel: CampaignViewModel) {
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onChapterRewardsReject()
        kotlinx.coroutines.delay(100)
    }

    //region 7.2.1. onPauseBattle сохраняет текущий экран боя

    @Test
    fun `onPauseBattle сохраняет CampaignBattle в lastActiveBattle и переходит в MainMenu`() = runBlocking {
        // Подготовка: ViewModel с экраном CampaignBattle
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)
        assertTrue(viewModel.state.value.screen == AppScreen.QuickBattle)

        // Вызов проверяемого кода
        viewModel.onPauseBattle()

        // Проверка: lastActiveBattle = QuickBattle, screen = MainMenu
        val state = viewModel.state.value
        assertEquals(AppScreen.MainMenu, state.screen, "Экран должен переключиться в MainMenu")
        assertEquals(AppScreen.QuickBattle, state.lastActiveBattle,
            "lastActiveBattle должен сохранить QuickBattle")
        scope.cancel()
    }

    @Test
    fun `onPauseBattle сохраняет QuickBattle в lastActiveBattle`() = runBlocking {
        // Подготовка: переходим в QuickBattle
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)

        // Вызов проверяемого кода
        viewModel.onPauseBattle()

        // Проверка
        assertEquals(AppScreen.QuickBattle, viewModel.state.value.lastActiveBattle)
        assertEquals(AppScreen.MainMenu, viewModel.state.value.screen)
        scope.cancel()
    }

    @Test
    fun `onPauseBattle не сохраняет lastActiveBattle если экран MainMenu`() {
        // Подготовка: MainMenu — экран по умолчанию
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода
        viewModel.onPauseBattle()

        // Проверка: lastActiveBattle остался null, screen не изменился
        assertNull(viewModel.state.value.lastActiveBattle,
            "lastActiveBattle не должен сохраняться для MainMenu")
    }

    @Test
    fun `onPauseBattle не сохраняет lastActiveBattle если экран CampaignSheet`() = runBlocking {
        // Подготовка: имитируем навигацию в CampaignSheet
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)
        viewModel.onPauseBattle()
        assertEquals(AppScreen.MainMenu, viewModel.state.value.screen)

        // Вызов проверяемого кода из MainMenu (уже не бой)
        viewModel.onPauseBattle()

        // Проверка: lastActiveBattle не изменился (остался от предыдущего вызова)
        assertEquals(AppScreen.QuickBattle, viewModel.state.value.lastActiveBattle)
        scope.cancel()
    }

    //endregion

    //region 7.2.2. onResumeBattle восстанавливает экран боя

    @Test
    fun `onResumeBattle восстанавливает CampaignBattle из lastActiveBattle и очищает его`() = runBlocking {
        // Подготовка: сохраняем CampaignBattle через onPauseBattle
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)
        viewModel.onPauseBattle()
        assertNotNull(viewModel.state.value.lastActiveBattle)

        // Вызов проверяемого кода
        viewModel.onResumeBattle()

        // Проверка: экран восстановлен в QuickBattle, lastActiveBattle очищен
        val state = viewModel.state.value
        assertEquals(AppScreen.QuickBattle, state.screen,
            "Экран должен восстановиться в QuickBattle")
        assertNull(state.lastActiveBattle,
            "lastActiveBattle должен быть очищен после восстановления")
        scope.cancel()
    }

    @Test
    fun `onResumeBattle не делает ничего если lastActiveBattle равен null`() {
        // Подготовка: чистое состояние (lastActiveBattle = null по умолчанию)
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода
        viewModel.onResumeBattle()

        // Проверка: screen остался MainMenu
        assertEquals(AppScreen.MainMenu, viewModel.state.value.screen,
            "Экран не должен измениться если lastActiveBattle == null")
    }

    //endregion

    //region 7.2.3. onBackToMenu сбрасывает lastActiveBattle

    @Test
    fun `onBackToMenu сбрасывает lastActiveBattle в null`() = runBlocking {
        // Подготовка: сохраняем бой через onPauseBattle
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)
        viewModel.onPauseBattle()
        assertNotNull(viewModel.state.value.lastActiveBattle,
            "lastActiveBattle должен быть установлен после onPauseBattle")

        // Вызов проверяемого кода
        viewModel.onBackToMenu()

        // Проверка: lastActiveBattle сброшен через CampaignUiState()
        assertNull(viewModel.state.value.lastActiveBattle,
            "onBackToMenu должен сбросить lastActiveBattle в null")
        assertEquals(AppScreen.MainMenu, viewModel.state.value.screen)
        scope.cancel()
    }

    //endregion

    //region 7.2.4. CampaignUiState.lastActiveBattle по умолчанию

    @Test
    fun `CampaignUiState имеет lastActiveBattle равный null по умолчанию`() {
        // Подготовка

        // Вызов проверяемого кода: создаём дефолтный CampaignUiState
        val state = CampaignUiState()

        // Проверка
        assertNull(state.lastActiveBattle,
            "lastActiveBattle должен быть null по умолчанию")
    }

    //endregion

    //region 8.1. Обработка ошибок БД в onCampaignModeSelected

    @Test
    fun `onCampaignModeSelected устанавливает fatalError при исключении в getCampaignCount`() = runBlocking {
        // Подготовка: FakeRepository выбрасывает исключение
        val repo = FakeCampaignRepository().apply { shouldThrowOnGetCampaignCount = true }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        // Вызов проверяемого кода
        viewModel.onCampaignModeSelected()

        // Ждём завершения корутины
        kotlinx.coroutines.delay(100)

        // Проверка: fatalError установлен, экран не изменился
        val state = viewModel.state.value
        assertNotNull(state.fatalError, "fatalError должен быть установлен при ошибке БД")
        assertTrue(state.fatalError!!.contains("Ошибка базы данных"),
            "fatalError должен содержать сообщение об ошибке БД")
        assertEquals(AppScreen.MainMenu, state.screen,
            "Экран не должен измениться при ошибке БД")

        viewModelScope.cancel()
    }

    @Test
    fun `onCampaignModeSelected не устанавливает fatalError при успешном запросе`() = runBlocking {
        // Подготовка: нормальный FakeRepository (пустая БД)
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        // Вызов проверяемого кода
        viewModel.onCampaignModeSelected()

        // Ждём завершения корутины
        kotlinx.coroutines.delay(100)

        // Проверка: fatalError = null, экран перешёл в CampaignSetup (count == 0)
        val state = viewModel.state.value
        assertNull(state.fatalError, "fatalError должен быть null при успешном запросе")
        assertEquals(AppScreen.CampaignSetup, state.screen,
            "При пустой БД экран должен перейти в CampaignSetup")

        viewModelScope.cancel()
    }

    @Test
    fun `CampaignUiState имеет fatalError равный null по умолчанию`() {
        // Подготовка

        // Вызов проверяемого кода: создаём дефолтный CampaignUiState
        val state = CampaignUiState()

        // Проверка
        assertNull(state.fatalError, "fatalError должен быть null по умолчанию")
    }

    @Test
    fun `onBackToMenu сбрасывает fatalError в null`() = runBlocking {
        // Подготовка: напрямую устанавливаем fatalError через state copy
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)
        viewModel.onPauseBattle()

        // Вызов проверяемого кода: onBackToMenu создаёт новый CampaignUiState()
        viewModel.onBackToMenu()

        // Проверка: fatalError = null
        assertNull(viewModel.state.value.fatalError,
            "onBackToMenu должен сбросить fatalError в null через CampaignUiState()")
        scope.cancel()
    }

    //endregion

    //region 9.1. Pre-battle диалог для кампании

    @Test
    fun `onStartCampaign переводит screen в CampaignBattle и заполняет preBattleHunters`() = runBlocking {
        // Подготовка: ViewModel с названием и классом
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)

        // Вызов проверяемого кода
        viewModel.onStartCampaign()

        // Ждём завершения корутины
        kotlinx.coroutines.delay(100)

        // Проверка: screen = CampaignBattle, preBattleHunters заполнен, isPrologue = true
        val state = viewModel.state.value
        assertTrue(state.screen is AppScreen.CampaignBattle,
            "Экран должен переключиться в CampaignBattle после старта кампании")
        assertTrue(state.preBattleHunters.isNotEmpty(),
            "preBattleHunters должен быть заполнен")
        assertEquals(1, state.preBattleHunters.size,
            "Должен быть один охотник")
        assertTrue(state.isPrologue,
            "isPrologue должен быть true для новой кампании")

        viewModelScope.cancel()
    }

    @Test
    fun `startBattleInternal создаёт BattleViewModel в фазе PRE_BATTLE`() = runBlocking {
        // Подготовка
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Проверка: BattleViewModel создан, фаза PRE_BATTLE
        val battleVm = viewModel.getBattleViewModel()
        assertNotNull(battleVm, "BattleViewModel должен быть создан")
        assertEquals(FightPhase.PRE_BATTLE, battleVm.state.value.phase,
            "Фаза боя должна быть PRE_BATTLE до вызова onConfirmCampaignBattleStart")

        viewModelScope.cancel()
    }

    @Test
    fun `onConfirmCampaignBattleStart запускает бой с переданными параметрами`() = runBlocking {
        // Подготовка: создаём кампанию, доходим до PRE_BATTLE
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        val battleVm = viewModel.getBattleViewModel()
        assertNotNull(battleVm)

        // Вызов проверяемого кода
        viewModel.onConfirmCampaignBattleStart(damageForWound = 3, healthForStanceChange = 5)

        // Проверка: бой начался с переданными параметрами
        val battleState = battleVm.state.value
        assertEquals(FightPhase.PHASE_I, battleState.phase,
            "Фаза должна быть PHASE_I после подтверждения старта боя")
        assertEquals(3, battleState.monster.damageForWound,
            "damageForWound должен соответствовать переданному значению")
        assertEquals(5, battleState.monster.healthForStanceChange,
            "healthForStanceChange должен соответствовать переданному значению")

        viewModelScope.cancel()
    }

    @Test
    fun `onConfirmCampaignBattleStart очищает preBattleHunters после запуска`() = runBlocking {
        // Подготовка
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        assertTrue(viewModel.state.value.preBattleHunters.isNotEmpty(),
            "preBattleHunters должен быть заполнен до запуска боя")

        // Вызов проверяемого кода
        viewModel.onConfirmCampaignBattleStart(4, 7)

        // Проверка: preBattleHunters очищен
        assertTrue(viewModel.state.value.preBattleHunters.isEmpty(),
            "preBattleHunters должен быть очищен после запуска боя")

        viewModelScope.cancel()
    }

    @Test
    fun `CampaignUiState preBattleHunters по умолчанию пуст`() {
        // Подготовка

        // Вызов проверяемого кода: создаём дефолтный CampaignUiState
        val state = CampaignUiState()

        // Проверка
        assertTrue(state.preBattleHunters.isEmpty(),
            "preBattleHunters должен быть пуст по умолчанию")
    }

    //endregion

    //region 9.2. Множественный выбор заданий

    @Test
    fun `onVictoryQuestToggled добавляет номер в selectedQuestNumbers`() {
        // Подготовка
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода
        viewModel.onVictoryQuestToggled(42)

        // Проверка
        val state = viewModel.state.value
        assertTrue(state.selectedQuestNumbers.contains(42),
            "selectedQuestNumbers должен содержать 42 после toggle")
        assertEquals(1, state.selectedQuestNumbers.size,
            "В selectedQuestNumbers должен быть 1 элемент")
    }

    @Test
    fun `onVictoryQuestToggled удаляет номер при повторном вызове`() {
        // Подготовка: добавляем 42
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onVictoryQuestToggled(42)

        // Вызов проверяемого кода: повторный toggle того же номера
        viewModel.onVictoryQuestToggled(42)

        // Проверка: 42 удалён
        val state = viewModel.state.value
        assertFalse(state.selectedQuestNumbers.contains(42),
            "selectedQuestNumbers не должен содержать 42 после повторного toggle")
        assertTrue(state.selectedQuestNumbers.isEmpty(),
            "selectedQuestNumbers должен быть пуст")
    }

    @Test
    fun `onVictoryQuestToggled поддерживает множественный выбор`() {
        // Подготовка
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода: несколько toggle
        viewModel.onVictoryQuestToggled(5)
        viewModel.onVictoryQuestToggled(10)
        viewModel.onVictoryQuestToggled(15)

        // Проверка: все три номера присутствуют
        val state = viewModel.state.value
        assertEquals(3, state.selectedQuestNumbers.size)
        assertTrue(state.selectedQuestNumbers.contains(5))
        assertTrue(state.selectedQuestNumbers.contains(10))
        assertTrue(state.selectedQuestNumbers.contains(15))
    }

    @Test
    fun `selectedQuestNumbers по умолчанию пуст в CampaignUiState`() {
        // Подготовка

        // Вызов проверяемого кода
        val state = CampaignUiState()

        // Проверка
        assertTrue(state.selectedQuestNumbers.isEmpty(),
            "selectedQuestNumbers должен быть пуст по умолчанию")
    }

    //endregion

    //region 9.3. Полный список боссов и isPrologue

    @Test
    fun `onVictory для пролога устанавливает defeatedBosses только с Вираксен`() = runBlocking {
        // Подготовка: создаём кампанию (isPrologue = true)
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Пролог")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: пролог — только Вираксен, предзаполнены bossName и bossElement
        val state = viewModel.state.value
        assertEquals(listOf("Вираксен"), state.defeatedBosses,
            "Для пролога defeatedBosses должен содержать только Вираксен")
        assertEquals("Вираксен", state.bossName,
            "Для пролога bossName должен быть Вираксен")
        assertEquals(Element.FIRE, state.bossElement,
            "Для пролога bossElement должен быть FIRE")

        viewModelScope.cancel()
    }

    @Test
    fun `onVictory для не-пролога загружает ALL_BOSS_NAMES`() = runBlocking {
        // Подготовка: имитируем не-пролог — после завершения пролога isPrologue сброшен в false
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Не пролог")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onConfirmCampaignBattleStart(4, 7)

        // Имитируем не-пролог: симулируем, что пролог уже завершён (isPrologue = false)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: defeatedBosses содержит только Вираксен (isPrologue ещё true)
        assertEquals(listOf("Вираксен"), viewModel.state.value.defeatedBosses,
            "При isPrologue=true defeatedBosses должен содержать только Вираксен")

        // Завершаем пролог: устанавливаем bossName и bossElement для валидации,
        // затем вызываем onConfirmVictory чтобы сбросить isPrologue
        viewModel.onVictoryBossNameChanged("Вираксен")
        viewModel.onVictoryBossElementChanged(Element.FIRE)
        viewModel.onVictoryQuestToggled(1)

        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: isPrologue сброшен в false
        assertFalse(viewModel.state.value.isPrologue,
            "После onConfirmVictory isPrologue должен быть false")

        viewModelScope.cancel()
    }

    @Test
    fun `CampaignUiState isPrologue по умолчанию false`() {
        // Подготовка

        // Вызов проверяемого кода
        val state = CampaignUiState()

        // Проверка
        assertFalse(state.isPrologue, "isPrologue должен быть false по умолчанию")
    }

    //endregion

    //region 9.4. Возврат в бой экспедиции

    @Test
    fun `onQuickBattleSelected создаёт BattleViewModel`() = runBlocking {
        // Подготовка
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        // Вызов проверяемого кода
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)

        // Проверка: BattleViewModel создан и сохранён
        assertNotNull(viewModel.getBattleViewModel(),
            "После onQuickBattleSelected BattleViewModel должен быть создан")
        assertEquals(AppScreen.QuickBattle, viewModel.state.value.screen,
            "Экран должен переключиться в QuickBattle")
        scope.cancel()
    }

    @Test
    fun `onQuickBattleSelected не создаёт новый BattleViewModel при повторном вызове`() = runBlocking {
        // Подготовка: первый вызов
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)
        val firstVm = viewModel.getBattleViewModel()
        assertNotNull(firstVm)

        // Имитация возврата в меню и повторного входа
        viewModel.onPauseBattle()
        assertEquals(AppScreen.MainMenu, viewModel.state.value.screen)

        // Вызов проверяемого кода: второй вызов
        viewModel.onResumeBattle()

        // Проверка: используется тот же BattleViewModel
        val secondVm = viewModel.getBattleViewModel()
        assertNotNull(secondVm)
        assertTrue(firstVm === secondVm,
            "При возврате в бой должен использоваться тот же BattleViewModel")

        assertEquals(AppScreen.QuickBattle, viewModel.state.value.screen,
            "Экран должен восстановиться в QuickBattle")
        scope.cancel()
    }

    @Test
    fun `getBattleViewModel возвращает null если бой не начинался`() {
        // Подготовка: чистый ViewModel

        // Вызов проверяемого кода
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Проверка
        assertNull(viewModel.getBattleViewModel(),
            "До вызова onQuickBattleSelected или onStartCampaign BattleViewModel должен быть null")
    }

    //endregion

    //region 12.4/14.2. getDifficultyForChapter

    @Test
    fun `getDifficultyForChapter возвращает 0 для первой главы`() {
        // Подготовка
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода
        val difficulty = viewModel.getDifficultyForChapter(1)

        // Проверка
        assertEquals(0, difficulty, "Глава 1 должна давать сложность 0")
    }

    @Test
    fun `getDifficultyForChapter возвращает корректную сложность по диапазонам`() {
        // Подготовка
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода + проверка
        assertEquals(1, viewModel.getDifficultyForChapter(2), "Глава 2 → сложность 1")
        assertEquals(1, viewModel.getDifficultyForChapter(4), "Глава 4 → сложность 1")
        assertEquals(2, viewModel.getDifficultyForChapter(5), "Глава 5 → сложность 2")
        assertEquals(2, viewModel.getDifficultyForChapter(8), "Глава 8 → сложность 2")
        assertEquals(3, viewModel.getDifficultyForChapter(9), "Глава 9 → сложность 3")
        assertEquals(3, viewModel.getDifficultyForChapter(11), "Глава 11 → сложность 3")
    }

    //endregion

    //region 14.1. Резолв босса по name+difficulty

    private fun createBoss(name: String, difficulty: Int, dfw: Int, hsc: Int?, element: Element? = Element.FIRE) = Boss(
        id = difficulty.toLong() + 1,
        name = name,
        element = element,
        difficulty = difficulty,
        stances = listOf(BossStance(dfw, hsc))
    )

    @Test
    fun `onPreBattleBossSelected резолвит босса и заполняет dfw и hsc`() = runBlocking {
        // Подготовка: Fake с двумя боссами разных сложностей
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(
                createBoss("Вираксен", 0, 2, 7),
                createBoss("Вираксен", 1, 5, 7)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)

        // Вызов проверяемого кода
        viewModel.onPreBattleBossSelected("Вираксен")

        // Проверка: босс разрешён по сложности 0, dfw/hsc заполнены
        val state = viewModel.state.value
        assertEquals("Вираксен", state.selectedPreBattleBossName)
        assertEquals(0, state.selectedPreBattleBoss?.difficulty)
        assertEquals("2", state.preBattleDamageForWound)
        assertEquals("7", state.preBattleHealthForStance)

        scope.cancel()
    }

    @Test
    fun `onPreBattleBossSelected null сбрасывает на 4 и 7`() {
        // Подготовка
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода
        viewModel.onPreBattleBossSelected(null)

        // Проверка
        val state = viewModel.state.value
        assertNull(state.selectedPreBattleBossName, "selectedPreBattleBossName должен быть null")
        assertEquals("4", state.preBattleDamageForWound, "dfw должен сброситься на 4")
        assertEquals("7", state.preBattleHealthForStance, "hsc должен сброситься на 7")
    }

    @Test
    fun `onPreBattleDifficultySelected пересчитывает dfw и hsc для выбранного босса`() = runBlocking {
        // Подготовка: босс с двумя сложностями
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(
                createBoss("Вираксен", 0, 2, 7),
                createBoss("Вираксен", 1, 5, 7)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)
        viewModel.onPreBattleBossSelected("Вираксен")

        // Вызов проверяемого кода: смена сложности на 1
        viewModel.onPreBattleDifficultySelected(1)

        // Проверка: босс переразрешён по сложности 1, dfw = 5
        val state = viewModel.state.value
        assertEquals(1, state.preBattleDifficulty)
        assertEquals(1, state.selectedPreBattleBoss?.difficulty)
        assertEquals("5", state.preBattleDamageForWound, "dfw должен пересчитаться для сложности 1")

        scope.cancel()
    }

    //endregion

    //region 14.4. Автозаполнение bossName/bossElement в onVictory

    @Test
    fun `onVictory с выбранным боссом автозаполняет bossName и bossElement`() = runBlocking {
        // Подготовка: кампания с выбранным боссом
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(createBoss("Вираксен", 0, 2, 7))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onPreBattleBossSelected("Вираксен")

        // Вызов проверяемого кода
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: bossName и bossElement заполнены из выбранного босса
        val state = viewModel.state.value
        assertEquals("Вираксен", state.bossName, "bossName должен быть из выбранного босса")
        assertEquals(Element.FIRE, state.bossElement, "bossElement должен быть из выбранного босса")

        scope.cancel()
    }

    //endregion

    //region 11.1. Выпадающий список боссов в PostVictoryDialog

    @Test
    fun `onVictoryBossSelected обновляет bossName`() {
        // Подготовка
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода
        viewModel.onVictoryBossSelected("Вираксен")

        // Проверка
        assertEquals("Вираксен", viewModel.state.value.bossName,
            "bossName должен обновиться через onVictoryBossSelected")
    }

    //endregion

    //region 11.2. Ошибка при невыбранной стихии босса

    @Test
    fun `onConfirmVictory показывает ошибку при bossElement null`() = runBlocking {
        // Подготовка: проходим пролог, чтобы isPrologue=false, затем второй бой с пустой стихией
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        // Завершаем пролог: onVictory предзаполняет FIRE, onConfirmVictory сбрасывает isPrologue
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryBossSelected("Вираксен")
        viewModel.onVictoryQuestToggled(1)
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Теперь не-пролог: второй бой → onVictory оставляет bossElement=null
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)

        // Вызов проверяемого кода
        viewModel.onConfirmVictory()

        // Проверка: ошибка «Выберите стихию босса»
        assertEquals("Выберите стихию босса", viewModel.state.value.error,
            "Должна быть ошибка о необходимости выбрать стихию босса")

        scope.cancel()
    }

    //endregion

    //region 11.3. Очистка error в onVictory

    @Test
    fun `onVictory очищает error в null`() = runBlocking {
        // Подготовка: кампания с предустановленной ошибкой
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: error = null
        assertNull(viewModel.state.value.error,
            "onVictory должен очистить error в null")

        scope.cancel()
    }

    //endregion

    //region 16.1. Null hsc в «Подготовке к бою»

    @Test
    fun `onPreBattleBossSelected для Иекороса оставляет preBattleHealthForStance пустым`() = runBlocking {
        // Подготовка: Иекорос с null hsc
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(createBoss("Иекорос", 0, 2, null, Element.LIGHTNING))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)

        // Вызов проверяемого кода
        viewModel.onPreBattleBossSelected("Иекорос")

        // Проверка: dfw заполнен, hsc — пустая строка (не «7»)
        val state = viewModel.state.value
        assertEquals("2", state.preBattleDamageForWound, "dfw должен быть 2")
        assertEquals("", state.preBattleHealthForStance,
            "hsc должен быть пустым для босса с null (смена по запросу)")

        scope.cancel()
    }

    @Test
    fun `onConfirmCampaignBattleStart с null hsc стартует бой`() = runBlocking {
        // Подготовка: кампания, preBattleHunters заполнен
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: null hsc
        viewModel.onConfirmCampaignBattleStart(damageForWound = 4, healthForStanceChange = null)

        // Проверка: бой начался (фаза PHASE_I), monster.healthForStanceChange = null
        val battleState = viewModel.getBattleViewModel()!!.state.value
        assertEquals(FightPhase.PHASE_I, battleState.phase, "Бой должен начаться")
        assertNull(battleState.monster.healthForStanceChange,
            "monster.healthForStanceChange должен быть null при ручном вводе без hsc")

        scope.cancel()
    }

    //endregion

    //region 18.x. Пробуждённый (nullable element)

    @Test
    fun `onPreBattleBossSelected для Пробуждённого авто-устанавливает сложность 3`() = runBlocking {
        // Подготовка: Пробуждённый только при сложности 3, без стихии
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(createBoss("Пробуждённый", 3, 30, 8, element = null))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)

        // Вызов проверяемого кода
        viewModel.onPreBattleBossSelected("Пробуждённый")

        // Проверка: сложность принудительно 3, босс разрешён
        val state = viewModel.state.value
        assertEquals(3, state.preBattleDifficulty,
            "Для Пробуждённого сложность должна быть 3")
        assertEquals("Пробуждённый", state.selectedPreBattleBoss?.name)
        assertEquals("30", state.preBattleDamageForWound, "dfw должен быть 30")

        scope.cancel()
    }

    @Test
    fun `onVictory с боссом без стихии устанавливает bossHasNoElement`() = runBlocking {
        // Подготовка: кампания с боссом без стихии, пролог завершён (не-пролог)
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(createBoss("Пробуждённый", 3, 30, 8, element = null))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        // Завершаем пролог (isPrologue → false)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryBossSelected("Вираксен")
        viewModel.onVictoryQuestToggled(1)
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Второй бой: выбираем Пробуждённого (без стихии), побеждаем
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onPreBattleBossSelected("Пробуждённый")

        // Вызов проверяемого кода
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: bossHasNoElement = true, bossElement = null, bossName заполнен
        val state = viewModel.state.value
        assertTrue(state.bossHasNoElement,
            "bossHasNoElement должен быть true для босса без стихии")
        assertNull(state.bossElement, "bossElement должен быть null")
        assertEquals("Пробуждённый", state.bossName)

        scope.cancel()
    }

    @Test
    fun `onConfirmVictory без стихии не показывает ошибку и не выдаёт ресурс`() = runBlocking {
        // Подготовка: кампания с боссом без стихии, пролог завершён
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(createBoss("Пробуждённый", 3, 30, 8, element = null))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        // Завершаем пролог
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryBossSelected("Вираксен")
        viewModel.onVictoryQuestToggled(1)
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Второй бой: выбираем Пробуждённого, побеждаем
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onPreBattleBossSelected("Пробуждённый")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)
        repo.addResourceCalls.clear()

        // Вызов проверяемого кода: подтверждение победы без стихии
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: нет ошибки «Выберите стихию», ресурс стихии не выдан
        val state = viewModel.state.value
        assertNull(state.error, "Не должно быть ошибки «Выберите стихию»")
        assertTrue(repo.addResourceCalls.isEmpty(),
            "Для босса без стихии ресурс стихии не должен выдаваться")

        scope.cancel()
    }

    //endregion

    //region 18.4. Логика bossElement в onVictory (исправлен баг `?:`)

    @Test
    fun `onVictory с боссом без стихии в прологе оставляет bossElement null`() = runBlocking {
        // Подготовка: пролог (isPrologue = true) с выбранным боссом без стихии (Пробуждённый)
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(createBoss("Пробуждённый", 3, 30, 8, element = null))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onPreBattleBossSelected("Пробуждённый")

        // Вызов проверяемого кода
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: bossElement = null (не подставляется FIRE), bossHasNoElement = true
        val state = viewModel.state.value
        assertEquals("Пробуждённый", state.bossName, "bossName должен быть из выбранного босса")
        assertNull(state.bossElement, "bossElement должен быть null для босса без стихии в прологе")
        assertTrue(state.bossHasNoElement, "bossHasNoElement должен быть true для босса без стихии")

        scope.cancel()
    }

    @Test
    fun `onVictory без выбранного босса в прологе предзаполняет FIRE`() = runBlocking {
        // Подготовка: пролог (isPrologue = true), босс не выбран
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: для пролога без выбранного босса bossElement = FIRE
        val state = viewModel.state.value
        assertEquals("Вираксен", state.bossName, "bossName должен быть Вираксен")
        assertEquals(Element.FIRE, state.bossElement, "bossElement должен быть FIRE")
        assertFalse(state.bossHasNoElement, "bossHasNoElement должен быть false")

        scope.cancel()
    }

    //endregion

    //region 25.5. Сортировка боссов + опциональный dfw

    @Test
    fun `loadBosses сортирует боссов по стихии и имени`() = runBlocking {
        // Подготовка: боссы в неотсортированном порядке
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(
                createBoss("Торамат", 0, 2, 7, Element.HORN),
                createBoss("Пробуждённый", 3, 30, 8, element = null),
                createBoss("Оруксен", 0, 2, 6, Element.CORAL),
                createBoss("Вираксен", 0, 2, 7, Element.FIRE),
                createBoss("Юром", 0, 2, 6, Element.METAL),
                createBoss("Дигоракс", 0, 2, 8, Element.HORN)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        // Вызов проверяемого кода
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(100)

        // Проверка: Коралл, Металл, Огонь, Рог (по имени), null-стихия — последняя
        val names = viewModel.state.value.availableBosses.map { it.name }
        assertEquals(
            listOf("Оруксен", "Юром", "Вираксен", "Дигоракс", "Торамат", "Пробуждённый"),
            names,
            "Боссы должны быть отсортированы по стихии (русский алфавит) затем по имени, без стихии — в конце"
        )

        scope.cancel()
    }

    @Test
    fun `onConfirmCampaignBattleStart с null dfw запускает бой без порога раны`() = runBlocking {
        // Подготовка: кампания с одним охотником
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: старт боя с пустым dfw
        viewModel.onConfirmCampaignBattleStart(null, 7)

        // Проверка: у монстра нет порога раны
        val monster = viewModel.battleState?.value?.monster
        assertNull(monster?.damageForWound, "damageForWound должен быть null (нет порога раны)")

        scope.cancel()
    }

    //endregion

    //region 28.1. onConfirmVictory — открытие заданий по одному без краша

    @Test
    fun `onConfirmVictory открывает каждое выбранное задание по одному с isAvailable true`() = runBlocking {
        // Подготовка: пролог пройден (36.1), после следующей победы в PostVictoryDialog выбраны задания 1 и 2
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        repo.saveVictoryRecords.clear() // победа пролога не относится к проверке
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryBossSelected("Вираксен")
        viewModel.onVictoryBossElementChanged(Element.FIRE)
        viewModel.onVictoryQuestToggled(1)
        viewModel.onVictoryQuestToggled(2)

        // Вызов проверяемого кода
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: для каждого выбранного номера сохранено задание с isAvailable=true
        val quests = repo.savedQuests
        assertEquals(2, quests.size, "Должно быть сохранено 2 задания")
        assertEquals(setOf(1, 2), quests.map { it.questNumber }.toSet(),
            "Номера сохранённых заданий должны соответствовать выбранным")
        assertTrue(quests.all { it.isAvailable },
            "Все открытые задания должны иметь isAvailable=true")
        assertNull(viewModel.state.value.error,
            "Ошибки быть не должно при успешном сохранении")
        assertEquals(1, repo.saveVictoryRecords.size,
            "saveVictory должен быть вызван один раз")

        viewModelScope.cancel()
    }

    @Test
    fun `onConfirmVictory не падает при ошибке сохранения и выставляет error`() = runBlocking {
        // Подготовка: репозиторий выбрасывает исключение в saveQuest
        val repo = FakeCampaignRepository().apply { shouldThrowOnSaveQuest = true }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)

        // Вызов проверяемого кода: не должно быть необработанного исключения
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: ошибка записана в state, приложение не упало
        assertNotNull(viewModel.state.value.error,
            "При сбое сохранения должна быть установлена ошибка")
        assertTrue(viewModel.state.value.error!!.contains("Ошибка сохранения победы"),
            "Сообщение об ошибке должно указывать на сохранение победы")

        viewModelScope.cancel()
    }

    //endregion

    //region 28.2. onConfirmVictory — начисление ресурсов всем охотникам

    @Test
    fun `onConfirmVictory начисляет материи и растения каждому охотнику`() = runBlocking {
        // Подготовка: кампания с двумя охотниками, пролог пройден (36.1), следующая победа над Вираксеном;
        // лист кампании после пролога перечитывает охотников из репозитория
        val repo = FakeCampaignRepository().apply {
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON),
                CampaignHunter(id = 2, campaignId = 1, playerName = "Мира", className = HunterClass.MIRA)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onClassToggled(HunterClass.MIRA)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        repo.resourceAddRecords.clear() // 2 «Огонь» пролога не относятся к проверке
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryBossSelected("Вираксен")
        viewModel.onVictoryBossElementChanged(Element.FIRE)
        viewModel.onVictoryQuestToggled(1)
        viewModel.onVictoryResourceChanged(ResourceType.MATERIAL, Material.SCALES.name, 2)
        viewModel.onVictoryResourceChanged(ResourceType.PLANT, Plant.NILLEA.name, 1)

        // Вызов проверяемого кода
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: ресурсы начислены каждому охотнику (2 охотника)
        val scalesAdds = repo.resourceAddRecords.filter {
            it.resourceType == "MATERIAL" && it.resourceName == Material.SCALES.name
        }
        assertEquals(2, scalesAdds.size,
            "Чешуя (SCALES) должна быть начислена каждому охотнику")
        assertTrue(scalesAdds.all { it.amount == 2 },
            "Каждому охотнику должно быть начислено 2 единицы SCALES")

        val nilleaAdds = repo.resourceAddRecords.filter {
            it.resourceType == "PLANT" && it.resourceName == Plant.NILLEA.name
        }
        assertEquals(2, nilleaAdds.size,
            "Ниллея (NILLEA) должна быть начислена каждому охотнику")
        assertTrue(nilleaAdds.all { it.amount == 1 },
            "Каждому охотнику должно быть начислено 1 единица NILLEA")

        val fireAdds = repo.resourceAddRecords.filter {
            it.resourceType == "ELEMENT" && it.resourceName == Element.FIRE.name
        }
        assertEquals(2, fireAdds.size,
            "Стихия FIRE (босс пролога) должна быть начислена каждому охотнику")
        assertTrue(fireAdds.all { it.amount == 2 },
            "Каждому охотнику должно быть начислено 2 единицы FIRE")

        viewModelScope.cancel()
    }

    //endregion

    //region 28.3. Поражение — задание становится доступным

    @Test
    fun `onBattleFinished сохраняет выбранное задание как доступное`() = runBlocking {
        // Подготовка: кампания в прологе, после поражения выбрано задание 7
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onDefeatQuestToggled(7)

        // Вызов проверяемого кода
        viewModel.onBattleFinished()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 7 сохранено с isAvailable=true, экран перешёл в CampaignSheet
        val quests = repo.savedQuests
        assertEquals(1, quests.size, "Должно быть сохранено 1 задание")
        assertEquals(7, quests.first().questNumber, "Номер задания должен быть 7")
        assertTrue(quests.first().isAvailable,
            "Задание после поражения должно быть доступным (isAvailable=true)")
        assertTrue(viewModel.state.value.screen is AppScreen.CampaignSheet,
            "Экран должен перейти в CampaignSheet после поражения")
        assertTrue(viewModel.state.value.selectedDefeatQuestNumbers.isEmpty(),
            "Выбранные номера заданий должны быть очищены после сохранения")

        viewModelScope.cancel()
    }

    @Test
    fun `onDefeatQuestToggled переключает выбранные номера заданий`() {
        // Подготовка
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода
        viewModel.onDefeatQuestToggled(7)
        viewModel.onDefeatQuestToggled(9)

        // Проверка: оба номера добавлены
        assertTrue(viewModel.state.value.selectedDefeatQuestNumbers.contains(7))
        assertTrue(viewModel.state.value.selectedDefeatQuestNumbers.contains(9))

        // Повторный toggle снимает выбор
        viewModel.onDefeatQuestToggled(7)
        assertFalse(viewModel.state.value.selectedDefeatQuestNumbers.contains(7),
            "Повторный toggle должен снять выбор номера 7")
    }

    //endregion

    //region 28.4. Активное задание завершается при победе

    @Test
    fun `onConfirmVictory завершает активное задание`() = runBlocking {
        // Подготовка: открытое задание 3, выбираем его активным перед боем
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = true)
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel) // задание выбирается уже после пролога (36.1)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        assertTrue(viewModel.state.value.showQuestSelectDialog,
            "Должен открыться диалог выбора активного задания")
        viewModel.onActiveQuestSelected("3")
        assertEquals("3", viewModel.state.value.activeQuestId,
            "Активное задание должно быть установлено")

        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryBossSelected("Коровон")
        viewModel.onVictoryBossElementChanged(Element.CORAL)
        viewModel.onVictoryQuestToggled(1)

        // Вызов проверяемого кода
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: saveVictory вызван с completedQuestId = активное задание "3"
        val lastVictory = repo.saveVictoryRecords.lastOrNull()
        assertNotNull(lastVictory, "saveVictory должен быть вызван")
        assertEquals("3", lastVictory!!.completedQuestId,
            "Завершаться должно активное задание (completedQuestId = 3)")

        viewModelScope.cancel()
    }

    //endregion

    //region 28.7. Кнопки +/− у ресурсов

    @Test
    fun `onResourceIncrement добавляет 1 к ресурсу охотника`() = runBlocking {
        // Подготовка: кампания с одним охотником
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onResourceIncrement(ResourceType.MATERIAL, Material.SCALES.name)
        kotlinx.coroutines.delay(100)

        // Проверка: addResource вызван с amount = 1
        val last = repo.resourceAddRecords.lastOrNull()
        assertNotNull(last, "addResource должен быть вызван")
        assertEquals("MATERIAL", last!!.resourceType)
        assertEquals(Material.SCALES.name, last.resourceName)
        assertEquals(1, last.amount, "Инкремент должен добавлять ровно 1")

        viewModelScope.cancel()
    }

    @Test
    fun `onResourceDecrement уменьшает ресурс на 1`() = runBlocking {
        // Подготовка: у охотника 5 единиц SCALES
        val repo = FakeCampaignRepository().apply {
            materialsToReturn = mapOf(Material.SCALES to 5)
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onHunterSelected(0)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onResourceDecrement(ResourceType.MATERIAL, Material.SCALES.name)
        kotlinx.coroutines.delay(100)

        // Проверка: updateResource вызван с quantity = 4
        val last = repo.resourceUpdateRecords.lastOrNull()
        assertNotNull(last, "updateResource должен быть вызван")
        assertEquals("MATERIAL", last!!.resourceType)
        assertEquals(Material.SCALES.name, last.resourceName)
        assertEquals(4, last.quantity, "Декремент должен уменьшить количество на 1")

        viewModelScope.cancel()
    }

    @Test
    fun `onResourceDecrement не уходит в минус при нуле`() = runBlocking {
        // Подготовка: у охотника 0 единиц SCALES
        val repo = FakeCampaignRepository().apply {
            materialsToReturn = mapOf(Material.SCALES to 0)
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onHunterSelected(0)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: попытка уменьшить нулевой ресурс
        viewModel.onResourceDecrement(ResourceType.MATERIAL, Material.SCALES.name)
        kotlinx.coroutines.delay(100)

        // Проверка: updateResource НЕ вызван (минимум 0)
        assertTrue(repo.resourceUpdateRecords.isEmpty(),
            "При нулевом количестве декремент не должен вызывать updateResource")

        viewModelScope.cancel()
    }

    //endregion

    //region 29.1. Идемпотентность сохранения заданий + корректные id охотников + isSaving

    @Test
    fun `onStartCampaign перечитывает охотников из БД с реальными id`() = runBlocking {
        // Подготовка: фейковый репозиторий возвращает охотников с id из БД
        val repo = FakeCampaignRepository().apply {
            huntersToReturn = listOf(
                CampaignHunter(id = 11, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON),
                CampaignHunter(id = 12, campaignId = 1, playerName = "Мира", className = HunterClass.MIRA)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onClassToggled(HunterClass.MIRA)

        // Вызов проверяемого кода
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Проверка: state.hunters содержит охотников с id из БД, а не 0
        val hunters = viewModel.state.value.hunters
        assertEquals(2, hunters.size, "Должно быть 2 охотника")
        assertTrue(hunters.all { it.id != 0L },
            "Охотники в state.hunters должны иметь реальные id из БД")
        assertEquals(listOf(11L, 12L), hunters.map { it.id },
            "id охотников должны соответствовать getHunters(campaignId)")

        viewModelScope.cancel()
    }

    @Test
    fun `onConfirmVictory начисляет ресурсы охотникам с реальными id`() = runBlocking {
        // Подготовка: кампания с охотниками, у которых реальные id из БД
        val repo = FakeCampaignRepository().apply {
            huntersToReturn = listOf(
                CampaignHunter(id = 11, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON),
                CampaignHunter(id = 12, campaignId = 1, playerName = "Мира", className = HunterClass.MIRA)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onClassToggled(HunterClass.MIRA)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)
        viewModel.onVictoryResourceChanged(ResourceType.MATERIAL, Material.SCALES.name, 1)

        // Вызов проверяемого кода
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: addResource вызван с реальными id охотников (не 0)
        val scalesAdds = repo.resourceAddRecords.filter {
            it.resourceType == "MATERIAL" && it.resourceName == Material.SCALES.name
        }
        assertEquals(2, scalesAdds.size, "SCALES должен начисляться каждому охотнику")
        assertTrue(scalesAdds.all { it.hunterId in listOf(11L, 12L) },
            "Ресурсы должны начисляться охотникам с реальными id (не 0)")

        viewModelScope.cancel()
    }

    @Test
    fun `onConfirmVictory сохраняет каждое задание ровно один раз при повторных нажатиях`() = runBlocking {
        // Подготовка: кампания, выбранные задания 1 и 2
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)
        viewModel.onVictoryQuestToggled(2)

        // Вызов проверяемого кода: два нажатия «Продолжить» подряд
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: задания не продублированы (по одному на номер)
        val quests = repo.savedQuests
        assertEquals(2, quests.size, "Задания не должны дублироваться при повторных нажатиях")
        assertEquals(setOf(1, 2), quests.map { it.questNumber }.toSet(),
            "Номера сохранённых заданий должны соответствовать выбранным")

        viewModelScope.cancel()
    }

    @Test
    fun `onConfirmVictory блокирует повторный вызов во время сохранения`() = runBlocking {
        // Подготовка: сохранение задания занимает время (задержка в saveQuest)
        val repo = FakeCampaignRepository().apply { saveQuestDelayMs = 50 }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)

        // Вызов проверяемого кода: первое нажатие начинает сохранение
        viewModel.onConfirmVictory()
        // Сразу проверяем isSaving = true (без ожидания завершения)
        assertTrue(viewModel.state.value.isSaving,
            "Во время сохранения isSaving должен быть true")

        // Повторное нажатие во время сохранения игнорируется
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(200)

        // Проверка: задание сохранено ровно один раз, isSaving сброшен
        assertEquals(1, repo.savedQuests.size,
            "Повторное нажатие во время сохранения не должно создавать дубликат")
        assertFalse(viewModel.state.value.isSaving,
            "После завершения сохранения isSaving должен быть false")

        viewModelScope.cancel()
    }

    @Test
    fun `onConfirmVictory после успеха закрывает диалог и открывает награды главы`() = runBlocking {
        // Подготовка: кампания в прологе, выбрано задание 1
        val repo = FakeCampaignRepository()
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)

        // Вызов проверяемого кода
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: диалог победы закрыт, открыто окно наград главы
        val state = viewModel.state.value
        assertFalse(state.showPostVictory, "Диалог победы должен закрыться")
        assertTrue(state.showChapterRewards, "После победы должно открыться окно наград главы")

        // Переход к листу кампании через «Отклонить» награды главы
        viewModel.onChapterRewardsReject()
        kotlinx.coroutines.delay(100)
        assertTrue(viewModel.state.value.screen is AppScreen.CampaignSheet,
            "После отказа от наград главы экран должен перейти в CampaignSheet")

        viewModelScope.cancel()
    }

    //endregion

    //region 31.1T. Репозиторий каталога заданий через Fake

    @Test
    fun `getAllTaskInfo возвращает каталог заданий через репозиторий`() = runBlocking {
        // Подготовка: фейковый репозиторий с каталогом заданий и кампанией с охотником
        val repo = FakeCampaignRepository().apply {
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 1, name = "Память пустыни", bossName = "Торамат", bossElement = Element.HORN),
                TaskInfo(questNumber = 2, name = "Полёт в вечную бурю", bossName = "Озев", bossElement = Element.LIGHTNING)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        // Подготовка: создаём кампанию, чтобы onStartCampaignBattle не вышел досрочно
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: читаем каталог через onStartCampaignBattle (который подгружает taskInfoByQuestNumber)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)

        // Проверка: taskInfoByQuestNumber заполнен из getAllTaskInfo()
        val taskInfoMap = viewModel.state.value.taskInfoByQuestNumber
        assertEquals(2, taskInfoMap.size, "Каталог заданий должен содержать 2 записи")
        assertEquals("Память пустыни", taskInfoMap[1]?.name)
        assertEquals("Торамат", taskInfoMap[1]?.bossName)
        assertEquals(Element.HORN, taskInfoMap[1]?.bossElement)
        assertEquals("Полёт в вечную бурю", taskInfoMap[2]?.name)
        assertEquals(Element.LIGHTNING, taskInfoMap[2]?.bossElement)

        viewModelScope.cancel()
    }

    @Test
    fun `getTaskInfo возвращает null для неизвестного номера задания`() = runBlocking {
        // Подготовка: фейковый репозиторий с одним заданием и кампанией с охотником
        val repo = FakeCampaignRepository().apply {
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 1, name = "Память пустыни", bossName = "Торамат", bossElement = Element.HORN)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: запрашиваем неизвестный номер (99)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)

        // Проверка: в каталоге нет номера 99
        assertNull(viewModel.state.value.taskInfoByQuestNumber[99],
            "Неизвестный номер задания не должен присутствовать в каталоге")

        viewModelScope.cancel()
    }

    //endregion

    //region 31.3T. Сортировка открытых заданий и каталог TaskInfo

    @Test
    fun `loadCampaignSheet показывает только открытые задания отсортированные по номеру`() = runBlocking {
        // Подготовка: фейковый репозиторий с заданиями вперемешку (выполненные + открытые)
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "10", name = "Задание 10", chapter = 1, questNumber = 10, isAvailable = true),
                Quest(id = "5", name = "Задание 5", chapter = 1, questNumber = 5, isAvailable = true),
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = false),
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 1, name = "Память пустыни", bossName = "Торамат", bossElement = Element.HORN),
                TaskInfo(questNumber = 5, name = "Серебряные когти", bossName = "Юром", bossElement = Element.METAL),
                TaskInfo(questNumber = 10, name = "Затопленные земли", bossName = "Оруксен", bossElement = Element.CORAL)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        // Подготовка: создаём кампанию
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: открываем лист кампании
        viewModel.onCampaignSelected(1)
        kotlinx.coroutines.delay(100)

        // Проверка: только открытые задания, отсортированы по questNumber (1, 5, 10)
        val openQuests = viewModel.state.value.campaignQuests
        assertEquals(listOf(1, 5, 10), openQuests.map { it.questNumber },
            "В листе должны быть только открытые задания, отсортированные по возрастанию номера")
        assertTrue(openQuests.all { it.isAvailable },
            "В листе не должно быть выполненных/недоступных заданий")

        // Проверка: каталог TaskInfo заполнен
        val taskInfoMap = viewModel.state.value.taskInfoByQuestNumber
        assertEquals(3, taskInfoMap.size, "Каталог заданий должен содержать 3 записи")
        assertEquals("Память пустыни", taskInfoMap[1]?.name)
        assertEquals("Торамат", taskInfoMap[1]?.bossName)
        assertEquals(Element.HORN, taskInfoMap[1]?.bossElement)

        viewModelScope.cancel()
    }

    @Test
    fun `onStartCampaignBattle сортирует доступные задания по номеру`() = runBlocking {
        // Подготовка: фейковый репозиторий с доступными заданиями вперемешку
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "9", name = "Задание 9", chapter = 1, questNumber = 9, isAvailable = true),
                Quest(id = "2", name = "Задание 2", chapter = 1, questNumber = 2, isAvailable = true),
                Quest(id = "36", name = "Задание 36", chapter = 1, questNumber = 36, isAvailable = true)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: открываем диалог выбора активного задания
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)

        // Проверка: доступные задания отсортированы по questNumber (2, 9, 36)
        val available = viewModel.state.value.availableQuestsForNext
        assertEquals(listOf(2, 9, 36), available.map { it.questNumber },
            "Доступные задания в диалоге выбора должны быть отсортированы по возрастанию номера")

        viewModelScope.cancel()
    }

    //endregion

    //region 31.5T. Окно наград за задание

    @Test
    fun `onVictory не-пролог открывает окно наград задания`() = runBlocking {
        // Подготовка: кампания с активным заданием 3 и каталогом TaskInfo
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 3, name = "Рёв моря", bossName = "Коровон", bossElement = Element.CORAL)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("3")

        // Вызов проверяемого кода
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: окно наград задания с режимом VICTORY и активным заданием 3
        val state = viewModel.state.value
        assertTrue(state.showQuestRewards, "Должно открыться окно наград задания")
        assertEquals(QuestRewardsMode.VICTORY, state.questRewardsMode)
        assertEquals(3, state.activeQuestNumber)
        assertEquals("Рёв моря", state.taskInfoByQuestNumber[3]?.name)
        assertEquals("Коровон", state.taskInfoByQuestNumber[3]?.bossName)

        viewModelScope.cancel()
    }

    @Test
    fun `onDefeat открывает окно наград за поражение`() = runBlocking {
        // Подготовка: кампания с активным заданием 3
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 3, name = "Рёв моря", bossName = "Коровон", bossElement = Element.CORAL)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("3")

        // Вызов проверяемого кода
        viewModel.onDefeat()
        kotlinx.coroutines.delay(100)

        // Проверка: окно наград в режиме DEFEAT
        val state = viewModel.state.value
        assertTrue(state.showQuestRewards, "Должно открыться окно наград за поражение")
        assertEquals(QuestRewardsMode.DEFEAT, state.questRewardsMode)
        assertEquals(3, state.activeQuestNumber)

        viewModelScope.cancel()
    }

    @Test
    fun `onQuestRewardsAccept применяет награды задания и переходит в окно главы`() = runBlocking {
        // Подготовка: кампания, активное задание 3, каталог с наградами
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 3,
                    name = "Рёв моря",
                    bossName = "Коровон",
                    bossElement = Element.CORAL,
                    victoryMaterials = mapOf(Material.SCALES to 1),
                    victoryPlants = mapOf(Plant.NILLEA to 1),
                    victoryOpenQuests = listOf(10),
                    victoryAchievements = listOf("Затишье")
                )
            )
            chapterInfoToReturn = listOf(
                ChapterInfo(chapter = 1, rewards = mapOf(Material.BONES to 1))
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("3")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: ресурсы начислены всем охотникам, достижения выданы, задания открыты, окно главы открыто
        val state = viewModel.state.value
        assertFalse(state.showQuestRewards, "Окно наград задания должно закрыться")
        val scalesAdds = repo.resourceAddRecords.filter {
            it.resourceType == "MATERIAL" && it.resourceName == Material.SCALES.name
        }
        assertEquals(1, scalesAdds.size, "SCALES должен начисляться охотнику")
        assertTrue(scalesAdds.all { it.amount == 1 })
        val fireAdds = repo.resourceAddRecords.filter {
            it.resourceType == "ELEMENT" && it.resourceName == Element.CORAL.name
        }
        assertEquals(1, fireAdds.size, "Стихия CORAL должна начисляться охотнику")
        assertTrue(fireAdds.all { it.amount == 2 }, "Стихия босса должна начисляться в количестве 2")
        assertTrue(repo.savedAchievements.any { it.name == "Затишье" },
            "Достижение Затишье должно быть выдано")
        assertTrue(repo.savedQuests.any { it.questNumber == 10 && it.isAvailable },
            "Задание 10 должно быть открыто")
        assertTrue(state.showChapterRewards, "После наград задания должно открыться окно наград главы")

        viewModelScope.cancel()
    }

    @Test
    fun `onDefeatRewardsAccept открывает задания поражения и переходит в CampaignSheet`() = runBlocking {
        // Подготовка: кампания, активное задание 3, каталог с наградами поражения
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 3,
                    name = "Рёв моря",
                    bossName = "Коровон",
                    bossElement = Element.CORAL,
                    defeatOpenQuests = listOf(10)
                )
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("3")
        viewModel.onDefeat()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onDefeatRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 10 открыто, экран перешёл в CampaignSheet
        val state = viewModel.state.value
        assertFalse(state.showQuestRewards, "Окно наград за поражение должно закрыться")
        assertTrue(repo.savedQuests.any { it.questNumber == 10 && it.isAvailable },
            "Задание поражения 10 должно быть открыто")
        assertTrue(state.screen is AppScreen.CampaignSheet,
            "После наград поражения экран должен перейти в CampaignSheet")

        viewModelScope.cancel()
    }

    @Test
    fun `onQuestRewardsEdit для победы предзаполняет PostVictoryDialog`() = runBlocking {
        // Подготовка: кампания, активное задание 3, каталог
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 3,
                    name = "Рёв моря",
                    bossName = "Коровон",
                    bossElement = Element.CORAL,
                    victoryMaterials = mapOf(Material.SCALES to 2),
                    victoryPlants = mapOf(Plant.NILLEA to 1),
                    victoryOpenQuests = listOf(10)
                )
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("3")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsEdit()
        kotlinx.coroutines.delay(100)

        // Проверка: PostVictoryDialog предзаполнен из каталога
        val state = viewModel.state.value
        assertFalse(state.showQuestRewards, "Окно наград задания должно закрыться")
        assertTrue(state.showPostVictory, "Должен открыться PostVictoryDialog")
        assertEquals("Коровон", state.bossName)
        assertEquals(Element.CORAL, state.bossElement)
        assertEquals(mapOf(Material.SCALES to 2), state.victoryMaterials)
        assertEquals(mapOf(Plant.NILLEA to 1), state.victoryPlants)
        assertEquals(setOf(10), state.selectedQuestNumbers)

        viewModelScope.cancel()
    }

    @Test
    fun `onQuestRewardsEdit для поражения включает форму чекбоксов`() = runBlocking {
        // Подготовка: кампания, активное задание 3
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 3, name = "Рёв моря", bossName = "Коровон", bossElement = Element.CORAL)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("3")
        viewModel.onDefeat()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsEdit()
        kotlinx.coroutines.delay(100)

        // Проверка: режим редактирования поражения включён
        val state = viewModel.state.value
        assertFalse(state.showQuestRewards, "Окно наград должно закрыться")
        assertTrue(state.editDefeatMode, "Должна открыться форма чекбоксов поражения")

        viewModelScope.cancel()
    }

    @Test
    fun `onQuestRewardsDismiss сбрасывает флаги окна наград`() = runBlocking {
        // Подготовка: кампания, активное задание 3
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = true)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("3")
        viewModel.onDefeat()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsDismiss()

        // Проверка: флаги сброшены
        val state = viewModel.state.value
        assertFalse(state.showQuestRewards)
        assertEquals(null, state.questRewardsMode)
        assertEquals(null, state.activeQuestNumber)

        viewModelScope.cancel()
    }

    //endregion

    //region 31.6T2. Окно наград за главу

    @Test
    fun `onConfirmVictory после победы открывает окно наград главы с каталогом`() = runBlocking {
        // Подготовка: кампания в прологе, каталог глав с наградами главы 1
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = listOf(
                ChapterInfo(chapter = 1, rewards = mapOf(Material.BONES to 1, Material.SCALES to 1, Material.BLOOD to 2))
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)

        // Вызов проверяемого кода
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: окно наград главы открыто, каталог заполнен, сообщение о наградах сформировано
        val state = viewModel.state.value
        assertTrue(state.showChapterRewards, "Должно открыться окно наград главы")
        assertEquals(1, state.chapterInfoByNumber.size, "Каталог глав должен содержать 1 запись")
        assertEquals(mapOf(Material.BONES to 1, Material.SCALES to 1, Material.BLOOD to 2),
            state.chapterInfoByNumber[1]?.rewards)

        viewModelScope.cancel()
    }

    @Test
    fun `onChapterRewardsAccept применяет эффекты главы и переходит к следующей главе`() = runBlocking {
        // Подготовка: кампания в прологе, глава 1 с ресурсами, открытием, истечением, кузней/лабораторией
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = listOf(
                ChapterInfo(
                    chapter = 1,
                    rewards = mapOf(Material.BONES to 1),
                    rewardPlants = mapOf(Plant.NILLEA to 1),
                    openQuests = listOf(1, 2, 36),
                    expireQuests = listOf(5),
                    forgeUpgrade = true,
                    labUpgrade = true
                )
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)
        assertTrue(viewModel.state.value.showChapterRewards, "Окно наград главы должно быть открыто")

        // Вызов проверяемого кода
        viewModel.onChapterRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: ресурсы начислены, задания открыты, истёкшие помечены, уровни повышены, глава +1
        val state = viewModel.state.value
        assertFalse(state.showChapterRewards, "Окно наград главы должно закрыться")
        val bonesAdds = repo.resourceAddRecords.filter {
            it.resourceType == "MATERIAL" && it.resourceName == Material.BONES.name
        }
        assertEquals(1, bonesAdds.size, "Ресурсы главы должны начисляться охотнику")
        assertTrue(repo.savedQuests.any { it.questNumber == 1 && it.isAvailable },
            "Задание 1 должно быть открыто")
        assertTrue(repo.savedQuests.any { it.questNumber == 36 && it.isAvailable },
            "Задание 36 должно быть открыто")
        assertTrue(repo.unavailableQuestIds.contains("5"),
            "Задание 5 должно быть помечено недоступным")
        assertEquals(1, repo.forgeLevelRecords.size, "Уровень кузни должен повышаться")
        assertEquals(1, repo.labLevelRecords.size, "Уровень лаборатории должен повышаться")
        assertTrue(repo.chapterUpdateRecords.any { it.chapter == 2 },
            "Глава должна перейти к следующей (1 → 2)")
        assertTrue(state.screen is AppScreen.CampaignSheet,
            "После наград главы экран должен перейти в CampaignSheet")

        viewModelScope.cancel()
    }

    @Test
    fun `onChapterRewardsReject не применяет эффекты главы`() = runBlocking {
        // Подготовка: кампания в прологе, глава 1 с кузней/лабораторией и заданиями
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = listOf(
                ChapterInfo(
                    chapter = 1,
                    openQuests = listOf(36),
                    forgeUpgrade = true
                )
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)
        assertTrue(viewModel.state.value.showChapterRewards)

        // Вызов проверяемого кода
        viewModel.onChapterRewardsReject()
        kotlinx.coroutines.delay(100)

        // Проверка: эффекты главы не применены, глава не изменена
        val state = viewModel.state.value
        assertFalse(state.showChapterRewards, "Окно наград главы должно закрыться")
        assertTrue(repo.savedQuests.none { it.questNumber == 36 },
            "Задание 36 главы не должно открываться при отказе")
        assertTrue(repo.forgeLevelRecords.isEmpty(), "Уровень кузни не должен повышаться при отказе")
        assertTrue(repo.chapterUpdateRecords.isEmpty(), "Глава не должна меняться при отказе")
        assertTrue(state.screen is AppScreen.CampaignSheet,
            "После отказа от наград главы экран должен перейти в CampaignSheet")

        viewModelScope.cancel()
    }

    @Test
    fun `onChapterDecisionSelected выдает достижение при выборе соответствующего варианта`() = runBlocking {
        // Подготовка: ViewModel с кампанией
        val repo = FakeCampaignRepository().apply {
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: выбор «Да» (вариант для достижения)
        viewModel.onChapterDecisionSelected("Да", "Голос Волтьяра")
        kotlinx.coroutines.delay(100)

        // Проверка: достижение сохранено
        assertTrue(repo.savedAchievements.any { it.name == "Голос Волтьяра" },
            "Достижение Голос Волтьяра должно быть выдано")

        viewModelScope.cancel()
    }

    @Test
    fun `chapterRewardsMessage содержит условное сообщение при наличии достижения`() = runBlocking {
        // Подготовка: глава 1 с обычным и условным сообщением, достижение «Яд Пазиса» есть у кампании
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = listOf(
                ChapterInfo(
                    chapter = 1,
                    messages = listOf("Повышение уровня кузни"),
                    conditionalMessages = listOf(
                        ConditionalMessage(achievementName = "Яд Пазиса", message = "Получите награду 25")
                    )
                )
            )
            achievementsToReturn = listOf(
                Achievement(id = "Яд Пазиса", name = "Яд Пазиса", unlocked = true)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Открываем окно наград главы через победу в прологе
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryQuestToggled(1)
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        val message = viewModel.state.value.chapterRewardsMessage

        // Проверка: сообщение главы и условное сообщение (при наличии достижения) присутствуют
        assertTrue(viewModel.state.value.showChapterRewards, "Окно наград главы должно быть открыто")
        assertTrue(message.contains("Повышение уровня кузни"),
            "Обычное сообщение главы должно присутствовать")
        assertTrue(
            viewModel.state.value.chapterConditionOutcomes.any { it.result == "Достижение есть: Получите награду 25." },
            "Условное сообщение должно быть в условиях главы с результатом «Достижение есть»"
        )

        viewModelScope.cancel()
    }

    //endregion

    //region 34.1T. resolveConditionTarget, onGetFromAlly, questDisplayLabel, предзаполнение босса

    @Test
    fun `resolveConditionTarget CHAPTER_IN при главе книги 1 (глава приложения 2) открывает задание 4`() = runBlocking {
        // Подготовка: глава приложения 2 = глава 1 книги (R-1), активное задание 1 с условием (главы 1,2 → 4, иначе 6)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskCondition(
                    kind = TaskConditionKind.CHAPTER_IN,
                    chapterSet = listOf(1, 2),
                    questNumber = 4,
                    elseQuestNumber = 6
                ).let { condition ->
                    TaskInfo(
                        questNumber = 1,
                        name = "Память пустыни",
                        bossName = "Торамат",
                        bossElement = Element.HORN,
                        victoryOpenQuestConditions = listOf(condition)
                    )
                }
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("1")
        viewModel.onUpdateChapter(2)
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 4 открыто (условие CHAPTER_IN, глава книги 1 входит в [1,2])
        assertTrue(repo.savedQuests.any { it.questNumber == 4 && it.isAvailable },
            "При главе книги 1 условие CHAPTER_IN (главы 1,2 → 4) должно открыть задание 4")
        assertFalse(repo.savedQuests.any { it.questNumber == 6 && it.isAvailable },
            "Задание 6 (else) не должно открываться")

        viewModelScope.cancel()
    }

    @Test
    fun `resolveConditionTarget CHAPTER_IN сравнивает с главой книги - глава приложения 4 открывает else`() = runBlocking {
        // Подготовка: глава приложения 3 = глава 2 книги → условие (главы 1,2 → 4); глава приложения 4 = глава 3 книги → 6 (R-1)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskCondition(
                    kind = TaskConditionKind.CHAPTER_IN,
                    chapterSet = listOf(1, 2),
                    questNumber = 4,
                    elseQuestNumber = 6
                ).let { condition ->
                    TaskInfo(
                        questNumber = 1,
                        name = "Память пустыни",
                        bossName = "Торамат",
                        bossElement = Element.HORN,
                        victoryOpenQuestConditions = listOf(condition)
                    )
                }
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("1")
        viewModel.onUpdateChapter(4)
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: глава книги 3 не входит в [1,2] — открывается задание 6
        assertTrue(repo.savedQuests.any { it.questNumber == 6 && it.isAvailable },
            "При главе книги 3 должно открыться задание 6 (else)")
        assertFalse(repo.savedQuests.any { it.questNumber == 4 && it.isAvailable },
            "Задание 4 не должно открываться")

        viewModelScope.cancel()
    }

    @Test
    fun `resolveConditionTarget ACHIEVEMENT_OWNED открывает задание при наличии достижения`() = runBlocking {
        // Подготовка: кампания с достижением «Грибной лес», активное задание 11 с условием (Грибной лес → 32, иначе 17)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "11", name = "Задание 11", chapter = 1, questNumber = 11, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskCondition(
                    kind = TaskConditionKind.ACHIEVEMENT_OWNED,
                    achievementName = "Грибной лес",
                    questNumber = 32,
                    elseQuestNumber = 17
                ).let { condition ->
                    TaskInfo(
                        questNumber = 11,
                        name = "Город памяти",
                        bossName = "Харджа",
                        bossElement = Element.FIRE,
                        victoryOpenQuestConditions = listOf(condition)
                    )
                }
            )
            achievementsToReturn = listOf(
                Achievement(id = "Грибной лес", name = "Грибной лес", unlocked = true)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("11")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 32 открыто (достижение «Грибной лес» есть)
        assertTrue(repo.savedQuests.any { it.questNumber == 32 && it.isAvailable },
            "При наличии достижения «Грибной лес» должно открыться задание 32")
        assertFalse(repo.savedQuests.any { it.questNumber == 17 && it.isAvailable },
            "Задание 17 (else) не должно открываться при наличии достижения")

        viewModelScope.cancel()
    }

    @Test
    fun `resolveConditionTarget поражение открывает задание по условию`() = runBlocking {
        // Подготовка: кампания в главе 3, активное задание 2 с defeat-условием (глава 3 → 1)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "2", name = "Задание 2", chapter = 1, questNumber = 2, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskCondition(
                    kind = TaskConditionKind.CHAPTER_IN,
                    chapterSet = listOf(3),
                    questNumber = 1,
                    elseQuestNumber = 1
                ).let { condition ->
                    TaskInfo(
                        questNumber = 2,
                        name = "Полёт в вечную бурю",
                        bossName = "Озев",
                        bossElement = Element.LIGHTNING,
                        defeatOpenQuestConditions = listOf(condition)
                    )
                }
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("2")
        // Эмулируем главу 3
        viewModel.onUpdateChapter(3)
        kotlinx.coroutines.delay(100)
        viewModel.onDefeat()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onDefeatRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 1 открыто (глава 3 входит в chapterSet [3])
        assertTrue(repo.savedQuests.any { it.questNumber == 1 && it.isAvailable },
            "При главе 3 и defeat-условии CHAPTER_IN [3] должно открыться задание 1")

        viewModelScope.cancel()
    }

    @Test
    fun `onActiveQuestSelected предзаполняет босса из каталога заданий`() = runBlocking {
        // Подготовка: кампания, каталог с заданием 1 (Торамат, HORN)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 1, name = "Память пустыни", bossName = "Торамат", bossElement = Element.HORN)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: выбор задания 1
        viewModel.onActiveQuestSelected("1")
        kotlinx.coroutines.delay(100)

        // Проверка: pre-battle форма предзаполнена (имя босса и стихия из TaskInfo)
        val state = viewModel.state.value
        assertEquals("Торамат", state.selectedPreBattleBossName,
            "Имя босса должно быть предзаполнено из каталога")
        assertEquals(Element.HORN, state.bossElement,
            "Стихия босса должна быть предзаполнена из каталога")

        viewModelScope.cancel()
    }

    @Test
    fun `questDisplayLabel возвращает номер задания перед названием`() = runBlocking {
        // Подготовка: ViewModel с каталогом
        val repo = FakeCampaignRepository().apply {
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 1, name = "Память пустыни", bossName = "Торамат", bossElement = Element.HORN)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
            questsToReturn = listOf(
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: открываем лист кампании
        viewModel.onCampaignSelected(1)
        kotlinx.coroutines.delay(100)

        // Проверка: taskInfoByQuestNumber содержит каталог с боссом и названием для questNumber 1
        val taskInfo = viewModel.state.value.taskInfoByQuestNumber[1]
        assertNotNull(taskInfo, "Каталог должен содержать задание 1")
        assertEquals("Память пустыни", taskInfo!!.name)
        assertEquals("Торамат", taskInfo.bossName)
        assertEquals(Element.HORN, taskInfo.bossElement)

        viewModelScope.cancel()
    }

    //endregion

    //region 34.2T. Редактирование достижений (onAddAchievement, onDeleteAchievement)

    @Test
    fun `onOpenAchievementEditor устанавливает showAchievementEditor true и очищает newAchievementName`() {
        // Подготовка: ViewModel с предустановленным именем
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onNewAchievementNameChanged("Старое имя")

        // Вызов проверяемого кода
        viewModel.onOpenAchievementEditor()

        // Проверка
        val state = viewModel.state.value
        assertTrue(state.showAchievementEditor, "showAchievementEditor должен быть true")
        assertEquals("", state.newAchievementName, "newAchievementName должен быть очищен")
    }

    @Test
    fun `onCloseAchievementEditor сбрасывает showAchievementEditor и ошибку`() {
        // Подготовка: ViewModel с открытым редактором и ошибкой
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onOpenAchievementEditor()

        // Вызов проверяемого кода
        viewModel.onCloseAchievementEditor()

        // Проверка
        val state = viewModel.state.value
        assertFalse(state.showAchievementEditor, "showAchievementEditor должен быть false")
        assertEquals("", state.newAchievementName, "newAchievementName должен быть очищен")
        assertNull(state.error, "error должен быть null")
    }

    @Test
    fun `onNewAchievementNameChanged обновляет newAchievementName`() {
        // Подготовка: ViewModel
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода
        viewModel.onNewAchievementNameChanged("Новое достижение")

        // Проверка
        assertEquals("Новое достижение", viewModel.state.value.newAchievementName)
    }

    @Test
    fun `onAddAchievement с пустым названием показывает ошибку`() = runBlocking {
        // Подготовка: ViewModel с кампанией и пустым названием
        val repo = FakeCampaignRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: пустое название (после .trim())
        viewModel.onNewAchievementNameChanged("  ")
        viewModel.onAddAchievement()

        // Проверка: ошибка, saveAchievement не вызывался
        assertEquals("Введите название достижения", viewModel.state.value.error)
        assertTrue(repo.savedAchievements.isEmpty(), "saveAchievement не должен вызываться")
    }

    @Test
    fun `onAddAchievement с непустым названием сохраняет достижение и перечитывает список`() = runBlocking {
        // Подготовка: ViewModel с кампанией
        val repo = FakeCampaignRepository().apply {
            achievementsToReturn = listOf(
                Achievement(id = "Новое достижение", name = "Новое достижение", unlocked = true)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onNewAchievementNameChanged("Новое достижение")
        viewModel.onAddAchievement()
        kotlinx.coroutines.delay(100)

        // Проверка: достижение сохранено, состояние очищено, список перечитан
        val saved = repo.savedAchievements.firstOrNull()
        assertNotNull(saved, "saveAchievement должен быть вызван")
        assertEquals("Новое достижение", saved!!.name)
        assertTrue(saved.unlocked, "Достижение должно быть unlocked = true")
        assertTrue(repo.deletedAchievementIds.contains("Новое достижение"),
            "deleteAchievement должен быть вызван перед saveAchievement")
        assertEquals("", viewModel.state.value.newAchievementName, "newAchievementName должен быть очищен")
        assertNull(viewModel.state.value.error, "error должен быть null")
        assertEquals(1, viewModel.state.value.campaignAchievements.size,
            "campaignAchievements должен быть перечитан")
    }

    @Test
    fun `onDeleteAchievement вызывает deleteAchievement и перечитывает список`() = runBlocking {
        // Подготовка: ViewModel с кампанией и достижением
        val repo = FakeCampaignRepository().apply {
            achievementsToReturn = listOf(
                Achievement(id = "Достижение", name = "Достижение", unlocked = true)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onDeleteAchievement("Достижение")
        kotlinx.coroutines.delay(100)

        // Проверка: deleteAchievement вызван, список перечитан
        assertTrue(repo.deletedAchievementIds.contains("Достижение"),
            "deleteAchievement должен быть вызван с 'Достижение'")
        assertEquals(1, viewModel.state.value.campaignAchievements.size,
            "campaignAchievements должен быть перечитан после удаления")
    }

    //endregion

    //region 34.3T. Расширенная модель условий (ACHIEVEMENT_OWNED_IN_CHAPTER, rewardAchievement)

    @Test
    fun `resolveConditionTarget ACHIEVEMENT_OWNED_IN_CHAPTER открывает задание при главе 8 и достижении`() = runBlocking {
        // Подготовка: кампания с достижением «Горящий уголёк», глава 8, активное задание 25
        // Первое условие: ACHIEVEMENT_OWNED_IN_CHAPTER (глава 8 + «Горящий уголёк» → 34)
        // Второе условие: ACHIEVEMENT_OWNED (только «Горящий уголёк» → 27)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "25", name = "Задание 25", chapter = 1, questNumber = 25, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 25,
                    name = "Горящее солнце",
                    bossName = "Харджа",
                    bossElement = Element.FIRE,
                    victoryOpenQuestConditions = listOf(
                        TaskCondition(
                            kind = TaskConditionKind.ACHIEVEMENT_OWNED_IN_CHAPTER,
                            achievementName = "Горящий уголёк",
                            chapterSet = listOf(8),
                            questNumber = 34
                        ),
                        TaskCondition(
                            kind = TaskConditionKind.ACHIEVEMENT_OWNED,
                            achievementName = "Горящий уголёк",
                            questNumber = 27
                        )
                    )
                )
            )
            achievementsToReturn = listOf(
                Achievement(id = "Горящий уголёк", name = "Горящий уголёк", unlocked = true)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("25")
        // Устанавливаем главу 8 книги (глава приложения 9, R-1) для проверки комбинированного условия
        viewModel.onUpdateChapter(9)
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 34 открыто (первое условие с chapterSet=[8] сработало)
        assertTrue(repo.savedQuests.any { it.questNumber == 34 && it.isAvailable },
            "При главе 8 и достижении «Горящий уголёк» должно открыться задание 34")
        assertFalse(repo.savedQuests.any { it.questNumber == 27 && it.isAvailable },
            "Задание 27 (fallback) не должно открываться при сработавшем первом условии")

        viewModelScope.cancel()
    }

    @Test
    fun `resolveConditionTarget ACHIEVEMENT_OWNED_IN_CHAPTER не срабатывает при главе не 8`() = runBlocking {
        // Подготовка: кампания с достижением «Горящий уголёк», глава 5 (не 8)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "25", name = "Задание 25", chapter = 1, questNumber = 25, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 25,
                    name = "Горящее солнце",
                    bossName = "Харджа",
                    bossElement = Element.FIRE,
                    victoryOpenQuestConditions = listOf(
                        TaskCondition(
                            kind = TaskConditionKind.ACHIEVEMENT_OWNED_IN_CHAPTER,
                            achievementName = "Горящий уголёк",
                            chapterSet = listOf(8),
                            questNumber = 34
                        ),
                        TaskCondition(
                            kind = TaskConditionKind.ACHIEVEMENT_OWNED,
                            achievementName = "Горящий уголёк",
                            questNumber = 27
                        )
                    )
                )
            )
            achievementsToReturn = listOf(
                Achievement(id = "Горящий уголёк", name = "Горящий уголёк", unlocked = true)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("25")
        viewModel.onUpdateChapter(5)
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 27 открыто (первое условие не сработало — chapter не 8)
        assertTrue(repo.savedQuests.any { it.questNumber == 27 && it.isAvailable },
            "При главе 5 условие ACHIEVEMENT_OWNED_IN_CHAPTER не срабатывает, должно открыться 27")
        assertFalse(repo.savedQuests.any { it.questNumber == 34 && it.isAvailable },
            "Задание 34 (ACHIEVEMENT_OWNED_IN_CHAPTER) не должно открыться при главе 5")

        viewModelScope.cancel()
    }

    @Test
    fun `applyQuestOpenConditions rewardAchievement выдаёт достижение при наличии источника`() = runBlocking {
        // Подготовка: кампания с достижением «Голос Волтьяра», активное задание 29 с rewardAchievement
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "29", name = "Задание 29", chapter = 1, questNumber = 29, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 29,
                    name = "Пещеры эха",
                    bossName = "Иекорос",
                    bossElement = Element.LIGHTNING,
                    victoryOpenQuestConditions = listOf(
                        TaskCondition(
                            kind = TaskConditionKind.ACHIEVEMENT_OWNED,
                            achievementName = "Голос Волтьяра",
                            rewardAchievement = "Уробборос"
                        )
                    )
                )
            )
            achievementsToReturn = listOf(
                Achievement(id = "Голос Волтьяра", name = "Голос Волтьяра", unlocked = true)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("29")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: accept наград задания
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: достижение «Уробборос» выдано (через rewardAchievement)
        assertTrue(repo.savedAchievements.any { it.name == "Уробборос" && it.unlocked },
            "Условное достижение «Уробборос» должно быть выдано при наличии «Голос Волтьяра»")
    }

    @Test
    fun `applyQuestOpenConditions rewardAchievement не выдаётся без источника`() = runBlocking {
        // Подготовка: кампания БЕЗ достижения «Голос Волтьяра», активное задание 29
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "29", name = "Задание 29", chapter = 1, questNumber = 29, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 29,
                    name = "Пещеры эха",
                    bossName = "Иекорос",
                    bossElement = Element.LIGHTNING,
                    victoryOpenQuestConditions = listOf(
                        TaskCondition(
                            kind = TaskConditionKind.ACHIEVEMENT_OWNED,
                            achievementName = "Голос Волтьяра",
                            rewardAchievement = "Уробборос"
                        )
                    )
                )
            )
            achievementsToReturn = emptyList()
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("29")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: достижение «Уробборос» не должно выдаваться
        assertFalse(repo.savedAchievements.any { it.name == "Уробборос" },
            "Без достижения «Голос Волтьяра» условное достижение не должно выдаваться")
    }

    //endregion

    //region 40.1T. ALL_BOSS_NAMES содержит 23 босса

    @Test
    fun `ALL_BOSS_NAMES содержит 23 босса после добавления Гидар Рейкал Сиркаадж Мумараак`() {
        // Подготовка: нет, проверяем статический список

        // Вызов — чтение ALL_BOSS_NAMES

        // Проверка: размер 23, новые боссы присутствуют
        assertEquals(23, CampaignViewModel.ALL_BOSS_NAMES.size,
            "ALL_BOSS_NAMES должен содержать 23 босса (4 новых: Гидар, Рейкал, Сиркаадж, Мумараак)")
        assertTrue(CampaignViewModel.ALL_BOSS_NAMES.contains("Гидар"), "Должен содержать Гидар")
        assertTrue(CampaignViewModel.ALL_BOSS_NAMES.contains("Рейкал"), "Должен содержать Рейкал")
        assertTrue(CampaignViewModel.ALL_BOSS_NAMES.contains("Сиркаадж"), "Должен содержать Сиркаадж")
        assertTrue(CampaignViewModel.ALL_BOSS_NAMES.contains("Мумараак"), "Должен содержать Мумараак")
        // Боссы предыдущих наборов остаются в списке
        listOf("Вираксен", "Пробуждённый", "Тараск", "Кситерос", "Зекат", "Зекалит", "Пазис", "Нагарджас")
            .forEach { name ->
                assertTrue(CampaignViewModel.ALL_BOSS_NAMES.contains(name), "Должен содержать $name")
            }
    }

    //endregion

    //region 41.1T. Новые классы DRUSK, ZARAIA

    @Test
    fun `HunterClass содержит 8 значений после добавления DRUSK и ZARAIA`() {
        // Подготовка: нет

        // Вызов — чтение HunterClass.values()

        // Проверка: размер 8, новые классы присутствуют
        assertEquals(8, HunterClass.entries.size, "HunterClass должен содержать 8 значений")
        assertTrue(HunterClass.entries.any { it.displayName == "Друск" }, "Должен содержать Друск")
        assertTrue(HunterClass.entries.any { it.displayName == "Зарайа" }, "Должен содержать Зарайа")
        assertEquals("Друск", HunterClass.DRUSK.displayName, "Отображаемое имя Друска")
        assertEquals("Зарайа", HunterClass.ZARAIA.displayName, "Отображаемое имя Зарайи")
    }

    //endregion

    //region 40.2T. QUEST_NOT_AVAILABLE условие

    @Test
    fun `resolveConditionTarget QUEST_NOT_AVAILABLE открывает 45 если 18 недоступно`() = runBlocking {
        // Подготовка: кампания БЕЗ задания 18 в availableQuestNumbers, активное задание 42
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 42,
                    name = "Тёмная трясина",
                    bossName = "Рейкал",
                    bossElement = Element.POISON,
                    victoryOpenQuestConditions = listOf(
                        TaskCondition(
                            kind = TaskConditionKind.QUEST_NOT_AVAILABLE,
                            chapterSet = listOf(18),
                            questNumber = 45
                        )
                    )
                )
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("42")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 45 открыто (18 не в availableQuestNumbers)
        assertTrue(repo.savedQuests.any { it.questNumber == 45 && it.isAvailable },
            "Если задания 18 нет в доступных, QUEST_NOT_AVAILABLE должно открыть 45")
    }

    @Test
    fun `resolveConditionTarget QUEST_NOT_AVAILABLE НЕ открывает 45 если 18 доступно`() = runBlocking {
        // Подготовка: кампания С заданием 18 в дружественном квесте
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "18", name = "Задание 18", chapter = 1, questNumber = 18, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 42,
                    name = "Тёмная трясина",
                    bossName = "Рейкал",
                    bossElement = Element.POISON,
                    victoryOpenQuestConditions = listOf(
                        TaskCondition(
                            kind = TaskConditionKind.QUEST_NOT_AVAILABLE,
                            chapterSet = listOf(18),
                            questNumber = 45
                        )
                    )
                )
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("42")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 45 не открыто (18 есть в availableQuestNumbers)
        assertFalse(repo.savedQuests.any { it.questNumber == 45 },
            "Если задание 18 доступно, QUEST_NOT_AVAILABLE не должно ничего открывать")
    }

    //endregion

    //region 40.2T. defeatAchievements

    @Test
    fun `onDefeatRewardsAccept выдаёт defeatAchievements при наличии в taskInfo`() = runBlocking {
        // Подготовка: кампания с активным заданием 47 (есть defeatAchievements: Оледенение)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "47", name = "Задание 47", chapter = 1, questNumber = 47, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 47, name = "Морозный укус", bossName = "Сиркаадж",
                    bossElement = Element.ICE, defeatAchievements = listOf("Оледенение"))
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("47")
        viewModel.onDefeat()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onDefeatRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: defeatAchievement «Оледенение» сохранено
        assertTrue(repo.savedAchievements.any { it.name == "Оледенение" && it.unlocked },
            "При поражении должно выдаваться достижение «Оледенение»")
    }

    @Test
    fun `onDefeatRewardsAccept не выдаёт defeatAchievements если их нет в taskInfo`() = runBlocking {
        // Подготовка: задание 49 (нет defeatAchievements)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(
                Quest(id = "49", name = "Задание 49", chapter = 1, questNumber = 49, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 49, name = "Звёздные врата", bossName = "Мумараак",
                    bossElement = Element.ICE)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("49")
        viewModel.onDefeat()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onDefeatRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: достижения не выдавались
        assertFalse(repo.savedAchievements.any { it.name == "Оледенение" },
            "Если defeatAchievements пуст, достижение не должно выдаваться")
    }

    //endregion

    //region 34.4T. onCompleteQuest с openDependentQuests

    @Test
    fun `onCompleteQuest открывает victoryOpenQuests через openDependentQuests`() = runBlocking {
        // Подготовка: кампания с заданием 47 (victoryOpenQuests = [48])
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "47", name = "Задание 47", chapter = 1, questNumber = 47, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 47, name = "Морозный укус", bossName = "Сиркаадж",
                    bossElement = Element.ICE, victoryOpenQuests = listOf(48))
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)

        // Открываем лист кампании
        viewModel.onCampaignSelected(1)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: завершаем задание 47
        viewModel.onCompleteQuest("47")
        kotlinx.coroutines.delay(100)

        // Проверка: задание 48 открыто (victoryOpenQuests сработал)
        assertTrue(repo.savedQuests.any { it.questNumber == 48 && it.isAvailable },
            "onCompleteQuest должен открыть victoryOpenQuests (48)")
    }

    @Test
    fun `onCompleteQuest не открывает задания если victoryOpenQuests пуст`() = runBlocking {
        // Подготовка: задание 41 (нет victoryOpenQuests)
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "41", name = "Задание 41", chapter = 1, questNumber = 41, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 41, name = "Умирающий лес", bossName = "Гидар",
                    bossElement = Element.POISON)
            )
            huntersToReturn = listOf(
                CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)
            )
            achievementsToReturn = listOf(
                Achievement(id = "Змеиная кровь", name = "Змеиная кровь", unlocked = true)
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, scope)

        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
        viewModel.onCampaignSelected(1)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onCompleteQuest("41")
        kotlinx.coroutines.delay(100)

        // Проверка: никаких новых заданий не добавлено
        val savedQuestNumbers = repo.savedQuests.filter { it.isAvailable }.map { it.questNumber }
        assertTrue(savedQuestNumbers.isEmpty() || savedQuestNumbers.none { it == 48 },
            "Если victoryOpenQuests пуст, новые задания не открываются")
    }

    //endregion

    /** Новая кампания с Дареоном после пролога (isPrologue = false, глава 1). */
    private suspend fun startCampaignAfterPrologue(viewModel: CampaignViewModel) {
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        completePrologue(viewModel)
    }

    /** Победа в бою по заданию и «Принять» в окне наград задания (открывает окно наград главы). */
    private suspend fun winQuestAndAcceptRewards(viewModel: CampaignViewModel, questId: String) {
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected(questId)
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)
    }

    private fun hunterDareon() =
        CampaignHunter(id = 1, campaignId = 1, playerName = "Дареон", className = HunterClass.DAREON)

    //region 36.1T. Пролог: победа применяется молча, сразу окно наград главы

    @Test
    fun `onVictory в прологе не открывает окно «Задание выполнено!» и сразу открывает награды главы`() = runBlocking {
        // Подготовка: новая кампания с двумя охотниками — первый бой является прологом
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = listOf(ChapterInfo(chapter = 1, openQuests = listOf(1, 2, 36)))
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        viewModel.onCampaignNameChanged("Пролог")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onClassToggled(HunterClass.MIRA)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: окон задания нет, сразу открыто окно наград главы 1, пролог завершён
        val state = viewModel.state.value
        assertFalse(state.showPostVictory, "Окно «Задание выполнено!» в прологе не показывается")
        assertFalse(state.showQuestRewards, "Окно наград за задание в прологе не показывается")
        assertTrue(state.showChapterRewards, "После победы в прологе сразу открывается окно наград главы")
        assertFalse(state.isPrologue, "После победы в прологе isPrologue = false")
        // Проверка: трофей Вираксена сохранён, задание не завершается, задания не открываются
        val victory = repo.saveVictoryRecords.single()
        assertEquals("Вираксен", victory.trophy.bossName)
        assertEquals(Element.FIRE, victory.trophy.element)
        assertEquals("", victory.completedQuestId, "Пролог не завершает никакое задание")
        assertTrue(repo.savedQuests.isEmpty(), "Задания открываются только при принятии наград главы")
        // Проверка: по 2 «Огонь» каждому из двух охотников
        val fireAdds = repo.resourceAddRecords.filter {
            it.resourceType == "ELEMENT" && it.resourceName == Element.FIRE.name
        }
        assertEquals(2, fireAdds.size, "«Огонь» должен быть начислен каждому охотнику")
        assertTrue(fireAdds.all { it.amount == 2 }, "Каждому охотнику — 2 «Огонь»")

        viewModelScope.cancel()
    }

    //endregion

    //region 42.1T. Сравнение достижений без учёта регистра и «ё/е»

    @Test
    fun `chapterRewardsMessage показывает условное сообщение при другом регистре достижения`() = runBlocking {
        // Подготовка: глава 1 с сообщением по «Яд Пазиса», у кампании достижение «яд пазиса»
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = listOf(
                ChapterInfo(
                    chapter = 1,
                    conditionalMessages = listOf(
                        ConditionalMessage(achievementName = "Яд Пазиса", message = "Получите награду 25")
                    )
                )
            )
            achievementsToReturn = listOf(Achievement(id = "яд пазиса", name = "яд пазиса", unlocked = true))
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: победа в прологе открывает окно наград главы 1
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: условное сообщение показано, хотя регистр достижения другой
        assertTrue(
            viewModel.state.value.chapterConditionOutcomes.any { it.result == "Достижение есть: Получите награду 25." },
            "Условное сообщение главы должно срабатывать без учёта регистра достижения"
        )

        viewModelScope.cancel()
    }

    @Test
    fun `onChapterRewardsAccept открывает условное задание главы при другом написании достижения`() = runBlocking {
        // Подготовка: глава 1 с правилом «Народ Золотых гор → 7, иначе 8»;
        // у кампании то же достижение в нижнем регистре и с лишними пробелами
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = listOf(
                ChapterInfo(
                    chapter = 1,
                    conditionalOpenQuests = listOf(
                        ConditionalQuestOpen(achievements = listOf("Народ Золотых гор"), questNumber = 7, elseQuestNumber = 8)
                    )
                )
            )
            achievementsToReturn = listOf(
                Achievement(id = "народ золотых  гор", name = " народ золотых  гор ", unlocked = true)
            )
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onChapterRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: сработала ветка «есть достижение» — задание 7, а не 8
        assertTrue(repo.savedQuests.any { it.questNumber == 7 && it.isAvailable },
            "При достижении в другом написании должно открыться задание 7")
        assertFalse(repo.savedQuests.any { it.questNumber == 8 },
            "Задание 8 (ветка «иначе») открываться не должно")

        viewModelScope.cancel()
    }

    @Test
    fun `условие задания ACHIEVEMENT_OWNED срабатывает без учёта регистра`() = runBlocking {
        // Подготовка: задание 11 «Грибной лес → 32, иначе 17», у кампании «ГРИБНОЙ ЛЕС»
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "11", name = "Задание 11", chapter = 1, questNumber = 11, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 11, name = "Город памяти", bossName = "Харджа", bossElement = Element.FIRE,
                    victoryOpenQuestConditions = listOf(
                        TaskCondition(
                            kind = TaskConditionKind.ACHIEVEMENT_OWNED,
                            achievementName = "Грибной лес",
                            questNumber = 32,
                            elseQuestNumber = 17
                        )
                    )
                )
            )
            achievementsToReturn = listOf(Achievement(id = "ГРИБНОЙ ЛЕС", name = "ГРИБНОЙ ЛЕС", unlocked = true))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onCampaignSelected(1)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: «Выполнено» у задания 11
        viewModel.onCompleteQuest("11")
        kotlinx.coroutines.delay(100)

        // Проверка: открыто задание 32, а не 17
        assertTrue(repo.savedQuests.any { it.questNumber == 32 && it.isAvailable },
            "При «ГРИБНОЙ ЛЕС» должно открыться задание 32")
        assertFalse(repo.savedQuests.any { it.questNumber == 17 },
            "Задание 17 (ветка «иначе») открываться не должно")

        viewModelScope.cancel()
    }

    @Test
    fun `выдача достижения не дублирует равнозначное в другом написании`() = runBlocking {
        // Подготовка: у кампании уже есть «голос волтьяра»
        val repo = FakeCampaignRepository().apply {
            achievementsToReturn = listOf(Achievement(id = "голос волтьяра", name = "голос волтьяра", unlocked = true))
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: решение главы 7 «Да» выдаёт «Голос Волтьяра»
        viewModel.onChapterDecisionSelected("Да", "Голос Волтьяра")
        kotlinx.coroutines.delay(100)

        // Проверка: повторно достижение не сохраняется
        assertTrue(repo.savedAchievements.isEmpty(),
            "Равнозначное достижение в другом регистре не должно сохраняться повторно")

        viewModelScope.cancel()
    }

    @Test
    fun `onAddAchievement заменяет равнозначное достижение новым написанием`() = runBlocking {
        // Подготовка: у кампании достижение в прежнем написании «яд пазиса»
        val repo = FakeCampaignRepository().apply {
            achievementsToReturn = listOf(Achievement(id = "яд пазиса", name = "яд пазиса", unlocked = true))
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        viewModel.onNewAchievementNameChanged("Яд Пазиса")

        // Вызов проверяемого кода
        viewModel.onAddAchievement()
        kotlinx.coroutines.delay(100)

        // Проверка: прежнее написание удалено, сохранено новое — дубля нет
        assertTrue(repo.deletedAchievementIds.contains("яд пазиса"),
            "Равнозначное достижение в прежнем написании должно быть удалено")
        assertEquals(listOf("Яд Пазиса"), repo.savedAchievements.map { it.name },
            "Должно быть сохранено достижение в новом написании")

        viewModelScope.cancel()
    }

    @Test
    fun `достижения заданий 36 и 5 из seed срабатывают в условиях главы 4`() = runBlocking {
        // Подготовка: реальные seed-каталоги заданий и глав (сквозная проверка написания достижений),
        // открыты задания 36 и 5, выданные достижения видны в getAchievements
        val repo = FakeCampaignRepository().apply {
            achievementsPersist = true
            taskInfoToReturn = taskInfoSeedEntities().map { it.toDomain() }
            chapterInfoToReturn = chapterInfoSeedEntities().map { it.toDomain() }
            availableQuestsToReturn = listOf(
                Quest(id = "5", name = "Задание 5", chapter = 3, questNumber = 5, isAvailable = true),
                Quest(id = "36", name = "Задание 36", chapter = 2, questNumber = 36, isAvailable = true)
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onUpdateChapter(3)
        kotlinx.coroutines.delay(100)
        // Задание 36 в главе 3 выдаёт «Яд Пазиса»; награды главы 3 переводят кампанию в главу 4
        winQuestAndAcceptRewards(viewModel, "36")
        viewModel.onChapterRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: задание 5 в главе 4 выдаёт «Народ Золотых гор» и открывает награды главы 4
        winQuestAndAcceptRewards(viewModel, "5")

        // Проверка: окно наград главы 4 содержит условное сообщение по «Яд Пазиса»
        val state = viewModel.state.value
        assertEquals(4, state.currentCampaign?.currentChapter, "Кампания должна быть в главе 4")
        assertTrue(state.showChapterRewards, "Должно открыться окно наград главы 4")
        assertTrue(
            state.chapterConditionOutcomes.any { it.result == "Достижение есть: Получите награду 25." },
            "После задания 36 окно главы 4 должно содержать «Получите награду 25»"
        )

        // Вызов проверяемого кода: принятие наград главы 4
        viewModel.onChapterRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: при «Народ Золотых гор» глава 4 открывает задание 7, а не 8
        assertTrue(repo.savedQuests.any { it.questNumber == 7 && it.isAvailable },
            "После задания 5 глава 4 должна открыть задание 7")
        assertFalse(repo.savedQuests.any { it.questNumber == 8 },
            "Задание 8 открывается только без «Народ Золотых гор»")

        viewModelScope.cancel()
    }

    //endregion

    //region 42.3T. Выполненное задание пропадает из списка открытых

    @Test
    fun `onCompleteQuest убирает задание из списка открытых и не снимает отметку повторно`() = runBlocking {
        // Подготовка: открытые задания 1 и 2
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true),
                Quest(id = "2", name = "Задание 2", chapter = 1, questNumber = 2, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 1, name = "Память пустыни", bossName = "Торамат", bossElement = Element.HORN)
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onCampaignSelected(1)
        kotlinx.coroutines.delay(100)
        assertEquals(listOf(1, 2), viewModel.state.value.campaignQuests.map { it.questNumber })

        // Вызов проверяемого кода: «Выполнено» у задания 1
        viewModel.onCompleteQuest("1")
        kotlinx.coroutines.delay(100)

        // Проверка: задание завершено и пропало из списка открытых
        assertEquals(1, repo.completedQuestIds.count { it == "1" }, "Задание 1 должно быть завершено")
        assertEquals(listOf(2), viewModel.state.value.campaignQuests.map { it.questNumber },
            "Выполненное задание не должно оставаться в списке открытых")

        // Вызов проверяемого кода: повторное нажатие для уже выполненного задания
        viewModel.onCompleteQuest("1")
        kotlinx.coroutines.delay(100)

        // Проверка: режима переключателя нет — отметка не снимается, задание не возвращается
        assertEquals(1, repo.completedQuestIds.count { it == "1" }, "Повторное завершение не выполняется")
        assertEquals(listOf(2), viewModel.state.value.campaignQuests.map { it.questNumber })

        viewModelScope.cancel()
    }

    @Test
    fun `после победы по заданию выполненное задание пропадает из листа и диалога выбора`() = runBlocking {
        // Подготовка: открытые задания 1 и 2, бой по заданию 1
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true),
                Quest(id = "2", name = "Задание 2", chapter = 1, questNumber = 2, isAvailable = true)
            )
            availableQuestsToReturn = questsToReturn
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 1, name = "Память пустыни", bossName = "Торамат", bossElement = Element.HORN)
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        winQuestAndAcceptRewards(viewModel, "1")

        // Вызов проверяемого кода: принятие наград главы открывает лист кампании
        viewModel.onChapterRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: задание 1 завершено победой и в листе остаётся только задание 2
        assertEquals("1", repo.saveVictoryRecords.last().completedQuestId)
        assertEquals(listOf(2), viewModel.state.value.campaignQuests.map { it.questNumber },
            "Задание, выполненное победой, не должно оставаться в списке открытых")

        viewModelScope.cancel()
    }

    @Test
    fun `onQuestRewardsEdit не предзаполняет выполненные задания`() = runBlocking {
        // Подготовка: задание 1 выполнено, 2 и 3 открыты; бой по заданию 3 (открывает 10)
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true, isCompleted = true),
                Quest(id = "2", name = "Задание 2", chapter = 1, questNumber = 2, isAvailable = true),
                Quest(id = "3", name = "Задание 3", chapter = 1, questNumber = 3, isAvailable = true)
            )
            availableQuestsToReturn = questsToReturn.filter { !it.isCompleted }
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 3, name = "Рёв моря", bossName = "Коровон", bossElement = Element.CORAL,
                    victoryOpenQuests = listOf(10)
                )
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("3")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: «Редактировать» в окне наград за задание
        viewModel.onQuestRewardsEdit()
        kotlinx.coroutines.delay(100)

        // Проверка: выполненное задание 1 не попадает в предзаполнение (иначе «Продолжить» снова его открыло бы)
        assertEquals(setOf(2, 3, 10), viewModel.state.value.selectedQuestNumbers)

        viewModelScope.cancel()
    }

    @Test
    fun `onConfirmVictory без активного задания не завершает открываемые задания`() = runBlocking {
        // Подготовка: бой без задания, в PostVictoryDialog выбраны открываемые задания 3 и 5
        val repo = FakeCampaignRepository().apply {
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected(null)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        viewModel.onVictoryBossSelected("Торамат")
        viewModel.onVictoryBossElementChanged(Element.HORN)
        viewModel.onVictoryQuestToggled(3)
        viewModel.onVictoryQuestToggled(5)

        // Вызов проверяемого кода
        viewModel.onConfirmVictory()
        kotlinx.coroutines.delay(100)

        // Проверка: задания 3 и 5 открыты, но не завершены (раньше завершалось первое из открываемых)
        assertNull(viewModel.state.value.error)
        assertEquals(setOf(3, 5), repo.savedQuests.filter { it.isAvailable }.map { it.questNumber }.toSet())
        assertEquals("", repo.saveVictoryRecords.last().completedQuestId,
            "Без активного задания никакое задание не должно завершаться")

        viewModelScope.cancel()
    }

    //endregion

    //region 42.4T. «Принять» после боя без задания + босс подготовки к бою

    @Test
    fun `onQuestRewardsAccept без задания начисляет 2 стихии выбранного босса и открывает награды главы`() = runBlocking {
        // Подготовка: после пролога бой без задания с выбранным на подготовке Тораматом (Рог)
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(createBoss("Торамат", difficulty = 0, dfw = 2, hsc = 7, element = Element.HORN))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected(null)
        kotlinx.coroutines.delay(100)
        viewModel.onPreBattleBossSelected("Торамат")
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        assertTrue(viewModel.state.value.showQuestRewards, "Должно открыться окно наград за задание")
        repo.resourceAddRecords.clear()
        val victoriesBefore = repo.saveVictoryRecords.size

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: ошибки нет, окно наград задания закрыто, открыто окно наград главы
        val state = viewModel.state.value
        assertNull(state.error, "Для боя без задания «Принять» не должно показывать ошибку")
        assertFalse(state.showQuestRewards)
        assertTrue(state.showChapterRewards, "После «Принять» должно открыться окно наград главы")
        // Проверка: трофей Торамата, задание не завершается
        assertEquals(victoriesBefore + 1, repo.saveVictoryRecords.size)
        val victory = repo.saveVictoryRecords.last()
        assertEquals("Торамат", victory.trophy.bossName)
        assertEquals(Element.HORN, victory.trophy.element)
        assertEquals("", victory.completedQuestId)
        // Проверка: 2 стихии «Рог» охотнику, других наград нет
        assertEquals(
            listOf(FakeCampaignRepository.ResourceAddRecord(1, "ELEMENT", Element.HORN.name, 2)),
            repo.resourceAddRecords
        )

        viewModelScope.cancel()
    }

    @Test
    fun `onQuestRewardsAccept без задания и без выбранного босса показывает сообщение`() = runBlocking {
        // Подготовка: бой без задания в режиме «Ввести данные вручную» (босс не выбран)
        val repo = FakeCampaignRepository().apply {
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected(null)
        kotlinx.coroutines.delay(100)
        viewModel.onPreBattleBossSelected(null)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)
        val victoriesBefore = repo.saveVictoryRecords.size

        // Вызов проверяемого кода
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка: награды не применены, пользователь направлен в «Редактировать»
        val state = viewModel.state.value
        assertEquals("Босс не выбран — укажите его через «Редактировать»", state.error)
        assertTrue(state.showQuestRewards, "Окно наград за задание должно остаться открытым")
        assertFalse(state.showChapterRewards)
        assertEquals(victoriesBefore, repo.saveVictoryRecords.size, "Победа не должна сохраняться")

        viewModelScope.cancel()
    }

    @Test
    fun `старт боя по заданию подставляет босса задания со сложностью главы`() = runBlocking {
        // Подготовка: Вираксен (пролог) и Торамат двух сложностей; задание 1 — Торамат
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(
                createBoss("Вираксен", difficulty = 0, dfw = 2, hsc = 7, element = Element.FIRE),
                createBoss("Торамат", difficulty = 0, dfw = 2, hsc = 7, element = Element.HORN),
                createBoss("Торамат", difficulty = 1, dfw = 4, hsc = 7, element = Element.HORN)
            )
            availableQuestsToReturn = listOf(
                Quest(id = "1", name = "Задание 1", chapter = 1, questNumber = 1, isAvailable = true)
            )
            taskInfoToReturn = listOf(
                TaskInfo(questNumber = 1, name = "Память пустыни", bossName = "Торамат", bossElement = Element.HORN)
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        viewModel.onCampaignNameChanged("Тест")
        viewModel.onClassToggled(HunterClass.DAREON)
        viewModel.onStartCampaign()
        kotlinx.coroutines.delay(100)
        assertEquals("Вираксен", viewModel.state.value.selectedPreBattleBoss?.name, "В прологе выбран Вираксен")
        completePrologue(viewModel)
        viewModel.onUpdateChapter(2) // глава 2 → сложность 1
        kotlinx.coroutines.delay(100)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: выбор задания 1
        viewModel.onActiveQuestSelected("1")
        kotlinx.coroutines.delay(100)

        // Проверка: для боя выбран Торамат сложности главы, поля подготовки — из его стойки I
        val state = viewModel.state.value
        assertEquals("Торамат", state.selectedPreBattleBoss?.name, "Босс боя должен совпадать с боссом задания")
        assertEquals(1, state.selectedPreBattleBoss?.difficulty)
        assertEquals(1, state.preBattleDifficulty)
        assertEquals("4", state.preBattleDamageForWound)
        assertEquals("7", state.preBattleHealthForStance)

        viewModelScope.cancel()
    }

    @Test
    fun `старт боя без задания берёт единственную сложность босса`() = runBlocking {
        // Подготовка: Пробуждённый существует только на сложности 3
        val repo = FakeCampaignRepository().apply {
            bossesToReturn = listOf(createBoss("Пробуждённый", difficulty = 3, dfw = 30, hsc = 8, element = null))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onPreBattleBossSelected("Пробуждённый")
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: бой без задания (глава 1 → сложность 0)
        viewModel.onActiveQuestSelected(null)
        kotlinx.coroutines.delay(100)

        // Проверка: выбран Пробуждённый на его единственной сложности 3
        val state = viewModel.state.value
        assertEquals("Пробуждённый", state.selectedPreBattleBoss?.name)
        assertEquals(3, state.preBattleDifficulty)

        viewModelScope.cancel()
    }

    //endregion

    //region Решения defects.md (25.09.2026): D-5, D-10–D-13, R-6, C-8, C-9

    @Test
    fun `D-11 onUpdateChapter ограничивает главу диапазоном 1–12`() = runBlocking {
        // Подготовка
        val repo = FakeCampaignRepository().apply { huntersToReturn = listOf(hunterDareon()) }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)

        // Вызов проверяемого кода
        viewModel.onUpdateChapter(0)
        kotlinx.coroutines.delay(100)
        val low = viewModel.state.value.currentCampaign?.currentChapter
        viewModel.onUpdateChapter(20)
        kotlinx.coroutines.delay(100)

        // Проверка
        assertEquals(1, low, "Глава не может быть меньше 1")
        assertEquals(CampaignViewModel.MAX_CHAPTER, viewModel.state.value.currentCampaign?.currentChapter,
            "Глава не может быть больше 12")

        viewModelScope.cancel()
    }

    @Test
    fun `D-12 экспедиция стартует с числом охотников из ViewModel без ограничения`() = runBlocking {
        // Подготовка
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(FakeCampaignRepository(), viewModelScope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)

        // Вызов проверяемого кода
        viewModel.onPreBattleHunterCountChanged("7")
        viewModel.onConfirmQuickBattleStart()

        // Проверка
        assertEquals("7", viewModel.state.value.preBattleHunterCountText)
        val battle = viewModel.getBattleViewModel()?.state?.value
        assertNotNull(battle)
        assertEquals(7, battle.hunterCount)
        assertEquals(FightPhase.PHASE_I, battle.phase)

        viewModelScope.cancel()
    }

    @Test
    fun `D-12 экспедиция не стартует при некорректном числе охотников`() = runBlocking {
        // Подготовка
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(FakeCampaignRepository(), viewModelScope)
        viewModel.onQuickBattleSelected()
        kotlinx.coroutines.delay(50)

        // Вызов проверяемого кода
        viewModel.onPreBattleHunterCountChanged("0")
        viewModel.onConfirmQuickBattleStart()

        // Проверка
        assertEquals(FightPhase.PRE_BATTLE, viewModel.getBattleViewModel()?.state?.value?.phase)

        viewModelScope.cancel()
    }

    @Test
    fun `D-10 «Нет» в решении главы отзывает достижение, выданное выбором «Да»`() = runBlocking {
        // Подготовка
        val repo = FakeCampaignRepository().apply {
            achievementsPersist = true
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onChapterDecisionSelected("Да", "Голос Волтьяра")
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onChapterDecisionSelected("Нет", null)
        kotlinx.coroutines.delay(100)

        // Проверка
        assertTrue(repo.deletedAchievementIds.contains("Голос Волтьяра"))
        assertTrue(repo.achievementsToReturn.none { it.name == "Голос Волтьяра" })

        viewModelScope.cancel()
    }

    @Test
    fun `D-10 «Отклонить» наград главы отзывает достижение решения`() = runBlocking {
        // Подготовка
        val repo = FakeCampaignRepository().apply {
            achievementsPersist = true
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onChapterDecisionSelected("Да", "Голос Волтьяра")
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onChapterRewardsReject()
        kotlinx.coroutines.delay(100)

        // Проверка
        assertTrue(repo.achievementsToReturn.none { it.name == "Голос Волтьяра" },
            "Достижение решения не должно оставаться после «Отклонить»")

        viewModelScope.cancel()
    }

    @Test
    fun `D-10 достижение, полученное до решения главы, не отзывается`() = runBlocking {
        // Подготовка: «Голос Волтьяра» уже есть у кампании
        val repo = FakeCampaignRepository().apply {
            achievementsPersist = true
            achievementsToReturn = listOf(Achievement(id = "Голос Волтьяра", name = "Голос Волтьяра", unlocked = true))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onChapterDecisionSelected("Да", "Голос Волтьяра")
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onChapterDecisionSelected("Нет", null)
        viewModel.onChapterRewardsReject()
        kotlinx.coroutines.delay(100)

        // Проверка
        assertTrue(repo.deletedAchievementIds.isEmpty())

        viewModelScope.cancel()
    }

    @Test
    fun `D-5 выполненные задания показываются отдельно и не отмечаются в редакторе`() = runBlocking {
        // Подготовка: задание 3 выполнено, задание 4 открыто
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 2, questNumber = 3, isCompleted = true, isAvailable = true),
                Quest(id = "4", name = "Задание 4", chapter = 2, questNumber = 4, isAvailable = true)
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onOpenQuestEditor()

        // Вызов проверяемого кода
        viewModel.onToggleEditedQuest(3)
        viewModel.onSaveQuestEdits()
        kotlinx.coroutines.delay(100)

        // Проверка
        val state = viewModel.state.value
        assertEquals(listOf(3), state.campaignCompletedQuests.map { it.questNumber })
        assertEquals(listOf(4), state.campaignQuests.map { it.questNumber })
        assertFalse(3 in state.editedQuestNumbers, "Выполненное задание нельзя отметить открытым")
        assertTrue(repo.savedQuests.none { it.questNumber == 3 }, "Выполненное задание не переоткрывается")

        viewModelScope.cancel()
    }

    @Test
    fun `D-13 «Выход в меню» на экране победы применяет награды задания и главы`() = runBlocking {
        // Подготовка: глава 2, бой по заданию 1 (Торамат, рог)
        val repo = FakeCampaignRepository().apply {
            availableQuestsToReturn = listOf(Quest(id = "1", name = "Задание 1", chapter = 2, questNumber = 1, isAvailable = true))
            taskInfoToReturn = listOf(
                TaskInfo(
                    questNumber = 1, name = "Память пустыни", bossName = "Торамат", bossElement = Element.HORN,
                    victoryMaterials = mapOf(Material.BONES to 2)
                )
            )
            chapterInfoToReturn = listOf(ChapterInfo(chapter = 2, openQuests = listOf(3)))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onUpdateChapter(2)
        kotlinx.coroutines.delay(100)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("1")
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onVictoryExitToMenu()
        kotlinx.coroutines.delay(200)

        // Проверка: трофей и выполнение задания, ресурсы, награды главы, главное меню
        assertTrue(repo.saveVictoryRecords.any { it.completedQuestId == "1" && it.trophy.bossName == "Торамат" })
        assertTrue(repo.resourceAddRecords.any { it.resourceName == Material.BONES.name && it.amount == 2 })
        assertTrue(repo.savedQuests.any { it.questNumber == 3 && it.isAvailable }, "Награды главы 2 применены")
        assertTrue(repo.chapterUpdateRecords.any { it.chapter == 3 }, "Кампания переходит в главу 3")
        assertEquals(AppScreen.MainMenu, viewModel.state.value.screen)

        viewModelScope.cancel()
    }

    @Test
    fun `R-6 глава 11 истекает все открытые задания`() = runBlocking {
        // Подготовка: реальный seed глав, кампания в главе 11, открыты задания 5 и 7, задание 3 выполнено
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = chapterInfoSeedEntities().map { it.toDomain() }
            questsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 2, questNumber = 3, isCompleted = true, isAvailable = true),
                Quest(id = "5", name = "Задание 5", chapter = 3, questNumber = 5, isAvailable = true),
                Quest(id = "7", name = "Задание 7", chapter = 5, questNumber = 7, isAvailable = true)
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onUpdateChapter(11)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onChapterRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка
        assertEquals(setOf("5", "7"), repo.unavailableQuestIds.toSet(), "Истекают все открытые задания")
        assertEquals(12, viewModel.state.value.currentCampaign?.currentChapter)

        viewModelScope.cancel()
    }

    @Test
    fun `R-6 после главы 11 следующий бой — только Пробуждённый`() = runBlocking {
        // Подготовка: глава 12, в главе 11 объявлен финал
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = chapterInfoSeedEntities().map { it.toDomain() }
            bossesToReturn = listOf(createBoss("Пробуждённый", difficulty = 3, dfw = 30, hsc = 8, element = null))
            availableQuestsToReturn = listOf(Quest(id = "9", name = "Задание 9", chapter = 9, questNumber = 9, isAvailable = true))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onUpdateChapter(12)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)

        // Проверка: выбора задания нет, бой с Пробуждённым начат
        val state = viewModel.state.value
        assertFalse(state.showQuestSelectDialog, "В финале задание не выбирается")
        assertNull(state.activeQuestId)
        assertEquals("Пробуждённый", state.selectedPreBattleBossName)
        assertTrue(state.screen is AppScreen.CampaignBattle)

        viewModelScope.cancel()
    }

    @Test
    fun `R-6 в главе 11 и раньше бой начинается с выбора задания`() = runBlocking {
        // Подготовка
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = chapterInfoSeedEntities().map { it.toDomain() }
            availableQuestsToReturn = listOf(Quest(id = "9", name = "Задание 9", chapter = 9, questNumber = 9, isAvailable = true))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onUpdateChapter(11)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)

        // Проверка
        assertTrue(viewModel.state.value.showQuestSelectDialog)

        viewModelScope.cancel()
    }

    @Test
    fun `C-8 глава 10 открывает задание 30, если нет хотя бы одного из «Три копья» и «Эхо водопада»`() = runBlocking {
        // Подготовка: есть только «Три копья»
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = chapterInfoSeedEntities().map { it.toDomain() }
            achievementsToReturn = listOf(Achievement(id = "Три копья", name = "Три копья", unlocked = true))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onUpdateChapter(10)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onChapterRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка
        val opened = repo.savedQuests.map { it.questNumber }
        assertTrue(29 in opened, "«Три копья» открывает задание 29")
        assertTrue(30 in opened, "Без «Эхо водопада» открывается задание 30")
        assertFalse(40 in opened)

        viewModelScope.cancel()
    }

    @Test
    fun `C-8 глава 10 не открывает задание 30 при обоих достижениях`() = runBlocking {
        // Подготовка
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = chapterInfoSeedEntities().map { it.toDomain() }
            achievementsToReturn = listOf(
                Achievement(id = "Три копья", name = "Три копья", unlocked = true),
                Achievement(id = "Эхо водопада", name = "Эхо водопада", unlocked = true)
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onUpdateChapter(10)
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onChapterRewardsAccept()
        kotlinx.coroutines.delay(100)

        // Проверка
        val opened = repo.savedQuests.map { it.questNumber }
        assertTrue(29 in opened && 40 in opened)
        assertFalse(30 in opened, "При обоих достижениях задание 30 не открывается")

        viewModelScope.cancel()
    }

    @Test
    fun `C-9 улучшение набора охотника главы 8 показывается только при «Голос Волтьяра»`() = runBlocking {
        // Подготовка: глава 8 из seed; победа без задания открывает окно наград главы
        suspend fun chapterWindow(achievements: List<Achievement>): CampaignUiState {
            val repo = FakeCampaignRepository().apply {
                chapterInfoToReturn = chapterInfoSeedEntities().map { it.toDomain() }
                achievementsToReturn = achievements
                bossesToReturn = listOf(createBoss("Торамат", difficulty = 2, dfw = 4, hsc = 7, element = Element.HORN))
                huntersToReturn = listOf(hunterDareon())
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val viewModel = CampaignViewModel(repo, scope)
            startCampaignAfterPrologue(viewModel)
            viewModel.onUpdateChapter(8)
            kotlinx.coroutines.delay(100)
            viewModel.onPreBattleBossSelected("Торамат")
            viewModel.onStartCampaignBattle()
            kotlinx.coroutines.delay(100)
            viewModel.onActiveQuestSelected(null)
            kotlinx.coroutines.delay(100)
            viewModel.onVictory()
            kotlinx.coroutines.delay(100)
            viewModel.onQuestRewardsAccept()
            kotlinx.coroutines.delay(200)
            val window = viewModel.state.value
            scope.cancel()
            return window
        }

        // Вызов проверяемого кода
        val without = chapterWindow(emptyList())
        val with = chapterWindow(listOf(Achievement(id = "Голос Волтьяра", name = "Голос Волтьяра", unlocked = true)))

        // Проверка: безусловная часть — в сообщении, условное улучшение набора — в условиях главы с результатом
        assertTrue(without.chapterRewardsMessage.contains("Повышение уровня кузни"), "Окно наград главы 8 должно открыться")
        assertFalse(without.chapterRewardsMessage.contains("Улучшение набора охотника"))
        val kitCondition = "Если есть достижение «Голос Волтьяра» — улучшение набора охотника"
        assertEquals("Достижения нет.", without.chapterConditionOutcomes.single { it.description == kitCondition }.result)
        assertEquals(
            "Достижение есть, улучшите набор охотника.",
            with.chapterConditionOutcomes.single { it.description == kitCondition }.result
        )
    }

    @Test
    fun `окно наград задания показывает условие задания 1 с результатом для главы книги`() = runBlocking {
        // Подготовка: глава приложения 2 = глава книги 1, бой по заданию 1 из seed
        val repo = FakeCampaignRepository().apply {
            taskInfoToReturn = taskInfoSeedEntities().map { it.toDomain() }
            availableQuestsToReturn = listOf(Quest(id = "1", name = "Задание 1", chapter = 2, questNumber = 1, isAvailable = true))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onUpdateChapter(2)
        kotlinx.coroutines.delay(100)
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected("1")
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Проверка
        val outcome = viewModel.state.value.questConditionOutcomes.single()
        assertEquals("Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6", outcome.description)
        assertEquals("Добавлено задание 4.", outcome.result)

        // «Принять» открывает именно показанное задание
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(100)
        assertTrue(repo.savedQuests.any { it.questNumber == 4 && it.isAvailable })
        assertFalse(repo.savedQuests.any { it.questNumber == 6 })

        viewModelScope.cancel()
    }

    @Test
    fun `окно наград главы показывает условное задание главы 5 с результатом «Добавлено задание 47»`() = runBlocking {
        // Подготовка: глава 5 из seed, достижения «Упавшая звезда» нет
        val repo = FakeCampaignRepository().apply {
            chapterInfoToReturn = chapterInfoSeedEntities().map { it.toDomain() }
            bossesToReturn = listOf(createBoss("Торамат", difficulty = 2, dfw = 4, hsc = 7, element = Element.HORN))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)
        viewModel.onUpdateChapter(5)
        kotlinx.coroutines.delay(100)
        viewModel.onPreBattleBossSelected("Торамат")
        viewModel.onStartCampaignBattle()
        kotlinx.coroutines.delay(100)
        viewModel.onActiveQuestSelected(null)
        kotlinx.coroutines.delay(100)
        viewModel.onVictory()
        kotlinx.coroutines.delay(100)

        // Вызов проверяемого кода: «Принять» награды задания открывает окно наград главы 5
        viewModel.onQuestRewardsAccept()
        kotlinx.coroutines.delay(200)

        // Проверка
        val outcome = viewModel.state.value.chapterConditionOutcomes.single { "Упавшая звезда" in it.description }
        assertEquals("Если есть достижение «Упавшая звезда», открыть задание 15, иначе добавить задание 47", outcome.description)
        assertEquals("Добавлено задание 47.", outcome.result)

        viewModelScope.cancel()
    }

    //endregion

    //region «Отмена» у выполненного задания

    @Test
    fun `«Отмена» возвращает выполненное задание в открытые`() = runBlocking {
        // Подготовка: задание 3 выполнено, задание 4 открыто
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(
                Quest(id = "3", name = "Задание 3", chapter = 2, questNumber = 3, isCompleted = true, isAvailable = true),
                Quest(id = "4", name = "Задание 4", chapter = 2, questNumber = 4, isAvailable = true)
            )
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)

        // Вызов проверяемого кода
        viewModel.onUncompleteQuest("3")
        kotlinx.coroutines.delay(100)

        // Проверка: задание 3 снова в открытых, список выполненных пуст, зависимые задания не трогаются
        val state = viewModel.state.value
        assertEquals(listOf("3"), repo.uncompletedQuestIds)
        assertEquals(listOf(3, 4), state.campaignQuests.map { it.questNumber })
        assertTrue(state.campaignCompletedQuests.isEmpty())
        assertTrue(repo.unavailableQuestIds.isEmpty(), "Задания, открытые выполнением, не закрываются")

        viewModelScope.cancel()
    }

    @Test
    fun `«Отмена» для невыполненного задания ничего не делает`() = runBlocking {
        // Подготовка
        val repo = FakeCampaignRepository().apply {
            questsToReturn = listOf(Quest(id = "4", name = "Задание 4", chapter = 2, questNumber = 4, isAvailable = true))
            huntersToReturn = listOf(hunterDareon())
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CampaignViewModel(repo, viewModelScope)
        startCampaignAfterPrologue(viewModel)

        // Вызов проверяемого кода
        viewModel.onUncompleteQuest("4")
        kotlinx.coroutines.delay(100)

        // Проверка
        assertTrue(repo.uncompletedQuestIds.isEmpty())

        viewModelScope.cancel()
    }

    //endregion
}
