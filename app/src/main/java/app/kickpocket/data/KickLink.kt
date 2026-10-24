package app.kickpocket.data

import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class ChannelTab(val label: String) { Live("Live"), Videos("VODs"), Clips("Clips") }

sealed interface KickLink {
    data object Home : KickLink
    data class Channel(val slug: String, val tab: ChannelTab = ChannelTab.Live) : KickLink
    data class Video(val slug: String, val id: String, val positionMs: Long = 0) : KickLink
    data class Clip(val id: String) : KickLink

    companion object {
        internal val videoId = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        internal val clipId = Regex("clip_[A-Za-z0-9_-]{1,100}")
        private val reserved = setOf("video", "videos", "clips", "categories", "search", "auth", "settings", "dashboard", "login", "signup")

        fun parse(input: String): KickLink {
            val text = input.trim()
            val value = if (text.startsWith("kick.com/", true) || text.startsWith("www.kick.com/", true)) "https://$text" else text
            if (!value.contains("://")) return Channel(channel(value))
            val url = value.toHttpUrlOrNull() ?: throw IllegalArgumentException("Enter a channel name or a Kick link.")
            require(url.host in setOf("kick.com", "www.kick.com") && url.port == (if (url.isHttps) 443 else 80) &&
                url.username.isEmpty() && url.password.isEmpty()) { "Use a link from kick.com." }
            val path = url.pathSegments.toMutableList().apply { if (lastOrNull() == "") removeAt(lastIndex) }
            if (path.isEmpty()) return Home
            url.queryParameter("clip")?.let {
                require(path.size == 1 && clipId.matches(it)) { "This Kick clip link is invalid." }
                channel(path[0])
                return Clip(it)
            }
            if (path.size == 2 && path[0] == "clips" && clipId.matches(path[1])) return Clip(path[1])
            val slug = channel(path[0])
            return when {
                path.size == 1 -> Channel(slug)
                path.size == 2 && path[1] == "videos" -> Channel(slug, ChannelTab.Videos)
                path.size == 2 && path[1] == "clips" -> Channel(slug, ChannelTab.Clips)
                path.size == 3 && path[1] == "videos" && videoId.matches(path[2]) ->
                    Video(slug, path[2], position(url.queryParameter("t")))
                path.size == 3 && path[1] == "clips" && clipId.matches(path[2]) -> Clip(path[2])
                else -> throw IllegalArgumentException("Open a Kick channel, VOD, or clip link.")
            }
        }

        private fun channel(value: String): String = ChannelInput.normalize(value).also {
            require(it !in reserved) { "Open a Kick channel, VOD, or clip link." }
        }

        private fun position(value: String?): Long {
            if (value.isNullOrBlank()) return 0
            val seconds = value.toLongOrNull() ?: Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?")
                .matchEntire(value.lowercase(Locale.ROOT))?.let { match ->
                    val parts = (1..3).map { match.groupValues[it].toLongOrNull() ?: 0 }
                    if (parts.any { it > 7 * 24 * 3600 }) return@let 0L
                    parts[0] * 3600 + parts[1] * 60 + parts[2]
                } ?: 0
            return seconds.coerceIn(0, 7 * 24 * 3600) * 1000
        }
    }
}
