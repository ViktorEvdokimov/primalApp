package com.primalapp.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.primalapp.campaign.InventoryItem
import com.primalapp.campaign.ItemKind
import com.primalapp.campaign.SkillStep
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.CampaignSheetState
import com.primalapp.viewmodel.CampaignSheetViewModel
import com.primalapp.viewmodel.ExchangeRequest
import com.primalapp.viewmodel.ItemEditorState
import com.primalapp.viewmodel.QuestRow
import com.primalapp.viewmodel.Screen
import com.primalapp.viewmodel.SheetTab
import com.primalapp.viewmodel.SheetPrompt

/** Лист кампании: шапка, бой, кузница, лаборатория, вкладки. */
@Composable
fun CampaignSheetScreen(app: AppViewModel, localId: Long) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(localId) { CampaignSheetViewModel(app, localId, scope) }
    val state by viewModel.state.collectAsState()
    var exchange by remember { mutableStateOf<ExchangeRequest?>(null) }
    DisposableEffect(viewModel) { onDispose { viewModel.onClose() } }

    if (state.loaded && state.missing) {
        ScreenScaffold(title = "Кампания", onBack = { app.back() }) { Hint("Кампания недоступна.") }
        return
    }

    ScreenScaffold(
        title = state.name,
        onBack = { app.back() },
        actions = {
            if (state.onServer) TextButton(onClick = { viewModel.onRefresh() }) { Text("Обновить") }
        }
    ) {
        Header(state, viewModel)
        BattleBlock(app, localId, state, viewModel)
        Divider()
        ScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = 0.dp) {
            SheetTab.entries.forEach { tab ->
                Tab(selected = state.tab == tab, onClick = { viewModel.onTabSelected(tab) }, text = { Text(tab.title) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when (state.tab) {
            SheetTab.HUNTERS -> HuntersTab(state, viewModel) { exchange = it }
            SheetTab.QUESTS -> QuestsTab(state, viewModel)
            SheetTab.ACHIEVEMENTS -> AchievementsTab(state, viewModel)
            SheetTab.TROPHIES -> TrophiesTab(state)
            SheetTab.HISTORY -> HistoryTab(state, viewModel)
            SheetTab.NOTES -> NotesTab(state, viewModel)
        }
        if (state.canDelete) {
            Divider()
            TextButton(onClick = { viewModel.onDeleteRequested() }) { Text("Удалить кампанию", color = MaterialTheme.colorScheme.error) }
        }
    }

    exchange?.let { request -> ExchangeDialog(app, localId, request) { exchange = null } }
    SheetDialogs(state, viewModel)
}

@Composable
private fun Header(state: CampaignSheetState, viewModel: CampaignSheetViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(state.chapterTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (!state.pendingTransition) TextButton(onClick = { viewModel.onChangeChapterRequested() }) { Text("Изменить главу") }
    }
    Text("Кузня: ${state.forgeLevel} · Лаборатория: ${state.labLevel} · Уровень враждебности: ${state.difficulty}", fontSize = 14.sp)
    state.finalBossName?.let { Text("Следующий бой — финальный: $it", fontSize = 14.sp) }
    when {
        state.access.name == "LINK" -> Hint("Кампания по ссылке · владелец ${state.ownerName.orEmpty()}")
        state.detached -> Hint("Кампания на сервере · вход не выполнен: изменения уйдут на сервер после входа в её аккаунт")
        state.onServer -> Hint("Кампания на сервере" + if (state.unsynced) " · есть неотправленные изменения" else "")
        else -> Hint("Кампания только на устройстве")
    }
    state.syncMessage?.let { Banner(it, error = true) }
}

@Composable
private fun BattleBlock(app: AppViewModel, localId: Long, state: CampaignSheetState, viewModel: CampaignSheetViewModel) {
    val context = LocalContext.current
    Spacer(Modifier.height(8.dp))
    state.activeBattles.forEach { Banner(it) }
    when {
        state.completed -> Banner("Кампания пройдена! Пробуждённый повержен.")
        state.pendingTransition -> Button(onClick = { app.navigate(Screen.Transition(localId)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Переход главы")
        }
        else -> Button(onClick = { viewModel.onStartBattle() }, modifier = Modifier.fillMaxWidth()) { Text("Начать бой") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
        OutlinedButton(onClick = { app.navigate(Screen.Forge(localId)) }, contentPadding = padding, modifier = Modifier.weight(1f)) {
            Text("Кузница", maxLines = 1)
        }
        OutlinedButton(onClick = { app.navigate(Screen.Lab(localId)) }, contentPadding = padding, modifier = Modifier.weight(1.3f)) {
            Text("Лаборатория", maxLines = 1)
        }
        OutlinedButton(onClick = { app.navigate(Screen.Info()) }, contentPadding = padding, modifier = Modifier.weight(0.8f)) {
            Text("Инфо", maxLines = 1)
        }
    }
    // «Поделиться» и «Ссылка на сайт» — всегда: без входа или без сервера экран предложит войти или загрузить кампанию
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { viewModel.onShareOpened() }, modifier = Modifier.weight(1f)) { Text("Поделиться") }
        OutlinedButton(onClick = { viewModel.onSiteLink() }, modifier = Modifier.weight(1f)) { Text("Ссылка на сайт") }
    }
    androidx.compose.runtime.LaunchedEffect(state.shareText) {
        state.shareText?.let { link ->
            shareText(context, "Кампания «${state.name}» на сайте", link)
            viewModel.onShareTextHandled()
        }
    }
}

// region Охотники

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HuntersTab(state: CampaignSheetState, viewModel: CampaignSheetViewModel, onExchange: (ExchangeRequest) -> Unit) {
    HunterChips(state.hunters, state.hunter?.hunterClass, state.mainHunter) { viewModel.onHunterSelected(it) }
    val hunter = state.hunter ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = state.isMainHunter, onCheckedChange = { viewModel.onMainHunterToggled(it) })
        Text("Основной персонаж")
    }
    Hint("Выбран по умолчанию в листе, кузнице и лаборатории и выделен жирным. У каждого участника свой.")
    TextButton(onClick = { viewModel.onRenameHunterRequested() }) { Text("Изменить имя игрока") }
    SectionTitle("Древо навыков")
    SkillBranch.entries.forEach { branch ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ветвь ${branch.letter}", modifier = Modifier.width(80.dp))
            (1..2).forEach { tier ->
                val unlocked = SkillStep(branch, tier) in hunter.skills
                val enabled = if (unlocked) state.lockable(branch, tier) else state.unlockable(branch, tier)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = unlocked, enabled = enabled, onCheckedChange = { viewModel.onSkillClicked(branch, tier) })
                    Text("Ступень $tier", fontSize = 13.sp)
                }
            }
        }
    }
    SectionTitle("Материи")
    ResourceCode.materials.forEach { ResourceRow(it, hunter.quantity(it), viewModel) }
    SectionTitle("Растения")
    ResourceCode.plants.forEach { ResourceRow(it, hunter.quantity(it), viewModel) }
    SectionTitle("Стихии")
    ResourceCode.elements.forEach { ResourceRow(it, hunter.quantity(it), viewModel) }
    OutlinedButton(onClick = { onExchange(ExchangeRequest(hunter.hunterClass, emptyList())) }, modifier = Modifier.fillMaxWidth()) {
        Text("Обменять ресурсы")
    }
    SectionTitle("Инвентарь")
    if (hunter.items.isEmpty()) Hint("Предметов нет.")
    ItemKind.entries.forEach { kind ->
        val items = hunter.items.filter { it.kind == kind }
        if (items.isNotEmpty()) {
            Text(kind.displayName, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            items.forEach { item -> ItemRow(item, viewModel) }
        }
    }
    TextButton(onClick = { viewModel.onAddItem() }) { Text("Добавить предмет") }
}

@Composable
private fun ResourceRow(code: ResourceCode, quantity: Int, viewModel: CampaignSheetViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        ResourceIcon(code, 24)
        Spacer(Modifier.width(8.dp))
        Text(code.displayName, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { viewModel.onResourceChanged(code, -1) }, enabled = quantity > 0, modifier = Modifier.height(32.dp)) {
            Text("−")
        }
        Text("$quantity", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = { viewModel.onResourceChanged(code, 1) }, modifier = Modifier.height(32.dp)) { Text("+") }
    }
}

@Composable
private fun ItemRow(item: InventoryItem, viewModel: CampaignSheetViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ElementIcon(item.element, 18)
        Text(item.name + (item.level?.let { " · $it ур." } ?: ""), modifier = Modifier.weight(1f).padding(start = 4.dp))
        TextButton(onClick = { viewModel.onEditItem(item) }) { Text("✎") }
        TextButton(onClick = { viewModel.onRemoveItem(item) }) { Text("×") }
    }
}

// endregion

// region Задания, достижения, трофеи, история, заметки

@Composable
private fun QuestLine(quest: QuestRow, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        ElementIcon(quest.element, 18)
        Spacer(Modifier.width(4.dp))
        Text(quest.label, modifier = Modifier.weight(1f))
        ExpansionBadge(quest.expansion)
        trailing()
    }
}

@Composable
private fun QuestsTab(state: CampaignSheetState, viewModel: CampaignSheetViewModel) {
    SectionTitle("Открытые")
    if (state.openQuests.isEmpty()) Hint("Открытых заданий нет.")
    state.openQuests.forEach { quest ->
        QuestLine(quest) { TextButton(onClick = { viewModel.onCompleteQuest(quest.number) }) { Text("Выполнено") } }
    }
    if (state.completedQuests.isNotEmpty()) {
        SectionTitle("Выполненные:")
        state.completedQuests.forEach { quest ->
            QuestLine(quest) { TextButton(onClick = { viewModel.onReopenQuest(quest.number) }) { Text("Отмена") } }
        }
    }
    if (state.expiredQuests.isNotEmpty()) {
        SectionTitle("Истёкшие:")
        state.expiredQuests.forEach { QuestLine(it) }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { viewModel.onOpenQuestEditor() }, modifier = Modifier.fillMaxWidth()) { Text("Редактировать задания") }
}

@Composable
private fun AchievementsTab(state: CampaignSheetState, viewModel: CampaignSheetViewModel) {
    if (state.achievements.isEmpty()) Hint("Достижений пока нет.")
    state.achievements.forEach { achievement ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(achievement.name + if (achievement.custom) " (своё)" else "", modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.onDeleteAchievement(achievement) }) { Text("Удалить") }
        }
    }
    SectionTitle("Добавить достижение")
    OutlinedTextField(
        value = state.achievementInput,
        onValueChange = { viewModel.onAchievementInput(it) },
        label = { Text("Название") },
        modifier = Modifier.fillMaxWidth()
    )
    state.achievementSuggestions.forEach { suggestion ->
        TextButton(onClick = { viewModel.onAddAchievement(suggestion) }) { Text(suggestion) }
    }
    Button(onClick = { viewModel.onAddAchievement() }, enabled = state.achievementInput.isNotBlank()) { Text("Добавить") }
}

@Composable
private fun TrophiesTab(state: CampaignSheetState) {
    if (state.trophies.isEmpty()) Hint("Трофеев пока нет.")
    state.trophies.forEach { trophy ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            ElementIcon(trophy.element)
            Spacer(Modifier.width(6.dp))
            Text("${trophy.bossName} — " + trophy.chapters.joinToString(", ") { if (it == 0) "пролог" else "глава $it" })
        }
    }
}

@Composable
private fun HistoryTab(state: CampaignSheetState, viewModel: CampaignSheetViewModel) {
    if (state.battles.isEmpty()) Hint("Боёв пока не было.")
    state.battles.forEach { battle ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("${battle.title} — ${battle.status}", fontWeight = FontWeight.Medium)
            Text(battle.details, fontSize = 13.sp)
            if (battle.inProgress) TextButton(onClick = { viewModel.onAbandonBattle(battle.id) }) { Text("Снять отметку") }
        }
    }
}

@Composable
private fun NotesTab(state: CampaignSheetState, viewModel: CampaignSheetViewModel) {
    OutlinedTextField(
        value = state.notes,
        onValueChange = { viewModel.onNotesChanged(it) },
        label = { Text("Заметки") },
        modifier = Modifier.fillMaxWidth().height(260.dp)
    )
    state.notesStatus?.let { Hint(it) }
}

// endregion

// region Окна

@Composable
private fun SheetDialogs(state: CampaignSheetState, viewModel: CampaignSheetViewModel) {
    state.completedResult?.let { (text, rules) ->
        AlertDialog(
            onDismissRequest = { viewModel.onCompletedResultDismissed() },
            title = { Text("Задание выполнено") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(text)
                    RulesSection("Условные задания:", rules)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.onCompletedResultDismissed() }) { Text("OK") } }
        )
    }
    state.questEditor?.let { selected -> QuestEditorDialog(state, selected, viewModel) }
    if (state.chapterDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onChapterDialogDismissed() },
            title = { Text("Изменить главу") },
            text = {
                Column {
                    Hint("Исправление ошибки: 0 — пролог, 1–11. Бои, начатые раньше, после этого не принимаются.")
                    OutlinedTextField(value = state.chapterInput, onValueChange = { viewModel.onChapterInput(it) }, label = { Text("Глава") })
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.onChapterConfirmed() }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { viewModel.onChapterDialogDismissed() }) { Text("Отмена") } }
        )
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = "Удалить кампанию?",
            lines = listOf(if (state.onServer) "Кампания будет удалена на сервере и у всех участников." else "Кампания будет удалена с устройства."),
            confirm = "Удалить",
            onConfirm = { viewModel.onDeleteConfirmed() },
            onDismiss = { viewModel.onDeleteDismissed() }
        )
    }
    state.itemEditor?.let { ItemEditorDialog(it, viewModel) }
    state.hunterNameInput?.let { name ->
        AlertDialog(
            onDismissRequest = { viewModel.onHunterNameDismissed() },
            title = { Text("Имя игрока") },
            text = {
                OutlinedTextField(value = name, onValueChange = { viewModel.onHunterNameInput(it) },
                    label = { Text(state.hunter?.hunterClass?.displayName.orEmpty()) }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { viewModel.onHunterNameSaved() }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { viewModel.onHunterNameDismissed() }) { Text("Отмена") } }
        )
    }
    state.share?.let { ShareDialog(state, viewModel) }
    state.prompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.onPromptDismissed() },
            title = { Text(if (prompt is SheetPrompt.Login) "Вход в аккаунт" else "Загрузить на сервер") },
            text = { Text(prompt.text) },
            confirmButton = {
                when (prompt) {
                    is SheetPrompt.Login -> TextButton(onClick = { viewModel.onLoginRequested() }) { Text("Войти") }
                    is SheetPrompt.Upload -> TextButton(onClick = { viewModel.onUploadConfirmed() }) { Text("Загрузить") }
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.onPromptDismissed() }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun QuestEditorDialog(state: CampaignSheetState, selected: Set<Int>, viewModel: CampaignSheetViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.onQuestEditorDismissed() },
        title = { Text("Открытые задания") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Hint("Выполненные и истёкшие задания не меняются.")
                state.allQuests.forEach { quest ->
                    val locked = quest.number in state.questEditorLocked
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = quest.number in selected || locked, enabled = !locked,
                            onCheckedChange = { viewModel.onQuestEditorToggled(quest.number) })
                        Text("${quest.number}. ${quest.name}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        ExpansionBadge(quest.expansion)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.onQuestEditorSaved() }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { viewModel.onQuestEditorDismissed() }) { Text("Отмена") } }
    )
}

@Composable
private fun ItemEditorDialog(editor: ItemEditorState, viewModel: CampaignSheetViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.onItemEditorDismissed() },
        title = { Text(if (editor.key == null) "Добавить предмет" else "Изменить предмет") },
        text = {
            Column {
                if (editor.key == null) {
                    Dropdown(
                        label = "Вид",
                        selectedLabel = editor.kind.displayName,
                        options = ItemKind.entries.map { it to it.displayName },
                        onSelected = { viewModel.onItemEditorChanged(editor.copy(kind = it, level = if (it == ItemKind.REWARD) null else editor.level ?: 1)) }
                    )
                }
                OutlinedTextField(value = editor.name, onValueChange = { viewModel.onItemEditorChanged(editor.copy(name = it.take(100))) },
                    label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                if (editor.kind != ItemKind.REWARD) {
                    Dropdown(
                        label = "Уровень",
                        selectedLabel = "${editor.level ?: 1} ур.",
                        options = (1..3).map { it to "$it ур." },
                        onSelected = { viewModel.onItemEditorChanged(editor.copy(level = it)) }
                    )
                }
                Hint("Без оплаты: чтобы список совпадал с картами на столе.")
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.onItemEditorSaved() }, enabled = editor.name.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { viewModel.onItemEditorDismissed() }) { Text("Отмена") } }
    )
}

/** «Поделиться»: ссылка-приглашение, QR-код, кто присоединился; перевыпуск и отзыв — с подтверждением. */
@Composable
private fun ShareDialog(state: CampaignSheetState, viewModel: CampaignSheetViewModel) {
    val share = state.share ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { viewModel.onShareClosed() },
        title = { Text("Поделиться кампанией") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    share.loading -> CircularProgressIndicator()
                    share.link == null -> {
                        Hint("По ссылке-приглашению участники открывают кампанию на сайте или в приложении и могут её править.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.onShareCreate() }) { Text("Создать ссылку") }
                    }
                    else -> {
                        val link = share.link!!
                        Text(link.url, fontSize = 13.sp)
                        val qr = remember(link.url) { qrBitmap(link.url) }
                        qr?.let { Image(it.asImageBitmap(), contentDescription = "QR-код ссылки", modifier = Modifier.size(200.dp).padding(8.dp)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { shareText(context, "Приглашение в кампанию «${state.name}»", link.url) }) { Text("Отправить") }
                            OutlinedButton(onClick = { copyText(context, link.url) }) { Text("Копировать") }
                        }
                        if (link.joined.isNotEmpty()) {
                            SectionTitle("Присоединились:")
                            link.joined.forEach { Text("${it.name} (${if (it.kind == "GUEST") "гость" else "аккаунт"})", fontSize = 14.sp) }
                        }
                        Row {
                            TextButton(onClick = { viewModel.onShareReissueRequested() }) { Text("Перевыпустить") }
                            TextButton(onClick = { viewModel.onShareRevokeRequested() }) { Text("Отозвать") }
                        }
                    }
                }
                ErrorText(share.error)
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.onShareClosed() }) { Text("Закрыть") } }
    )
    if (share.confirmReissue) {
        ConfirmDialog("Перевыпустить ссылку?", listOf("Доступ по старой ссылке пропадёт сразу."), "Перевыпустить",
            onConfirm = { viewModel.onShareCreate() }, onDismiss = { viewModel.onShareConfirmDismissed() })
    }
    if (share.confirmRevoke) {
        ConfirmDialog("Отозвать ссылку?", listOf("Доступ по ссылке сразу пропадёт у всех участников."), "Отозвать",
            onConfirm = { viewModel.onShareRevoke() }, onDismiss = { viewModel.onShareConfirmDismissed() })
    }
}

// endregion

/** Системное окно «Поделиться» (мессенджеры, почта). */
fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}

fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Ссылка", text))
}

private fun qrBitmap(text: String): Bitmap? = runCatching {
    val size = 512
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { index -> if (matrix.get(index % size, index / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
