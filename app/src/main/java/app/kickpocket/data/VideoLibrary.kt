package app.kickpocket.data

import kotlinx.serialization.Serializable

@Serializable
data class WatchProgress(val recording: ChannelRecording, val positionMs: Long, val durationMs: Long,
    val updatedAt: Long, val completed: Boolean = false)

@Serializable
data class VideoBookmark(val recording: ChannelRecording, val positionMs: Long, val createdAt: Long)

object VideoLibrary {
    fun key(recording: ChannelRecording): String = "${recording.kind}:${recording.id}"

    fun checkpoint(history: List<WatchProgress>, recording: ChannelRecording, positionMs: Long,
        durationMs: Long, now: Long, ended: Boolean = false): List<WatchProgress> {
        val duration = durationMs.coerceAtLeast(0)
        val position = positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        if (position < 3_000 && !ended && history.none { key(it.recording) == key(recording) }) return history
        val completed = ended || (duration > 0 && duration - position <= minOf(10_000, duration / 20))
        val entry = WatchProgress(recording.copy(playbackUrl = null), position, duration, now, completed)
        return (listOf(entry) + history.filterNot { key(it.recording) == key(recording) }).take(50)
    }

    fun resume(history: List<WatchProgress>, recording: ChannelRecording): Long =
        history.firstOrNull { key(it.recording) == key(recording) && !it.completed }?.positionMs ?: 0

    fun toggleSaved(saved: List<ChannelRecording>, recording: ChannelRecording): List<ChannelRecording> =
        if (saved.any { key(it) == key(recording) }) saved.filterNot { key(it) == key(recording) }
        else (listOf(recording.copy(playbackUrl = null)) + saved).take(200)

    fun bookmark(bookmarks: List<VideoBookmark>, recording: ChannelRecording, positionMs: Long, now: Long): List<VideoBookmark> {
        val position = positionMs.coerceAtLeast(0) / 1000 * 1000
        return (listOf(VideoBookmark(recording.copy(playbackUrl = null), position, now)) +
            bookmarks.filterNot { key(it.recording) == key(recording) && it.positionMs == position }).take(300)
    }

    fun link(recording: ChannelRecording, positionMs: Long? = null): String {
        val path = if (recording.kind == RecordingKind.Vod) "videos" else "clips"
        return "https://kick.com/${recording.channelSlug}/$path/${recording.id}" +
            (positionMs?.let { "?t=${it.coerceAtLeast(0) / 1000}" } ?: "")
    }
}
