package io.github.superthom196.nexiom.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

/** Why a call to the box didn't give an answer. */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The token is no longer good: the phone was signed out, or the household password changed. */
class SignedOut : ApiException("Signed out")

/** The box has no household account yet. */
class NotSetUp(message: String) : ApiException(message)

/** The box answered with an error; the message is its own words. */
class Refused(val status: Int, message: String) : ApiException(message)

/** No answer: another network, Tailscale off, or the box off. */
class Unreachable(cause: Throwable) : ApiException(cause.message ?: "No answer", cause)

internal val NexiomJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

fun nexiomHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json(NexiomJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 6_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
}

/**
 * The box's phone API. Every box answers at nexiom.home, at home through its DNS and away
 * through the Tailscale app, so the app checks which box answered by its id.
 */
class NexiomApi(private val client: HttpClient, private val base: String = BASE_URL) {

    suspend fun box(): BoxInfo = request(authed = false) { client.get("$base/api/box") }

    /** A 401 here is a wrong household name or password, not a signed-out phone. */
    suspend fun signIn(household: String, password: String, phone: String): SignInResponse =
        request(authed = false) {
            client.post("$base/api/signin") {
                contentType(ContentType.Application.Json)
                setBody(SignInRequest(household, password, phone))
            }
        }

    suspend fun signOut(token: String) {
        request<JsonObject>(authed = true) { client.post("$base/api/signout") { bearerAuth(token) } }
    }

    suspend fun dashboard(token: String): Dashboard =
        request(authed = true) { client.get("$base/api/dashboard") { bearerAuth(token) } }

    /**
     * The live status while the dashboard is on screen. The box sends only changes, so the
     * stream can be quiet for a long time; it ends with [SignedOut] when the phone is signed out.
     */
    fun events(token: String): Flow<Live> = channelFlow {
        try {
            client.prepareGet("$base/api/events") {
                bearerAuth(token)
                timeout {
                    requestTimeoutMillis = Long.MAX_VALUE
                    socketTimeoutMillis = Long.MAX_VALUE
                }
            }.execute { response ->
                when (response.status.value) {
                    200 -> {}
                    401 -> throw SignedOut()
                    else -> throw Refused(response.status.value, errorText(response))
                }
                val body = response.bodyAsChannel()
                val events = EventReader()
                while (true) {
                    val line = body.readUTF8Line() ?: break
                    val event = events.line(line) ?: continue
                    if (event.first == "status") send(NexiomJson.decodeFromString<Live>(event.second))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            throw e
        } catch (e: SerializationException) {
            throw Refused(200, e.message ?: "Unexpected answer")
        } catch (e: Exception) {
            throw Unreachable(e)
        }
    }

    /** `/api/web` sets this phone's household cookie, then goes to `next` (a path or a nexiom.home page). */
    fun webUrl(next: String): String = "$base/api/web?next=${next.encodeURLParameter()}"

    private suspend inline fun <reified T> request(authed: Boolean, send: () -> HttpResponse): T {
        val response = try {
            send()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Unreachable(e)
        }
        when (response.status.value) {
            in 200..299 -> return try {
                response.body<T>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw Refused(response.status.value, e.message ?: "Unexpected answer")
            }
            401 -> if (authed) throw SignedOut()
            409 -> throw NotSetUp(errorText(response))
        }
        throw Refused(response.status.value, errorText(response))
    }

    private suspend fun errorText(response: HttpResponse): String {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val error = runCatching { NexiomJson.decodeFromString<ApiError>(text).error }.getOrDefault("")
        return error.ifBlank { "The box answered ${response.status.value}" }
    }

    companion object {
        const val HOST = "nexiom.home"
        const val BASE_URL = "http://$HOST"

        /** A path on the box, or a page on nexiom.home or one of its subdomains. */
        fun isNexiomAddress(link: String): Boolean {
            if (link.startsWith("/") && !link.startsWith("//")) return true
            val host = runCatching { Url(link).host.lowercase() }.getOrDefault("")
            return host == HOST || host.endsWith(".$HOST")
        }
    }
}

/**
 * Server-sent events, a line at a time: `event:` and `data:` fields, a blank line ending each
 * event, `:` starting a comment.
 */
internal class EventReader {
    private var name = "message"
    private val data = StringBuilder()

    /** The (name, data) of the event this line ends, if it ends one. */
    fun line(line: String): Pair<String, String>? {
        if (line.isEmpty()) {
            val event = if (data.isEmpty()) null else name to data.toString()
            name = "message"
            data.clear()
            return event
        }
        if (line.startsWith(":")) return null
        val field = line.substringBefore(':')
        val value = line.substringAfter(':', "").removePrefix(" ")
        when (field) {
            "event" -> name = value
            "data" -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }
        }
        return null
    }
}
