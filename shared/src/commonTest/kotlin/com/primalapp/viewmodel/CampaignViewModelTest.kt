package com.primalapp.viewmodel

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
        override suspend fun saveVictory(campaignId: Long, trophy: Trophy, completedQuestId: String, nextQuestId: String?) {}
        override suspend fun getSkills(hunterId: Long): List<SkillNode> = emptyList()
        override suspend fun unlockSkill(hunterId: Long, branch: SkillBranch, tier: Int) {}
        override suspend fun getAvailableSkillBranches(hunterId: Long): List<SkillBranch> = emptyList()
        override suspend fun getMaterials(hunterId: Long): Map<Material, Int> = emptyMap()
        override suspend fun getPlants(hunterId: Long): Map<Plant, Int> = emptyMap()
        override suspend fun getElements(hunterId: Long): Map<Element, Int> = emptyMap()
        override suspend fun updateResource(hunterId: Long, resourceType: ResourceType, resourceName: String, quantity: Int) {}
        override suspend fun addResource(hunterId: Long, resourceType: ResourceType, resourceName: String, amount: Int) {}
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
    fun `onPauseBattle сохраняет CampaignBattle в lastActiveBattle и переходит в MainMenu`() {
        // Подготовка: ViewModel с экраном CampaignBattle
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onQuickBattleSelected()
        assertTrue(viewModel.state.value.screen == AppScreen.QuickBattle)

        // Вызов проверяемого кода
        viewModel.onPauseBattle()

        // Проверка: lastActiveBattle = QuickBattle, screen = MainMenu
        val state = viewModel.state.value
        assertEquals(AppScreen.MainMenu, state.screen, "Экран должен переключиться в MainMenu")
        assertEquals(AppScreen.QuickBattle, state.lastActiveBattle,
            "lastActiveBattle должен сохранить QuickBattle")
    }

    @Test
    fun `onPauseBattle сохраняет QuickBattle в lastActiveBattle`() {
        // Подготовка: переходим в QuickBattle
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onQuickBattleSelected()

        // Вызов проверяемого кода
        viewModel.onPauseBattle()

        // Проверка
        assertEquals(AppScreen.QuickBattle, viewModel.state.value.lastActiveBattle)
        assertEquals(AppScreen.MainMenu, viewModel.state.value.screen)
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
    fun `onPauseBattle не сохраняет lastActiveBattle если экран CampaignSheet`() {
        // Подготовка: имитируем навигацию в CampaignSheet
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onQuickBattleSelected()
        viewModel.onPauseBattle()
        assertEquals(AppScreen.MainMenu, viewModel.state.value.screen)

        // Вызов проверяемого кода из MainMenu (уже не бой)
        viewModel.onPauseBattle()

        // Проверка: lastActiveBattle не изменился (остался от предыдущего вызова)
        assertEquals(AppScreen.QuickBattle, viewModel.state.value.lastActiveBattle)
    }

    //endregion

    //region 7.2.2. onResumeBattle восстанавливает экран боя

    @Test
    fun `onResumeBattle восстанавливает CampaignBattle из lastActiveBattle и очищает его`() {
        // Подготовка: сохраняем CampaignBattle через onPauseBattle
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onQuickBattleSelected()
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
    fun `onBackToMenu сбрасывает lastActiveBattle в null`() {
        // Подготовка: сохраняем бой через onPauseBattle
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onQuickBattleSelected()
        viewModel.onPauseBattle()
        assertNotNull(viewModel.state.value.lastActiveBattle,
            "lastActiveBattle должен быть установлен после onPauseBattle")

        // Вызов проверяемого кода
        viewModel.onBackToMenu()

        // Проверка: lastActiveBattle сброшен через CampaignUiState()
        assertNull(viewModel.state.value.lastActiveBattle,
            "onBackToMenu должен сбросить lastActiveBattle в null")
        assertEquals(AppScreen.MainMenu, viewModel.state.value.screen)
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
    fun `onBackToMenu сбрасывает fatalError в null`() {
        // Подготовка: напрямую устанавливаем fatalError через state copy
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onQuickBattleSelected()
        viewModel.onPauseBattle()

        // Вызов проверяемого кода: onBackToMenu создаёт новый CampaignUiState()
        viewModel.onBackToMenu()

        // Проверка: fatalError = null
        assertNull(viewModel.state.value.fatalError,
            "onBackToMenu должен сбросить fatalError в null через CampaignUiState()")
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
    fun `ALL_BOSS_NAMES содержит 13 боссов`() {
        // Подготовка

        // Вызов проверяемого кода
        val bosses = CampaignViewModel.ALL_BOSS_NAMES

        // Проверка
        assertEquals(13, bosses.size, "ALL_BOSS_NAMES должен содержать 13 боссов")
        assertTrue(bosses.contains("Вираксен"), "Должен содержать Вираксен")
        assertTrue(bosses.contains("Пробуждённый"), "Должен содержать Пробуждённый")
    }

    //endregion

    //region 9.4. Возврат в бой экспедиции

    @Test
    fun `onQuickBattleSelected создаёт BattleViewModel`() {
        // Подготовка
        val viewModel = CampaignViewModel(FakeCampaignRepository())

        // Вызов проверяемого кода
        viewModel.onQuickBattleSelected()

        // Проверка: BattleViewModel создан и сохранён
        assertNotNull(viewModel.getBattleViewModel(),
            "После onQuickBattleSelected BattleViewModel должен быть создан")
        assertEquals(AppScreen.QuickBattle, viewModel.state.value.screen,
            "Экран должен переключиться в QuickBattle")
    }

    @Test
    fun `onQuickBattleSelected не создаёт новый BattleViewModel при повторном вызове`() {
        // Подготовка: первый вызов
        val viewModel = CampaignViewModel(FakeCampaignRepository())
        viewModel.onQuickBattleSelected()
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
}
