package de.dahunni.myrsvp.book

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Book metadata as stored in the PRB header (docs/SPEC.md §4). */
data class BookMeta(
    val title: String,
    val author: String? = null,
    val language: String? = null,
    val year: Int? = null,
)

/** A chapter in a stored book; [byteOffset] counts from the first byte after the `---` line. */
data class BookChapter(val title: String, val byteOffset: Int)

/** What an importer produces: paragraphs (no line breaks inside) and chapters by paragraph index. */
data class ConvertedBook(
    val meta: BookMeta,
    val paragraphs: List<String>,
    val chapters: List<Pair<String, Int>>,
)

/** The header of a stored book, plus where its body starts in the file. */
data class PrbHeader(
    val meta: BookMeta,
    val chapters: List<BookChapter>,
    val bodyStart: Int,
)

class PrbFormatException(message: String) : Exception(message)

/**
 * The internal book format "PRB1": a small `key: value` header, a `---` line, then one paragraph
 * per line, all UTF-8.
 */
object Prb {
    private const val MAGIC = "PRB1"
    private const val SEPARATOR = "---"

    fun write(book: ConvertedBook, out: OutputStream) {
        val cleaned = book.paragraphs.map(::oneLine)
        val paragraphs = cleaned.filter { it.isNotEmpty() }
        // Empty paragraphs are dropped, so chapter paragraph indices are mapped to the kept ones.
        val keptIndex = IntArray(cleaned.size + 1)
        for (i in cleaned.indices) keptIndex[i + 1] = keptIndex[i] + if (cleaned[i].isNotEmpty()) 1 else 0
        val offsets = IntArray(paragraphs.size + 1)
        for (i in paragraphs.indices) offsets[i + 1] = offsets[i] + utf8Length(paragraphs[i]) + 1

        val header = StringBuilder()
        header.append(MAGIC).append('\n')
        header.append("title: ").append(oneLine(book.meta.title)).append('\n')
        book.meta.author?.let { header.append("author: ").append(oneLine(it)).append('\n') }
        book.meta.language?.let { header.append("language: ").append(oneLine(it)).append('\n') }
        book.meta.year?.let { header.append("year: ").append(it).append('\n') }
        book.chapters
            .map { (title, paragraph) -> oneLine(title) to keptIndex[paragraph.coerceIn(0, book.paragraphs.size)] }
            .filter { (_, p) -> p < paragraphs.size }
            .distinctBy { it.second }
            .sortedBy { it.second }
            .forEach { (title, p) -> header.append("chapter: ").append(offsets[p]).append(' ').append(title).append('\n') }
        header.append(SEPARATOR).append('\n')

        out.write(header.toString().toByteArray(Charsets.UTF_8))
        for (p in paragraphs) out.write((p + "\n").toByteArray(Charsets.UTF_8))
    }

    fun toBytes(book: ConvertedBook): ByteArray = ByteArrayOutputStream().also { write(book, it) }.toByteArray()

    /** Reads only the header; [input] is left positioned somewhere after it. */
    fun readHeader(input: InputStream): PrbHeader {
        var bytesRead = 0
        fun readLine(): String? {
            val buf = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8.name())
                bytesRead++
                if (b == '\n'.code) return buf.toString(Charsets.UTF_8.name()).removeSuffix("\r")
                buf.write(b)
            }
        }
        if (readLine() != MAGIC) throw PrbFormatException("Not a PRB1 file")
        var title = ""
        var author: String? = null
        var language: String? = null
        var year: Int? = null
        val chapters = ArrayList<BookChapter>()
        while (true) {
            val line = readLine() ?: throw PrbFormatException("Header without ---")
            if (line == SEPARATOR) break
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val value = line.substring(colon + 1).trim()
            when (line.substring(0, colon)) {
                "title" -> title = value
                "author" -> author = value.ifEmpty { null }
                "language" -> language = value.ifEmpty { null }
                "year" -> year = value.toIntOrNull()
                "chapter" -> {
                    val space = value.indexOf(' ')
                    val offset = (if (space < 0) value else value.substring(0, space)).toIntOrNull()
                    if (offset != null) chapters += BookChapter(if (space < 0) "" else value.substring(space + 1), offset)
                }
            }
        }
        return PrbHeader(BookMeta(title, author, language, year), chapters, bytesRead)
    }

    /** Header and body (paragraphs joined by '\n', without a trailing line break). */
    fun read(bytes: ByteArray): Pair<PrbHeader, String> {
        val header = readHeader(bytes.inputStream())
        val body = String(bytes, header.bodyStart, bytes.size - header.bodyStart, Charsets.UTF_8).trimEnd('\n')
        return header to body
    }

    private fun oneLine(s: String) = s.replace(Regex("\\s+"), " ").trim()
}
