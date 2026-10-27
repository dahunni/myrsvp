package de.dahunni.myrsvp.ui.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import de.dahunni.myrsvp.BuildConfig
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.input.KeyCodes
import de.dahunni.myrsvp.ui.theme.InitialFocus
import de.dahunni.myrsvp.ui.theme.ListScreen
import de.dahunni.myrsvp.ui.theme.RsvpTheme
import de.dahunni.myrsvp.ui.theme.UiFont
import kotlinx.coroutines.launch

private const val CREDITS = """my-rsvp %s
TH-Operations GmbH

An offline RSVP e-reader. No internet access, no tracking, no accounts: books, settings and reading positions stay on this device.

Reading rules (focus letter, word timing, sentence pauses) adapted from:
• RSVP Nano by ionutdecebal (github.com/ionutdecebal/rsvpnano), MIT License
• pocket-reader by laurensvanhelvoort (github.com/laurensvanhelvoort/pocket-reader), MIT License

Font: Alte DIN 1451 Mittelschrift by Peter Wiegel, SIL Open Font License 1.1 (full text below).

Libraries: AndroidX and Jetpack Compose (Apache License 2.0), Kotlin (Apache License 2.0), jsoup (MIT License, full text below).
"""

/** Credits and licenses; ▲▼ scroll. */
@Composable
fun AboutScreen(showHints: Boolean) {
    val context = LocalContext.current
    val text = remember {
        fun asset(name: String) = runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("")
        CREDITS.format(BuildConfig.VERSION_NAME) +
            "\n— Alte DIN 1451 —\n\n" + asset("licenses/AlteDIN1451-OFL.txt") +
            "\n\n— jsoup —\n\n" + asset("licenses/jsoup-MIT.txt")
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    ListScreen(
        title = stringResource(R.string.about_title),
        hints = listOf(stringResource(R.string.hint_scroll), "", stringResource(R.string.hint_back)),
        showHints = showHints,
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = UiFont, fontSize = 11.sp, lineHeight = 16.sp),
            color = RsvpTheme.colors.textMuted,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    val step = when (event.nativeKeyEvent.keyCode) {
                        in KeyCodes.UP -> -1
                        in KeyCodes.DOWN -> 1
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown) {
                        scope.launch { scroll.animateScrollBy(step * scroll.viewportSize * 0.8f) }
                    }
                    true
                }
                .focusable()
                .verticalScroll(scroll),
        )
    }
    InitialFocus(focus)
}
