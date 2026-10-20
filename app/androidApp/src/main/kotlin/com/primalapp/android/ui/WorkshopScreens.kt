package com.primalapp.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.campaign.HunterState
import com.primalapp.model.campaign.HunterClass
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.CostPart
import com.primalapp.viewmodel.ExchangeMode
import com.primalapp.viewmodel.ExchangeRequest
import com.primalapp.viewmodel.ExchangeViewModel
import com.primalapp.viewmodel.ForgeViewModel
import com.primalapp.viewmodel.LabViewModel
import com.primalapp.viewmodel.describe

/** Охотники отряда; основной персонаж ([main]) выделен жирным (web qa № 150). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HunterChips(hunters: List<HunterState>, selected: HunterClass?, main: HunterClass?, onSelected: (HunterClass) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        hunters.forEach { hunter ->
            FilterChip(selected = hunter.hunterClass == selected, onClick = { onSelected(hunter.hunterClass) },
                label = {
                    Text("${hunter.hunterClass.displayName} — ${hunter.playerName}",
                        fontWeight = if (hunter.hunterClass == main) FontWeight.Bold else null)
                })
        }
    }
}

private fun List<CostPart>.pairs(): List<Pair<ResourceCode, Int>> = map { it.code to it.quantity }

/** Кузница: открытые кузни, планшет стихии текущего уровня, «Создать» и «Обменять ресурсы». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForgeScreen(app: AppViewModel, localId: Long) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(localId) { ForgeViewModel(app, localId, scope) }
    val state by viewModel.state.collectAsState()

    ScreenScaffold(title = "Кузница", onBack = { app.back() }) {
        Text("Кузня: ${state.forgeLevel}", fontWeight = FontWeight.Bold)
        HunterChips(state.hunters, state.selectedHunter, state.mainHunter) { viewModel.onHunterSelected(it) }
        if (state.openForges.isEmpty()) {
            Hint("Кузни ещё не открыты: кузня стихии открывается победой над боссом этой стихии.")
            return@ScreenScaffold
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.openForges.forEach { element ->
                FilterChip(selected = element == state.element, onClick = { viewModel.onElementSelected(element) },
                    label = { Row(verticalAlignment = Alignment.CenterVertically) { ElementIcon(element, 16); Text(" ${element.displayName}") } })
            }
        }
        Text("Запас:", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        ResourceLine(state.stock.pairs(), "нет стихий и материй")
        state.element?.let { SectionTitle("Кузня: ${it.displayName.lowercase()} · ${state.forgeLevel}-й уровень") }
        state.items.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    Text(item.kind, fontSize = 13.sp)
                    ResourceLine(item.cost.pairs())
                    if (!item.canCraft) Text("Не хватает: ${item.missing.describe()}", fontSize = 13.sp)
                    Row {
                        Button(onClick = { viewModel.onCraftRequested(item) }, enabled = item.canCraft) { Text("Создать") }
                        if (!item.canCraft) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.onExchangeRequested(item) }) { Text("Обменять ресурсы") }
                        }
                    }
                }
            }
        }
    }

    state.confirm?.let { item ->
        ConfirmDialog(
            title = "Создать снаряжение?",
            lines = listOf("${state.hunter?.playerName.orEmpty()}: «${item.name}».", "Будет списано: ${item.cost.describe()}."),
            confirm = "Создать",
            onConfirm = { viewModel.onCraftConfirmed() },
            onDismiss = { viewModel.onCraftDismissed() }
        )
    }
    state.exchange?.let { request -> ExchangeDialog(app, localId, request) { viewModel.onExchangeClosed() } }
}

/** Лаборатория: 6 зелий планшета; растения на выбор — в окне «Приготовить зелье?». */
@Composable
fun LabScreen(app: AppViewModel, localId: Long) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(localId) { LabViewModel(app, localId, scope) }
    val state by viewModel.state.collectAsState()

    ScreenScaffold(title = "Лаборатория", onBack = { app.back() }) {
        Text("Лаборатория: ${state.labLevel}", fontWeight = FontWeight.Bold)
        HunterChips(state.hunters, state.selectedHunter, state.mainHunter) { viewModel.onHunterSelected(it) }
        Text("Запас:", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        ResourceLine(state.stock.pairs(), "растений нет")
        Hint("Цены одинаковы на всех уровнях; уровень лаборатории — уровень карты зелья.")
        state.potions.forEach { potion ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(potion.name, fontWeight = FontWeight.Bold)
                    potion.units.forEach { Text(it.label, fontSize = 14.sp) }
                    if (!potion.affordable) Text("Не хватает растений", fontSize = 13.sp)
                    Row {
                        Button(onClick = { viewModel.onBrewRequested(potion) }, enabled = potion.affordable) { Text("Приготовить") }
                        if (!potion.affordable) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.onExchangeRequested() }) { Text("Обменять ресурсы") }
                        }
                    }
                }
            }
        }
    }

    state.confirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = { viewModel.onBrewDismissed() },
            title = { Text("Приготовить зелье?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${state.hunter?.playerName.orEmpty()}: «${confirm.potion.name}».")
                    confirm.choices.forEachIndexed { index, choice ->
                        if (choice.options.size > 1) {
                            Dropdown(
                                label = "Растение ${index + 1}",
                                selectedLabel = choice.selected?.let { "${it.displayName} (${state.hunter?.quantity(ResourceCode.of(it)) ?: 0})" } ?: "",
                                options = choice.options.map { it to "${it.displayName} (${state.hunter?.quantity(ResourceCode.of(it)) ?: 0})" },
                                onSelected = { viewModel.onPlantChosen(index, it) }
                            )
                        } else {
                            Text("Растение ${index + 1}: ${choice.options.firstOrNull()?.displayName.orEmpty()}")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (state.confirmAffordable) Text("Будет списано: ${state.confirmCost}.")
                    else Text("На выбранные растения запаса не хватает — выберите другие.")
                    val existing = state.existingText
                    if (existing != null) Text(existing) else Hint("Зелье каждого вида у охотника одно: повторное приготовление повышает его уровень до уровня лаборатории.")
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.onBrewConfirmed() }, enabled = state.confirmAffordable) { Text("Приготовить") } },
            dismissButton = { TextButton(onClick = { viewModel.onBrewDismissed() }) { Text("Отмена") } }
        )
    }
    state.exchange?.let { request -> ExchangeDialog(app, localId, request) { viewModel.onExchangeClosed() } }
}

/** «Обменять ресурсы»: обмен, преобразование, продажа предмета. */
@Composable
fun ExchangeDialog(app: AppViewModel, localId: Long, request: ExchangeRequest, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(request) { ExchangeViewModel(app, localId, request, scope) }
    val state by viewModel.state.collectAsState()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Обменять ресурсы") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Охотник: ${state.hunterName}")
                state.missingText?.let { Text(it, fontSize = 14.sp) }
                if (!state.plantsOnly) {
                    ExchangeMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.mode == mode, onClick = { viewModel.onModeSelected(mode) })
                            Text(mode.title)
                        }
                    }
                }
                when (if (state.plantsOnly) ExchangeMode.EXCHANGE else state.mode) {
                    ExchangeMode.EXCHANGE -> {
                        Dropdown("Что хотите получить", state.want?.displayName ?: "", state.wantOptions.map { it.value to it.label },
                            { viewModel.onWantSelected(it) })
                        if (state.want != null) {
                            Dropdown("Что предлагаете взамен", state.offerOptions.firstOrNull { it.value == state.offer }?.label ?: "",
                                state.offerOptions.map { it.value to it.label }, { viewModel.onOfferSelected(it) }, enabled = state.offerOptions.isNotEmpty())
                            state.offerHint?.let { Hint(it) }
                            Dropdown("С кем обменяться", state.partnerOptions.firstOrNull { it.value == state.partner }?.label ?: "",
                                state.partnerOptions.map { it.value to it.label }, { viewModel.onPartnerSelected(it) }, enabled = state.partnerOptions.isNotEmpty())
                            state.partnerHint?.let { Hint(it) }
                        }
                        Button(onClick = { viewModel.onExchange() }, enabled = state.offer != null && state.partner != null) { Text("Обменяться") }
                    }
                    ExchangeMode.CONVERT -> {
                        Dropdown("Получить материю", state.gain?.displayName ?: "", ResourceCode.materials.map { it to it.displayName },
                            { viewModel.onGainSelected(it) })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.spendElement, onClick = { viewModel.onSpendElementChanged(true) })
                            Text("1 стихия → 1 материя")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !state.spendElement, onClick = { viewModel.onSpendElementChanged(false) })
                            Text("2 материи → 1 материя")
                        }
                        val options = if (state.spendElement) state.elementOptions else state.materialOptions
                        val slots = if (state.spendElement) 1 else 2
                        (0 until slots).forEach { index ->
                            Dropdown("Отдать ${index + 1}", state.spend.getOrNull(index)?.displayName ?: "", options.map { it.value to it.label },
                                { viewModel.onSpendSelected(index, it) }, enabled = options.isNotEmpty())
                        }
                        state.convertHint?.let { Hint(it) }
                        Button(onClick = { viewModel.onConvert() },
                            enabled = state.gain != null && state.spend.take(slots).all { it != null } && state.convertHint == null) { Text("Преобразовать") }
                    }
                    ExchangeMode.SELL -> {
                        if (state.itemOptions.isEmpty()) {
                            Hint("Нет карт снаряжения или наград для продажи.")
                        } else {
                            Dropdown("Карта", state.itemOptions.firstOrNull { it.value == state.item }?.label ?: "",
                                state.itemOptions.map { it.value to it.label }, { viewModel.onItemSelected(it) })
                            Dropdown("Получить", state.sellGainOptions.firstOrNull { it.value == state.sellGain }?.label ?: "",
                                state.sellGainOptions.map { it.value to it.label }, { viewModel.onSellGainSelected(it) })
                            Button(onClick = { viewModel.onSell() }, enabled = state.item != null && state.sellGain != null) { Text("Продать") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } }
    )
}
