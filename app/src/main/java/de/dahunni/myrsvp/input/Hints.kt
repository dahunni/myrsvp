package de.dahunni.myrsvp.input

import android.view.KeyEvent

/** One key named in a key-hint ("▲", "OK", "↩"); tapping it does exactly what pressing that key does. */
sealed interface HintKey {
    val glyph: String

    /** Sent as a key press to the focused element, through the same path as a hardware key. */
    data class Press(override val glyph: String, val keyCode: Int) : HintKey

    /** BACK goes through the system back dispatcher, like the hardware key and the back gesture. */
    data object Back : HintKey {
        override val glyph = "↩"
    }
}

/** A key-hint in the hint bar: its keys and a label, e.g. "▲▼ Speed". */
data class Hint(val keys: List<HintKey>, val label: String)

/** The hints the screens use; glyphs and key codes in one place, next to [KeyCodes]. */
object Hints {
    fun upDown(label: String) = Hint(
        listOf(HintKey.Press("▲", KeyCodes.UP.first()), HintKey.Press("▼", KeyCodes.DOWN.first())),
        label,
    )

    fun leftRight(label: String) = Hint(
        listOf(HintKey.Press("◀", KeyCodes.LEFT.first()), HintKey.Press("▶", KeyCodes.RIGHT.first())),
        label,
    )

    fun ok(label: String) = Hint(listOf(HintKey.Press("OK", KeyCodes.OK.first())), label)

    fun menu(label: String) = Hint(listOf(HintKey.Press("☰", KeyEvent.KEYCODE_MENU)), label)

    fun back(label: String) = Hint(listOf(HintKey.Back), label)
}
