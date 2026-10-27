package de.dahunni.myrsvp.engine

/** A chapter starting at the word with index [wordIndex]. */
data class Chapter(val title: String, val wordIndex: Int)

/** Words joined for display; word k of the range covers [wordStarts] k until k + its length. */
class JoinedText(val text: String, val wordStarts: IntArray)

/**
 * A tokenized book: its words, the sentence index and the chapters.
 * Everything the reader needs to navigate and to estimate the time left.
 */
class ReadingText(
    val words: List<Word>,
    chapters: List<Chapter> = emptyList(),
) {
    val size: Int get() = words.size

    /** Word indices where a sentence starts, ascending; the first is always 0. */
    val sentenceStarts: IntArray = buildList {
        if (words.isNotEmpty()) add(0)
        for (i in 0 until words.size - 1) if (words[i].endsSentence) add(i + 1)
    }.toIntArray()

    val chapters: List<Chapter> = chapters.filter { it.wordIndex in words.indices }.sortedBy { it.wordIndex }

    /** remainingWeight[i] = sum of [WordTiming.weight] for words i until the end. */
    private val remainingWeight = DoubleArray(words.size + 1).also { acc ->
        for (i in words.indices.reversed()) {
            acc[i] = acc[i + 1] + WordTiming.weight(words[i].text, words[i].pause)
        }
    }

    /** Index into [sentenceStarts] of the sentence containing [wordIndex]. */
    fun sentenceOf(wordIndex: Int): Int {
        val pos = sentenceStarts.binarySearch(wordIndex)
        return if (pos >= 0) pos else -pos - 2
    }

    fun sentenceStart(wordIndex: Int): Int = sentenceStarts[sentenceOf(wordIndex)]

    /** Word index range of the sentence containing [wordIndex]. */
    fun sentenceRange(wordIndex: Int): IntRange {
        val s = sentenceOf(wordIndex)
        val end = if (s + 1 < sentenceStarts.size) sentenceStarts[s + 1] - 1 else words.size - 1
        return sentenceStarts[s]..end
    }

    /**
     * The words in [range] as one string, separated by single spaces, except where the tokenizer
     * split a word at a dash ("fortune—must" stays without a space).
     */
    fun joinedText(range: IntRange): JoinedText {
        val sb = StringBuilder()
        val starts = IntArray(range.count())
        for ((k, i) in range.withIndex()) {
            val word = words[i]
            if (k > 0) {
                val prev = words[i - 1]
                if (prev.offset + prev.text.length != word.offset) sb.append(' ')
            }
            starts[k] = sb.length
            sb.append(word.text)
        }
        return JoinedText(sb.toString(), starts)
    }

    /** Start of the sentence after the one containing [wordIndex], or null in the last sentence. */
    fun nextSentenceStart(wordIndex: Int): Int? = sentenceStarts.getOrNull(sentenceOf(wordIndex) + 1)

    /** Start of the sentence before the one containing [wordIndex] (0 if there is none). */
    fun previousSentenceStart(wordIndex: Int): Int = sentenceStarts[(sentenceOf(wordIndex) - 1).coerceAtLeast(0)]

    /** Sentence start at [percent] % of the words (number keys 1–9 jump to 10 %…90 %). */
    fun sentenceStartAtPercent(percent: Int): Int {
        if (words.isEmpty()) return 0
        val target = (words.size.toLong() * percent.coerceIn(0, 100) / 100).toInt().coerceAtMost(words.size - 1)
        return sentenceStart(target)
    }

    /** Index into [chapters] of the chapter containing [wordIndex], or -1 before the first chapter. */
    fun chapterOf(wordIndex: Int): Int = chapters.indexOfLast { it.wordIndex <= wordIndex }

    /**
     * Progress 0.0–1.0 within the chapter containing [wordIndex] (the text before the first
     * chapter counts as one more); null if the text has no chapters.
     */
    fun chapterProgress(wordIndex: Int): Float? {
        if (chapters.isEmpty() || words.isEmpty()) return null
        val c = chapterOf(wordIndex)
        val start = if (c < 0) 0 else chapters[c].wordIndex
        val end = chapters.getOrNull(c + 1)?.wordIndex ?: words.size
        return ((wordIndex - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /** Progress 0.0–1.0 when the word at [wordIndex] is shown. */
    fun progress(wordIndex: Int): Float = if (words.isEmpty()) 0f else wordIndex.toFloat() / words.size

    /** Reading time from [wordIndex] to the end at [wpm], without ramp-up. */
    fun remainingMs(wordIndex: Int, wpm: Int): Long =
        Math.round(remainingWeight[wordIndex.coerceIn(0, words.size)] * WordTiming.baseMs(wpm))

    companion object {
        fun parse(text: String, chapters: List<Chapter> = emptyList()) = ReadingText(Tokenizer.tokenize(text), chapters)
    }
}
