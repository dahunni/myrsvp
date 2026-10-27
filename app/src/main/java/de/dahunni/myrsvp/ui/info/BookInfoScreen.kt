package de.dahunni.myrsvp.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.book.BookMeta
import de.dahunni.myrsvp.engine.ReadingText
import de.dahunni.myrsvp.ui.theme.FocusRow
import de.dahunni.myrsvp.ui.theme.LabelValue
import de.dahunni.myrsvp.ui.theme.ListScreen
import de.dahunni.myrsvp.ui.theme.RowGap
import de.dahunni.myrsvp.ui.theme.durationText
import java.text.NumberFormat
import java.util.Locale

/** Title, author, year, word count, reading time at the current speed, progress (docs/SPEC.md §1). */
@Composable
fun BookInfoScreen(meta: BookMeta, text: ReadingText, index: Int, wpm: Int, showHints: Boolean) {
    val unknown = stringResource(R.string.info_unknown)
    val language = meta.language?.let { tag ->
        Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault()).ifEmpty { tag }
    }
    val rows = listOf(
        stringResource(R.string.info_book_title) to meta.title,
        stringResource(R.string.info_author) to (meta.author ?: unknown),
        stringResource(R.string.info_year) to (meta.year?.toString() ?: unknown),
        stringResource(R.string.info_language) to (language ?: unknown),
        stringResource(R.string.info_words) to NumberFormat.getIntegerInstance().format(text.size),
        stringResource(R.string.info_chapters) to text.chapters.size.toString(),
        stringResource(R.string.info_reading_time) to
            stringResource(R.string.info_reading_time_value, durationText(text.remainingMs(0, wpm)), wpm),
        stringResource(R.string.info_time_left) to durationText(text.remainingMs(index, wpm)),
        stringResource(R.string.info_progress) to stringResource(R.string.reader_percent, (text.progress(index) * 100).toInt()),
    )
    val listState = rememberLazyListState()
    ListScreen(
        title = stringResource(R.string.info_title),
        subtitle = meta.title,
        hints = listOf(stringResource(R.string.hint_scroll), "", stringResource(R.string.hint_back)),
        showHints = showHints,
    ) {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(RowGap)) {
            // Read-only rows are still focusable, so the D-pad can scroll through them.
            itemsIndexed(rows) { i, (label, value) ->
                FocusRow(autoFocus = i == 0) { f -> LabelValue(label, value, f) }
            }
        }
    }
}
