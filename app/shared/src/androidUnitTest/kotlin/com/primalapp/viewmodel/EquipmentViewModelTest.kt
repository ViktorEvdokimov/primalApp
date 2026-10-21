package com.primalapp.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.primalapp.catalog.EquipmentCards
import com.primalapp.database.PlatformContext
import com.primalapp.database.PrimalDatabase
import com.primalapp.database.entity.CacheBlobEntity
import com.primalapp.model.campaign.Element
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

/** «Инфо» → «Экипировка» (web qa № 154): вкладки, фильтры по типу, классу и уровню, карты из файлов приложения. */
@RunWith(RobolectricTestRunner::class)
class EquipmentViewModelTest {

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

    private suspend fun open(): EquipmentViewModel {
        db.cacheBlobDao().put(CacheBlobEntity("catalog", null, File(assets, "catalog-bundle.json").readBytes()))
        core.settings.setMode(ConnectionMode.OFFLINE)
        val app = AppViewModel(core, scope)
        withTimeout(5_000) { core.ready.first { it } }
        core.equipmentCards = EquipmentCards.parse(File(assets, EquipmentCards.MANIFEST).readText())
        val equipment = EquipmentViewModel(app, scope)
        withTimeout(5_000) { equipment.state.first { it.items.isNotEmpty() } }
        return equipment
    }

    @Test
    fun `снаряжение 1-го уровня — базовое, затем кузни по стихиям, как на планшете`() = runBlocking {
        // Вызов проверяемого кода
        val equipment = open()

        // Проверка
        val items = equipment.state.value.items
        assertEquals(10 + 108, items.size)
        assertEquals(listOf("Большой меч", "Большой лук"), items.take(2).map { it.name })
        assertEquals("Основной доспех", items[9].name)
        assertEquals(listOf("Язык пламени", "Лук-испепелитель"), items.drop(10).take(2).map { it.name })
        assertEquals("Базовое · Оружие · большой меч", items[0].caption)
        assertEquals("Огонь · Оружие · большой меч", items[10].caption)
        assertEquals(listOf(null) + Element.entries, items.map { it.element }.distinct(), "Кузни — по стихиям")
        assertEquals("cards/HELMET-1.webp", items.first { it.code == "HELMET" }.card.asset)
        assertEquals(124, core.equipmentCards.codes.size)
    }

    @Test
    fun `класс отбирает только оружие, базового на 2-м уровне нет, зелья — без фильтров типа и класса`() = runBlocking {
        // Подготовка
        val equipment = open()

        // Вызов проверяемого кода
        equipment.onClassSelected(HunterClass.KARA)
        equipment.onTypeSelected(ForgeItemType.WEAPON)

        // Проверка
        assertEquals(listOf("Парные клинки", "Крылья дракона"), equipment.state.value.items.take(2).map { it.name })
        assertEquals(10, equipment.state.value.items.size)
        equipment.onLevelSelected(2)
        assertEquals(9, equipment.state.value.items.size)
        assertTrue(equipment.state.value.showsBasicHint)
        assertEquals("cards/FIRE_05-2.webp", equipment.state.value.items.first().card.asset)
        equipment.onTypeSelected(ForgeItemType.HELMET)
        assertEquals(9, equipment.state.value.items.size, "Шлемы класс не фильтрует")

        // Вызов: зелья
        equipment.onTabSelected(EquipmentTab.POTIONS)
        val potions = equipment.state.value.items
        assertEquals(listOf("Алемор", "Имперум", "Ирден", "Хатрокс", "Эвок", "Видья"), potions.map { it.name })
        assertFalse(equipment.state.value.showsFilters)
        assertEquals("cards/LAB_03-2.webp", potions[2].card.asset)
        equipment.onCardRequested(potions[2])
        assertEquals("Ирден · 2-й уровень", equipment.state.value.card?.title)
        equipment.onCardDismissed()
        assertNull(equipment.state.value.card)
    }
}
