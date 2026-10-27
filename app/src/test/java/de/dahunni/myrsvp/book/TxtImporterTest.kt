package de.dahunni.myrsvp.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtImporterTest {
    private fun convert(s: String, name: String = "pride_and_prejudice.txt") =
        TxtImporter.convert(s.toByteArray(Charsets.UTF_8), name)

    @Test
    fun `blank lines separate paragraphs, hard-wrapped lines are joined`() {
        val book = convert("It is a truth\nuniversally acknowledged.\n\nHowever little\nknown.\n")
        assertEquals(listOf("It is a truth universally acknowledged.", "However little known."), book.paragraphs)
    }

    @Test
    fun `title from the file name`() {
        assertEquals("pride and prejudice", convert("x").meta.title)
        assertEquals("Walden", TxtImporter.titleFromFileName("/storage/Walden.txt"))
    }

    @Test
    fun `chapter lines become chapters`() {
        val book = convert("Chapter 1\n\nIt is a truth.\n\nCHAPTER III.\n\nMore.\n\nKapitel 3\n\nMehr.")
        assertEquals(listOf("Chapter 1" to 0, "CHAPTER III." to 2, "Kapitel 3" to 4), book.chapters)
    }

    @Test
    fun `a table of contents does not produce chapters`() {
        val book = convert("Contents\n\nLetter 1\nLetter 2\nChapter 1\n\nLetter 1\n\nDear sister.\n\nLetter 2\n\nAgain.\n\nChapter 1\n\nI am.")
        assertEquals(listOf("Letter 1" to 4, "Letter 2" to 6, "Chapter 1" to 8), book.chapters)
    }

    @Test
    fun `chapter line without blank lines around it is still its own paragraph`() {
        val book = convert("Intro text.\nChapter 2. The Visit\nHe came.\n\nEnd.")
        assertEquals(listOf("Intro text.", "Chapter 2. The Visit", "He came.", "End."), book.paragraphs)
        assertEquals(listOf("Chapter 2. The Visit" to 1), book.chapters)
    }

    @Test
    fun `chapter detection`() {
        listOf("Chapter 1", "CHAPTER XLII", "Chapter One", "Kapitel 12", "Part II", "Chapter 4: The End").forEach {
            assertTrue(it, TxtImporter.isChapterLine(it))
        }
        listOf("Chapters are long", "Part of the plan.", "Chapter", "I read chapter 1 of the book and it was very long indeed").forEach {
            assertFalse(it, TxtImporter.isChapterLine(it))
        }
    }

    @Test
    fun `files without blank lines have one paragraph per line`() {
        val book = convert("First paragraph.\nSecond paragraph.\nThird paragraph.")
        assertEquals(3, book.paragraphs.size)
    }

    @Test
    fun `windows-1252 fallback`() {
        val bytes = byteArrayOf('G'.code.toByte(), 0xFC.toByte(), 'n'.code.toByte(), 't'.code.toByte(), 'e'.code.toByte(), 'r'.code.toByte())
        assertEquals(listOf("Günter"), TxtImporter.convert(bytes, "a.txt").paragraphs)
    }

    @Test
    fun `title and author from a project gutenberg header`() {
        val text = "Title: Frankenstein; Or, The Modern Prometheus\nAuthor: Mary Wollstonecraft Shelley\n\n" +
            "*** START OF THE PROJECT GUTENBERG EBOOK 84 ***\nLetter 1\n\nYou will rejoice.\n"
        val book = convert(text, "pg84.txt")
        assertEquals(BookMeta("Frankenstein; Or, The Modern Prometheus", "Mary Wollstonecraft Shelley"), book.meta)
        assertEquals(listOf("Letter 1" to 0), book.chapters)
    }

    @Test
    fun `chapter part of a table-of-contents label`() {
        assertEquals("CHAPTER II.", TxtImporter.chapterPart("I hope Mr. Bingley will like it. CHAPTER II."))
        assertEquals("Chapter 1", TxtImporter.chapterPart("Chapter 1"))
        assertEquals("The Chapter House", TxtImporter.chapterPart("The Chapter House"))
        assertEquals("PRIDE. and PREJUDICE", TxtImporter.chapterPart("PRIDE. and PREJUDICE"))
    }

    @Test
    fun `bom, crlf and project gutenberg boilerplate are removed`() {
        val text = "\uFEFFThe Project Gutenberg eBook\r\n\r\n*** START OF THE PROJECT GUTENBERG EBOOK WALDEN ***\r\n" +
            "Economy.\r\n\r\nWhen I wrote.\r\n*** END OF THE PROJECT GUTENBERG EBOOK WALDEN ***\r\nLicense text."
        assertEquals(listOf("Economy.", "When I wrote."), convert(text).paragraphs)
    }
}
