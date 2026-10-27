package de.dahunni.myrsvp.ui.settings

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.ui.theme.InitialFocus
import de.dahunni.myrsvp.ui.theme.ListScreen
import de.dahunni.myrsvp.ui.theme.RsvpTheme

/**
 * Hidden key test (Settings → Version, OK 5×, docs/SPEC.md §3): shows the last key event, to
 * find the Qin F22 Pro's real key codes for input/ReaderKeys.kt.
 */
@Composable
fun KeyTestScreen() {
    var last by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val colors = RsvpTheme.colors
    ListScreen(title = stringResource(R.string.keys_title), hints = emptyList(), showHints = false) {
        Column(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    val e = event.nativeKeyEvent
                    val action = if (e.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
                    last = "${KeyEvent.keyCodeToString(e.keyCode)}\n" +
                        "keyCode ${e.keyCode} · scanCode ${e.scanCode}\n$action · repeat ${e.repeatCount}"
                    e.keyCode != KeyEvent.KEYCODE_BACK
                }
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.keys_prompt), style = RsvpTheme.type.meta, color = colors.textMuted)
            Text(last ?: stringResource(R.string.keys_none), style = RsvpTheme.type.pausedSentence, color = colors.text)
        }
    }
    InitialFocus(focus)
}
