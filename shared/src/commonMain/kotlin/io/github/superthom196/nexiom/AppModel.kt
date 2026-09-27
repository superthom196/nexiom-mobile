package io.github.superthom196.nexiom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.superthom196.nexiom.api.ApiException
import io.github.superthom196.nexiom.api.Dashboard
import io.github.superthom196.nexiom.api.NexiomApi
import io.github.superthom196.nexiom.api.NotSetUp
import io.github.superthom196.nexiom.api.Refused
import io.github.superthom196.nexiom.api.SignedOut
import io.github.superthom196.nexiom.api.Tile
import io.github.superthom196.nexiom.api.Unreachable
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Find : Screen
    data class NotSetUp(val box: FoundBox) : Screen
    data class SignIn(val boxId: String, val household: String, val signedOut: Boolean = false) : Screen
    data object Home : Screen
}

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val dashboard: Dashboard) : HomeState
    data object Unreachable : HomeState

    /** Another box answered at nexiom.home, such as on another household's tailnet. */
    data object WrongBox : HomeState
}

sealed interface SignInProblem {
    /** The box's own words, such as a wrong household name or password. */
    data class Box(val message: String) : SignInProblem
    data object WrongBox : SignInProblem
    data object Unreachable : SignInProblem
    data object NotSetUp : SignInProblem
}

/** A web page open inside the app; `headers` go with its first request only. */
data class WebPage(val title: String, val url: String, val headers: Map<String, String>)

class AppModel(private val platform: Platform, private val client: HttpClient) : ViewModel() {
    private val api = NexiomApi(client)
    private val sessions = SessionStore(platform.store)
    private var session: Session? = sessions.load()

    var screen by mutableStateOf(startScreen())
        private set
    var home by mutableStateOf<HomeState>(HomeState.Loading)
        private set
    var web by mutableStateOf<WebPage?>(null)
        private set
    var signingIn by mutableStateOf(false)
        private set
    var signInProblem by mutableStateOf<SignInProblem?>(null)
        private set

    /** Bumped by Try again, so the dashboard starts following the box afresh. */
    var attempt by mutableIntStateOf(0)
        private set

    val household: String get() = session?.household.orEmpty()

    private fun startScreen(): Screen {
        val s = session ?: return Screen.Find
        return if (s.token != null) Screen.Home else Screen.SignIn(s.boxId, s.household)
    }

    // --- Find ----------------------------------------------------------------------

    /** Boxes found over mDNS, plus whichever box answers at nexiom.home when mDNS is blocked. */
    fun boxesNearby(): Flow<List<FoundBox>> =
        combine(platform.discover().onStart { emit(emptyList()) }, direct()) { found, direct ->
            if (direct == null || found.any { it.id == direct.id }) found else found + direct
        }

    private fun direct(): Flow<FoundBox?> = flow {
        while (true) {
            val box = try {
                api.box().let { FoundBox(it.id, it.household) }
            } catch (_: ApiException) {
                null
            }
            emit(box)
            delay(PROBE_EVERY_MS)
        }
    }

    fun choose(box: FoundBox) {
        signInProblem = null
        screen = if (box.ready) Screen.SignIn(box.id, box.household) else Screen.NotSetUp(box)
    }

    fun openWelcome() = platform.openInBrowser(NexiomApi.BASE_URL)

    fun findAgain() {
        save(null)
        signInProblem = null
        screen = Screen.Find
    }

    // --- Sign in -------------------------------------------------------------------

    fun signIn(household: String, password: String) {
        val target = screen as? Screen.SignIn ?: return
        if (signingIn) return
        signingIn = true
        signInProblem = null
        viewModelScope.launch {
            try {
                // Every box is nexiom.home: make sure the one answering is the one chosen.
                if (api.box().id != target.boxId) {
                    signInProblem = SignInProblem.WrongBox
                    return@launch
                }
                val result = api.signIn(household.trim(), password, platform.deviceName)
                save(Session(result.box.id, result.box.household, result.token, result.phone.id))
                home = HomeState.Loading
                screen = Screen.Home
            } catch (_: NotSetUp) {
                signInProblem = SignInProblem.NotSetUp
            } catch (e: Refused) {
                signInProblem = SignInProblem.Box(e.message.orEmpty())
            } catch (_: ApiException) {
                signInProblem = SignInProblem.Unreachable
            } finally {
                signingIn = false
            }
        }
    }

    fun signOut() {
        val token = session?.token
        save(null)
        web = null
        home = HomeState.Loading
        screen = Screen.Find
        if (token != null) {
            viewModelScope.launch {
                try {
                    api.signOut(token)
                } catch (_: ApiException) {
                    // Settings > Phones can still sign it out.
                }
            }
        }
    }

    private fun signedOut() {
        val s = session ?: return
        save(s.copy(token = null, phoneId = null))
        web = null
        home = HomeState.Loading
        screen = Screen.SignIn(s.boxId, s.household, signedOut = true)
    }

    // --- Dashboard -----------------------------------------------------------------

    /**
     * Runs while the dashboard is on screen: loads it, then keeps it live over the event
     * stream, reloading when the stream breaks. Returns when the box can't be reached.
     */
    suspend fun follow() {
        while (true) {
            val token = session?.token ?: return
            if (!refresh(token)) return
            try {
                api.events(token).collect { live ->
                    (home as? HomeState.Ready)?.let { home = HomeState.Ready(it.dashboard.with(live)) }
                }
            } catch (_: SignedOut) {
                signedOut()
                return
            } catch (_: ApiException) {
                // The stream broke (Wi-Fi to mobile data, the box restarting): reload below.
            }
            delay(RECONNECT_AFTER_MS)
        }
    }

    private suspend fun refresh(token: String): Boolean {
        val dashboard = try {
            api.dashboard(token)
        } catch (_: SignedOut) {
            signedOut()
            return false
        } catch (_: ApiException) {
            home = HomeState.Unreachable
            return false
        }
        val s = session ?: return false
        if (dashboard.box.id != s.boxId) {
            home = HomeState.WrongBox
            return false
        }
        if (dashboard.box.household.isNotBlank() && dashboard.box.household != s.household) {
            save(s.copy(household = dashboard.box.household))
        }
        home = HomeState.Ready(dashboard)
        return true
    }

    fun retry() {
        home = HomeState.Loading
        attempt++
    }

    /** The service's official app when the phone has it, else its web page inside the app. */
    fun openTile(tile: Tile) {
        val link = tile.link ?: return
        if (platform.openOfficialApp(tile.id)) return
        openWeb(tile.name, link)
    }

    /** Pages on nexiom.home open signed in, through `/api/web`; others (the tailnet's) as they are. */
    fun openWeb(title: String, link: String) {
        val token = session?.token ?: return
        web = if (NexiomApi.isNexiomAddress(link)) {
            WebPage(title, api.webUrl(link), mapOf("Authorization" to "Bearer $token"))
        } else {
            WebPage(title, link, emptyMap())
        }
    }

    fun closeWeb() {
        web = null
    }

    private fun save(new: Session?) {
        session = new
        sessions.save(new)
    }

    override fun onCleared() {
        client.close()
    }

    private companion object {
        const val PROBE_EVERY_MS = 5_000L
        const val RECONNECT_AFTER_MS = 3_000L
    }
}
