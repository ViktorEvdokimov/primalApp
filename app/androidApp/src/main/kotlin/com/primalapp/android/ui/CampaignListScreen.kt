package com.primalapp.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.viewmodel.CampaignViewModel
import com.primalapp.viewmodel.CampaignUiState

@Composable
fun CampaignListScreen(state: CampaignUiState, viewModel: CampaignViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Ваши кампании", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Кампаний: ${state.campaigns.size} / 10",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.campaigns) { campaign ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.onCampaignSelected(campaign.id) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(campaign.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "Глава ${campaign.currentChapter} | Кузня ${campaign.forgeLevel} | Лаб ${campaign.labLevel}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.onDeleteCampaign(campaign.id) }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.onNewCampaignRequested() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.campaigns.size < 10
        ) {
            Text("Новая кампания")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.onBackToMenu() }, modifier = Modifier.fillMaxWidth()) {
            Text("Назад в меню")
        }
    }
}
