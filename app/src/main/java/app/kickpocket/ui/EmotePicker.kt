package app.kickpocket.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.kickpocket.data.Emote

private enum class EmoteProvider(val label: String) { All("All"), Kick("Kick"), SevenTv("7TV") }

@Composable
internal fun EmotePicker(
    emotes: Map<String, Emote>,
    animated: Boolean,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf(EmoteProvider.All) }
    val matches = remember(emotes, query, provider) {
        emotes.entries.filter { (alias, emote) ->
            alias.isNotBlank() && alias.none(Char::isWhitespace) && alias.contains(query.trim(), ignoreCase = true) &&
                when (provider) {
                    EmoteProvider.All -> true
                    EmoteProvider.Kick -> emote.id.startsWith("kick:")
                    EmoteProvider.SevenTv -> !emote.id.startsWith("kick:")
                }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(
            Modifier.safeDrawingPadding().imePadding().padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth()
                .heightIn(max = 520.dp).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp), color = PanelColor,
        ) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Emotes", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close emote menu") }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search emotes") }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmoteProvider.entries.forEach { choice ->
                        FilterChip(selected = provider == choice, onClick = { provider = choice }, label = { Text(choice.label) })
                    }
                }
                if (matches.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (emotes.isEmpty()) "No emotes available yet." else "No emotes match your search.",
                            color = SubtleColor, modifier = Modifier.padding(12.dp))
                    }
                } else {
                    key(query, provider) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(72.dp), modifier = Modifier.weight(1f).testTag("emote-grid"),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            items(matches, key = { it.key }, contentType = { "emote" }) { (alias, emote) ->
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .clickable(role = Role.Button) { onChoose(alias) }
                                        .semantics(mergeDescendants = true) { contentDescription = "Insert $alias" }
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    EmoteImage(emote, animated, Modifier.size(36.dp), contentDescription = null)
                                    Text(alias, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
                Text("Choose an emote, then press Send in chat.", fontSize = 11.sp, color = SubtleColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
            }
        }
    }
}
