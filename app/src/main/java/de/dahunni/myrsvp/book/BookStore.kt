package de.dahunni.myrsvp.book

import java.io.File
import java.io.InputStream
import java.util.UUID

/** A book in the library, as listed without reading its body. */
data class StoredBook(
    val id: String,
    val meta: BookMeta,
    val chapterCount: Int,
    val bodyBytes: Long,
    val importedAt: Long,
)

/**
 * The library on disk: one `files/books/<uuid>.prb` per book. Plain blocking I/O; call it from
 * a background dispatcher.
 */
class BookStore(private val dir: File) {
    fun list(): List<StoredBook> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                val header = file.inputStream().buffered().use { Prb.readHeader(it) }
                StoredBook(
                    id = file.name.removeSuffix(EXT),
                    meta = header.meta,
                    chapterCount = header.chapters.size,
                    bodyBytes = file.length() - header.bodyStart,
                    importedAt = file.lastModified(),
                )
            }.getOrNull()
        }
    }

    /**
     * Stores [book]. A book with the same title and author replaces the old file under the same
     * id, so its reading position is kept. Returns the id.
     */
    fun save(book: ConvertedBook): String {
        dir.mkdirs()
        val id = list().firstOrNull { sameBook(it.meta, book.meta) }?.id ?: UUID.randomUUID().toString()
        val tmp = File(dir, "$id$EXT.tmp")
        tmp.outputStream().buffered().use { Prb.write(book, it) }
        if (!tmp.renameTo(file(id))) {
            tmp.delete()
            throw ImportException(ImportError.Unreadable)
        }
        return id
    }

    fun load(id: String): BookText = BookText.fromPrb(file(id).readBytes())

    fun delete(id: String) {
        file(id).delete()
    }

    private fun file(id: String) = File(dir, "$id$EXT")

    private fun sameBook(a: BookMeta, b: BookMeta) =
        a.title.equals(b.title, ignoreCase = true) && a.author.orEmpty().equals(b.author.orEmpty(), ignoreCase = true)

    companion object {
        private const val EXT = ".prb"
    }
}

/** Picks the importer: EPUB if it is a zip (or named .epub), else plain text. */
object BookImporter {
    /** Larger plain-text files are almost certainly not books. */
    private const val MAX_TEXT_BYTES = 32 * 1024 * 1024

    fun convert(bytes: ByteArray, fileName: String): ConvertedBook = convert(bytes.inputStream(), fileName)

    /** Streams EPUBs (their images are never held in memory); text files are read whole. */
    fun convert(input: InputStream, fileName: String): ConvertedBook {
        val buffered = input.buffered()
        buffered.mark(4)
        val magic = ByteArray(4)
        val n = buffered.read(magic)
        buffered.reset()
        val isZip = n >= 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        if (isZip || fileName.endsWith(".epub", ignoreCase = true)) return EpubImporter.convert(buffered, fileName)

        val bytes = buffered.readNBytesCompat(MAX_TEXT_BYTES + 1)
        if (bytes.size > MAX_TEXT_BYTES || !looksLikeText(bytes)) throw ImportException(ImportError.Unsupported)
        return TxtImporter.convert(bytes, fileName).also {
            if (it.paragraphs.isEmpty()) throw ImportException(ImportError.Empty)
        }
    }

    /** No NUL bytes in the first 4 KB and no PDF signature: good enough to keep PDFs and images out. */
    private fun looksLikeText(bytes: ByteArray): Boolean =
        (0 until minOf(bytes.size, 4096)).none { bytes[it] == 0.toByte() } && !bytes.startsWith("%PDF")

    private fun ByteArray.startsWith(prefix: String) =
        size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val r = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (r < 0) break
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }
}
