package app.kickpocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kickpocket.data.ChannelLibraryState
import app.kickpocket.data.ChannelRecording
import app.kickpocket.data.ChannelTab
import app.kickpocket.data.ClipSort
import app.kickpocket.data.ClipTimeRange
import app.kickpocket.data.VideoLibrary
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ChannelTabs(selected: ChannelTab, onSelect: (ChannelTab) -> Unit) {
    SecondaryTabRow(selectedTabIndex = selected.ordinal, containerColor = AppBackground, contentColor = KickGreen) {
        ChannelTab.entries.forEach { tab ->
            Tab(selected == tab, onClick = { onSelect(tab) }, text = { Text(tab.label) },
                modifier = Modifier.testTag("channel-tab-${tab.name.lowercase()}"))
        }
    }
}

@Composable
internal fun ChannelRecordings(
    state: ChannelLibraryState,
    selectedId: String?,
    onOpen: (ChannelRecording) -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    saved: Set<String> = emptySet(),
    onSave: (ChannelRecording) -> Unit = {},
    onClipFilters: (ClipSort, ClipTimeRange) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.tab, state.clipSort, state.clipTimeRange) { listState.scrollToItem(0) }
    Column(modifier.background(AppBackground)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.tab == ChannelTab.Videos) "Past broadcasts" else "Clips",
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, "Refresh recordings", tint = SubtleColor)
            }
        }
        if (state.tab == ChannelTab.Clips) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecordingFilter(state.clipSort.label, ClipSort.entries.map { it.label }, "clip-sort", Modifier.weight(1f)) { index ->
                onClipFilters(ClipSort.entries[index], state.clipTimeRange)
            }
            RecordingFilter(state.clipTimeRange.label, ClipTimeRange.entries.map { it.label }, "clip-time-range", Modifier.weight(1f)) { index ->
                onClipFilters(state.clipSort, ClipTimeRange.entries[index])
            }
        }
        if (state.loading && state.items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = KickGreen, strokeWidth = 2.dp)
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("channel-recordings"), state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.error != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text("Retry") }
                }
            }
            if (state.items.isEmpty() && state.error == null) item {
                Text(if (state.tab == ChannelTab.Videos) "No past broadcasts available." else "No clips available.",
                    color = SubtleColor, modifier = Modifier.padding(12.dp))
            }
            items(state.items, key = { it.id }) { recording ->
                RecordingCard(recording, selectedId == recording.id, VideoLibrary.key(recording) in saved,
                    onSave = { onSave(recording) }, onOpen = { onOpen(recording) })
            }
            if (state.tab == ChannelTab.Clips && state.clipsCursor != null) item {
                OutlinedButton(onClick = onMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Load more clips")
                }
            }
        }
    }
}

@Composable
private fun RecordingFilter(label: String, choices: List<String>, tag: String, modifier: Modifier, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag),
            contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEachIndexed { index, choice ->
                DropdownMenuItem(text = { Text(choice) }, onClick = { expanded = false; onSelect(index) })
            }
        }
    }
}

@Composable
private fun RecordingCard(recording: ChannelRecording, selected: Boolean, saved: Boolean, onSave: () -> Unit, onOpen: () -> Unit) {
    Surface(onClick = onOpen, color = if (selected) MaterialTheme.colorScheme.secondaryContainer else PanelColor,
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("recording-${recording.id}")) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = AppBackground, modifier = Modifier.width(112.dp).aspectRatio(16f / 9f)) {
                Box {
                    AsyncImage(recording.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (recording.durationSeconds > 0) Text(durationLabel(recording.durationSeconds), color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 10.sp, style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(recording.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                val date = recording.createdAt?.let { value -> runCatching {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
                        .withZone(ZoneId.systemDefault()).format(Instant.parse(value))
                }.getOrNull() }
                Text(listOfNotNull("${compactCount(recording.views)} views", date).joinToString(" · "),
                    color = SubtleColor, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (recording.availability == "sub_only") Text("Subscribers only", fontSize = 10.sp, color = SubtleColor)
                if (selected) Text("Selected", fontSize = 10.sp, color = KickGreen)
            }
            IconButton(onClick = onSave) {
                Icon(if (saved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (saved) "Remove from watch later" else "Save to watch later", tint = if (saved) KickGreen else SubtleColor)
            }
        }
    }
}

internal fun durationLabel(seconds: Long): String = if (seconds >= 3600)
    String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
