package com.primalapp.viewmodel

import com.primalapp.campaign.CampaignOp
import com.primalapp.campaign.HunterState
import com.primalapp.campaign.InventoryItem
import com.primalapp.campaign.ItemKind
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.model.campaign.ResourceType
import com.primalapp.sync.StoredCampaign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Строка цены: ресурс и количество. */
data class CostPart(val code: ResourceCode, val quantity: Int)

fun List<CostPart>.describe(): String = joinToString(", ") { "${it.code.displayName} ${it.quantity}" }

// region Кузница

data class ForgeItemRow(
    val code: String,
    val name: String,
    val kind: String,
    val cost: List<CostPart>,
    val missing: List<CostPart>
) {
    val canCraft: Boolean get() = missing.isEmpty()
}

data class ForgeState(
    val hunters: List<HunterState> = emptyList(),
    val selectedHunter: HunterClass? = null,
    val forgeLevel: Int = 1,
    val openForges: List<Element> = emptyList(),
    val element: Element? = null,
    val items: List<ForgeItemRow> = emptyList(),
    val stock: List<CostPart> = emptyList(),
    val confirm: ForgeItemRow? = null,
    val exchange: ExchangeRequest? = null
) {
    val hunter: HunterState? get() = hunters.firstOrNull { it.hunterClass == selectedHunter }
}

/** Открыть окно «Обменять ресурсы» для охотника: [missing] — чего не хватает (из кузницы и лаборатории). */
data class ExchangeRequest(val hunter: HunterClass, val missing: List<CostPart>, val plantsOnly: Boolean = false)

/**
 * Кузница: планшет открытой кузни текущего уровня; оружие — только класса выбранного охотника. Создание списывает
 * 1 стихию кузни и материи планшета, карта уходит в инвентарь охотника.
 */
class ForgeViewModel(app: AppViewModel, localId: Long, scope: CoroutineScope) : CampaignScopedViewModel(app, localId, scope) {

    private val _state = MutableStateFlow(ForgeState())
    val state: StateFlow<ForgeState> = _state.asStateFlow()

    init {
        observe()
    }

    override fun onCampaign(campaign: StoredCampaign) {
        val state = campaign.state
        val open = rules.openForges(state)
        _state.update { current ->
            val hunter = current.selectedHunter?.takeIf { state.hunter(it) != null } ?: state.squad.firstOrNull()?.hunterClass
            val element = current.element?.takeIf { it in open } ?: open.firstOrNull()
            rebuild(current.copy(hunters = state.squad, selectedHunter = hunter, forgeLevel = state.forgeLevel,
                openForges = open, element = element), campaign)
        }
    }

    private fun rebuild(current: ForgeState, campaign: StoredCampaign? = this.campaign.value): ForgeState {
        val state = campaign?.state ?: return current
        val hunter = current.hunter ?: return current
        val element = current.element
        val items = if (element == null) emptyList() else catalog.forge(element)
            .filter { it.slot.availableTo(hunter.hunterClass) }
            .map { item ->
                val cost = rules.forgeCost(state, item.code).map { CostPart(it.key, it.value) }
                ForgeItemRow(
                    code = item.code,
                    name = item.name,
                    kind = if (item.slot.isWeapon) "Оружие · ${item.slot.displayName.lowercase()}" else item.slot.displayName,
                    cost = cost,
                    missing = cost.mapNotNull { part ->
                        val lack = part.quantity - hunter.quantity(part.code)
                        if (lack > 0) CostPart(part.code, lack) else null
                    }
                )
            }
        val stock = (ResourceCode.elements + ResourceCode.materials).mapNotNull { code ->
            hunter.quantity(code).takeIf { it > 0 }?.let { CostPart(code, it) }
        }
        return current.copy(items = items, stock = stock)
    }

    fun onHunterSelected(hunterClass: HunterClass) = _state.update { rebuild(it.copy(selectedHunter = hunterClass)) }

    fun onElementSelected(element: Element) = _state.update { rebuild(it.copy(element = element)) }

    fun onCraftRequested(row: ForgeItemRow) = _state.update { it.copy(confirm = row) }

    fun onCraftDismissed() = _state.update { it.copy(confirm = null) }

    fun onCraftConfirmed() {
        val row = _state.value.confirm ?: return
        val hunter = _state.value.selectedHunter ?: return
        val level = _state.value.forgeLevel
        _state.update { it.copy(confirm = null) }
        perform(CampaignOp.Craft(hunter, row.code, now(), actor)) {
            app.showMessage("«${row.name}» создан — возьмите из коробки карту $level-го уровня.")
        }
    }

    fun onExchangeRequested(row: ForgeItemRow?) {
        val hunter = _state.value.selectedHunter ?: return
        _state.update { it.copy(exchange = ExchangeRequest(hunter, row?.missing.orEmpty())) }
    }

    fun onExchangeClosed() = _state.update { it.copy(exchange = null) }
}

// endregion

// region Лаборатория

/** Единица цены зелья: допустимые растения и выбранное игроком. */
data class LabUnitChoice(val options: List<Plant>, val any: Boolean, val selected: Plant?) {
    val label: String get() = if (any) "1 любое растение" else "1 " + options.joinToString(" / ") { it.displayName }
}

data class PotionRow(val code: String, val name: String, val units: List<LabUnitChoice>, val affordable: Boolean)

/** Окно «Приготовить зелье?»; [existingLevel] — уровень такого же зелья у охотника, `null` — зелья нет. */
data class LabConfirm(val potion: PotionRow, val choices: List<LabUnitChoice>, val existingLevel: Int? = null) {
    val plants: List<Plant>? get() = choices.map { it.selected ?: return null }
}

data class LabState(
    val hunters: List<HunterState> = emptyList(),
    val selectedHunter: HunterClass? = null,
    val labLevel: Int = 1,
    val potions: List<PotionRow> = emptyList(),
    val stock: List<CostPart> = emptyList(),
    val confirm: LabConfirm? = null,
    val exchange: ExchangeRequest? = null
) {
    val hunter: HunterState? get() = hunters.firstOrNull { it.hunterClass == selectedHunter }

    /** Хватает ли растений на выбранное сочетание. */
    val confirmAffordable: Boolean
        get() {
            val plants = confirm?.plants ?: return false
            val hunter = hunter ?: return false
            return plants.groupingBy { it }.eachCount().all { (plant, count) -> hunter.quantity(ResourceCode.of(plant)) >= count }
        }

    /** Зелье каждого вида одно: что будет с имеющимся. */
    val existingText: String?
        get() {
            val confirm = confirm ?: return null
            val level = confirm.existingLevel ?: return null
            val name = confirm.potion.name
            return if (labLevel > level) {
                "У охотника уже есть «$name» $level-го уровня: растения спишутся, зелье станет $labLevel-го уровня."
            } else {
                "У охотника уже есть «$name» $level-го уровня: растения спишутся, уровень зелья не изменится."
            }
        }

    val confirmCost: String
        get() = confirm?.plants?.groupingBy { it }?.eachCount()?.entries?.joinToString(", ") { "${it.key.displayName} ${it.value}" }.orEmpty()
}

/**
 * Лаборатория: 6 зелий планшета, по 2 растения; где на планшете выбор или «любое» — игрок выбирает растение
 * (по умолчанию — первое по планшету, которого хватает). Уровень карты зелья — уровень лаборатории.
 */
class LabViewModel(app: AppViewModel, localId: Long, scope: CoroutineScope) : CampaignScopedViewModel(app, localId, scope) {

    private val _state = MutableStateFlow(LabState())
    val state: StateFlow<LabState> = _state.asStateFlow()

    init {
        observe()
    }

    override fun onCampaign(campaign: StoredCampaign) {
        val state = campaign.state
        _state.update { current ->
            val hunter = current.selectedHunter?.takeIf { state.hunter(it) != null } ?: state.squad.firstOrNull()?.hunterClass
            rebuild(current.copy(hunters = state.squad, selectedHunter = hunter, labLevel = state.labLevel))
        }
    }

    private fun rebuild(current: LabState): LabState {
        val hunter = current.hunter ?: return current
        val potions = catalog.lab.map { potion ->
            val units = potion.units.map { LabUnitChoice(if (it.any) Plant.entries else it.options, it.any, null) }
            PotionRow(potion.code, potion.name, units, defaultChoice(units, hunter) != null)
        }
        val stock = ResourceCode.plants.mapNotNull { code -> hunter.quantity(code).takeIf { it > 0 }?.let { CostPart(code, it) } }
        return current.copy(potions = potions, stock = stock)
    }

    /** Первое по планшету сочетание, на которое хватает запаса; не хватает — `null`. */
    private fun defaultChoice(units: List<LabUnitChoice>, hunter: HunterState): List<Plant>? {
        fun search(index: Int, used: Map<Plant, Int>): List<Plant>? {
            if (index == units.size) return emptyList()
            for (plant in units[index].options) {
                val count = (used[plant] ?: 0) + 1
                if (hunter.quantity(ResourceCode.of(plant)) >= count) {
                    search(index + 1, used + (plant to count))?.let { return listOf(plant) + it }
                }
            }
            return null
        }
        return search(0, emptyMap())
    }

    fun onHunterSelected(hunterClass: HunterClass) = _state.update { rebuild(it.copy(selectedHunter = hunterClass)) }

    fun onBrewRequested(row: PotionRow) {
        val hunter = _state.value.hunter ?: return
        val defaults = defaultChoice(row.units, hunter)
        val choices = row.units.mapIndexed { index, unit -> unit.copy(selected = defaults?.getOrNull(index) ?: unit.options.firstOrNull()) }
        val existing = rules.existingPotion(hunter, row.name, row.code)
        _state.update { it.copy(confirm = LabConfirm(row, choices, existing?.let { item -> item.level ?: 1 })) }
    }

    fun onPlantChosen(unitIndex: Int, plant: Plant) = _state.update { state ->
        val confirm = state.confirm ?: return@update state
        state.copy(confirm = confirm.copy(choices = confirm.choices.mapIndexed { index, choice ->
            if (index == unitIndex) choice.copy(selected = plant) else choice
        }))
    }

    fun onBrewDismissed() = _state.update { it.copy(confirm = null) }

    fun onBrewConfirmed() {
        val confirm = _state.value.confirm ?: return
        val plants = confirm.plants ?: return
        val hunter = _state.value.selectedHunter ?: return
        val level = _state.value.labLevel
        _state.update { it.copy(confirm = null) }
        val existingLevel = confirm.existingLevel
        perform(CampaignOp.Brew(hunter, confirm.potion.code, plants, now(), actor)) {
            app.showMessage(
                when {
                    existingLevel == null -> "«${confirm.potion.name}» приготовлено — возьмите из коробки карту $level-го уровня."
                    level > existingLevel -> "«${confirm.potion.name}»: уровень зелья повышен до $level-го — замените карту."
                    else -> "«${confirm.potion.name}» приготовлено снова — уровень зелья остаётся $existingLevel-м."
                }
            )
        }
    }

    /** Растения получают только обменом — окно сразу открывает обмен. */
    fun onExchangeRequested() {
        val hunter = _state.value.selectedHunter ?: return
        _state.update { it.copy(exchange = ExchangeRequest(hunter, emptyList(), plantsOnly = true)) }
    }

    fun onExchangeClosed() = _state.update { it.copy(exchange = null) }
}

// endregion

// region Обмен ресурсов

enum class ExchangeMode(val title: String) { EXCHANGE("Обмен"), CONVERT("Преобразование"), SELL("Продажа предмета") }

data class Option<T>(val value: T, val label: String)

data class ExchangeState(
    val hunter: HunterClass? = null,
    val hunterName: String = "",
    val mode: ExchangeMode = ExchangeMode.EXCHANGE,
    val plantsOnly: Boolean = false,
    val missingText: String? = null,
    // Обмен
    val want: ResourceCode? = null,
    val wantOptions: List<Option<ResourceCode>> = emptyList(),
    val offer: ResourceCode? = null,
    val offerOptions: List<Option<ResourceCode>> = emptyList(),
    val offerHint: String? = null,
    val partner: HunterClass? = null,
    val partnerOptions: List<Option<HunterClass>> = emptyList(),
    val partnerHint: String? = null,
    // Преобразование
    val gain: ResourceCode? = null,
    val spendElement: Boolean = true,
    val spend: List<ResourceCode?> = listOf(null, null),
    val elementOptions: List<Option<ResourceCode>> = emptyList(),
    val materialOptions: List<Option<ResourceCode>> = emptyList(),
    val convertHint: String? = null,
    // Продажа
    val item: String? = null,
    val itemOptions: List<Option<String>> = emptyList(),
    val sellGain: ResourceCode? = null,
    val sellGainOptions: List<Option<ResourceCode>> = emptyList()
)

/**
 * «Обменять ресурсы»: обмен 1 к 1 внутри типа с другим охотником, преобразование (1 стихия или 2 материи → 1
 * материя) и продажа карты снаряжения или награды за 1 материю или 1 стихию кузни этой карты.
 */
class ExchangeViewModel(
    app: AppViewModel,
    localId: Long,
    private val request: ExchangeRequest,
    scope: CoroutineScope
) : CampaignScopedViewModel(app, localId, scope) {

    private val _state = MutableStateFlow(
        ExchangeState(
            hunter = request.hunter,
            plantsOnly = request.plantsOnly,
            want = request.missing.firstOrNull()?.code,
            gain = request.missing.firstOrNull { it.code.type == ResourceType.MATERIAL }?.code
        )
    )
    val state: StateFlow<ExchangeState> = _state.asStateFlow()

    init {
        observe()
    }

    override fun onCampaign(campaign: StoredCampaign) = _state.update { rebuild(it) }

    private fun rebuild(current: ExchangeState): ExchangeState {
        val campaign = campaign.value ?: return current
        val hunter = campaign.state.hunter(current.hunter ?: return current) ?: return current
        val others = campaign.state.squad.filter { it.hunterClass != hunter.hunterClass }
        val types = if (current.plantsOnly) listOf(ResourceCode.plants) else listOf(ResourceCode.elements, ResourceCode.materials, ResourceCode.plants)
        val wantOptions = types.flatten().map { Option(it, it.displayName) }
        val want = current.want
        val offerOptions = if (want == null) emptyList() else ResourceCode.entries
            .filter { it.type == want.type && it != want && hunter.quantity(it) > 0 }
            .map { Option(it, "${it.displayName} (${hunter.quantity(it)})") }
        val offerTypeName = when (want?.type) {
            ResourceType.ELEMENT -> "стихии"
            ResourceType.MATERIAL -> "материи"
            ResourceType.PLANT -> "растения"
            null -> ""
        }
        val partnerOptions = if (want == null) emptyList() else others.filter { it.quantity(want) > 0 }
            .map { Option(it.hunterClass, "${it.playerName} (${it.quantity(want)})") }
        val sellable = hunter.items.filter { it.kind != ItemKind.POTION }
        val item = sellable.firstOrNull { it.key == current.item } ?: sellable.firstOrNull()
        val missingText = request.missing.takeIf { it.isNotEmpty() }?.let { "Охотнику ${hunter.playerName} не хватает: ${it.describe()}." }
        return current.copy(
            hunterName = hunter.playerName,
            missingText = missingText,
            wantOptions = wantOptions,
            offer = current.offer?.takeIf { offer -> offerOptions.any { it.value == offer } } ?: offerOptions.firstOrNull()?.value,
            offerOptions = offerOptions,
            offerHint = if (want != null && offerOptions.isEmpty()) {
                "У охотника ${hunter.playerName} нечего предложить взамен: нужен ресурс того же типа ($offerTypeName)."
            } else null,
            partner = current.partner?.takeIf { partner -> partnerOptions.any { it.value == partner } } ?: partnerOptions.firstOrNull()?.value,
            partnerOptions = partnerOptions,
            partnerHint = if (want != null && partnerOptions.isEmpty()) "Ни у кого из охотников нет ресурса «${want.displayName}»." else null,
            elementOptions = ResourceCode.elements.filter { hunter.quantity(it) > 0 }.map { Option(it, "${it.displayName} (${hunter.quantity(it)})") },
            materialOptions = ResourceCode.materials.filter { hunter.quantity(it) > 0 }.map { Option(it, "${it.displayName} (${hunter.quantity(it)})") },
            convertHint = convertHint(current, hunter),
            item = item?.key,
            itemOptions = sellable.map { Option(it.key, itemLabel(it)) },
            sellGainOptions = sellGains(item),
            sellGain = current.sellGain?.takeIf { gain -> sellGains(item).any { it.value == gain } } ?: sellGains(item).firstOrNull()?.value
        )
    }

    private fun itemLabel(item: InventoryItem): String = item.name + (item.level?.let { " · $it ур." } ?: "")

    private fun sellGains(item: InventoryItem?): List<Option<ResourceCode>> {
        if (item == null) return emptyList()
        val materials = ResourceCode.materials.map { Option(it, it.displayName) }
        val element = item.element?.let { ResourceCode.of(it) }?.let { listOf(Option(it, "${it.displayName} (стихия кузни)")) }.orEmpty()
        return element + materials
    }

    private fun convertHint(current: ExchangeState, hunter: HunterState): String? {
        val spend = if (current.spendElement) current.spend.take(1) else current.spend
        if (spend.any { it == null }) return null
        val needed = spend.filterNotNull().groupingBy { it }.eachCount()
        return if (needed.any { (code, count) -> hunter.quantity(code) < count }) "Запаса не хватает." else null
    }

    fun onModeSelected(mode: ExchangeMode) = _state.update { rebuild(it.copy(mode = mode)) }
    fun onWantSelected(code: ResourceCode) = _state.update { rebuild(it.copy(want = code, offer = null, partner = null)) }
    fun onOfferSelected(code: ResourceCode) = _state.update { rebuild(it.copy(offer = code)) }
    fun onPartnerSelected(hunter: HunterClass) = _state.update { rebuild(it.copy(partner = hunter)) }
    fun onGainSelected(code: ResourceCode) = _state.update { rebuild(it.copy(gain = code)) }
    fun onSpendElementChanged(element: Boolean) = _state.update { rebuild(it.copy(spendElement = element, spend = listOf(null, null))) }
    fun onSpendSelected(index: Int, code: ResourceCode) = _state.update { state ->
        rebuild(state.copy(spend = state.spend.mapIndexed { i, old -> if (i == index) code else old }))
    }
    fun onItemSelected(key: String) = _state.update { rebuild(it.copy(item = key, sellGain = null)) }
    fun onSellGainSelected(code: ResourceCode) = _state.update { rebuild(it.copy(sellGain = code)) }

    fun onExchange() {
        val current = _state.value
        val hunter = current.hunter ?: return
        val want = current.want ?: return
        val offer = current.offer ?: return
        val partner = current.partner ?: return
        val partnerName = campaign.value?.state?.hunter(partner)?.playerName ?: partner.displayName
        perform(CampaignOp.Exchange(hunter, partner, mapOf(offer to 1), mapOf(want to 1), now(), actor)) {
            app.showMessage("${current.hunterName} отдал(а) «${offer.displayName}» охотнику $partnerName и получил(а) «${want.displayName}».")
        }
    }

    fun onConvert() {
        val current = _state.value
        val hunter = current.hunter ?: return
        val gain = current.gain ?: return
        val spend = (if (current.spendElement) current.spend.take(1) else current.spend).map { it ?: return }
        perform(CampaignOp.Convert(hunter, spend, gain, now(), actor)) {
            app.showMessage("Получено: ${gain.displayName} 1.")
        }
    }

    fun onSell() {
        val current = _state.value
        val hunter = current.hunter ?: return
        val key = current.item ?: return
        val gain = current.sellGain ?: return
        perform(CampaignOp.Sell(hunter, key, gain, now(), actor)) {
            app.showMessage("Карта сброшена, получено: ${gain.displayName} 1.")
        }
    }
}

// endregion
