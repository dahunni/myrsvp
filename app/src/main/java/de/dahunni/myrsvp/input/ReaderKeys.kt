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

/** The five D-pad directions; touch gestures map onto the same ones (see ReaderGestures.kt). */
enum class Direction { Up, Down, Left, Right, Center }

/**
 * What a direction does on the reader, for keys and gestures alike: ▲▼ change the speed while
 * playing and turn pages while paused, ◀▶ move by sentence, the center plays or pauses.
 */
fun readerAction(direction: Direction, paused: Boolean): ReaderAction = when (direction) {
    Direction.Up -> if (paused) ReaderAction.PageUp else ReaderAction.SpeedUp
    Direction.Down -> if (paused) ReaderAction.PageDown else ReaderAction.SpeedDown
    Direction.Left -> ReaderAction.SentenceBack
    Direction.Right -> ReaderAction.SentenceForward
    Direction.Center -> ReaderAction.PlayPause
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
    /** Reader: sentence back / forward. Lists: change a setting's value. */
    val LEFT = setOf(KeyEvent.KEYCODE_DPAD_LEFT)
    val RIGHT = setOf(KeyEvent.KEYCODE_DPAD_RIGHT)
    val MENU = setOf(KeyEvent.KEYCODE_MENU)

    /** Key code → digit 1–9 (top-row and numpad digits). */
    val DIGITS: Map<Int, Int> = (1..9).associateBy { KeyEvent.KEYCODE_0 + it } +
        (1..9).associateBy { KeyEvent.KEYCODE_NUMPAD_0 + it }
}

/**
 * Turns raw key events into [ReaderAction]s. OK acts on release so that holding it can open
 * the menu instead; speed keys repeat while held, the other keys (paging included) don't.
 */
class ReaderKeyInterpreter(longPressMs: Long) {
    private val ok = OkKey(longPressMs)

    fun handles(keyCode: Int): Boolean = keyCode in KeyCodes.UP || keyCode in KeyCodes.DOWN ||
        keyCode in KeyCodes.OK || keyCode in KeyCodes.LEFT || keyCode in KeyCodes.RIGHT ||
        keyCode in KeyCodes.MENU || keyCode in KeyCodes.DIGITS

    /**
     * @param down true for ACTION_DOWN (including auto-repeats), false for ACTION_UP
     * @param heldMs eventTime − downTime of the event
     * @param paused the reader shows the paused view (▲▼ page instead of changing speed)
     */
    fun onKey(keyCode: Int, down: Boolean, repeatCount: Int, heldMs: Long, paused: Boolean): ReaderAction? {
        if (keyCode in KeyCodes.OK) {
            return when (ok.onKey(down, repeatCount, heldMs)) {
                OkPress.Short -> ReaderAction.PlayPause
                OkPress.Long -> ReaderAction.OpenMenu
                null -> null
            }
        }
        if (!down) return null
        val direction = when (keyCode) {
            in KeyCodes.UP -> Direction.Up
            in KeyCodes.DOWN -> Direction.Down
            in KeyCodes.LEFT -> Direction.Left
            in KeyCodes.RIGHT -> Direction.Right
            else -> null
        }
        // Only the speed repeats while a key is held; a held key never turns more than one page.
        val repeats = direction == Direction.Up || direction == Direction.Down
        if (repeatCount > 0 && !(repeats && !paused)) return null
        if (direction != null) return readerAction(direction, paused)
        return when (keyCode) {
            in KeyCodes.MENU -> ReaderAction.OpenMenu
            in KeyCodes.DIGITS -> ReaderAction.JumpToPercent(KeyCodes.DIGITS.getValue(keyCode) * 10)
            else -> null
        }
    }

}

enum class OkPress { Short, Long }

/**
 * OK (center/enter), the same on every screen: a short press acts on release, holding it acts
 * once as soon as the long-press time is reached (reader: menu; library: the book's menu).
 */
class OkKey(private val longPressMs: Long) {
    private var longPressFired = false

    /** @see ReaderKeyInterpreter.onKey */
    fun onKey(down: Boolean, repeatCount: Int, heldMs: Long): OkPress? {
        if (down) {
            if (repeatCount == 0) longPressFired = false
            if (!longPressFired && repeatCount > 0 && heldMs >= longPressMs) {
                longPressFired = true
                return OkPress.Long
            }
            return null
        }
        val wasLongPress = longPressFired
        longPressFired = false
        return if (wasLongPress) null else OkPress.Short
    }
}

/**
 * True on devices with a D-pad or a phone keypad (like the Qin F22 Pro), where the key-hint bar
 * is shown.
 */
fun hasKeypad(config: Configuration): Boolean =
    config.navigation == Configuration.NAVIGATION_DPAD || config.keyboard == Configuration.KEYBOARD_12KEY
