package se.constructions.castmote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.constructions.castmote.ui.BrowserScreen
import se.constructions.castmote.ui.CastViewModel
import se.constructions.castmote.ui.ControlScreen
import se.constructions.castmote.ui.DeviceListScreen
import se.constructions.castmote.ui.YouTubeLoginScreen
import se.constructions.castmote.ui.theme.CastmoteTheme

class MainActivity : ComponentActivity() {
    // Activity-scoped so onCreate/onNewIntent can feed incoming links into the same instance the UI uses.
    private val vm: CastViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        IncomingLink.urlFrom(intent.action, intent.dataString, intent.getStringExtra(Intent.EXTRA_TEXT))
            ?.let { vm.onIncomingUrl(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lock-screen media control posts a notification — ask once on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            CastmoteTheme {
                Surface {
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reconnectIfNeeded() }
                    val devices by vm.devices.collectAsStateWithLifecycle()
                    val connected by vm.connected.collectAsStateWithLifecycle()
                    val receiver by vm.receiver.collectAsStateWithLifecycle()
                    val media by vm.media.collectAsStateWithLifecycle()
                    val castStatus by vm.castStatus.collectAsStateWithLifecycle()
                    val online by vm.online.collectAsStateWithLifecycle()
                    val youTubeSignedIn by vm.youTubeSignedIn.collectAsStateWithLifecycle()
                    val showYouTubeLogin by vm.showYouTubeLogin.collectAsStateWithLifecycle()
                    val history by vm.history.collectAsStateWithLifecycle()
                    val browserCanCast by vm.browserCanCast.collectAsStateWithLifecycle()
                    val browserStreams by vm.browserStreams.collectAsStateWithLifecycle()
                    val pendingUrl by vm.pendingUrl.collectAsStateWithLifecycle()
                    var tab by remember { mutableIntStateOf(0) } // 0 = Remote, 1 = Browser

                    // An incoming link prefills the Remote tab's cast field — jump there so it's visible.
                    LaunchedEffect(pendingUrl) { if (pendingUrl != null) tab = 0 }

                    val device = connected

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = tab == 0, onClick = { tab = 0 },
                                    icon = { Icon(Icons.Default.SettingsRemote, contentDescription = null) },
                                    label = { Text("Remote") },
                                )
                                NavigationBarItem(
                                    selected = tab == 1, onClick = { tab = 1 },
                                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                                    label = { Text("Browser") },
                                )
                            }
                        },
                    ) { pad ->
                        Box(Modifier.padding(pad)) {
                            // BrowserScreen stays composed so its WebView (and the page you're on)
                            // survives switching to the Remote tab. The Remote content is drawn on
                            // top as an opaque Surface when the Remote tab is selected.
                            BrowserScreen(
                                sniffer = vm.streamSniffer,
                                canCast = browserCanCast,
                                streams = browserStreams,
                                devices = devices,
                                onPage = vm::onBrowserPage,
                                onCast = vm::castFromBrowser,
                            )
                            if (tab == 0) {
                                Surface(Modifier.fillMaxSize()) {
                                if (showYouTubeLogin) {
                                    YouTubeLoginScreen(
                                        onSignedIn = vm::saveYouTubeCookies,
                                        onCancel = vm::closeYouTubeLogin,
                                    )
                                } else if (device == null) {
                                    DeviceListScreen(
                                        devices = devices,
                                        onSelect = vm::connect,
                                        onConnectManual = vm::connectManual,
                                        onRemoveManual = vm::removeManual,
                                    )
                                } else {
                                    ControlScreen(
                                        device = device,
                                        receiver = receiver,
                                        media = media,
                                        castStatus = castStatus,
                                        youTubeSignedIn = youTubeSignedIn,
                                        online = online,
                                        history = history,
                                        onBack = vm::disconnect,
                                        onReconnect = vm::reconnect,
                                        onPlayPause = vm::playPause,
                                        onStopMedia = vm::stopMedia,
                                        onSeek = vm::seek,
                                        onVolume = vm::setVolume,
                                        onMuted = vm::setMuted,
                                        onStopApp = vm::stopApp,
                                        onCast = vm::castUrl,
                                        onCastEntry = vm::castEntry,
                                        onClearHistory = vm::clearHistory,
                                        onYouTubeSignIn = vm::openYouTubeLogin,
                                        onYouTubeSignOut = vm::signOutYouTube,
                                        prefillUrl = pendingUrl,
                                        onPrefillConsumed = vm::consumePendingUrl,
                                    )
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
        handleIntent(intent) // a link that cold-started the app
    }
}
