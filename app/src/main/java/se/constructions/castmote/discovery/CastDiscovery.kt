package se.constructions.castmote.discovery

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import java.net.InetAddress

/** Discovers Chromecasts via mDNS (`_googlecast._tcp`) and emits the live device set. */
class CastDiscovery(private val context: Context) {

    private val serviceType = "_googlecast._tcp.local."

    fun devices(): Flow<List<CastDevice>> = callbackFlow {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("castmote").apply {
            setReferenceCounted(true)
            acquire()
        }

        // Keyed by the mDNS service instance name (stable across add/resolve/remove events).
        val found = LinkedHashMap<String, CastDevice>()

        val jmdns: JmDNS
        try {
            val ip = wifi.connectionInfo.ipAddress
            val address = InetAddress.getByAddress(
                byteArrayOf(ip.toByte(), (ip shr 8).toByte(), (ip shr 16).toByte(), (ip shr 24).toByte()),
            )
            jmdns = JmDNS.create(address)
        } catch (t: Throwable) {
            lock.release()
            close(t)
            return@callbackFlow
        }

        fun toDevice(info: ServiceInfo): CastDevice? {
            val host = info.inet4Addresses.firstOrNull()?.hostAddress ?: return null
            val txt = info.propertyNames.toList().associateWith { (info.getPropertyString(it) ?: "") }
            return TxtRecords.parse(txt, host, if (info.port > 0) info.port else 8009)
        }

        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns.requestServiceInfo(event.type, event.name, 1000)
            }

            override fun serviceResolved(event: ServiceEvent) {
                toDevice(event.info)?.let {
                    found[event.name] = it
                    trySend(found.values.toList())
                }
            }

            override fun serviceRemoved(event: ServiceEvent) {
                if (found.remove(event.name) != null) {
                    trySend(found.values.toList())
                }
            }
        }

        jmdns.addServiceListener(serviceType, listener)

        awaitClose {
            jmdns.removeServiceListener(serviceType, listener)
            runCatching { jmdns.close() }
            lock.release()
        }
        // jmDNS opens sockets and does a blocking reverse-DNS lookup in JmDNS.create();
        // run the whole producer off the main thread (the collector is on Dispatchers.Main).
    }.flowOn(Dispatchers.IO)
}
