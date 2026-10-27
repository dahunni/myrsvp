package de.dahunni.myrsvp.book

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.select.NodeFilter
import java.io.InputStream
import java.net.URLDecoder
import java.util.Calendar
import java.util.zip.ZipInputStream

/**
 * EPUB 2/3 books (docs/SPEC.md §4): container.xml → OPF → spine; each XHTML document becomes
 * paragraphs (images, tables and footnote markers dropped). Chapters come from the EPUB3 nav
 * document or the EPUB2 NCX, else from h1–h3 headings.
 */
object EpubImporter {
    private val TEXT_ENTRY = Regex(".*\\.(x?html?|xml|opf|ncx)$", RegexOption.IGNORE_CASE)
    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "blockquote", "li", "ul", "ol", "dl", "dt", "dd", "pre", "address",
        "h1", "h2", "h3", "h4", "h5", "h6", "header", "footer", "main", "nav", "hr", "body",
    )
    private val SKIPPED_TAGS = setOf(
        "head", "script", "style", "img", "image", "svg", "table", "figure", "math", "object", "video", "audio", "sup",
    )
    private val NOTE_TYPES = Regex("\\b(noteref|footnote|endnote|endnotes|footnotes|rearnote|rearnotes)\\b")
    private val CHAPTER_HEADINGS = setOf("h1", "h2", "h3")
    /** Project Gutenberg's license header and footer, marked in all their EPUBs. */
    private val BOILERPLATE_IDS = setOf("pg-header", "pg-footer")
    private const val BOILERPLATE_CLASS = "pg-boilerplate"
    private val DROP_CAP = Regex("^\\p{L}$")

    /** IDPF and Adobe font obfuscation: "encrypted" fonts are fine, encrypted text is DRM. */
    private val FONT_OBFUSCATION = setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")

    fun convert(input: InputStream, fileName: String): ConvertedBook = convert(readTextEntries(input), fileName)

    fun convert(entries: Map<String, String>, fileName: String): ConvertedBook {
        checkDrm(entries)
        val container = entries["META-INF/container.xml"] ?: throw ImportException(ImportError.NotAnEpub)
        val opfPath = xml(container).selectFirst("rootfile[full-path], *|rootfile[full-path]")?.attr("full-path")
            ?: throw ImportException(ImportError.NotAnEpub)
        val opf = xml(entries[opfPath] ?: throw ImportException(ImportError.NotAnEpub))
        val opfDir = opfPath.substringBeforeLast('/', "")

        val manifest = opf.items().associate { item ->
            item.attr("id") to resolve(opfDir, item.attr("href"))
        }
        val spine = opf.select("itemref, *|itemref").mapNotNull { manifest[it.attr("idref")] }

        // Paragraphs of all spine documents, remembering where each document and anchor starts.
        val paragraphs = ArrayList<String>()
        val headings = ArrayList<Pair<String, Int>>()
        val anchors = HashMap<String, Int>()
        for (path in spine) {
            val html = entries[path] ?: continue
            anchors[path] = paragraphs.size
            extract(Jsoup.parse(html), path, paragraphs, headings, anchors)
        }
        if (paragraphs.isEmpty()) throw ImportException(ImportError.Empty)

        val toc = navChapters(opf, manifest, entries) ?: ncxChapters(opf, manifest, entries)
        val chapters = toc
            ?.map { (title, target) -> TxtImporter.chapterPart(title) to target }
            ?.mapNotNull { (title, target) -> anchors[target]?.let { title to it } ?: anchors[target.substringBefore('#')]?.let { title to it } }
            ?.takeIf { it.isNotEmpty() }
            ?: headings

        return ConvertedBook(metadata(opf, fileName), paragraphs, chapters)
    }

    /** Reads only the text-like entries of the zip; images and fonts are never kept in memory. */
    fun readTextEntries(input: InputStream): Map<String, String> {
        val entries = HashMap<String, String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && (TEXT_ENTRY.matches(entry.name) || entry.name.startsWith("META-INF/"))) {
                    entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        if (entries.isEmpty()) throw ImportException(ImportError.NotAnEpub)
        return entries
    }

    private fun checkDrm(entries: Map<String, String>) {
        if ("META-INF/rights.xml" in entries) throw ImportException(ImportError.Drm)
        val encryption = entries["META-INF/encryption.xml"] ?: return
        val encryptsContent = xml(encryption).select("*|EncryptedData").any { data ->
            val algorithm = data.selectFirst("*|EncryptionMethod")?.attr("Algorithm").orEmpty()
            algorithm !in FONT_OBFUSCATION
        }
        if (encryptsContent) throw ImportException(ImportError.Drm)
    }

    private fun metadata(opf: Document, fileName: String): BookMeta {
        val title = opf.selectFirst("dc|title")?.text()?.trim()
        val author = opf.select("dc|creator")
            .firstOrNull { val role = it.attr("opf:role"); role.isEmpty() || role == "aut" }
            ?.text()?.trim()
        val language = opf.selectFirst("dc|language")?.text()?.trim()
        return BookMeta(
            title = title?.ifEmpty { null } ?: TxtImporter.titleFromFileName(fileName),
            author = author?.ifEmpty { null },
            language = language?.ifEmpty { null },
            // Project Gutenberg's dc:date is the date of the ebook, not of the book.
            year = if (isGutenberg(opf)) null else publicationYear(opf),
        )
    }

    private fun isGutenberg(opf: Document) =
        opf.select("dc|publisher, dc|identifier, dc|source").any { it.text().contains("gutenberg", ignoreCase = true) }

    /** dc:date as a year, unless it is marked as a modification/conversion date or lies in the future. */
    private fun publicationYear(opf: Document): Int? {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return opf.select("dc|date")
            .filter { it.attr("opf:event").let { e -> e.isEmpty() || e == "publication" || e == "original-publication" } }
            .firstNotNullOfOrNull { Regex("^\\s*(-?\\d{3,4})").find(it.text())?.groupValues?.get(1)?.toIntOrNull() }
            ?.takeIf { it in 1..currentYear }
    }

    private fun navChapters(opf: Document, manifest: Map<String, String>, entries: Map<String, String>): List<Pair<String, String>>? {
        val navId = opf.items().firstOrNull { it.attr("properties").split(' ').contains("nav") }?.attr("id")
        val navPath = manifest[navId] ?: return null
        val nav = Jsoup.parse(entries[navPath] ?: return null)
        val toc = nav.select("nav").firstOrNull { it.attr("epub:type").split(' ').contains("toc") } ?: nav.selectFirst("nav")
        val navDir = navPath.substringBeforeLast('/', "")
        return toc?.select("li > a[href]")
            ?.map { it.text().trim() to resolve(navDir, it.attr("href")) }
            ?.filter { it.first.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun ncxChapters(opf: Document, manifest: Map<String, String>, entries: Map<String, String>): List<Pair<String, String>>? {
        val ncxPath = manifest[opf.selectFirst("spine, *|spine")?.attr("toc")]
            ?: opf.items().firstOrNull { it.attr("media-type") == "application/x-dtbncx+xml" }
                ?.let { manifest[it.attr("id")] }
            ?: return null
        val ncx = xml(entries[ncxPath] ?: return null)
        val ncxDir = ncxPath.substringBeforeLast('/', "")
        return ncx.select("navPoint")
            .mapNotNull { point ->
                val title = point.selectFirst("navLabel > text")?.text()?.trim().orEmpty()
                val src = point.selectFirst("content")?.attr("src") ?: return@mapNotNull null
                if (title.isEmpty()) null else title to resolve(ncxDir, src)
            }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Collects the text of [doc] into [paragraphs]: every block element ends a paragraph, `<br>`
     * too. h1–h3 are recorded in [headings]; element ids in [anchors] as "path#id".
     */
    private fun extract(
        doc: Document,
        path: String,
        paragraphs: MutableList<String>,
        headings: MutableList<Pair<String, Int>>,
        anchors: MutableMap<String, Int>,
    ) {
        val current = StringBuilder()
        var headingStart = -1
        fun flush() {
            val p = current.toString().replace('\u00A0', ' ').replace("\u00AD", "").replace("\u200B", "")
                .replace(Regex("\\s+"), " ").trim()
            current.setLength(0)
            if (p.isNotEmpty()) paragraphs += p
        }
        val body = doc.body()
        body.filter(object : NodeFilter {
            override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
                if (node is TextNode) {
                    current.append(node.wholeText)
                    return NodeFilter.FilterResult.CONTINUE
                }
                val el = node as? Element ?: return NodeFilter.FilterResult.CONTINUE
                val tag = el.normalName()
                // A drop cap drawn as an image (<img alt="N">) still belongs to its word.
                if (tag == "img" && DROP_CAP.matches(el.attr("alt"))) current.append(el.attr("alt"))
                if (tag in SKIPPED_TAGS || isNote(el) || isBoilerplate(el)) return NodeFilter.FilterResult.SKIP_ENTIRELY
                if (el.id().isNotEmpty()) anchors.putIfAbsent("$path#${el.id()}", paragraphs.size + if (current.isBlank()) 0 else 1)
                when {
                    tag == "br" -> flush()
                    tag in BLOCK_TAGS -> flush()
                }
                if (tag in CHAPTER_HEADINGS) headingStart = paragraphs.size
                return NodeFilter.FilterResult.CONTINUE
            }

            override fun tail(node: Node, depth: Int): NodeFilter.FilterResult {
                val el = node as? Element ?: return NodeFilter.FilterResult.CONTINUE
                val tag = el.normalName()
                if (tag in BLOCK_TAGS) {
                    flush()
                    if (tag in CHAPTER_HEADINGS && headingStart in 0 until paragraphs.size) {
                        // A heading split by <br> ("Chapter I<br/>Down the Rabbit-Hole") is one title.
                        headings += paragraphs.subList(headingStart, paragraphs.size).joinToString(" ") to headingStart
                        headingStart = -1
                    }
                }
                return NodeFilter.FilterResult.CONTINUE
            }
        })
        flush()
    }

    private fun isBoilerplate(el: Element) = el.id() in BOILERPLATE_IDS || el.hasClass(BOILERPLATE_CLASS)

    private fun isNote(el: Element): Boolean {
        val type = el.attr("epub:type") + " " + el.attr("role").removePrefix("doc-")
        return NOTE_TYPES.containsMatchIn(type)
    }

    /** Manifest items, also in OPFs that prefix their elements ("opf:item"). */
    private fun Document.items() = select("manifest > item, *|manifest > *|item")

    private fun xml(s: String): Document = Jsoup.parse(s, "", Parser.xmlParser())

    /** Resolves [href] (URL-encoded, maybe with "../") against the directory [dir] inside the zip. */
    private fun resolve(dir: String, href: String): String {
        val decoded = URLDecoder.decode(href.replace("+", "%2B"), "UTF-8")
        val parts = ArrayList<String>()
        if (!decoded.startsWith("/")) dir.split('/').filter { it.isNotEmpty() }.forEach { parts += it }
        for (part in decoded.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }
}
