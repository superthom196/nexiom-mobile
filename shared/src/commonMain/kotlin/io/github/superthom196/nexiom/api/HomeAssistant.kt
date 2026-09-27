package io.github.superthom196.nexiom.api

import io.github.superthom196.nexiom.KeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

/** A Home Assistant scene: `id` is its entity id (`scene.evening`). */
data class Scene(val id: String, val name: String)

@Serializable
internal data class HaState(
    @SerialName("entity_id") val entityId: String,
    val attributes: HaAttributes = HaAttributes(),
)

@Serializable
internal data class HaAttributes(@SerialName("friendly_name") val friendlyName: String? = null)

/**
 * Home Assistant's own REST API, with a long-lived token the box makes for this phone. The token
 * is kept on the phone and asked for again only when Home Assistant refuses it (the phone was
 * signed out and swept, or the household password changed).
 */
class HomeAssistant(
    private val client: HttpClient,
    private val nexiom: NexiomApi,
    private val store: KeyValueStore,
) {
    /** Every scene, by name. */
    suspend fun scenes(token: String): List<Scene> = withAccess(token) { access ->
        val response = call { client.get("${access.url}/api/states") { bearerAuth(access.token) } }
        val states = try {
            response.body<List<HaState>>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Refused(response.status.value, e.message ?: "Unexpected answer from Home Assistant")
        }
        states
            .filter { it.entityId.startsWith("scene.") }
            .map { Scene(it.entityId, it.attributes.friendlyName ?: it.entityId.removePrefix("scene.")) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun run(token: String, sceneId: String) {
        withAccess(token) { access ->
            call {
                client.post("${access.url}/api/services/scene/turn_on") {
                    bearerAuth(access.token)
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("entity_id", sceneId) })
                }
            }
        }
    }

    /** Forgets the token, as when the phone signs out; the box sweeps it from Home Assistant. */
    fun forget() = store.set(KEY, null)

    private suspend fun <T> withAccess(token: String, block: suspend (HomeAssistantAccess) -> T): T {
        saved()?.let { access ->
            try {
                return block(access)
            } catch (_: TokenRefused) {
                forget()
            }
        }
        val fresh = nexiom.homeAssistant(token)
        store.set(KEY, NexiomJson.encodeToString(fresh))
        return try {
            block(fresh)
        } catch (_: TokenRefused) {
            throw Refused(401, "Home Assistant refused this phone's token")
        }
    }

    private fun saved(): HomeAssistantAccess? =
        store.get(KEY)?.let { runCatching { NexiomJson.decodeFromString<HomeAssistantAccess>(it) }.getOrNull() }

    private suspend fun call(send: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            send()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Unreachable(e)
        }
        return when (response.status.value) {
            in 200..299 -> response
            401, 403 -> throw TokenRefused()
            else -> throw Refused(response.status.value, "Home Assistant answered ${response.status.value}")
        }
    }

    private class TokenRefused : Exception()

    private companion object {
        const val KEY = "homeassistant"
    }
}
