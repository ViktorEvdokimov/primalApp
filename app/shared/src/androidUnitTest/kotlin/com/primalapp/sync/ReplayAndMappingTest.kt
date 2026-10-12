package com.primalapp.sync

import com.primalapp.campaign.Actor
import com.primalapp.campaign.ActorKind
import com.primalapp.campaign.BattlePurpose
import com.primalapp.campaign.BattleReport
import com.primalapp.campaign.BattleResult
import com.primalapp.campaign.BattleStatus
import com.primalapp.campaign.CampaignOp
import com.primalapp.campaign.CampaignRules
import com.primalapp.campaign.CampaignState
import com.primalapp.campaign.CampaignStatus
import com.primalapp.campaign.QuestStatus
import com.primalapp.campaign.RuleContext
import com.primalapp.catalog.TestCatalog
import com.primalapp.database.dao.CampaignStoreDao
import com.primalapp.database.entity.CampaignStoreEntity
import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.ResourceCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Синхронизация: повтор журнала действий поверх свежего снимка сервера (правки разных устройств складываются) и
 * перевод снимка сервера в состояние приложения и обратно.
 */
class ReplayAndMappingTest {

    private val catalog = TestCatalog.catalog
    private val rules = CampaignRules(catalog)
    private var key = 0
    private val alice = Actor(ActorKind.USER, "Алиса")
    private val ctx = RuleContext(Expansion.entries.toSet(), 1_000, alice) { "k${key++}" }
    private val store = CampaignStore(NoDao, { catalog }, { Expansion.entries.toSet() })

    private fun chapterOne(): CampaignState {
        val start = rules.create("Кампания", listOf(HunterClass.DAREON to "Алиса", HunterClass.MIRA to "Вадим"), ctx)
        val prologue = BattleReport("p", BattlePurpose.PROLOGUE, null, null, 0, 0, 0, BattleResult.VICTORY, null, 3, 1, 2)
        return rules.acceptTransition(rules.submitResult(start, prologue, true, null, emptyList(), ctx), emptyMap(), ctx)
    }

    @Test
    fun `правки ресурсов разных устройств складываются`() {
        // Подготовка: на сервере другой участник добавил Мире кровь, в приложении Алиса добавила себе кости
        val base = chapterOne()
        val server = rules.adjustResources(base, HunterClass.MIRA, mapOf(ResourceCode.BLOOD to 2))
        val ops = listOf(CampaignOp.AdjustResources(HunterClass.DAREON, mapOf(ResourceCode.BONES to 3), 10, alice))

        // Вызов проверяемого кода
        val (state, kept, messages) = store.replay(server, ops, null)

        // Проверка
        assertEquals(4, state.hunter(HunterClass.MIRA)!!.quantity(ResourceCode.BLOOD))
        assertEquals(4, state.hunter(HunterClass.DAREON)!!.quantity(ResourceCode.BONES))
        assertEquals(ops, kept)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `победа, опоздавшая к чужой принятой победе, записывается отклонённой`() {
        // Подготовка: оба боя начаты в главе 1; на сервере уже принята чужая победа в задании 2
        val base = chapterOne()
        val mine = BattleReport("mine", BattlePurpose.QUEST, 1, "TORAMAT", 1, 1, base.progressSeq, BattleResult.VICTORY, null, 4, 1, 2)
        val theirs = BattleReport("theirs", BattlePurpose.QUEST, 2, "OZEV", 1, 1, base.progressSeq, BattleResult.VICTORY, null, 4, 1, 2)
        val server = rules.submitResult(base, theirs, true, null, emptyList(),
            ctx.copy(actor = Actor(ActorKind.GUEST, "Вадим")))
        val ops = listOf(CampaignOp.SubmitResult(mine, accept = true, at = 20, actor = alice))

        // Вызов проверяемого кода
        val (state, kept, messages) = store.replay(server, ops, null)

        // Проверка
        assertEquals(BattleStatus.DISMISSED, state.battle("mine")!!.status)
        assertEquals(QuestStatus.OPEN, state.quest(1)!!.status, "Задание 1 не выполнено")
        assertEquals(CampaignStatus.CHAPTER_TRANSITION, state.status)
        assertEquals(1, kept.size)
        assertTrue(!(kept.single() as CampaignOp.SubmitResult).accept)
        assertTrue(messages.single().startsWith("Результат боя не принят: Глава 1 уже завершена: принята победа в бою с боссом «Озев» (результат от: Вадим)"))
    }

    @Test
    fun `недопустимое на свежем снимке действие отбрасывается с сообщением`() {
        // Подготовка: на сервере ресурс уже потрачен
        val base = rules.adjustResources(chapterOne(), HunterClass.DAREON, mapOf(ResourceCode.BONES to -1))
        val ops = listOf(CampaignOp.AdjustResources(HunterClass.DAREON, mapOf(ResourceCode.BONES to -1), 10, alice))

        // Вызов проверяемого кода
        val (_, kept, messages) = store.replay(base, ops, null)

        // Проверка
        assertTrue(kept.isEmpty())
        assertEquals("Изменение не применено: Не хватает ресурсов: Кости.", messages.single())
    }

    @Test
    fun `снимок сервера и состояние приложения переводятся друг в друга без потерь`() {
        // Подготовка
        val state = chapterOne()

        // Вызов проверяемого кода
        val dto = SnapshotMapper.toDto(state)
        val back = SnapshotMapper.toState(dto)

        // Проверка
        assertEquals("ACTIVE", dto.status)
        assertEquals("DAREON", dto.hunters.first().`class`)
        assertEquals(state.copy(hunters = state.hunters.map { hunter -> hunter.copy(items = hunter.items.map { it.copy(key = "") }) },
            achievements = state.achievements.map { it.copy(key = "") }),
            back.copy(hunters = back.hunters.map { hunter -> hunter.copy(items = hunter.items.map { it.copy(key = "") }) },
                achievements = back.achievements.map { it.copy(key = "") }))
        assertEquals("1970-01-01T00:00:00.001Z", dto.battles.single().startedAt)
    }

    /** Хранилище для повтора журнала БД не использует. */
    private object NoDao : CampaignStoreDao {
        override fun observeAll(): Flow<List<CampaignStoreEntity>> = emptyFlow()
        override suspend fun all(): List<CampaignStoreEntity> = emptyList()
        override fun observe(localId: Long): Flow<CampaignStoreEntity?> = emptyFlow()
        override suspend fun get(localId: Long): CampaignStoreEntity? = null
        override suspend fun byServerId(account: String, serverId: Long): CampaignStoreEntity? = null
        override suspend fun insert(entity: CampaignStoreEntity): Long = 0
        override suspend fun update(entity: CampaignStoreEntity) = Unit
        override suspend fun delete(localId: Long) = Unit
    }
}
