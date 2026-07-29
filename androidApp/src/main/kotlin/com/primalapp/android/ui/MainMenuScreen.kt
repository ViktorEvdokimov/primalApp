package com.primalapp.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.viewmodel.CampaignViewModel

@Composable
fun MainMenuScreen(viewModel: CampaignViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Primal: Пробуждение",
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
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = { viewModel.onQuickBattleSelected() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Режим экспедиции", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.onCampaignModeSelected() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Режим кампании", fontSize = 18.sp)
        }
    }
}
