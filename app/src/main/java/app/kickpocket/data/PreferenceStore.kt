package app.kickpocket.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceStore(context: Context) {
    private val storage = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching {
        AppJson.decodeFromString<AppPreferences>(storage.getString("preferences", null) ?: "{}")
    }.getOrDefault(AppPreferences()).let { value ->
        value.copy(watchHistory = value.watchHistory.filter { it.recording.kind == RecordingKind.Vod })
    })
    val preferences = mutable.asStateFlow()

    @Synchronized
    fun update(transform: (AppPreferences) -> AppPreferences) {
        val value = transform(mutable.value).let { it.copy(chatFontSize = it.chatFontSize.coerceIn(12, 20),
            playbackSpeed = it.playbackSpeed.takeIf(Float::isFinite)?.coerceIn(0.5f, 2f) ?: 1f) }
        if (value == mutable.value) return
        mutable.value = value
        storage.edit { putString("preferences", AppJson.encodeToString(value)) }
    }

    fun remember(channel: KickChannel) = update {
        val recent = RecentChannel(channel.slug, channel.displayName, channel.avatarUrl)
        it.copy(recentChannels = (listOf(recent) + it.recentChannels.filter { old -> old.slug != channel.slug }).take(12))
    }

    fun recordEmotes(content: String, emotes: Map<String, Emote>) = update {
        it.copy(emoteUsage = EmoteMenu.record(it.emoteUsage, content, emotes))
    }

    fun toggleFavorite(channel: RecentChannel) = update {
        val exists = it.favoriteChannels.any { old -> old.slug == channel.slug }
        it.copy(favoriteChannels = if (exists) it.favoriteChannels.filterNot { old -> old.slug == channel.slug }
            else it.favoriteChannels + channel)
    }

    fun checkpoint(recording: ChannelRecording, positionMs: Long, durationMs: Long, ended: Boolean = false) = update {
        it.copy(watchHistory = VideoLibrary.checkpoint(it.watchHistory, recording, positionMs, durationMs, System.currentTimeMillis(), ended))
    }

    fun toggleWatchLater(recording: ChannelRecording) = update { it.copy(watchLater = VideoLibrary.toggleSaved(it.watchLater, recording)) }
    fun bookmark(recording: ChannelRecording, positionMs: Long) = update {
        it.copy(bookmarks = VideoLibrary.bookmark(it.bookmarks, recording, positionMs, System.currentTimeMillis()))
    }
}

