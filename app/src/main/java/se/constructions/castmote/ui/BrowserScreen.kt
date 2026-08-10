package se.constructions.castmote.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import se.constructions.castmote.browser.CastWebViewClient
import se.constructions.castmote.browser.DetectedStream
import se.constructions.castmote.browser.StreamSniffer
import se.constructions.castmote.discovery.CastDevice

private const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

private const val HOME_URL = "https://www.google.com"

/** Quick-launch sites for the browser top bar (favicon shown via [FaviconImage] by host). */
private data class Shortcut(val host: String, val url: String)

private val SHORTCUTS = listOf(
    Shortcut("ehftv.com", "https://ehftv.com"),
    Shortcut("primevideo.com", "https://www.primevideo.com"),
    Shortcut("netflix.com", "https://www.netflix.com"),
    Shortcut("youtube.com", "https://www.youtube.com"),
    Shortcut("disneyplus.com", "https://www.disneyplus.com"),
    Shortcut("tv4play.se", "https://www.tv4play.se"),
    Shortcut("svtplay.se", "https://www.svtplay.se"),
    Shortcut("viaplay.se", "https://viaplay.se"),
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    sniffer: StreamSniffer,
    canCast: Boolean,
    streams: List<DetectedStream>,
    devices: List<CastDevice>,
    onPage: (String) -> Unit,
    onCast: (CastDevice, DetectedStream?) -> Unit,
) {
    val currentOnPage by rememberUpdatedState(onPage)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf("https://") }
    var canGoBack by remember { mutableStateOf(false) }
    // Cast flow: 0 = closed, 1 = pick stream (intercept, multiple), 2 = pick device.
    var castStep by remember { mutableStateOf(0) }
    var chosenStream by remember { mutableStateOf<DetectedStream?>(null) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Favicon shortcut bar — tap a site to open it in the WebView.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SHORTCUTS.forEach { s ->
                    FaviconImage(
                        host = s.host,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { address = s.url; webView?.loadUrl(s.url) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    label = { Text("Address") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val a = address.trim()
                        if (a.isNotBlank() && a != "https://") webView?.loadUrl(normalizeUrl(a))
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Go") }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = CHROME_UA
                        // Many sites' menus/links open a "new window" (window.open / target=_blank);
                        // without this the WebView silently drops them and the menu looks broken.
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(true)
                        // One client: forwards page URL to the ViewModel and feeds the sniffer.
                        val client = object : CastWebViewClient(sniffer) {
                            override fun onPageStarted(v: WebView?, url: String?, f: android.graphics.Bitmap?) {
                                currentOnPage(url.orEmpty())
                                if (!url.isNullOrEmpty()) address = url
                                canGoBack = v?.canGoBack() == true
                                super.onPageStarted(v, url, f)
                            }
                            override fun onPageFinished(v: WebView?, url: String?) {
                                super.onPageFinished(v, url)
                                canGoBack = v?.canGoBack() == true
                                // Persist cookies to disk now — WebView writes them lazily, so a
                                // login would otherwise be lost when the app restarts.
                                CookieManager.getInstance().flush()
                            }
                            // Single-page apps (YouTube, etc.) change the URL via the History API
                            // without a full load, so onPageStarted never fires. This does — keep
                            // the page URL current so the Cast badge updates on in-app navigation.
                            override fun doUpdateVisitedHistory(v: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(v, url, isReload)
                                if (!url.isNullOrEmpty()) {
                                    currentOnPage(url)
                                    address = url
                                }
                                canGoBack = v?.canGoBack() == true
                            }
                        }
                        webViewClient = client
                        // Handle "new window" requests by loading the target in this same WebView,
                        // so menu items / target=_blank links navigate instead of vanishing.
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?,
                            ): Boolean {
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                val popup = WebView(view!!.context)
                                popup.settings.javaScriptEnabled = true
                                popup.settings.userAgentString = CHROME_UA
                                popup.webViewClient = object : android.webkit.WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                    ): Boolean {
                                        request?.url?.toString()?.let { view.loadUrl(it) }
                                        return true
                                    }
                                }
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        addJavascriptInterface(client.Bridge(), "Castmote")
                        webView = this
                        loadUrl(HOME_URL)   // a homepage so the tab is never blank
                    }
                },
            )
        }
        if (canCast) {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Multiple sniffed streams → pick one first; otherwise straight to device.
                        if (streams.size > 1) {
                            castStep = 1
                        } else {
                            chosenStream = streams.firstOrNull()
                            castStep = 2
                        }
                    },
                ) { Text(if (streams.size > 1) "Cast ▶ (${streams.size})" else "Cast ▶") }

                // Step 1 — choose which sniffed stream (intercept path with several).
                DropdownMenu(expanded = castStep == 1, onDismissRequest = { castStep = 0 }) {
                    streams.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s.kind} · ${s.url.substringAfterLast('/').take(28)}") },
                            onClick = { chosenStream = s; castStep = 2 },
                        )
                    }
                }
                // Step 2 — choose the Chromecast (connects on demand, no pre-connect needed).
                DropdownMenu(expanded = castStep == 2, onDismissRequest = { castStep = 0 }) {
                    if (devices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No Chromecasts found") },
                            onClick = { castStep = 0 },
                        )
                    }
                    devices.forEach { d ->
                        DropdownMenuItem(
                            text = { Text("📺  ${d.friendlyName}") },
                            onClick = { castStep = 0; onCast(d, chosenStream) },
                        )
                    }
                }
            }
        }
    }
}

private fun normalizeUrl(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(' ') || !t.contains('.') -> "https://www.google.com/search?q=" + android.net.Uri.encode(t)
        else -> "https://$t"
    }
}
