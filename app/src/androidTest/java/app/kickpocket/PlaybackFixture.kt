package app.kickpocket

import androidx.lifecycle.ViewModelProvider
import app.kickpocket.data.KickChannel

/** Playback content for instrumentation checks, excluded from the installed app. */
internal fun openPlaybackFixture(activity: MainActivity) {
    val stream = ViewModelProvider(activity)[StreamViewModel::class.java]
    stream.leave()
    val channel = KickChannel(
        id = 0, userId = 0, chatroomId = 0, slug = "test-stream", displayName = "Test stream",
        avatarUrl = null,
        playbackUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
        isLive = true, title = "Playback fixture", category = "", viewers = 0, isDemo = true,
    )
    (activity.application as PuntApplication).streamState.value = StreamState(channel = channel)
    stream.player.load(requireNotNull(channel.playbackUrl), false)
    stream.foreground(true)
}
