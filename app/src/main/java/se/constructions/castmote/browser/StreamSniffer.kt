package se.constructions.castmote.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Accumulates the media manifests seen on the current page. Fed by the WebView client and the
 * injected JS hook (both on background threads). Resets when navigation to a new page starts.
 */
class StreamSniffer {
    private val _streams = MutableStateFlow<List<DetectedStream>>(emptyList())
    val streams: StateFlow<List<DetectedStream>> = _streams.asStateFlow()

    @Volatile private var pageUrl: String = ""

    fun onPageStarted(url: String) {
        pageUrl = url
        _streams.value = emptyList()
    }

    fun onRequest(url: String, contentType: String?) {
        val kind = MediaUrl.kindOf(url, contentType) ?: return
        _streams.update { current ->
            if (current.any { it.url == url }) current
            else current + DetectedStream(url, kind, pageUrl)
        }
    }
}
