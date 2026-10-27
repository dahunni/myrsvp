package de.dahunni.myrsvp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.dahunni.myrsvp.BuildConfig
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.data.Settings
import de.dahunni.myrsvp.engine.WordTiming
import de.dahunni.myrsvp.input.ListKey
import de.dahunni.myrsvp.ui.theme.FocusColor
import de.dahunni.myrsvp.ui.theme.FocusRow
import de.dahunni.myrsvp.ui.theme.LabelValue
import de.dahunni.myrsvp.ui.theme.ListScreen
import de.dahunni.myrsvp.ui.theme.RowGap
import de.dahunni.myrsvp.ui.theme.WordSize
import de.dahunni.myrsvp.ui.theme.rememberFocusedRow

/** Pressing OK this often on the version row opens the key test screen. */
private const val KEY_TEST_PRESSES = 5

/** A setting with a value that ◀▶ change; OK or a tap steps forward (wrapping round). */
private class ValueRow(val label: String, val value: String, val step: (forward: Boolean, wrap: Boolean) -> Unit)

private fun <T> List<T>.stepFrom(current: T, forward: Boolean, wrap: Boolean): T {
    val i = indexOf(current) + if (forward) 1 else -1
    return when {
        i in indices -> get(i)
        !wrap -> current
        i < 0 -> last()
        else -> first()
    }
}

/** Settings (docs/SPEC.md §1): speed, theme, focus color, word size, pause at sentences, ramp-up, guide lines. */
@Composable
fun SettingsScreen(
    settings: Settings,
    showHints: Boolean,
    onChange: ((Settings) -> Settings) -> Unit,
    onAbout: () -> Unit,
    onKeyTest: () -> Unit,
) {
    val on = stringResource(R.string.settings_on)
    val off = stringResource(R.string.settings_off)
    fun onOff(b: Boolean) = if (b) on else off
    val speeds = (WordTiming.MIN_WPM..WordTiming.MAX_WPM step WordTiming.WPM_STEP).toList()
    val focusNames = mapOf(
        FocusColor.Red to stringResource(R.string.settings_focus_red),
        FocusColor.Gold to stringResource(R.string.settings_focus_gold),
        FocusColor.Blue to stringResource(R.string.settings_focus_blue),
    )
    val sizeNames = mapOf(
        WordSize.S to stringResource(R.string.settings_size_small),
        WordSize.M to stringResource(R.string.settings_size_medium),
        WordSize.L to stringResource(R.string.settings_size_large),
    )
    val rows = listOf(
        ValueRow(stringResource(R.string.settings_speed), stringResource(R.string.reader_wpm, settings.wpm)) { fwd, wrap ->
            onChange { it.copy(wpm = speeds.stepFrom(it.wpm, fwd, wrap)) }
        },
        ValueRow(
            stringResource(R.string.settings_theme),
            stringResource(if (settings.darkTheme) R.string.settings_theme_dark else R.string.settings_theme_light),
        ) { _, _ -> onChange { it.copy(darkTheme = !it.darkTheme) } },
        ValueRow(stringResource(R.string.settings_focus), focusNames.getValue(settings.focusColor)) { fwd, wrap ->
            onChange { it.copy(focusColor = FocusColor.entries.stepFrom(it.focusColor, fwd, wrap)) }
        },
        ValueRow(stringResource(R.string.settings_word_size), sizeNames.getValue(settings.wordSize)) { fwd, wrap ->
            onChange { it.copy(wordSize = WordSize.entries.stepFrom(it.wordSize, fwd, wrap)) }
        },
        ValueRow(stringResource(R.string.settings_pause_at_sentences), onOff(settings.pauseAtSentences)) { _, _ ->
            onChange { it.copy(pauseAtSentences = !it.pauseAtSentences) }
        },
        ValueRow(stringResource(R.string.settings_ramp_up), onOff(settings.rampUp)) { _, _ ->
            onChange { it.copy(rampUp = !it.rampUp) }
        },
        ValueRow(stringResource(R.string.settings_guide_lines), onOff(settings.guideLines)) { _, _ ->
            onChange { it.copy(guideLines = !it.guideLines) }
        },
    )

    val listState = rememberLazyListState()
    val focused = rememberFocusedRow()
    var versionPresses by remember { mutableIntStateOf(0) }
    ListScreen(
        title = stringResource(R.string.settings_title),
        hints = listOf(stringResource(R.string.hint_move), stringResource(R.string.hint_change), stringResource(R.string.hint_back)),
        showHints = showHints,
    ) {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(RowGap)) {
            items(rows.size) { i ->
                val row = rows[i]
                FocusRow(
                    onClick = { row.step(true, true) },
                    onKey = { key ->
                        when (key) {
                            ListKey.Left -> { row.step(false, false); true }
                            ListKey.Right -> { row.step(true, false); true }
                            ListKey.Menu -> false
                        }
                    },
                    autoFocus = focused.intValue == i,
                    onFocused = { focused.intValue = i },
                ) { f ->
                    LabelValue(row.label, if (f) stringResource(R.string.settings_value_focused, row.value) else row.value, f)
                }
            }
            item {
                FocusRow(
                    onClick = onAbout,
                    autoFocus = focused.intValue == rows.size,
                    onFocused = { focused.intValue = rows.size },
                ) { f -> LabelValue(stringResource(R.string.settings_about), null, f) }
            }
            item {
                FocusRow(
                    onClick = {
                        versionPresses++
                        if (versionPresses >= KEY_TEST_PRESSES) {
                            versionPresses = 0
                            onKeyTest()
                        }
                    },
                    autoFocus = focused.intValue == rows.size + 1,
                    onFocused = { focused.intValue = rows.size + 1 },
                ) { f -> LabelValue(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME, f) }
            }
        }
    }
}
