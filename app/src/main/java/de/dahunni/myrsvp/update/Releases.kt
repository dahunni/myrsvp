package de.dahunni.myrsvp.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A release version "1.2.3"; tags may start with "v", missing parts count as 0, suffixes are ignored. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(text: String): Version? {
            val core = text.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            val parts = core.split('.')
            if (parts.size !in 1..3) return null
            val numbers = parts.map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
            val (major, minor, patch) = numbers + List(3 - numbers.size) { 0 }
            return Version(major, minor, patch)
        }
    }
}

/** A published release as the updater needs it. */
data class Release(
    val version: Version,
    val tag: String,
    /** Release notes as plain lines, ready to show. */
    val notes: List<String>,
    val pageUrl: String,
    /** The attached APK, null if the release has none (yet). */
    val apkUrl: String?,
    val apkSha256: String?,
    val apkSize: Long,
)

/** GitHub's "latest release" JSON → [Release] (https://docs.github.com/rest/releases/releases). */
object Releases {
    @Serializable
    private class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
        /** "sha256:<hex>" on newer releases. */
        val digest: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Null for drafts, pre-releases and tags that aren't versions. */
    fun parse(text: String): Release? {
        val r = json.decodeFromString<GitHubRelease>(text)
        if (r.draft || r.prerelease) return null
        val version = Version.parse(r.tagName) ?: return null
        val apk = r.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        return Release(
            version = version,
            tag = r.tagName,
            notes = notes(r.body.orEmpty()),
            pageUrl = r.htmlUrl,
            apkUrl = apk?.url,
            apkSha256 = apk?.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
            apkSize = apk?.size ?: 0,
        )
    }

    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val EMPHASIS = Regex("(\\*\\*|__|\\*|_|`)(.+?)\\1")
    private val LIST_ITEM = Regex("^\\s*[-*+]\\s+")
    private val HEADING = Regex("^#+\\s*")
    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

    /** Markdown release notes as plain lines: headings without "#", list items with "•", links as their text. */
    fun notes(markdown: String): List<String> =
        markdown.replace(COMMENT, "").replace("\r\n", "\n").split('\n')
            .map { line ->
                var l = line.trimEnd()
                l = if (LIST_ITEM.containsMatchIn(l)) "• " + l.replace(LIST_ITEM, "") else l.trim().replace(HEADING, "")
                l.replace(LINK, "$1").replace(EMPHASIS, "$2")
            }
            .filter { it.isNotBlank() }
}
