package io.github.superthom196.nexiom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.superthom196.nexiom.api.ApiException
import io.github.superthom196.nexiom.api.Drive
import io.github.superthom196.nexiom.api.FolderView
import io.github.superthom196.nexiom.api.NexiomApi
import io.github.superthom196.nexiom.api.Refused
import io.github.superthom196.nexiom.api.SignedOut
import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where shared files go: a folder on a drive, and what to call it. */
@Serializable
data class Destination(val device: String, val path: String, val label: String) {
    companion object {
        fun of(view: FolderView) = Destination(view.device, view.path, view.path.substringAfterLast('/').ifEmpty { view.drive })
    }
}

sealed interface ShareState {
    data object SignedOut : ShareState
    data object Loading : ShareState
    data class Drives(val drives: List<Drive>) : ShareState
    data class Folder(val view: FolderView) : ShareState

    /** No answer from the box. */
    data object Unreachable : ShareState

    /** The box's own words, such as a drive that went away. */
    data class Refused(val message: String) : ShareState
}

/** Share to Nexiom's folder browser: it opens at the last folder used. */
class ShareModel(platform: Platform, private val client: HttpClient) : ViewModel() {
    private val api = NexiomApi(client)
    private val store = platform.store
    private val token = SessionStore(store).load()?.token
    private var job: Job? = null

    var state by mutableStateOf<ShareState>(if (token == null) ShareState.SignedOut else ShareState.Loading)
        private set

    init {
        start()
    }

    fun start() {
        val last = lastDestination()
        if (last != null) open(last.device, last.path, drivesIfGone = true) else showDrives()
    }

    fun showDrives() = load { ShareState.Drives(api.drives(it)) }

    fun open(device: String, path: String, drivesIfGone: Boolean = false) =
        load(drivesIfGone) { ShareState.Folder(api.folder(it, device, path)) }

    /** Up a folder; from the top of a drive, to the drives. Returns false when there's nowhere up. */
    fun up(): Boolean {
        val view = (state as? ShareState.Folder)?.view ?: return false
        if (view.path.isEmpty()) showDrives() else open(view.device, view.parent)
        return true
    }

    /** The folder shown, when files can go there; remembered for next time. */
    fun choose(): Destination? {
        val view = (state as? ShareState.Folder)?.view ?: return null
        if (view.frozen != null) return null
        return Destination.of(view).also { store.set(LAST, Json.encodeToString(it)) }
    }

    private fun load(drivesIfGone: Boolean = false, block: suspend (String) -> ShareState) {
        val token = token ?: return
        job?.cancel()
        state = ShareState.Loading
        job = viewModelScope.launch {
            state = try {
                block(token)
            } catch (_: SignedOut) {
                ShareState.SignedOut
            } catch (e: Refused) {
                // The last folder may be on a drive that's been unplugged or renamed.
                if (drivesIfGone) {
                    showDrives()
                    return@launch
                }
                ShareState.Refused(e.message.orEmpty())
            } catch (_: ApiException) {
                ShareState.Unreachable
            }
        }
    }

    private fun lastDestination(): Destination? =
        store.get(LAST)?.let { runCatching { Json.decodeFromString<Destination>(it) }.getOrNull() }

    override fun onCleared() {
        client.close()
    }

    private companion object {
        const val LAST = "share_folder"
    }
}
