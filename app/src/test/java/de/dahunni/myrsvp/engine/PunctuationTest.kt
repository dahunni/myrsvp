package de.dahunni.myrsvp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationTest {
    @Test
    fun `sentence ends`() {
        listOf("wife.", "now!", "really?", "so…", "wait...", "?!").forEach { assertTrue(it, isSentenceEnd(it)) }
    }

    @Test
    fun `sentence ends with closing quotes and brackets`() {
        listOf("wife.”", "now!\"", "really?’", "(see above.)", "end.»", "it.”)").forEach {
            assertTrue(it, isSentenceEnd(it))
        }
    }

    @Test
    fun `abbreviations are not sentence ends`() {
        listOf("Mr.", "Mrs.", "Ms.", "Dr.", "St.", "Prof.", "Jr.", "Sr.", "vs.", "e.g.", "i.e.", "etc.", "no.", "No.")
            .forEach { assertFalse(it, isSentenceEnd(it)) }
    }

    @Test
    fun `abbreviations after an opening bracket or quote`() {
        assertFalse(isSentenceEnd("(Mr."))
        assertFalse(isSentenceEnd("“Dr."))
    }

    @Test
    fun `initials are not sentence ends`() {
        assertFalse(isSentenceEnd("H."))
        assertFalse(isSentenceEnd("G."))
        // A lower-case single letter is not an initial.
        assertTrue(isSentenceEnd("b."))
    }

    @Test
    fun `an abbreviation followed by a closing quote ends the sentence`() {
        assertTrue(isSentenceEnd("“No.”"))
    }

    @Test
    fun `plain words and clause ends are not sentence ends`() {
        listOf("word", "word,", "word;", "word:", "—", "Mr", "3.5").forEach { assertFalse(it, isSentenceEnd(it)) }
    }

    @Test
    fun `clause ends`() {
        listOf("however,", "this;", "follows:", "and–", "but—", "“No,”").forEach { assertTrue(it, isClauseEnd(it)) }
        listOf("word", "word.", "").forEach { assertFalse(it, isClauseEnd(it)) }
    }

    @Test
    fun `pause class with paragraph end winning`() {
        assertEquals(Pause.None, pauseAfter("word", paragraphEnd = false))
        assertEquals(Pause.Clause, pauseAfter("word,", paragraphEnd = false))
        assertEquals(Pause.Sentence, pauseAfter("word.", paragraphEnd = false))
        assertEquals(Pause.Paragraph, pauseAfter("word.", paragraphEnd = true))
        assertEquals(Pause.Paragraph, pauseAfter("Chapter", paragraphEnd = true))
    }
}
