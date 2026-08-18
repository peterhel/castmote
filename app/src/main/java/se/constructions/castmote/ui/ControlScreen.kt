package se.constructions.castmote.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.constructions.castmote.controller.MediaStatus
import se.constructions.castmote.controller.ReceiverStatus
import se.constructions.castmote.discovery.CastDevice
import se.constructions.castmote.history.HistoryEntry

/** Compact label for a skip step: "30s", "1m30s", "10m", "20m". */
internal fun formatSkip(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return when {
        m == 0 -> "${s}s"
        s == 0 -> "${m}m"
        else -> "${m}m${s}s"
    }
}

private val SKIP_STEPS = listOf(30, 60, 300, 600, 1200) // 30s · 1m · 5m · 10m · 20m

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
    onCastEntry: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onYouTubeSignIn: () -> Unit,
    onYouTubeSignOut: () -> Unit,
    prefillUrl: String? = null,
    onPrefillConsumed: () -> Unit = {},
) {
    var url by remember { mutableStateOf("") }
    var skipSeconds by remember { mutableIntStateOf(30) } // step chosen by the chips

    // Seed the cast field from an incoming deep link / share, then clear it so it doesn't refill.
    LaunchedEffect(prefillUrl) {
        if (!prefillUrl.isNullOrBlank()) {
            url = prefillUrl
            onPrefillConsumed()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Devices")
        }

        if (!online) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Disconnected", color = MaterialTheme.colorScheme.onErrorContainer)
                    Button(onClick = onReconnect) { Text("Reconnect") }
                }
            }
        }

        // ── Now playing ──────────────────────────────────────────────────────
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(device.friendlyName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    receiver?.displayName?.let { "App: $it" } ?: "No app running",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (media != null) {
                    Text(
                        "${media.title ?: media.contentId ?: "Media"} — ${media.playerState}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    SeekBar(
                        currentTime = media.currentTime,
                        duration = media.duration,
                        isPlaying = media.playerState == "PLAYING",
                        onSeek = onSeek,
                    )
                } else {
                    Text(
                        "Nothing playing — open the Browser tab or paste a URL below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Transport: Play/Pause is the hero; Stop is secondary.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val playing = media?.playerState == "PLAYING"
                    FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            Modifier.size(32.dp),
                        )
                    }
                    OutlinedIconButton(onClick = onStopMedia, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }

                // Skip step: ‹ back — chips — forward ›. Buttons seek by the selected step.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalIconButton(onClick = {
                        onSeek(((media?.currentTime ?: 0.0) - skipSeconds).coerceAtLeast(0.0))
                    }) { Icon(Icons.Default.FastRewind, contentDescription = "Skip back") }

                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SKIP_STEPS.forEach { step ->
                            FilterChip(
                                selected = skipSeconds == step,
                                onClick = { skipSeconds = step },
                                label = { Text(formatSkip(step)) },
                            )
                        }
                    }

                    FilledTonalIconButton(onClick = {
                        onSeek((media?.currentTime ?: 0.0) + skipSeconds)
                    }) { Icon(Icons.Default.FastForward, contentDescription = "Skip forward") }
                }
            }
        }

        // ── Volume ───────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slider(
                value = (receiver?.volumeLevel ?: 0.0).toFloat(),
                onValueChange = { onVolume(it.toDouble()) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
            )
            val muted = receiver?.muted == true
            IconToggleButton(checked = muted, onCheckedChange = { onMuted(it) }) {
                Icon(
                    if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (muted) "Unmute" else "Mute",
                )
            }
        }

        HorizontalDivider()

        // ── Cast something ───────────────────────────────────────────────────
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Media URL or link") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { if (url.isNotBlank()) onCast(url) }) {
            Icon(Icons.Default.Cast, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Cast")
        }

        when (val status = castStatus) {
            CastStatus.Idle -> {}
            CastStatus.Resolving -> Text("Resolving…")
            is CastStatus.Error -> Text(status.message, color = MaterialTheme.colorScheme.error)
        }

        HistorySection(entries = history, onCast = onCastEntry, onClear = onClearHistory)

        // YouTube account row.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.SmartDisplay, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    if (youTubeSignedIn) "YouTube — signed in ✓" else "YouTube account",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (youTubeSignedIn) "Casting ad-free" else "Sign in to cast ad-free",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (youTubeSignedIn) {
                OutlinedButton(onClick = onYouTubeSignOut) { Text("Sign out") }
            } else {
                OutlinedButton(onClick = onYouTubeSignIn) { Text("Sign in") }
            }
        }

        HorizontalDivider()

        // Destructive action, kept away from the transport/volume controls.
        TextButton(
            onClick = onStopApp,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Stop app on TV")
        }
    }
}
