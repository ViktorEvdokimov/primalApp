package com.primalapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import com.primalapp.campaign.CampaignRules
import com.primalapp.model.campaign.ResourceCode
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.OutcomeViewModel
import com.primalapp.viewmodel.QuestSelectViewModel
import com.primalapp.viewmodel.TransitionViewModel

/** Выбор задания для боя: открытые задания и «Продолжить без задания». */
@Composable
fun QuestSelectScreen(app: AppViewModel, localId: Long) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(localId) { QuestSelectViewModel(app, localId, scope) }
    val state by viewModel.state.collectAsState()
    ScreenScaffold(title = "Выбор задания", onBack = { app.back() }) {
        Hint(state.campaignName)
        if (state.quests.isEmpty()) Hint("Открытых заданий нет.")
        state.quests.forEach { quest ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { viewModel.onQuestSelected(quest.number) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ElementIcon(quest.element)
                    Spacer(Modifier.width(6.dp))
                    Text(quest.label, modifier = Modifier.weight(1f))
                    ExpansionBadge(quest.expansion)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.onQuestSelected(null) }, modifier = Modifier.fillMaxWidth()) { Text("Продолжить без задания") }
    }
}

/** Итог боя: награды и правила, «Принять», «Редактировать», «Отклонить». */
@Composable
fun OutcomeScreen(app: AppViewModel, localId: Long) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(localId) { OutcomeViewModel(app, localId, scope) }
    val state by viewModel.state.collectAsState()

    ScreenScaffold(title = "Итог боя", onBack = { app.toMainMenu() }) {
        if (state.loading) {
            CircularProgressIndicator()
            return@ScreenScaffold
        }
        Hint(state.campaignName)
        if (state.changedReasons.isNotEmpty()) {
            Banner("Пока шёл бой, кампания изменилась.", error = true)
            state.changedReasons.forEach { Text(it) }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.onDismissConfirmed() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Отклонить результат")
            }
            Hint("Бой будет записан в историю. Награды можно внести вручную на листе кампании.")
            return@ScreenScaffold
        }
        val preview = state.preview ?: return@ScreenScaffold
        val editing = state.editing
        Text("ПОБЕДА!", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        preview.questNumber?.let { Text("Задание $it «${preview.questName.orEmpty()}» выполнено.") }
        if (editing == null) {
            preview.rewards.trophyBoss?.let { Text("Трофей: ${viewModel.bossName(it)}") }
            if (preview.rewards.perHunter.isNotEmpty()) {
                SectionTitle("Каждый охотник получает:")
                ResourceLine(preview.rewards.perHunter.entries.map { it.key to it.value })
            }
            if (preview.rewards.openQuests.isNotEmpty()) {
                SectionTitle("Добавляются задания:")
                preview.rewards.openQuests.forEach { Text("$it. ${viewModel.questName(it)}") }
            }
            if (preview.rewards.achievements.isNotEmpty()) {
                SectionTitle("Достижения:")
                preview.rewards.achievements.forEach { Text(it.name) }
            }
            preview.rewards.messages.forEach { Text(it, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp)) }
            RulesSection("Условные задания:", state.conditionalQuests)
            RulesSection("Условные награды:", state.conditionalRewards)
            if (preview.rewards.trophyBoss == null) Banner("Босс не выбран — укажите его через «Редактировать».", error = true)
        } else {
            OverridesEditor(viewModel, editing)
        }
        if (preview.rewards.rewardCards.isNotEmpty()) {
            SectionTitle("Карты наград:")
            preview.rewards.rewardCards.forEachIndexed { index, card ->
                Dropdown(
                    label = "Кому выдать «${CampaignRules.rewardCardName(card)}»",
                    selectedLabel = state.hunters.firstOrNull { it.first == state.rewardCardHolders.getOrNull(index) }?.second ?: "",
                    options = state.hunters,
                    onSelected = { viewModel.onRewardCardHolder(index, it) }
                )
            }
        }
        if (preview.otherActiveBattles.isNotEmpty()) {
            Banner("Идут другие бои этой кампании. Приняв победу, вы закроете главу — их результаты не примут.")
        }
        ErrorText(state.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.onAccept() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (editing == null) "Принять" else "Принять с правками")
        }
        if (editing == null) {
            OutlinedButton(onClick = { viewModel.onEdit() }, modifier = Modifier.fillMaxWidth()) { Text("Редактировать") }
        } else {
            OutlinedButton(onClick = { viewModel.onEditCancelled() }, modifier = Modifier.fillMaxWidth()) { Text("Отменить правки") }
        }
        OutlinedButton(onClick = { viewModel.onDismissRequested() }, modifier = Modifier.fillMaxWidth()) { Text("Отклонить") }
    }

    if (state.confirmDismiss) {
        ConfirmDialog(
            title = "Отклонить награды боя?",
            lines = state.preview?.dismissConsequences.orEmpty(),
            confirm = "Отклонить",
            dismiss = "Вернуться",
            onConfirm = { viewModel.onDismissConfirmed() },
            onDismiss = { viewModel.onDismissCancelled() }
        )
    }
}

@Composable
private fun OverridesEditor(viewModel: OutcomeViewModel, form: com.primalapp.viewmodel.OverridesForm) {
    Dropdown(
        label = "Босс трофея",
        selectedLabel = form.bossCode?.let { viewModel.bossName(it) } ?: "Не выбран",
        options = listOf<Pair<String?, String>>(null to "Не выбран") + viewModel.bossOptions().map { it.value to it.label },
        onSelected = { viewModel.onEditBoss(it) }
    )
    SectionTitle("Каждый охотник получает:")
    ResourceCode.entries.forEach { code ->
        val value = form.perHunter[code] ?: 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            ResourceIcon(code, 20)
            Text(" ${code.displayName}", modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.onEditResource(code, -1) }, enabled = value > 0) { Text("−") }
            Text("$value", fontWeight = FontWeight.Bold)
            TextButton(onClick = { viewModel.onEditResource(code, 1) }) { Text("+") }
        }
    }
    SectionTitle("Добавляются задания:")
    viewModel.questOptions().forEach { option ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = option.value in form.openQuests, onCheckedChange = { viewModel.onEditQuestToggled(option.value) })
            Text(option.label, fontSize = 14.sp)
        }
    }
    SectionTitle("Достижения:")
    viewModel.achievementOptions().forEach { option ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = option.value in form.achievements, onCheckedChange = { viewModel.onEditAchievementToggled(option.value) })
            Text(option.label, fontSize = 14.sp)
        }
    }
}

/** Переход главы: решения главы, награды, «Принять» и «Отклонить». */
@Composable
fun TransitionScreen(app: AppViewModel, localId: Long) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(localId) { TransitionViewModel(app, localId, scope) }
    val state by viewModel.state.collectAsState()

    ScreenScaffold(title = "Переход главы", onBack = { app.back() }) {
        if (state.loading) {
            CircularProgressIndicator()
            return@ScreenScaffold
        }
        val preview = state.preview
        if (preview == null) {
            ErrorText(state.error)
            return@ScreenScaffold
        }
        Text("Награды главы ${preview.toChapter}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        preview.decisions.filter { it.selected != null }.forEach { decision ->
            Text("${decision.question} — ${decision.options.firstOrNull { it.first == decision.selected }?.second.orEmpty()}")
        }
        if (preview.perHunter.isNotEmpty()) {
            SectionTitle("Каждый охотник получает:")
            ResourceLine(preview.perHunter.entries.map { it.key to it.value })
        }
        if (preview.openQuests.isNotEmpty()) {
            SectionTitle("Добавляются задания:")
            preview.openQuests.forEach { Text("$it. ${viewModel.questName(it)}") }
        }
        val expiring = preview.expireQuests.filter { it.wasOpen }
        if (expiring.isNotEmpty()) {
            SectionTitle("Истекает время заданий:")
            Hint("Когда истекает время задания, применяются последствия невыполненного задания.")
            expiring.forEach { quest ->
                Text("Задание ${quest.number} «${quest.name}»: " +
                    if (quest.consequences.isEmpty()) "последствий нет." else quest.consequences.joinToString(", ") + ".")
            }
        }
        if (preview.achievements.isNotEmpty()) {
            SectionTitle("Достижения:")
            preview.achievements.forEach { Text(it.name) }
        }
        if (preview.forgeLevelUp) Text("Кузня улучшается.", fontWeight = FontWeight.Medium)
        if (preview.labLevelUp) Text("Лаборатория улучшается.", fontWeight = FontWeight.Medium)
        if (preview.hunterKitUpgrade) Text("Каждый охотник улучшает свой набор.", fontWeight = FontWeight.Medium)
        if (preview.rewardCards.isNotEmpty()) Text("Карты наград: ${preview.rewardCards.joinToString(", ")}.")
        preview.messages.forEach { Text(it, fontWeight = FontWeight.Medium) }
        preview.finalBattle?.let { Text("Следующий бой — финальный: ${viewModel.bossName(it)}", fontWeight = FontWeight.Bold) }
        RulesSection("Условные задания:", state.conditionalQuests)
        RulesSection("Условные награды:", state.conditionalRewards)
        ErrorText(state.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.onAccept() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Принять") }
        OutlinedButton(onClick = { viewModel.onRejectRequested() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Отклонить") }
    }

    state.pendingDecision?.let { decision ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Решение главы") },
            text = { Text(decision.question) },
            confirmButton = {
                Column {
                    decision.options.forEach { (code, label) ->
                        TextButton(onClick = { viewModel.onDecision(decision.code, code) }) { Text(label) }
                    }
                }
            }
        )
    }
    if (state.confirmReject) {
        ConfirmDialog(
            title = "Отклонить награды главы ${state.preview?.toChapter}?",
            lines = state.preview?.rejectConsequences.orEmpty(),
            confirm = "Отклонить",
            dismiss = "Вернуться",
            onConfirm = { viewModel.onRejectConfirmed() },
            onDismiss = { viewModel.onRejectCancelled() }
        )
    }
}
