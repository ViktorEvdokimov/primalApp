package com.primalapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.viewmodel.CampaignUiState
import com.primalapp.viewmodel.CampaignViewModel
import com.primalapp.viewmodel.QuestRewardsMode

@Composable
fun QuestRewardsDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    val mode = state.questRewardsMode ?: return
    val taskInfo = state.activeQuestNumber?.let { state.taskInfoByQuestNumber[it] }
    val isVictory = mode == QuestRewardsMode.VICTORY

    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (isVictory) "Награды за задание" else "Награды за поражение") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Задание ${state.activeQuestNumber ?: "?"}: ${taskInfo?.name ?: "—"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                if (taskInfo != null) {
                    val boss = taskInfo.bossName + (taskInfo.bossElement?.let { " (${it.displayName})" } ?: "")
                    if (boss.isNotBlank()) {
                        Text("Босс: $boss", fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                } else {
                    Text("Данные о задании отсутствуют.", fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))

                if (isVictory) {
                    VictoryRewardsSummary(state)
                } else {
                    DefeatRewardsSummary(taskInfo?.defeatOpenQuests.orEmpty())
                }

                val errorText = state.error
                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorText, color = androidx.compose.ui.graphics.Color.Red)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isVictory) viewModel.onQuestRewardsAccept()
                    else viewModel.onDefeatRewardsAccept()
                },
                enabled = !state.isSaving
            ) {
                Text(if (state.isSaving) "Сохранение..." else "Принять")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { viewModel.onQuestRewardsEdit() }) {
                    Text("Редактировать")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { viewModel.onQuestRewardsDismiss() }) {
                    Text("Выход")
                }
            }
        }
    )
}

@Composable
private fun VictoryRewardsSummary(state: CampaignUiState) {
    val taskInfo = state.activeQuestNumber?.let { state.taskInfoByQuestNumber[it] }

    Text("Ресурсы:", fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("Материи:")
    if (taskInfo?.victoryMaterials.isNullOrEmpty()) {
        Text("—", fontSize = 13.sp)
    } else {
        taskInfo!!.victoryMaterials.forEach { (mat, qty) ->
            Text("${mat.displayName}: $qty", fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("Растения:")
    if (taskInfo?.victoryPlants.isNullOrEmpty()) {
        Text("—", fontSize = 13.sp)
    } else {
        taskInfo!!.victoryPlants.forEach { (plant, qty) ->
            Text("${plant.displayName}: $qty", fontSize = 13.sp)
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Открываемые задания:", fontWeight = FontWeight.Bold)
    val openQuests = taskInfo?.victoryOpenQuests.orEmpty()
    if (openQuests.isEmpty()) {
        Text("—", fontSize = 13.sp)
    } else {
        Text(openQuests.sorted().joinToString(", "), fontSize = 13.sp)
    }

    val achievements = taskInfo?.victoryAchievements.orEmpty()
    if (achievements.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text("Достижения:", fontWeight = FontWeight.Bold)
        achievements.forEach { Text(it, fontSize = 13.sp) }
    }

    val cards = taskInfo?.victoryRewardCards.orEmpty()
    if (cards.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text("Карты наград: ${cards.joinToString(", ")}", fontSize = 13.sp)
    }

    val special = taskInfo?.victorySpecial.orEmpty()
    if (special.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(special, fontSize = 13.sp)
    }
}

@Composable
private fun DefeatRewardsSummary(openQuests: List<Int>) {
    Text("Открываемые задания:", fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    if (openQuests.isEmpty()) {
        Text("—", fontSize = 13.sp)
    } else {
        Text(openQuests.sorted().joinToString(", "), fontSize = 13.sp)
    }
}
