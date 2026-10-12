package app.kickpocket

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.kickpocket.ui.FollowButton
import app.kickpocket.ui.PuntTheme
import app.kickpocket.ui.StreamLayout
import app.kickpocket.data.AppPreferences
import app.kickpocket.data.KickChannel
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FollowButtonSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun offlineChannelHeaderShowsFollowInPortraitAndLandscape() {
        val viewModel = ViewModelProvider(compose.activity)[StreamViewModel::class.java]
        val landscape = mutableStateOf(false)
        val fullscreen = mutableStateOf(false)
        var logins = 0
        val channel = KickChannel(100, 101, 102, "channel", "Channel", null, null, false, "Offline", "", 0)
        compose.runOnUiThread {
            compose.activity.setContent {
                PuntTheme {
                    StreamLayout(viewModel, StreamState(channel = channel), AppPreferences(), landscape.value,
                        fullscreen.value, false, onFullscreen = {}, onSettings = {}, onLogin = { logins++ })
                }
            }
        }
        compose.onNodeWithContentDescription("Sign in to follow Channel").assertIsDisplayed().performClick()
        assertEquals(1, logins)
        compose.runOnUiThread { landscape.value = true }
        compose.onNodeWithContentDescription("Sign in to follow Channel").assertIsDisplayed()
        compose.runOnUiThread { fullscreen.value = true }
        compose.onNodeWithContentDescription("Sign in to follow Channel").assertDoesNotExist()
    }

    @Test fun followControlSupportsLoginLoadingToggleAndRetry() {
        val state = mutableStateOf(FollowState())
        val signedIn = mutableStateOf(false)
        var logins = 0
        var mutations = 0
        var retries = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                PuntTheme {
                    Column {
                        FollowButton(state.value, signedIn.value, "Channel",
                            onToggle = { mutations++; state.value = FollowState(following = state.value.following != true) },
                            onRetry = { retries++ }, onLogin = { logins++ })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Sign in to follow Channel").performClick()
        assertEquals(1, logins)
        assertEquals(0, mutations)
        compose.runOnUiThread { signedIn.value = true; state.value = FollowState(checking = true) }
        compose.onNodeWithText("Checking…").assertIsNotEnabled()
        compose.runOnUiThread { state.value = FollowState(following = false) }
        compose.onNodeWithContentDescription("Follow Channel").performClick()
        compose.onNodeWithText("Following").assertIsDisplayed()
        compose.onNodeWithContentDescription("Unfollow Channel").performClick()
        compose.onNodeWithText("Follow").assertIsDisplayed()
        assertEquals(2, mutations)
        compose.runOnUiThread { state.value = FollowState(following = false, changing = true) }
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.runOnUiThread { state.value = FollowState(error = "Connection lost") }
        compose.onNodeWithContentDescription("Retry follow status for Channel").performClick()
        assertEquals(1, retries)
        assertEquals(2, mutations)
    }
}
