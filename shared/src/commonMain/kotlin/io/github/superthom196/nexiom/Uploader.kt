package io.github.superthom196.nexiom

import io.github.superthom196.nexiom.api.ApiException
import io.github.superthom196.nexiom.api.NexiomApi
import io.github.superthom196.nexiom.api.Refused
import io.github.superthom196.nexiom.api.SignedOut
import io.ktor.utils.io.ByteReadChannel

/** A file to send: its name, its size when known, and how to read it (again, if it has to be resent). */
class Outgoing(val name: String, val size: Long?, val open: () -> ByteReadChannel)

/** What happened to a batch: the names the files were saved as, and why the others weren't. */
data class UploadResult(val sent: List<String>, val failed: List<Pair<String, ApiException>>)

/** Sends shared files to a folder, one at a time, keeping any file already there. */
class Uploader(private val api: NexiomApi) {

    /**
     * Sends `files` to `to`. A name that's taken gets a number ("photo (2).jpg"): the folder's
     * names are asked for first when the box lists them, and a file the box skips as already
     * there is sent again under the next number. A signed-out phone stops the batch.
     */
    suspend fun send(
        token: String,
        to: Destination,
        files: List<Outgoing>,
        progress: (index: Int, sent: Long) -> Unit = { _, _ -> },
    ): UploadResult {
        val taken = try {
            api.folder(token, to.device, to.path).files.orEmpty().toMutableSet()
        } catch (_: SignedOut) {
            return UploadResult(emptyList(), files.map { it.name to SignedOut() })
        } catch (_: ApiException) {
            mutableSetOf()
        }
        val sent = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, ApiException>>()
        for ((index, file) in files.withIndex()) {
            try {
                val name = sendOne(token, to, file, taken) { progress(index, it) }
                taken += name
                sent += name
            } catch (e: SignedOut) {
                files.drop(index).forEach { failed += it.name to e }
                break
            } catch (e: ApiException) {
                failed += file.name to e
            }
        }
        return UploadResult(sent, failed)
    }

    private suspend fun sendOne(
        token: String,
        to: Destination,
        file: Outgoing,
        taken: MutableSet<String>,
        progress: (Long) -> Unit,
    ): String {
        repeat(MAX_TRIES) {
            val name = freeName(file.name, taken)
            val answer = api.upload(token, to.device, to.path, name, file.size, file.open, progress)
            if (!answer.skipped) return name
            taken += name
        }
        throw Refused(409, "Too many files called ${file.name} already")
    }

    private companion object {
        const val MAX_TRIES = 20
    }
}
