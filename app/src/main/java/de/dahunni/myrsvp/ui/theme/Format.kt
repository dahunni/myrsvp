package de.dahunni.myrsvp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.dahunni.myrsvp.R

/** "5 h 6 min", "23 min" or "< 1 min". */
@Composable
fun durationText(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return when {
        minutes >= 60 -> stringResource(R.string.duration_hours, minutes / 60, minutes % 60)
        minutes >= 1 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_under_minute)
    }
}
