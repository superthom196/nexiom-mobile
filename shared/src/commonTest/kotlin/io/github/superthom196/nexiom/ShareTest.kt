package io.github.superthom196.nexiom

import io.github.superthom196.nexiom.api.NexiomApi
import io.github.superthom196.nexiom.api.SignedOut
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

class NamesTest {
    @Test
    fun numbersGoBeforeTheExtension() {
        assertEquals("photo.jpg", numbered("photo.jpg", 1))
        assertEquals("photo (2).jpg", numbered("photo.jpg", 2))
        assertEquals("archive.tar (3).gz", numbered("archive.tar.gz", 3))
        assertEquals("README (2)", numbered("README", 2))
    }

    @Test
    fun aFreeNameSkipsTakenOnes() {
        assertEquals("a.pdf", freeName("a.pdf", setOf("b.pdf")))
        assertEquals("a (3).pdf", freeName("a.pdf", setOf("a.pdf", "a (2).pdf")))
    }

    @Test
    fun caseDoesntMakeANameFree() {
        // exFAT and NTFS drives: Scan.pdf and scan.pdf are the same file.
        assertEquals("Scan (3).pdf", freeName("Scan.pdf", setOf("scan.pdf", "SCAN (2).PDF")))
    }

    @Test
    fun namesAreMadeSafeForTheBox() {
        assertEquals("Scan_ 1_2.pdf", safeName("Scan: 1/2.pdf"))
        assertEquals("hidden", safeName(".hidden."))
        assertEquals("Shared file", safeName("..."))
        val long = safeName("x".repeat(300) + ".jpeg")
        assertTrue(long.length <= 120 && long.endsWith(".jpeg"))
    }
}

class UploaderTest {
    private val to = Destination("/dev/sdb1", "Documents", "Documents")

    private fun file(name: String) = Outgoing(name, 3) { ByteReadChannel("abc") }

    @Test
    fun aTakenNameIsKeptAndTheNewFileNumbered() = runTest {
        val sentAs = mutableListOf<String>()
        val api = NexiomApi(
            fakeClient { request ->
                when (request.url.encodedPath) {
                    "/api/folders" -> json("""{"device": "/dev/sdb1", "drive": "Big", "path": "Documents", "files": ["scan.pdf"]}""")
                    "/api/upload" -> json("""{"saved": true}""").also { sentAs += request.url.parameters["name"]!! }
                    else -> null
                }
            },
        )
        val result = Uploader(api).send("tok", to, listOf(file("scan.pdf"), file("scan.pdf")))

        assertEquals(listOf("scan (2).pdf", "scan (3).pdf"), sentAs)
        assertEquals(sentAs, result.sent)
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun aFolderOfTheSameNameIsTakenToo() = runTest {
        val sentAs = mutableListOf<String>()
        val api = NexiomApi(
            fakeClient { request ->
                when (request.url.encodedPath) {
                    "/api/folders" -> json(
                        """{"device": "/dev/sdb1", "drive": "Big", "path": "Documents", "files": [],
                            "folders": [{"name": "Taxes", "path": "Documents/Taxes"}]}""",
                    )
                    else -> json("""{"saved": true}""").also { sentAs += request.url.parameters["name"]!! }
                }
            },
        )
        Uploader(api).send("tok", to, listOf(file("taxes")))

        assertEquals(listOf("taxes (2)"), sentAs)
    }

    @Test
    fun aFileTheBoxSkipsIsSentUnderTheNextNumber() = runTest {
        val tried = mutableListOf<String>()
        val api = NexiomApi(
            fakeClient { request ->
                when (request.url.encodedPath) {
                    // An older box doesn't list the folder's files.
                    "/api/folders" -> json("""{"device": "/dev/sdb1", "drive": "Big", "path": "Documents"}""")
                    else -> {
                        val name = request.url.parameters["name"]!!
                        tried += name
                        json(if (name == "scan.pdf") """{"skipped": true}""" else """{"saved": true}""")
                    }
                }
            },
        )
        val result = Uploader(api).send("tok", to, listOf(file("scan.pdf")))

        assertEquals(listOf("scan.pdf", "scan (2).pdf"), tried)
        assertEquals(listOf("scan (2).pdf"), result.sent)
    }

    @Test
    fun aSignedOutPhoneStopsTheBatch() = runTest {
        var uploads = 0
        val api = NexiomApi(
            fakeClient { request ->
                when (request.url.encodedPath) {
                    "/api/folders" -> json("""{"device": "/dev/sdb1", "drive": "Big", "path": "Documents", "files": []}""")
                    else -> json("""{"error": "Sign in again."}""", HttpStatusCode.Unauthorized).also { uploads++ }
                }
            },
        )
        val result = Uploader(api).send("tok", to, listOf(file("a.pdf"), file("b.pdf")))

        assertEquals(1, uploads)
        assertEquals(listOf("a.pdf", "b.pdf"), result.failed.map { it.first })
        assertTrue(result.failed.all { it.second is SignedOut })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShareModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun signedIn(client: io.ktor.client.HttpClient) = FakePlatform({ client }).apply {
        store.values["session"] = """{"boxId":"0123456789abcdef","household":"smiths","token":"tok"}"""
    }

    @Test
    fun aPhoneNotSignedInIsToldSo() {
        val model = ShareModel(FakePlatform({ fakeClient { null } }), fakeClient { null })
        assertEquals(ShareState.SignedOut, model.state)
    }

    @Test
    fun itOpensAtTheLastFolderAndRemembersTheChosenOne() = runTest(dispatcher) {
        val client = fakeClient { request ->
            val path = request.url.parameters["path"]
            json("""{"device": "/dev/sdb1", "drive": "Big", "path": "$path", "parent": "", "folders": [{"name": "Scans", "path": "Documents/Scans"}]}""")
        }
        val platform = signedIn(client)
        platform.store.values["share_folder"] = """{"device":"/dev/sdb1","path":"Documents","label":"Documents"}"""
        val model = ShareModel(platform, client)
        advanceUntilIdle()
        assertEquals("Documents", (model.state as ShareState.Folder).view.path)

        model.open("/dev/sdb1", "Documents/Scans")
        advanceUntilIdle()
        assertEquals(Destination("/dev/sdb1", "Documents/Scans", "Scans"), model.choose())
        assertTrue(platform.store.values["share_folder"]!!.contains("Documents/Scans"))
    }

    @Test
    fun aLastFolderThatsGoneFallsBackToTheDrives() = runTest(dispatcher) {
        val client = fakeClient { request ->
            if (request.url.parameters["device"] == null) json("""{"drives": [{"device": "/dev/sdc1", "name": "New"}]}""")
            else json("""{"error": "That drive isn't mounted."}""", HttpStatusCode.BadRequest)
        }
        val platform = signedIn(client)
        platform.store.values["share_folder"] = """{"device":"/dev/sdb1","path":"Documents","label":"Documents"}"""
        val model = ShareModel(platform, client)
        advanceUntilIdle()

        assertEquals("New", (model.state as ShareState.Drives).drives.single().name)
    }

    @Test
    fun aFrozenFolderCantBeChosen() = runTest(dispatcher) {
        val client = fakeClient {
            json("""{"device": "/dev/sdb1", "drive": "Big", "path": "Music", "frozen": "Music Assistant owns this folder."}""")
        }
        val model = ShareModel(signedIn(client), client)
        model.open("/dev/sdb1", "Music")
        advanceUntilIdle()

        assertIs<ShareState.Folder>(model.state)
        assertNull(model.choose())
    }
}
