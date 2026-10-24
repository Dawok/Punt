package app.kickpocket.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import app.kickpocket.MainActivity
import app.kickpocket.data.KickChannel
import app.kickpocket.data.ChannelRecording
import app.kickpocket.player.PlayerController

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    channel: KickChannel,
    controller: PlayerController,
    loading: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    onFullscreen: () -> Unit = {},
    pip: Boolean = false,
    recording: ChannelRecording? = null,
    playbackError: String? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val error = state.error ?: playbackError
    var controlsVisible by remember { mutableStateOf(true) }
    var qualityMenu by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(view, state.playing, state.buffering, pip) {
        view.keepScreenOn = !pip && (state.playing || state.buffering)
        onDispose { view.keepScreenOn = false }
    }
    Box(modifier.testTag("video-player").background(Color.Black), contentAlignment = Alignment.Center) {
        if ((recording?.playbackUrl ?: channel.playbackUrl.takeIf { recording == null && channel.isLive }) != null) {
            AndroidView(
                factory = { context -> PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    player = controller.player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerAutoShow = true
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { controlsVisible = it == View.VISIBLE })
                    addOnLayoutChangeListener { playerView, _, _, _, _, _, _, _, _ -> (context as? MainActivity)?.videoBounds(playerView) }
                } },
                update = {
                    if (it.player !== controller.player) it.player = controller.player
                    it.setShowFastForwardButton(recording != null || channel.isDemo)
                    it.setShowRewindButton(recording != null || channel.isDemo)
                    it.useController = !pip
                    it.controllerShowTimeoutMs = if (qualityMenu) 0 else 3_000
                    if (pip) it.hideController() else if (qualityMenu) it.showController()
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!pip && !loading) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.LiveTv, null, tint = SubtleColor, modifier = Modifier.size(30.dp))
                Text(if (recording != null) "Recording unavailable" else if (!channel.isLive) "Channel is offline" else "Stream unavailable", color = Color.White,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Text(if (recording != null) "Retry or choose another recording." else "You can still hang out in chat.", color = SubtleColor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (!pip && controlsVisible && error == null) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                .padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (recording == null && channel.isLive && !channel.isDemo && channel.playbackUrl != null) {
                        val atLive = state.playWhenReady && state.liveOffsetMs?.let { it <= 10_000 } == true
                        TextButton(onClick = controller::goLive,
                            modifier = Modifier.testTag("go-live").semantics { contentDescription = "Go live" }.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Box(Modifier.size(6.dp).background(if (atLive) KickGreen else Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp)))
                            Text("LIVE", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                                modifier = Modifier.padding(start = 6.dp))
                        }
                    } else Text(recording?.kind?.label ?: if (channel.isDemo) "SAMPLE" else if (channel.isLive) "LIVE" else "OFFLINE", color = Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                    if (recording == null && channel.isLive && !channel.isDemo) Text("${compactCount(channel.viewers)}", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                }
                Spacer(Modifier.weight(1f))
                if (channel.isLive || recording != null) Box {
                    TextButton(onClick = { qualityMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(state.selectedQuality, color = Color.White, fontSize = 11.sp)
                        Icon(Icons.Default.KeyboardArrowDown, "Video quality", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(qualityMenu, { qualityMenu = false }) {
                        DropdownMenuItem(text = { Text("Auto (adaptive)") }, onClick = { controller.quality(null); qualityMenu = false })
                        state.qualities.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { controller.quality(option); qualityMenu = false })
                        }
                    }
                }
                IconButton(onClick = onFullscreen) {
                    Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        if (fullscreen) "Exit fullscreen" else "Fullscreen", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (error != null && !pip) {
            Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry, enabled = !loading) { Text(if (loading) "Retrying…" else if (recording != null) "Retry video" else "Retry stream") }
            }
        } else if (loading) CircularProgressIndicator(Modifier.size(if (pip) 16.dp else 28.dp), color = KickGreen, strokeWidth = 2.dp)
        if (state.recovering && !pip) Text("Reconnecting stream · ${state.recoveryAttempt}/5",
            color = Color.White, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
fun StreamTitle(channel: KickChannel, recordingTitle: String? = null) {
    val title = recordingTitle ?: channel.title
    if (title.isNotBlank()) Text(title, color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().background(PanelColor).padding(horizontal = 16.dp, vertical = 9.dp))
}

fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1fm", value / 1_000_000.0)
    value >= 1_000 -> String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0)
    else -> value.toString()
}
