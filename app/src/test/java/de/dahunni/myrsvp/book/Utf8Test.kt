package de.dahunni.myrsvp.book

import org.junit.Assert.assertEquals
import org.junit.Test

class Utf8Test {
    private val text = "aä€😀b" // 1 + 2 + 3 + 4 + 1 bytes; 😀 is two chars

    @Test
    fun `length matches the encoder`() {
        assertEquals(text.toByteArray(Charsets.UTF_8).size, utf8Length(text))
        assertEquals(11, utf8Length(text))
    }

    @Test
    fun `char to byte`() {
        assertEquals(0, charToByteOffset(text, 0))
        assertEquals(1, charToByteOffset(text, 1))
        assertEquals(3, charToByteOffset(text, 2))
        assertEquals(6, charToByteOffset(text, 3))
        assertEquals(10, charToByteOffset(text, 5))
        assertEquals(11, charToByteOffset(text, 99))
    }

    @Test
    fun `byte to char in any order`() {
        assertEquals(listOf(5, 0, 1, 2, 3), byteToCharOffsets(text, listOf(10, 0, 1, 3, 6)))
    }

    @Test
    fun `offsets inside a character map to that character, past the end to the length`() {
        assertEquals(listOf(1, 2, 6), byteToCharOffsets(text, listOf(2, 4, 50)))
    }
}
