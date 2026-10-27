package de.dahunni.myrsvp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.dahunni.myrsvp.R

// TODO: bundle Literata (OFL) in res/font and replace this fallback.
val ReadingFont: FontFamily = FontFamily.Serif

/**
 * Alte DIN 1451 Mittelschrift by Peter Wiegel (OFL, assets/licenses/AlteDIN1451-OFL.txt).
 * It has a single weight, so it is registered for every weight the styles use: no fake bold.
 */
private val AlteDin = R.font.alte_din_1451
val UiFont: FontFamily = FontFamily(
    Font(AlteDin, FontWeight.Normal),
    Font(AlteDin, FontWeight.Medium),
    Font(AlteDin, FontWeight.SemiBold),
)

enum class WordSize(val sp: Int) { S(30), M(36), L(42) }

/** Text styles from docs/DESIGN.md (sizes already in sp). */
@Immutable
data class RsvpTypography(
    val word: TextStyle = TextStyle(fontFamily = ReadingFont, fontSize = 42.sp, lineHeight = 1.3.em),
    val pausedSentence: TextStyle = TextStyle(fontFamily = ReadingFont, fontSize = 18.sp, lineHeight = 1.5.em),
    val pausedNext: TextStyle = TextStyle(fontFamily = ReadingFont, fontSize = 14.sp, lineHeight = 1.5.em),
    val screenTitle: TextStyle = TextStyle(fontFamily = UiFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    val bookTitle: TextStyle = TextStyle(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    val rowTitle: TextStyle = TextStyle(fontFamily = UiFont, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    val meta: TextStyle = TextStyle(fontFamily = UiFont, fontSize = 10.sp),
    val wpm: TextStyle = TextStyle(fontFamily = UiFont, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    val hint: TextStyle = TextStyle(fontFamily = UiFont, fontSize = 9.5.sp),
) {
    fun withWordSize(size: WordSize) = copy(word = word.copy(fontSize = size.sp.sp))
}
