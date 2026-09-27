package io.github.superthom196.nexiom

import io.github.superthom196.nexiom.api.HomeAssistant
import io.github.superthom196.nexiom.api.NexiomApi
import io.github.superthom196.nexiom.api.Scene
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val STATES = """[
    {"entity_id": "light.hall", "state": "on", "attributes": {"friendly_name": "Hall"}},
    {"entity_id": "scene.movie", "state": "unknown", "attributes": {"friendly_name": "Movie night"}},
    {"entity_id": "scene.evening", "state": "unknown", "attributes": {"friendly_name": "Evening"}},
    {"entity_id": "scene.bare", "state": "unknown", "attributes": {}}
]"""

/** A box with Home Assistant at ha.nexiom.home, which accepts only `goodToken`. */
private class HaBox(var goodToken: String = "ha-2") {
    val tokensMade = mutableListOf<String>()
    val ran = mutableListOf<String>()

    fun route(request: HttpRequestData) = when {
        request.url.host == "nexiom.home" && request.url.encodedPath == "/api/homeassistant" ->
            json("""{"url": "http://ha.nexiom.home", "token": "$goodToken"}""").also { tokensMade += goodToken }
        request.url.host == "nexiom.home" && request.url.encodedPath == "/api/dashboard" -> json(Answers.dashboardWithHomeAssistant())
        request.url.host == "nexiom.home" && request.url.encodedPath == "/api/phone-setup" ->
            json("""{"apps": [{"id": "tailscale", "name": "Tailscale"}, {"id": "homeassistant", "name": "Home Assistant", "server": "http://ha.nexiom.home", "login": "smiths"}]}""")
        request.url.host == "ha.nexiom.home" && request.headers[HttpHeaders.Authorization] != "Bearer $goodToken" ->
            json("""{"message": "Invalid token"}""", HttpStatusCode.Unauthorized)
        request.url.encodedPath == "/api/states" -> json(STATES)
        request.url.encodedPath == "/api/services/scene/turn_on" ->
            json("[]").also { ran += (request.body as TextContent).text }
        else -> null
    }
}

class HomeAssistantTest {
    @Test
    fun scenesAreTheSceneEntitiesByName() = runTest {
        val box = HaBox()
        val client = fakeClient(box::route)
        val scenes = HomeAssistant(client, NexiomApi(client), MemoryStore()).scenes("tok")

        assertEquals(
            listOf(Scene("scene.bare", "bare"), Scene("scene.evening", "Evening"), Scene("scene.movie", "Movie night")),
            scenes,
        )
    }

    @Test
    fun theTokenIsKeptAndRenewedOnlyWhenRefused() = runTest {
        val box = HaBox()
        val client = fakeClient(box::route)
        val store = MemoryStore()
        val ha = HomeAssistant(client, NexiomApi(client), store)

        ha.scenes("tok")
        ha.run("tok", "scene.evening")
        assertEquals(listOf("ha-2"), box.tokensMade)
        assertEquals(listOf("""{"entity_id":"scene.evening"}"""), box.ran)

        box.goodToken = "ha-3" // the household password changed and the box swept the old one
        ha.scenes("tok")
        assertEquals(listOf("ha-2", "ha-3"), box.tokensMade)
        assertTrue(store.values["homeassistant"]!!.contains("ha-3"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardExtrasTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theDashboardBringsScenesAndTheSetupList() = runTest(dispatcher) {
        val client = fakeClient(HaBox()::route)
        val platform = FakePlatform({ client })
        platform.store.values["session"] = """{"boxId":"0123456789abcdef","household":"smiths","token":"tok"}"""
        platform.officialApps = setOf("tailscale")
        val model = AppModel(platform, client)

        val following = launch { model.follow() }
        advanceTimeBy(1_000)
        following.cancel()
        advanceUntilIdle()

        assertEquals(listOf("tailscale", "homeassistant"), model.setupApps.map { it.id })
        assertEquals(setOf("tailscale"), model.installed)
        assertEquals(3, (model.scenes as ScenesState.Ready).all.size)
    }

    @Test
    fun favouritesAreKeptInOrderOnThePhone() {
        val platform = FakePlatform({ fakeClient { null } })
        val model = AppModel(platform, fakeClient { null })
        model.setFavourite("scene.movie", true)
        model.setFavourite("scene.evening", true)
        model.moveFavourite(1, 0)

        assertEquals(listOf("scene.evening", "scene.movie"), model.favourites)
        assertEquals(listOf("scene.evening", "scene.movie"), AppModel(platform, fakeClient { null }).favourites)

        model.setFavourite("scene.evening", false)
        assertEquals(listOf("scene.movie"), model.favourites)
    }

    @Test
    fun runningASceneShowsItWorked() = runTest(dispatcher) {
        val box = HaBox()
        val client = fakeClient(box::route)
        val platform = FakePlatform({ client })
        platform.store.values["session"] = """{"boxId":"0123456789abcdef","household":"smiths","token":"tok"}"""
        val model = AppModel(platform, client)

        model.runScene(Scene("scene.evening", "Evening"))
        dispatcher.scheduler.runCurrent()
        assertEquals(SceneRun.Done, model.sceneRuns["scene.evening"])
        advanceUntilIdle()
        assertEquals(null, model.sceneRuns["scene.evening"])
    }

    @Test
    fun signingOutForgetsHomeAssistantsToken() {
        val platform = FakePlatform({ fakeClient { null } })
        platform.store.values["session"] = """{"boxId":"0123456789abcdef","household":"smiths","token":"tok"}"""
        platform.store.values["homeassistant"] = """{"url":"http://ha.nexiom.home","token":"ha-2"}"""
        platform.store.values["scenes"] = """["scene.evening"]"""
        AppModel(platform, fakeClient { null }).signOut()

        assertEquals(null, platform.store.values["homeassistant"])
        assertEquals(null, platform.store.values["scenes"])
    }
}
