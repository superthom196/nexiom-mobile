package io.github.superthom196.nexiom

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow

/** A box advertising `_nexiom._tcp` on this network. `household` is empty until it's set up. */
data class FoundBox(val id: String, val household: String) {
    val ready: Boolean get() = household.isNotBlank()
}

/** Small string storage private to the app. */
interface KeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

/** What only the phone's own platform can do. */
interface Platform {
    /** The name the phone signs in under, shown in Settings > Phones. */
    val deviceName: String

    val store: KeyValueStore

    /** A client for the box's API, on the platform's own HTTP engine. */
    fun newHttpClient(): HttpClient

    /** The boxes on this network as they come and go, over mDNS. */
    fun discover(): Flow<List<FoundBox>>

    /** Opens a tile's official app when the phone has it; false when it doesn't. */
    fun openOfficialApp(serviceId: String): Boolean

    /** Whether the official app of a Set up this phone step (`tailscale`, `bitwarden`, …) is on the phone. */
    fun appInstalled(appId: String): Boolean

    /** Opens that step's app; false when the phone doesn't have it. */
    fun openApp(appId: String): Boolean

    /** That step's app in the phone's app store. */
    fun openStore(appId: String)

    fun openInBrowser(url: String)

    /** Puts `text` on the clipboard. */
    fun copy(text: String)
}
