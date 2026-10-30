package app.kickpocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kickpocket.StreamState
import app.kickpocket.StreamViewModel
import app.kickpocket.data.AppPreferences
import app.kickpocket.data.RecentChannel
import app.kickpocket.data.ChannelTab
import app.kickpocket.data.VideoLibrary
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class StreamPart { Video, Header, Title, Chat, Error }

/** Keep the video node attached while only its bounds and surrounding chrome change. */
@Composable
fun StreamLayout(
    viewModel: StreamViewModel,
    state: StreamState,
    preferences: AppPreferences,
    landscape: Boolean,
    fullscreen: Boolean,
    pip: Boolean,
    onFullscreen: () -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    onPickerVisibility: (Boolean) -> Unit = {},
) {
    val channel = requireNotNull(state.channel)
    val follow by viewModel.followState.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    Layout(modifier = Modifier.fillMaxSize().background(if (landscape || pip || fullscreen) Color.Black else AppBackground), content = {
        // This remains the first child in every mode. Do not move it into conditional layouts.
        VideoPlayer(channel, viewModel.player, state.loading, viewModel::retryPlayback,
            Modifier.layoutId(StreamPart.Video), fullscreen = fullscreen, onFullscreen = onFullscreen, pip = pip,
            recording = state.recording, playbackError = state.error.takeIf { state.recording != null },
            saved = state.recording?.let { recording -> preferences.watchLater.any { VideoLibrary.key(it) == VideoLibrary.key(recording) } } == true,
            onSave = { state.recording?.let(viewModel::saveRecording) }, onBookmark = viewModel::bookmarkRecording)
        if (!pip && !fullscreen) {
            Row(Modifier.layoutId(StreamPart.Header).background(AppBackground).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to channels") }
                Avatar(channel.avatarUrl, channel.displayName, 30)
                Column(Modifier.padding(horizontal = 10.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(channel.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (channel.verified) Icon(Icons.Default.Verified, "Verified channel", tint = KickGreen, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                    }
                    if (channel.category.isNotEmpty()) Text(channel.category, color = SubtleColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!channel.isDemo && account.account?.id != channel.userId) {
                    FollowButton(follow, account.account != null, channel.displayName,
                        viewModel::toggleFollow, viewModel::retryFollow, onLogin)
                }
                if (!channel.isDemo) PinButton(preferences.favoriteChannels.any { it.slug == channel.slug }, channel.displayName) {
                    viewModel.toggleFavorite(RecentChannel(channel.slug, channel.displayName, channel.avatarUrl))
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = SubtleColor) }
            }
            Box(Modifier.layoutId(StreamPart.Title)) { StreamTitle(channel, state.recording?.title) }
            Column(Modifier.layoutId(StreamPart.Chat)) {
                if (!channel.isDemo) ChannelTabs(library.tab, viewModel::selectLibraryTab)
                if (library.tab == ChannelTab.Live) {
                    ChatPanel(viewModel, state.emotes, state.emoteWarnings, preferences, channel.isDemo,
                        Modifier.weight(1f).fillMaxWidth(), onLogin = onLogin, onPickerVisibility = onPickerVisibility)
                } else ChannelRecordings(library, state.recording?.id, viewModel::openRecording,
                    onRefresh = { viewModel.loadLibrary() }, onMore = { viewModel.loadLibrary(more = true) },
                    modifier = Modifier.weight(1f).fillMaxWidth(), saved = preferences.watchLater.map(VideoLibrary::key).toSet(),
                    onSave = viewModel::saveRecording, onClipFilters = viewModel::clipFilters)
            }
            Column(Modifier.layoutId(StreamPart.Error)) {
                state.error?.takeIf { state.recording == null }?.let { ErrorCard(it) }
                follow.error?.let { ErrorCard(it) }
            }
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val parts = measurables.associateBy { it.layoutId }
        val video = requireNotNull(parts[StreamPart.Video])
        if (pip || fullscreen) {
            val player = video.measure(Constraints.fixed(width, height))
            layout(width, height) { player.placeRelative(0, 0) }
        } else {
            fun flexibleWidth(w: Int, maxH: Int) = Constraints(minWidth = w, maxWidth = w, maxHeight = maxH.coerceAtLeast(0))
            val header = requireNotNull(parts[StreamPart.Header]).measure(flexibleWidth(width, height))
            val error = requireNotNull(parts[StreamPart.Error]).measure(flexibleWidth(width, height - header.height))
            val bodyTop = header.height + error.height
            val bodyHeight = (height - bodyTop).coerceAtLeast(0)
            val videoWidth = if (landscape) (width * 1.6f / 2.6f).toInt() else width
            val title = requireNotNull(parts[StreamPart.Title]).measure(flexibleWidth(videoWidth, bodyHeight))
            val roomForVideo = (bodyHeight - title.height - if (landscape) 0 else 128.dp.roundToPx()).coerceAtLeast(0)
            // Leave room for the composer when the keyboard reduces the available height.
            val videoHeight = (videoWidth * 9 / 16).coerceAtMost(roomForVideo)
            val player = video.measure(Constraints.fixed(videoWidth, videoHeight))
            val chat = requireNotNull(parts[StreamPart.Chat]).measure(Constraints.fixed(
                if (landscape) width - videoWidth else width,
                if (landscape) bodyHeight else (bodyHeight - title.height - videoHeight).coerceAtLeast(0),
            ))
            layout(width, height) {
                header.placeRelative(0, 0)
                error.placeRelative(0, header.height)
                if (landscape) {
                    player.placeRelative(0, bodyTop + (bodyHeight - title.height - videoHeight) / 2)
                    title.placeRelative(0, height - title.height)
                    chat.placeRelative(videoWidth, bodyTop)
                } else {
                    player.placeRelative(0, bodyTop)
                    title.placeRelative(0, bodyTop + videoHeight)
                    chat.placeRelative(0, bodyTop + videoHeight + title.height)
                }
            }
        }
    }
}
