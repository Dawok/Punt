package app.kickpocket.ui

import android.media.AudioManager
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.ui.PlayerView
import app.kickpocket.player.PlayerController
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
// Single taps call PlayerView.performClick through GestureDetector; native control buttons keep their own clicks.
@android.annotation.SuppressLint("ClickableViewAccessibility")
internal fun PlayerGestures(view: PlayerView?, controller: PlayerController, recorded: Boolean, onHint: (String) -> Unit) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val hint by rememberUpdatedState(onHint)
    DisposableEffect(activity) {
        val original = activity?.window?.attributes?.screenBrightness
        onDispose { if (original != null) activity.window.attributes = activity.window.attributes.apply { screenBrightness = original } }
    }
    DisposableEffect(view, recorded) {
        if (view == null) return@DisposableEffect onDispose { }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var boosted = false
        var previousSpeed = 1f
        var dragging = false
        var brightness = 0.5f
        var volume = 0f
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                dragging = false
                brightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 }
                    ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                return true
            }
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean { view.performClick(); return true }
            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (recorded) {
                    val offset = if (event.x < view.width / 2) -10_000L else 10_000L
                    controller.seekBy(offset); hint(if (offset < 0) "−10 seconds" else "+10 seconds")
                } else view.showController()
                return true
            }
            override fun onLongPress(event: MotionEvent) {
                if (recorded && !dragging) {
                    previousSpeed = controller.player.playbackParameters.speed
                    controller.speed(2f, persist = false); boosted = true; hint("2× while held")
                }
            }
            override fun onScroll(start: MotionEvent?, event: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (start == null || (!dragging && abs(event.y - start.y) <= abs(event.x - start.x))) return false
                dragging = true
                val fraction = distanceY / view.height.coerceAtLeast(1)
                if (start.x < view.width / 2) {
                    brightness = (brightness + fraction).coerceIn(0.02f, 1f)
                    activity?.window?.let { window -> window.attributes = window.attributes.apply { screenBrightness = brightness } }
                    hint("Brightness ${(brightness * 100).roundToInt()}%")
                } else {
                    volume = (volume + fraction * maximum).coerceIn(0f, maximum.toFloat())
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume.roundToInt(), 0)
                    hint("Volume ${(volume / maximum.coerceAtLeast(1) * 100).roundToInt()}%")
                }
                return true
            }
        })
        view.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (boosted) { controller.speed(previousSpeed, persist = false); boosted = false; hint("${previousSpeed}×") }
            }
            true
        }
        onDispose {
            view.setOnTouchListener(null)
            if (boosted) controller.speed(previousSpeed, persist = false)
        }
    }
}
