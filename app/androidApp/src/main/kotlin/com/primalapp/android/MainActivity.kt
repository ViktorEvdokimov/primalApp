package com.primalapp.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.primalapp.android.ui.BattleHost
import com.primalapp.android.ui.BattleSetupScreen
import com.primalapp.android.ui.CampaignCreateScreen
import com.primalapp.android.ui.CampaignListScreen
import com.primalapp.android.ui.CampaignSheetScreen
import com.primalapp.android.ui.ForgeScreen
import com.primalapp.android.ui.EquipmentScreen
import com.primalapp.android.ui.InfoScreen
import com.primalapp.android.ui.JoinScreen
import com.primalapp.android.ui.LoginScreen
import com.primalapp.android.ui.LabScreen
import com.primalapp.android.ui.MainMenuScreen
import com.primalapp.android.ui.OutcomeScreen
import com.primalapp.android.ui.QuestSelectScreen
import com.primalapp.android.ui.SettingsScreen
import com.primalapp.android.ui.TransitionScreen
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.Screen

class MainActivity : ComponentActivity() {

    private val app: AppViewModel get() = (application as PrimalApplication).appViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleLink(intent)
        setContent {
            MaterialTheme {
                PrimalApp(app)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    override fun onResume() {
        super.onResume()
        app.onResume()
    }

    /** Ссылка на сайт (App Links): приглашение, лист кампании. */
    private fun handleLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.dataString?.let { app.openLink(it) }
    }
}

@Composable
fun PrimalApp(app: AppViewModel) {
    val stack by app.stack.collectAsState()
    val ready by app.core.ready.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(app) {
        app.messages.collect { message -> snackbar.showSnackbar(message) }
    }

    BackHandler(enabled = stack.size > 1) { app.back() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!ready) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }
            when (val screen = stack.last()) {
                Screen.MainMenu -> MainMenuScreen(app)
                Screen.ExpeditionSetup -> BattleSetupScreen(app, null, null)
                Screen.Battle -> BattleHost(app)
                Screen.CampaignList -> CampaignListScreen(app)
                Screen.CampaignCreate -> CampaignCreateScreen(app)
                is Screen.CampaignSheet -> CampaignSheetScreen(app, screen.localId)
                is Screen.Forge -> ForgeScreen(app, screen.localId)
                is Screen.Lab -> LabScreen(app, screen.localId)
                is Screen.QuestSelect -> QuestSelectScreen(app, screen.localId)
                is Screen.CampaignBattleSetup -> BattleSetupScreen(app, screen.localId, screen.questNumber)
                is Screen.Outcome -> OutcomeScreen(app, screen.localId)
                is Screen.Transition -> TransitionScreen(app, screen.localId)
                is Screen.Info -> InfoScreen(app, screen.section, screen.entryId)
                Screen.Equipment -> EquipmentScreen(app)
                Screen.Settings -> SettingsScreen(app)
                Screen.Login -> LoginScreen(app)
                is Screen.Join -> JoinScreen(app, screen.token)
            }
        }
    }
}
