package de.dahunni.myrsvp.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrbTest {
    private val book = ConvertedBook(
        meta = BookMeta("Pride and Prejudice", "Jane Austen", "en", 1813),
        paragraphs = listOf("Chapter 1", "It is a truth.", "", "Chapter 2", "Mr. Bennet was among the earliest."),
        chapters = listOf("Chapter 1" to 0, "Chapter 2" to 3),
    )

    @Test
    fun `writes the documented format`() {
        val text = Prb.toBytes(book).toString(Charsets.UTF_8)
        assertEquals(
            """
            PRB1
            title: Pride and Prejudice
            author: Jane Austen
            language: en
            year: 1813
            chapter: 0 Chapter 1
            chapter: 25 Chapter 2
            ---
            Chapter 1
            It is a truth.
            Chapter 2
            Mr. Bennet was among the earliest.

            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `round trip`() {
        val (header, body) = Prb.read(Prb.toBytes(book))
        assertEquals(book.meta, header.meta)
        assertEquals(listOf(BookChapter("Chapter 1", 0), BookChapter("Chapter 2", 25)), header.chapters)
        assertEquals("Chapter 1\nIt is a truth.\nChapter 2\nMr. Bennet was among the earliest.", body)
    }

    @Test
    fun `chapter offsets are bytes, not chars`() {
        val b = ConvertedBook(BookMeta("Ü"), listOf("Grüße", "Straße"), listOf("Zwei" to 1))
        val bytes = Prb.toBytes(b)
        val (header, body) = Prb.read(bytes)
        assertEquals(8, header.chapters.single().byteOffset) // "Grüße" is 7 bytes, plus the line break
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        assertEquals("Straße", String(bodyBytes, 8, bodyBytes.size - 8, Charsets.UTF_8))
    }

    @Test
    fun `line breaks in values and paragraphs are flattened`() {
        val b = ConvertedBook(BookMeta("Two\nlines"), listOf("a\nb", "  c  "), emptyList())
        val (header, body) = Prb.read(Prb.toBytes(b))
        assertEquals("Two lines", header.meta.title)
        assertNull(header.meta.author)
        assertEquals("a b\nc", body)
    }

    @Test(expected = PrbFormatException::class)
    fun `rejects other files`() {
        Prb.read("hello".toByteArray())
    }
}
