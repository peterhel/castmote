package se.constructions.castmote.caster

import se.constructions.castmote.browser.StreamKind

/** The cast actions a strategy can perform; implemented by the ViewModel over CastController. */
interface CastSink {
    /** Cast a sniffed manifest to the default media receiver. */
    suspend fun castStream(url: String, kind: StreamKind, hlsFmp4: Boolean)

    /** Cast by page URL through the app's existing smart routing (e.g. YouTube VOD/live). */
    suspend fun castPage(pageUrl: String)
}
