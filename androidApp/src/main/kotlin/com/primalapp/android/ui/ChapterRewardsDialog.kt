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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.model.campaign.ChapterDecision
import com.primalapp.viewmodel.CampaignUiState
import com.primalapp.viewmodel.CampaignViewModel

@Composable
fun ChapterRewardsDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    val chapter = state.currentCampaign?.currentChapter ?: return
    val chapterInfo = state.chapterInfoByNumber[chapter]

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Награды главы $chapter") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (state.chapterRewardsMessage.isNotBlank()) {
                    Text(state.chapterRewardsMessage, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                if (chapterInfo == null) {
                    Text("Данные о главе отсутствуют.", fontSize = 14.sp)
                } else {
                    ChapterRewardsSummary(chapterInfo)
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
                onClick = { viewModel.onChapterRewardsAccept() },
                enabled = !state.isSaving
            ) {
                Text(if (state.isSaving) "Сохранение..." else "Принять")
            }
        },
        dismissButton = {
            Row {
                if (chapterInfo != null && chapterInfo.decisions.isNotEmpty()) {
                    chapterInfo.decisions.forEach { decision ->
                        ChapterDecisionButtons(
                            decision = decision,
                            viewModel = viewModel,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                TextButton(onClick = { viewModel.onChapterRewardsReject() }) {
                    Text("Отклонить")
                }
            }
        }
    )
}

@Composable
private fun ChapterDecisionButtons(
    decision: ChapterDecision,
    viewModel: CampaignViewModel,
    modifier: Modifier = Modifier
) {
    Text(decision.question, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = modifier)
    Spacer(Modifier.height(4.dp))
    Row(modifier = modifier) {
        decision.options.forEach { option ->
            TextButton(
                onClick = {
                    val isAchievementOption = option == decision.optionForAchievement
                    viewModel.onChapterDecisionSelected(option, if (isAchievementOption) decision.achievementOnOption else null)
                }
            ) {
                Text(option)
            }
        }
    }
}

@Composable
private fun ChapterRewardsSummary(chapterInfo: com.primalapp.model.campaign.ChapterInfo) {
    if (chapterInfo.rewards.isNotEmpty()) {
        Text("Материи:", fontWeight = FontWeight.Bold)
        chapterInfo.rewards.forEach { (mat, qty) ->
            Text("${mat.displayName}: $qty", fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
    }
    if (chapterInfo.rewardPlants.isNotEmpty()) {
        Text("Растения:", fontWeight = FontWeight.Bold)
        chapterInfo.rewardPlants.forEach { (plant, qty) ->
            Text("${plant.displayName}: $qty", fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
    }
    if (chapterInfo.openQuests.isNotEmpty()) {
        Text("Открываемые задания: ${chapterInfo.openQuests.sorted().joinToString(", ")}", fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
    }
    if (chapterInfo.conditionalOpenQuests.isNotEmpty()) {
        Text("Условные задания: ${chapterInfo.conditionalOpenQuests.size}", fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
    }
    if (chapterInfo.expireQuests.isNotEmpty()) {
        Text("Истекающие задания: ${chapterInfo.expireQuests.sorted().joinToString(", ")}", fontSize = 13.sp)
    }
}
