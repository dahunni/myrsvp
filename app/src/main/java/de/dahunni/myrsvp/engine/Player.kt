package de.dahunni.myrsvp.engine

/**
 * Reader playback state. [index] is the word on screen while playing, and the word playback
 * resumes with while paused.
 */
data class PlayerState(
    val index: Int = 0,
    val playing: Boolean = false,
    /** Pause was requested with "Pause at sentences" on: keep playing until the sentence ends. */
    val pauseAtSentenceEnd: Boolean = false,
    val wpm: Int = WordTiming.DEFAULT_WPM,
    /** Words shown since the last start/resume, for the ramp-up. */
    val wordsSinceStart: Int = 0,
    /** Changes on every jump, so a running word timer knows to start over. */
    val seek: Int = 0,
    /** Time of the last "back" press, for the double-press rule. */
    val lastBackAtMs: Long = Long.MIN_VALUE,
)

data class PlayerSettings(
    val rampUp: Boolean = true,
    val pauseAtSentences: Boolean = true,
)

/** Pure state transitions of the reader (docs/SPEC.md §2 and §3). */
class Player(
    val text: ReadingText,
    val settings: PlayerSettings = PlayerSettings(),
) {
    fun durationMs(state: PlayerState): Long {
        val word = text.words[state.index]
        return WordTiming.durationMs(WordTiming.weight(word.text, word.pause), state.wpm, state.wordsSinceStart, settings.rampUp)
    }

    fun play(state: PlayerState): PlayerState =
        if (text.size == 0) state
        else state.copy(playing = true, pauseAtSentenceEnd = false, wordsSinceStart = 0, seek = state.seek + 1)

    /**
     * Pause request. With "Pause at sentences" playback goes on until the current sentence has been
     * shown; a second request pauses right away.
     */
    fun pause(state: PlayerState): PlayerState = when {
        !state.playing -> state
        settings.pauseAtSentences && !state.pauseAtSentenceEnd -> state.copy(pauseAtSentenceEnd = true)
        else -> pauseNow(state)
    }

    /** Pause without waiting for the sentence end (e.g. when the app goes to the background). */
    fun pauseNow(state: PlayerState): PlayerState = state.copy(playing = false, pauseAtSentenceEnd = false)

    fun togglePlay(state: PlayerState): PlayerState = if (state.playing) pause(state) else play(state)

    /** The current word's time is up: move on, stopping at a requested sentence end or the end of the text. */
    fun advance(state: PlayerState): PlayerState {
        if (!state.playing) return state
        if (state.index >= text.size - 1) return state.copy(playing = false, pauseAtSentenceEnd = false)
        val next = state.copy(index = state.index + 1, wordsSinceStart = state.wordsSinceStart + 1)
        return if (state.pauseAtSentenceEnd && text.words[state.index].endsSentence) {
            next.copy(playing = false, pauseAtSentenceEnd = false)
        } else {
            next
        }
    }

    fun faster(state: PlayerState) = state.copy(wpm = WordTiming.clampWpm(state.wpm + WordTiming.WPM_STEP))

    fun slower(state: PlayerState) = state.copy(wpm = WordTiming.clampWpm(state.wpm - WordTiming.WPM_STEP))

    /**
     * Back once → start of the current sentence. Pressed again within [BACK_REPEAT_MS], or when already
     * at the start of the sentence → start of the previous sentence.
     */
    fun back(state: PlayerState, nowMs: Long): PlayerState {
        if (text.size == 0) return state
        val repeated = state.lastBackAtMs != Long.MIN_VALUE && nowMs - state.lastBackAtMs <= BACK_REPEAT_MS
        val start = text.sentenceStart(state.index)
        val target = if (repeated || state.index == start) text.previousSentenceStart(state.index) else start
        return seekTo(state, target).copy(lastBackAtMs = nowMs)
    }

    fun forward(state: PlayerState): PlayerState {
        val target = text.nextSentenceStart(state.index) ?: return state
        return seekTo(state, target)
    }

    /** Number keys 1–9, only while paused: jump to 10 %…90 %. */
    fun jumpToPercent(state: PlayerState, percent: Int): PlayerState =
        if (text.size == 0) state else jumpTo(state, text.sentenceStartAtPercent(percent))

    /** Only while paused (e.g. paging): jump to the start of the sentence containing [wordIndex]. */
    fun jumpTo(state: PlayerState, wordIndex: Int): PlayerState =
        if (state.playing || text.size == 0) state
        else seekTo(state, text.sentenceStart(wordIndex.coerceIn(0, text.size - 1)))

    /** A jump while a pause is pending pauses right at the jump target. */
    private fun seekTo(state: PlayerState, index: Int) = state.copy(
        index = index,
        playing = state.playing && !state.pauseAtSentenceEnd,
        pauseAtSentenceEnd = false,
        seek = state.seek + 1,
        lastBackAtMs = Long.MIN_VALUE,
    )

    companion object {
        const val BACK_REPEAT_MS = 1_500L
    }
}
