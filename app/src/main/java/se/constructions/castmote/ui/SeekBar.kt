package se.constructions.castmote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Formats a duration in seconds as `M:SS`, or `H:MM:SS` once it passes an hour. */
fun formatTime(seconds: Double): String {
    val total = if (seconds.isNaN() || seconds < 0) 0L else seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * A seekable progress bar for the active media session.
 *
 * The receiver only pushes a MEDIA_STATUS every few seconds, so the bar would otherwise
 * jump in coarse steps. To keep it smooth we interpolate locally: tick the displayed time
 * forward once a second while playing, rebasing to the authoritative [currentTime] each time
 * a fresh status arrives. While the user is dragging, the bar follows the finger and ignores
 * incoming updates; on release we issue a single [onSeek].
 *
 * Tapping anywhere on the track also seeks there (Material3 Slider moves the thumb to the tap).
 */
@Composable
fun SeekBar(
    currentTime: Double,
    duration: Double?,
    isPlaying: Boolean,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dur = duration ?: 0.0
    val hasDuration = dur > 0.0

    // Locally-advanced playback position; rebased whenever the receiver reports a new time.
    var ticked by remember { mutableStateOf(currentTime) }
    LaunchedEffect(currentTime, isPlaying, duration) {
        ticked = currentTime
        if (isPlaying && hasDuration) {
            while (isActive) {
                delay(1000)
                ticked = (ticked + 1.0).coerceAtMost(dur)
            }
        }
    }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val position = if (dragging) dragValue.toDouble() else ticked.coerceIn(0.0, if (hasDuration) dur else ticked)

    Column(modifier.fillMaxWidth()) {
        Slider(
            value = position.toFloat(),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragValue.toDouble())
            },
            valueRange = 0f..(if (hasDuration) dur.toFloat() else 1f),
            enabled = hasDuration,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
            Text(
                if (hasDuration) formatTime(dur) else "live",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
