package app.kickpocket

import app.kickpocket.data.*
import org.junit.Assert.*
import org.junit.Test

class ChannelSuggestionsTest {
    private fun card(slug: String) = ChannelCard(slug, slug, "", "", 0, null, null)

    @Test fun mergesPinnedRecentAndFollowedWithoutDuplicatesAndUnderstandsLinks() {
        val preferences = AppPreferences(favoriteChannels = listOf(RecentChannel("channel", "Channel")),
            recentChannels = listOf(RecentChannel("channel", "Channel"), RecentChannel("channel_two", "Two")))
        val result = ChannelSuggestions.find("https://kick.com/CHANNEL", preferences, listOf(card("channel_three")), listOf(card("channel")))
        assertEquals(listOf("channel", "channel_two", "channel_three"), result.map { it.channel.slug })
        assertEquals(listOf("Pinned", "Recent", "Following"), result.map { it.source })
        assertTrue(ChannelSuggestions.find("https://example.com/channel", preferences, emptyList(), emptyList()).isEmpty())
    }

    @Test fun prefixMatchesBeatDisplayNameMatchesAndResultsAreBounded() {
        val preferences = AppPreferences(favoriteChannels = listOf(RecentChannel("other", "Channel viewer")))
        val result = ChannelSuggestions.find("channel", preferences, emptyList(), (1..10).map { card("channel_$it") })
        assertEquals(6, result.size)
        assertTrue(result.all { it.channel.slug.startsWith("channel") })
    }

    @Test fun oldPreferencesKeepTheirValuesAndNewPreferencesRoundTrip() {
        val old = AppJson.decodeFromString<AppPreferences>("""{"animatedEmotes":false,"chatFontSize":16}""")
        assertFalse(old.animatedEmotes)
        assertEquals(16, old.chatFontSize)
        assertTrue(old.favoriteChannels.isEmpty())
        assertEquals(0, old.wifiQuality)
        val updated = old.copy(wifiQuality = 1080, mobileQuality = 480, favoriteChannels = listOf(RecentChannel("channel", "Channel")))
        assertEquals(updated, AppJson.decodeFromString<AppPreferences>(AppJson.encodeToString(updated)))
    }
}
