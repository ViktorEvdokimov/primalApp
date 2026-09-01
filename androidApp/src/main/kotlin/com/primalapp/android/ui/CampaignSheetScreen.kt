package com.primalapp.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.ResourceType
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.viewmodel.CampaignUiState
import com.primalapp.viewmodel.CampaignViewModel

@Composable
private fun questDisplayLabel(state: CampaignUiState, quest: com.primalapp.model.campaign.Quest): String {
    val taskInfo = state.taskInfoByQuestNumber[quest.questNumber]
    val prefix = "${quest.questNumber}. "
    return if (taskInfo != null) {
        val boss = taskInfo.bossName + (taskInfo.bossElement?.let { " (${it.displayName})" } ?: "")
        if (boss.isBlank()) "$prefix${taskInfo.name}" else "$prefix${taskInfo.name} — $boss"
    } else {
        "${prefix}Задание ${quest.questNumber}"
    }
}

@Composable
fun CampaignSheetScreen(state: CampaignUiState, viewModel: CampaignViewModel) {
    val campaign = state.currentCampaign ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(campaign.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Глава ${campaign.currentChapter}", fontSize = 16.sp)
            Text("Кузня ${campaign.forgeLevel}", fontSize = 16.sp)
            Text("Лаб ${campaign.labLevel}", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Глава:")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.onUpdateChapter(campaign.currentChapter - 1) }, modifier = Modifier.padding(end = 4.dp)) {
                    Text("-")
                }
                Text("${campaign.currentChapter}")
                Button(onClick = { viewModel.onUpdateChapter(campaign.currentChapter + 1) }, modifier = Modifier.padding(start = 4.dp)) {
                    Text("+")
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (state.hunters.isNotEmpty()) {
            Text("Охотники:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.hunters.forEachIndexed { index, hunter ->
                    val isSelected = index == state.selectedHunterIndex
                    Button(
                        onClick = { viewModel.onHunterSelected(index) },
                        colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("${hunter.playerName}\n(${hunter.className.displayName})", fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SkillTreeSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            ResourcesSection(state, viewModel)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Задания:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Открытые:", fontSize = 14.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { viewModel.onOpenQuestEditor() }, modifier = Modifier.height(32.dp)) {
                Text("Редактировать", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (state.campaignQuests.isEmpty()) {
            Text("Нет открытых заданий.")
        } else {
            state.campaignQuests.forEach { quest ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        questDisplayLabel(state, quest),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { viewModel.onToggleQuestCompleted(quest.id) }, modifier = Modifier.height(32.dp)) {
                        Text("Выполнено", fontSize = 12.sp)
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Достижения:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        if (state.campaignAchievements.isEmpty()) {
            Text("Нет достижений.", fontSize = 14.sp)
        } else {
            state.campaignAchievements.forEach { achievement ->
                Text(
                    "${achievement.name}",
                    fontSize = 14.sp,
                    color = if (achievement.unlocked) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Gray
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Поверженные боссы:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        if (state.campaignTrophies.isEmpty()) {
            Text("Нет поверженных боссов.")
        } else {
            state.campaignTrophies.forEach { trophy ->
                Text(
                    trophy.bossName + (trophy.element?.let { " (${it.displayName})" } ?: ""),
                    fontSize = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Стихии поверженных боссов:", fontWeight = FontWeight.Bold)
        val defeatedElements = state.campaignTrophies.mapNotNull { it.element }.distinct()
        if (defeatedElements.isEmpty()) {
            Text("Нет.")
        } else {
            Text(defeatedElements.joinToString(", ") { it.displayName }, fontSize = 14.sp)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Заметки:", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = state.notes,
            onValueChange = { viewModel.onNotesChanged(it) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            maxLines = 10
        )
        Button(onClick = { viewModel.onSaveNotes() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Сохранить заметки")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.onStartCampaignBattle() },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Начать бой", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (state.saveMessage.isNotEmpty()) {
            Text(state.saveMessage, color = MaterialTheme.colorScheme.secondary)
        }

        Button(onClick = { viewModel.onBackToMenu() }, modifier = Modifier.fillMaxWidth()) {
            Text("В главное меню")
        }
    }

    if (state.showQuestSelectDialog) {
        QuestSelectDialog(state, viewModel)
    }
    if (state.showQuestEditDialog) {
        QuestEditDialog(state, viewModel)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuestEditDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.onCancelQuestEdits() },
        title = { Text("Редактирование заданий") },
        text = {
            Column {
                Text("Отметьте номера открытых заданий:")
                val allNumbers = (1..49).toList()
                FlowRow {
                    allNumbers.forEach { number ->
                        val checked = state.editedQuestNumbers.contains(number)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(52.dp)) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.onToggleEditedQuest(number) }
                            )
                            Text("$number", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { viewModel.onSaveQuestEdits() }) { Text("Сохранить") } },
        dismissButton = { OutlinedButton(onClick = { viewModel.onCancelQuestEdits() }) { Text("Отмена") } }
    )
}

@Composable
private fun QuestSelectDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.onCloseQuestSelectDialog() },
        title = { Text("Выбор задания") },
        text = {
            Column {
                if (state.availableQuestsForNext.isEmpty()) {
                    Text("Нет открытых заданий.")
                } else {
                    state.availableQuestsForNext.forEach { quest ->
                        TextButton(
                            onClick = { viewModel.onActiveQuestSelected(quest.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(questDisplayLabel(state, quest))
                        }
                    }
                }
                TextButton(
                    onClick = { viewModel.onActiveQuestSelected(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Продолжить без задания")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { viewModel.onCloseQuestSelectDialog() }) { Text("Отмена") }
        }
    )
}

@Composable
private fun SkillTreeSection(state: CampaignUiState, viewModel: CampaignViewModel) {
    Text("Древо навыков:", fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    SkillBranch.entries.forEach { branch ->
        val tier1 = state.skills.find { it.branch == branch && it.tier == 1 }
        val tier2 = state.skills.find { it.branch == branch && it.tier == 2 }
        if (tier1 != null && tier2 != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ветвь ${branch.letter}", modifier = Modifier.width(60.dp))
                SkillCheckbox(tier1.unlocked, "${branch.letter}1") {
                    viewModel.onUnlockSkill(branch, 1)
                }
                Spacer(modifier = Modifier.width(8.dp))
                SkillCheckbox(tier2.unlocked, "${branch.letter}2") {
                    viewModel.onUnlockSkill(branch, 2)
                }
            }
        }
    }
}

@Composable
private fun SkillCheckbox(unlocked: Boolean, label: String, onClick: () -> Unit) {
    val color = if (unlocked) Color(0xFF4CAF50) else Color(0xFFBDBDBD)
    Button(
        onClick = onClick,
        modifier = Modifier.padding(2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun ResourcesSection(state: CampaignUiState, viewModel: CampaignViewModel) {
    Text("Ресурсы:", fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))

    Text("Материи:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Material.entries.forEach { mat ->
        val qty = state.materials[mat] ?: 0
        ResourceRow(
            label = "${mat.displayName}: $qty",
            onDecrement = { viewModel.onResourceDecrement(ResourceType.MATERIAL, mat.name) },
            onIncrement = { viewModel.onResourceIncrement(ResourceType.MATERIAL, mat.name) },
            onExchange = { viewModel.onOpenExchange(ResourceType.MATERIAL, mat.name) }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("Растения:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Plant.entries.forEach { plant ->
        val qty = state.plants[plant] ?: 0
        ResourceRow(
            label = "${plant.displayName}: $qty",
            onDecrement = { viewModel.onResourceDecrement(ResourceType.PLANT, plant.name) },
            onIncrement = { viewModel.onResourceIncrement(ResourceType.PLANT, plant.name) },
            onExchange = { viewModel.onOpenExchange(ResourceType.PLANT, plant.name) }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("Стихии:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Element.entries.forEach { elem ->
        val qty = state.elements[elem] ?: 0
        ResourceRow(
            label = "${elem.displayName}: $qty",
            onDecrement = { viewModel.onResourceDecrement(ResourceType.ELEMENT, elem.name) },
            onIncrement = { viewModel.onResourceIncrement(ResourceType.ELEMENT, elem.name) },
            onExchange = { viewModel.onOpenExchange(ResourceType.ELEMENT, elem.name) }
        )
    }
}

@Composable
private fun ResourceRow(
    label: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onExchange: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Button(onClick = onDecrement, modifier = Modifier.height(32.dp)) {
            Text("-", fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Button(onClick = onIncrement, modifier = Modifier.height(32.dp)) {
            Text("+", fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Button(onClick = onExchange, modifier = Modifier.height(32.dp)) {
            Text("Обмен", fontSize = 12.sp)
        }
    }
}
