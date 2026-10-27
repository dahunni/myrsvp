package de.dahunni.myrsvp.engine

/**
 * Splits words that don't fit the word area (docs/SPEC.md §2 "Long words").
 * [fits] is supplied by the UI, which knows the font and the available width.
 */
object WordSplitter {
    private const val HYPHEN = '-'

    fun split(word: String, fits: (String) -> Boolean): List<String> {
        if (fits(word)) return listOf(word)
        val hyphenParts = splitAtHyphens(word)
        if (hyphenParts.size > 1) {
            return mergeWhileFitting(hyphenParts, fits).flatMap { if (fits(it)) listOf(it) else splitEvenly(it, fits) }
        }
        return splitEvenly(word, fits)
    }

    /** "mother-in-law" → "mother-", "in-", "law"; the hyphens stay on the left part. */
    private fun splitAtHyphens(word: String): List<String> {
        val parts = ArrayList<String>()
        var start = 0
        for (i in 1 until word.length - 1) {
            if (word[i] == HYPHEN) {
                parts += word.substring(start, i + 1)
                start = i + 1
            }
        }
        parts += word.substring(start)
        return parts
    }

    private fun mergeWhileFitting(parts: List<String>, fits: (String) -> Boolean): List<String> {
        val merged = ArrayList<String>()
        for (part in parts) {
            val joined = merged.lastOrNull()?.plus(part)
            if (joined != null && fits(joined)) merged[merged.size - 1] = joined else merged += part
        }
        return merged
    }

    /**
     * Cuts into the smallest number of roughly equal chunks that all fit, each but the last ending in "-".
     * Cuts are moved by up to two letters towards a syllable boundary when the chunks still fit.
     * Gives up at single letters, which are shown even if they still overflow.
     */
    private fun splitEvenly(word: String, fits: (String) -> Boolean): List<String> {
        for (count in 2..word.length) {
            val even = evenCuts(word.length, count)
            val bySyllable = chunks(word, syllableCuts(word, even))
            if (bySyllable.all(fits)) return bySyllable
            val chunks = chunks(word, even)
            if (chunks.all(fits)) return chunks
        }
        return chunks(word, evenCuts(word.length, word.length))
    }

    /** Positions where chunks end, the last one being [length]. */
    private fun evenCuts(length: Int, count: Int): IntArray =
        IntArray(count) { k -> (length.toLong() * (k + 1) / count).toInt() }

    private fun syllableCuts(word: String, even: IntArray): IntArray {
        val cuts = even.copyOf()
        for (k in 0 until cuts.size - 1) {
            val low = if (k == 0) 1 else cuts[k - 1] + 1
            val high = cuts[k + 1] - 1
            val best = (0..MAX_SHIFT).asSequence()
                .flatMap { d -> sequenceOf(cuts[k] - d, cuts[k] + d) }
                .firstOrNull { it in low..high && isSyllableBoundary(word, it) }
            if (best != null) cuts[k] = best
        }
        return cuts
    }

    /** Simple rules that work for most European languages: "fa|ther" (V|CV) and "ac|knowledge" (VC|CV). */
    private fun isSyllableBoundary(word: String, cut: Int): Boolean {
        if (cut < 2 || cut > word.length - 2) return false
        val before = word[cut - 1]
        val after = word[cut]
        val afterNext = word[cut + 1]
        return when {
            !before.isLetter() || !after.isLetter() || !isVowel(afterNext) -> false
            isVowel(before) -> !isVowel(after)
            else -> !isVowel(after) && isVowel(word[cut - 2])
        }
    }

    private fun isVowel(c: Char) = c.lowercaseChar() in VOWELS

    private fun chunks(word: String, cuts: IntArray): List<String> {
        var start = 0
        return cuts.mapIndexed { k, end ->
            val chunk = word.substring(start, end)
            start = end
            if (k < cuts.size - 1 && chunk.last() != HYPHEN) "$chunk$HYPHEN" else chunk
        }
    }

    private const val MAX_SHIFT = 2
    private const val VOWELS = "aeiouyäöüàáâãåæèéêëìíîïòóôõøùúûý"
}
