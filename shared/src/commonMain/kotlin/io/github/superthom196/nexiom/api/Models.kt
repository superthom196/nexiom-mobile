package io.github.superthom196.nexiom.api

import kotlinx.serialization.Serializable

// The box's phone API, as Nexiom Server's docs/architecture.md "Phone app" describes it.
// Unknown keys are ignored, so a newer server doesn't break an older app.

/** `GET /api/box`: which box answered. `household` is empty until the welcome screen is done. */
@Serializable
data class BoxInfo(val id: String, val household: String = "", val ready: Boolean = false)

@Serializable
internal data class SignInRequest(val household: String, val password: String, val phone: String)

@Serializable
data class PhoneInfo(val id: String, val name: String)

@Serializable
data class SignInResponse(val token: String, val phone: PhoneInfo, val box: BoxInfo)

/** A launcher tile. `link` is null while the service can't be opened (its description says why). */
@Serializable
data class Tile(
    val id: String,
    val name: String,
    val description: String = "",
    val link: String? = null,
    val pillar: String = "more",
)

@Serializable
data class Section(val key: String, val title: String, val tiles: List<Tile> = emptyList())

@Serializable
data class ServiceCounts(val running: Int, val total: Int)

/** The status strip. Any part the box can't read is null and left out. */
@Serializable
data class Status(val services: ServiceCounts? = null, val storage: Int? = null, val blocked: Long? = null)

/** A resource ring: fills by `used` of `total`; `detail` is its text ("3.1 GB / 8 GB"). */
@Serializable
data class Ring(val used: Long, val total: Long, val detail: String = "") {
    val fraction: Float get() = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f

    /** `detail` with GB to the nearest whole GB ("118.3 GB / 256.1 GB" -> "118 GB / 256 GB"), so it fits. */
    val wholeGbDetail: String
        get() = Regex("""(\d+\.\d+) GB""").replace(detail) { "${kotlin.math.round(it.groupValues[1].toDouble()).toLong()} GB" }
}

/** `cpu` is 0–100. */
@Serializable
data class Stats(val cpu: Double? = null, val ram: Ring? = null, val disk: Ring? = null, val storage: Ring? = null)

/** Something that needs attention; `link` is a Settings page, or null when the household can't change it. */
@Serializable
data class Attention(val id: String, val text: String, val link: String? = null)

/** What `GET /api/events` sends as `status`: the live parts of the dashboard. */
@Serializable
data class Live(
    val status: Status = Status(),
    val stats: Stats = Stats(),
    val attention: List<Attention> = emptyList(),
)

@Serializable
data class Dashboard(
    val box: BoxInfo,
    val sections: List<Section> = emptyList(),
    val status: Status = Status(),
    val stats: Stats = Stats(),
    val attention: List<Attention> = emptyList(),
) {
    fun with(live: Live) = copy(status = live.status, stats = live.stats, attention = live.attention)
}

@Serializable
internal data class ApiError(val error: String = "")

/** `POST /api/homeassistant`: where Home Assistant is, and a long-lived token of this phone's own. */
@Serializable
data class HomeAssistantAccess(val url: String, val token: String)

/**
 * An official app Set up this phone lists: `server` and `login` are the values it takes (null when
 * it takes none or the box doesn't have one yet); `tailnet` is Tailscale's network, once connected.
 */
@Serializable
data class SetupApp(
    val id: String,
    val name: String,
    val server: String? = null,
    val login: String? = null,
    val tailnet: String? = null,
)

@Serializable
internal data class PhoneSetup(val apps: List<SetupApp> = emptyList())

/** A mounted drive the box can write to. */
@Serializable
data class Drive(val device: String, val name: String)

@Serializable
internal data class Drives(val drives: List<Drive> = emptyList())

@Serializable
data class FolderRef(val name: String, val path: String)

/**
 * A folder on a drive: its subfolders, and as `frozen` why files can't go here (or null).
 * `files` are the names already here, when the box lists them.
 */
@Serializable
data class FolderView(
    val device: String,
    val drive: String,
    val path: String = "",
    val parent: String = "",
    val frozen: String? = null,
    val folders: List<FolderRef> = emptyList(),
    val files: List<String>? = null,
)

/** `POST /api/upload`: saved, or skipped because a file of that name is already there. */
@Serializable
data class UploadAnswer(val saved: Boolean = false, val skipped: Boolean = false)
