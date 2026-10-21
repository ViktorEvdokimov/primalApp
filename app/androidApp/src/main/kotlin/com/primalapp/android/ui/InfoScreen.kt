package com.primalapp.android.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.info.InfoEntry
import com.primalapp.info.InfoSectionCode
import com.primalapp.info.InfoSegment
import com.primalapp.info.InfoText
import com.primalapp.viewmodel.AppViewModel
import com.primalapp.viewmodel.InfoViewModel
import com.primalapp.viewmodel.Screen

/** «Инфо»: поиск по всем разделам, разделы; ключевые слова раскрываются, реакции и жетоны — карточки. */
@Composable
fun InfoScreen(app: AppViewModel, section: InfoSectionCode?, entryId: Long?) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(section, entryId) { InfoViewModel(app, section, entryId, scope) }
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    BackHandler(enabled = state.section != null) { viewModel.onBack() }

    LaunchedEffect(state.focus, state.entries) {
        val focus = state.focus ?: return@LaunchedEffect
        val index = state.entries.indexOfFirst { it.id == focus }
        if (index >= 0) {
            listState.scrollToItem(index + 1)
            viewModel.onFocusHandled()
        }
    }

    ScreenScaffold(
        title = state.section?.title ?: "Инфо",
        onBack = { if (!viewModel.onBack()) app.back() },
        scroll = false
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (state.section == null) {
                item {
                    OutlinedTextField(value = state.query, onValueChange = { viewModel.onQuery(it) },
                        label = { Text("Поиск по названию и тексту") }, modifier = Modifier.fillMaxWidth())
                    if (!state.fromServer) Hint("Встроенная версия «Инфо»: обновится с сервера в онлайн-режиме.")
                }
                if (state.query.isNotBlank()) {
                    if (state.results.isEmpty()) item { Hint("Ничего не найдено.") }
                    items(state.results, key = { "r" + it.first.id }) { (entry, label) ->
                        Text(label, modifier = Modifier.fillMaxWidth().clickable { viewModel.onOpenEntry(entry) }.padding(vertical = 8.dp))
                    }
                } else {
                    items(state.sections, key = { it.first.name }) { (code, count) ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.onSection(code) }) {
                            Column(Modifier.padding(14.dp)) {
                                Text(code.title, fontWeight = FontWeight.Bold)
                                Hint(if (count == 0) "скоро" else "Статей: $count")
                            }
                        }
                    }
                    // Экипировка — карты снаряжения из файлов приложения, а не статьи «Инфо» (web qa № 154)
                    item(key = "equipment") {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { app.navigate(Screen.Equipment) }) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Экипировка", fontWeight = FontWeight.Bold)
                                Hint("Предметов: ${app.core.equipmentCards.codes.size}")
                            }
                        }
                    }
                }
            } else {
                item {
                    if (state.section == InfoSectionCode.KEYWORDS) {
                        OutlinedTextField(value = state.filter, onValueChange = { viewModel.onFilter(it) },
                            label = { Text("Фильтр") }, modifier = Modifier.fillMaxWidth())
                    }
                    if (state.entries.isEmpty()) Hint("Здесь пока ничего нет.")
                }
                items(state.entries, key = { it.id }) { entry ->
                    if (entry.section.cards) {
                        CardEntry(entry, viewModel)
                    } else {
                        KeywordEntry(entry, entry.id in state.expanded, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordEntry(entry: InfoEntry, expanded: Boolean, viewModel: InfoViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text((if (expanded) "▾ " else "▸ ") + InfoText.label(entry), fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().clickable { viewModel.onToggle(entry) }.padding(vertical = 6.dp))
        if (expanded) {
            EntryImage(entry, viewModel, Modifier.fillMaxWidth(0.6f))
            EntryBody(entry, viewModel)
        }
    }
}

@Composable
private fun CardEntry(entry: InfoEntry, viewModel: InfoViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(10.dp)) {
            EntryImage(entry, viewModel, Modifier.width(96.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                entry.title?.let { Text(it, fontWeight = FontWeight.Bold) }
                EntryBody(entry, viewModel)
            }
        }
    }
}

@Composable
private fun EntryImage(entry: InfoEntry, viewModel: InfoViewModel, modifier: Modifier) {
    if (entry.imageKey == null) return
    val bitmap by produceState<ImageBitmap?>(null, entry.imageKey) {
        value = viewModel.image(entry)?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    bitmap?.let {
        // Картинки маленькие (значки правил) — растягиваются по ширине места с сохранением пропорций
        val ratio = if (it.height > 0) it.width.toFloat() / it.height else 1f
        Image(it, contentDescription = entry.title, contentScale = ContentScale.Fit,
            modifier = modifier.padding(vertical = 4.dp).aspectRatio(ratio.coerceIn(0.2f, 5f)))
    }
}

/** Текст статьи: абзацы, **жирный**, ссылки «См. также». */
@Composable
private fun EntryBody(entry: InfoEntry, viewModel: InfoViewModel) {
    val resolve = viewModel.resolver()
    val linkColor = MaterialTheme.colorScheme.primary
    InfoText.paragraphs(entry.body).forEach { paragraph ->
        val segments = InfoText.segments(paragraph, resolve)
        val links = mutableListOf<Pair<IntRange, InfoEntry>>()
        val text = buildAnnotatedString {
            segments.forEach { segment ->
                when (segment) {
                    is InfoSegment.Plain -> append(segment.text)
                    is InfoSegment.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(segment.text) }
                    is InfoSegment.Link -> {
                        val start = length
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(segment.text) }
                        links += (start until length) to segment.entry
                    }
                }
            }
        }
        @Suppress("DEPRECATION")
        ClickableText(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 3.dp),
            onClick = { offset -> links.firstOrNull { offset in it.first }?.let { viewModel.onOpenEntry(it.second) } }
        )
    }
}
