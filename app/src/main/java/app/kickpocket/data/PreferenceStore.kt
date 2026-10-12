package app.kickpocket.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceStore(context: Context) {
    private val storage = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching {
        AppJson.decodeFromString<AppPreferences>(storage.getString("preferences", null) ?: "{}")
    }.getOrDefault(AppPreferences()))
    val preferences = mutable.asStateFlow()

    fun update(transform: (AppPreferences) -> AppPreferences) {
        val value = transform(mutable.value).let { it.copy(chatFontSize = it.chatFontSize.coerceIn(12, 20)) }
        mutable.value = value
        storage.edit { putString("preferences", AppJson.encodeToString(value)) }
    }

    fun remember(channel: KickChannel) = update {
        val recent = RecentChannel(channel.slug, channel.displayName, channel.avatarUrl)
        it.copy(recentChannels = (listOf(recent) + it.recentChannels.filter { old -> old.slug != channel.slug }).take(12))
    }
}

