package com.primalapp.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.viewmodel.BattleViewModel
import com.primalapp.viewmodel.BattleScreenState
import com.primalapp.viewmodel.FightPhase

@Composable
fun PreBattleScreen(onStart: (Int, Int?, Int) -> Unit) {
    var hunterCount by remember { mutableStateOf("2") }
    var damageForWound by remember { mutableStateOf("4") }
    var healthForStance by remember { mutableStateOf("7") }

    Column {
        Text("Начало боя", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hunterCount,
            onValueChange = { hunterCount = it },
            label = { Text("Количество охотников (1-4)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = damageForWound,
            onValueChange = { damageForWound = it },
            label = { Text("Урон для нанесения раны на игрока (пусто = нет порога раны)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = healthForStance,
            onValueChange = { healthForStance = it },
            label = { Text("Здоровье для смены стойки") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val count = hunterCount.toIntOrNull()
                val wound = damageForWound.toIntOrNull()
                val stance = healthForStance.toIntOrNull()
                if (count != null && stance != null) {
                    onStart(count, wound, stance)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Начать бой") }
    }
}

@Composable
fun SetupScreen(onConfirm: (Int, Int?, Int) -> Unit) {
    PreBattleScreen(onStart = onConfirm)
}

@Composable
fun BattleScreen(state: BattleScreenState, viewModel: BattleViewModel, onBackToMenu: () -> Unit = {}) {
    val monster = state.monster
    var showSurrenderDialog by remember { mutableStateOf(false) }
    val phaseLabel = when (state.phase) {
        FightPhase.PHASE_I -> "I"
        FightPhase.PHASE_II -> "II"
        FightPhase.PHASE_III -> "III"
        FightPhase.PHASE_IV -> "IV"
        FightPhase.PHASE_V -> "V"
        FightPhase.PHASE_VI -> "VI"
        FightPhase.PHASE_VII -> "VII"
        FightPhase.PHASE_VIII -> "VIII"
        FightPhase.PHASE_IX -> "IX"
        else -> ""
    }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Фаза $phaseLabel", fontWeight = FontWeight.Bold)
            Text("Раунд ${state.currentRound}/${state.maxRounds}")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Здоровье: ${monster.currentHealth}")
            Text("Ярость: ${monster.rage}")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Накопленный урон: ${monster.accumulatedDamage}")
            Text("Прочность: ${monster.damageForWound?.toString() ?: "нет"}")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Статус: ${if (monster.isHardened) "Затвердевший" else "Обычный"}")
            Text("Смена стойки: ${monster.healthForStanceChange?.let { "при $it HP" } ?: "по запросу"}")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (state.isTimerRunning) {
            Text("Ожидание... ${state.pendingDamage} урона", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        }

        Text("Нанести урон:", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { viewModel.onQuickButtonPress(1) }) { Text("+1") }
            Button(onClick = { viewModel.onQuickButtonPress(5) }) { Text("+5") }
            Button(onClick = { viewModel.onQuickButtonPress(10) }) { Text("+10") }
            Button(onClick = { viewModel.onQuickButtonPress(50) }) { Text("+50") }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.damageInputText,
                onValueChange = { viewModel.onDamageInputChanged(it) },
                label = { Text("Ввести урон") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.onOkPress() }),
                singleLine = true,
                modifier = Modifier.weight(1f).onFocusChanged { fs -> if (fs.isFocused) viewModel.onInputFieldFocused() }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.onOkPress() }) { Text("OK") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = { viewModel.onCancelPress() }) { Text("Отмена") }
        }

        if (state.isTimerRunning) {
            Button(onClick = { viewModel.commitDamage() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Применить урон сейчас (${state.pendingDamage})")
            }
        }
        if (state.canUndo) {
            Button(onClick = { viewModel.onUndoPress() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Отменить действие")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Ярость:", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { viewModel.removeRage(1) }) { Text("-1") }
            Button(onClick = { viewModel.addRage(1) }) { Text("+1") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { viewModel.addRagePerHunter() }) { Text("+1/охот") }
            Button(onClick = { viewModel.addRagePerHunterMinusOne() }) { Text("+1/охот-1") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Затвердевший:")
            Switch(checked = monster.isHardened, onCheckedChange = { viewModel.toggleHardened() })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (monster.healthForStanceChange == null) {
            Button(onClick = { viewModel.onManualStanceChange() }, modifier = Modifier.fillMaxWidth()) {
                Text("Сменить стойку")
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(onClick = { viewModel.endRound() }, modifier = Modifier.fillMaxWidth()) {
            Text("Закончить раунд")
        }
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showSurrenderDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сдаться")
        }
        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = onBackToMenu, modifier = Modifier.fillMaxWidth()) {
            Text("Выход в меню")
        }
        if (state.message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(state.message, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
        }
    }

    if (showSurrenderDialog) {
        AlertDialog(
            onDismissRequest = { showSurrenderDialog = false },
            title = { Text("Сдаться?") },
            text = { Text("Вы уверены, что хотите сдаться? Бой будет засчитан как поражение.") },
            confirmButton = {
                TextButton(onClick = {
                    showSurrenderDialog = false
                    viewModel.onSurrender()
                }) { Text("Сдаться") }
            },
            dismissButton = {
                TextButton(onClick = { showSurrenderDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun PhaseChangeDialog(viewModel: BattleViewModel, onDismiss: () -> Unit) {
    val battleState = viewModel.state.value
    val initialDfw = battleState.pendingDamageForWound
    val initialHsc = battleState.pendingHealthForStanceChange
    var damageForWound by remember(initialDfw) { mutableStateOf(initialDfw) }
    var healthForStance by remember(initialHsc) { mutableStateOf(initialHsc) }
    var bossHealth by remember { mutableStateOf("") }

    val damageForWoundVal = damageForWound.toIntOrNull()
    val healthForStanceVal = healthForStance.toIntOrNull()
    val bossHealthVal = bossHealth.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена стойки!") },
        text = {
            Column {
                Text("Монстр перешёл на следующую стойку. Укажите новые параметры:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = damageForWound, onValueChange = { damageForWound = it }, label = { Text("Урон для нанесения раны на игрока (пусто = нет порога раны)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = healthForStance, onValueChange = { healthForStance = it }, label = { Text("Здоровье для смены стойки (пусто = по запросу)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (healthForStanceVal != null && healthForStanceVal > 0) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = bossHealth, onValueChange = { bossHealth = it }, label = { Text("Здоровье босса в новой стойке") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (damageForWoundVal == null || damageForWoundVal > 0) {
                    viewModel.confirmPhaseChange(damageForWoundVal, healthForStanceVal, bossHealthVal)
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun RageSurgeDialog(viewModel: BattleViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.confirmRageSurge() },
        title = { Text("Всплеск ярости") },
        text = { Text("Ярость монстра достигла критического уровня!") },
        confirmButton = { TextButton(onClick = { viewModel.confirmRageSurge() }) { Text("OK") } }
    )
}

@Composable
fun VictoryScreen(viewModel: BattleViewModel, onBackToMenu: () -> Unit = {}) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("ПОБЕДА!", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Монстр повержен!")
        Spacer(Modifier.height(32.dp))
        Button(onClick = { viewModel.resetBattle() }) { Text("Новый бой") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBackToMenu) { Text("Выход в меню") }
    }
}

@Composable
fun DefeatScreen(viewModel: BattleViewModel, onBackToMenu: () -> Unit = {}) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("ПОРАЖЕНИЕ", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Закончились раунды...")
        Spacer(Modifier.height(32.dp))
        Button(onClick = { viewModel.resetBattle() }) { Text("Новый бой") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBackToMenu) { Text("Выход в меню") }
    }
}
