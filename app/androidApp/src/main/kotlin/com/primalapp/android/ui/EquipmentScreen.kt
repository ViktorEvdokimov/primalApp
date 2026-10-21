package com.primalapp.android.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.catalog.EquipmentCard
import com.primalapp.model.campaign.HunterClass
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.EquipmentRow
import com.primalapp.viewmodel.EquipmentState
import com.primalapp.viewmodel.EquipmentTab
import com.primalapp.viewmodel.EquipmentViewModel
import com.primalapp.viewmodel.ForgeItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** «Инфо» → «Экипировка»: вкладки «Снаряжение» и «Зелья»; фильтры по типу, классу (только оружие) и уровню; карты сеткой. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentScreen(app: AppViewModel) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { EquipmentViewModel(app, scope) }
    val state by viewModel.state.collectAsState()

    ScreenScaffold(title = "Экипировка", onBack = { app.back() }, scroll = false) {
        TabRow(selectedTabIndex = state.tab.ordinal) {
            EquipmentTab.entries.forEach { tab ->
                Tab(selected = tab == state.tab, onClick = { viewModel.onTabSelected(tab) }, text = { Text(tab.label) })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).padding(top = 8.dp)
        ) {
            // Фильтры — в начале сетки: на телефоне они прокручиваются вместе с картами
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) { Filters(state, viewModel) }
            items(state.items, key = { it.code }) { row -> EquipmentCell(row) { viewModel.onCardRequested(row) } }
        }
    }

    state.card?.let { card -> CardDialog(card) { viewModel.onCardDismissed() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(state: EquipmentState, viewModel: EquipmentViewModel) {
    Column {
        if (state.showsFilters) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.typeFilter == null, onClick = { viewModel.onTypeSelected(null) }, label = { Text("Все") })
                ForgeItemType.entries.forEach { type ->
                    FilterChip(selected = state.typeFilter == type, onClick = { viewModel.onTypeSelected(type) }, label = { Text(type.label) })
                }
            }
            // Класс отбирает только оружие: шлемы, доспехи и предметы подходят всем
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.classFilter == null, onClick = { viewModel.onClassSelected(null) },
                    label = { Text("Все классы") })
                HunterClass.entries.forEach { hunterClass ->
                    FilterChip(selected = state.classFilter == hunterClass, onClick = { viewModel.onClassSelected(hunterClass) },
                        label = { Text(hunterClass.displayName) })
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { level ->
                FilterChip(selected = state.level == level, onClick = { viewModel.onLevelSelected(level) },
                    label = { Text("$level-й уровень") })
            }
        }
        if (state.showsBasicHint) Hint("Базовое снаряжение есть только 1-го уровня.")
    }
}

@Composable
private fun EquipmentCell(row: EquipmentRow, onClick: () -> Unit) {
    Column {
        // Место под карту известно заранее (оружие 70×120 мм, остальное 60×60) — сетка не прыгает при загрузке
        val frame = Modifier.fillMaxWidth().aspectRatio(if (row.weapon) 70f / 120f else 1f)
            .clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
        val thumbnail = rememberThumbnail(row.card)
        if (thumbnail != null) {
            Image(thumbnail, contentDescription = row.card.title, contentScale = ContentScale.Fit, modifier = frame)
        } else {
            Box(frame.background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
            row.element?.let { Box(Modifier.padding(top = 2.dp)) { ElementIcon(it, 14) } }
            Text((if (row.element != null) " " else "") + row.caption, fontSize = 12.sp)
        }
    }
}

/** Уменьшенная вдвое картинка карты из файлов приложения — для сетки; крупно карта открывается в окне. */
@Composable
private fun rememberThumbnail(card: EquipmentCard): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, card.asset) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                context.assets.open(card.asset).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}
