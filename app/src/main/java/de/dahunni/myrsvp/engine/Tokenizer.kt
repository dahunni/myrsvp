package de.dahunni.myrsvp.engine

/**
 * One word as shown by the reader.
 * [offset] is the char index of its first character in the text it was read from.
 */
data class Word(
    val text: String,
    val offset: Int,
    val pause: Pause,
) {
    val endsSentence: Boolean get() = pause == Pause.Sentence || pause == Pause.Paragraph
}

/** Splits text (one paragraph per line, as in the internal book format) into [Word]s. */
object Tokenizer {
    private val DASHES = charArrayOf('—', '–')

    fun tokenize(text: String): List<Word> {
        val words = ArrayList<Word>()
        var lineStart = 0
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val firstOfParagraph = words.size
            tokenizeLine(text, lineStart, lineEnd, words)
            if (words.size > firstOfParagraph) {
                val last = words.last()
                words[words.size - 1] = last.copy(pause = Pause.Paragraph)
            }
            lineStart = lineEnd + 1
        }
        return words
    }

    private fun tokenizeLine(text: String, from: Int, to: Int, out: MutableList<Word>) {
        var i = from
        while (i < to) {
            while (i < to && text[i].isWhitespace()) i++
            if (i >= to) break
            val start = i
            while (i < to && !text[i].isWhitespace()) i++
            splitAtDashes(text.substring(start, i), start, out)
        }
    }

    /** "fortune—must" becomes "fortune—" + "must", so a dash between words never glues them together. */
    private fun splitAtDashes(token: String, offset: Int, out: MutableList<Word>) {
        var partStart = 0
        for (i in token.indices) {
            val isInnerDash = token[i] in DASHES && i > 0 && i < token.length - 1 &&
                token[i - 1].isLetterOrDigit() && token[i + 1].isLetterOrDigit()
            if (isInnerDash) {
                out += word(token.substring(partStart, i + 1), offset + partStart)
                partStart = i + 1
            }
        }
        out += word(token.substring(partStart), offset + partStart)
    }

    private fun word(text: String, offset: Int) = Word(text, offset, pauseAfter(text, paragraphEnd = false))
}
