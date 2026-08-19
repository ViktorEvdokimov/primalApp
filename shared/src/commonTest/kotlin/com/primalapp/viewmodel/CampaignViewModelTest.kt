package com.primalapp.viewmodel

import com.primalapp.model.campaign.Achievement
import com.primalapp.model.campaign.Boss
import com.primalapp.model.campaign.BossStance
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
import com.primalapp.domain.ExchangeResult
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
        private val campaigns = mutableMapOf<Long, Campaign>()
        private var nextId = 1L

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
        override suspend fun getHunters(campaignId: Long): List<CampaignHunter> = emptyList()
        override suspend fun addHunters(campaignId: Long, hunters: List<CampaignHunter>) {}
        override suspend fun getTrophies(campaignId: Long): List<Trophy> = emptyList()
        override suspend fun saveTrophy(campaignId: Long, trophy: Trophy) {}
        override suspend fun getCompletedQuests(campaignId: Long): List<Quest> = emptyList()
        override suspend fun getQuests(campaignId: Long): List<Quest> = emptyList()
        override suspend fun saveQuest(campaignId: Long, quest: Quest) {}
        override suspend fun completeQuest(campaignId: Long, questId: String) {}
        override suspend fun getAvailableQuests(campaignId: Long): List<Quest> = emptyList()
        override suspend fun getAllBosses(): List<Boss> = bossesToReturn
        var bossesToReturn: List<Boss> = emptyList()
        override suspend fun saveVictory(campaignId: Long, trophy: Trophy, completedQuestId: String, nextQuestId: String?) {}
        override suspend fun getSkills(hunterId: Long): List<SkillNode> = emptyList()
        override suspend fun unlockSkill(hunterId: Long, branch: SkillBranch, tier: Int) {}
        override suspend fun getAvailableSkillBranches(hunterId: Long): List<SkillBranch> = emptyList()
        override suspend fun getMaterials(hunterId: Long): Map<Material, Int> = emptyMap()
        override suspend fun getPlants(hunterId: Long): Map<Plant, Int> = emptyMap()
        override suspend fun getElements(hunterId: Long): Map<Element, Int> = emptyMap()
        override suspend fun updateResource(hunterId: Long, resourceType: ResourceType, resourceName: String, quantity: Int) {}
        override suspend fun addResource(hunterId: Long, resourceType: ResourceType, resourceName: String, amount: Int) {
            addResourceCalls.add(resourceName)
        }
        val addResourceCalls = mutableListOf<String>()
        override suspend fun getHuntersWithResource(campaignId: Long, resourceName: String, resourceType: ResourceType): List<CampaignHunter> = emptyList()
        override suspend fun exchangeResources(
            fromHunterId: Long, toHunterId: Long,
            fromResources: List<Pair<String, Int>>,
            toResources: List<Pair<String, Int>>,
            resourceType: ResourceType
        ): ExchangeResult = ExchangeResult.Valid()
        override suspend fun advanceChapter(campaignId: Long) {}
        override suspend fun updateChapter(campaignId: Long, chapter: Int) {}
        override suspend fun getForgeLevel(campaignId: Long): Int = 1
        override suspend fun getLabLevel(campaignId: Long): Int = 1
        override suspend fun getAchievements(campaignId: Long): List<Achievement> = emptyList()
        override suspend fun saveAchievement(campaignId: Long, achievement: Achievement) {}
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

    @Test
    fun `ALL_BOSS_NAMES содержит 19 боссов`() {
        // Подготовка

        // Вызов проверяемого кода
        val bosses = CampaignViewModel.ALL_BOSS_NAMES

        // Проверка
        assertEquals(19, bosses.size, "ALL_BOSS_NAMES должен содержать 19 боссов")
        assertTrue(bosses.contains("Вираксен"), "Должен содержать Вираксен")
        assertTrue(bosses.contains("Пробуждённый"), "Должен содержать Пробуждённый")
        assertTrue(bosses.contains("Тараск"), "Должен содержать Тараск")
        assertTrue(bosses.contains("Кситерос"), "Должен содержать Кситерос")
        assertTrue(bosses.contains("Зекат"), "Должен содержать Зекат")
        assertTrue(bosses.contains("Зекалит"), "Должен содержать Зекалит")
        assertTrue(bosses.contains("Пазис"), "Должен содержать Пазис")
        assertTrue(bosses.contains("Нагарджас"), "Должен содержать Нагарджас")
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
}
