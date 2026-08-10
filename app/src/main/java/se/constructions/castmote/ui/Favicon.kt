package se.constructions.castmote.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.constructions.castmote.history.CastHistory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Fetches and caches site favicons by host, so the Recent list can render them lazily. */
object FaviconLoader {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun load(host: String): ImageBitmap? {
        cache[host]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(CastHistory.faviconUrl(host)).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                try {
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()?.also { cache[host] = it }
        }
    }
}

/** A site favicon for [host]; shows a neutral placeholder until (or unless) it loads. */
@Composable
fun FaviconImage(host: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, host) { value = FaviconLoader.load(host) }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(4.dp)))
    } else {
        Box(modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
