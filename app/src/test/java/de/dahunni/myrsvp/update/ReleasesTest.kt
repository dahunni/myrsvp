package de.dahunni.myrsvp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    @Test
    fun `versions parse from tags`() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(1, 2, 0), Version.parse("1.2"))
        assertEquals(Version(2, 0, 0), Version.parse("V2"))
        assertEquals(Version(1, 0, 0), Version.parse("1.0.0-beta.1"))
        assertNull(Version.parse("latest"))
        assertNull(Version.parse("1.2.3.4"))
        assertNull(Version.parse("1.-2"))
    }

    @Test
    fun `versions compare numerically`() {
        assertTrue(Version.parse("1.10.0")!! > Version.parse("1.9.9")!!)
        assertTrue(Version.parse("2.0.0")!! > Version.parse("1.99.99")!!)
        assertEquals(0, Version(1, 2, 0).compareTo(Version.parse("v1.2")!!))
    }

    private val json = """
        {
          "tag_name": "v1.3.0",
          "html_url": "https://github.com/dahunni/myrsvp/releases/tag/v1.3.0",
          "draft": false,
          "prerelease": false,
          "body": "## What's new\r\n- **Faster** import\r\n* See [the docs](https://example.com)\r\n\r\nThanks!",
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://x/notes.txt", "size": 10},
            {"name": "myrsvp-1.3.0.apk", "browser_download_url": "https://x/myrsvp-1.3.0.apk", "size": 4200000,
             "digest": "sha256:abc123"}
          ],
          "author": {"login": "dahunni"}
        }
    """.trimIndent()

    @Test
    fun `github release json`() {
        val r = Releases.parse(json)!!
        assertEquals(Version(1, 3, 0), r.version)
        assertEquals("v1.3.0", r.tag)
        assertEquals("https://x/myrsvp-1.3.0.apk", r.apkUrl)
        assertEquals("abc123", r.apkSha256)
        assertEquals(4_200_000L, r.apkSize)
        assertEquals(listOf("What's new", "• Faster import", "• See the docs", "Thanks!"), r.notes)
    }

    @Test
    fun `drafts, pre-releases and releases without apk`() {
        assertNull(Releases.parse(json.replace("\"draft\": false", "\"draft\": true")))
        assertNull(Releases.parse(json.replace("\"prerelease\": false", "\"prerelease\": true")))
        val noApk = Releases.parse("""{"tag_name": "v2.0.0", "assets": []}""")!!
        assertNull(noApk.apkUrl)
        assertEquals(emptyList<String>(), noApk.notes)
    }

    @Test
    fun `notes drop comments and markup`() {
        assertEquals(
            listOf("Plain line", "• x and y", "Heading"),
            Releases.notes("<!-- hidden -->\nPlain line\n  - `x` and **y**\n### Heading\n"),
        )
    }
}
