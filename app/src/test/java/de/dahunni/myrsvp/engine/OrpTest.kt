package de.dahunni.myrsvp.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class OrpTest {
    @Test
    fun `index follows the length table`() {
        assertEquals(0, orpIndex("a"))
        assertEquals(1, orpIndex("is"))
        assertEquals(1, orpIndex("truth"))
        assertEquals(2, orpIndex("single"))
        assertEquals(2, orpIndex("possessed"))
        assertEquals(3, orpIndex("universally"))
        assertEquals(3, orpIndex("neighbourhood"))
        assertEquals(4, orpIndex("acknowledgement"))
        assertEquals(4, orpIndex("incomprehensibilities"))
    }

    @Test
    fun `punctuation does not count as letters`() {
        assertEquals(2, orpIndex("“Hello,")) // H e l l o → 2nd letter 'e' at index 2
        assertEquals(1, orpIndex("wife.")) // 4 letters
        assertEquals(2, orpIndex("(it)"))
        assertEquals(1, orpIndex("“I")) // single letter after the quote
        assertEquals(2, orpIndex("single,”")) // 6 letters, trailing punctuation ignored
    }

    @Test
    fun `digits count like letters`() {
        assertEquals(1, orpIndex("1813"))
    }

    @Test
    fun `words without letters focus on the first character`() {
        assertEquals(0, orpIndex("—"))
        assertEquals(0, orpIndex("…"))
        assertEquals(0, orpIndex(""))
    }

    @Test
    fun `split at orp keeps all characters`() {
        assertEquals(OrpParts("uni", "v", "ersally"), splitAtOrp("universally"))
        assertEquals(OrpParts("“H", "e", "llo,"), splitAtOrp("“Hello,"))
        assertEquals(OrpParts("", "a", ""), splitAtOrp("a"))
        assertEquals(OrpParts("", "", ""), splitAtOrp(""))
    }

    @Test
    fun `letter count ignores punctuation`() {
        assertEquals(5, letterCount("“Hello,”"))
        assertEquals(0, letterCount("—"))
    }
}
