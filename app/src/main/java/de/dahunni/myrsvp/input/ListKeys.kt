package de.dahunni.myrsvp.input

/**
 * Keys that list screens handle themselves. ▲▼ move the focus and OK selects through the normal
 * Compose focus/click handling; BACK goes through the system back dispatcher.
 */
enum class ListKey { Left, Right, Menu }

fun listKey(keyCode: Int): ListKey? = when (keyCode) {
    in KeyCodes.LEFT -> ListKey.Left
    in KeyCodes.RIGHT -> ListKey.Right
    in KeyCodes.MENU -> ListKey.Menu
    else -> null
}

