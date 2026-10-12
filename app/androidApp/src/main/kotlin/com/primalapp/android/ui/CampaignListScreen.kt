package com.primalapp.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.sync.CampaignAccess
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.CampaignListViewModel

/** Кампании: на устройстве и с сервера (свои и открытые по ссылке). */
@Composable
fun CampaignListScreen(app: AppViewModel) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { CampaignListViewModel(app, scope) }
    val state by viewModel.state.collectAsState()
    val connection by app.connection.collectAsState()

    ScreenScaffold(title = "Кампании", onBack = { app.back() }) {
        Hint(connection.label)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { viewModel.onNew() }, modifier = Modifier.fillMaxWidth()) { Text("Новая кампания") }
        Spacer(Modifier.height(8.dp))
        if (state.items.isEmpty()) {
            Hint("Кампаний пока нет.")
        }
        state.items.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.onOpen(item.localId) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(item.chapterTitle + if (item.pendingTransition) " · Ждёт перехода главы" else if (item.completed) " · Пройдена" else "")
                    if (item.squad.isNotEmpty()) Text(item.squad, fontSize = 13.sp)
                    Text(item.accessLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.unsynced) Text("Есть изменения, не отправленные на сервер", fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
                    item.syncMessage?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.access == CampaignAccess.LOCAL && !item.uploadRequested && state.canUpload) {
                            TextButton(onClick = { viewModel.onUpload(item) }) { Text("Загрузить на сервер") }
                        }
                        if (item.canDelete) {
                            TextButton(onClick = { viewModel.onDeleteRequested(item) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }

    state.confirmDelete?.let { item ->
        ConfirmDialog(
            title = "Удалить кампанию?",
            lines = listOf(
                if (item.access == CampaignAccess.OWNER) "Кампания «${item.name}» будет удалена на сервере и у всех участников."
                else "Кампания «${item.name}» будет удалена с устройства."
            ),
            confirm = "Удалить",
            onConfirm = { viewModel.onDeleteConfirmed() },
            onDismiss = { viewModel.onDeleteDismissed() }
        )
    }
}
