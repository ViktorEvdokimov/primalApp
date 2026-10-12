package com.primalapp.android.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.BattleSetupViewModel
import com.primalapp.viewmodel.Screen

/**
 * Подготовка к бою: экспедиция (localId = null) или бой кампании (босс задания, сложность по главе, отряд кампании).
 */
@Composable
fun BattleSetupScreen(app: AppViewModel, localId: Long?, questNumber: Int?) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(localId, questNumber) { BattleSetupViewModel(app, localId, questNumber, scope) }
    val state by viewModel.state.collectAsState()

    ScreenScaffold(title = "Подготовка к бою", onBack = { app.back() }) {
        state.subtitle?.let { Text(it, fontSize = 16.sp) }
        Hint("Охотники готовы к сражению.")
        Spacer(Modifier.height(12.dp))
        if (!state.ready) return@ScreenScaffold
        if (state.isCampaign && state.error != null && state.bosses.isEmpty()) {
            ErrorText(state.error)
            return@ScreenScaffold
        }
        state.activeBattles.forEach { Banner("Идёт бой: $it") }

        Dropdown(
            label = "Выберите босса",
            selectedLabel = state.selectedBossLabel,
            options = listOf<Pair<String?, String>>(null to "Ввести данные вручную") + state.bosses.map { it.code to it.label },
            onSelected = { viewModel.onBossSelected(it) },
            enabled = !state.bossLocked
        )
        Spacer(Modifier.height(8.dp))
        Dropdown(
            label = "Сложность",
            selectedLabel = "Сложность: ${state.difficulty}",
            options = state.availableDifficulties.map { it to "$it" },
            onSelected = { viewModel.onDifficultySelected(it) },
            enabled = !state.isCampaign || !state.bossLocked || state.availableDifficulties.size > 1
        )
        ReactionDeckHint(state.difficulty)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.hunterCountText,
            onValueChange = { viewModel.onHunterCountChanged(it) },
            label = { Text("Количество охотников") },
            enabled = state.hunterCountEditable,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.damageForWoundText,
            onValueChange = { viewModel.onDamageForWoundChanged(it) },
            label = { Text("Урон для нанесения раны на игрока (пусто = нет порога раны)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.healthForStanceText,
            onValueChange = { viewModel.onHealthForStanceChanged(it) },
            label = { Text("Здоровье для смены стойки (пусто = по запросу)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        ErrorText(state.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.onStart() }, modifier = Modifier.fillMaxWidth()) {
            Text("Начать бой", fontSize = 18.sp)
        }
        if (state.canChooseQuest && localId != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { app.back() }, modifier = Modifier.fillMaxWidth()) { Text("Другое задание") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { app.toMainMenu() }, modifier = Modifier.fillMaxWidth()) { Text("Выход в меню") }
    }

    if (state.showActiveWarning) {
        ConfirmDialog(
            title = "Уже идёт бой",
            lines = state.activeBattles + "Глава засчитывается по первой принятой победе: если она будет не ваша, результат вашего боя не примут. Начать бой?",
            confirm = "Начать бой",
            onConfirm = { viewModel.onConfirmActiveWarning() },
            onDismiss = { viewModel.onDismissDialogs() }
        )
    }
    if (state.showReplaceConfirm) {
        ConfirmDialog(
            title = "Новый бой",
            lines = listOf("Текущий бой будет потерян. Начать новый бой?"),
            confirm = "Начать бой",
            onConfirm = { viewModel.onConfirmReplace() },
            onDismiss = { viewModel.onDismissDialogs() }
        )
    }
}
