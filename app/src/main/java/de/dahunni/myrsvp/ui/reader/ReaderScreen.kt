package de.dahunni.myrsvp.ui.reader

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.node.Ref
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.engine.Player
import de.dahunni.myrsvp.engine.PlayerSettings
import de.dahunni.myrsvp.engine.PlayerState
import de.dahunni.myrsvp.engine.ReadingText
import de.dahunni.myrsvp.input.ReaderAction
import de.dahunni.myrsvp.input.ReaderKeyInterpreter
import de.dahunni.myrsvp.input.hasKeypad
import de.dahunni.myrsvp.data.Settings
import de.dahunni.myrsvp.ui.ReaderSession
import de.dahunni.myrsvp.ui.theme.HintBar
import de.dahunni.myrsvp.ui.theme.MaxContentWidth
import de.dahunni.myrsvp.ui.theme.RsvpTheme
import de.dahunni.myrsvp.ui.theme.durationText
import kotlinx.coroutines.delay

/** Keeps the paused text off the header and footer. */
private val PausedEdgeGap = 12.dp
/** Speed change: the header's wpm label pops into a pill, holds, then settles back. */
private const val SPEED_POP_IN_MS = 150
private const val SPEED_HOLD_MS = 800L
private const val SPEED_POP_OUT_MS = 300
private const val SPEED_POP_SCALE = 0.15f
private val SpeedPillPadding = DpSize(7.dp, 3.dp)

/**
 * Reader screen: one word at a time while playing, a page of text while paused (docs/DESIGN.md).
 * The playback state lives in [session], so it survives the menu and settings screens.
 */
@Composable
fun ReaderScreen(
    session: ReaderSession,
    title: String,
    settings: Settings,
    onOpenMenu: () -> Unit,
    onSpeedChange: (wpm: Int) -> Unit,
    onSaveProgress: () -> Unit,
) {
    val colors = RsvpTheme.colors
    val text = session.book.text
    val playerSettings = PlayerSettings(rampUp = settings.rampUp, pauseAtSentences = settings.pauseAtSentences)
    val player = remember(text, playerSettings) { Player(text, playerSettings) }
    var state by session::state
    val showGuides = settings.guideLines
    /** The piece of a split long word currently on screen, or null for the whole word. */
    var shownChunk by remember { mutableStateOf<String?>(null) }
    var speedChanges by remember { mutableIntStateOf(0) }
    val keys = remember { ReaderKeyInterpreter(ViewConfiguration.getLongPressTimeout().toLong()) }
    /** Set from composition once the paused area is measured; used by ▲▼ paging. */
    val pages = remember { Ref<PageLayout>() }
    val focusRequester = remember { FocusRequester() }
    val showHints = hasKeypad(LocalConfiguration.current)

    fun perform(action: ReaderAction) {
        state = when (action) {
            ReaderAction.SpeedUp -> player.faster(state).also { speedChanges++; onSpeedChange(it.wpm) }
            ReaderAction.SpeedDown -> player.slower(state).also { speedChanges++; onSpeedChange(it.wpm) }
            ReaderAction.PageUp -> pages.value?.let { player.jumpTo(state, it.previousPageStart(state.index)) } ?: state
            ReaderAction.PageDown -> pages.value?.page(state.index)?.nextPageStart?.let { player.jumpTo(state, it) } ?: state
            ReaderAction.PlayPause -> player.togglePlay(state)
            ReaderAction.SentenceBack -> player.back(state, SystemClock.uptimeMillis())
            ReaderAction.SentenceForward -> player.forward(state)
            is ReaderAction.JumpToPercent -> player.jumpToPercent(state, action.percent)
            ReaderAction.OpenMenu -> player.pauseNow(state).also { onOpenMenu() }
        }
    }

    BackHandler(enabled = state.playing) { state = player.pause(state) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { state = player.pauseNow(state) }

    // The speed is global: take it over from the settings when the reader (re)appears.
    LaunchedEffect(Unit) { state = state.copy(wpm = settings.wpm) }
    // Save the position whenever the reader stands still, and when it is left.
    LaunchedEffect(state.playing, state.index) { if (!state.playing) onSaveProgress() }
    DisposableEffect(Unit) {
        onDispose {
            state = player.pauseNow(state)
            onSaveProgress()
        }
    }

    val view = LocalView.current
    DisposableEffect(state.playing) {
        view.keepScreenOn = state.playing
        onDispose { view.keepScreenOn = false }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (!keys.handles(native.keyCode)) return@onPreviewKeyEvent false
                val down = event.type == KeyEventType.KeyDown
                keys.onKey(native.keyCode, down, native.repeatCount, native.eventTime - native.downTime, paused = !state.playing)
                    ?.let(::perform)
                true
            }
            .pointerInput(player) { detectTapGestures(onTap = { perform(ReaderAction.PlayPause) }) }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 18.dp, start = 16.dp, end = 16.dp, bottom = 14.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wordStyle = RsvpTheme.type.word
        val measurer = rememberTextMeasurer()
        val bandWidthPx = constraints.maxWidth.coerceAtMost(with(LocalDensity.current) { MaxContentWidth.roundToPx() })
        val fitter = remember(measurer, wordStyle, bandWidthPx) { WordFitter(measurer, wordStyle, bandWidthPx) }
        val currentFitter by rememberUpdatedState(fitter)

        // Playback: show each word (or its pieces) for its duration, on a drift-free clock.
        LaunchedEffect(state.playing, state.seek) {
            if (!state.playing) return@LaunchedEffect
            val seek = state.seek
            var deadline = SystemClock.uptimeMillis()
            while (true) {
                val word = text.words[state.index].text
                val pieces = currentFitter.split(word)
                val total = player.durationMs(state)
                val letters = pieces.sumOf { it.length }.coerceAtLeast(1)
                for (piece in pieces) {
                    shownChunk = if (pieces.size > 1) piece else null
                    deadline += total * piece.length / letters
                    delay(deadline - SystemClock.uptimeMillis())
                }
                if (state.seek != seek) break
                state = player.advance(state)
                if (!state.playing) break
            }
            shownChunk = null
        }

        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) {
            Header(text, title, state, speedChanges)
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = PausedEdgeGap),
                contentAlignment = Alignment.Center,
            ) {
                val type = RsvpTheme.type
                val gapPx = with(LocalDensity.current) { PausedSentenceGap.roundToPx() }
                val pageLayout = remember(text, measurer, type, constraints.maxWidth, constraints.maxHeight, gapPx) {
                    PageLayout(text, measurer, type.pausedSentence, type.pausedNext, constraints.maxWidth, constraints.maxHeight, gapPx)
                }
                SideEffect { pages.value = pageLayout }
                if (state.playing) {
                    WordBand(
                        word = shownChunk ?: text.words.getOrNull(state.index)?.text.orEmpty(),
                        style = wordStyle,
                        showGuides = showGuides,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (text.size > 0) {
                    val page = remember(pageLayout, state.index) { pageLayout.page(state.index) }
                    PausedText(page, Modifier.fillMaxSize())
                }
            }
            Footer(text, state, showHints)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun Header(text: ReadingText, title: String, state: PlayerState, speedChanges: Int) {
    val colors = RsvpTheme.colors
    val type = RsvpTheme.type
    val chapter = text.chapters.getOrNull(text.chapterOf(state.index))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = type.bookTitle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (chapter != null) {
                Text(chapter.title, style = type.meta, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (state.playing) {
            SpeedLabel(state.wpm, speedChanges)
        } else {
            Text(
                stringResource(R.string.reader_paused),
                style = type.meta.copy(fontWeight = FontWeight.SemiBold),
                color = colors.background,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.text)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun Footer(text: ReadingText, state: PlayerState, showHints: Boolean) {
    val colors = RsvpTheme.colors
    val type = RsvpTheme.type
    val progress = text.progress(state.index)
    val percent = (progress * 100).toInt()
    val chapterIndex = text.chapterOf(state.index)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ProgressBar(progress)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val left = if (chapterIndex >= 0) {
                stringResource(R.string.reader_percent_chapter, percent, chapterIndex + 1, text.chapters.size)
            } else {
                stringResource(R.string.reader_percent, percent)
            }
            Text(left, style = type.meta, color = colors.textMuted)
            val right = if (state.playing) stringResource(R.string.reader_time_left, durationText(text.remainingMs(state.index, state.wpm)))
            else stringResource(R.string.reader_wpm, state.wpm)
            Text(right, style = type.meta, color = colors.textMuted)
        }
        if (showHints) {
            if (state.playing) {
                HintBar(listOf(stringResource(R.string.hint_speed), stringResource(R.string.hint_pause), stringResource(R.string.hint_sentence)))
            } else {
                HintBar(listOf(stringResource(R.string.hint_resume), stringResource(R.string.hint_page), stringResource(R.string.hint_menu)))
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val colors = RsvpTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(colors.guideLine)
            .drawBehind {
                val w = size.width * progress.coerceIn(0f, 1f)
                if (w > 0f) {
                    drawRoundRect(
                        colors.textMuted,
                        size = size.copy(width = w),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                }
            },
    )
}

/**
 * The header's wpm label. When the speed changes it smoothly pops into a pill (slightly larger,
 * full text color), holds for a moment and settles back, so the word itself is never covered.
 */
@Composable
private fun SpeedLabel(wpm: Int, changes: Int) {
    val colors = RsvpTheme.colors
    val pop = remember { Animatable(0f) }
    LaunchedEffect(changes) {
        if (changes == 0) return@LaunchedEffect
        pop.animateTo(1f, tween(SPEED_POP_IN_MS, easing = FastOutSlowInEasing))
        delay(SPEED_HOLD_MS)
        pop.animateTo(0f, tween(SPEED_POP_OUT_MS, easing = FastOutSlowInEasing))
    }
    val p = pop.value
    Text(
        stringResource(R.string.reader_wpm, wpm),
        style = RsvpTheme.type.wpm,
        color = lerp(colors.textMuted, colors.text, p),
        modifier = Modifier
            .graphicsLayer {
                val scale = 1f + SPEED_POP_SCALE * p
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            // The pill is drawn around the label, outside its bounds, so the header layout never shifts.
            .drawBehind {
                if (p > 0f) {
                    val pad = Offset(SpeedPillPadding.width.toPx(), SpeedPillPadding.height.toPx())
                    val pill = Size(size.width + 2 * pad.x, size.height + 2 * pad.y)
                    drawRoundRect(
                        colors.rowFocused.copy(alpha = p),
                        topLeft = -pad,
                        size = pill,
                        cornerRadius = CornerRadius(pill.height / 2),
                    )
                }
            },
    )
}
