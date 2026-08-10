package se.constructions.castmote.resolver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure selection of a castable stream from `yt-dlp --dump-single-json` output.
 * Prefers an HLS master manifest (adaptive), then an HLS variant, then the best
 * progressive MP4. Raises [ResolverException] for DRM or when nothing is playable.
 */
object StreamSelector {

    private val json = Json { ignoreUnknownKeys = true }
    private val HLS_PROTOCOLS = setOf("m3u8", "m3u8_native")
    private const val HLS_TYPE = "application/vnd.apple.mpegurl"
    private const val MP4_TYPE = "video/mp4"

    /**
     * @param preferProgressive when true, a self-contained progressive MP4 is chosen ahead of any
     * HLS/DASH manifest. The default media receiver plays a single muxed file far more reliably than
     * YouTube's adaptive manifests, so YouTube VOD uses this; live (no progressive) still falls to HLS.
     */
    fun select(dumpJson: String, preferProgressive: Boolean = false): ResolvedStream {
        val root = json.parseToJsonElement(dumpJson).jsonObject
        val title = root.str("title")
        val live = root.bool("is_live") == true || root.str("live_status") == "is_live"
        val formats = root["formats"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val effective = if (formats.isNotEmpty()) formats else singleFormat(root)

        val playable = effective.filter { it.str("url") != null || it.str("manifest_url") != null }
        val nonDrm = playable.filter { it.bool("has_drm") != true }
        if (nonDrm.isEmpty()) {
            val drm = root.bool("_has_drm") == true || playable.any { it.bool("has_drm") == true }
            throw ResolverException(if (drm) "Can't cast this link (DRM)" else "Can't cast this link")
        }

        // True progressive = a single self-contained file (http/https), NOT an HLS/DASH manifest.
        // YouTube hands muxed renditions back as hls_playlist manifests that happen to list both
        // codecs; those must go through the HLS path with the right MIME type, not be cast as MP4.
        val progressive = nonDrm.filter {
            it.str("url") != null && it.str("vcodec").notNone() && it.str("acodec").notNone() &&
                it.str("protocol") !in HLS_PROTOCOLS && it.str("manifest_url") == null &&
                it.str("url")?.contains("/manifest/") != true
        }
        fun bestProgressive(): ResolvedStream? =
            progressive.maxByOrNull { it.height() }?.let { ResolvedStream(it.str("url")!!, MP4_TYPE, title, live) }

        if (preferProgressive) bestProgressive()?.let { return it }

        nonDrm.firstOrNull { it.str("manifest_url") != null }?.let {
            return ResolvedStream(it.str("manifest_url")!!, HLS_TYPE, title, live)
        }
        nonDrm.firstOrNull { it.str("protocol") in HLS_PROTOCOLS && it.str("url") != null }?.let {
            return ResolvedStream(it.str("url")!!, HLS_TYPE, title, live)
        }
        bestProgressive()?.let { return it }
        nonDrm.filter { it.str("url") != null }.maxByOrNull { it.height() }?.let {
            return ResolvedStream(it.str("url")!!, MP4_TYPE, title, live)
        }
        throw ResolverException("Can't cast this link")
    }

    private fun singleFormat(root: JsonObject): List<JsonObject> =
        if (root.str("url") != null) listOf(root) else emptyList()

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.height(): Int = this["height"]?.jsonPrimitive?.intOrNull ?: 0
    private fun String?.notNone(): Boolean = this != null && this != "none"
}
