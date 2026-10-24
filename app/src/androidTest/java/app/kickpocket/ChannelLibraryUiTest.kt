package app.kickpocket

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.kickpocket.data.*
import app.kickpocket.ui.ChannelRecordings
import app.kickpocket.ui.ChannelTabs
import app.kickpocket.ui.PuntTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChannelLibraryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tabsCardsAndClipPaginationOpenTheSelectedRecording() {
        val entry = ChannelRecording("clip_ABC", RecordingKind.Clip, "channel", "A recorded moment", durationSeconds = 65)
        val state = mutableStateOf(ChannelLibraryState(tab = ChannelTab.Clips, clips = listOf(entry), clipsCursor = "next"))
        var opened: ChannelRecording? = null
        var more = 0
        compose.setContent {
            PuntTheme {
                Column(Modifier.height(420.dp)) {
                    ChannelTabs(state.value.tab) { state.value = state.value.copy(tab = it) }
                    ChannelRecordings(state.value, opened?.id, { opened = it }, {}, { more++ })
                }
            }
        }
        compose.onNodeWithText("1:05").assertIsDisplayed()
        compose.onNodeWithText("A recorded moment").performClick()
        assertEquals(entry, opened)
        compose.onNodeWithText("Load more clips").performClick()
        assertEquals(1, more)
        compose.onNodeWithTag("channel-tab-videos").performClick()
        compose.onNodeWithText("No past broadcasts available.").assertIsDisplayed()
    }

    @Test fun loadingFailureAndEmptyStatesStayDistinctAndRetryable() {
        val state = mutableStateOf(ChannelLibraryState(tab = ChannelTab.Videos, loading = true))
        var retries = 0
        compose.setContent {
            PuntTheme { ChannelRecordings(state.value, null, {}, { retries++ }, {}, Modifier.height(320.dp)) }
        }
        compose.onNodeWithText("No past broadcasts available.").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(loading = false, error = "Could not load recordings.") }
        compose.onNodeWithText("Could not load recordings.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        compose.runOnIdle { state.value = state.value.copy(error = null) }
        compose.onNodeWithText("No past broadcasts available.").assertIsDisplayed()
    }
}
