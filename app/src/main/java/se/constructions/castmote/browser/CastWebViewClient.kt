package se.constructions.castmote.browser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/** JS injected on each page: hooks fetch/XHR to report URLs the native interceptor may miss. */
private const val SNIFF_JS = """
(function() {
  if (window.__castmoteHooked) return; window.__castmoteHooked = true;
  function report(u){ try { if (u) Castmote.onUrl(String(u)); } catch(e){} }
  var of = window.fetch;
  if (of) window.fetch = function(i){ report((i && i.url) || i); return of.apply(this, arguments); };
  var oo = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(m, u){ report(u); return oo.apply(this, arguments); };
})();
"""

/**
 * Reports every resource URL to [sniffer] (off the main thread) and re-injects the fetch/XHR
 * hook after each navigation. Always returns null from shouldInterceptRequest so the WebView
 * loads normally — we only observe.
 */
open class CastWebViewClient(private val sniffer: StreamSniffer) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        sniffer.onPageStarted(url.orEmpty())
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        view?.evaluateJavascript(SNIFF_JS, null)
        super.onPageFinished(view, url)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        request?.url?.toString()?.let { sniffer.onRequest(it, null) }
        return null
    }

    /** Bridge target for the injected JS; add with addJavascriptInterface(name = "Castmote"). */
    inner class Bridge {
        @android.webkit.JavascriptInterface
        fun onUrl(url: String) { sniffer.onRequest(url, null) }
    }
}
