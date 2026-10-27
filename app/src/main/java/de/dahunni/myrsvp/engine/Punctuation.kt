package de.dahunni.myrsvp.engine

/** How long the reader rests after a word, beyond its normal duration. */
enum class Pause { None, Clause, Sentence, Paragraph }

/** Closing quotes and brackets that may follow sentence punctuation: `end."` or `(see above.)` */
private const val CLOSERS = "\"'”’»«›)]}"
private const val SENTENCE_ENDS = ".!?…"
private const val CLAUSE_ENDS = ",;:–—"

/** Lower-case, without the final dot. "no" stays here although "No." can end a sentence; see [isSentenceEnd]. */
private val ABBREVIATIONS = setOf(
    "mr", "mrs", "ms", "dr", "st", "prof", "jr", "sr", "vs", "e.g", "i.e", "etc", "no",
)

private fun String.trimClosers(): String = trimEnd { it in CLOSERS }

/**
 * True if [word] ends a sentence: `.` `!` `?` `…`, optionally followed by closing quotes/brackets.
 * Abbreviations (Mr. e.g. etc.) and initials (the "H." in "H. G. Wells") are not sentence ends,
 * unless a closing quote follows them (`“No.”`), which only happens at the end of a sentence.
 */
fun isSentenceEnd(word: String): Boolean {
    val core = word.trimClosers()
    if (core.isEmpty() || core.last() !in SENTENCE_ENDS) return false
    if (core.length != word.length || core.last() != '.' || core.endsWith("..")) return true
    val stem = core.dropLast(1).trimStart { !it.isLetterOrDigit() }
    if (stem.lowercase() in ABBREVIATIONS) return false
    if (stem.length == 1 && stem[0].isUpperCase()) return false
    return true
}

/** True if [word] ends a clause: `,` `;` `:` `–` `—` (closing quotes/brackets allowed after it). */
fun isClauseEnd(word: String): Boolean {
    val core = word.trimClosers()
    return core.isNotEmpty() && core.last() in CLAUSE_ENDS
}

fun pauseAfter(word: String, paragraphEnd: Boolean): Pause = when {
    paragraphEnd -> Pause.Paragraph
    isSentenceEnd(word) -> Pause.Sentence
    isClauseEnd(word) -> Pause.Clause
    else -> Pause.None
}
