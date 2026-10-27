package de.dahunni.myrsvp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.dahunni.myrsvp.engine.OrpParts
import de.dahunni.myrsvp.engine.WordSplitter
import de.dahunni.myrsvp.engine.splitAtOrp
import de.dahunni.myrsvp.ui.theme.RsvpTheme
import kotlin.math.roundToInt

/**
 * Where the center of the focus letter sits, relative to the band width: 128 dp on the Qin's
 * 288 dp content width (docs/DESIGN.md). Centering the letter (not its left edge) on a fixed x
 * keeps the ticks and the eye's anchor still, however wide the letter is.
 */
private const val FOCUS_CENTER_FRACTION = 128f / 288f
private val GuideLineWidth = 1.dp
private val TickWidth = 1.5.dp
private val TickHeight = 13.dp

fun focusCenterPx(bandWidthPx: Int): Int = (bandWidthPx * FOCUS_CENTER_FRACTION).roundToInt()

/**
 * The word between two guide lines: the focus letter centered on a fixed x, the part before it
 * ending at the letter, the rest starting after it, with ticks above and below the letter.
 */
@Composable
fun WordBand(word: String, style: TextStyle, showGuides: Boolean, modifier: Modifier = Modifier) {
    val colors = RsvpTheme.colors
    val parts = remember(word) { splitAtOrp(word) }
    val lineModifier = if (showGuides) {
        Modifier.drawBehind {
            val stroke = GuideLineWidth.toPx()
            drawLine(colors.guideLine, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
            val bottom = size.height - stroke / 2
            drawLine(colors.guideLine, Offset(0f, bottom), Offset(size.width, bottom), stroke)
        }
    } else {
        Modifier
    }
    Layout(
        modifier = modifier.then(lineModifier),
        content = {
            Text(parts.left, style = style, color = colors.text, maxLines = 1, softWrap = false)
            Text(parts.focus, style = style, color = colors.accent, maxLines = 1, softWrap = false)
            Text(parts.right, style = style, color = colors.text, maxLines = 1, softWrap = false)
            val tickColor = if (showGuides) colors.tick else colors.background
            Box(Modifier.size(TickWidth, TickHeight).background(tickColor))
            Box(Modifier.size(TickWidth, TickHeight).background(tickColor))
        },
    ) { measurables, constraints ->
        val loose = Constraints()
        val (left, focus, right, tickTop, tickBottom) = measurables.map { it.measure(loose) }
        val width = constraints.maxWidth
        val line = GuideLineWidth.roundToPx()
        val wordTop = line + tickTop.height
        val height = wordTop + focus.height + tickBottom.height + line
        val center = focusCenterPx(width)
        val focusX = center - focus.width / 2
        val tickX = center - tickTop.width / 2
        layout(width, height) {
            left.place(focusX - left.width, wordTop)
            focus.place(focusX, wordTop)
            right.place(focusX + focus.width, wordTop)
            tickTop.place(tickX, line)
            tickBottom.place(tickX, wordTop + focus.height)
        }
    }
}

/**
 * Decides whether a word fits the band at the current size, and splits it if not
 * (docs/SPEC.md "Long words"). One instance per band width + text style.
 */
class WordFitter(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val bandWidthPx: Int,
) {
    private val cache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>) = size > 256
    }

    fun split(word: String): List<String> = cache.getOrPut(word) { WordSplitter.split(word, ::fits) }

    private fun fits(chunk: String): Boolean {
        if (bandWidthPx <= 0) return true
        val parts: OrpParts = splitAtOrp(chunk)
        val focusX = focusCenterPx(bandWidthPx) - width(parts.focus) / 2
        return width(parts.left) <= focusX && width(parts.focus + parts.right) <= bandWidthPx - focusX
    }

    private fun width(text: String): Int =
        if (text.isEmpty()) 0 else measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
}
