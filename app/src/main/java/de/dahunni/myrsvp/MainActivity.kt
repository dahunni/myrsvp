package de.dahunni.myrsvp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import de.dahunni.myrsvp.ui.AppModel
import de.dahunni.myrsvp.ui.MyrsvpApp
import de.dahunni.myrsvp.ui.theme.MyrsvpTheme
import de.dahunni.myrsvp.ui.theme.darkColors

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSystemBars(dark = true)
        setContent {
            val settings by model.settings.collectAsState()
            val s = settings
            if (s == null) {
                // Settings are still loading (a few ms): plain background, no flash of the wrong theme.
                Box(Modifier.fillMaxSize().background(darkColors().background))
            } else {
                LaunchedEffect(s.darkTheme) { setSystemBars(s.darkTheme) }
                MyrsvpTheme(dark = s.darkTheme, focus = s.focusColor, wordSize = s.wordSize) {
                    MyrsvpApp(model, s)
                }
            }
        }
    }

    /** Transparent bars; their icons follow the app theme, not the system one. */
    private fun setSystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
