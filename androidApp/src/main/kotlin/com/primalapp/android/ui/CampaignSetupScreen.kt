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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.model.campaign.HunterClass
import com.primalapp.viewmodel.CampaignViewModel
import com.primalapp.viewmodel.CampaignUiState

@Composable
fun CampaignSetupScreen(state: CampaignUiState, viewModel: CampaignViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Новая кампания", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = state.campaignName,
            onValueChange = { viewModel.onCampaignNameChanged(it) },
            label = { Text("Название кампании") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Выберите классы охотников:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        HunterClass.entries.forEach { cls ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.selectedClasses.contains(cls),
                    onCheckedChange = { viewModel.onClassToggled(cls) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(cls.displayName, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        val errorText = state.error
        if (errorText != null) {
            Text(errorText, color = androidx.compose.ui.graphics.Color.Red)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = { viewModel.onStartCampaign() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Начать кампанию")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.onBackToMenu() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Назад")
        }
    }
}
