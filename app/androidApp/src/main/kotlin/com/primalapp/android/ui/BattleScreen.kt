package com.primalapp.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.primalapp.viewmodel.BattleParam
import com.primalapp.viewmodel.BattleVibrationEvent
import com.primalapp.viewmodel.BattleViewModel
import com.primalapp.viewmodel.BattleScreenState
import com.primalapp.viewmodel.FightPhase
import com.primalapp.viewmodel.MonsterStatusInfo

@Composable
fun BattleScreen(state: BattleScreenState, viewModel: BattleViewModel, onBackToMenu: () -> Unit = {}) {
    val monster = state.monster
    var showSurrenderDialog by remember { mutableStateOf(false) }
    val vibrate = rememberBattleVibrator()
    LaunchedEffect(viewModel) {
        viewModel.vibrationEvents.collect { vibrate(it) }
    }
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding((160f / 25.4f).dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ParamText("Фаза $phaseLabel", state.highlightedParams.contains(BattleParam.PHASE), baseBold = true)
            ParamText("Раунд ${state.currentRound}/${state.maxRounds}", state.highlightedParams.contains(BattleParam.ROUND))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ParamText("Здоровье: ${monster.currentHealth}", state.highlightedParams.contains(BattleParam.HEALTH))
            ParamText("Ярость: ${monster.rage}", state.highlightedParams.contains(BattleParam.RAGE))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ParamText("Накопленный урон: ${monster.accumulatedDamage}", state.highlightedParams.contains(BattleParam.ACCUMULATED_DAMAGE))
            ParamText("Прочность: ${monster.damageForWound?.toString() ?: "нет"}", state.highlightedParams.contains(BattleParam.DAMAGE_FOR_WOUND))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val statusText = listOfNotNull(
                MonsterStatusInfo.HARDENED.title.takeIf { monster.isHardened },
                MonsterStatusInfo.RESILIENT.title.takeIf { monster.isResilient }
            ).joinToString(", ").ifEmpty { "Обычный" }
            ParamText(
                "Статус: $statusText",
                state.highlightedParams.contains(BattleParam.HARDENED) || state.highlightedParams.contains(BattleParam.RESILIENT)
            )
            ParamText("Смена стойки: ${monster.healthForStanceChange?.let { "при $it HP" } ?: "по запросу"}", state.highlightedParams.contains(BattleParam.HEALTH_FOR_STANCE_CHANGE))
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
        Text(
            "Отрицательное значение отменяет накопленный урон",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        OutlinedButton(onClick = { viewModel.healWound() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Заживить рану (+1 здоровья)")
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

        StatusSwitchRow(
            info = MonsterStatusInfo.HARDENED,
            checked = monster.isHardened,
            onToggle = { viewModel.toggleHardened() },
            onInfo = { viewModel.onStatusInfoRequested(MonsterStatusInfo.HARDENED) }
        )
        StatusSwitchRow(
            info = MonsterStatusInfo.RESILIENT,
            checked = monster.isResilient,
            onToggle = { viewModel.toggleResilient() },
            onInfo = { viewModel.onStatusInfoRequested(MonsterStatusInfo.RESILIENT) }
        )
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
            onClick = {
                vibrate(BattleVibrationEvent.SHORT)
                showSurrenderDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сдаться")
        }
        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = {
            vibrate(BattleVibrationEvent.SHORT)
            onBackToMenu()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Выход в меню")
        }
        if (state.message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(state.message, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
        }
    }

    state.statusInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.onStatusInfoDismissed() },
            title = { Text(info.title) },
            text = { Text(info.description) },
            confirmButton = { TextButton(onClick = { viewModel.onStatusInfoDismissed() }) { Text("Понятно") } }
        )
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
                TextButton(onClick = {
                    vibrate(BattleVibrationEvent.SHORT)
                    showSurrenderDialog = false
                }) { Text("Отмена") }
            }
        )
    }
}

/** Переключатель статуса монстра с кнопкой «i» (серый кружок) — описание статуса (R-3). */
@Composable
private fun StatusSwitchRow(info: MonsterStatusInfo, checked: Boolean, onToggle: () -> Unit, onInfo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${info.title}:")
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color.Gray, CircleShape)
                .semantics(mergeDescendants = true) { contentDescription = "Описание: ${info.title}" }
                .clickable(onClick = onInfo),
            contentAlignment = Alignment.Center
        ) {
            Text("i", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun ParamText(text: String, highlighted: Boolean, baseBold: Boolean = false) {
    Text(
        text = text,
        color = if (highlighted) MaterialTheme.colorScheme.error else Color.Unspecified,
        fontWeight = if (highlighted || baseBold) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
private fun rememberBattleVibrator(): (BattleVibrationEvent) -> Unit {
    val context = LocalContext.current
    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    return { event ->
        val effect = when (event) {
            BattleVibrationEvent.SHORT ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    null
                }
            BattleVibrationEvent.DOUBLE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 30), -1)
                } else {
                    null
                }
        }
        if (effect != null) {
            runCatching { vibrator?.vibrate(effect) }
        }
    }
}

@Composable
fun PhaseChangeDialog(battleState: BattleScreenState, viewModel: BattleViewModel, onDismiss: () -> Unit) {
    // Состояние передаётся параметром: если перенесённый урон сразу вызывает следующую смену стойки,
    // окно остаётся открытым и должно показать новую стойку; поля сбрасываются для каждой стойки
    val stance = battleState.monster.currentPhase
    val initialDfw = battleState.pendingDamageForWound
    val initialHsc = battleState.pendingHealthForStanceChange
    var damageForWound by remember(stance, initialDfw) { mutableStateOf(initialDfw) }
    var healthForStance by remember(stance, initialHsc) { mutableStateOf(initialHsc) }
    var bossHealth by remember(stance) { mutableStateOf("") }

    val damageForWoundVal = damageForWound.toIntOrNull()
    val healthForStanceVal = healthForStance.toIntOrNull()
    val bossHealthVal = bossHealth.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена стойки!") },
        text = {
            Column {
                Text("Монстр перешёл на стойку ${battleState.monster.currentPhase}.")
                Text(
                    if (battleState.phaseChangeFromBossData) "Параметры заполнены из базы боссов — при необходимости исправьте:"
                    else "Данных о стойке в базе боссов нет — укажите параметры:"
                )
                val carried = battleState.monster.accumulatedDamage
                if (carried > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (battleState.monster.isResilient) "Перенесённый урон $carried сбросится («Устойчивость стойки»)."
                        else "Перенесённый урон $carried будет нанесён с новой прочностью после «OK».",
                        fontWeight = FontWeight.Medium
                    )
                }
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
        title = { Text("Выплеск ярости") },
        text = {
            Text(
                "Ярость монстра достигла 3 за каждого охотника. Каждый охотник получает урон, " +
                    "равный силе монстра. После этого ярость сбрасывается до 1 за каждого охотника."
            )
        },
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
    val surrendered = viewModel.state.collectAsState().value.surrendered
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("ПОРАЖЕНИЕ", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(defeatReason(surrendered))
        Spacer(Modifier.height(32.dp))
        Button(onClick = { viewModel.resetBattle() }) { Text("Новый бой") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBackToMenu) { Text("Выход в меню") }
    }
}

/** Причина поражения для экрана поражения (D-14). */
fun defeatReason(surrendered: Boolean): String =
    if (surrendered) "Вы сдались." else "Закончились раунды..."
