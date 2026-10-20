package com.primalapp.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.primalapp.database.PlatformContext
import com.primalapp.database.PrimalDatabase
import com.primalapp.database.entity.CacheBlobEntity
import com.primalapp.model.campaign.HunterClass
import com.primalapp.sync.AppCore
import com.primalapp.sync.ConnectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Основной персонаж (web qa № 150): выбран по умолчанию в листе, кузнице и лаборатории; флажок на листе. */
@RunWith(RobolectricTestRunner::class)
class MainHunterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PrimalDatabase::class.java).allowMainThreadQueries().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val core = AppCore(PlatformContext(context), db, scope)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** Офлайн-приложение с кампанией на устройстве: Дареон — Алиса, Мира — Вадим, Торег — Вера. */
    private suspend fun start(main: HunterClass?): Pair<AppViewModel, Long> {
        db.cacheBlobDao().put(CacheBlobEntity("catalog", null, File("../androidApp/src/main/assets/catalog-bundle.json").readBytes()))
        core.settings.setMode(ConnectionMode.OFFLINE)
        val app = AppViewModel(core, scope)
        withTimeout(5_000) { core.ready.first { it } }
        val state = core.store.rules.create("Общая",
            listOf(HunterClass.DAREON to "Алиса", HunterClass.MIRA to "Вадим", HunterClass.TOREG to "Вера"),
            core.store.context(null, null))
        val localId = core.store.create(state, upload = false, account = null)
        if (main != null) core.store.setMainHunter(localId, main)
        return app to localId
    }

    @Test
    fun `лист — основной персонаж выбран по умолчанию`() = runBlocking {
        // Подготовка
        val (app, localId) = start(HunterClass.MIRA)

        // Вызов проверяемого кода
        val viewModel = CampaignSheetViewModel(app, localId, scope)
        val state = withTimeout(5_000) { viewModel.state.first { it.loaded && it.hunters.isNotEmpty() } }

        // Проверка
        assertEquals(HunterClass.MIRA, state.mainHunter)
        assertEquals(HunterClass.MIRA, state.hunter?.hunterClass)
        assertTrue(state.isMainHunter)
    }

    @Test
    fun `без основного персонажа выбран первый, а флажок делает выбранного основным и снимает выбор`() = runBlocking {
        // Подготовка
        val (app, localId) = start(null)
        val viewModel = CampaignSheetViewModel(app, localId, scope)
        val initial = withTimeout(5_000) { viewModel.state.first { it.loaded && it.hunters.isNotEmpty() } }
        assertEquals(HunterClass.DAREON, initial.hunter?.hunterClass)
        assertFalse(initial.isMainHunter)

        // Вызов проверяемого кода
        viewModel.onHunterSelected(HunterClass.TOREG)
        viewModel.onMainHunterToggled(true)

        // Проверка
        withTimeout(5_000) { viewModel.state.first { it.mainHunter == HunterClass.TOREG } }
        assertEquals(HunterClass.TOREG, core.store.get(localId)!!.mainHunter)
        assertTrue(viewModel.state.value.isMainHunter)

        // Вызов проверяемого кода: снять выбор — выбранный охотник остаётся
        viewModel.onMainHunterToggled(false)

        // Проверка
        val cleared = withTimeout(5_000) { viewModel.state.first { it.mainHunter == null } }
        assertNull(core.store.get(localId)!!.mainHunter)
        assertEquals(HunterClass.TOREG, cleared.hunter?.hunterClass)
    }

    @Test
    fun `кузница и лаборатория — основной персонаж выбран по умолчанию`() = runBlocking {
        // Подготовка
        val (app, localId) = start(HunterClass.TOREG)

        // Вызов проверяемого кода
        val forge = ForgeViewModel(app, localId, scope)
        val lab = LabViewModel(app, localId, scope)

        // Проверка
        val forgeState = withTimeout(5_000) { forge.state.first { it.hunters.isNotEmpty() } }
        val labState = withTimeout(5_000) { lab.state.first { it.hunters.isNotEmpty() } }
        assertEquals(HunterClass.TOREG, forgeState.selectedHunter)
        assertEquals(HunterClass.TOREG, forgeState.mainHunter)
        assertEquals(HunterClass.TOREG, labState.selectedHunter)
        assertEquals(HunterClass.TOREG, labState.mainHunter)
    }
}
