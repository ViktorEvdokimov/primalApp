package com.primalapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.primalapp.model.campaign.Element
import com.primalapp.viewmodel.CampaignUiState
import com.primalapp.viewmodel.CampaignViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostVictoryDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    var bossName by remember { mutableStateOf("") }
    var selectedElement by remember { mutableStateOf<Element?>(null) }
    var elementDropdownExpanded by remember { mutableStateOf(false) }
    var selectedQuestId by remember { mutableStateOf<String?>(null) }
    var questDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Победа!") },
        text = {
            Column {
                Text("Задание выполнено!")
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bossName,
                    onValueChange = { bossName = it },
                    label = { Text("Имя поверженного босса") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = elementDropdownExpanded,
                    onExpandedChange = { elementDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedElement?.displayName ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Стихия босса") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = elementDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = elementDropdownExpanded,
                        onDismissRequest = { elementDropdownExpanded = false }
                    ) {
                        Element.entries.forEach { elem ->
                            DropdownMenuItem(
                                text = { Text(elem.displayName) },
                                onClick = {
                                    selectedElement = elem
                                    elementDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text("Следующее задание:")
                ExposedDropdownMenuBox(
                    expanded = questDropdownExpanded,
                    onExpandedChange = { questDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.availableQuestsForNext.find { it.id == selectedQuestId }?.name ?: "Выберите задание",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Задание") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = questDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = questDropdownExpanded,
                        onDismissRequest = { questDropdownExpanded = false }
                    ) {
                        state.availableQuestsForNext.forEach { quest ->
                            DropdownMenuItem(
                                text = { Text(quest.name) },
                                onClick = {
                                    selectedQuestId = quest.id
                                    questDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                val errorText = state.error
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorText, color = androidx.compose.ui.graphics.Color.Red)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.onVictoryBossNameChanged(bossName)
                selectedElement?.let { viewModel.onVictoryBossElementChanged(it) }
                selectedQuestId?.let { viewModel.onVictoryNextQuestSelected(it) }
                viewModel.onConfirmVictory()
            }) {
                Text("Продолжить")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.onBackToMenu() }) {
                Text("Выход")
            }
        }
    )
}
