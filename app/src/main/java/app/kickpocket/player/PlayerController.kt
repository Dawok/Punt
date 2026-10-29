package app.kickpocket.player

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.kickpocket.data.PreferenceStore
import app.kickpocket.data.ChannelRecording
import app.kickpocket.data.VideoLibrary
import androidx.media3.common.PlaybackParameters

data class VideoQuality(val id: String, val label: String, val group: TrackGroup, val index: Int)
data class PlayerState(
    val playWhenReady: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    val qualities: List<VideoQuality> = emptyList(),
    val selectedQuality: String = "Auto",
    val liveOffsetMs: Long? = null,
    val recovering: Boolean = false,
    val recoveryAttempt: Int = 0,
    val speed: Float = 1f,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerController(private val context: Context, private val preferences: PreferenceStore) {
    private val mutable = MutableStateFlow(PlayerState())
    val state = mutable.asStateFlow()
    private var foreground = false
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var watchingNetwork = false
    private var mobileNetwork = false
    private var currentNetwork: Network? = null
    private val recovery = PlaybackRecovery()
    private val recoveryWakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Punt:playback-recovery").apply { setReferenceCounted(false) }
    private var pendingRetry: Runnable? = null
    private var loadedLive = false
    private var activeRecording: ChannelRecording? = null
    private var lastCheckpointAt = 0L
    private var recoverableFailure = false
    private var lastPosition = 0L
    private var lastProgressAt = 0L
    private var stableSince = 0L
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            progressHandler.post {
                mobileNetwork = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                applyQuality()
                val changed = currentNetwork != network
                currentNetwork = network
                if (changed && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                    engine?.playWhenReady == true && (recoverableFailure || engine?.playbackState == Player.STATE_BUFFERING)) {
                    pendingRetry?.let { progressHandler.removeCallbacks(it); progressHandler.postDelayed(it, 250) }
                        ?: scheduleRecovery()
                }
            }
        }
        override fun onLost(network: Network) {
            progressHandler.post { if (currentNetwork == network) currentNetwork = null }
        }
    }

    private var engine: ExoPlayer? = null
    val player: ExoPlayer get() = engine ?: createPlayer().also {
        engine = it
        watchNetwork()
        applyQuality()
    }
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progress = object : Runnable {
        override fun run() {
            engine?.let { current ->
                mutable.update { it.copy(liveOffsetMs = current.currentLiveOffset.takeIf { offset -> offset != C.TIME_UNSET }) }
                val now = SystemClock.elapsedRealtime()
                if (current.isPlaying && now - lastCheckpointAt >= 5_000) checkpoint()
                if (current.currentPosition != lastPosition && current.isPlaying) {
                    lastPosition = current.currentPosition
                    lastProgressAt = now
                    if (stableSince == 0L) stableSince = now
                    if (now - stableSince >= 30_000) { recovery.reset(); recoverableFailure = false }
                } else {
                    if (!current.isPlaying) stableSince = 0L
                    val stalled = current.playbackState == Player.STATE_BUFFERING || recoverableFailure ||
                        (current.playbackState == Player.STATE_READY && current.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE)
                    if (stalled && current.playWhenReady && current.mediaItemCount > 0 && now - lastProgressAt >= 20_000) {
                        lastProgressAt = now
                        scheduleRecovery()
                    }
                }
                if (current.mediaItemCount > 0) progressHandler.postDelayed(this, 1_000)
            }
        }
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            preferences.preferences.collect { applyQuality() }
        }
    }

    private fun watchNetwork() {
        if (watchingNetwork) return
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        mobileNetwork = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback)
            watchingNetwork = true
        } catch (_: RuntimeException) { /* Playback still recovers through player events. */ }
    }

    private fun applyQuality() {
        val current = engine ?: return
        val settings = preferences.preferences.value
        val height = if (mobileNetwork) settings.mobileQuality else settings.wifiQuality
        current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(Int.MAX_VALUE, QualityPreferences.limit(height)).build()
        mutable.update { it.copy(selectedQuality = QualityPreferences.label(height)) }
    }

    private fun cancelRecovery() {
        pendingRetry?.let(progressHandler::removeCallbacks)
        pendingRetry = null
        if (recoveryWakeLock.isHeld) recoveryWakeLock.release()
        mutable.update { it.copy(recovering = false) }
    }

    private fun scheduleRecovery() {
        val current = engine ?: return
        if (pendingRetry != null || current.mediaItemCount == 0 || !current.playWhenReady) return
        val delay = recovery.nextDelayMs(current.playWhenReady)
        if (delay == null) {
            if (recoveryWakeLock.isHeld) recoveryWakeLock.release()
            mutable.update { it.copy(recovering = false, error = "Could not resume playback. Check your connection and retry.") }
            return
        }
        mutable.update { it.copy(recovering = true, recoveryAttempt = recovery.attempts, error = null) }
        recoveryWakeLock.acquire(delay + 25_000)
        val retry = Runnable {
            pendingRetry = null
            if (engine !== current || !current.playWhenReady || current.mediaItemCount == 0) return@Runnable
            val item = current.currentMediaItem ?: return@Runnable
            lastProgressAt = SystemClock.elapsedRealtime()
            current.setMediaItem(item, if (loadedLive) C.TIME_UNSET else current.currentPosition)
            current.prepare()
        }
        pendingRetry = retry
        progressHandler.postDelayed(retry, delay)
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
                    if (!playWhenReady) { cancelRecovery(); checkpoint() }
                    else if (recoverableFailure && player.playbackState == Player.STATE_IDLE) scheduleRecovery()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    mutable.update { it.copy(playing = isPlaying) }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) checkpoint(ended = true)
                    mutable.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING, playWhenReady = player.playWhenReady) }
                    if (playbackState == Player.STATE_READY) {
                        cancelRecovery()
                        mutable.update { it.copy(error = null) }
                    }
                }
                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    mutable.update { it.copy(speed = playbackParameters.speed) }
                }
                override fun onPlayerError(error: PlaybackException) {
                    val httpStatus = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                    recoverableFailure = error.errorCode in setOf(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) ||
                        httpStatus == 408 || httpStatus == 429 || (httpStatus != null && httpStatus >= 500)
                    if (recoverableFailure && player.playWhenReady) {
                        scheduleRecovery()
                        return
                    }
                    cancelRecovery()
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

    fun load(url: String, live: Boolean, title: String = "Stream", artist: String = "Punt", startPositionMs: Long = 0,
        recording: ChannelRecording? = null) {
        checkpoint()
        activeRecording = recording?.copy(playbackUrl = null)
        lastCheckpointAt = SystemClock.elapsedRealtime()
        cancelRecovery()
        recovery.reset()
        recoverableFailure = false
        loadedLive = live
        lastPosition = 0
        lastProgressAt = SystemClock.elapsedRealtime()
        stableSince = 0
        context.startService(Intent(context, PlaybackService::class.java))
        mutable.value = PlayerState(buffering = true)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        applyQuality()
        val item = MediaItem.Builder().setUri(url)
            .setMediaId(recording?.let(VideoLibrary::key) ?: "live")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
        if (android.net.Uri.parse(url).lastPathSegment?.endsWith(".m3u8", ignoreCase = true) == true)
            item.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (live) item.setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(6_000).setMaxPlaybackSpeed(1.03f).build())
        player.setMediaItem(item.build(), if (live) C.TIME_UNSET else startPositionMs.coerceAtLeast(0))
        player.setPlaybackSpeed(if (live) 1f else preferences.preferences.value.playbackSpeed)
        player.prepare()
        player.playWhenReady = true
        mutable.update { it.copy(playWhenReady = player.playWhenReady) }
        progressHandler.removeCallbacks(progress)
        progressHandler.post(progress)
    }

    private fun checkpoint(ended: Boolean = false) {
        val recording = activeRecording ?: return
        val current = engine ?: return
        if (current.mediaItemCount == 0) return
        if (current.currentMediaItem?.mediaId != VideoLibrary.key(recording)) return
        preferences.checkpoint(recording, current.currentPosition, current.duration.takeIf { it != C.TIME_UNSET } ?: 0, ended)
        lastCheckpointAt = SystemClock.elapsedRealtime()
    }

    fun speed(value: Float, persist: Boolean = true) {
        if (loadedLive || !value.isFinite()) return
        val speed = value.coerceIn(0.5f, 2f)
        if (persist) preferences.update { it.copy(playbackSpeed = speed) }
        player.setPlaybackSpeed(speed)
    }

    fun seekBy(offsetMs: Long) {
        if (loadedLive) return
        val current = player
        if (!current.isCurrentMediaItemSeekable) return
        val maximum = current.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        current.seekTo((current.currentPosition + offsetMs).coerceIn(0, maximum))
        checkpoint()
    }

    fun quality(option: VideoQuality?) {
        val height = option?.group?.getFormat(option.index)?.height ?: 0
        preferences.update { if (mobileNetwork) it.copy(mobileQuality = height) else it.copy(wifiQuality = height) }
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
        cancelRecovery()
        recovery.reset()
        lastProgressAt = SystemClock.elapsedRealtime()
        mutable.update { it.copy(error = null) }
        player.seekToDefaultPosition()
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) player.prepare()
        player.play()
    }

    fun stop() {
        checkpoint()
        activeRecording = null
        cancelRecovery()
        progressHandler.removeCallbacks(progress)
        player.stop()
        player.clearMediaItems()
        mutable.value = PlayerState()
    }

    fun release() {
        checkpoint()
        activeRecording = null
        cancelRecovery()
        progressHandler.removeCallbacks(progress)
        if (watchingNetwork) {
            connectivity.unregisterNetworkCallback(networkCallback)
            watchingNetwork = false
            currentNetwork = null
        }
        engine?.release()
        engine = null
        mutable.value = PlayerState()
    }
}

