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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.primalapp.model.Hunter
import com.primalapp.viewmodel.CampaignUiState
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
                CampaignBattleHost(battleState, battleVm, campaignViewModel, campaignState)
            }
        }
        AppScreen.QuickBattle -> {
            val battleVm = campaignViewModel.getBattleViewModel()
            if (battleVm != null) {
                val quickState by battleVm.state.collectAsState()
            QuickBattleHost(
                quickState,
                battleVm,
                campaignState,
                campaignViewModel,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignBattleHost(
    battleState: com.primalapp.viewmodel.BattleScreenState,
    battleViewModel: BattleViewModel,
    campaignViewModel: CampaignViewModel,
    campaignState: CampaignUiState
) {
    when (battleState.phase) {
        FightPhase.PRE_BATTLE, FightPhase.SETUP -> {
            var bossDropdownExpanded by remember { mutableStateOf(false) }
            var difficultyDropdownExpanded by remember { mutableStateOf(false) }
            val selectedBossName = campaignState.selectedPreBattleBossName?.let { name ->
                campaignState.availableBosses.firstOrNull { it.name == name }
                    ?.let { b -> b.element?.let { "${it.displayName} - ${b.name}" } ?: b.name } ?: name
            } ?: "Ввести данные вручную"
            val selectedDifficulty = campaignState.preBattleDifficulty

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Подготовка к бою", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Охотники готовы к сражению.")
                Spacer(Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = bossDropdownExpanded,
                    onExpandedChange = { bossDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedBossName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Выберите босса") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bossDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = bossDropdownExpanded,
                        onDismissRequest = { bossDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ввести данные вручную") },
                            onClick = {
                                campaignViewModel.onPreBattleBossSelected(null)
                                bossDropdownExpanded = false
                            }
                        )
                        campaignState.availableBosses.distinctBy { it.name }.forEach { boss ->
                            DropdownMenuItem(
                                text = { Text(boss.element?.let { "${it.displayName} - ${boss.name}" } ?: boss.name) },
                                onClick = {
                                    campaignViewModel.onPreBattleBossSelected(boss.name)
                                    bossDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = difficultyDropdownExpanded,
                    onExpandedChange = { difficultyDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "Сложность: $selectedDifficulty",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Сложность") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = difficultyDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = difficultyDropdownExpanded,
                        onDismissRequest = { difficultyDropdownExpanded = false }
                    ) {
                        (0..3).forEach { diff ->
                            DropdownMenuItem(
                                text = { Text("$diff") },
                                onClick = {
                                    campaignViewModel.onPreBattleDifficultySelected(diff)
                                    difficultyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = campaignState.preBattleDamageForWound,
                    onValueChange = { campaignViewModel.onPreBattleDfwChanged(it) },
                    label = { Text("Урон для нанесения раны на игрока (пусто = нет порога раны)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = campaignState.preBattleHealthForStance,
                    onValueChange = { campaignViewModel.onPreBattleHscChanged(it) },
                    label = { Text("Здоровье для смены стойки (пусто = по запросу)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val wound = campaignState.preBattleDamageForWound.toIntOrNull()
                        val stance = campaignState.preBattleHealthForStance.toIntOrNull()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBattleHost(
    state: com.primalapp.viewmodel.BattleScreenState,
    viewModel: BattleViewModel,
    campaignState: CampaignUiState,
    campaignViewModel: CampaignViewModel,
    onPauseBattle: () -> Unit = {},
    onBackToMenu: () -> Unit = {}
) {
    when (state.phase) {
        FightPhase.PRE_BATTLE, FightPhase.SETUP -> {
            var bossDropdownExpanded by remember { mutableStateOf(false) }
            var difficultyDropdownExpanded by remember { mutableStateOf(false) }
            var hunterCountText by remember { mutableStateOf(campaignState.preBattleHunterCount.toString()) }
            val selectedBossName = campaignState.selectedPreBattleBossName?.let { name ->
                campaignState.availableBosses.firstOrNull { it.name == name }
                    ?.let { b -> b.element?.let { "${it.displayName} - ${b.name}" } ?: b.name } ?: name
            } ?: "Ввести данные вручную"
            val selectedDifficulty = campaignState.preBattleDifficulty

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Подготовка к бою", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Охотники готовы к сражению.")
                Spacer(Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = bossDropdownExpanded,
                    onExpandedChange = { bossDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedBossName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Выберите босса") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bossDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = bossDropdownExpanded,
                        onDismissRequest = { bossDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ввести данные вручную") },
                            onClick = {
                                campaignViewModel.onPreBattleBossSelected(null)
                                bossDropdownExpanded = false
                            }
                        )
                        campaignState.availableBosses.distinctBy { it.name }.forEach { boss ->
                            DropdownMenuItem(
                                text = { Text(boss.element?.let { "${it.displayName} - ${boss.name}" } ?: boss.name) },
                                onClick = {
                                    campaignViewModel.onPreBattleBossSelected(boss.name)
                                    bossDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = difficultyDropdownExpanded,
                    onExpandedChange = { difficultyDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "Сложность: $selectedDifficulty",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Сложность") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = difficultyDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = difficultyDropdownExpanded,
                        onDismissRequest = { difficultyDropdownExpanded = false }
                    ) {
                        (0..3).forEach { diff ->
                            DropdownMenuItem(
                                text = { Text("$diff") },
                                onClick = {
                                    campaignViewModel.onPreBattleDifficultySelected(diff)
                                    difficultyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = hunterCountText,
                    onValueChange = { hunterCountText = it },
                    label = { Text("Количество охотников") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = campaignState.preBattleDamageForWound,
                    onValueChange = { campaignViewModel.onPreBattleDfwChanged(it) },
                    label = { Text("Урон для нанесения раны на игрока (пусто = нет порога раны)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = campaignState.preBattleHealthForStance,
                    onValueChange = { campaignViewModel.onPreBattleHscChanged(it) },
                    label = { Text("Здоровье для смены стойки (пусто = по запросу)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val wound = campaignState.preBattleDamageForWound.toIntOrNull()
                        val stance = campaignState.preBattleHealthForStance.toIntOrNull()
                        val count = hunterCountText.toIntOrNull()?.coerceIn(1, 6) ?: return@Button
                        val boss = campaignState.selectedPreBattleBoss
                        val difficulty = campaignState.preBattleDifficulty
                        viewModel.startBattleWithHunters(
                            hunters = (1..count).map { Hunter(name = "Охотник $it") },
                            damageForWound = wound,
                            healthForStanceChange = stance,
                            boss = boss,
                            difficulty = difficulty
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Начать бой", fontSize = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPauseBattle) {
                    Text("Выход в меню")
                }
            }
        }
        FightPhase.PHASE_I, FightPhase.PHASE_II, FightPhase.PHASE_III,
        FightPhase.PHASE_IV, FightPhase.PHASE_V, FightPhase.PHASE_VI,
        FightPhase.PHASE_VII, FightPhase.PHASE_VIII, FightPhase.PHASE_IX -> BattleScreen(state, viewModel, onPauseBattle)
        FightPhase.VICTORY -> VictoryScreen(viewModel, onBackToMenu)
        FightPhase.DEFEAT -> DefeatScreen(viewModel, onBackToMenu)
    }
    if (state.showPhaseChangeDialog) PhaseChangeDialog(viewModel) { viewModel.dismissPhaseChangeDialog() }
    if (state.showRageSurgeDialog) RageSurgeDialog(viewModel)
}
