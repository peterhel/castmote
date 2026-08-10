package se.constructions.castmote.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(onSignedIn: (String) -> Unit, onCancel: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // System back navigates the WebView history; cancels the flow only when it can't go back.
    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onCancel()
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Text("Sign in, then tap Done", Modifier.weight(1f).padding(top = 12.dp))
            Button(onClick = {
                val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
                onSignedIn(cookie)
            }) { Text("Done") }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(ctx).apply {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = CHROME_UA
                    webViewClient = WebViewClient()
                    loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fm.youtube.com")
                    webView = this
                }
            },
        )
    }
}
