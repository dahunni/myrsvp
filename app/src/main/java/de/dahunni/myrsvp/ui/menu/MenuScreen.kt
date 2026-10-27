package de.dahunni.myrsvp.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.input.Hints
import de.dahunni.myrsvp.engine.ReadingText
import de.dahunni.myrsvp.ui.theme.FocusRow
import de.dahunni.myrsvp.ui.theme.LabelValue
import de.dahunni.myrsvp.ui.theme.ListScreen
import de.dahunni.myrsvp.ui.theme.RowGap
import de.dahunni.myrsvp.ui.theme.rememberFocusedRow

/** Reader menu (docs/SPEC.md §1): Continue · Chapters · Book info · Settings · Start from beginning · Library. */
@Composable
fun MenuScreen(
    title: String,
    text: ReadingText,
    index: Int,
    showHints: Boolean,
    onContinue: () -> Unit,
    onChapters: () -> Unit,
    onBookInfo: () -> Unit,
    onSettings: () -> Unit,
    onStartOver: () -> Unit,
    onLibrary: () -> Unit,
) {
    val chapter = text.chapterOf(index)
    val chaptersValue = when {
        text.chapters.isEmpty() -> stringResource(R.string.menu_chapters_none)
        else -> stringResource(R.string.menu_chapters_value, (chapter + 1).coerceAtLeast(1), text.chapters.size)
    }
    ListScreen(
        title = stringResource(R.string.menu_title),
        subtitle = stringResource(R.string.menu_subtitle, title, (text.progress(index) * 100).toInt()),
        hints = listOf(Hints.upDown(stringResource(R.string.hint_move)), Hints.ok(stringResource(R.string.hint_select)), Hints.back(stringResource(R.string.hint_back))),
        showHints = showHints,
    ) {
        val rows = listOf(
            Triple(R.string.menu_continue, null, onContinue),
            Triple(R.string.menu_chapters, chaptersValue) { if (text.chapters.isNotEmpty()) onChapters() },
            Triple(R.string.menu_book_info, null, onBookInfo),
            Triple(R.string.menu_settings, null, onSettings),
            Triple(R.string.menu_start_over, null, onStartOver),
            Triple(R.string.menu_library, null, onLibrary),
        )
        val focused = rememberFocusedRow()
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(RowGap)) {
            rows.forEachIndexed { i, (label, value, action) ->
                FocusRow(onClick = action, autoFocus = focused.intValue == i, onFocused = { focused.intValue = i }) { f ->
                    LabelValue(stringResource(label), value, f)
                }
            }
        }
    }
}

/** Chapter list; the current chapter has the focus, OK jumps to its start. */
@Composable
fun ChaptersScreen(
    title: String,
    text: ReadingText,
    index: Int,
    showHints: Boolean,
    onSelect: (wordIndex: Int) -> Unit,
) {
    val current = text.chapterOf(index).coerceAtLeast(0)
    val listState = rememberLazyListState()
    ListScreen(
        title = stringResource(R.string.chapters_title),
        subtitle = title,
        hints = listOf(Hints.upDown(stringResource(R.string.hint_move)), Hints.ok(stringResource(R.string.hint_select)), Hints.back(stringResource(R.string.hint_back))),
        showHints = showHints,
    ) {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(RowGap)) {
            itemsIndexed(text.chapters) { i, chapter ->
                FocusRow(
                    onClick = { onSelect(chapter.wordIndex) },
                    autoFocus = i == current,
                ) { f ->
                    LabelValue(
                        label = chapter.title,
                        value = stringResource(R.string.reader_percent, (text.progress(chapter.wordIndex) * 100).toInt()),
                        focused = f,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { listState.scrollToItem(current) }
}
