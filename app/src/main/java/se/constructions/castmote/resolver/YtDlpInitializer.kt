package se.constructions.castmote.resolver

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL

/** Idempotent one-time init of the embedded yt-dlp (unpacks Python on first call). */
object YtDlpInitializer {

    @Volatile
    private var initialized = false

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) return
        YoutubeDL.getInstance().init(context.applicationContext)
        initialized = true
    }
}
