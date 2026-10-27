package de.dahunni.myrsvp.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.input.Hints
import de.dahunni.myrsvp.ui.UpdateState
import de.dahunni.myrsvp.update.Release
import de.dahunni.myrsvp.update.UpdateError
import de.dahunni.myrsvp.ui.theme.FocusRow
import de.dahunni.myrsvp.ui.theme.LabelValue
import de.dahunni.myrsvp.ui.theme.ListScreen
import de.dahunni.myrsvp.ui.theme.RowGap
import de.dahunni.myrsvp.ui.theme.RsvpTheme
import de.dahunni.myrsvp.ui.theme.rememberFocusedRow

/**
 * A new release: install, later or skip, then the changelog. The changelog lines are read-only
 * rows, so ▲▼ scroll through them like everything else.
 */
@Composable
fun UpdateScreen(
    release: Release,
    state: UpdateState,
    installedVersion: String,
    showHints: Boolean,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = RsvpTheme.colors
    val focused = rememberFocusedRow()
    val installLabel = when {
        release.apkUrl == null -> stringResource(R.string.update_no_apk)
        state is UpdateState.Downloading -> stringResource(R.string.update_downloading, (state.progress * 100).toInt())
        state is UpdateState.Downloaded -> stringResource(R.string.update_install_again)
        state is UpdateState.Failed && state.release == release -> stringResource(R.string.update_retry)
        else -> stringResource(R.string.update_install)
    }
    val message = when {
        state is UpdateState.Failed && state.release == release -> stringResource(
            when (state.error) {
                UpdateError.Offline -> R.string.update_error_offline
                UpdateError.Download -> R.string.update_error_download
                UpdateError.Checksum -> R.string.update_error_checksum
            },
        )
        state is UpdateState.Downloaded -> stringResource(R.string.update_permission_hint)
        else -> null
    }
    val actions = listOf(
        installLabel to { if (release.apkUrl != null && state !is UpdateState.Downloading) onInstall() },
        stringResource(R.string.update_later) to onLater,
        stringResource(R.string.update_skip) to onSkip,
    )
    val whatsNew = stringResource(R.string.update_whats_new)
    ListScreen(
        title = stringResource(R.string.update_title),
        subtitle = stringResource(R.string.update_subtitle, release.version.toString(), installedVersion),
        hints = listOf(
            Hints.upDown(stringResource(R.string.hint_move)),
            Hints.ok(stringResource(R.string.hint_select)),
            Hints.back(stringResource(R.string.hint_back)),
        ),
        showHints = showHints,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(RowGap)) {
            actions.forEachIndexed { i, (label, action) ->
                item {
                    FocusRow(onClick = action, autoFocus = focused.intValue == i, onFocused = { focused.intValue = i }) { f ->
                        LabelValue(label, null, f)
                    }
                }
            }
            if (message != null) {
                item {
                    Text(message, style = RsvpTheme.type.meta, color = colors.textMuted, modifier = Modifier.padding(4.dp))
                }
            }
            if (release.notes.isNotEmpty()) {
                item {
                    Text(
                        whatsNew,
                        style = RsvpTheme.type.meta,
                        color = colors.textMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
                items(release.notes) { line ->
                    FocusRow { Text(line, style = RsvpTheme.type.rowTitle, color = colors.text) }
                }
            }
        }
    }
}
