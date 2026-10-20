package com.primalapp.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.primalapp.campaign.BattlePurpose
import com.primalapp.campaign.BattleReport
import com.primalapp.campaign.BattleResult
import com.primalapp.catalog.EquipmentCards
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Описание» в кузнице и лаборатории (web qa № 151, 152): карта снаряжения из файлов игры уровня кузни/лаборатории. */
@RunWith(RobolectricTestRunner::class)
class EquipmentCardsTest {

    private val assets = File("../androidApp/src/main/assets")
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PrimalDatabase::class.java).allowMainThreadQueries().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val core = AppCore(PlatformContext(context), db, scope)
    private val cards = EquipmentCards.parse(File(assets, EquipmentCards.MANIFEST).readText())

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `у каждого предмета кузни и зелья каталога — карты трёх уровней, у каждой карты есть файл`() {
        // Подготовка: коды предметов и зелий из каталога в файлах приложения
        val bundle = Json.parseToJsonElement(File(assets, "catalog-bundle.json").readText()).jsonObject
        val forge = bundle.getValue("forge").jsonArray.flatMap { board ->
            board.jsonObject.getValue("items").jsonArray.map { it.jsonObject }
        }
        val lab = bundle.getValue("lab").jsonArray.map { it.jsonObject }

        // Вызов проверяемого кода и проверка
        (forge + lab).forEach { item ->
            val code = item.getValue("code").jsonPrimitive.content
            val name = item.getValue("name").jsonPrimitive.content
            (1..3).forEach { level ->
                val card = cards.card(code, level)
                assertEquals("$name · $level-й уровень", card?.title, "$code-$level")
                assertTrue(File(assets, card!!.asset).isFile, card.asset)
            }
        }
        assertEquals(108, forge.size)
        assertEquals("cards/FIRE_05-2.webp", cards.card("FIRE_05", 2)?.asset)
        listOf("DUAL_BLADES", "SPEAR", "HELMET", "ARMOR").forEach { code ->
            assertTrue(File(assets, cards.card(code, 1)!!.asset).isFile, "Базовое снаряжение $code")
        }
        assertNull(cards.card("FIRE_05", 4))
    }

    @Test
    fun `кузница — «Описание» у всего снаряжения с картой уровня кузни`() = runBlocking {
        // Подготовка: кампания Кары и Миры после пролога (кузня огня открыта)
        val (app, localId) = campaignAfterPrologue()
        val forge = ForgeViewModel(app, localId, scope)
        withTimeout(5_000) { forge.state.first { it.items.isNotEmpty() } }

        // Вызов проверяемого кода
        forge.onHunterSelected(HunterClass.KARA)
        val wings = forge.state.value.items.first { it.name == "Крылья дракона" }
        forge.onDescriptionRequested(wings)

        // Проверка
        assertEquals("cards/FIRE_05-1.webp", forge.state.value.card?.asset)
        forge.onDescriptionDismissed()
        assertNull(forge.state.value.card)
        forge.onHunterSelected(HunterClass.MIRA)
        assertTrue(forge.state.value.items.all { it.card != null }, "Оружие, шлем, доспех, предметы — у всех есть карта")
        assertEquals("cards/FIRE_09-1.webp", forge.state.value.items.first { it.name == "Чешуйчатый шлем" }.card?.asset)
    }

    @Test
    fun `лаборатория — «Описание» у каждого зелья с картой уровня лаборатории`() = runBlocking {
        // Подготовка
        val (app, localId) = campaignAfterPrologue()
        val lab = LabViewModel(app, localId, scope)
        withTimeout(5_000) { lab.state.first { it.potions.isNotEmpty() } }

        // Вызов проверяемого кода
        val irden = lab.state.value.potions.first { it.name == "Ирден" }
        lab.onDescriptionRequested(irden)

        // Проверка
        val level = lab.state.value.labLevel
        assertEquals("cards/LAB_03-$level.webp", lab.state.value.card?.asset)
        assertEquals("Ирден · $level-й уровень", lab.state.value.card?.title)
        assertTrue(lab.state.value.potions.all { it.card?.level == level })
        lab.onDescriptionDismissed()
        assertNull(lab.state.value.card)
    }

    /** Кампания Кары и Миры после пролога (кузня огня открыта), список карт — из файлов приложения. */
    private suspend fun campaignAfterPrologue(): Pair<AppViewModel, Long> {
        db.cacheBlobDao().put(CacheBlobEntity("catalog", null, File(assets, "catalog-bundle.json").readBytes()))
        core.settings.setMode(ConnectionMode.OFFLINE)
        val app = AppViewModel(core, scope)
        withTimeout(5_000) { core.ready.first { it } }
        core.equipmentCards = cards
        val rules = core.store.rules
        val ctx = core.store.context(null, null)
        val start = rules.create("Карты", listOf(HunterClass.KARA to "Кара", HunterClass.MIRA to "Мира"), ctx)
        val prologue = BattleReport("p", BattlePurpose.PROLOGUE, null, null, 0, 0, 0, BattleResult.VICTORY, null, 3, 1, 2)
        val chapterOne = rules.acceptTransition(rules.submitResult(start, prologue, true, null, emptyList(), ctx), emptyMap(), ctx)
        return app to core.store.create(chapterOne, upload = false, account = null)
    }
}
