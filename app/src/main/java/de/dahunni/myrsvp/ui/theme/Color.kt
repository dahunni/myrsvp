package de.dahunni.myrsvp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** All colors from docs/DESIGN.md. Use these via [RsvpTheme.colors], never hard-coded values. */
@Immutable
data class RsvpColors(
    val background: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val guideLine: Color,
    val tick: Color,
    val divider: Color,
    val rowFocused: Color,
    val dashedBorder: Color,
    val addBooksLabel: Color,
    /** Focus-letter color; also the outline of focused list rows. */
    val accent: Color,
    val isDark: Boolean,
)

enum class FocusColor { Red, Gold, Blue }

fun accentFor(focus: FocusColor, dark: Boolean): Color = when (focus) {
    FocusColor.Red -> if (dark) Color(0xFFFF4B3E) else Color(0xFFD21F1F)
    FocusColor.Gold -> if (dark) Color(0xFFE8B04A) else Color(0xFFB07A12)
    FocusColor.Blue -> if (dark) Color(0xFF4FA3FF) else Color(0xFF1F5FD2)
}

fun darkColors(focus: FocusColor = FocusColor.Red) = RsvpColors(
    background = Color(0xFF0E0E0E),
    text = Color(0xFFEDEDED),
    textMuted = Color(0xFF8A8A8A),
    textFaint = Color(0xFF5C5C5C),
    guideLine = Color(0xFF2A2A2A),
    tick = Color(0xFF4A4A4A),
    divider = Color(0xFF222222),
    rowFocused = Color(0xFF262626),
    dashedBorder = Color(0xFF3A3A3A),
    addBooksLabel = Color(0xFFBDBDBD),
    accent = accentFor(focus, dark = true),
    isDark = true,
)

fun lightColors(focus: FocusColor = FocusColor.Red) = RsvpColors(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF111111),
    textMuted = Color(0xFF6B6B6B),
    textFaint = Color(0xFF9A9A9A),
    guideLine = Color(0xFFD9D9D9),
    tick = Color(0xFFB5B5B5),
    divider = Color(0xFFE6E6E6),
    rowFocused = Color(0xFFEFEFEF),
    dashedBorder = Color(0xFFC9C9C9),
    addBooksLabel = Color(0xFF555555),
    accent = accentFor(focus, dark = false),
    isDark = false,
)
