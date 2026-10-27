package de.dahunni.myrsvp.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.input.ListKey
import de.dahunni.myrsvp.input.listKey
import de.dahunni.myrsvp.ui.ImportProgress
import de.dahunni.myrsvp.ui.LibraryBook
import de.dahunni.myrsvp.ui.theme.FocusRow
import de.dahunni.myrsvp.ui.theme.ListScreen
import de.dahunni.myrsvp.ui.theme.RowGap
import de.dahunni.myrsvp.ui.theme.RsvpTheme

private val BookRowPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp)
private val BarHeight = 2.5.dp

/**
 * Library: books by last read (the first one, the book read last, has the focus), finished books
 * dimmed at the bottom, "Add books" at the end. ☰ (or a long press) opens the library menu.
 */
@Composable
fun LibraryScreen(
    books: List<LibraryBook>,
    importing: ImportProgress?,
    showHints: Boolean,
    onOpen: (String) -> Unit,
    onAddBooks: () -> Unit,
    onMenu: (bookId: String?) -> Unit,
) {
    val listState = rememberLazyListState()
    /** The book whose row has the focus, for ☰ (null on "Add books"). */
    val focusedBook = remember { mutableStateOf<String?>(null) }

    ListScreen(
        title = stringResource(R.string.library_title),
        trailing = pluralStringResource(R.plurals.library_count, books.size, books.size),
        hints = listOf(stringResource(R.string.hint_move), stringResource(R.string.hint_open), stringResource(R.string.hint_menu)),
        showHints = showHints,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (listKey(event.nativeKeyEvent.keyCode) != ListKey.Menu) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown) onMenu(focusedBook.value)
            true
        },
    ) {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(RowGap), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(books, key = { _, b -> b.id }) { i, book ->
                FocusRow(
                    onClick = { onOpen(book.id) },
                    onLongClick = { onMenu(book.id) },
                    autoFocus = i == 0,
                    padding = BookRowPadding,
                    dimmed = book.finished,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) focusedBook.value = book.id },
                ) { BookRow(book) }
            }
            item(key = "add") {
                AddBooksRow(
                    importing = importing,
                    onClick = onAddBooks,
                    autoFocus = books.isEmpty(),
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .onFocusChanged { if (it.isFocused) focusedBook.value = null },
                )
            }
        }
    }
}

@Composable
private fun BookRow(book: LibraryBook) {
    val colors = RsvpTheme.colors
    val type = RsvpTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(book.title, style = type.rowTitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (book.author != null) {
            Text(book.author, style = type.hint, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(BarHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(colors.guideLine),
            ) {
                val fraction = if (book.finished) 1f else book.percent / 100f
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(if (book.finished) colors.textFaint else colors.textMuted),
                    )
                }
            }
            Text(
                if (book.finished) stringResource(R.string.library_done) else stringResource(R.string.reader_percent, book.percent),
                style = type.hint.copy(fontSize = 9.sp),
                color = colors.textMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 28.dp),
            )
        }
    }
}

/** Transparent row with a dashed border; shows the import progress while importing. */
@Composable
private fun AddBooksRow(
    importing: ImportProgress?,
    onClick: () -> Unit,
    autoFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = RsvpTheme.colors
    val dashed = Modifier.drawBehind {
        val stroke = 1.dp.toPx()
        drawRoundRect(
            colors.dashedBorder,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = size.copy(width = size.width - stroke, height = size.height - stroke),
            cornerRadius = CornerRadius(7.dp.toPx()),
            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )
    }
    FocusRow(
        onClick = { if (importing == null) onClick() },
        autoFocus = autoFocus,
        padding = PaddingValues(9.dp),
        background = dashed,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Canvas(Modifier.size(13.dp)) {
                val w = 1.dp.toPx()
                val pad = size.width * 0.21f
                drawLine(colors.addBooksLabel, Offset(size.width / 2, pad), Offset(size.width / 2, size.height - pad), w, StrokeCap.Round)
                drawLine(colors.addBooksLabel, Offset(pad, size.height / 2), Offset(size.width - pad, size.height / 2), w, StrokeCap.Round)
            }
            Text(
                if (importing != null) stringResource(R.string.library_importing, importing.done + 1, importing.total)
                else stringResource(R.string.library_add_books),
                style = RsvpTheme.type.rowTitle.copy(fontSize = 11.sp),
                color = colors.addBooksLabel,
            )
        }
    }
}
