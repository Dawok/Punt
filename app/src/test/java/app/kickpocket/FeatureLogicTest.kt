package app.kickpocket

import app.kickpocket.alerts.LiveAlertPolicy
import app.kickpocket.chat.ChatFilters
import app.kickpocket.data.*
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class FeatureLogicTest {
    private val vod = ChannelRecording("12345678-1234-1234-1234-123456789abc", RecordingKind.Vod, "channel", "Recorded stream",
        playbackUrl = "https://example.com/video.m3u8?token=temporary")
    private val clip = ChannelRecording("clip_ABC", RecordingKind.Clip, "channel", "Clip")
    private fun message(id: Int, username: String, text: String) = ChatMessage("$id", id.toLong(), username, null, text)

    @Test fun historyResumesUnfinishedVideosAndSupportsRewindingToTheBeginning() {
        val history = VideoLibrary.checkpoint(emptyList(), vod, 30_000, 120_000, 100)
        assertEquals(30_000L, VideoLibrary.resume(history, vod))
        assertNull(history.single().recording.playbackUrl)
        val rewound = VideoLibrary.checkpoint(history, vod, 0, 120_000, 200)
        assertEquals(0L, VideoLibrary.resume(rewound, vod))
        val finished = VideoLibrary.checkpoint(history, vod, 119_000, 120_000, 200)
        assertTrue(finished.single().completed)
        assertEquals(0L, VideoLibrary.resume(finished, vod))
        assertTrue(VideoLibrary.checkpoint(emptyList(), clip, 0, 4_000, 100).isEmpty())
        assertFalse(VideoLibrary.checkpoint(emptyList(), vod, 3_000, 4_000, 100).single().completed)
    }

    @Test fun clipsNeverResumeOrEnterContinueWatchingIncludingPreviouslySavedProgress() {
        val oldClip = WatchProgress(clip, 20_000, 60_000, 100)
        val video = WatchProgress(vod, 30_000, 120_000, 200)
        val history = listOf(oldClip, video, video.copy(recording = vod.copy(id = "finished"), completed = true))
        assertEquals(listOf(video), VideoLibrary.continueWatching(history))
        assertEquals(0L, VideoLibrary.resume(history, clip))
        assertEquals(30_000L, VideoLibrary.resume(history, vod))
        assertEquals(history.drop(1), VideoLibrary.checkpoint(history, clip, 40_000, 60_000, 300))
        assertTrue(VideoLibrary.checkpoint(emptyList(), clip, 40_000, 60_000, 300).isEmpty())
        assertTrue(VideoLibrary.checkpoint(emptyList(), clip, 60_000, 60_000, 300, ended = true).isEmpty())
    }

    @Test fun historyAndSavedListsAreBoundedAndDoNotDuplicateEntries() {
        var history = emptyList<WatchProgress>()
        repeat(70) { history = VideoLibrary.checkpoint(history, vod.copy(id = "$it"), 10_000, 90_000, it.toLong()) }
        assertEquals(50, history.size)
        val again = VideoLibrary.checkpoint(history, vod.copy(id = "69"), 20_000, 90_000, 100)
        assertEquals(50, again.size)
        assertEquals(20_000L, again.first().positionMs)
        val saved = VideoLibrary.toggleSaved(emptyList(), vod)
        assertNull(saved.single().playbackUrl)
        assertTrue(VideoLibrary.toggleSaved(saved, vod).isEmpty())
    }

    @Test fun bookmarksReopenVodAndClipTimestampsIncludingZero() {
        val bookmarks = VideoLibrary.bookmark(emptyList(), clip, 12_987, 100)
        assertEquals(12_000L, bookmarks.single().positionMs)
        assertEquals(1, VideoLibrary.bookmark(bookmarks, clip, 12_111, 200).size)
        assertEquals(KickLink.Clip(clip.id, 12_000), KickLink.parse(VideoLibrary.link(clip, 12_000)))
        assertEquals(KickLink.Video(vod.channelSlug, vod.id, 0), KickLink.parse(VideoLibrary.link(vod, 0)))
        assertEquals(KickLink.Video(vod.channelSlug, vod.id), KickLink.parse(VideoLibrary.link(vod)))
    }

    @Test fun preferencesRemainBackwardCompatibleAndNeverPersistPlaybackTokens() {
        val old = AppJson.decodeFromString<AppPreferences>("{\"chatFontSize\":16}")
        assertTrue(old.watchHistory.isEmpty())
        val preferences = old.copy(watchLater = listOf(vod), bookmarks = VideoLibrary.bookmark(emptyList(), vod, 10_000, 100))
        val encoded = AppJson.encodeToString(preferences)
        assertFalse(encoded.contains("temporary"))
        assertFalse(encoded.contains("playbackUrl"))
        assertEquals(vod.copy(playbackUrl = null), AppJson.decodeFromString<AppPreferences>(encoded).watchLater.single())
    }

    @Test fun chatFiltersIgnoreCaseHideUsersAndReduceRepeatedMessages() {
        val messages = listOf(message(1, "BOT", "Hello"), message(2, "viewer", "Blocked phrase"),
            message(3, "viewer", "Keep this"), message(4, "other", "KEEP THIS"), message(5, "other", "Different"))
        val preferences = AppPreferences(hiddenChatUsers = listOf("@bot"), hiddenChatKeywords = listOf("blocked"), reduceRepeatedMessages = true)
        assertEquals(listOf("3", "5"), ChatFilters.visible(messages, preferences).map { it.id })
        assertEquals(listOf("@bot", "keyword"), ChatFilters.terms(" @bot\n\nkeyword\nKEYWORD"))
        assertEquals(5, ChatFilters.visible(messages, AppPreferences()).size)
    }

    @Test fun mentionMatchingUsesWholeUsernamesAndEscapesSpecialCharacters() {
        assertTrue(ChatFilters.mentions("Hey @Viewer!", "viewer"))
        assertTrue(ChatFilters.mentions("viewer hello", "viewer"))
        assertFalse(ChatFilters.mentions("someviewer is here", "viewer"))
        assertFalse(ChatFilters.mentions("viewer_name", "viewer"))
        assertFalse(ChatFilters.mentions("anything", null))
        assertTrue(ChatFilters.mentions("@a+b", "a+b"))
    }

    @Test fun globalSearchEncodesQueriesAndParsesLiveAndOfflineChannelsAndCategories() {
        assertEquals("chess & games", KickSearch.url("chess & games").toHttpUrl().queryParameter("searched_word"))
        assertNull(KickSearch.query("ab"))
        assertNull(KickSearch.query("https://kick.com/channel/videos"))
        assertEquals("channel", KickSearch.query("@channel"))
        val result = KickSearch.parse(AppJson.parseToJsonElement("""{"channels":[{"slug":"live-channel","isLive":true,"user":{"username":"Live channel","profilePic":"https://example.com/avatar.png"}},{"slug":"offline-channel"}],"categories":[{"id":10,"slug":"category","name":"A category","banner":{"src":"https://example.com/image.png"}}]}"""))
        assertTrue(result.channels.first().live)
        assertFalse(result.channels.last().live)
        assertEquals("Live channel", result.channels.first().name)
        assertEquals(10L, result.categories.single().id)
        assertThrows(IOException::class.java) { KickSearch.parse(AppJson.parseToJsonElement("{}")) }
    }

    @Test fun alertsIgnoreInitialStateAndDuplicatesButDetectNewSessionsEvenWithoutAnOfflinePoll() {
        assertFalse(LiveAlertPolicy.shouldNotify(null, "session-1"))
        assertFalse(LiveAlertPolicy.shouldNotify("session-1", "session-1"))
        assertFalse(LiveAlertPolicy.shouldNotify("session-1", "offline"))
        assertTrue(LiveAlertPolicy.shouldNotify("offline", "session-1"))
        assertTrue(LiveAlertPolicy.shouldNotify("session-1", "session-2"))
        val channel = KickChannel(1, 1, 1, "channel", "Channel", null, null, true, "Stream", "", 0, liveSessionId = "new-session")
        assertEquals("new-session", LiveAlertPolicy.session(channel))
        assertEquals("offline", LiveAlertPolicy.session(channel.copy(isLive = false)))
    }
}
