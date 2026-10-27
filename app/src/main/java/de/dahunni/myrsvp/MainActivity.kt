package de.dahunni.myrsvp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.dahunni.myrsvp.book.SampleBook
import de.dahunni.myrsvp.ui.reader.ReaderScreen
import de.dahunni.myrsvp.ui.theme.MyrsvpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            MyrsvpTheme {
                // Milestone 1: straight into the reader with the built-in sample text.
                ReaderScreen(text = SampleBook.text, title = SampleBook.TITLE)
            }
        }
    }
}
