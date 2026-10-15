package com.primalapp.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.primalapp.campaign.CampaignState
import com.primalapp.database.PlatformContext
import com.primalapp.database.PrimalDatabase
import com.primalapp.database.entity.CacheBlobEntity
import com.primalapp.database.entity.CampaignStoreEntity
import com.primalapp.sync.AccountInfo
import com.primalapp.sync.AppCore
import com.primalapp.sync.CampaignAccess
import com.primalapp.sync.ConnectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Поделиться» и «Ссылка на сайт» листа кампании: кнопки есть всегда, без входа — предложение войти. */
@RunWith(RobolectricTestRunner::class)
class CampaignSheetShareTest {

    private val server = "http://127.0.0.1:9"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PrimalDatabase::class.java).allowMainThreadQueries().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val core = AppCore(PlatformContext(context), db, scope)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** Приложение готово: каталог (файл приложения — в кэше), сервер-заглушка, режим и вход. */
    private suspend fun start(mode: ConnectionMode, account: AccountInfo?): AppViewModel {
        db.cacheBlobDao().put(CacheBlobEntity("catalog", null, File("../androidApp/src/main/assets/catalog-bundle.json").readBytes()))
        core.settings.setServer(server)
        core.settings.setMode(mode)
        core.settings.setAccount(account)
        val app = AppViewModel(core, scope)
        withTimeout(5_000) { core.ready.first { it } }
        return app
    }

    private suspend fun campaign(access: CampaignAccess, serverId: Long?, account: String?): Long =
        core.store.insertEntity(CampaignStoreEntity(
            serverId = serverId,
            account = account,
            access = access.name,
            stateJson = core.store.encodeState(CampaignState(name = "Кампания")),
            createdAt = 1,
            updatedAt = 1
        ))

    private suspend fun sheet(app: AppViewModel, localId: Long): CampaignSheetViewModel =
        CampaignSheetViewModel(app, localId, scope).also { viewModel ->
            withTimeout(5_000) { viewModel.campaign.first { it != null } }
        }

    @Test
    fun `без входа «Поделиться» предлагает войти — и в офлайн-режиме`() = runBlocking {
        // Подготовка
        val app = start(ConnectionMode.OFFLINE, account = null)
        val viewModel = sheet(app, campaign(CampaignAccess.LOCAL, serverId = null, account = null))

        // Вызов проверяемого кода
        viewModel.onShareOpened()

        // Проверка
        assertIs<SheetPrompt.Login>(viewModel.state.value.prompt)
        assertNull(viewModel.state.value.share, "Окно ссылки-приглашения без входа не открывается")
    }

    @Test
    fun `«Войти» в подсказке открывает экран входа`() = runBlocking {
        // Подготовка
        val app = start(ConnectionMode.OFFLINE, account = null)
        val viewModel = sheet(app, campaign(CampaignAccess.LOCAL, serverId = null, account = null))
        viewModel.onShareOpened()

        // Вызов проверяемого кода
        viewModel.onLoginRequested()

        // Проверка
        assertEquals(Screen.Login, app.screen)
        assertNull(viewModel.state.value.prompt)
    }

    @Test
    fun `«Ссылка на сайт» кампании на сервере не требует входа`() = runBlocking {
        // Подготовка: копия кампании аккаунта осталась после выхода
        val app = start(ConnectionMode.OFFLINE, account = null)
        val viewModel = sheet(app, campaign(CampaignAccess.OWNER, serverId = 12, account = "$server|u7"))

        // Вызов проверяемого кода
        viewModel.onSiteLink()

        // Проверка
        assertEquals("$server/campaigns/12", viewModel.state.value.shareText)
        assertNull(viewModel.state.value.prompt)
    }

    @Test
    fun `«Ссылка на сайт» кампании только на устройстве без входа предлагает войти`() = runBlocking {
        // Подготовка
        val app = start(ConnectionMode.OFFLINE, account = null)
        val viewModel = sheet(app, campaign(CampaignAccess.LOCAL, serverId = null, account = null))

        // Вызов проверяемого кода
        viewModel.onSiteLink()

        // Проверка
        assertIs<SheetPrompt.Login>(viewModel.state.value.prompt)
        assertNull(viewModel.state.value.shareText)
    }

    @Test
    fun `после входа «Поделиться» кампании на устройстве предлагает загрузить её на сервер`() = runBlocking {
        // Подготовка
        val app = start(ConnectionMode.ONLINE, AccountInfo(server, "USER", 7, "alice", "Алиса", "d1"))
        val viewModel = sheet(app, campaign(CampaignAccess.LOCAL, serverId = null, account = null))

        // Вызов проверяемого кода
        viewModel.onShareOpened()

        // Проверка
        val prompt = assertIs<SheetPrompt.Upload>(viewModel.state.value.prompt)
        assertTrue(prompt.thenShare)
    }

    @Test
    fun `после входа в офлайн-режиме «Поделиться» подсказывает включить онлайн`() = runBlocking {
        // Подготовка
        val app = start(ConnectionMode.OFFLINE, AccountInfo(server, "USER", 7, "alice", "Алиса", "d1"))
        val viewModel = sheet(app, campaign(CampaignAccess.OWNER, serverId = 12, account = "$server|u7"))
        val messages = mutableListOf<String>()
        scope.launch { app.messages.collect { messages += it } }

        // Вызов проверяемого кода
        viewModel.onShareOpened()

        // Проверка
        assertNull(viewModel.state.value.prompt)
        assertEquals(listOf("Поделиться можно в онлайн-режиме — включите его в настройках."), messages)
    }
}
