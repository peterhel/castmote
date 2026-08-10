package se.constructions.castmote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.constructions.castmote.controller.MediaStatus
import se.constructions.castmote.controller.ReceiverStatus
import se.constructions.castmote.discovery.CastDevice
import se.constructions.castmote.history.HistoryEntry

@Composable
fun ControlScreen(
    device: CastDevice,
    receiver: ReceiverStatus?,
    media: MediaStatus?,
    castStatus: CastStatus,
    youTubeSignedIn: Boolean,
    online: Boolean,
    history: List<HistoryEntry>,
    onBack: () -> Unit,
    onReconnect: () -> Unit,
    onPlayPause: () -> Unit,
    onStopMedia: () -> Unit,
    onSeek: (Double) -> Unit,
    onVolume: (Double) -> Unit,
    onMuted: (Boolean) -> Unit,
    onStopApp: () -> Unit,
    onCast: (String) -> Unit,
    onClearHistory: () -> Unit,
    onYouTubeSignIn: () -> Unit,
    onYouTubeSignOut: () -> Unit,
) {
    var url by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedButton(onClick = onBack) { Text("‹ Devices") }
        if (!online) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Disconnected", color = MaterialTheme.colorScheme.error)
                Button(onClick = onReconnect) { Text("Reconnect") }
            }
        }
        Text(device.friendlyName, style = MaterialTheme.typography.headlineSmall)
        Text(
            receiver?.displayName?.let { "App: $it" } ?: "No app running",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            media?.let { "${it.title ?: it.contentId ?: "Media"} — ${it.playerState}" } ?: "Nothing playing",
            Modifier.padding(top = 8.dp),
        )

        if (media != null) {
            SeekBar(
                currentTime = media.currentTime,
                duration = media.duration,
                isPlaying = media.playerState == "PLAYING",
                onSeek = onSeek,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlayPause) { Text(if (media?.playerState == "PLAYING") "Pause" else "Play") }
            Button(onClick = onStopMedia) { Text("Stop") }
            Button(onClick = { onSeek(((media?.currentTime ?: 0.0) + 30).coerceAtLeast(0.0)) }) { Text("+30s") }
            Button(onClick = { onSeek(((media?.currentTime ?: 0.0) - 30).coerceAtLeast(0.0)) }) { Text("-30s") }
        }

        Text("Volume", Modifier.padding(top = 16.dp))
        Slider(
            value = (receiver?.volumeLevel ?: 0.0).toFloat(),
            onValueChange = { onVolume(it.toDouble()) },
            valueRange = 0f..1f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMuted(true) }) { Text("Mute") }
            OutlinedButton(onClick = { onMuted(false) }) { Text("Unmute") }
            OutlinedButton(onClick = onStopApp) { Text("Stop app") }
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Media URL or link") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            singleLine = true,
        )
        Button(
            onClick = { if (url.isNotBlank()) onCast(url) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Cast") }

        when (val status = castStatus) {
            CastStatus.Idle -> {}
            CastStatus.Resolving -> Text("Resolving…", Modifier.padding(top = 8.dp))
            is CastStatus.Error -> Text(
                status.message,
                Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        HistorySection(
            entries = history,
            onCast = onCast,
            onClear = onClearHistory,
            modifier = Modifier.padding(top = 16.dp),
        )

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (youTubeSignedIn) {
                Text("YouTube: signed in ✓", Modifier.weight(1f).padding(top = 12.dp))
                OutlinedButton(onClick = onYouTubeSignOut) { Text("Sign out") }
            } else {
                Text("YouTube ads?", Modifier.weight(1f).padding(top = 12.dp))
                OutlinedButton(onClick = onYouTubeSignIn) { Text("Sign in for ad-free") }
            }
        }
    }
}
