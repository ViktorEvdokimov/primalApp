package com.primalapp.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.primalapp.model.campaign.HunterClass
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.CampaignCreateViewModel

/** Новая кампания: название и отряд из 2–5 охотников; порядок выбора — порядок в отряде. */
@Composable
fun CampaignCreateScreen(app: AppViewModel) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { CampaignCreateViewModel(app, scope) }
    val state by viewModel.state.collectAsState()

    ScreenScaffold(title = "Новая кампания", onBack = { app.back() }) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { viewModel.onNameChanged(it) },
            label = { Text("Название кампании") },
            modifier = Modifier.fillMaxWidth()
        )
        SectionTitle("Отряд (2–5 охотников)")
        HunterClass.entries.forEach { hunterClass ->
            val member = state.squad.firstOrNull { it.hunterClass == hunterClass }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = member != null, onCheckedChange = { viewModel.onClassToggled(hunterClass) })
                val position = state.squad.indexOfFirst { it.hunterClass == hunterClass }
                Text(if (position >= 0) "${position + 1}. ${hunterClass.displayName}" else hunterClass.displayName)
            }
            if (member != null) {
                OutlinedTextField(
                    value = member.playerName,
                    onValueChange = { viewModel.onPlayerNameChanged(hunterClass, it) },
                    label = { Text("Игрок (${hunterClass.displayName})") },
                    placeholder = { Text(hunterClass.displayName) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        ErrorText(state.error)
        Hint(if (state.willUpload) "Кампания будет загружена на сервер и доступна на сайте." else "Кампания будет храниться на устройстве.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.onCreate() }, enabled = !state.creating, modifier = Modifier.fillMaxWidth()) {
            Text("Начать кампанию")
        }
    }
}
