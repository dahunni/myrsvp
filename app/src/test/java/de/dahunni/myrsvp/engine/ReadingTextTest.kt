package de.dahunni.myrsvp.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingTextTest {
    // Words: 0 Chapter 1 | 2 It 3 is 4 Mr. 5 Darcy. | 6 He 7 left! | 8 Then 9 came 10 H. 11 G. 12 Wells
    private val text = ReadingText.parse(
        "Chapter 1\nIt is Mr. Darcy. He left!\nThen came H. G. Wells",
        chapters = listOf(Chapter("Chapter 1", 0), Chapter("Chapter 2", 8)),
    )

    @Test
    fun `sentence starts skip abbreviations and initials`() {
        assertArrayEquals(intArrayOf(0, 2, 6, 8), text.sentenceStarts)
    }

    @Test
    fun `sentence of a word`() {
        assertEquals(0, text.sentenceOf(1))
        assertEquals(1, text.sentenceOf(2))
        assertEquals(1, text.sentenceOf(5))
        assertEquals(3, text.sentenceOf(12))
        assertEquals(2..5, text.sentenceRange(4))
        assertEquals(8..12, text.sentenceRange(10))
    }

    @Test
    fun `next and previous sentence`() {
        assertEquals(6, text.nextSentenceStart(3))
        assertNull(text.nextSentenceStart(9))
        assertEquals(2, text.previousSentenceStart(7))
        assertEquals(0, text.previousSentenceStart(1))
    }

    @Test
    fun `percent jumps land on a sentence start`() {
        assertEquals(0, text.sentenceStartAtPercent(10)) // word 1
        assertEquals(2, text.sentenceStartAtPercent(30)) // word 3
        assertEquals(8, text.sentenceStartAtPercent(90)) // word 11
        assertEquals(8, text.sentenceStartAtPercent(100))
    }

    @Test
    fun `chapters`() {
        assertEquals(0, text.chapterOf(7))
        assertEquals(1, text.chapterOf(8))
        assertEquals(-1, ReadingText.parse("a b", listOf(Chapter("x", 1))).chapterOf(0))
    }

    @Test
    fun `chapter progress`() {
        // 13 words; chapters at 0 and 8.
        assertEquals(0f, text.chapterProgress(0)!!, 1e-6f)
        assertEquals(4f / 8, text.chapterProgress(4)!!, 1e-6f)
        assertEquals(0f, text.chapterProgress(8)!!, 1e-6f)
        assertEquals(4f / 5, text.chapterProgress(12)!!, 1e-6f)
        // Before the first chapter, the opening counts as its own part.
        val late = ReadingText.parse("a b c d", listOf(Chapter("x", 2)))
        assertEquals(0.5f, late.chapterProgress(1)!!, 1e-6f)
        assertEquals(null, ReadingText.parse("a b").chapterProgress(1))
    }

    @Test
    fun `chapters outside the text are dropped and the rest sorted`() {
        val t = ReadingText.parse("a b c", listOf(Chapter("2", 2), Chapter("out", 9), Chapter("1", 0)))
        assertEquals(listOf("1", "2"), t.chapters.map { it.title })
    }

    @Test
    fun `remaining time sums word weights`() {
        val t = ReadingText.parse("a b,\nc d.")
        // a 1.0, b, 1.5 (paragraph end overrides → 2.5), c 1.0, d. 2.5
        assertEquals(Math.round(7.0 * 200), t.remainingMs(0, 300))
        assertEquals(Math.round(3.5 * 200), t.remainingMs(2, 300))
        assertEquals(0L, t.remainingMs(4, 300))
    }

    @Test
    fun `joined text keeps dash splits together`() {
        val t = ReadingText.parse("a good   fortune—must be")
        val joined = t.joinedText(0..4)
        assertEquals("a good fortune—must be", joined.text)
        assertArrayEquals(intArrayOf(0, 2, 7, 15, 20), joined.wordStarts)
        assertEquals("fortune—must", t.joinedText(2..3).text)
    }

    @Test
    fun `progress`() {
        assertEquals(0f, text.progress(0))
        assertEquals(0.5f, ReadingText.parse("a b c d").progress(2))
    }

    @Test
    fun `empty text`() {
        val t = ReadingText.parse("")
        assertEquals(0, t.sentenceStarts.size)
        assertEquals(0, t.sentenceStartAtPercent(50))
        assertEquals(0f, t.progress(0))
    }
}
