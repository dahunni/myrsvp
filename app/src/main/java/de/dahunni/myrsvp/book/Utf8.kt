package de.dahunni.myrsvp.book

/** UTF-8 byte length of one UTF-16 char; a surrogate pair counts 2 + 2 = 4 bytes. */
private fun utf8Bytes(c: Char): Int = when {
    c.code < 0x80 -> 1
    c.code < 0x800 -> 2
    c.isSurrogate() -> 2
    else -> 3
}

fun utf8Length(s: String, from: Int = 0, to: Int = s.length): Int {
    var n = 0
    for (i in from until to) n += utf8Bytes(s[i])
    return n
}

/** Byte offset (UTF-8) of the char at [charIndex] in [text]. */
fun charToByteOffset(text: String, charIndex: Int): Int = utf8Length(text, 0, charIndex.coerceIn(0, text.length))

/**
 * Char indices for UTF-8 byte offsets into [text], in one pass. An offset inside a multi-byte
 * character maps to that character; offsets past the end map to text.length.
 */
fun byteToCharOffsets(text: String, byteOffsets: List<Int>): List<Int> {
    val order = byteOffsets.indices.sortedBy { byteOffsets[it] }
    val result = IntArray(byteOffsets.size)
    var bytes = 0
    var chars = 0
    for (k in order) {
        val target = byteOffsets[k]
        while (chars < text.length && bytes + utf8Bytes(text[chars]) <= target) {
            bytes += utf8Bytes(text[chars])
            chars++
        }
        result[k] = chars
    }
    return result.toList()
}
