package se.constructions.castmote.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import se.constructions.castmote.MainActivity

/**
 * Foreground service that mirrors the active cast onto a platform [MediaSession] + MediaStyle
 * notification, giving Android the lock-screen / notification-shade media control (scrubber +
 * transport). Reads [NowPlaying.state]; routes taps back through [NowPlaying]'s command lambdas.
 * Uses only platform APIs — no androidx.media dependency.
 */
class MediaControlService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSession

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(CHANNEL, "Uppspelning", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        session = MediaSession(this, "Castmote").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { NowPlaying.onPlayPause?.invoke() }
                override fun onPause() { NowPlaying.onPlayPause?.invoke() }
                override fun onStop() { NowPlaying.onStop?.invoke() }
                override fun onSeekTo(pos: Long) { NowPlaying.onSeekTo?.invoke(pos) }
                override fun onRewind() { seekBy(-SKIP_MS) }
                override fun onFastForward() { seekBy(SKIP_MS) }
            })
            isActive = true
        }
        scope.launch { NowPlaying.state.collect { render(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> NowPlaying.onPlayPause?.invoke()
            ACTION_STOP -> NowPlaying.onStop?.invoke()
            ACTION_REWIND -> seekBy(-SKIP_MS)
            ACTION_FORWARD -> seekBy(SKIP_MS)
        }
        render(NowPlaying.state.value) // start foreground promptly (within the 5s window)
        return START_NOT_STICKY
    }

    private fun seekBy(deltaMs: Long) {
        val pos = NowPlaying.state.value?.positionMs ?: return
        NowPlaying.onSeekTo?.invoke((pos + deltaMs).coerceAtLeast(0))
    }

    private fun render(s: NowPlaying.State?) {
        if (s == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, s.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s.subtitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, s.subtitle)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs)
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP or PlaybackState.ACTION_REWIND or
                        PlaybackState.ACTION_FAST_FORWARD,
                )
                .setState(
                    if (s.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    s.positionMs,
                    if (s.isPlaying) 1f else 0f,
                )
                .build(),
        )
        startForegroundCompat(buildNotification(s))
        } catch (e: Exception) {
            android.util.Log.e("CastmoteYT", "render/startForeground failed (${e.javaClass.simpleName}: ${e.message})")
        }
    }

    private fun buildNotification(s: NowPlaying.State): Notification {
        val playPauseIcon = if (s.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(s.title)
            .setContentText(s.subtitle)
            .setContentIntent(openApp())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(s.isPlaying)
            .addAction(action(android.R.drawable.ic_media_rew, "Bakåt", ACTION_REWIND))
            .addAction(action(playPauseIcon, "Spela/Paus", ACTION_PLAY_PAUSE))
            .addAction(action(android.R.drawable.ic_media_ff, "Framåt", ACTION_FORWARD))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stopp", ACTION_STOP))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun action(icon: Int, title: String, action: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, MediaControlService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(icon, title, pi).build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onDestroy() {
        session.isActive = false
        session.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "castmote_playback"
        private const val NOTIF_ID = 1
        private const val SKIP_MS = 30_000L
        private const val ACTION_PLAY_PAUSE = "se.constructions.castmote.PLAY_PAUSE"
        private const val ACTION_STOP = "se.constructions.castmote.STOP"
        private const val ACTION_REWIND = "se.constructions.castmote.REWIND"
        private const val ACTION_FORWARD = "se.constructions.castmote.FORWARD"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MediaControlService::class.java))
        }
    }
}
