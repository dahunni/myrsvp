package de.dahunni.myrsvp.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.dahunni.myrsvp.input.ListKey
import de.dahunni.myrsvp.input.listKey

val MaxContentWidth = 480.dp
private val RowShape = RoundedCornerShape(7.dp)
private val FocusOutline = 1.5.dp
private const val FOCUS_FADE_MS = 150

/** Padding of list rows: Menu and Settings (docs/DESIGN.md "Lists"); the library uses 8 dp vertically. */
val RowPadding = PaddingValues(horizontal = 9.dp, vertical = 10.dp)
val RowGap = 3.dp
/** Values never take more than this, so a row's label always keeps most of the width. */
private val MaxValueWidth = 150.dp

/**
 * Frame of every list screen: title (with an optional line above it and a value to its right),
 * the content, and the key-hint bar at the bottom.
 */
@Composable
fun ListScreen(
    title: String,
    hints: List<String>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    showHints: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = RsvpTheme.colors
    val type = RsvpTheme.type
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 18.dp, start = 12.dp, end = 12.dp, bottom = 14.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) {
            Column(
                Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (subtitle != null) {
                    Text(subtitle, style = type.meta, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(title, style = type.screenTitle, color = colors.text, modifier = Modifier.weight(1f))
                    if (trailing != null) Text(trailing, style = type.meta, color = colors.textMuted)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            if (showHints) HintBar(hints, Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** Key hints spread evenly under a 0.5 dp divider (docs/DESIGN.md). */
@Composable
fun HintBar(hints: List<String>, modifier: Modifier = Modifier) {
    val colors = RsvpTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind { drawLine(colors.divider, Offset(0f, 0f), Offset(size.width, 0f), 0.5.dp.toPx()) }
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        hints.forEach { Text(it, style = RsvpTheme.type.hint, color = colors.textMuted) }
    }
}

/**
 * A focusable list row. Focused: rowFocused background + accent outline, the only focus
 * indicator (docs/DESIGN.md). OK/tap call [onClick]; ◀▶ and ☰ go to [onKey].
 * Without [onClick] the row is still focusable, so read-only screens scroll with the D-pad.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onKey: ((ListKey) -> Boolean)? = null,
    /** Take the focus when the row first appears (the row a screen starts on). */
    autoFocus: Boolean = false,
    onFocused: () -> Unit = {},
    padding: PaddingValues = RowPadding,
    dimmed: Boolean = false,
    background: Modifier = Modifier,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val colors = RsvpTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg by animateColorAsState(if (focused) colors.rowFocused else Color.Transparent, tween(FOCUS_FADE_MS), label = "row")
    val requester = remember { FocusRequester() }
    if (autoFocus) {
        // Requested from the row itself: in a lazy list the row only exists once it is laid out.
        LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    }
    val clickModifier = if (onClick != null) {
        Modifier.combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
    } else {
        Modifier.focusable(interactionSource = interaction)
    }
    Box(
        modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusRequester(requester)
            // Clickables aren't focusable in touch mode by default; the D-pad needs a focused row from the start.
            .focusProperties { canFocus = true }
            .onPreviewKeyEvent { event ->
                val key = listKey(event.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false
                val handler = onKey ?: return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) handler(key) else true
            }
            .clip(RowShape)
            .then(if (focused) Modifier else background)
            .background(bg)
            .then(if (focused) Modifier.border(FocusOutline, colors.accent, RowShape) else Modifier)
            .then(clickModifier)
            .padding(padding),
    ) {
        Box(Modifier.alpha(if (dimmed) 0.5f else 1f)) { content(focused) }
    }
}

/** Label left, value right: Menu, Settings and Book info rows. */
@Composable
fun LabelValue(label: String, value: String?, focused: Boolean, valueColor: Color? = null) {
    val colors = RsvpTheme.colors
    val type = RsvpTheme.type
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = type.rowTitle, color = colors.text, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = type.rowValue,
                color = valueColor ?: if (focused) colors.text else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = MaxValueWidth),
            )
        }
    }
}

/** Gives [requester] the focus once the screen is laid out (screens without a lazy list). */
@Composable
fun InitialFocus(requester: FocusRequester) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

/**
 * The focused row of a screen with fixed rows, kept while the screen is on the back stack:
 * returning from a sub-screen focuses the row it was opened from.
 */
@Composable
fun rememberFocusedRow(initial: Int = 0): MutableIntState = rememberSaveable { mutableIntStateOf(initial) }
