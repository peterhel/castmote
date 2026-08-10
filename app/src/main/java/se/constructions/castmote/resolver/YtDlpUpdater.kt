package se.constructions.castmote.resolver

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL

/** Updates the embedded yt-dlp binary at most once a week. */
object YtDlpUpdater {

    private const val PREFS = "castmote"
    private const val KEY_LAST_UPDATE = "ytdlp_last_update"
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** Call from a background thread. Best-effort; failures are ignored by the caller. */
    fun maybeUpdate(context: Context, now: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (now - prefs.getLong(KEY_LAST_UPDATE, 0L) < WEEK_MS) return
        YtDlpInitializer.ensureInitialized(context)
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        prefs.edit().putLong(KEY_LAST_UPDATE, now).apply()
    }
}
