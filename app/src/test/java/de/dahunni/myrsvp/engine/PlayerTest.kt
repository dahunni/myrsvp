package de.dahunni.myrsvp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTest {
    // Sentences start at 0 (One two three.), 3 (Four five six.), 6 (Seven eight.)
    private val text = ReadingText.parse("One two three. Four five six. Seven eight.")
    private val player = Player(text)
    private val playing = player.play(PlayerState())

    private fun PlayerState.advanced(times: Int) = (1..times).fold(this) { s, _ -> player.advance(s) }

    @Test
    fun `play starts with ramp-up and advance counts words`() {
        assertTrue(playing.playing)
        assertEquals(0, playing.wordsSinceStart)
        val s = playing.advanced(2)
        assertEquals(2, s.index)
        assertEquals(2, s.wordsSinceStart)
    }

    @Test
    fun `resume resets the ramp-up`() {
        val paused = player.pauseNow(playing.advanced(4))
        assertEquals(0, player.play(paused).wordsSinceStart)
        assertEquals(4, player.play(paused).index)
    }

    @Test
    fun `duration includes ramp-up and punctuation`() {
        assertEquals(320L, player.durationMs(playing)) // "One" at 1.6×
        val atThree = playing.advanced(2).copy(wordsSinceStart = 8)
        assertEquals(400L, player.durationMs(atThree)) // "three." = 2.0 × 200 ms
        assertEquals(200L, Player(text, PlayerSettings(rampUp = false)).durationMs(playing))
    }

    @Test
    fun `pause at sentences plays to the end of the sentence`() {
        val requested = player.pause(playing.advanced(1)) // on "two"
        assertTrue(requested.playing)
        assertTrue(requested.pauseAtSentenceEnd)
        val afterTwo = player.advance(requested)
        assertTrue(afterTwo.playing) // "three." still to show
        val stopped = player.advance(afterTwo)
        assertFalse(stopped.playing)
        assertEquals(3, stopped.index) // resumes with "Four"
    }

    @Test
    fun `pause on the last word of a sentence stops after it`() {
        val stopped = player.advance(player.pause(playing.advanced(2)))
        assertFalse(stopped.playing)
        assertEquals(3, stopped.index)
    }

    @Test
    fun `second pause request pauses immediately`() {
        val s = player.pause(player.pause(playing.advanced(1)))
        assertFalse(s.playing)
        assertEquals(1, s.index)
    }

    @Test
    fun `without pause at sentences pause is immediate`() {
        val p = Player(text, PlayerSettings(pauseAtSentences = false))
        val s = p.pause(p.play(PlayerState()).let(p::advance))
        assertFalse(s.playing)
        assertEquals(1, s.index)
    }

    @Test
    fun `toggle play`() {
        assertTrue(player.togglePlay(PlayerState()).playing)
        assertTrue(player.togglePlay(playing).pauseAtSentenceEnd)
    }

    @Test
    fun `playback stops at the end of the text`() {
        val end = playing.advanced(20)
        assertFalse(end.playing)
        assertEquals(7, end.index)
    }

    @Test
    fun `advance does nothing while paused`() {
        assertEquals(PlayerState(), player.advance(PlayerState()))
    }

    @Test
    fun `speed steps of 25 within limits`() {
        assertEquals(325, player.faster(PlayerState()).wpm)
        assertEquals(275, player.slower(PlayerState()).wpm)
        assertEquals(900, player.faster(PlayerState(wpm = 900)).wpm)
        assertEquals(100, player.slower(PlayerState(wpm = 100)).wpm)
    }

    @Test
    fun `back once goes to the start of the current sentence`() {
        val s = player.back(PlayerState(index = 5), nowMs = 10_000)
        assertEquals(3, s.index)
    }

    @Test
    fun `back twice within 1_5 s goes to the previous sentence`() {
        val once = player.back(PlayerState(index = 7).copy(playing = true), nowMs = 10_000)
        assertEquals(6, once.index)
        val movedOn = once.copy(index = 7) // playback went on
        assertEquals(3, player.back(movedOn, nowMs = 11_500).index)
        assertEquals(6, player.back(movedOn, nowMs = 11_501).index)
    }

    @Test
    fun `back at a sentence start goes to the previous sentence`() {
        assertEquals(3, player.back(PlayerState(index = 6), nowMs = 0).index)
        assertEquals(0, player.back(PlayerState(index = 0), nowMs = 0).index)
    }

    @Test
    fun `forward goes to the next sentence start`() {
        assertEquals(3, player.forward(PlayerState(index = 1)).index)
        assertEquals(6, player.forward(PlayerState(index = 3)).index)
        assertEquals(7, player.forward(PlayerState(index = 7)).index) // last sentence: stays
    }

    @Test
    fun `jumps change the seek counter and keep playing`() {
        val jumped = player.forward(playing)
        assertNotEquals(playing.seek, jumped.seek)
        assertTrue(jumped.playing)
    }

    @Test
    fun `a jump while a pause is pending pauses at the target`() {
        val jumped = player.back(player.pause(playing.advanced(4)), nowMs = 0)
        assertEquals(3, jumped.index)
        assertFalse(jumped.playing)
        assertFalse(jumped.pauseAtSentenceEnd)
    }

    @Test
    fun `percent jumps only while paused`() {
        assertEquals(6, player.jumpToPercent(PlayerState(), 90).index) // word 7 → sentence at 6
        assertEquals(3, player.jumpToPercent(PlayerState(), 50).index) // word 4 → sentence at 3
        assertEquals(0, player.jumpToPercent(playing, 90).index)
    }

    @Test
    fun `jumpTo snaps to the sentence start, only while paused`() {
        assertEquals(3, player.jumpTo(PlayerState(), 5).index)
        assertEquals(6, player.jumpTo(PlayerState(), 99).index)
        assertEquals(0, player.jumpTo(playing, 5).index)
        assertNotEquals(PlayerState().seek, player.jumpTo(PlayerState(), 5).seek)
    }

    @Test
    fun `empty text never plays`() {
        val p = Player(ReadingText.parse(""))
        assertFalse(p.play(PlayerState()).playing)
        assertEquals(0, p.back(PlayerState(), 0).index)
        assertEquals(0, p.forward(PlayerState()).index)
    }
}
