package app.kickpocket

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import app.kickpocket.data.ChannelInput
import app.kickpocket.data.ChatConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Optional real-service check: pass -e kickChannel CHANNEL to the instrumentation runner. */
class LiveChannelSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensRealChannelAndConnectsChat() {
        val input = InstrumentationRegistry.getArguments().getString("kickChannel")
        assumeTrue("Pass kickChannel to check a real channel.", !input.isNullOrBlank())
        val slug = ChannelInput.normalize(requireNotNull(input))
        val viewModel = ViewModelProvider(compose.activity)[StreamViewModel::class.java]
        compose.runOnUiThread { viewModel.open(slug) }
        compose.waitUntil(45_000) { viewModel.state.value.channel != null || viewModel.state.value.error != null }
        assertTrue(viewModel.state.value.error ?: "Channel did not open.", viewModel.state.value.channel != null)
        compose.waitUntil(25_000) { viewModel.connection.value == ChatConnection.Connected }
        compose.waitUntil(30_000) { viewModel.state.value.emotes.isNotEmpty() }
        assertTrue(viewModel.state.value.emotes.isNotEmpty())
        if (viewModel.state.value.channel?.isLive == true) {
            compose.waitUntil(45_000) {
                var ready = false
                compose.runOnUiThread { ready = viewModel.player.player.playbackState == Player.STATE_READY && viewModel.player.player.videoSize.width > 0 }
                ready
            }
        }
    }
}
