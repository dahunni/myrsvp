package de.dahunni.myrsvp.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderKeyInterpreterTest {
    private val keys = ReaderKeyInterpreter(longPressMs = 500)

    private fun down(code: Int, repeat: Int = 0, held: Long = 0, paused: Boolean = false) =
        keys.onKey(code, down = true, repeat, held, paused)
    private fun up(code: Int, held: Long = 0) = keys.onKey(code, down = false, repeatCount = 0, heldMs = held, paused = false)

    @Test
    fun `ok acts on release`() {
        assertNull(down(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(ReaderAction.PlayPause, up(KeyEvent.KEYCODE_DPAD_CENTER, held = 100))
        assertNull(down(KeyEvent.KEYCODE_ENTER))
        assertEquals(ReaderAction.PlayPause, up(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun `long-press ok opens the menu once and does not toggle play`() {
        assertNull(down(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(down(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 1, held = 300))
        assertEquals(ReaderAction.OpenMenu, down(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 2, held = 550))
        assertNull(down(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 3, held = 600))
        assertNull(up(KeyEvent.KEYCODE_DPAD_CENTER, held = 700))
        // The next short press works normally again.
        assertNull(down(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(ReaderAction.PlayPause, up(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `speed keys repeat while held`() {
        assertEquals(ReaderAction.SpeedUp, down(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(ReaderAction.SpeedUp, down(KeyEvent.KEYCODE_DPAD_UP, repeat = 5))
        assertEquals(ReaderAction.SpeedDown, down(KeyEvent.KEYCODE_DPAD_DOWN, repeat = 1))
        assertNull(up(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun `up and down page while paused, once per press`() {
        assertEquals(ReaderAction.PageUp, down(KeyEvent.KEYCODE_DPAD_UP, paused = true))
        assertEquals(ReaderAction.PageDown, down(KeyEvent.KEYCODE_DPAD_DOWN, paused = true))
        assertNull(down(KeyEvent.KEYCODE_DPAD_DOWN, repeat = 1, paused = true))
    }

    @Test
    fun `sentence keys and menu fire once per press`() {
        assertEquals(ReaderAction.SentenceBack, down(KeyEvent.KEYCODE_DPAD_LEFT))
        assertNull(down(KeyEvent.KEYCODE_DPAD_LEFT, repeat = 1))
        assertEquals(ReaderAction.SentenceForward, down(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(ReaderAction.OpenMenu, down(KeyEvent.KEYCODE_MENU))
    }

    @Test
    fun `number keys jump to tens of percent`() {
        assertEquals(ReaderAction.JumpToPercent(10), down(KeyEvent.KEYCODE_1))
        assertEquals(ReaderAction.JumpToPercent(90), down(KeyEvent.KEYCODE_9))
        assertEquals(ReaderAction.JumpToPercent(50), down(KeyEvent.KEYCODE_NUMPAD_5))
        assertNull(down(KeyEvent.KEYCODE_0))
    }

    @Test
    fun `handled keys`() {
        assertTrue(keys.handles(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(keys.handles(KeyEvent.KEYCODE_7))
        assertFalse(keys.handles(KeyEvent.KEYCODE_BACK))
        assertFalse(keys.handles(KeyEvent.KEYCODE_VOLUME_UP))
    }
}
