package app.kickpocket

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PictureInPictureSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun switchingAppsEntersPipKeepsVideoPlayingAndRestoresChat() {
        assumeTrue(compose.activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
        val stream = ViewModelProvider(compose.activity)[StreamViewModel::class.java]
        compose.onNodeWithText("Try a sample stream").performClick()
        val startupTimeout = InstrumentationRegistry.getArguments().getString("videoStartupMs")?.toLongOrNull()
            ?.coerceIn(1_000, 180_000) ?: 60_000L
        compose.waitUntil(startupTimeout) {
            var ready = false
            compose.runOnUiThread { ready = stream.player.player.playbackState == Player.STATE_READY && stream.player.player.videoSize.width > 0 }
            ready
        }
        var originalView: PlayerView? = null
        compose.runOnUiThread { originalView = requireNotNull(findPlayer(compose.activity.window.decorView)) }
        shell("input keyevent 3")
        compose.waitUntil(15_000) { compose.activity.isInPictureInPictureMode }
        compose.onNodeWithText("Sample chat").assertDoesNotExist()
        var position = 0L
        compose.runOnUiThread {
            assertSame(originalView, findPlayer(compose.activity.window.decorView))
            assertTrue(stream.player.player.playWhenReady)
            position = stream.player.player.currentPosition
        }
        compose.waitUntil(10_000) {
            var advanced = false
            compose.runOnUiThread { advanced = stream.player.player.currentPosition > position + 500 && stream.player.player.isPlaying }
            advanced
        }
        resumeApp()
        compose.onNodeWithText("Sample chat").assertIsDisplayed()
        compose.runOnUiThread { assertTrue(stream.player.player.playWhenReady); assertSame(originalView, findPlayer(compose.activity.window.decorView)) }
    }

    @Test fun pausedPlaybackDoesNotEnterPip() {
        assumeTrue(compose.activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
        val stream = ViewModelProvider(compose.activity)[StreamViewModel::class.java]
        compose.onNodeWithText("Try a sample stream").performClick()
        compose.runOnUiThread { stream.player.player.pause() }
        compose.waitForIdle()
        shell("input keyevent 3")
        // Await the actual stop; checking immediately after the key press would race PiP entry.
        compose.waitUntil(15_000) { !compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
        assertFalse(compose.activity.isInPictureInPictureMode)
        resumeApp()
        compose.onNodeWithText("Sample chat").assertIsDisplayed()
        compose.runOnUiThread { assertFalse(stream.player.player.playWhenReady) }
    }

    private fun shell(command: String) {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findPlayer(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun resumeApp() {
        shell("am start -W -n app.kickpocket/.MainActivity")
        compose.waitUntil(60_000) {
            !compose.activity.isInPictureInPictureMode && compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodesWithText("Sample chat").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }
}
