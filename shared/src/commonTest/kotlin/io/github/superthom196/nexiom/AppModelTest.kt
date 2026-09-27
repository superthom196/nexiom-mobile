package io.github.superthom196.nexiom

import io.github.superthom196.nexiom.api.Tile
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

    private val box = FoundBox("0123456789abcdef", "smiths")

    @Test
    fun aFreshPhoneStartsByFindingItsBox() {
        val model = AppModel(FakePlatform({ fakeClient { null } }), fakeClient { null })
        assertEquals(Screen.Find, model.screen)
    }

    @Test
    fun aBoxNotSetUpSaysSo() {
        val model = AppModel(FakePlatform({ fakeClient { null } }), fakeClient { null })
        model.choose(FoundBox("0123456789abcdef", ""))
        assertIs<Screen.NotSetUp>(model.screen)
    }

    @Test
    fun signingInKeepsTheTokenAndOpensTheDashboard() = test {
        val platform = FakePlatform({ fakeClient { null } })
        val client = fakeClient { request ->
            when (request.url.encodedPath) {
                "/api/box" -> json(Answers.box())
                "/api/signin" -> json(Answers.signIn())
                else -> null
            }
        }
        val model = AppModel(platform, client)
        model.choose(box)
        model.signIn("smiths", "secret")
        advanceUntilIdle()

        assertEquals(Screen.Home, model.screen)
        assertTrue(platform.store.values["session"]!!.contains("\"token\":\"tok\""))
        // The next start goes straight to the dashboard.
        assertEquals(Screen.Home, AppModel(platform, fakeClient { null }).screen)
    }

    @Test
    fun anotherBoxAtNexiomHomeIsRefusedBeforeSigningIn() = test {
        var signInCalled = false
        val client = fakeClient { request ->
            when (request.url.encodedPath) {
                "/api/box" -> json(Answers.box(id = "ffffffffffffffff"))
                else -> json(Answers.signIn()).also { signInCalled = true }
            }
        }
        val model = AppModel(FakePlatform({ client }), client)
        model.choose(box)
        model.signIn("smiths", "secret")
        advanceUntilIdle()

        assertEquals(SignInProblem.WrongBox, model.signInProblem)
        assertEquals(false, signInCalled)
    }

    @Test
    fun aWrongPasswordShowsTheBoxsWords() = test {
        val client = fakeClient { request ->
            when (request.url.encodedPath) {
                "/api/box" -> json(Answers.box())
                else -> json("""{"error": "That's not the household name and password."}""", HttpStatusCode.Unauthorized)
            }
        }
        val model = AppModel(FakePlatform({ client }), client)
        model.choose(box)
        model.signIn("smiths", "nope")
        advanceUntilIdle()

        assertEquals(SignInProblem.Box("That's not the household name and password."), model.signInProblem)
        assertIs<Screen.SignIn>(model.screen)
    }

    @Test
    fun aSignedOutPhoneIsAskedToSignInAgain() = test {
        val platform = FakePlatform({ fakeClient { null } })
        platform.store.values["session"] = """{"boxId":"0123456789abcdef","household":"smiths","token":"old"}"""
        val client = fakeClient { json("""{"error": "Sign in again."}""", HttpStatusCode.Unauthorized) }
        val model = AppModel(platform, client)
        model.follow()

        assertEquals(Screen.SignIn("0123456789abcdef", "smiths", signedOut = true), model.screen)
        assertTrue(!platform.store.values["session"]!!.contains("token"))
    }

    @Test
    fun anotherBoxAnsweringIsNotShownAsThisOne() = test {
        val platform = FakePlatform({ fakeClient { null } })
        platform.store.values["session"] = """{"boxId":"0123456789abcdef","household":"smiths","token":"tok"}"""
        val model = AppModel(platform, fakeClient { json(Answers.dashboard(boxId = "ffffffffffffffff")) })
        model.follow()

        assertEquals(HomeState.WrongBox, model.home)
    }

    @Test
    fun noAnswerShowsCantReach() = test {
        val platform = FakePlatform({ fakeClient { null } })
        platform.store.values["session"] = """{"boxId":"0123456789abcdef","household":"smiths","token":"tok"}"""
        val model = AppModel(platform, fakeClient { null })
        model.follow()

        assertEquals(HomeState.Unreachable, model.home)
    }

    @Test
    fun tilesOpenTheOfficialAppFirst() {
        val platform = FakePlatform({ fakeClient { null } })
        platform.store.values["session"] = """{"boxId":"0123456789abcdef","household":"smiths","token":"tok"}"""
        platform.officialApps = setOf("immich")
        val model = AppModel(platform, fakeClient { null })

        model.openTile(Tile("immich", "Photos", link = "http://photos.nexiom.home/"))
        assertEquals(listOf("app:immich"), platform.opened)
        assertNull(model.web)

        model.openTile(Tile("jellyfin", "Films", link = "http://films.nexiom.home/"))
        val page = model.web!!
        assertEquals("http://nexiom.home/api/web?next=http%3A%2F%2Ffilms.nexiom.home%2F", page.url)
        assertEquals("Bearer tok", page.headers["Authorization"])

        model.openTile(Tile("vaultwarden", "Passwords", link = "https://box.tail1234.ts.net/"))
        assertEquals(WebPage("Passwords", "https://box.tail1234.ts.net/", emptyMap()), model.web)
    }
}
