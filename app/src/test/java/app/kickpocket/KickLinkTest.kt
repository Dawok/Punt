package app.kickpocket

import app.kickpocket.data.ChannelTab
import app.kickpocket.data.KickLink
import org.junit.Assert.*
import org.junit.Test

class KickLinkTest {
    private val id = "12345678-1234-1234-1234-123456789abc"

    @Test fun channelNamesLinksAndLibraryTabs() {
        for (input in listOf("@USER", " user ", "kick.com/user", "http://www.kick.com/user/?source=share"))
            assertEquals(KickLink.Channel("user"), KickLink.parse(input))
        assertEquals(KickLink.Home, KickLink.parse("https://kick.com/"))
        assertEquals(KickLink.Channel("user", ChannelTab.Videos), KickLink.parse("https://kick.com/user/videos"))
        assertEquals(KickLink.Channel("user", ChannelTab.Clips), KickLink.parse("https://www.kick.com/user/clips/?sort=date"))
    }

    @Test fun vodLinksSupportTimestampsAndClipLinksResolveTheClipId() {
        assertEquals(KickLink.Video("user", id, 90_000), KickLink.parse("https://kick.com/user/videos/$id?t=90"))
        assertEquals(KickLink.Video("user", id, 3_723_000), KickLink.parse("https://kick.com/user/videos/$id?t=1h2m3s"))
        for (path in listOf("user?clip=clip_ABC123", "user/?source=share&clip=clip_ABC123", "user/clips/clip_ABC123", "clips/clip_ABC123"))
            assertEquals(KickLink.Clip("clip_ABC123"), KickLink.parse("https://kick.com/$path"))
        assertEquals(KickLink.Video("user", id), KickLink.parse("https://kick.com/user/videos/$id?t=not-a-time"))
    }

    @Test fun rejectsUnsupportedHostsSchemesCredentialsAndPaths() {
        for (input in listOf("", "https://example.com/user", "https://kick.com.example.com/user", "file://kick.com/user",
            "https://user@kick.com/channel", "https://kick.com:444/user", "https://kick.com/user/videos/not-a-video",
            "https://kick.com/user?clip=invalid", "https://kick.com/search", "https://kick.com/user/settings", "user/name"))
            assertThrows(input, IllegalArgumentException::class.java) { KickLink.parse(input) }
    }
}
