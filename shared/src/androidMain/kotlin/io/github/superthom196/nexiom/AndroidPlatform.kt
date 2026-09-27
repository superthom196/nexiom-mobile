package io.github.superthom196.nexiom

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import io.github.superthom196.nexiom.api.nexiomHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidPlatform(private val context: Context) : Platform {

    override val deviceName: String
        get() = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    override val store: KeyValueStore = object : KeyValueStore {
        private val prefs = context.getSharedPreferences("nexiom", Context.MODE_PRIVATE)
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun set(key: String, value: String?) {
            prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        }
    }

    override fun newHttpClient(): HttpClient = nexiomHttpClient(OkHttp.create())

    /**
     * Boxes advertising `_nexiom._tcp`, named by their TXT `household` and told apart by `id`.
     * Services are resolved one at a time: before Android 14 a second resolve fails while one runs.
     */
    override fun discover(): Flow<List<FoundBox>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val boxes = linkedMapOf<String, FoundBox>() // by service name
        val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        fun publish() {
            trySend(boxes.values.distinctBy { it.id })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                toResolve.trySend(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                launch {
                    if (boxes.remove(info.serviceName) != null) publish()
                }
            }
        }

        launch {
            for (info in toResolve) {
                val resolved = nsd.resolve(info) ?: continue
                val attrs = resolved.attributes
                val id = attrs["id"]?.decodeToString()?.takeIf { it.isNotBlank() } ?: continue
                val household = attrs["household"]?.decodeToString().orEmpty()
                boxes[info.serviceName] = FoundBox(id, household)
                publish()
            }
        }

        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            toResolve.close()
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION") // registerServiceInfoCallback is Android 14+; this app starts at 12.
    private suspend fun NsdManager.resolve(info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { cont ->
            resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo)
                    }
                },
            )
        }

    override fun openOfficialApp(serviceId: String): Boolean {
        val intent = OFFICIAL_APPS[serviceId].orEmpty()
            .firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
            ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    override fun openInBrowser(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No browser on the phone: nothing to open it with.
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_nexiom._tcp"

        /** A tile's service id to the official app that opens it, in order of preference. */
        val OFFICIAL_APPS = mapOf(
            "immich" to listOf("app.alextran.immich"),
            "vaultwarden" to listOf("com.x8bit.bitwarden"),
            "homeassistant" to listOf("io.homeassistant.companion.android", "io.homeassistant.companion.android.minimal"),
            "music-assistant" to listOf("io.music_assistant.client"),
        )
    }
}
