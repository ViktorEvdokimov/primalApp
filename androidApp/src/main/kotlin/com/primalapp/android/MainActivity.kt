package com.primalapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.database.CampaignRepositoryImpl
import com.primalapp.database.PlatformContext
import com.primalapp.database.createPrimalDatabase
import com.primalapp.android.ui.BattleScreen
import com.primalapp.android.ui.CampaignListScreen
import com.primalapp.android.ui.CampaignSetupScreen
import com.primalapp.android.ui.CampaignSheetScreen
import com.primalapp.android.ui.DefeatScreen
import com.primalapp.android.ui.ExchangeDialog
import com.primalapp.android.ui.MainMenuScreen
import com.primalapp.android.ui.PhaseChangeDialog
import com.primalapp.android.ui.PostVictoryDialog
import com.primalapp.android.ui.PreBattleScreen
import com.primalapp.android.ui.RageSurgeDialog
import com.primalapp.android.ui.SetupScreen
import com.primalapp.android.ui.VictoryScreen
import com.primalapp.viewmodel.AppScreen
import com.primalapp.viewmodel.BattleViewModel
import com.primalapp.viewmodel.CampaignViewModel
import com.primalapp.viewmodel.FightPhase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PrimalApp()
            }
        }
    }
}

@Composable
fun PrimalApp() {
    val context = LocalContext.current
    val db = remember {
        createPrimalDatabase(PlatformContext(context))
    }
    val repository = remember { CampaignRepositoryImpl(db) }
    val campaignViewModel = remember { CampaignViewModel(repository) }
    val campaignState by campaignViewModel.state.collectAsState()

    when (campaignState.screen) {
        AppScreen.MainMenu -> MainMenuScreen(campaignState, campaignViewModel)
        AppScreen.CampaignSetup -> CampaignSetupScreen(campaignState, campaignViewModel)
        AppScreen.CampaignList -> CampaignListScreen(campaignState, campaignViewModel)
        is AppScreen.CampaignSheet -> CampaignSheetScreen(campaignState, campaignViewModel)
        is AppScreen.CampaignBattle -> {
            val battleVm = campaignViewModel.getBattleViewModel()
            if (battleVm != null) {
                val battleState by battleVm.state.collectAsState()
                CampaignBattleHost(battleState, battleVm, campaignViewModel)
            }
        }
        AppScreen.QuickBattle -> {
            val battleVm = campaignViewModel.getBattleViewModel()
            if (battleVm != null) {
                val quickState by battleVm.state.collectAsState()
                QuickBattleHost(
                    quickState,
                    battleVm,
                    onPauseBattle = { campaignViewModel.onPauseBattle() },
                    onBackToMenu = {
                        battleVm.resetBattle()
                        campaignViewModel.onBackToMenu()
                    }
                )
            }
        }
    }

    if (campaignState.showPostVictory) {
        PostVictoryDialog(campaignState, campaignViewModel)
    }
    if (campaignState.showExchangeDialog) {
        ExchangeDialog(campaignState, campaignViewModel)
    }
    if (campaignState.fatalError != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Ошибка") },
            text = { Text(campaignState.fatalError ?: "") },
            confirmButton = {
                TextButton(onClick = { campaignViewModel.onBackToMenu() }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
fun CampaignBattleHost(
    battleState: com.primalapp.viewmodel.BattleScreenState,
    battleViewModel: BattleViewModel,
    campaignViewModel: CampaignViewModel
) {
    when (battleState.phase) {
        FightPhase.PRE_BATTLE, FightPhase.SETUP -> {
            var damageForWound by remember { mutableStateOf("4") }
            var healthForStance by remember { mutableStateOf("7") }

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Подготовка к бою", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Охотники готовы к сражению.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = damageForWound,
                    onValueChange = { damageForWound = it },
                    label = { Text("Урон для нанесения раны на игрока") },
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
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val wound = damageForWound.toIntOrNull() ?: return@Button
                        val stance = healthForStance.toIntOrNull() ?: return@Button
                        campaignViewModel.onConfirmCampaignBattleStart(wound, stance)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Начать бой", fontSize = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { campaignViewModel.onPauseBattle() }) {
                    Text("Выход в меню")
                }
            }
        }
        FightPhase.PHASE_I, FightPhase.PHASE_II, FightPhase.PHASE_III,
        FightPhase.PHASE_IV, FightPhase.PHASE_V, FightPhase.PHASE_VI,
        FightPhase.PHASE_VII, FightPhase.PHASE_VIII, FightPhase.PHASE_IX -> {
            BattleScreen(battleState, battleViewModel, onBackToMenu = { campaignViewModel.onPauseBattle() })
        }
        FightPhase.VICTORY -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("ПОБЕДА!", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Монстр повержен!")
                Spacer(Modifier.height(32.dp))
                Button(onClick = { campaignViewModel.onVictory() }) {
                    Text("Продолжить")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { campaignViewModel.onBackToMenu() }) {
                    Text("Выход в меню")
                }
            }
        }
        FightPhase.DEFEAT -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("ПОРАЖЕНИЕ", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Закончились раунды...")
                Spacer(Modifier.height(32.dp))
                Button(onClick = { campaignViewModel.onBattleFinished() }) {
                    Text("К листу кампании")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { campaignViewModel.onBackToMenu() }) {
                    Text("Выход в меню")
                }
            }
        }
    }

    if (battleState.showPhaseChangeDialog) {
        PhaseChangeDialog(battleViewModel) { battleViewModel.dismissPhaseChangeDialog() }
    }
    if (battleState.showRageSurgeDialog) {
        RageSurgeDialog(battleViewModel)
    }
}

@Composable
fun QuickBattleHost(
    state: com.primalapp.viewmodel.BattleScreenState,
    viewModel: BattleViewModel,
    onPauseBattle: () -> Unit = {},
    onBackToMenu: () -> Unit = {}
) {
    when (state.phase) {
        FightPhase.PRE_BATTLE -> PreBattleScreen { c, w, s -> viewModel.startBattle(c, w, s) }
        FightPhase.SETUP -> SetupScreen { c, w, s -> viewModel.startBattle(c, w, s) }
        FightPhase.PHASE_I, FightPhase.PHASE_II, FightPhase.PHASE_III,
        FightPhase.PHASE_IV, FightPhase.PHASE_V, FightPhase.PHASE_VI,
        FightPhase.PHASE_VII, FightPhase.PHASE_VIII, FightPhase.PHASE_IX -> BattleScreen(state, viewModel, onPauseBattle)
        FightPhase.VICTORY -> VictoryScreen(viewModel, onBackToMenu)
        FightPhase.DEFEAT -> DefeatScreen(viewModel, onBackToMenu)
    }
    if (state.showPhaseChangeDialog) PhaseChangeDialog(viewModel) { viewModel.dismissPhaseChangeDialog() }
    if (state.showRageSurgeDialog) RageSurgeDialog(viewModel)
}
