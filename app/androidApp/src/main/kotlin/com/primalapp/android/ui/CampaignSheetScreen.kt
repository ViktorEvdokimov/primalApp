package com.primalapp.android.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.android.R
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Полученные:", fontSize = 14.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { viewModel.onOpenAchievementEditor() }, modifier = Modifier.height(32.dp)) {
                Text("Редактировать", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (state.campaignAchievements.isEmpty()) {
            Text("Нет достижений.", fontSize = 14.sp)
        } else {
            state.campaignAchievements.forEach { achievement ->
                Text(
                    "${achievement.name}",
                    fontSize = 14.sp,
                    color = androidx.compose.ui.graphics.Color.Green
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
    if (state.showAchievementEditor) {
        AchievementEditDialog(state, viewModel)
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
private fun AchievementEditDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.onCloseAchievementEditor() },
        title = { Text("Редактирование достижений") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (state.campaignAchievements.isEmpty()) {
                    Text("Нет достижений.", fontSize = 14.sp)
                } else {
                    state.campaignAchievements.forEach { achievement ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(achievement.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            OutlinedButton(
                                onClick = { viewModel.onDeleteAchievement(achievement.id) },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Удалить", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.newAchievementName,
                    onValueChange = { viewModel.onNewAchievementNameChanged(it) },
                    label = { Text("Название достижения") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.onAddAchievement() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Добавить")
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.onCloseAchievementEditor() }) { Text("Готово") }
        },
        dismissButton = {
            OutlinedButton(onClick = { viewModel.onCloseAchievementEditor() }) { Text("Отмена") }
        }
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
            iconRes = mat.iconRes(),
            onDecrement = { viewModel.onResourceDecrement(ResourceType.MATERIAL, mat.name) },
            onIncrement = { viewModel.onResourceIncrement(ResourceType.MATERIAL, mat.name) }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("Растения:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Plant.entries.forEach { plant ->
        val qty = state.plants[plant] ?: 0
        ResourceRow(
            label = "${plant.displayName}: $qty",
            iconRes = plant.iconRes(),
            onDecrement = { viewModel.onResourceDecrement(ResourceType.PLANT, plant.name) },
            onIncrement = { viewModel.onResourceIncrement(ResourceType.PLANT, plant.name) }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("Стихии:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Element.entries.forEach { elem ->
        val qty = state.elements[elem] ?: 0
        ResourceRow(
            label = "${elem.displayName}: $qty",
            iconRes = elem.iconRes(),
            onDecrement = { viewModel.onResourceDecrement(ResourceType.ELEMENT, elem.name) },
            onIncrement = { viewModel.onResourceIncrement(ResourceType.ELEMENT, elem.name) }
        )
    }
}

@Composable
private fun ResourceRow(
    label: String,
    iconRes: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Button(onClick = onDecrement, modifier = Modifier.height(32.dp)) {
            Text("-", fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Button(onClick = onIncrement, modifier = Modifier.height(32.dp)) {
            Text("+", fontSize = 12.sp)
        }
    }
}

private fun Material.iconRes(): Int = when (this) {
    Material.SCALES -> R.drawable.scales
    Material.BONES -> R.drawable.bones
    Material.BLOOD -> R.drawable.blood
    Material.ZIMIA -> R.drawable.zimia
    Material.IRIDIA -> R.drawable.iridia
    Material.ZLATIA -> R.drawable.zlatia
}

private fun Plant.iconRes(): Int = when (this) {
    Plant.NILLEA -> R.drawable.nillea
    Plant.TARMARET -> R.drawable.tarmaret
    Plant.ALBALACEA -> R.drawable.albalacea
    Plant.MELLIS -> R.drawable.mellis
    Plant.ANTHEMON -> R.drawable.anthemon
    Plant.SELICORNIA -> R.drawable.selicornia
}

private fun Element.iconRes(): Int = when (this) {
    Element.FIRE -> R.drawable.fire
    Element.HORN -> R.drawable.horn
    Element.CORAL -> R.drawable.coral
    Element.CRYSTAL -> R.drawable.crystal
    Element.LIGHTNING -> R.drawable.lightning
    Element.METAL -> R.drawable.metal
    Element.FEATHER -> R.drawable.feather
    Element.POISON -> R.drawable.poison
    Element.ICE -> R.drawable.ice
}
