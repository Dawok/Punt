package app.kickpocket.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kickpocket.data.*

@Composable
internal fun ContinueWatching(preferences: AppPreferences, onOpen: (ChannelRecording, Long?) -> Unit) {
    val entries = preferences.watchHistory.filterNot { it.completed }.take(10)
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Continue watching", color = SubtleColor, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { VideoLibrary.key(it.recording) }) { progress ->
                Surface(onClick = { onOpen(progress.recording, progress.positionMs) }, color = PanelColor,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), modifier = Modifier.width(190.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(progress.recording.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        Text("${progress.recording.channelSlug} · ${durationLabel(progress.positionMs / 1000)}", color = SubtleColor, fontSize = 11.sp)
                        if (progress.durationMs > 0) LinearProgressIndicator(
                            progress = { (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryDialog(preferences: AppPreferences, onOpen: (ChannelRecording, Long?) -> Unit,
    onRemoveHistory: (ChannelRecording) -> Unit, onRemoveSaved: (ChannelRecording) -> Unit,
    onRemoveBookmark: (VideoBookmark) -> Unit, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val history = preferences.watchHistory.filterNot { it.completed }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your library") }, text = {
        Column(Modifier.fillMaxWidth().height(420.dp)) {
            SecondaryTabRow(tab) {
                listOf("Continue", "Watch later", "Bookmarks").forEachIndexed { index, label ->
                    Tab(tab == index, onClick = { tab = index }, text = { Text(label, fontSize = 11.sp) })
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                when (tab) {
                    0 -> {
                        if (history.isEmpty()) item { Text("Unfinished videos will appear here.", Modifier.padding(vertical = 16.dp)) }
                        items(history, key = { VideoLibrary.key(it.recording) }) { entry ->
                            LibraryItem(entry.recording, "Resume at ${durationLabel(entry.positionMs / 1000)}",
                                { onOpen(entry.recording, entry.positionMs); onDismiss() }, { onRemoveHistory(entry.recording) })
                        }
                    }
                    1 -> {
                        if (preferences.watchLater.isEmpty()) item { Text("Save a VOD or clip to watch it later.", Modifier.padding(vertical = 16.dp)) }
                        items(preferences.watchLater, key = VideoLibrary::key) { entry ->
                            LibraryItem(entry, entry.kind.label, { onOpen(entry, null); onDismiss() }, { onRemoveSaved(entry) })
                        }
                    }
                    2 -> {
                        if (preferences.bookmarks.isEmpty()) item { Text("Bookmark a moment while watching a recording.", Modifier.padding(vertical = 16.dp)) }
                        items(preferences.bookmarks, key = { "${VideoLibrary.key(it.recording)}:${it.positionMs}" }) { entry ->
                            LibraryItem(entry.recording, "Bookmark at ${durationLabel(entry.positionMs / 1000)}",
                                { onOpen(entry.recording, entry.positionMs); onDismiss() }, { onRemoveBookmark(entry) })
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun LibraryItem(recording: ChannelRecording, detail: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recording.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("${recording.channelSlug} · $detail", color = SubtleColor, fontSize = 11.sp)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove ${recording.title}") }
    }
}
