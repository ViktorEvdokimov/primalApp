package com.primalapp.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.primalapp.model.campaign.Expansion
import com.primalapp.sync.ConnectionMode
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.AuthMode
import com.primalapp.viewmodel.JoinViewModel
import com.primalapp.viewmodel.SettingsViewModel

/** Настройки: режим работы, сервер, вход и регистрация, имя, дополнения, синхронизация. */
@Composable
fun SettingsScreen(app: AppViewModel) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { SettingsViewModel(app, scope) }
    val state by viewModel.state.collectAsState()

    ScreenScaffold(title = "Настройки", onBack = { app.back() }) {
        SectionTitle("Режим работы")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.mode == ConnectionMode.ONLINE) "Онлайн — с сервером" else "Офлайн — только устройство", modifier = Modifier.weight(1f))
            Switch(checked = state.mode == ConnectionMode.ONLINE, onCheckedChange = { viewModel.onModeChanged(it) })
        }
        Hint("Без сети работают экспедиция, кампании и все справочники. Онлайн справочники и кампании аккаунта обновляются с сервера.")
        Hint(state.syncText)
        state.lastSync?.let { Hint("Последняя синхронизация: $it") }
        Hint(state.catalogSource)
        if (state.mode == ConnectionMode.ONLINE) {
            TextButton(onClick = { viewModel.onSyncNow() }) { Text("Синхронизировать") }
        }

        SectionTitle("Сервер")
        OutlinedTextField(
            value = state.serverInput,
            onValueChange = { viewModel.onServerInput(it) },
            label = { Text("Адрес сервера") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.onSaveServer() }) { Text("Сохранить адрес") }
            TextButton(onClick = { viewModel.onDefaultServer() }) { Text("По умолчанию") }
        }

        SectionTitle("Аккаунт")
        when {
            state.isUser -> {
                Text("Вы вошли как ${state.accountName}" + (state.accountLogin?.let { " (логин $it)" } ?: ""))
                OutlinedTextField(value = state.displayNameInput, onValueChange = { viewModel.onDisplayNameInput(it) },
                    label = { Text("Имя (его видят участники кампаний)") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { viewModel.onSaveDisplayName() }) { Text("Сохранить имя") }
                OutlinedButton(onClick = { viewModel.onLogout() }) { Text("Выйти") }
            }
            else -> {
                if (state.isGuest) {
                    Hint("Вы гость по ссылке-приглашению (${state.accountName}). Войдите или зарегистрируйтесь — доступ к кампаниям сохранится в аккаунте.")
                } else {
                    Hint("Войдите, чтобы кампании хранились на сервере, открывались на сайте и других устройствах.")
                }
                AuthForm(viewModel, state)
                if (state.isGuest) OutlinedButton(onClick = { viewModel.onLogout() }) { Text("Выйти") }
            }
        }

        SectionTitle("Дополнения")
        Hint("Задания отключённых дополнений не показываются; условие «есть дополнение» проверяется по этому списку.")
        Expansion.entries.forEach { expansion ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = expansion in state.expansions, onCheckedChange = { viewModel.onExpansionToggled(expansion) })
                Text("${expansion.displayName} (задания ${expansion.quests.first}–${expansion.quests.last})")
            }
        }
    }

    state.confirmLogout?.let { names ->
        ConfirmDialog(
            title = if (state.confirmServer != null) "Сменить сервер?" else "Выйти из аккаунта?",
            lines = listOf("Изменения этих кампаний ещё не отправлены на сервер и будут потеряны:") + names,
            confirm = if (state.confirmServer != null) "Сменить" else "Выйти",
            onConfirm = { viewModel.onConfirmDangerous() },
            onDismiss = { viewModel.onCancelDangerous() }
        )
    }
}

@Composable
private fun AuthForm(viewModel: SettingsViewModel, state: com.primalapp.viewmodel.SettingsState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = state.authMode == AuthMode.LOGIN, onClick = { viewModel.onAuthMode(AuthMode.LOGIN) })
        Text("Вход")
        Spacer(Modifier.height(0.dp))
        RadioButton(selected = state.authMode == AuthMode.REGISTER, onClick = { viewModel.onAuthMode(AuthMode.REGISTER) })
        Text("Регистрация")
    }
    OutlinedTextField(value = state.login, onValueChange = { viewModel.onLogin(it) }, label = { Text("Логин") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = state.password, onValueChange = { viewModel.onPassword(it) }, label = { Text("Пароль") },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
    if (state.authMode == AuthMode.REGISTER) {
        OutlinedTextField(value = state.password2, onValueChange = { viewModel.onPassword2(it) }, label = { Text("Повтор пароля") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.newDisplayName, onValueChange = { viewModel.onNewDisplayName(it) },
            label = { Text("Имя (его видят участники кампаний)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Hint("Логин — латиница, цифры, «.», «_», «-», 3–32 символа; пароль — 8–64 символа.")
    }
    ErrorText(state.error)
    if (state.busy) CircularProgressIndicator() else Button(onClick = { viewModel.onSubmitAuth() }) {
        Text(if (state.authMode == AuthMode.LOGIN) "Войти" else "Зарегистрироваться")
    }
}

/** Приглашение по ссылке: «Вас пригласили в кампанию «…»», имя гостя и «Присоединиться». */
@Composable
fun JoinScreen(app: AppViewModel, token: String) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(token) { JoinViewModel(app, token, scope) }
    val state by viewModel.state.collectAsState()
    ScreenScaffold(title = "Приглашение", onBack = { app.back() }) {
        when {
            state.loading -> CircularProgressIndicator()
            state.campaignName == null -> {
                ErrorText(state.error)
                TextButton(onClick = { viewModel.load() }) { Text("Повторить") }
            }
            else -> {
                Text("Вас пригласили в кампанию «${state.campaignName}»", fontWeight = FontWeight.Bold)
                Text("Владелец: ${state.ownerName}")
                Spacer(Modifier.height(8.dp))
                if (state.needsName) {
                    OutlinedTextField(value = state.displayName, onValueChange = { viewModel.onName(it) },
                        label = { Text("Ваше имя (для истории боёв)") }, modifier = Modifier.fillMaxWidth())
                    Hint("Можно войти или зарегистрироваться в настройках — тогда кампания будет доступна в аккаунте.")
                }
                ErrorText(state.error)
                Button(onClick = { viewModel.onJoin() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Присоединиться") }
                Column {}
            }
        }
    }
}
