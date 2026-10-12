package com.primalapp.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.android.R
import com.primalapp.catalog.RuleExplanation
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Expansion
import com.primalapp.model.campaign.ResourceCode

/** Экран с заголовком и кнопкой «Назад»; содержимое прокручивается. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                }
            },
            actions = { actions() }
        )
        val modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        Column(
            modifier = if (scroll) modifier.verticalScroll(rememberScrollState()) else modifier,
            content = {
                content()
                Spacer(Modifier.height(24.dp))
            }
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ErrorText(text: String?) {
    if (!text.isNullOrBlank()) {
        Text(text, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** Плашка-баннер: «Ждёт перехода главы», «Идёт бой…», «Нет связи с сервером». */
@Composable
fun Banner(text: String, error: Boolean = false) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(text, modifier = Modifier.padding(8.dp), fontSize = 14.sp)
    }
}

/** Окно подтверждения с текстом или списком пунктов. */
@Composable
fun ConfirmDialog(
    title: String,
    lines: List<String>,
    confirm: String,
    dismiss: String = "Отмена",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                lines.forEach { Text(if (lines.size > 1) "• $it" else it, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } }
    )
}

/** Выпадающий список. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled)
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    onSelected(value)
                    expanded = false
                })
            }
        }
    }
}

/** Иконка ресурса (материи, растения, стихии). */
@Composable
fun ResourceIcon(code: ResourceCode, size: Int = 22) {
    Image(painterResource(code.iconRes()), contentDescription = code.displayName, modifier = Modifier.size(size.dp))
}

@Composable
fun ElementIcon(element: Element?, size: Int = 20) {
    if (element != null) ResourceIcon(ResourceCode.of(element), size)
}

/** Цена или запас иконками: «🔥 1 · Кости 1». */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ResourceLine(parts: List<Pair<ResourceCode, Int>>, empty: String = "—") {
    if (parts.isEmpty()) {
        Text(empty, fontSize = 14.sp)
        return
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { (code, quantity) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResourceIcon(code, 18)
                Spacer(Modifier.width(2.dp))
                Text("${code.displayName} $quantity", fontSize = 14.sp)
            }
        }
    }
}

/** Пометка дополнения у задания: «Кошмар», «Перо», «Яд», «Лёд». */
@Composable
fun ExpansionBadge(expansion: Expansion?) {
    if (expansion != null) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
            Text(expansion.displayName, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
        }
    }
}

/** «Условные задания» / «Условные награды»: правило и результат проверки для этой кампании. */
@Composable
fun RulesSection(title: String, rules: List<RuleExplanation>) {
    if (rules.isEmpty()) return
    SectionTitle(title)
    rules.forEach { rule ->
        Text(rule.description, fontSize = 14.sp)
        Text(rule.result, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
fun Divider() = HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

@DrawableRes
fun ResourceCode.iconRes(): Int = when (this) {
    ResourceCode.SCALES -> R.drawable.scales
    ResourceCode.BONES -> R.drawable.bones
    ResourceCode.BLOOD -> R.drawable.blood
    ResourceCode.ZIMIA -> R.drawable.zimia
    ResourceCode.IRIDIA -> R.drawable.iridia
    ResourceCode.ZLATIA -> R.drawable.zlatia
    ResourceCode.NILLEA -> R.drawable.nillea
    ResourceCode.TARMARET -> R.drawable.tarmaret
    ResourceCode.ALBALACEA -> R.drawable.albalacea
    ResourceCode.MELLIS -> R.drawable.mellis
    ResourceCode.ANTHEMON -> R.drawable.anthemon
    ResourceCode.SELICORNIA -> R.drawable.selicornia
    ResourceCode.FIRE -> R.drawable.fire
    ResourceCode.HORN -> R.drawable.horn
    ResourceCode.CORAL -> R.drawable.coral
    ResourceCode.CRYSTAL -> R.drawable.crystal
    ResourceCode.LIGHTNING -> R.drawable.lightning
    ResourceCode.METAL -> R.drawable.metal
    ResourceCode.FEATHER -> R.drawable.feather
    ResourceCode.POISON -> R.drawable.poison
    ResourceCode.ICE -> R.drawable.ice
}
