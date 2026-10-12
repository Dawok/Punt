package app.kickpocket.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VideoQuality(val id: String, val label: String, val group: TrackGroup, val index: Int)
data class PlayerState(
    val playWhenReady: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    val qualities: List<VideoQuality> = emptyList(),
    val selectedQuality: String = "Auto",
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerController(context: Context) {
    private val mutable = MutableStateFlow(PlayerState())
    val state = mutable.asStateFlow()
    private var foreground = false
    private var resumePlayback = false

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(
            DefaultHttpDataSource.Factory().setUserAgent("Punt/${app.kickpocket.BuildConfig.VERSION_NAME} (Android)")
                .setDefaultRequestProperties(mapOf("Referer" to "https://kick.com/", "Origin" to "https://kick.com")),
        ))
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .build().apply {
            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    mutable.update { it.copy(playWhenReady = playWhenReady) }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    mutable.update { it.copy(playing = isPlaying) }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    mutable.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING, playWhenReady = player.playWhenReady) }
                }
                override fun onPlayerError(error: PlaybackException) {
                    mutable.update { it.copy(buffering = false, error = "Playback stopped. Check your connection, then retry the stream.") }
                }
                override fun onTracksChanged(tracks: Tracks) {
                    val options = tracks.groups.flatMapIndexed { groupIndex, group ->
                        if (group.type != C.TRACK_TYPE_VIDEO) emptyList()
                        else (0 until group.length).filter { group.isTrackSupported(it) }.mapNotNull { index ->
                            val format = group.getTrackFormat(index)
                            if (format.height <= 0) null else VideoQuality(
                                id = "$groupIndex:$index",
                                label = "${format.height}p" + if (format.frameRate >= 50) "60" else "",
                                group = group.mediaTrackGroup,
                                index = index,
                            )
                        }
                    }.distinctBy { it.label }.sortedByDescending { it.group.getFormat(it.index).height }
                    mutable.update { it.copy(qualities = options) }
                }
            })
        }

    fun load(url: String, live: Boolean) {
        mutable.value = PlayerState(buffering = true)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        val item = MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8)
        if (live) item.setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(6_000).setMaxPlaybackSpeed(1.03f).build())
        player.setMediaItem(item.build())
        player.prepare()
        resumePlayback = true
        player.playWhenReady = foreground
        mutable.update { it.copy(playWhenReady = player.playWhenReady) }
    }

    fun quality(option: VideoQuality?) {
        val parameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (option != null) parameters.addOverride(TrackSelectionOverride(option.group, option.index))
        player.trackSelectionParameters = parameters.build()
        mutable.update { it.copy(selectedQuality = option?.label ?: "Auto") }
    }

    fun foreground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        if (active) {
            if (resumePlayback && player.mediaItemCount > 0) player.play()
        } else {
            resumePlayback = player.playWhenReady
            player.pause()
        }
    }

    fun stop() {
        resumePlayback = false
        player.stop()
        player.clearMediaItems()
        mutable.value = PlayerState()
    }

    fun release() = player.release()
}

