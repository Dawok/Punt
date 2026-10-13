package app.kickpocket

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.Player
import app.kickpocket.data.RecentChannel
import app.kickpocket.data.PreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun searchSuggestsRecentChannelsAndPinningPersistsWithoutLogin() {
        val app = compose.activity.application as PuntApplication
        val previous = app.preferences.preferences.value
        try {
            compose.runOnUiThread {
                app.preferences.update { it.copy(recentChannels = listOf(RecentChannel("channel-fixture", "Test channel")), favoriteChannels = emptyList()) }
            }
            compose.onNodeWithText("Channel name or Kick link").performTextInput("channel-fixture")
            compose.onNodeWithText("channel-fixture · Recent").assertIsDisplayed()
            compose.onNodeWithContentDescription("Pin Test channel").performClick()
            compose.onNodeWithText("channel-fixture · Pinned").assertIsDisplayed()
            assertEquals(listOf("channel-fixture"), PreferenceStore(compose.activity).preferences.value.favoriteChannels.map { it.slug })
            compose.onNodeWithText("Channel name or Kick link").performTextClearance()
            compose.onNodeWithContentDescription("Unpin Test channel").performClick()
            assertTrue(app.preferences.preferences.value.favoriteChannels.isEmpty())
        } finally { compose.runOnUiThread { app.preferences.update { previous } } }
    }

    @Test fun separateQualityPreferencesPersistAcrossStoreRecreation() {
        val app = compose.activity.application as PuntApplication
        val previous = app.preferences.preferences.value
        try {
            compose.runOnUiThread { app.preferences.update { it.copy(wifiQuality = 1080, mobileQuality = 480) } }
            val restored = PreferenceStore(compose.activity).preferences.value
            assertEquals(1080, restored.wifiQuality)
            assertEquals(480, restored.mobileQuality)
        } finally { compose.runOnUiThread { app.preferences.update { previous } } }
    }

    @Test fun opensPlaybackFixtureWithChatAndReturnsHome() {
        compose.runOnUiThread { openPlaybackFixture(compose.activity) }
        compose.onNodeWithText("Test stream").assertIsDisplayed()
        compose.onNodeWithText("Sample chat").assertIsDisplayed()
        compose.onNodeWithText("Simulated").assertIsDisplayed()
        compose.onNodeWithText("Read-only chat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to channels").performClick()
        compose.onNodeWithContentDescription("Watch channel").assertIsDisplayed()
        compose.onNodeWithText("Try a sample stream").assertDoesNotExist()
    }

    @Test fun rejectsForeignChannelLinksWithoutNetworkRequest() {
        compose.onNodeWithText("Channel name or Kick link").performTextInput("https://example.com/user")
        compose.onNodeWithContentDescription("Watch channel").performClick()
        compose.onNodeWithText("Use a channel link from kick.com.").assertIsDisplayed()
    }

    @Test fun settingsTogglePersistsAndHistoryCanBeCleared() {
        val app = compose.activity.application as PuntApplication
        val previous = app.preferences.preferences.value.animatedEmotes
        compose.runOnUiThread {
            app.preferences.update { it.copy(recentChannels = listOf(RecentChannel("user", "User"))) }
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Animated emotes").performClick()
        compose.onNodeWithText("Clear recent channels").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Watch channel").assertIsDisplayed()
        assertEquals(!previous, app.preferences.preferences.value.animatedEmotes)
        assertTrue(app.preferences.preferences.value.recentChannels.isEmpty())
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Animated emotes").performClick()
        compose.onNodeWithText("Done").performClick()
    }

    @Test fun fullscreenKeepsPlayerAndBackReturnsToChat() {
        val viewModel = ViewModelProvider(compose.activity)[StreamViewModel::class.java]
        compose.runOnUiThread { openPlaybackFixture(compose.activity) }
        val player = viewModel.player.player
        // This checks navigation and instance retention; playback has its own checks.
        // Pausing avoids continuous software video rendering competing with rotation/idling.
        compose.runOnUiThread { player.pause() }
        if (compose.onAllNodesWithContentDescription("Fullscreen").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("video-player").performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, height / 2f)) }
        }
        compose.onNodeWithContentDescription("Fullscreen").performClick()
        compose.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed().performClick()
        compose.onNodeWithText("Sample chat").assertIsDisplayed()
        assertTrue(player === viewModel.player.player)
    }

    @Test fun fixtureVideoReachesReadyAndDecodesVideo() {
        val viewModel = ViewModelProvider(compose.activity)[StreamViewModel::class.java]
        compose.runOnUiThread { openPlaybackFixture(compose.activity) }
        val startupTimeout = InstrumentationRegistry.getArguments().getString("videoStartupMs")?.toLongOrNull()
            ?.coerceIn(1_000, 180_000) ?: 60_000L
        try { compose.waitUntil(timeoutMillis = startupTimeout) {
            var ready = false
            compose.runOnUiThread {
                ready = viewModel.player.player.playbackState == Player.STATE_READY && viewModel.player.player.videoSize.width > 0
            }
            ready
        } } catch (error: ComposeTimeoutException) {
            compose.runOnUiThread {
                val player = viewModel.player.player
                throw AssertionError("Playback state=${player.playbackState}, video=${player.videoSize.width}x${player.videoSize.height}, playWhenReady=${player.playWhenReady}, error=${player.playerError?.errorCodeName}, cause=${player.playerError?.cause}", error)
            }
        }
        compose.runOnUiThread { assertTrue(viewModel.player.player.isPlaying) }
    }
}
