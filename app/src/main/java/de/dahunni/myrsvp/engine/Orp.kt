package de.dahunni.myrsvp.engine

/** Counts letters and digits only; surrounding punctuation doesn't make a word "longer". */
fun letterCount(word: String): Int = word.count { it.isLetterOrDigit() }

/**
 * Index (into [word]) of the focus letter, the ORP (docs/SPEC.md §2).
 * The position is counted among letters only, so leading quotes or brackets are skipped.
 * Words without any letter (e.g. "—") focus on their first character.
 */
fun orpIndex(word: String): Int {
    val letters = letterCount(word)
    if (letters == 0) return 0
    val nth = when {
        letters == 1 -> 0
        letters <= 5 -> 1
        letters <= 9 -> 2
        letters <= 13 -> 3
        else -> 4
    }
    var seen = 0
    for (i in word.indices) {
        if (word[i].isLetterOrDigit()) {
            if (seen == nth) return i
            seen++
        }
    }
    return 0
}

/** A word cut at its focus letter: [left] + [focus] + [right] == the original word. */
data class OrpParts(val left: String, val focus: String, val right: String)

fun splitAtOrp(word: String): OrpParts {
    if (word.isEmpty()) return OrpParts("", "", "")
    val i = orpIndex(word)
    return OrpParts(word.substring(0, i), word.substring(i, i + 1), word.substring(i + 1))
}
