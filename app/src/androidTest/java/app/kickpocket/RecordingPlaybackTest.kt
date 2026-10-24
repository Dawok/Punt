package app.kickpocket

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.kickpocket.data.ChannelRecording
import app.kickpocket.data.KickChannel
import app.kickpocket.data.RecordingKind
import app.kickpocket.ui.PuntTheme
import app.kickpocket.ui.VideoPlayer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecordingPlaybackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mp4RecordingOnAnOfflineChannelHasASeekableTimelineWithoutLiveControls() {
        val app = ApplicationProvider.getApplicationContext<PuntApplication>()
        val file = app.cacheDir.resolve("recording-fixture.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("recording.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val url = Uri.fromFile(file).toString()
        val channel = KickChannel(1, 1, 1, "channel", "Channel", null, null, false, "Offline", "", 0)
        val recording = ChannelRecording("clip_fixture", RecordingKind.Clip, "channel", "Recorded clip", playbackUrl = url)
        compose.setContent { PuntTheme { VideoPlayer(channel, app.playback, false, {}, recording = recording) } }
        try {
            compose.runOnIdle { app.playback.load(url, false, startPositionMs = 10_000); app.playback.player.pause() }
            compose.waitUntil(60_000) {
                var ready = false
                compose.runOnIdle { ready = app.playback.player.playbackState == Player.STATE_READY }
                ready
            }
            compose.runOnIdle {
                val player = app.playback.player
                assertFalse(player.isCurrentMediaItemLive)
                assertTrue(player.isCurrentMediaItemSeekable)
                assertTrue(player.duration >= 59_000)
                assertTrue(player.currentPosition >= 10_000)
                player.seekTo(30_000)
                assertEquals(30_000L, player.currentPosition)
            }
            compose.onNodeWithTag("go-live").assertDoesNotExist()
        } finally {
            compose.runOnIdle { app.playback.stop() }
            file.delete()
        }
    }
}
