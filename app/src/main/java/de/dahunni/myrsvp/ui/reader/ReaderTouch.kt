package de.dahunni.myrsvp.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.dahunni.myrsvp.input.Direction
import de.dahunni.myrsvp.input.ReaderGesture
import de.dahunni.myrsvp.input.swipeDirection

/** A swipe must travel this far to count (~ a fingertip). */
private val MinSwipe = 40.dp

/**
 * Touch on the reader, mirroring the D-pad (docs/SPEC.md §3): tap = OK, swipe = the arrow in the
 * finger's direction, long press = menu. The mapping to actions lives in input/ReaderGestures.kt.
 */
fun Modifier.readerTouch(onGesture: (ReaderGesture) -> Unit): Modifier = pointerInput(Unit) {
    val minSwipe = MinSwipe.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        val track = Track(down.id, down.position, viewConfiguration.touchSlop)
        // Until the finger lifts or the long-press time is up, whichever comes first.
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { follow(track) }
        val longPress = !track.released && !track.moved
        if (longPress) onGesture(ReaderGesture.LongPress)
        follow(track)
        when {
            longPress -> Unit
            !track.moved -> onGesture(ReaderGesture.Move(Direction.Center))
            else -> swipeDirection(track.delta.x, track.delta.y, minSwipe)?.let { onGesture(ReaderGesture.Move(it)) }
        }
    }
}

/** How far one pointer went, whether it left the touch slop, whether it lifted. */
private class Track(val id: PointerId, val start: Offset, val slop: Float) {
    var delta = Offset.Zero
    var moved = false
    var released = false
}

/** Follows [track]'s pointer until it lifts (or disappears). */
private suspend fun AwaitPointerEventScope.follow(track: Track) {
    while (!track.released) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == track.id }
        if (change == null) {
            track.released = true
            return
        }
        track.delta = change.position - track.start
        if (track.delta.getDistance() > track.slop) track.moved = true
        if (!change.pressed) track.released = true
        change.consume()
    }
}
