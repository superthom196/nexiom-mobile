package io.github.superthom196.nexiom

import io.github.superthom196.nexiom.api.nexiomHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A box's answers, as Nexiom Server gives them (docs/architecture.md "Phone app"). */
object Answers {
    fun box(id: String = "0123456789abcdef", household: String = "smiths", ready: Boolean = true) =
        """{"id": "$id", "household": "$household", "ready": $ready}"""

    fun signIn(token: String = "tok", boxId: String = "0123456789abcdef") =
        """{"token": "$token", "phone": {"id": "a1b2c3d4", "name": "Pixel 8"}, "box": ${box(boxId)}}"""

    const val LIVE = """
        "status": {"services": {"running": 11, "total": 12}, "storage": 42, "blocked": 12345},
        "stats": {
            "cpu": 12.5,
            "ram": {"used": 3100000000, "total": 7800000000, "detail": "3.1 GB / 8 GB"},
            "disk": {"used": 12000000000, "total": 30000000000, "detail": "12 GB / 32 GB"},
            "storage": null
        },
        "attention": [{"id": "service:immich", "text": "Immich isn't running", "link": "/settings/apps"},
                      {"id": "disk", "text": "The system disk is 91% full", "link": null}]
    """

    fun dashboard(boxId: String = "0123456789abcdef") = """{
        "box": ${box(boxId)},
        "sections": [
            {"key": "entertainment", "title": "Entertainment", "tiles": [
                {"id": "music-assistant", "name": "Music", "description": "Your music", "link": "http://music.nexiom.home/", "pillar": "music"},
                {"id": "crate", "name": "CRATE", "description": "Needs a music drive", "link": null, "pillar": "music"}
            ]},
            {"key": "more", "title": "More", "tiles": [
                {"id": "vaultwarden", "name": "Passwords", "description": "Set it up in Settings", "link": null, "pillar": "more", "new_tab": true}
            ]}
        ],
        $LIVE
    }"""
}

fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    Triple(status, body, "application/json")

/**
 * A client whose answers come from `route`; `null` means no answer at all. It answers on the
 * caller's thread, so tests run on the test clock.
 */
fun fakeClient(route: (HttpRequestData) -> Triple<HttpStatusCode, String, String>?): HttpClient =
    nexiomHttpClient(
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = Dispatchers.Unconfined
                addHandler { request ->
                    val answer = route(request) ?: throw kotlinx.io.IOException("nexiom.home: no route to host")
                    respond(answer.second, answer.first, headersOf(HttpHeaders.ContentType, answer.third))
                }
            },
        ),
    )

class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun set(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

class FakePlatform(
    private val client: () -> HttpClient,
    private val found: Flow<List<FoundBox>> = flowOf(emptyList()),
) : Platform {
    override val deviceName = "Pixel 8"
    override val store = MemoryStore()
    val opened = mutableListOf<String>()
    var officialApps = setOf<String>()

    override fun newHttpClient() = client()
    override fun discover() = found
    override fun openOfficialApp(serviceId: String) = (serviceId in officialApps).also { if (it) opened += "app:$serviceId" }
    override fun openInBrowser(url: String) {
        opened += url
    }
}
