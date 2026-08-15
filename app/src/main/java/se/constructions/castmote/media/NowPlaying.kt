package se.constructions.castmote.media

import kotlinx.coroutines.flow.MutableStateFlow
import se.constructions.castmote.controller.MediaStatus
import se.constructions.castmote.controller.ReceiverStatus

/**
 * Process-wide bridge between the ViewModel (which owns the live cast connection) and the
 * [MediaControlService] (which renders the lock-screen / notification media control). The VM
 * publishes [state] and wires the command lambdas to the controller; the service reflects state
 * into a MediaSession and routes transport taps back through the lambdas.
 */
object NowPlaying {
    data class State(
        val title: String,
        val subtitle: String,
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
    )

    /** null = nothing playing → the service tears its notification down. */
    val state = MutableStateFlow<State?>(null)

    @Volatile var onPlayPause: (() -> Unit)? = null
    @Volatile var onSeekTo: ((Long) -> Unit)? = null
    @Volatile var onStop: (() -> Unit)? = null

    /** Pure mapping from cast status to a lock-screen [State]; null when there's no media session. */
    fun playback(media: MediaStatus?, receiver: ReceiverStatus?, device: String?): State? {
        if (media == null) return null
        val title = media.title?.takeIf { it.isNotBlank() }
            ?: receiver?.displayName?.takeIf { it.isNotBlank() }
            ?: "Castmote"
        val subtitle = listOfNotNull(receiver?.displayName, device)
            .filter { it.isNotBlank() && it != title }
            .distinct()
            .joinToString(" · ")
        return State(
            title = title,
            subtitle = subtitle,
            positionMs = (media.currentTime * 1000).toLong().coerceAtLeast(0),
            durationMs = ((media.duration ?: 0.0) * 1000).toLong().coerceAtLeast(0),
            isPlaying = media.playerState == "PLAYING",
        )
    }
}
