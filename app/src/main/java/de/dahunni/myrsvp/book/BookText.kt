package de.dahunni.myrsvp.book

import de.dahunni.myrsvp.engine.Chapter
import de.dahunni.myrsvp.engine.ReadingText
import de.dahunni.myrsvp.engine.Tokenizer

/**
 * A stored book ready for the reader. Positions on disk are UTF-8 byte offsets into the body
 * (docs/SPEC.md §4); the reader works with word indices, so this converts between the two.
 */
class BookText(val meta: BookMeta, private val body: String, chapters: List<BookChapter>) {
    private val words = Tokenizer.tokenize(body)
    private val wordOffsets = IntArray(words.size) { words[it].offset }

    val text: ReadingText = run {
        val charOffsets = byteToCharOffsets(body, chapters.map { it.byteOffset })
        ReadingText(words, chapters.mapIndexed { i, c -> Chapter(c.title, wordAtChar(charOffsets[i])) }.distinctBy { it.wordIndex })
    }

    val bodyBytes: Int = utf8Length(body)

    /** The sentence start at or before the saved [byteOffset]. */
    fun wordIndexForByteOffset(byteOffset: Int): Int {
        if (words.isEmpty()) return 0
        val char = byteToCharOffsets(body, listOf(byteOffset)).single()
        return text.sentenceStart(wordAtOrBefore(char))
    }

    /** Byte offset to save for the word at [wordIndex]: the start of its sentence. */
    fun byteOffsetForWord(wordIndex: Int): Int {
        if (words.isEmpty()) return 0
        return charToByteOffset(body, words[text.sentenceStart(wordIndex.coerceIn(0, words.size - 1))].offset)
    }

    /** First word starting at or after [char]. */
    private fun wordAtChar(char: Int): Int {
        val pos = wordOffsets.binarySearch(char)
        return (if (pos >= 0) pos else -pos - 1).coerceAtMost((words.size - 1).coerceAtLeast(0))
    }

    /** Last word starting at or before [char]. */
    private fun wordAtOrBefore(char: Int): Int {
        val pos = wordOffsets.binarySearch(char)
        return (if (pos >= 0) pos else -pos - 2).coerceIn(0, words.size - 1)
    }

    companion object {
        fun fromPrb(bytes: ByteArray): BookText {
            val (header, body) = Prb.read(bytes)
            return BookText(header.meta, body, header.chapters)
        }
    }
}
