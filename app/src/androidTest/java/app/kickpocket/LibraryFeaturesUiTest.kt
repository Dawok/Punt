package app.kickpocket

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.kickpocket.data.*
import app.kickpocket.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryFeaturesUiTest {
    @get:Rule val compose = createComposeRule()
    private val recording = ChannelRecording("12345678-1234-1234-1234-123456789abc", RecordingKind.Vod, "channel", "Saved video")

    @Test fun libraryResumesSavesAndOpensBookmarkTimestamps() {
        val preferences = mutableStateOf(AppPreferences(
            watchHistory = listOf(WatchProgress(recording, 20_000, 60_000, 100)), watchLater = listOf(recording),
            bookmarks = listOf(VideoBookmark(recording, 10_000, 100))))
        var opened: ChannelRecording? = null
        var position: Long? = null
        compose.setContent { PuntTheme {
            LibraryDialog(preferences.value, { item, time -> opened = item; position = time },
                { preferences.value = preferences.value.copy(watchHistory = emptyList()) },
                { preferences.value = preferences.value.copy(watchLater = emptyList()) },
                { preferences.value = preferences.value.copy(bookmarks = emptyList()) }, {})
        } }
        compose.onNodeWithText("Saved video").performClick()
        assertEquals(recording, opened)
        assertEquals(20_000L, position)
        compose.onNodeWithText("Bookmarks").performClick()
        compose.onNodeWithText("Saved video").performClick()
        assertEquals(10_000L, position)
        compose.onNodeWithText("Watch later").performClick()
        compose.onNodeWithContentDescription("Remove Saved video").performClick()
        compose.onNodeWithText("Save a VOD or clip to watch it later.").assertIsDisplayed()
    }

    @Test fun continueWatchingHidesLegacyClipsAndRemovesVideosWithoutOpeningThem() {
        val clip = recording.copy(id = "clip_ABC", kind = RecordingKind.Clip, title = "Old clip")
        val preferences = mutableStateOf(AppPreferences(watchHistory = listOf(
            WatchProgress(clip, 10_000, 60_000, 100), WatchProgress(recording, 20_000, 120_000, 200))))
        var opened = false
        compose.setContent { PuntTheme {
            ContinueWatching(preferences.value, { _, _ -> opened = true }, { item ->
                preferences.value = preferences.value.copy(watchHistory = preferences.value.watchHistory.filterNot {
                    VideoLibrary.key(it.recording) == VideoLibrary.key(item)
                })
            })
        } }
        compose.onNodeWithText("Old clip").assertDoesNotExist()
        compose.onNodeWithText("Saved video").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Saved video from Continue watching").performClick()
        compose.onNodeWithText("Continue watching").assertDoesNotExist()
        assertFalse(opened)
        assertEquals(listOf(clip), preferences.value.watchHistory.map { it.recording })
    }

    @Test fun remoteSearchResultsOpenChannelsAndCategories() {
        var opened: String? = null
        var category: CategoryCard? = null
        val result = KickSearchResult(listOf(SearchChannel("channel", "Remote channel", null, false)),
            listOf(CategoryCard(1, "category", "Remote category", null, 0)))
        compose.setContent { PuntTheme {
            SearchResults(KickSearchState("remote", result = result), { opened = it }, { category = it }, {})
        } }
        compose.onNodeWithText("Remote channel").performClick()
        assertEquals("channel", opened)
        compose.onNodeWithText("Remote category").performClick()
        assertEquals(1L, category?.id)
    }
}
