package app.kickpocket.data

data class ChannelSuggestion(val channel: RecentChannel, val source: String)

object ChannelSuggestions {
    fun find(input: String, preferences: AppPreferences, followed: List<ChannelCard>, browsing: List<ChannelCard>): List<ChannelSuggestion> {
        if (input.isBlank()) return emptyList()
        val query = runCatching { ChannelInput.normalize(input) }.getOrNull() ?: return emptyList()
        fun cards(channels: List<ChannelCard>, source: String) = channels.map {
            ChannelSuggestion(RecentChannel(it.slug, it.username, it.avatar), source)
        }
        return (preferences.favoriteChannels.map { ChannelSuggestion(it, "Pinned") } +
            preferences.recentChannels.map { ChannelSuggestion(it, "Recent") } +
            cards(followed, "Following") + cards(browsing, "Live"))
            .distinctBy { it.channel.slug.lowercase() }
            .filter { it.channel.slug.contains(query, true) || it.channel.displayName.contains(query, true) }
            .sortedBy { if (it.channel.slug.startsWith(query, true)) 0 else 1 }.take(6)
    }
}
