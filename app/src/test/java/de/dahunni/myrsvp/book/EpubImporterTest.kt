package de.dahunni.myrsvp.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubImporterTest {
    private val container = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent()

    private fun opf(nav: Boolean = true, ncx: Boolean = false, date: String = "1813") = """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
            <dc:title>Pride and Prejudice</dc:title>
            <dc:creator opf:role="aut">Jane Austen</dc:creator>
            <dc:language>en</dc:language>
            <dc:date>$date</dc:date>
          </metadata>
          <manifest>
            ${if (nav) """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""" else ""}
            ${if (ncx) """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""" else ""}
            <item id="c1" href="text/ch%201.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine${if (ncx) " toc=\"ncx\"" else ""}><itemref idref="c1"/><itemref idref="c2"/></spine>
        </package>
    """.trimIndent()

    private val navDoc = """
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
        <nav epub:type="toc"><ol>
          <li><a href="text/ch%201.xhtml">A caption. Chapter 1</a></li>
          <li><a href="text/ch2.xhtml#second">Chapter 2</a></li>
        </ol></nav></body></html>
    """.trimIndent()

    private val ncxDoc = """
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
          <navPoint id="a"><navLabel><text>First</text></navLabel><content src="text/ch%201.xhtml"/></navPoint>
          <navPoint id="b"><navLabel><text>Second</text></navLabel><content src="text/ch2.xhtml"/></navPoint>
        </navMap></ncx>
    """.trimIndent()

    private val ch1 = """
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>x</title><style>p{}</style></head><body>
        <h1>Chapter I<br/>The Ball</h1>
        <p>It is a truth <i>universally</i> acknowledged,<a epub:type="noteref" href="#n1">1</a> that a single&#160;man.</p>
        <p><img src="pic.png" alt="A picture"/></p>
        <table><tr><td>Table text</td></tr></table>
        <div>Loose text <p>Nested paragraph.</p> tail text</div>
        <aside epub:type="footnote" id="n1">A footnote.</aside>
        <section class="pg-boilerplate" id="pg-footer"><p>Project Gutenberg license.</p></section>
        </body></html>
    """.trimIndent()

    private val ch2 = """
        <html xmlns="http://www.w3.org/1999/xhtml"><body>
        <p>Intro of two.</p>
        <h2 id="second">Chapter II</h2>
        <p><span class="letra"><img alt="M" src="m.png"/></span>r. Bennet was among the earliest<sup>2</sup>.</p>
        </body></html>
    """.trimIndent()

    private fun epub(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype")); zip.write("application/epub+zip".toByteArray())
            zip.putNextEntry(ZipEntry("OEBPS/images/pic.png")); zip.write(ByteArray(100))
            for ((name, content) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
            }
        }
        return out.toByteArray()
    }

    private fun standard(opf: String = opf()) = epub(
        "META-INF/container.xml" to container,
        "OEBPS/content.opf" to opf,
        "OEBPS/nav.xhtml" to navDoc,
        "OEBPS/toc.ncx" to ncxDoc,
        "OEBPS/text/ch 1.xhtml" to ch1,
        "OEBPS/text/ch2.xhtml" to ch2,
    )

    private fun convert(bytes: ByteArray) = EpubImporter.convert(bytes.inputStream(), "book.epub")

    @Test
    fun `metadata`() {
        assertEquals(BookMeta("Pride and Prejudice", "Jane Austen", "en", 1813), convert(standard()).meta)
    }

    @Test
    fun `paragraphs in spine order without images, tables and footnotes`() {
        assertEquals(
            listOf(
                "Chapter I", "The Ball",
                "It is a truth universally acknowledged, that a single man.",
                "Loose text", "Nested paragraph.", "tail text",
                "Intro of two.", "Chapter II", "Mr. Bennet was among the earliest.",
            ),
            convert(standard()).paragraphs,
        )
    }

    @Test
    fun `chapters from the nav document, anchors included`() {
        assertEquals(listOf("Chapter 1" to 0, "Chapter 2" to 7), convert(standard()).chapters)
    }

    @Test
    fun `chapters from the ncx when there is no nav`() {
        assertEquals(listOf("First" to 0, "Second" to 6), convert(standard(opf(nav = false, ncx = true))).chapters)
    }

    @Test
    fun `chapters from headings when there is no table of contents`() {
        assertEquals(
            listOf("Chapter I The Ball" to 0, "Chapter II" to 7),
            convert(standard(opf(nav = false))).chapters,
        )
    }

    @Test
    fun `dates that are not years are ignored`() {
        assertNull(convert(standard(opf(date = "unknown"))).meta.year)
        assertEquals(1999, convert(standard(opf(date = "1999-05-01"))).meta.year)
        assertNull(convert(standard(opf(date = "9999"))).meta.year)
    }

    @Test
    fun `project gutenberg ebook dates are not publication years`() {
        val gutenberg = opf(date = "1998-06-01").replace("<dc:language>", "<dc:publisher>Project Gutenberg</dc:publisher><dc:language>")
        assertNull(convert(standard(gutenberg)).meta.year)
    }

    @Test
    fun `drm protected books are rejected`() {
        val encryption = """
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/>
              <enc:CipherData><enc:CipherReference URI="OEBPS/text/ch2.xhtml"/></enc:CipherData></enc:EncryptedData>
            </encryption>
        """.trimIndent()
        assertError(ImportError.Drm, epub("META-INF/container.xml" to container, "META-INF/encryption.xml" to encryption))
    }

    @Test
    fun `font obfuscation is not drm`() {
        val encryption = """
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/>
              <enc:CipherData><enc:CipherReference URI="OEBPS/fonts/a.otf"/></enc:CipherData></enc:EncryptedData>
            </encryption>
        """.trimIndent()
        val bytes = epub(
            "META-INF/container.xml" to container, "META-INF/encryption.xml" to encryption,
            "OEBPS/content.opf" to opf(), "OEBPS/nav.xhtml" to navDoc,
            "OEBPS/text/ch 1.xhtml" to ch1, "OEBPS/text/ch2.xhtml" to ch2,
        )
        assertEquals("Pride and Prejudice", convert(bytes).meta.title)
    }

    @Test
    fun `not an epub`() {
        assertError(ImportError.NotAnEpub, epub("hello.txt" to "hi"))
    }

    @Test
    fun `book picker sends zips to the epub importer and text to the txt importer`() {
        assertEquals("Pride and Prejudice", BookImporter.convert(standard(), "x.bin").meta.title)
        assertEquals(listOf("Hello."), BookImporter.convert("Hello.".toByteArray(), "a.txt").paragraphs)
        try {
            BookImporter.convert("%PDF-1.4".toByteArray() + ByteArray(10), "a.pdf")
            fail()
        } catch (e: ImportException) {
            assertEquals(ImportError.Unsupported, e.error)
        }
    }

    private fun assertError(expected: ImportError, bytes: ByteArray) {
        try {
            convert(bytes)
            fail("expected $expected")
        } catch (e: ImportException) {
            assertEquals(expected, e.error)
        }
    }
}
