package de.dahunni.myrsvp.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.dahunni.myrsvp.BuildConfig
import de.dahunni.myrsvp.R
import de.dahunni.myrsvp.book.ImportError
import de.dahunni.myrsvp.data.Settings
import de.dahunni.myrsvp.engine.Player
import de.dahunni.myrsvp.input.hasKeypad
import de.dahunni.myrsvp.ui.info.BookInfoScreen
import de.dahunni.myrsvp.ui.library.LibraryMenuScreen
import de.dahunni.myrsvp.ui.library.LibraryScreen
import de.dahunni.myrsvp.ui.menu.ChaptersScreen
import de.dahunni.myrsvp.ui.menu.MenuScreen
import de.dahunni.myrsvp.ui.reader.ReaderScreen
import de.dahunni.myrsvp.ui.settings.AboutScreen
import de.dahunni.myrsvp.ui.settings.KeyTestScreen
import de.dahunni.myrsvp.ui.settings.SettingsScreen
import de.dahunni.myrsvp.ui.theme.RsvpTheme
import de.dahunni.myrsvp.ui.update.UpdateScreen

private object Routes {
    const val LIBRARY = "library"
    const val LIBRARY_MENU = "library-menu?book={book}"
    const val READER = "reader"
    const val MENU = "menu"
    const val CHAPTERS = "chapters"
    const val INFO = "info"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val KEYS = "keys"
    const val UPDATE = "update"

    fun libraryMenu(bookId: String?) = "library-menu?book=${bookId.orEmpty()}"
}

/** File types offered in the system picker; some file managers label EPUBs as octet-stream. */
private val IMPORT_TYPES = arrayOf("application/epub+zip", "text/plain", "application/octet-stream")

/** All screens. The app starts in the library (docs/SPEC.md §1); no transitions (docs/DESIGN.md "Motion"). */
@Composable
fun MyrsvpApp(model: AppModel, settings: Settings) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val showHints = hasKeypad(LocalConfiguration.current)
    val books by model.library.collectAsState()
    val importing by model.importing.collectAsState()
    val update by model.update.collectAsState()
    val updatePrompt by model.updatePrompt.collectAsState()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.import(it) }
    val addBooks = { picker.launch(IMPORT_TYPES) }

    LaunchedEffect(Unit) {
        model.reports.collect { report -> Toast.makeText(context, reportText(context, report), Toast.LENGTH_LONG).show() }
    }

    // A new release found by the automatic check is offered once, when the user is in the library.
    LaunchedEffect(updatePrompt, route) {
        if (updatePrompt != null && route == Routes.LIBRARY) {
            model.updatePromptShown()
            nav.navigate(Routes.UPDATE)
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.LIBRARY,
        modifier = Modifier.background(RsvpTheme.colors.background),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.LIBRARY) {
            val list = books
            if (list == null) {
                Blank()
            } else {
                LibraryScreen(
                    books = list,
                    importing = importing,
                    showHints = showHints,
                    onOpen = { id ->
                        model.openBook(id)
                        nav.navigate(Routes.READER)
                    },
                    onAddBooks = addBooks,
                    onMenu = { id -> nav.navigate(Routes.libraryMenu(id)) },
                )
            }
        }
        composable(
            Routes.LIBRARY_MENU,
            arguments = listOf(navArgument("book") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val id = entry.arguments?.getString("book").orEmpty()
            LibraryMenuScreen(
                bookTitle = books?.firstOrNull { it.id == id }?.title,
                showHints = showHints,
                onAddBooks = {
                    nav.popBackStack()
                    addBooks()
                },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onRemove = {
                    model.deleteBook(id)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.READER) {
            when (val open = model.open) {
                is OpenState.Ready -> ReaderScreen(
                    session = open.session,
                    title = open.session.book.meta.title,
                    settings = settings,
                    onOpenMenu = { nav.navigate(Routes.MENU) },
                    onSpeedChange = { wpm -> model.updateSettings { it.copy(wpm = wpm) } },
                    onSaveProgress = { model.saveProgress(open.session) },
                )
                is OpenState.Loading -> Message(stringResource(R.string.reader_opening))
                is OpenState.Failed -> Message(stringResource(R.string.reader_open_failed))
                null -> BackToLibrary(nav)
            }
        }
        composable(Routes.MENU) {
            WithSession(model, nav) { session ->
                MenuScreen(
                    title = session.book.meta.title,
                    text = session.book.text,
                    index = session.state.index,
                    showHints = showHints,
                    onContinue = { nav.popBackStack() },
                    onChapters = { nav.navigate(Routes.CHAPTERS) },
                    onBookInfo = { nav.navigate(Routes.INFO) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onStartOver = {
                        session.state = Player(session.book.text).jumpTo(session.state, 0)
                        nav.popBackStack(Routes.READER, inclusive = false)
                    },
                    onLibrary = { nav.popBackStack(Routes.LIBRARY, inclusive = false) },
                )
            }
        }
        composable(Routes.CHAPTERS) {
            WithSession(model, nav) { session ->
                ChaptersScreen(
                    title = session.book.meta.title,
                    text = session.book.text,
                    index = session.state.index,
                    showHints = showHints,
                    onSelect = { wordIndex ->
                        session.state = Player(session.book.text).jumpTo(session.state, wordIndex)
                        nav.popBackStack(Routes.READER, inclusive = false)
                    },
                )
            }
        }
        composable(Routes.INFO) {
            WithSession(model, nav) { session ->
                BookInfoScreen(session.book.meta, session.book.text, session.state.index, settings.wpm, showHints)
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                showHints = showHints,
                onChange = model::updateSettings,
                update = update,
                onUpdate = {
                    if (update is UpdateState.Available || update is UpdateState.Downloaded || update is UpdateState.Downloading) {
                        nav.navigate(Routes.UPDATE)
                    } else {
                        model.checkForUpdates(manual = true)
                    }
                },
                onAbout = { nav.navigate(Routes.ABOUT) },
                onKeyTest = { nav.navigate(Routes.KEYS) },
            )
        }
        composable(Routes.ABOUT) { AboutScreen(showHints) }
        composable(Routes.KEYS) { KeyTestScreen() }
        composable(Routes.UPDATE) {
            val release = when (val u = update) {
                is UpdateState.Available -> u.release
                is UpdateState.Downloading -> u.release
                is UpdateState.Downloaded -> u.release
                is UpdateState.Failed -> u.release
                else -> null
            }
            if (release == null) {
                BackToLibrary(nav)
            } else {
                UpdateScreen(
                    release = release,
                    state = update,
                    installedVersion = BuildConfig.VERSION_NAME,
                    showHints = showHints,
                    onInstall = { model.downloadAndInstall(release) },
                    onLater = { nav.popBackStack() },
                    onSkip = {
                        model.skipUpdate(release)
                        nav.popBackStack()
                    },
                )
            }
        }
    }
}

/** Screens that need the open book; without one (e.g. after the process was killed) go to the library. */
@Composable
private fun WithSession(model: AppModel, nav: NavHostController, content: @Composable (ReaderSession) -> Unit) {
    val open = model.open
    if (open is OpenState.Ready) content(open.session) else BackToLibrary(nav)
}

@Composable
private fun BackToLibrary(nav: NavHostController) {
    Blank()
    LaunchedEffect(Unit) { nav.popBackStack(Routes.LIBRARY, inclusive = false) }
}

@Composable
private fun Blank() {
    Box(Modifier.fillMaxSize().background(RsvpTheme.colors.background))
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().background(RsvpTheme.colors.background), contentAlignment = Alignment.Center) {
        Text(text, style = RsvpTheme.type.meta, color = RsvpTheme.colors.textMuted)
    }
}

private fun reportText(context: Context, report: ImportReport): String {
    val lines = ArrayList<String>()
    if (report.added > 0) lines += context.resources.getQuantityString(R.plurals.library_imported, report.added, report.added)
    report.failures.forEach { (name, error) ->
        val reason = context.getString(
            when (error) {
                ImportError.Drm -> R.string.import_error_drm
                ImportError.NotAnEpub -> R.string.import_error_not_epub
                ImportError.Empty -> R.string.import_error_empty
                ImportError.Unsupported -> R.string.import_error_unsupported
                ImportError.Unreadable -> R.string.import_error_unreadable
            },
        )
        lines += context.getString(R.string.library_import_failed, name, reason)
    }
    return lines.joinToString("\n")
}
