package com.primalapp.viewmodel

import com.primalapp.catalog.Catalog
import com.primalapp.catalog.EquipmentCard
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.ForgeSlot
import com.primalapp.model.campaign.HunterClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Вкладка «Экипировки»: снаряжение (базовое и всех кузниц) или зелья. */
enum class EquipmentTab(val label: String) {
    EQUIPMENT("Снаряжение"), POTIONS("Зелья")
}

/**
 * Карта «Экипировки»: [element] — кузня (у базового снаряжения и зелий `null`), [caption] — источник и вид
 * («Огонь · Шлем», «Базовое · Оружие · большой меч», «Зелье»), [card] — карта выбранного уровня; [weapon] — карта
 * оружия 70×120 мм, остальные — 60×60 мм.
 */
data class EquipmentRow(
    val code: String,
    val name: String,
    val element: Element?,
    val caption: String,
    val card: EquipmentCard,
    val weapon: Boolean = false
)

data class EquipmentState(
    val tab: EquipmentTab = EquipmentTab.EQUIPMENT,
    /** фильтр по типу; `null` — все */
    val typeFilter: ForgeItemType? = null,
    /** фильтр по классу — отбирается только оружие; `null` — все классы */
    val classFilter: HunterClass? = null,
    val level: Int = 1,
    val items: List<EquipmentRow> = emptyList(),
    /** карта, открытая крупно */
    val card: EquipmentCard? = null
) {
    /** У зелий нет ни типа, ни класса — эти фильтры на их вкладке не показываются. */
    val showsFilters: Boolean get() = tab == EquipmentTab.EQUIPMENT

    /** Базовое снаряжение — только 1-го уровня: на 2-м и 3-м его нет. */
    val showsBasicHint: Boolean get() = showsFilters && level > 1
}

/**
 * «Инфо» → «Экипировка» (web qa № 154): все карты из файлов приложения — вкладки «Снаряжение» и «Зелья»; фильтры по
 * типу, классу (только оружие) и уровню, сохраняются при смене вкладки.
 */
class EquipmentViewModel(app: AppViewModel, scope: CoroutineScope) {

    private val core = app.core
    private var catalog: Catalog? = null

    private val _state = MutableStateFlow(EquipmentState())
    val state: StateFlow<EquipmentState> = _state.asStateFlow()

    init {
        scope.launch {
            core.catalog.catalog.collect { catalog ->
                this@EquipmentViewModel.catalog = catalog
                _state.update { rebuild(it) }
            }
        }
    }

    private fun rebuild(state: EquipmentState): EquipmentState {
        val catalog = catalog ?: return state
        val cards = core.equipmentCards
        val rows = when (state.tab) {
            EquipmentTab.POTIONS -> catalog.lab.mapNotNull { potion ->
                cards.card(potion.code, state.level)?.let { EquipmentRow(potion.code, potion.name, null, "Зелье", it) }
            }
            EquipmentTab.EQUIPMENT -> {
                // Базовое снаряжение (стартовый набор) — по картам, в каталоге его нет; затем кузни по стихиям в порядке
                // каталога, внутри кузни — как на её планшете
                val basic = ForgeSlot.entries.mapNotNull { slot -> cards.card(slot.name, 1)?.let { Triple(slot.name, it.name, slot) } }
                    .map { (code, name, slot) -> Item(code, name, slot, null) }
                val forged = catalog.forge.map { Item(it.code, it.name, it.slot, it.element) }
                (basic + forged)
                    .filter { state.typeFilter == null || ForgeItemType.of(it.slot) == state.typeFilter }
                    .filter { state.classFilter == null || it.slot.hunterClass == null || it.slot.hunterClass == state.classFilter }
                    .mapNotNull { item ->
                        val card = cards.card(item.code, state.level) ?: return@mapNotNull null
                        val source = item.element?.displayName ?: "Базовое"
                        EquipmentRow(item.code, item.name, item.element, "$source · ${forgeKind(item.slot)}", card, item.slot.isWeapon)
                    }
            }
        }
        return state.copy(items = rows)
    }

    private data class Item(val code: String, val name: String, val slot: ForgeSlot, val element: Element?)

    fun onTabSelected(tab: EquipmentTab) = _state.update { rebuild(it.copy(tab = tab)) }

    fun onTypeSelected(type: ForgeItemType?) = _state.update { rebuild(it.copy(typeFilter = type)) }

    fun onClassSelected(hunterClass: HunterClass?) = _state.update { rebuild(it.copy(classFilter = hunterClass)) }

    fun onLevelSelected(level: Int) = _state.update { rebuild(it.copy(level = level)) }

    fun onCardRequested(row: EquipmentRow) = _state.update { it.copy(card = row.card) }

    fun onCardDismissed() = _state.update { it.copy(card = null) }
}
