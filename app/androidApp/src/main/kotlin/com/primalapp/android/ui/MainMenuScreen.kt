package com.primalapp.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.sync.SyncStatus
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.Screen

/** Главное меню: экспедиция, кампании, «Инфо», настройки; «Вернуться к бою», если бой не закончен. */
@Composable
fun MainMenuScreen(app: AppViewModel) {
    val connection by app.connection.collectAsState()
    val battle by app.battleViewModel.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Компаньон в игре «Primal: Пробуждение»",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Выберите режим игры",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = { app.navigate(Screen.ExpeditionSetup) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Режим экспедиции", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { app.navigate(Screen.CampaignList) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Режим кампании", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = { app.navigate(Screen.Info()) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Инфо", fontSize = 16.sp)
        }
        if (battle.phase.name != "PRE_BATTLE" && battle.phase.name != "SETUP") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { app.resumeBattle() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Вернуться к бою", fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = connection.label,
            fontSize = 14.sp,
            color = if (connection.sync is SyncStatus.Unreachable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { app.navigate(Screen.Settings) }) { Text("Настройки") }
    }
}
