package de.dahunni.myrsvp.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WordTimingTest {
    private val eps = 1e-9

    @Test
    fun `base duration is 60000 over wpm`() {
        assertEquals(200.0, WordTiming.baseMs(300), eps)
        assertEquals(600.0, WordTiming.baseMs(100), eps)
        assertEquals(66.666, WordTiming.baseMs(900), 0.001)
    }

    @Test
    fun `plain words up to 6 letters weigh 1`() {
        assertEquals(1.0, WordTiming.weight("a", Pause.None), eps)
        assertEquals(1.0, WordTiming.weight("single", Pause.None), eps)
        assertEquals(1.0, WordTiming.weight("“single”", Pause.None), eps)
    }

    @Test
    fun `8 percent per letter beyond 6`() {
        assertEquals(1.08, WordTiming.weight("fortune", Pause.None), eps)
        assertEquals(1.40, WordTiming.weight("universally", Pause.None), eps)
    }

    @Test
    fun `punctuation bonuses`() {
        assertEquals(1.5, WordTiming.weight("man,", Pause.Clause), eps)
        assertEquals(2.0, WordTiming.weight("wife.", Pause.Sentence), eps)
        assertEquals(2.5, WordTiming.weight("wife.", Pause.Paragraph), eps)
        assertEquals(1.08 + 1.0, WordTiming.weight("fortune.", Pause.Sentence), eps)
    }

    @Test
    fun `duration at 300 wpm`() {
        assertEquals(200L, WordTiming.durationMs(1.0, 300, wordsSinceStart = 99, rampUp = true))
        assertEquals(400L, WordTiming.durationMs(2.0, 300, wordsSinceStart = 99, rampUp = true))
        assertEquals(500L, WordTiming.durationMs(2.5, 300, wordsSinceStart = 99, rampUp = true))
    }

    @Test
    fun `ramp-up goes linearly from 1_6 to 1_0 over 8 words`() {
        assertEquals(1.6, WordTiming.rampMultiplier(0), eps)
        assertEquals(1.525, WordTiming.rampMultiplier(1), eps)
        assertEquals(1.3, WordTiming.rampMultiplier(4), eps)
        assertEquals(1.075, WordTiming.rampMultiplier(7), eps)
        assertEquals(1.0, WordTiming.rampMultiplier(8), eps)
        assertEquals(1.0, WordTiming.rampMultiplier(1000), eps)
        assertEquals(320L, WordTiming.durationMs(1.0, 300, wordsSinceStart = 0, rampUp = true))
    }

    @Test
    fun `ramp-up can be turned off`() {
        assertEquals(200L, WordTiming.durationMs(1.0, 300, wordsSinceStart = 0, rampUp = false))
    }

    @Test
    fun `speed is clamped to 100 to 900`() {
        assertEquals(100, WordTiming.clampWpm(75))
        assertEquals(900, WordTiming.clampWpm(925))
        assertEquals(350, WordTiming.clampWpm(350))
    }
}
