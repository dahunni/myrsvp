package de.dahunni.myrsvp.engine

/** Word durations and the speed range from docs/SPEC.md §2. */
object WordTiming {
    const val MIN_WPM = 100
    const val MAX_WPM = 900
    const val WPM_STEP = 25
    const val DEFAULT_WPM = 300

    /** Number of words after start/resume that are shown longer ([rampMultiplier]). */
    const val RAMP_WORDS = 8
    private const val RAMP_START = 1.6

    private const val LONG_WORD_LETTERS = 6
    private const val PER_EXTRA_LETTER = 0.08

    fun clampWpm(wpm: Int): Int = wpm.coerceIn(MIN_WPM, MAX_WPM)

    /** Milliseconds per plain word: 60 000 / wpm (300 wpm → 200 ms). */
    fun baseMs(wpm: Int): Double = 60_000.0 / wpm

    /**
     * Duration of a word in units of [baseMs]: 1.0 for a plain word, +8 % per letter beyond 6,
     * plus 0.5 / 1.0 / 1.5 for a clause / sentence / paragraph end.
     */
    fun weight(word: String, pause: Pause): Double {
        val extraLetters = (letterCount(word) - LONG_WORD_LETTERS).coerceAtLeast(0)
        val bonus = when (pause) {
            Pause.None -> 0.0
            Pause.Clause -> 0.5
            Pause.Sentence -> 1.0
            Pause.Paragraph -> 1.5
        }
        return 1.0 + PER_EXTRA_LETTER * extraLetters + bonus
    }

    /**
     * Ramp-up after start/resume: word 0 is shown 1.6× as long, falling linearly in [RAMP_WORDS]
     * steps; from word 8 on it is 1.0×.
     */
    fun rampMultiplier(wordsSinceStart: Int): Double {
        if (wordsSinceStart >= RAMP_WORDS) return 1.0
        val t = wordsSinceStart.coerceAtLeast(0).toDouble() / RAMP_WORDS
        return RAMP_START + (1.0 - RAMP_START) * t
    }

    fun durationMs(weight: Double, wpm: Int, wordsSinceStart: Int, rampUp: Boolean): Long {
        val ramp = if (rampUp) rampMultiplier(wordsSinceStart) else 1.0
        return Math.round(weight * baseMs(wpm) * ramp)
    }
}
