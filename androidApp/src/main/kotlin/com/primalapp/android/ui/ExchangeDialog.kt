package com.primalapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.primalapp.viewmodel.CampaignUiState
import com.primalapp.viewmodel.CampaignViewModel

@Composable
fun ExchangeDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.onCloseExchange() },
        title = { Text("Обмен ресурсом") },
        text = {
            Column {
                Text("Ресурс: ${state.exchangeResourceName}")
                Text("Тип: ${state.exchangeResourceType?.name ?: ""}")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.exchangeAmount,
                    onValueChange = { viewModel.onExchangeAmountChanged(it) },
                    label = { Text("Количество") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { /* TODO: implement exchange flow with ally selection */ }) {
                Text("Получить у союзника")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.onCloseExchange() }) {
                Text("Отмена")
            }
        }
    )
}
