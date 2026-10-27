package de.dahunni.myrsvp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Reading progress of one book (docs/SPEC.md §4). */
data class Progress(
    /** UTF-8 byte offset of the current sentence start in the book body. */
    val byteOffset: Int = 0,
    val lastOpened: Long = 0,
    val finished: Boolean = false,
)

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

/** Progress per book id, stored as "<id>.offset", "<id>.opened", "<id>.done". */
class ProgressStore(context: Context) {
    private val store = context.applicationContext.progressDataStore

    val all: Flow<Map<String, Progress>> = store.data.map { prefs ->
        val ids = prefs.asMap().keys.map { it.name.substringBeforeLast('.') }.toSet()
        ids.associateWith { id ->
            Progress(
                byteOffset = prefs[offsetKey(id)] ?: 0,
                lastOpened = prefs[openedKey(id)] ?: 0,
                finished = prefs[doneKey(id)] ?: false,
            )
        }
    }

    suspend fun save(id: String, progress: Progress) {
        store.edit {
            it[offsetKey(id)] = progress.byteOffset
            it[openedKey(id)] = progress.lastOpened
            it[doneKey(id)] = progress.finished
        }
    }

    suspend fun remove(id: String) {
        store.edit {
            it.remove(offsetKey(id))
            it.remove(openedKey(id))
            it.remove(doneKey(id))
        }
    }

    private fun offsetKey(id: String) = intPreferencesKey("$id.offset")
    private fun openedKey(id: String) = longPreferencesKey("$id.opened")
    private fun doneKey(id: String) = booleanPreferencesKey("$id.done")
}
