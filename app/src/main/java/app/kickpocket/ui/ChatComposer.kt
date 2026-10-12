package app.kickpocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kickpocket.ChatSendState
import app.kickpocket.chat.insertEmote
import app.kickpocket.data.Emote

@Composable
internal fun ChatComposer(
    channelKey: String?,
    signedIn: Boolean,
    sample: Boolean,
    emotes: Map<String, Emote>,
    animatedEmotes: Boolean,
    send: ChatSendState,
    onSend: (String) -> Unit,
    onLogin: () -> Unit,
    onPickerVisibility: (Boolean) -> Unit = {},
    emoteUsage: Map<String, Long> = emptyMap(),
) {
    var draft by rememberSaveable(channelKey, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var handledSent by rememberSaveable(channelKey) { mutableLongStateOf(send.sent) }
    var pickerOpen by rememberSaveable(channelKey) { mutableStateOf(false) }
    var insertionError by remember(channelKey) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canCompose = !sample && signedIn
    val visible = pickerOpen && canCompose && !send.sending
    DisposableEffect(visible) {
        onPickerVisibility(visible)
        onDispose { onPickerVisibility(false) }
    }
    LaunchedEffect(canCompose, send.sending) { if (!canCompose || send.sending) pickerOpen = false }
    LaunchedEffect(send.sent) {
        if (send.sent > handledSent) { draft = TextFieldValue(""); insertionError = null }
        handledSent = send.sent
    }
    Column(Modifier.fillMaxWidth().background(PanelColor).padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (!sample) (insertionError ?: send.error)?.let {
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (!canCompose) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (sample) Text("Read-only chat", fontSize = 11.sp, color = SubtleColor, modifier = Modifier.padding(6.dp).weight(1f))
                else TextButton(onClick = onLogin, modifier = Modifier.weight(1f, fill = false)) { Text("Log in to chat", fontSize = 12.sp) }
                Spacer(Modifier.weight(1f))
                Text("Kick + 7TV · ${emotes.size} emotes", fontSize = 10.sp, color = SubtleColor)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft, onValueChange = { if (it.text.length <= 500) { draft = it; insertionError = null } },
                    placeholder = { Text("Send a message", fontSize = 13.sp) }, enabled = !send.sending,
                    maxLines = 3, modifier = Modifier.weight(1f).focusRequester(focus).testTag("chat-draft"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (draft.text.isNotBlank() && !send.sending) onSend(draft.text) }),
                )
                IconButton(onClick = { keyboard?.hide(); pickerOpen = true }, enabled = !send.sending) {
                    Icon(Icons.Outlined.EmojiEmotions, "Open emote menu", tint = KickGreen)
                }
                IconButton(onClick = { onSend(draft.text) }, enabled = draft.text.isNotBlank() && !send.sending) {
                    if (send.sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, "Send message", tint = if (draft.text.isBlank()) SubtleColor else KickGreen)
                }
            }
        }
    }
    if (visible) EmotePicker(emotes, animatedEmotes, emoteUsage,
        onChoose = { alias ->
            val insertion = insertEmote(draft.text, draft.selection.start, draft.selection.end, alias)
            if (insertion != null) {
                draft = TextFieldValue(insertion.text, TextRange(insertion.cursor))
                insertionError = null
            } else insertionError = "Message is full. Remove some text to add this emote."
            pickerOpen = false
            focus.requestFocus()
        },
        onDismiss = { pickerOpen = false },
    )
}
