package de.dahunni.myrsvp.input

import android.content.res.Configuration
import android.view.KeyEvent

/** What a key press means on the reader screen (docs/SPEC.md §3). */
sealed interface ReaderAction {
    data object SpeedUp : ReaderAction
    data object SpeedDown : ReaderAction
    /** Paused view: show the previous / next page of text. */
    data object PageUp : ReaderAction
    data object PageDown : ReaderAction
    data object PlayPause : ReaderAction
    data object SentenceBack : ReaderAction
    data object SentenceForward : ReaderAction
    data object OpenMenu : ReaderAction
    /** Number keys 1–9 → 10 %…90 %. */
    data class JumpToPercent(val percent: Int) : ReaderAction
}

/**
 * Key codes in one place, so they are easy to adjust once the Qin F22 Pro's real codes are known.
 * BACK is not here: it goes through the system back dispatcher (Compose BackHandler), which also
 * covers the back gesture on touch phones.
 */
object KeyCodes {
    /** Speed while playing, page while paused. */
    val UP = setOf(KeyEvent.KEYCODE_DPAD_UP)
    val DOWN = setOf(KeyEvent.KEYCODE_DPAD_DOWN)
    val OK = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
    val SENTENCE_BACK = setOf(KeyEvent.KEYCODE_DPAD_LEFT)
    val SENTENCE_FORWARD = setOf(KeyEvent.KEYCODE_DPAD_RIGHT)
    val MENU = setOf(KeyEvent.KEYCODE_MENU)

    /** Key code → digit 1–9 (top-row and numpad digits). */
    val DIGITS: Map<Int, Int> = (1..9).associateBy { KeyEvent.KEYCODE_0 + it } +
        (1..9).associateBy { KeyEvent.KEYCODE_NUMPAD_0 + it }
}

/**
 * Turns raw key events into [ReaderAction]s. OK acts on release so that holding it can open
 * the menu instead; speed keys repeat while held, the other keys (paging included) don't.
 */
class ReaderKeyInterpreter(private val longPressMs: Long) {
    private var okLongPressFired = false

    fun handles(keyCode: Int): Boolean = keyCode in KeyCodes.UP || keyCode in KeyCodes.DOWN ||
        keyCode in KeyCodes.OK || keyCode in KeyCodes.SENTENCE_BACK || keyCode in KeyCodes.SENTENCE_FORWARD ||
        keyCode in KeyCodes.MENU || keyCode in KeyCodes.DIGITS

    /**
     * @param down true for ACTION_DOWN (including auto-repeats), false for ACTION_UP
     * @param heldMs eventTime − downTime of the event
     * @param paused the reader shows the paused view (▲▼ page instead of changing speed)
     */
    fun onKey(keyCode: Int, down: Boolean, repeatCount: Int, heldMs: Long, paused: Boolean): ReaderAction? {
        if (keyCode in KeyCodes.OK) return onOk(down, repeatCount, heldMs)
        if (!down) return null
        if (!paused && keyCode in KeyCodes.UP) return ReaderAction.SpeedUp
        if (!paused && keyCode in KeyCodes.DOWN) return ReaderAction.SpeedDown
        if (repeatCount > 0) return null
        return when (keyCode) {
            in KeyCodes.UP -> ReaderAction.PageUp
            in KeyCodes.DOWN -> ReaderAction.PageDown
            in KeyCodes.SENTENCE_BACK -> ReaderAction.SentenceBack
            in KeyCodes.SENTENCE_FORWARD -> ReaderAction.SentenceForward
            in KeyCodes.MENU -> ReaderAction.OpenMenu
            in KeyCodes.DIGITS -> ReaderAction.JumpToPercent(KeyCodes.DIGITS.getValue(keyCode) * 10)
            else -> null
        }
    }

    private fun onOk(down: Boolean, repeatCount: Int, heldMs: Long): ReaderAction? {
        if (down) {
            if (repeatCount == 0) okLongPressFired = false
            if (!okLongPressFired && repeatCount > 0 && heldMs >= longPressMs) {
                okLongPressFired = true
                return ReaderAction.OpenMenu
            }
            return null
        }
        val wasLongPress = okLongPressFired
        okLongPressFired = false
        return if (wasLongPress) null else ReaderAction.PlayPause
    }
}

/**
 * True on devices with a D-pad or a phone keypad (like the Qin F22 Pro), where the key-hint bar
 * is shown.
 */
fun hasKeypad(config: Configuration): Boolean =
    config.navigation == Configuration.NAVIGATION_DPAD || config.keyboard == Configuration.KEYBOARD_12KEY
