package se.constructions.castmote.resolver

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.constructions.castmote.youtube.YouTubeUrl

/** [StreamResolver] backed by on-device yt-dlp (youtubedl-android). */
class YtDlpStreamResolver(private val context: Context) : StreamResolver {

    override suspend fun resolve(url: String): ResolvedStream = withContext(Dispatchers.IO) {
        YtDlpInitializer.ensureInitialized(context)
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--no-playlist")
        }
        val response = try {
            YoutubeDL.getInstance().execute(request)
        } catch (e: YoutubeDLException) {
            val message = e.message.orEmpty()
            throw ResolverException(
                if (message.contains("drm", ignoreCase = true)) "Can't cast this link (DRM)"
                else "Can't cast this link",
            )
        }
        // YouTube's adaptive (HLS/DASH) manifests are unreliable on the default receiver, but its
        // progressive MP4 plays fine — so prefer progressive for YouTube VOD (live has no progressive
        // and still falls back to HLS).
        StreamSelector.select(response.out, preferProgressive = YouTubeUrl.isYouTubeUrl(url))
    }
}
