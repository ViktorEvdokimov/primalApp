package com.primalapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.model.campaign.ResourceType
import com.primalapp.viewmodel.CampaignUiState
import com.primalapp.viewmodel.CampaignViewModel
import com.primalapp.viewmodel.ExchangeMode

@Composable
fun ExchangeDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    val type = state.exchangeResourceType ?: return
    val name = state.exchangeResourceName
    val currentHunter = state.hunters.getOrNull(state.selectedHunterIndex) ?: return

    AlertDialog(
        onDismissRequest = { viewModel.onCloseExchange() },
        title = { Text("Обмен: $name") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (state.exchangeMode) {
                    null -> ExchangeMenuContent(state, viewModel, type, name)
                    ExchangeMode.SELL -> SellContent(state, viewModel)
                    ExchangeMode.EXCHANGE -> ExchangeResourcesContent(state, viewModel, type, name)
                    ExchangeMode.GET_FROM_ALLY -> GetFromAllyContent(state, viewModel, currentHunter)
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun ExchangeMenuContent(state: CampaignUiState, viewModel: CampaignViewModel, type: ResourceType, name: String) {
    Text("Выберите действие:", fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    if (type == ResourceType.MATERIAL || type == ResourceType.ELEMENT) {
        Button(onClick = { viewModel.onExchangeModeSelected(ExchangeMode.SELL) }, modifier = Modifier.fillMaxWidth()) {
            Text("Продать предмет")
        }
        Spacer(Modifier.height(4.dp))
    }
    Button(onClick = { viewModel.onExchangeModeSelected(ExchangeMode.EXCHANGE) }, modifier = Modifier.fillMaxWidth()) {
        Text("Обменять на другой ресурс")
    }
    Spacer(Modifier.height(4.dp))
    Button(onClick = { viewModel.onExchangeModeSelected(ExchangeMode.GET_FROM_ALLY) }, modifier = Modifier.fillMaxWidth()) {
        Text("Получить у другого игрока")
    }
}

@Composable
private fun SellContent(state: CampaignUiState, viewModel: CampaignViewModel) {
    Text(state.exchangeMessage, fontSize = 14.sp)
    Spacer(Modifier.height(12.dp))
    Row {
        Button(onClick = { viewModel.onSellResource() }) { Text("Продать") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { viewModel.onCloseExchange() }) { Text("Отмена") }
    }
}

@Composable
private fun ExchangeResourcesContent(state: CampaignUiState, viewModel: CampaignViewModel, type: ResourceType, name: String) {
    Text("Доступные ресурсы для обмена (1:1):", fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    val resources = getSameTypeResources(state, type)
    resources.forEach { (resName, resQty) ->
        if (resQty >= 1 && resName != name) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${resName}: $resQty", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.onExchangeResource(resName, type) }, modifier = Modifier.height(32.dp)) {
                    Text("Обменять", fontSize = 12.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { viewModel.onCloseExchange() }, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
}

@Composable
private fun GetFromAllyContent(state: CampaignUiState, viewModel: CampaignViewModel, currentHunter: com.primalapp.model.campaign.CampaignHunter) {
    Text("Выберите игрока:", fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    state.hunters.filter { it.id != currentHunter.id }.forEach { hunter ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(hunter.playerName, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.onGetFromAlly(hunter.id) }, modifier = Modifier.height(32.dp)) {
                Text("Получить", fontSize = 12.sp)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { viewModel.onCloseExchange() }, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
}

private fun getSameTypeResources(state: CampaignUiState, type: ResourceType): Map<String, Int> {
    return when (type) {
        ResourceType.MATERIAL -> state.materials.mapKeys { it.key.name }
        ResourceType.PLANT -> state.plants.mapKeys { it.key.name }
        ResourceType.ELEMENT -> state.elements.mapKeys { it.key.name }
    }
}