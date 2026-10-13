package app.kickpocket.player

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
    val liveOffsetMs: Long? = null,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerController(private val context: Context) {
    private val mutable = MutableStateFlow(PlayerState())
    val state = mutable.asStateFlow()
    private var foreground = false

    private var engine: ExoPlayer? = null
    val player: ExoPlayer get() = engine ?: createPlayer().also { engine = it }
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progress = object : Runnable {
        override fun run() {
            engine?.let { current ->
                mutable.update { it.copy(liveOffsetMs = current.currentLiveOffset.takeIf { offset -> offset != C.TIME_UNSET }) }
                if (current.mediaItemCount > 0) progressHandler.postDelayed(this, 1_000)
            }
        }
    }

    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(
            DefaultHttpDataSource.Factory().setUserAgent("Punt/${app.kickpocket.BuildConfig.VERSION_NAME} (Android)")
                .setDefaultRequestProperties(mapOf("Referer" to "https://kick.com/", "Origin" to "https://kick.com")),
        ))
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build().apply {
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    mutable.update { it.copy(liveOffsetMs = player.currentLiveOffset.takeIf { offset -> offset != C.TIME_UNSET }) }
                }
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
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        val resume = player.playWhenReady
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.playWhenReady = resume
                        return
                    }
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

    fun load(url: String, live: Boolean, title: String = "Stream", artist: String = "Punt") {
        context.startService(Intent(context, PlaybackService::class.java))
        mutable.value = PlayerState(buffering = true)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        val item = MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
        if (live) item.setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(6_000).setMaxPlaybackSpeed(1.03f).build())
        player.setMediaItem(item.build())
        player.prepare()
        player.playWhenReady = true
        mutable.update { it.copy(playWhenReady = player.playWhenReady) }
        progressHandler.removeCallbacks(progress)
        progressHandler.post(progress)
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
        // Keep audio playing while the screen is off; PiP remains foreground video.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !active).build()
    }

    fun goLive() {
        if (player.mediaItemCount == 0) return
        mutable.update { it.copy(error = null) }
        player.seekToDefaultPosition()
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) player.prepare()
        player.play()
    }

    fun stop() {
        progressHandler.removeCallbacks(progress)
        player.stop()
        player.clearMediaItems()
        mutable.value = PlayerState()
    }

    fun release() {
        progressHandler.removeCallbacks(progress)
        engine?.release()
        engine = null
        mutable.value = PlayerState()
    }
}

