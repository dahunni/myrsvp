package de.dahunni.myrsvp.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.dahunni.myrsvp.book.BookImporter
import de.dahunni.myrsvp.book.BookStore
import de.dahunni.myrsvp.book.BookText
import de.dahunni.myrsvp.book.ImportError
import de.dahunni.myrsvp.book.ImportException
import de.dahunni.myrsvp.book.StoredBook
import de.dahunni.myrsvp.data.Progress
import de.dahunni.myrsvp.data.ProgressStore
import de.dahunni.myrsvp.data.Settings
import de.dahunni.myrsvp.data.SettingsStore
import de.dahunni.myrsvp.engine.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A book row in the library. */
data class LibraryBook(
    val id: String,
    val title: String,
    val author: String?,
    val percent: Int,
    val finished: Boolean,
    val lastOpened: Long,
)

/** The book open in the reader. Its playback state outlives the reader screen (menu, settings). */
class ReaderSession(val id: String, val book: BookText) {
    var state by mutableStateOf(PlayerState())
}

sealed interface OpenState {
    data class Loading(val id: String) : OpenState
    data class Ready(val session: ReaderSession) : OpenState
    data class Failed(val id: String) : OpenState
}

data class ImportProgress(val done: Int, val total: Int)

/** Result of one import run, for a short message in the library. */
data class ImportReport(val added: Int, val failures: List<Pair<String, ImportError>>)

/** App-wide state: settings, the library, imports and the open book. Survives configuration changes. */
class AppModel(app: Application) : AndroidViewModel(app) {
    private val books = BookStore(File(app.filesDir, "books"))
    private val settingsStore = SettingsStore(app)
    private val progressStore = ProgressStore(app)

    /** Null until loaded from disk, so the first frame already uses the right theme. */
    val settings: StateFlow<Settings?> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val stored = MutableStateFlow<List<StoredBook>?>(null)

    /** Unfinished books by last read (never opened: newest import first), finished ones at the end. */
    val library: StateFlow<List<LibraryBook>?> = combine(stored, progressStore.all) { list, progress ->
        list?.map { b ->
            val p = progress[b.id] ?: Progress()
            LibraryBook(
                id = b.id,
                title = b.meta.title,
                author = b.meta.author,
                percent = if (b.bodyBytes <= 0) 0 else (p.byteOffset * 100L / b.bodyBytes).toInt().coerceIn(0, 100),
                finished = p.finished,
                lastOpened = maxOf(p.lastOpened, b.importedAt),
            )
        }?.sortedWith(compareBy<LibraryBook> { it.finished }.thenByDescending { it.lastOpened })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val importing = MutableStateFlow<ImportProgress?>(null)
    private val _reports = MutableSharedFlow<ImportReport>(extraBufferCapacity = 4)
    val reports: SharedFlow<ImportReport> = _reports

    var open by mutableStateOf<OpenState?>(null)
        private set

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch { stored.value = withContext(Dispatchers.IO) { books.list() } }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || importing.value != null) return
        viewModelScope.launch {
            val failures = ArrayList<Pair<String, ImportError>>()
            var added = 0
            for ((i, uri) in uris.withIndex()) {
                importing.value = ImportProgress(i, uris.size)
                val name = displayName(uri)
                val result: Result<String> = withContext(Dispatchers.IO) {
                    try {
                        val resolver = getApplication<Application>().contentResolver
                        val book = resolver.openInputStream(uri)?.use { BookImporter.convert(it, name) }
                            ?: throw ImportException(ImportError.Unreadable)
                        Result.success(books.save(book))
                    } catch (e: ImportException) {
                        Result.failure(e)
                    } catch (e: Exception) {
                        Result.failure(ImportException(ImportError.Unreadable, e))
                    } catch (e: OutOfMemoryError) {
                        Result.failure(ImportException(ImportError.Unreadable, e))
                    }
                }
                result.onSuccess { id ->
                    added++
                    // The open book was replaced: the next open reads the new text.
                    if ((open as? OpenState.Ready)?.session?.id == id) open = null
                }
                result.onFailure { failures += name to ((it as? ImportException)?.error ?: ImportError.Unreadable) }
            }
            importing.value = null
            stored.value = withContext(Dispatchers.IO) { books.list() }
            _reports.tryEmit(ImportReport(added, failures))
        }
    }

    /** Opens a book for the reader; reuses the session if it is already open. */
    fun openBook(id: String) {
        val current = open
        if (current is OpenState.Ready && current.session.id == id) {
            markOpened(current.session)
            return
        }
        open = OpenState.Loading(id)
        viewModelScope.launch {
            val book = withContext(Dispatchers.Default) { runCatching { books.load(id) }.getOrNull() }
            if (book == null) {
                open = OpenState.Failed(id)
                return@launch
            }
            val progress = progressStore.all.first()[id] ?: Progress()
            val wpm = settingsStore.settings.first().wpm
            val session = ReaderSession(id, book)
            session.state = PlayerState(index = book.wordIndexForByteOffset(progress.byteOffset), wpm = wpm)
            if (open == OpenState.Loading(id)) open = OpenState.Ready(session)
            markOpened(session)
        }
    }

    private fun markOpened(session: ReaderSession) {
        saveProgress(session)
        viewModelScope.launch { settingsStore.update { it.copy(lastBookId = session.id) } }
    }

    /** Saves the session's position (current sentence start) and whether the end was reached. */
    fun saveProgress(session: ReaderSession) {
        val state = session.state
        val text = session.book.text
        val progress = Progress(
            byteOffset = session.book.byteOffsetForWord(state.index),
            lastOpened = System.currentTimeMillis(),
            finished = text.size > 0 && state.index >= text.size - 1,
        )
        viewModelScope.launch { progressStore.save(session.id, progress) }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    fun deleteBook(id: String) {
        val current = open
        if (current is OpenState.Ready && current.session.id == id || current is OpenState.Loading && current.id == id) open = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { books.delete(id) }
            progressStore.remove(id)
            settingsStore.update { if (it.lastBookId == id) it.copy(lastBookId = null) else it }
            stored.value = withContext(Dispatchers.IO) { books.list() }
        }
    }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "book"
    }
}
