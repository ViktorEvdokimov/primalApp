package com.primalapp.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.primalapp.campaign.BattlePurpose
import com.primalapp.campaign.BattleReport
import com.primalapp.campaign.BattleResult
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
import kotlin.test.assertNull

/** Фильтр кузницы по типу предмета (web qa № 153). */
@RunWith(RobolectricTestRunner::class)
class ForgeFilterTest {

    private val assets = File("../androidApp/src/main/assets")
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PrimalDatabase::class.java).allowMainThreadQueries().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val core = AppCore(PlatformContext(context), db, scope)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `фильтр по типу — только выбранный тип, сохраняется при смене охотника, «Все» — весь планшет`() = runBlocking {
        // Подготовка: кампания Кары и Миры после пролога (кузня огня открыта)
        db.cacheBlobDao().put(CacheBlobEntity("catalog", null, File(assets, "catalog-bundle.json").readBytes()))
        core.settings.setMode(ConnectionMode.OFFLINE)
        val app = AppViewModel(core, scope)
        withTimeout(5_000) { core.ready.first { it } }
        val rules = core.store.rules
        val ctx = core.store.context(null, null)
        val start = rules.create("Фильтр", listOf(HunterClass.KARA to "Кара", HunterClass.MIRA to "Мира"), ctx)
        val prologue = BattleReport("p", BattlePurpose.PROLOGUE, null, null, 0, 0, 0, BattleResult.VICTORY, null, 3, 1, 2)
        val chapterOne = rules.acceptTransition(rules.submitResult(start, prologue, true, null, emptyList(), ctx), emptyMap(), ctx)
        val forge = ForgeViewModel(app, core.store.create(chapterOne, upload = false, account = null), scope)
        withTimeout(5_000) { forge.state.first { it.items.isNotEmpty() } }
        forge.onHunterSelected(HunterClass.KARA)
        val names = { forge.state.value.items.map { it.name } }

        // Вызов проверяемого кода
        forge.onTypeSelected(ForgeItemType.HELMET)

        // Проверка
        assertEquals(ForgeItemType.HELMET, forge.state.value.typeFilter)
        assertEquals(listOf("Чешуйчатый шлем"), names())
        forge.onTypeSelected(ForgeItemType.ITEM)
        forge.onHunterSelected(HunterClass.MIRA)
        assertEquals(listOf("Перчатка Волтьяра", "Лавовый щит"), names())
        forge.onTypeSelected(ForgeItemType.WEAPON)
        assertEquals(listOf("Лук-испепелитель"), names())
        forge.onTypeSelected(ForgeItemType.ARMOR)
        assertEquals(listOf("Чешуйчатый доспех"), names())
        forge.onTypeSelected(null)
        assertNull(forge.state.value.typeFilter)
        assertEquals(
            listOf("Лук-испепелитель", "Чешуйчатый шлем", "Чешуйчатый доспех", "Перчатка Волтьяра", "Лавовый щит"),
            names()
        )
    }
}
