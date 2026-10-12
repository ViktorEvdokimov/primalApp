package com.primalapp.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.primalapp.campaign.Actor
import com.primalapp.campaign.ActorKind
import com.primalapp.campaign.BattlePurpose
import com.primalapp.campaign.BattleReport
import com.primalapp.campaign.BattleResult
import com.primalapp.campaign.CampaignOp
import com.primalapp.campaign.CampaignRules
import com.primalapp.campaign.CampaignState
import com.primalapp.campaign.RuleContext
import com.primalapp.catalog.TestCatalog
import com.primalapp.database.PlatformContext
import com.primalapp.database.PrimalDatabase
import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.net.CampaignSnapshotDto
import com.primalapp.net.CampaignSummaryDto
import com.primalapp.net.CookieJar
import com.primalapp.net.MeDto
import com.primalapp.net.PrimalApi
import com.primalapp.net.SnapshotStateDto
import com.primalapp.net.SnapshotUpdateDto
import com.primalapp.net.UserDto
import com.primalapp.net.DeviceDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Синхронизация с сервером на подставном сервере (Ktor MockEngine): копия кампании с сервера и отправка правок
 * с повтором журнала при конфликте версий.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PrimalDatabase::class.java).allowMainThreadQueries().build()
    private val catalog = TestCatalog.catalog
    private val rules = CampaignRules(catalog)
    private val alice = Actor(ActorKind.USER, "Алиса")
    private var keys = 0
    private val ctx = RuleContext(Expansion.entries.toSet(), 1_000, alice) { "k${keys++}" }

    /** Подставной сервер: одна кампания с версией и состоянием. */
    private var serverVersion = 1
    private lateinit var serverState: SnapshotStateDto
    private val puts = mutableListOf<Int>()

    private val settings = SettingsStore(db.keyValueDao())
    private val store = CampaignStore(db.campaignStoreDao(), { catalog }, { Expansion.entries.toSet() })
    private lateinit var sync: SyncEngine

    private fun snapshot() = CampaignSnapshotDto(12, serverVersion, "OWNER", "Алиса", Expansion.entries.map { it.name }, null, null, serverState)

    private fun MockRequestHandleScope.handle(request: HttpRequestData) = when {
        request.url.encodedPath.endsWith("/auth/me") -> respondJson(json.encodeToString(MeDto.serializer(),
            MeDto("USER", UserDto(7, "alice", "Алиса"), DeviceDto("d1"))))
        request.url.encodedPath.endsWith("/auth/csrf") ->
            respond("", HttpStatusCode.NoContent, headersOf(HttpHeaders.SetCookie, "XSRF-TOKEN=t; Path=/"))
        request.url.encodedPath.endsWith("/campaigns") -> respondJson(json.encodeToString(ListSerializer(CampaignSummaryDto.serializer()),
            listOf(CampaignSummaryDto(12, serverState.name, serverState.chapter, serverState.status, "OWNER", "Алиса"))))
        request.url.encodedPath.endsWith("/campaigns/12/snapshot") && request.method == HttpMethod.Get ->
            respondJson(json.encodeToString(CampaignSnapshotDto.serializer(), snapshot()))
        request.url.encodedPath.endsWith("/campaigns/12/snapshot") && request.method == HttpMethod.Put -> {
            val body = json.decodeFromString(SnapshotUpdateDto.serializer(), (request.body as TextContent).text)
            puts += body.expectedVersion
            if (body.expectedVersion != serverVersion) {
                respond("""{"status":409,"code":"VERSION_CONFLICT","detail":"Кампания уже изменена"}""", HttpStatusCode.Conflict,
                    headersOf(HttpHeaders.ContentType, "application/problem+json"))
            } else {
                serverVersion++
                serverState = body.state
                respondJson(json.encodeToString(CampaignSnapshotDto.serializer(), snapshot()))
            }
        }
        else -> respond("", HttpStatusCode.NotFound)
    }

    private fun MockRequestHandleScope.respondJson(text: String) =
        respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun setUp(state: CampaignState) = runBlocking {
        serverState = SnapshotMapper.toDto(state)
        settings.setServer("http://test.local")
        settings.setAccount(AccountInfo("http://test.local", "USER", 7, "alice", "Алиса", "d1"))
        val api = PrimalApi(MockEngine { request -> handle(request) }, { settings.current.serverUrl }, CookieJar(emptyList()) {})
        sync = SyncEngine(api, settings, store, CatalogRepository(PlatformContext(context), db.cacheBlobDao()),
            InfoRepository(PlatformContext(context), db.cacheBlobDao()), CoroutineScope(Dispatchers.Unconfined))
    }

    private fun chapterOne(): CampaignState {
        val start = rules.create("Общая", listOf(HunterClass.DAREON to "Алиса", HunterClass.MIRA to "Вадим"), ctx)
        val prologue = BattleReport("p", BattlePurpose.PROLOGUE, null, null, 0, 0, 0, BattleResult.VICTORY, null, 3, 1, 2)
        return rules.acceptTransition(rules.submitResult(start, prologue, true, null, emptyList(), ctx), emptyMap(), ctx)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `кампания аккаунта копируется на устройство`() = runBlocking {
        // Подготовка
        setUp(chapterOne())

        // Вызов проверяемого кода
        sync.sync(references = false)

        // Проверка
        val local = store.all().single()
        assertEquals(12, local.serverId)
        assertEquals(CampaignAccess.OWNER, local.access)
        assertEquals(1, local.baseVersion)
        assertEquals("Общая", local.state.name)
        assertEquals(SyncStatus.Idle, sync.status.value)
    }

    @Test
    fun `правка приложения и правка другого участника складываются при конфликте версий`() = runBlocking {
        // Подготовка: копия на устройстве, затем правка в приложении и параллельная правка на сервере
        setUp(chapterOne())
        sync.sync(references = false)
        val localId = store.all().single().localId
        store.onLocalChange = {}
        store.perform(localId, CampaignOp.AdjustResources(HunterClass.DAREON, mapOf(ResourceCode.BONES to 5), 10, alice))
        val other = rules.adjustResources(SnapshotMapper.toState(serverState), HunterClass.MIRA, mapOf(ResourceCode.ZLATIA to 2))
        serverState = SnapshotMapper.toDto(other)
        serverVersion++

        // Вызов проверяемого кода
        sync.sync(references = false)

        // Проверка: первая отправка — конфликт (версия 1), вторая — поверх свежего снимка (версия 2)
        assertEquals(listOf(1, 2), puts)
        val server = SnapshotMapper.toState(serverState)
        assertEquals(6, server.hunter(HunterClass.DAREON)!!.quantity(ResourceCode.BONES))
        assertEquals(2, server.hunter(HunterClass.MIRA)!!.quantity(ResourceCode.ZLATIA))
        val local = store.all().single()
        assertTrue(local.pendingOps.isEmpty())
        assertEquals(3, local.baseVersion)
        assertEquals(server.hunters.map { it.resources }, local.state.hunters.map { it.resources })
    }
}
