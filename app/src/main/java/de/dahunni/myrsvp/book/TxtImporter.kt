package de.dahunni.myrsvp.book

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Plain-text books (docs/SPEC.md §4): UTF-8 with a Windows-1252 fallback, blank line = paragraph,
 * lines like "Chapter 3", "CHAPTER III" or "Kapitel 3" are chapter markers, title from the file name.
 */
object TxtImporter {
    /** Below this share of blank lines, a file is taken to have one paragraph per line. */
    private const val MIN_BLANK_LINE_SHARE = 0.05
    private const val MAX_CHAPTER_LINE = 60

    private val CHAPTER_WORDS = "chapter|kapitel|chapitre|capítulo|capitolo|hoofdstuk|book|buch|part|teil|letter|brief"
    private val NUMBER_WORDS = "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|" +
        "fifteen|sixteen|seventeen|eighteen|nineteen|twenty|first|second|third|last|" +
        "eins|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|erstes|zweites|drittes|letztes"
    private val CHAPTER_LINE = Regex(
        "^(?:$CHAPTER_WORDS)\\s+(?:\\d+|[IVXLCDM]+|$NUMBER_WORDS)\\b[.:]?(?:\\s.*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val CHAPTER_IN_TEXT = Regex("(?<=\\s|^)(?:$CHAPTER_WORDS)\\s+(?:\\d+|[IVXLCDM]+)\\b", RegexOption.IGNORE_CASE)
    private val GUTENBERG_FIELD = Regex("^(Title|Author):\\s*(.+)$")
    private val GUTENBERG_START = Regex("^\\*\\*\\*\\s*START OF (THE|THIS) PROJECT GUTENBERG.*$", RegexOption.IGNORE_CASE)
    private val GUTENBERG_END = Regex("^\\*\\*\\*\\s*END OF (THE|THIS) PROJECT GUTENBERG.*$", RegexOption.IGNORE_CASE)

    fun convert(bytes: ByteArray, fileName: String): ConvertedBook {
        val text = decode(bytes).removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val allLines = text.split('\n')
        val lines = stripGutenberg(allLines)

        val nonBlank = lines.count { it.isNotBlank() }
        val blank = lines.size - nonBlank
        val linePerParagraph = nonBlank > 0 && blank < nonBlank * MIN_BLANK_LINE_SHARE

        val paragraphs = ArrayList<String>()
        val chapters = ArrayList<Pair<String, Int>>()
        val current = StringBuilder()
        fun flush() {
            val p = clean(current.toString())
            current.setLength(0)
            if (p.isEmpty()) return
            if (isChapterLine(p)) chapters += p to paragraphs.size
            paragraphs += p
        }
        for (line in lines) {
            when {
                line.isBlank() -> flush()
                linePerParagraph -> { current.append(line); flush() }
                // A chapter line is its own paragraph even without blank lines around it.
                isChapterLine(line.trim()) -> { flush(); current.append(line); flush() }
                else -> current.append(' ').append(line)
            }
        }
        flush()
        val fields = gutenbergFields(allLines)
        val meta = BookMeta(title = fields["Title"] ?: titleFromFileName(fileName), author = fields["Author"])
        return ConvertedBook(meta, paragraphs, withoutContentsEntries(chapters))
    }

    /**
     * A chapter line directly followed by another one is an entry of a table of contents, not a
     * heading: a real heading is followed by text.
     */
    private fun withoutContentsEntries(chapters: List<Pair<String, Int>>): List<Pair<String, Int>> {
        val starts = chapters.map { it.second }.toSet()
        return chapters.filter { (_, paragraph) -> paragraph + 1 !in starts }
    }

    fun isChapterLine(line: String): Boolean = line.length <= MAX_CHAPTER_LINE && CHAPTER_LINE.matches(line)

    /**
     * "I hope Mr. Bingley will like it. CHAPTER II." → "CHAPTER II.": table-of-contents labels
     * that start with an illustration caption are cut to their chapter part.
     */
    fun chapterPart(title: String): String {
        val start = CHAPTER_IN_TEXT.find(title)?.range?.first ?: return title
        return if (start > 0 && isChapterLine(title.substring(start))) title.substring(start) else title
    }

    fun titleFromFileName(fileName: String): String =
        fileName.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { fileName }

    /** Strict UTF-8; anything that isn't valid UTF-8 is read as Windows-1252. */
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1252"))
    }

    /** "Title:" and "Author:" from a Project Gutenberg header (only before its START line). */
    private fun gutenbergFields(lines: List<String>): Map<String, String> {
        val start = lines.indexOfFirst { GUTENBERG_START.matches(it.trim()) }
        if (start < 0) return emptyMap()
        return lines.subList(0, start)
            .mapNotNull { GUTENBERG_FIELD.find(it.trim())?.destructured }
            .associate { (key, value) -> key to value.trim() }
    }

    private fun stripGutenberg(lines: List<String>): List<String> {
        val start = lines.indexOfFirst { GUTENBERG_START.matches(it.trim()) }
        val end = lines.indexOfFirst { GUTENBERG_END.matches(it.trim()) }
        return lines.subList(if (start >= 0) start + 1 else 0, if (end > start) end else lines.size)
    }

    private fun clean(s: String) = s.replace('\u00A0', ' ').replace("\u00AD", "").replace(Regex("\\s+"), " ").trim()
}
