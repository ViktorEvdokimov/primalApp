package com.primalapp.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.primalapp.viewmodel.CampaignUiState
import com.primalapp.viewmodel.CampaignViewModel

/**
 * Решение главы (гл. 7 — тренировка в лагере у Волтьяра): отдельное окно после наград за задание
 * и до окна наград главы. Закрыть окно можно только ответом.
 */
@Composable
fun ChapterDecisionDialog(state: CampaignUiState, viewModel: CampaignViewModel) {
    val decision = state.pendingChapterDecision ?: return
    val chapter = state.currentCampaign?.currentChapter
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (chapter != null) "Решение главы $chapter" else "Решение главы") },
        text = { Text("${decision.question}?") },
        confirmButton = {
            Row {
                decision.options.forEach { option ->
                    TextButton(onClick = { viewModel.onChapterDecisionAnswered(option) }) {
                        Text(option)
                    }
                }
            }
        }
    )
}
