package com.primalapp.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.campaign.BattlePurpose
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.FightPhase
import com.primalapp.viewmodel.Screen

/**
 * Экран текущего боя: ход боя, затем итог. Экспедиция — «Новый бой» и «Выход в меню» (бой удаляется); бой кампании
 * — «К наградам» (победа) или «Завершить бой» (поражение), «Выход в меню» бой не удаляет: итог ещё нужно принять.
 */
@Composable
fun BattleHost(app: AppViewModel) {
    val viewModel = app.battleViewModel
    val state by viewModel.state.collectAsState()
    val campaignBattle by app.campaignBattle.collectAsState()
    val header = campaignBattle?.let { context ->
        "Кампания · " + when (context.purpose) {
            BattlePurpose.PROLOGUE -> "Пролог"
            BattlePurpose.QUEST -> "Задание ${context.questNumber}"
            BattlePurpose.FREE -> "Бой без задания"
            BattlePurpose.FINAL -> "Финальный бой"
        }
    }

    when (state.phase) {
        FightPhase.PRE_BATTLE, FightPhase.SETUP -> {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("Бой не начат.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { app.toMainMenu() }) { Text("В меню") }
            }
        }
        FightPhase.VICTORY, FightPhase.DEFEAT -> {
            val victory = state.phase == FightPhase.VICTORY
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                header?.let { Text(it) }
                Text(if (victory) "ПОБЕДА!" else "ПОРАЖЕНИЕ", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(if (victory) "Монстр повержен!" else defeatReason(state.surrendered))
                Spacer(Modifier.height(32.dp))
                val context = campaignBattle
                if (context != null) {
                    Button(onClick = { app.replace(Screen.Outcome(context.localId)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (victory) "К наградам" else "Завершить бой")
                    }
                } else {
                    Button(onClick = {
                        viewModel.resetBattle()
                        app.replace(Screen.ExpeditionSetup)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Новый бой") }
                }
                if (state.canUndo) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.onUndoPress() }, modifier = Modifier.fillMaxWidth()) { Text("Отменить итог") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { if (context == null) app.leaveExpedition() else app.toMainMenu() },
                    modifier = Modifier.fillMaxWidth()) { Text("Выход в меню") }
            }
        }
        else -> BattleScreen(
            state = state,
            viewModel = viewModel,
            onBackToMenu = { app.toMainMenu() },
            header = header,
            onInfo = { app.navigate(Screen.Info()) }
        )
    }
    if (state.showPhaseChangeDialog) PhaseChangeDialog(state, viewModel) { viewModel.dismissPhaseChangeDialog() }
    if (state.showRageSurgeDialog) RageSurgeDialog(viewModel)
}
