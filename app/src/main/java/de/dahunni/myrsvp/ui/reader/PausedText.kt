package de.dahunni.myrsvp.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.dahunni.myrsvp.engine.JoinedText
import de.dahunni.myrsvp.engine.Pause
import de.dahunni.myrsvp.engine.ReadingText
import de.dahunni.myrsvp.ui.theme.RsvpTheme

private val UnderlineWidth = 1.5.dp
/** Distance of the underline below the baseline, roughly where a CSS border-bottom of the word would be. */
private val UnderlineOffset = 4.dp
val PausedSentenceGap = 14.dp

/** Enough following text to fill any page; more would only cost measuring time. */
private const val MAX_FOLLOWING_CHARS = 2_000
/** How far ▲ looks back for the start of the previous page. */
private const val MAX_PAGE_SENTENCES = 60

/** One screen of the paused view: the current sentence, then as much following text as fits. */
class PausedPage(
    val current: JoinedText,
    /** Char range of the current word in [current]. */
    val wordStart: Int,
    val wordEnd: Int,
    /** The sentences after the current one, paragraphs separated by line breaks. */
    val following: String,
    /** Lines of [following] that fit completely; 0 hides it. */
    val followingLines: Int,
    /** The current sentence alone is taller than the page. */
    val overflows: Boolean,
    /** Word index the next page starts with (the first sentence not fully shown), null on the last page. */
    val nextPageStart: Int?,
)

/** Splits the text into pages for the paused view, measured exactly as [PausedText] draws them. */
class PageLayout(
    private val text: ReadingText,
    private val measurer: TextMeasurer,
    private val sentenceStyle: TextStyle,
    private val followingStyle: TextStyle,
    private val widthPx: Int,
    private val heightPx: Int,
    private val gapPx: Int,
) {
    private class Following(val text: String, val sentenceWords: List<Int>, val sentenceEnds: List<Int>, val nextWord: Int?)

    fun page(index: Int): PausedPage {
        val sentence = text.sentenceRange(index)
        val current = text.joinedText(sentence)
        val wordStart = current.wordStarts[index - sentence.first]
        val currentHeight = measure(current.text, sentenceStyle).size.height
        val room = heightPx - currentHeight - gapPx
        val following = following(sentence.last + 1)

        var lines = 0
        var visibleEnd = 0
        if (room > 0 && following.text.isNotEmpty()) {
            val layout = measure(following.text, followingStyle)
            while (lines < layout.lineCount && layout.getLineBottom(lines) <= room) lines++
            if (lines > 0) visibleEnd = layout.getLineEnd(lines - 1, visibleEnd = true)
        }
        val firstCut = following.sentenceEnds.indexOfFirst { it > visibleEnd }
        return PausedPage(
            current = current,
            wordStart = wordStart,
            wordEnd = wordStart + text.words[index].text.length,
            following = following.text,
            followingLines = lines,
            overflows = currentHeight > heightPx,
            nextPageStart = if (firstCut >= 0) following.sentenceWords[firstCut] else following.nextWord,
        )
    }

    /** Start of the page that ends right before the sentence containing [index]. */
    fun previousPageStart(index: Int): Int {
        val start = text.sentenceStart(index)
        if (start == 0) return 0
        var best = text.previousSentenceStart(start)
        repeat(MAX_PAGE_SENTENCES) {
            if (best == 0) return best
            val candidate = text.previousSentenceStart(best)
            val next = page(candidate).nextPageStart ?: return best
            if (next < start) return best
            best = candidate
        }
        return best
    }

    private fun following(from: Int): Following {
        val sb = StringBuilder()
        val words = ArrayList<Int>()
        val ends = ArrayList<Int>()
        var w = from
        while (w < text.size && sb.length < MAX_FOLLOWING_CHARS) {
            val range = text.sentenceRange(w)
            if (sb.isNotEmpty()) sb.append(if (text.words[w - 1].pause == Pause.Paragraph) '\n' else ' ')
            words += w
            sb.append(text.joinedText(range).text)
            ends += sb.length
            w = range.last + 1
        }
        return Following(sb.toString(), words, ends, if (w < text.size) w else null)
    }

    private fun measure(s: String, style: TextStyle) =
        measurer.measure(s, style, constraints = Constraints(maxWidth = widthPx))
}

/** Paused view: the current sentence with the current word marked, then the following text dimmed. */
@Composable
fun PausedText(page: PausedPage, modifier: Modifier = Modifier) {
    val colors = RsvpTheme.colors
    val type = RsvpTheme.type
    val annotated = remember(page, colors) {
        val s = page.current.text
        buildAnnotatedString {
            append(s.substring(0, page.wordStart))
            withStyle(SpanStyle(color = colors.text)) { append(s.substring(page.wordStart, page.wordEnd)) }
            append(s.substring(page.wordEnd))
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Column(
        modifier = modifier.clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(
            PausedSentenceGap,
            if (page.overflows) Alignment.Top else Alignment.CenterVertically,
        ),
    ) {
        Text(
            text = annotated,
            style = type.pausedSentence,
            color = colors.textMuted,
            onTextLayout = { layout = it },
            modifier = Modifier.drawBehind {
                val l = layout ?: return@drawBehind
                val wordStart = page.wordStart
                val wordEnd = page.wordEnd
                if (wordEnd > l.layoutInput.text.length) return@drawBehind
                val stroke = UnderlineWidth.toPx()
                val first = l.getLineForOffset(wordStart)
                val last = l.getLineForOffset(wordEnd - 1)
                for (line in first..last) {
                    val x0 = if (line == first) l.getBoundingBox(wordStart).left else l.getLineLeft(line)
                    val x1 = if (line == last) l.getBoundingBox(wordEnd - 1).right else l.getLineRight(line)
                    val y = l.getLineBaseline(line) + UnderlineOffset.toPx()
                    drawLine(colors.accent, Offset(x0, y), Offset(x1, y), stroke)
                }
            },
        )
        if (page.followingLines > 0) {
            Text(
                text = page.following,
                style = type.pausedNext,
                color = colors.textFaint,
                maxLines = page.followingLines,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
