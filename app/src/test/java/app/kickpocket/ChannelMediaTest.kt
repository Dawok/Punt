package app.kickpocket

import app.kickpocket.data.*
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ChannelMediaTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private fun json(value: String) = AppJson.parseToJsonElement(value)

    @Test fun readsCurrentVodMetadataWithoutTreatingDurationAsMilliseconds() {
        val page = MediaParser.videos(json("""{"data":[{"id":"$id","title":"Past stream","duration":7200,"viewer_count":123,"start_time":"2026-01-01T12:00:00Z","status":"sub_only","thumbnail":{"src":"https://example.com/video.jpg"}},{"id":"invalid"}]}"""), "channel")
        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals(RecordingKind.Vod, item.kind)
        assertEquals(7200, item.durationSeconds)
        assertEquals(123, item.views)
        assertEquals("sub_only", item.availability)
        assertNull(item.playbackUrl)
        assertNull(page.cursor)
    }

    @Test fun readsClipPaginationAndLegacyDetailWithAnMp4Source() {
        val page = MediaParser.clips(json("""{"data":{"clips":[{"id":"clip_ABC","title":"A clip","duration":30.5,"views_count":42,"playback_url":"https://example.com/playlist.m3u8"}],"cursor":"next+/="}}"""), "channel")
        assertEquals("next+/=", page.cursor)
        assertEquals(30, page.items.single().durationSeconds)
        assertEquals("channel", page.items.single().channelSlug)
        val clip = MediaParser.clip(json("""{"clip":{"id":"clip_ABC","channel":{"slug":"owner"},"clip_url":"https://example.com/clip.mp4","views":12}}"""))
        assertEquals("owner", clip.channelSlug)
        assertEquals("https://example.com/clip.mp4", clip.playbackUrl)
        assertEquals(12, clip.views)
    }

    @Test fun vodPlaybackUsesTheVodSourceAndRefreshesItsMetadata() {
        val recording = ChannelRecording(id, RecordingKind.Vod, "channel", "Past broadcast")
        val result = MediaParser.playback(json("""{"playback_url":{"live":"https://example.com/live.m3u8","vod":"https://example.com/vod.m3u8?token=example"},"video_session":{"video_title":"Recorded stream","video_duration":900}}"""), recording)
        assertEquals("https://example.com/vod.m3u8?token=example", result.playbackUrl)
        assertEquals("Recorded stream", result.title)
        assertEquals(900, result.durationSeconds)
        for (data in listOf("""{"playback_url":{"live":"https://example.com/live.m3u8"}}""", """{"playback_url":{"vod":"http://example.com/vod.m3u8"}}"""))
            assertThrows(IOException::class.java) { MediaParser.playback(json(data), recording) }
    }

    @Test fun malformedResponsesAndUnavailableMediaAreErrorsRatherThanEmptyLibraries() {
        assertThrows(IOException::class.java) { MediaParser.videos(json("{}"), "channel") }
        assertThrows(IOException::class.java) { MediaParser.clips(json("{\"data\":{}}"), "channel") }
        assertThrows(IOException::class.java) { MediaParser.clip(json("{\"clip\":{\"id\":\"clip_ABC\",\"channel\":{\"slug\":\"channel\"}}}")) }
        assertTrue(MediaParser.videos(json("{\"data\":[]}"), "channel").items.isEmpty())
    }

    @Test fun requestsEncodeClipCursorsAndKeepPlaybackCredentialsOnTheExactEndpoint() {
        val cursor = "next+/="
        val url = MediaRequests.clips(10, cursor).toHttpUrl()
        assertEquals(cursor, url.queryParameter("cursor"))
        assertEquals("all", url.queryParameter("time"))
        assertEquals("views", url.queryParameter("sort"))
        val playback = MediaRequests.playback(id).toHttpUrl()
        val request = SessionRequests.build(playback, "session_token=example-token", "test", MediaRequests.playbackBody)
        assertEquals("POST", request.method)
        assertEquals("Bearer example-token", request.header("Authorization"))
        for (bad in listOf("https://web.kick.com/api/v1/stream/$id/other", "https://example.com/api/v1/stream/$id/playback"))
            assertThrows(IllegalArgumentException::class.java) { SessionRequests.build(bad.toHttpUrl(), "session_token=example-token", "test", "{}") }
        assertThrows(IllegalArgumentException::class.java) { MediaRequests.clip("../other") }
        assertThrows(IllegalArgumentException::class.java) { MediaRequests.playback("../other") }
    }

    @Test fun clipFiltersApplyToEveryPageAndChangingThemDropsThePreviousCursorAndResults() {
        for (sort in ClipSort.entries) for (time in ClipTimeRange.entries) {
            val url = MediaRequests.clips(10, "next+/=", sort, time).toHttpUrl()
            assertEquals(sort.value, url.queryParameter("sort"))
            assertEquals(time.value, url.queryParameter("time"))
            assertEquals("next+/=", url.queryParameter("cursor"))
        }
        val clip = ChannelRecording("clip_ABC", RecordingKind.Clip, "channel", "Clip")
        val vod = ChannelRecording(id, RecordingKind.Vod, "channel", "Video")
        val state = ChannelLibraryState(tab = ChannelTab.Clips, videos = listOf(vod), clips = listOf(clip),
            clipsCursor = "old-page", loaded = setOf(ChannelTab.Clips, ChannelTab.Videos), loading = true, error = "Old error")
        assertSame(state, state.withClipFilters(state.clipSort, state.clipTimeRange))
        val changed = state.withClipFilters(ClipSort.Newest, ClipTimeRange.Week)
        assertEquals(ClipSort.Newest, changed.clipSort)
        assertEquals(ClipTimeRange.Week, changed.clipTimeRange)
        assertTrue(changed.clips.isEmpty())
        assertNull(changed.clipsCursor)
        assertFalse(changed.loading)
        assertNull(changed.error)
        assertEquals(setOf(ChannelTab.Videos), changed.loaded)
        assertEquals(listOf(vod), changed.videos)
    }
}
