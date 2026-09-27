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
