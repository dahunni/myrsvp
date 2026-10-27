package de.dahunni.myrsvp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerTest {
    @Test
    fun `splits on whitespace and keeps offsets`() {
        val words = Tokenizer.tokenize("It is  a truth")
        assertEquals(listOf("It", "is", "a", "truth"), words.map { it.text })
        assertEquals(listOf(0, 3, 7, 9), words.map { it.offset })
    }

    @Test
    fun `last word of every line is a paragraph end`() {
        val words = Tokenizer.tokenize("Chapter 1\nIt is a truth.\nHowever little")
        assertEquals(Pause.Paragraph, words[1].pause)
        assertEquals(Pause.Paragraph, words[5].pause)
        assertEquals(Pause.Paragraph, words.last().pause)
        assertEquals(Pause.None, words[2].pause)
    }

    @Test
    fun `blank lines and trailing newlines produce no words`() {
        val words = Tokenizer.tokenize("\nOne.\n\n   \nTwo\n")
        assertEquals(listOf("One.", "Two"), words.map { it.text })
        assertTrue(words.all { it.pause == Pause.Paragraph })
        assertTrue(Tokenizer.tokenize("").isEmpty())
    }

    @Test
    fun `punctuation sets the pause`() {
        val words = Tokenizer.tokenize("Mr. Bennet said, “No.” and left")
        assertEquals(
            listOf(Pause.None, Pause.None, Pause.Clause, Pause.Sentence, Pause.None, Pause.Paragraph),
            words.map { it.pause },
        )
    }

    @Test
    fun `dashes between words split them`() {
        val words = Tokenizer.tokenize("fortune—must be")
        assertEquals(listOf("fortune—", "must", "be"), words.map { it.text })
        assertEquals(listOf(0, 8, 13), words.map { it.offset })
        assertEquals(Pause.Clause, words[0].pause)
    }

    @Test
    fun `free-standing and trailing dashes stay`() {
        assertEquals(listOf("so", "—", "then—"), Tokenizer.tokenize("so — then—").map { it.text })
    }
}
