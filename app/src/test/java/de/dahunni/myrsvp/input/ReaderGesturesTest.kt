package de.dahunni.myrsvp.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderGesturesTest {
    @Test
    fun `swipes follow the finger`() {
        assertEquals(Direction.Up, swipeDirection(5f, -120f, 60f))
        assertEquals(Direction.Down, swipeDirection(-10f, 90f, 60f))
        assertEquals(Direction.Left, swipeDirection(-100f, 20f, 60f))
        assertEquals(Direction.Right, swipeDirection(80f, 0f, 60f))
    }

    @Test
    fun `short and diagonal swipes do nothing`() {
        assertNull(swipeDirection(30f, 20f, 60f))
        assertNull(swipeDirection(100f, -90f, 60f))
    }

    @Test
    fun `gestures do what the d-pad does`() {
        for (paused in listOf(false, true)) {
            for (d in Direction.entries) {
                assertEquals(readerAction(d, paused), readerAction(ReaderGesture.Move(d), paused))
            }
        }
        assertEquals(ReaderAction.SpeedUp, readerAction(ReaderGesture.Move(Direction.Up), paused = false))
        assertEquals(ReaderAction.PageDown, readerAction(ReaderGesture.Move(Direction.Down), paused = true))
        assertEquals(ReaderAction.PlayPause, readerAction(ReaderGesture.Move(Direction.Center), paused = true))
        assertEquals(ReaderAction.OpenMenu, readerAction(ReaderGesture.LongPress, paused = false))
    }
}
