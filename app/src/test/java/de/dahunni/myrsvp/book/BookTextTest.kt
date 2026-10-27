package de.dahunni.myrsvp.book

import de.dahunni.myrsvp.engine.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class BookTextTest {
    private val converted = ConvertedBook(
        meta = BookMeta("Über", "Autorin"),
        paragraphs = listOf("Kapitel 1", "Grüße aus Köln. Schön war's.", "Kapitel 2", "Straße und Brücke."),
        chapters = listOf("Kapitel 1" to 0, "Kapitel 2" to 2),
    )
    private val book = BookText.fromPrb(Prb.toBytes(converted))

    @Test
    fun `chapters map from bytes to words`() {
        // Words: 0 Kapitel 1 1 | 2 Grüße 3 aus 4 Köln. 5 Schön 6 war's. | 7 Kapitel 8 2 | 9 Straße 10 und 11 Brücke.
        assertEquals(listOf(Chapter("Kapitel 1", 0), Chapter("Kapitel 2", 7)), book.text.chapters)
    }

    @Test
    fun `saved offsets are sentence starts in bytes and round trip`() {
        val offset = book.byteOffsetForWord(6) // in "Schön war's." → sentence start "Schön"
        val body = Prb.read(Prb.toBytes(converted)).second
        assertEquals(body.indexOf("Schön").let { charToByteOffset(body, it) }, offset)
        assertEquals(5, book.wordIndexForByteOffset(offset))
    }

    @Test
    fun `an offset inside a sentence restores its start`() {
        val body = Prb.read(Prb.toBytes(converted)).second
        assertEquals(2, book.wordIndexForByteOffset(charToByteOffset(body, body.indexOf("Köln"))))
        assertEquals(9, book.wordIndexForByteOffset(Int.MAX_VALUE))
        assertEquals(0, book.wordIndexForByteOffset(0))
    }

    @Test
    fun `body size in bytes`() {
        assertEquals(Prb.read(Prb.toBytes(converted)).second.toByteArray().size, book.bodyBytes)
    }

    @Test
    fun `store replaces a book with the same title and author, keeping its id`() {
        val dir = Files.createTempDirectory("books").toFile()
        try {
            val store = BookStore(dir)
            val id = store.save(converted)
            val other = store.save(converted.copy(meta = BookMeta("Other")))
            assertEquals(id, store.save(converted.copy(paragraphs = converted.paragraphs + "Neu.")))
            assertEquals(setOf(id, other), store.list().map { it.id }.toSet())
            assertEquals("Neu.", store.load(id).text.words.last().text)
            assertEquals(2, store.list().first { it.id == id }.chapterCount)
            store.delete(other)
            assertEquals(listOf(id), store.list().map { it.id })
        } finally {
            dir.deleteRecursively()
        }
    }
}
