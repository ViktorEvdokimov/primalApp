package com.primalapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.model.campaign.Element
import com.primalapp.viewmodel.CampaignUiState
import com.primalapp.viewmodel.CampaignViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostVictoryDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    var elementDropdownExpanded by remember { mutableStateOf(false) }
    var bossDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Победа!") },
        text = {
            Column {
                Text("Задание выполнено!")
                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = bossDropdownExpanded,
                    onExpandedChange = { bossDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.bossName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Имя поверженного босса") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bossDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = bossDropdownExpanded,
                        onDismissRequest = { bossDropdownExpanded = false }
                    ) {
                        state.defeatedBosses.forEach { boss ->
                            DropdownMenuItem(
                                text = { Text(boss) },
                                onClick = {
                                    viewModel.onVictoryBossSelected(boss)
                                    bossDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (!state.bossHasNoElement) {
                    ExposedDropdownMenuBox(
                        expanded = elementDropdownExpanded,
                        onExpandedChange = { elementDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.bossElement?.displayName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Стихия босса") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = elementDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = elementDropdownExpanded,
                            onDismissRequest = { elementDropdownExpanded = false }
                        ) {
                            Element.entries.forEach { elem ->
                                DropdownMenuItem(
                                    text = { Text(elem.displayName) },
                                    onClick = {
                                        viewModel.onVictoryBossElementChanged(elem)
                                        elementDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text("Открытые задания:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..49).forEach { number ->
                        val checked = state.selectedQuestNumbers.contains(number)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(52.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.onVictoryQuestToggled(number) },
                                modifier = Modifier.size(24.dp)
                            )
                            Text("$number", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                val errorText = state.error
                if (errorText != null) {
                    Text(errorText, color = androidx.compose.ui.graphics.Color.Red)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.onConfirmVictory() }) {
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
