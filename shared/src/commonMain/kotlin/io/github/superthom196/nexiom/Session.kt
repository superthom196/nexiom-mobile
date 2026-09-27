package io.github.superthom196.nexiom

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The box this phone belongs to, and its token once signed in. */
@Serializable
data class Session(
    val boxId: String,
    val household: String,
    val token: String? = null,
    val phoneId: String? = null,
)

class SessionStore(private val store: KeyValueStore) {
    fun load(): Session? =
        store.get(KEY)?.let { runCatching { json.decodeFromString<Session>(it) }.getOrNull() }

    fun save(session: Session?) {
        store.set(KEY, session?.let { json.encodeToString(it) })
    }

    private companion object {
        const val KEY = "session"
        val json = Json { ignoreUnknownKeys = true }
    }
}
