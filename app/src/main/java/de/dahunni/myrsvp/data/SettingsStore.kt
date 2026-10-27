package de.dahunni.myrsvp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.dahunni.myrsvp.engine.WordTiming
import de.dahunni.myrsvp.ui.theme.FocusColor
import de.dahunni.myrsvp.ui.theme.WordSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything on the Settings screen, plus the speed (remembered globally) and the last open book. */
data class Settings(
    val wpm: Int = WordTiming.DEFAULT_WPM,
    val darkTheme: Boolean = true,
    val focusColor: FocusColor = FocusColor.Red,
    val wordSize: WordSize = WordSize.L,
    val pauseAtSentences: Boolean = true,
    val rampUp: Boolean = true,
    val guideLines: Boolean = true,
    val lastBookId: String? = null,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val new = transform(p.toSettings())
            p[WPM] = WordTiming.clampWpm(new.wpm)
            p[DARK] = new.darkTheme
            p[FOCUS] = new.focusColor.name
            p[WORD_SIZE] = new.wordSize.name
            p[PAUSE_AT_SENTENCES] = new.pauseAtSentences
            p[RAMP_UP] = new.rampUp
            p[GUIDE_LINES] = new.guideLines
            if (new.lastBookId != null) p[LAST_BOOK] = new.lastBookId else p.remove(LAST_BOOK)
        }
    }

    private fun Preferences.toSettings() = Settings(
        wpm = WordTiming.clampWpm(this[WPM] ?: WordTiming.DEFAULT_WPM),
        darkTheme = this[DARK] ?: true,
        focusColor = this[FOCUS]?.let { name -> FocusColor.entries.firstOrNull { it.name == name } } ?: FocusColor.Red,
        wordSize = this[WORD_SIZE]?.let { name -> WordSize.entries.firstOrNull { it.name == name } } ?: WordSize.L,
        pauseAtSentences = this[PAUSE_AT_SENTENCES] ?: true,
        rampUp = this[RAMP_UP] ?: true,
        guideLines = this[GUIDE_LINES] ?: true,
        lastBookId = this[LAST_BOOK],
    )

    private companion object {
        val WPM = intPreferencesKey("wpm")
        val DARK = booleanPreferencesKey("dark_theme")
        val FOCUS = stringPreferencesKey("focus_color")
        val WORD_SIZE = stringPreferencesKey("word_size")
        val PAUSE_AT_SENTENCES = booleanPreferencesKey("pause_at_sentences")
        val RAMP_UP = booleanPreferencesKey("ramp_up")
        val GUIDE_LINES = booleanPreferencesKey("guide_lines")
        val LAST_BOOK = stringPreferencesKey("last_book")
    }
}
