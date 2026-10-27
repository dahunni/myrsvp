package de.dahunni.myrsvp.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WordSplitterTest {
    private fun maxLength(n: Int): (String) -> Boolean = { it.length <= n }

    @Test
    fun `words that fit stay whole`() {
        assertEquals(listOf("truth"), WordSplitter.split("truth", maxLength(10)))
    }

    @Test
    fun `split at existing hyphens first`() {
        assertEquals(listOf("mother-", "in-law"), WordSplitter.split("mother-in-law", maxLength(7)))
        assertEquals(listOf("mother-", "in-", "law"), WordSplitter.split("mother-in-law", maxLength(4).or(7)))
    }

    @Test
    fun `hyphen parts that are still too long are cut evenly`() {
        assertEquals(listOf("extraor-", "dinary-", "well-", "known"), WordSplitter.split("extraordinary-well-known", maxLength(9)))
    }

    @Test
    fun `otherwise roughly equal chunks with a hyphen`() {
        assertEquals(listOf("acknow-", "ledged,"), WordSplitter.split("acknowledged,", maxLength(7)))
    }

    @Test
    fun `cuts move to a syllable boundary when the chunks still fit`() {
        // Evenly it would be "incom-prehe-nsible".
        assertEquals(listOf("incom-", "prehen-", "sible"), WordSplitter.split("incomprehensible", maxLength(7)))
        // "prehen-" would be too long for 6, so the even cut stays.
        assertEquals(listOf("incom-", "prehe-", "nsible"), WordSplitter.split("incomprehensible", maxLength(6)))
    }

    @Test
    fun `chunks never get lost characters`() {
        val word = "Donaudampfschifffahrtsgesellschaft"
        val chunks = WordSplitter.split(word, maxLength(8))
        assertEquals(word, chunks.joinToString("") { it.removeSuffix("-") })
        chunks.forEach { assertEquals(it, true, it.length <= 8) }
    }

    @Test
    fun `gives up at single letters`() {
        assertEquals(listOf("a-", "b-", "c"), WordSplitter.split("abc", { false }))
    }

    private fun ((String) -> Boolean).or(n: Int): (String) -> Boolean = { this(it) || it.length == n }
}
