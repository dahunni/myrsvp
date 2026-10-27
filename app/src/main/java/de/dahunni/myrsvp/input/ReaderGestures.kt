package de.dahunni.myrsvp.input

import kotlin.math.abs

/** What a finger did on the reader. Swipes and taps become [Direction]s, so they do what the D-pad does. */
sealed interface ReaderGesture {
    data class Move(val direction: Direction) : ReaderGesture
    data object LongPress : ReaderGesture
}

/**
 * The direction of a swipe by [dx]/[dy] (pixels, y pointing down): the finger's direction along
 * the dominant axis, so a swipe up is ▲ and a swipe to the left is ◀. Null if it is shorter than
 * [minDistance] or neither axis clearly dominates.
 */
fun swipeDirection(dx: Float, dy: Float, minDistance: Float): Direction? {
    val ax = abs(dx)
    val ay = abs(dy)
    return when {
        maxOf(ax, ay) < minDistance -> null
        ax >= ay * DOMINANCE -> if (dx < 0) Direction.Left else Direction.Right
        ay >= ax * DOMINANCE -> if (dy < 0) Direction.Up else Direction.Down
        else -> null
    }
}

/** A diagonal swipe has to lean this much towards one axis to count. */
private const val DOMINANCE = 1.5f

/** Long press opens the menu, like holding OK; everything else maps through [readerAction]. */
fun readerAction(gesture: ReaderGesture, paused: Boolean): ReaderAction = when (gesture) {
    is ReaderGesture.Move -> readerAction(gesture.direction, paused)
    ReaderGesture.LongPress -> ReaderAction.OpenMenu
}
