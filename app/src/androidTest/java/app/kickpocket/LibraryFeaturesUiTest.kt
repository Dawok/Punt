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
    private val recording = ChannelRecording("clip_ABC", RecordingKind.Clip, "channel", "Saved clip")

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
        compose.onNodeWithText("Saved clip").performClick()
        assertEquals(recording, opened)
        assertEquals(20_000L, position)
        compose.onNodeWithText("Bookmarks").performClick()
        compose.onNodeWithText("Saved clip").performClick()
        assertEquals(10_000L, position)
        compose.onNodeWithText("Watch later").performClick()
        compose.onNodeWithContentDescription("Remove Saved clip").performClick()
        compose.onNodeWithText("Save a VOD or clip to watch it later.").assertIsDisplayed()
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
