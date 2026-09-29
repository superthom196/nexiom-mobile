package io.github.superthom196.nexiom.api

import io.github.superthom196.nexiom.Answers
import io.github.superthom196.nexiom.fakeClient
import io.github.superthom196.nexiom.json
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NexiomApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(route: (HttpRequestData) -> Triple<HttpStatusCode, String, String>?) =
        NexiomApi(fakeClient { request -> requests += request; route(request) })

    @Test
    fun dashboardReadsTheBoxsAnswer() = runTest {
        val dashboard = api { json(Answers.dashboard()) }.dashboard("tok")

        assertEquals("smiths", dashboard.box.household)
        assertEquals(listOf("entertainment", "more"), dashboard.sections.map { it.key })
        val crate = dashboard.sections[0].tiles[1]
        assertNull(crate.link)
        assertEquals("Needs a music drive", crate.description)
        assertEquals(ServiceCounts(11, 12), dashboard.status.services)
        assertEquals(12345L, dashboard.status.blocked)
        assertEquals(12.5, dashboard.stats.cpu)
        assertEquals("3.1 GB / 8 GB", dashboard.stats.ram?.detail)
        assertNull(dashboard.stats.storage)
        assertNull(dashboard.attention[1].link)
    }

    @Test
    fun diskTextIsInWholeGigabytes() {
        assertEquals("118 GB / 256 GB", Ring(1, 2, "118.3 GB / 256.1 GB").wholeGbDetail)
        assertEquals("12 GB / 32 GB", Ring(1, 2, "12 GB / 32 GB").wholeGbDetail)
        assertEquals("900 MB / 2 GB", Ring(1, 2, "900 MB / 1.5 GB").wholeGbDetail)
    }

    @Test
    fun callsCarryTheToken() = runTest {
        api { json(Answers.dashboard()) }.dashboard("tok")

        assertEquals("http://nexiom.home/api/dashboard", requests.single().url.toString())
        assertEquals("Bearer tok", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun aRefusedTokenIsSignedOut() = runTest {
        assertFailsWith<SignedOut> {
            api { json("""{"error": "Sign in again."}""", HttpStatusCode.Unauthorized) }.dashboard("old")
        }
    }

    @Test
    fun aWrongPasswordIsTheBoxsOwnWordsNotSignedOut() = runTest {
        val e = assertFailsWith<Refused> {
            api { json("""{"error": "That's not the household name and password."}""", HttpStatusCode.Unauthorized) }
                .signIn("smiths", "nope", "Pixel 8")
        }
        assertEquals("That's not the household name and password.", e.message)
    }

    @Test
    fun signInSendsNamePasswordAndPhone() = runTest {
        val result = api { json(Answers.signIn()) }.signIn("smiths", "secret", "Pixel 8")

        assertEquals("tok", result.token)
        val body = (requests.single().body as TextContent).text
        assertEquals("""{"household":"smiths","password":"secret","phone":"Pixel 8"}""", body)
    }

    @Test
    fun aBoxWithoutAHouseholdIsNotSetUp() = runTest {
        assertFailsWith<NotSetUp> {
            api { json("""{"error": "This Nexiom isn't set up yet."}""", HttpStatusCode.Conflict) }
                .signIn("smiths", "secret", "Pixel 8")
        }
    }

    @Test
    fun noAnswerIsUnreachable() = runTest {
        assertFailsWith<Unreachable> { api { null }.box() }
    }

    @Test
    fun eventsGiveTheLiveStatus() = runTest {
        val stream = "event: status\ndata: {${Answers.LIVE.replace("\n", " ")}}\n\n"
        val live = api { Triple(HttpStatusCode.OK, stream, "text/event-stream") }.events("tok").toList()

        assertEquals(1, live.size)
        assertEquals(2, live[0].attention.size)
        assertEquals(42, live[0].status.storage)
    }

    @Test
    fun eventsEndSignedOutOnA401() = runTest {
        assertFailsWith<SignedOut> {
            api { json("""{"error": "Sign in again."}""", HttpStatusCode.Unauthorized) }.events("old").toList()
        }
    }

    @Test
    fun webPagesGoThroughApiWeb() {
        val api = NexiomApi(fakeClient { null })
        assertEquals("http://nexiom.home/api/web?next=%2Fsettings", api.webUrl("/settings"))
        assertEquals(
            "http://nexiom.home/api/web?next=http%3A%2F%2Fphotos.nexiom.home%2F",
            api.webUrl("http://photos.nexiom.home/"),
        )
    }

    @Test
    fun nexiomAddresses() {
        assertTrue(NexiomApi.isNexiomAddress("/settings"))
        assertTrue(NexiomApi.isNexiomAddress("http://nexiom.home/"))
        assertTrue(NexiomApi.isNexiomAddress("http://photos.nexiom.home/albums"))
        assertFalse(NexiomApi.isNexiomAddress("//evil.example/"))
        assertFalse(NexiomApi.isNexiomAddress("https://box.tail1234.ts.net/"))
        assertFalse(NexiomApi.isNexiomAddress("http://nexiom.home.evil.example/"))
    }
}

class EventReaderTest {
    @Test
    fun readsNamedEventsAndSkipsComments() {
        val reader = EventReader()
        val lines = listOf(": hello", "event: status", "data: {\"a\":", "data: 1}", "", "data: plain", "")
        val events = lines.mapNotNull(reader::line)
        assertEquals(listOf("status" to "{\"a\":\n1}", "message" to "plain"), events)
    }
}
