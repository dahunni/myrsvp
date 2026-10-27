package de.dahunni.myrsvp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalRsvpColors = staticCompositionLocalOf { darkColors() }
private val LocalRsvpTypography = staticCompositionLocalOf { RsvpTypography() }

/** Access with RsvpTheme.colors / RsvpTheme.type inside composables. */
object RsvpTheme {
    val colors: RsvpColors
        @Composable get() = LocalRsvpColors.current
    val type: RsvpTypography
        @Composable get() = LocalRsvpTypography.current
}

/**
 * App theme. Dark is the default; light and the focus color come from Settings (docs/SPEC.md).
 * Material3 is only used as a base so stock components don't clash with our colors.
 */
@Composable
fun MyrsvpTheme(
    dark: Boolean = true,
    focus: FocusColor = FocusColor.Red,
    wordSize: WordSize = WordSize.L,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) darkColors(focus) else lightColors(focus)
    val material = if (dark) {
        darkColorScheme(
            primary = colors.accent, background = colors.background, surface = colors.background,
            onBackground = colors.text, onSurface = colors.text,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, background = colors.background, surface = colors.background,
            onBackground = colors.text, onSurface = colors.text,
        )
    }
    CompositionLocalProvider(
        LocalRsvpColors provides colors,
        LocalRsvpTypography provides RsvpTypography().withWordSize(wordSize),
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
