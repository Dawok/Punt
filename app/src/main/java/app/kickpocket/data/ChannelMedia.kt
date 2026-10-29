package app.kickpocket.data

import java.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class RecordingKind(val label: String) { Vod("VOD"), Clip("Clip") }

@Serializable
data class ChannelRecording(
    val id: String,
    val kind: RecordingKind,
    val channelSlug: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0,
    val views: Long = 0,
    val createdAt: String? = null,
    val availability: String = "public",
    @Transient val playbackUrl: String? = null,
)

data class RecordingPage(val items: List<ChannelRecording>, val cursor: String? = null)

data class ChannelLibraryState(
    val tab: ChannelTab = ChannelTab.Live,
    val videos: List<ChannelRecording> = emptyList(),
    val clips: List<ChannelRecording> = emptyList(),
    val clipsCursor: String? = null,
    val loaded: Set<ChannelTab> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val items get() = if (tab == ChannelTab.Videos) videos else clips
}

object MediaRequests {
    const val playbackBody = "{\"video_player\":{\"player\":{}},\"video_session\":{},\"user_session\":{\"non_personalised_ads\":true}}"

    fun videos(channelId: Long): String {
        require(channelId > 0)
        return "https://web.kick.com/api/v1/channels/$channelId/videos"
    }

    fun clips(channelId: Long, cursor: String? = null): String {
        require(channelId > 0)
        return "https://web.kick.com/api/v1/channels/$channelId/clips".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "date").addQueryParameter("time", "all")
            .apply { cursor?.let { addQueryParameter("cursor", it) } }.build().toString()
    }

    fun clip(id: String): String {
        require(KickLink.clipId.matches(id))
        return "https://kick.com/api/v2/clips/$id"
    }

    fun playback(id: String): String {
        require(KickLink.videoId.matches(id))
        return "https://web.kick.com/api/v1/stream/$id/playback"
    }

    internal fun isPlayback(url: HttpUrl): Boolean = url.host == "web.kick.com" &&
        Regex("/api/v1/stream/(${KickLink.videoId.pattern})/playback").matches(url.encodedPath)
}

object MediaParser {
    fun videos(root: JsonElement, slug: String): RecordingPage {
        val entries = root.asObject()?.get("data") as? JsonArray
            ?: throw IOException("Kick returned an unexpected VOD list.")
        return RecordingPage(entries.mapNotNull { value ->
            val item = value.asObject() ?: return@mapNotNull null
            val id = item.text("id")?.takeIf(KickLink.videoId::matches) ?: return@mapNotNull null
            ChannelRecording(id, RecordingKind.Vod, slug, item.text("title").orEmpty().ifBlank { "Past broadcast" },
                image(item.obj("thumbnail")?.text("src")), duration(item), item.number("viewer_count") ?: 0,
                item.text("start_time"), item.text("status") ?: "public")
        })
    }

    fun clips(root: JsonElement, slug: String): RecordingPage {
        val data = root.asObject()?.obj("data") ?: throw IOException("Kick returned an unexpected clip list.")
        val items = data["clips"] as? JsonArray ?: throw IOException("Kick returned an unexpected clip list.")
        return RecordingPage(items.mapNotNull { it.asObject()?.let { item -> clipEntry(item, slug) } },
            data.text("cursor")?.takeIf(String::isNotBlank))
    }

    fun clip(root: JsonElement): ChannelRecording {
        val item = root.asObject()?.obj("clip") ?: throw IOException("This clip is no longer available.")
        return clipEntry(item, null)?.takeIf { it.playbackUrl != null }
            ?: throw IOException("This clip is unavailable or private.")
    }

    private fun clipEntry(item: JsonObject, fallbackSlug: String?): ChannelRecording? {
        val id = item.text("id")?.takeIf(KickLink.clipId::matches) ?: return null
        val slug = item.obj("channel")?.text("slug") ?: fallbackSlug ?: return null
        val safeSlug = runCatching { ChannelInput.normalize(slug) }.getOrNull() ?: return null
        return ChannelRecording(id, RecordingKind.Clip, safeSlug, item.text("title").orEmpty().ifBlank { "Clip" },
            image(item.text("thumbnail_url")), duration(item),
            item.number("views_count") ?: item.number("view_count") ?: item.number("views") ?: 0,
            item.text("created_at"), item.text("privacy") ?: "public",
            sequenceOf("playback_url", "clip_url", "video_url").mapNotNull { image(item.text(it)) }.firstOrNull())
    }

    fun playback(root: JsonElement, recording: ChannelRecording): ChannelRecording {
        val item = root.asObject() ?: throw IOException("Kick returned an unexpected video response.")
        val url = image(item.obj("playback_url")?.text("vod"))
            ?: throw IOException("This VOD is unavailable. It may be expired, private, or require a channel subscription.")
        val session = item.obj("video_session")
        return recording.copy(playbackUrl = url,
            title = session?.text("video_title")?.takeIf(String::isNotBlank) ?: recording.title,
            durationSeconds = session?.number("video_duration") ?: recording.durationSeconds)
    }

    private fun image(value: String?): String? = value?.takeIf {
        it.toHttpUrlOrNull()?.let { url -> url.isHttps && url.username.isEmpty() && url.password.isEmpty() } == true
    }

    private fun duration(item: JsonObject): Long = (item["duration"] as? JsonPrimitive)
        ?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 }?.toLong() ?: 0
}
