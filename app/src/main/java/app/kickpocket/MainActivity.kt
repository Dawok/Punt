package app.kickpocket

import android.os.Bundle
import android.os.Build
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Rational
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.kickpocket.ui.PuntApp
import app.kickpocket.ui.PuntTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : ComponentActivity() {
    private val stream: StreamViewModel by viewModels()
    private val browse: BrowseViewModel by viewModels()
    private val mutablePip = MutableStateFlow(false)
    val pip = mutablePip.asStateFlow()
    private var sourceRect: Rect? = null
    private var pipSuppressed = false
    private val supportsPip by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    private val pipAction = "app.kickpocket.PIP_TOGGLE_PLAYBACK"
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == pipAction && isInPictureInPictureMode) {
                val player = stream.player.player
                if (player.playWhenReady) player.pause() else player.play()
                updatePipParams()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(pipAction), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch { combine(stream.state, stream.player.state) { _, _ -> Unit }.collect { updatePipParams() } }
        setContent { PuntTheme { PuntApp(stream, browse, pip) } }
    }

    override fun onStart() { super.onStart(); stream.foreground(true) }
    override fun onStop() { stream.foreground(false); super.onStop() }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && pipEligible()) {
            try { enterPictureInPictureMode(pipParams()) } catch (_: IllegalStateException) { /* PiP disabled by the device. */ }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        mutablePip.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) stream.foreground(true)
    }

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (Build.VERSION.SDK_INT >= 35 && pipState.isTransitioningToPip) mutablePip.value = true
    }

    fun suppressPip(suppressed: Boolean) { pipSuppressed = suppressed; updatePipParams() }

    fun videoBounds(view: View) {
        val rect = Rect()
        val surface = (view as? androidx.media3.ui.PlayerView)?.videoSurfaceView ?: view
        if (surface.getGlobalVisibleRect(rect) && rect.width() > 0 && rect.height() > 0 && !mutablePip.value) {
            sourceRect = rect
            updatePipParams()
        }
    }

    private fun pipEligible(): Boolean = supportsPip && !pipSuppressed &&
        stream.state.value.channel?.isLive == true && stream.state.value.channel?.playbackUrl != null &&
        stream.player.state.value.playWhenReady && stream.player.state.value.error == null &&
        (stream.player.state.value.playing || stream.player.state.value.buffering)

    private fun pipParams(): PictureInPictureParams {
        val playing = stream.player.player.playWhenReady
        val title = if (playing) "Pause" else "Play"
        val pendingIntent = PendingIntent.getBroadcast(this, 0, Intent(pipAction).setPackage(packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val action = RemoteAction(Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), title, title, pendingIntent)
        return PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setActions(listOf(action)).apply {
            sourceRect?.let { setSourceRectHint(it) }
            if (Build.VERSION.SDK_INT >= 31) { setAutoEnterEnabled(pipEligible()); setSeamlessResizeEnabled(true) }
        }.build()
    }

    private fun updatePipParams() {
        if (supportsPip) try { setPictureInPictureParams(pipParams()) } catch (_: IllegalStateException) { /* PiP unavailable. */ }
    }

    override fun onDestroy() { unregisterReceiver(pipReceiver); super.onDestroy() }
}
