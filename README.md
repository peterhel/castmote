# Castmote

Android universal remote for Chromecast over the raw **CASTV2** protocol.
Discovers devices on the LAN (mDNS), then controls transport, volume, app
launch/stop, and casts media URLs — including sessions started by other senders.

Beyond direct media files, it can cast **web links**: page URLs are resolved
on-device with an embedded **yt-dlp** (DRM-free sites), and **YouTube** links are
played through YouTube's own receiver via its lounge protocol.

## Build & run

Uses Android Studio's bundled JBR (no separate JDK needed). From the repo root:

    export JAVA_HOME="$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr"
    ./gradlew :app:installDebug
    adb shell am start -n se.constructions.castmote/.MainActivity

The phone and the Chromecast must be on the same Wi-Fi / LAN. The device must
have accepted the "Allow USB debugging" prompt (`adb devices` should show
`device`, not `unauthorized`).

## Test

    ./gradlew :app:testDebugUnitTest

Protocol framing, the CastMessage protobuf, JSON payloads, CastConnection
(heartbeat + requestId correlation + the mdx type-exchange), receiver/media status
parsing, the discovery TXT parser, the yt-dlp stream selection, and the YouTube
URL/lounge logic are unit tested (48 tests). The TLS socket, jmDNS discovery,
on-device yt-dlp, the live YouTube lounge handshake, controller orchestration, and
Compose UI are validated by the manual hardware checklist below.

## Layout

- `protocol/` — `Framing`, `CastMessage` protobuf (Wire), `CastConnection`, `TlsCastChannel`
- `discovery/` — jmDNS discovery + TXT parsing
- `controller/` — high-level `CastController` and status models
- `resolver/` — `UrlClassifier`, `StreamSelector`, on-device `YtDlpStreamResolver`, `CastUrlUseCase`
- `youtube/` — `YouTubeUrl`, `YtLounge` (lounge HTTP client), `YouTubeException`
- `ui/` — Compose screens + `CastViewModel`

## Casting links

Pasting a URL into the control screen routes by type:

- **Direct media** (`.mp4`/`.m3u8`/…) → `LOAD` on the Default Media Receiver.
- **YouTube** (`youtube.com`/`youtu.be`) → launch YouTube's receiver (`233637DE`) and
  play the video id via the lounge protocol (mdx `screenId` → `get_lounge_token_batch`
  → `bc/bind` → `setPlaylist`). Plays **ad-free** when the Chromecast is linked to a
  Premium account (any anonymous sender inherits the screen's account context; a
  non-Premium authenticated sender — e.g. another phone — can temporarily displace it).
- **Any other page URL** → resolved on-device by the embedded **yt-dlp** to a stream
  URL, then `LOAD`ed on the Default Media Receiver. DRM titles fail with a clear message.

## Manual hardware checklist

Tick each by observing the phone and the TV (phone + Chromecast on the same LAN):

1. App launches → the target Chromecast appears in the list with its friendly name.
2. Tap it → the control screen shows current/idle state without error.
3. Paste a known-good MP4 URL (e.g. a Big Buck Bunny sample) → Cast → it plays on the TV.
4. Pause / Play toggles playback on the TV and the label updates.
5. +30s / -30s move the playback position.
6. Volume slider changes the TV volume; Mute / Unmute work.
7. "Stop app" ends the session on the TV.
8. From another sender (e.g. cast YouTube from a second phone), reopen Castmote →
   connect → confirm transport/volume reflect and control that session
   (the universal-remote check).

If discovery finds nothing: confirm the phone is on the same subnet as the cast,
and that the multicast permission is granted. jmDNS can be flaky on some Android
builds; `NsdManager` is the documented fallback (see the design spec).

## Known v1 limitations (worth checking on hardware)

- **Volume slider** sends a `SET_VOLUME` on every drag frame. If this floods the
  cast channel on real hardware, switch to local slider state +
  `onValueChangeFinished`.
- **`guessContentType`** is a coarse extension heuristic (`.aac` → `audio/mpeg`,
  all images → `image/jpeg`, default `video/mp4`). The Default Media Receiver is
  lenient, but refine the mime mapping if image/AAC casts misbehave.
- No screen mirroring, no local-file casting, no queue/playlist — single `LOAD`
  per cast (by design for v1).
- **Unofficial APIs (breakage risk).** Both the on-device **yt-dlp** extractors and
  the **YouTube lounge** API are unofficial and undocumented; sites/Google can change
  them and break casting. yt-dlp self-updates weekly to mitigate; the lounge protocol
  (`youtube/YtLounge`) is isolated so a format change is contained. yt-dlp's SVT
  extractor is known-broken upstream as of this writing.
- **YouTube ad-free is not in our control.** It depends on the Chromecast being linked
  to a Premium account (sign into / cast from the YouTube app once); our casts are
  anonymous and inherit that context. No Google credentials are handled by the app.
- No YouTube transport control (play/pause/seek), captions, or playlist in v1 — just
  "play this video".
