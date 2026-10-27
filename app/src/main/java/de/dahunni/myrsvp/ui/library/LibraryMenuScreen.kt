package de.dahunni.myrsvp.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.input.Hints
import de.dahunni.myrsvp.ui.theme.FocusRow
import de.dahunni.myrsvp.ui.theme.LabelValue
import de.dahunni.myrsvp.ui.theme.ListScreen
import de.dahunni.myrsvp.ui.theme.RowGap
import de.dahunni.myrsvp.ui.theme.rememberFocusedRow

/** ☰ in the library: add books, settings, and removing the focused book (OK twice). */
@Composable
fun LibraryMenuScreen(
    bookTitle: String?,
    showHints: Boolean,
    onAddBooks: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val focused = rememberFocusedRow()
    ListScreen(
        title = stringResource(R.string.library_menu_title),
        subtitle = bookTitle,
        hints = listOf(Hints.upDown(stringResource(R.string.hint_move)), Hints.ok(stringResource(R.string.hint_select)), Hints.back(stringResource(R.string.hint_back))),
        showHints = showHints,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
            FocusRow(onClick = onAddBooks, autoFocus = focused.intValue == 0, onFocused = { focused.intValue = 0 }) { f ->
                LabelValue(stringResource(R.string.library_add_books), null, f)
            }
            FocusRow(onClick = onSettings, autoFocus = focused.intValue == 1, onFocused = { focused.intValue = 1 }) { f -> LabelValue(stringResource(R.string.menu_settings), null, f) }
            if (bookTitle != null) {
                FocusRow(
                    onClick = { if (confirmRemove) onRemove() else confirmRemove = true },
                    onFocused = { focused.intValue = 2 },
                    modifier = Modifier.onFocusChanged { if (!it.isFocused) confirmRemove = false },
                ) { f ->
                    LabelValue(
                        if (confirmRemove) stringResource(R.string.library_menu_remove_confirm)
                        else stringResource(R.string.library_menu_remove, bookTitle),
                        null,
                        f,
                    )
                }
            }
        }
    }
}
