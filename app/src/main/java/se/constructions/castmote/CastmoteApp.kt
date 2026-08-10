package se.constructions.castmote

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.constructions.castmote.resolver.YtDlpInitializer
import se.constructions.castmote.resolver.YtDlpUpdater

class CastmoteApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { YtDlpInitializer.ensureInitialized(this@CastmoteApp) }
            runCatching { YtDlpUpdater.maybeUpdate(this@CastmoteApp, System.currentTimeMillis()) }
        }
    }
}
