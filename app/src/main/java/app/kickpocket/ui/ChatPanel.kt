package app.kickpocket.ui

import android.graphics.Color as AndroidColor
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kickpocket.StreamViewModel
import app.kickpocket.chat.ChatToken
import app.kickpocket.chat.ChatTokenizer
import app.kickpocket.chat.ChatFilters
import app.kickpocket.data.AppPreferences
import app.kickpocket.data.ChatConnection
import app.kickpocket.data.ChatMessage
import app.kickpocket.data.Emote
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.decode.BitmapFactoryDecoder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ChatPanel(
    viewModel: StreamViewModel,
    emotes: Map<String, Emote>,
    warnings: List<String>,
    preferences: AppPreferences,
    sample: Boolean,
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = {},
    onPickerVisibility: (Boolean) -> Unit = {},
) {
    // Subscribe here so message updates never recompose the player or the screen shell.
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val visible = remember(messages, preferences.hiddenChatUsers, preferences.hiddenChatKeywords, preferences.reduceRepeatedMessages) {
        ChatFilters.visible(messages, preferences)
    }
    val hidden = messages.size - visible.size
    Column(modifier.background(AppBackground)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (sample) "Sample chat" else "Stream chat" + if (hidden > 0) " · $hidden hidden" else "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            ConnectionLabel(connection, sample)
        }
        if (warnings.isNotEmpty()) {
            Text(warnings.joinToString(" "), color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        }
        ChatMessageList(visible, connection, emotes, preferences, Modifier.weight(1f).fillMaxWidth(),
            mentionUsername = account.account?.username.takeIf { preferences.highlightMentions })
        SessionChatComposer(viewModel, sample, emotes, preferences.animatedEmotes, preferences.emoteUsage, onLogin, onPickerVisibility)
    }
}

@Composable
internal fun ChatMessageList(
    messages: List<ChatMessage>,
    connection: ChatConnection,
    emotes: Map<String, Emote>,
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
    mentionUsername: String? = null,
) {
    val listState = rememberLazyListState()
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var following by remember { mutableStateOf(true) }
    val bottomTolerance = with(LocalDensity.current) { 24.dp.roundToPx() }

    // Pause only for a user gesture; new messages must never turn following off.
    // A small tolerance lets scrolling back to the newest message reliably resume it.
    LaunchedEffect(listState, bottomTolerance) {
        snapshotFlow {
            Triple(dragging, listState.isScrollInProgress,
                !listState.canScrollBackward ||
                    (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= bottomTolerance))
        }.distinctUntilChanged().collect { (dragged, scrolling, atBottom) ->
            if (dragged) following = false
            else if (!scrolling && atBottom) following = true
        }
    }
    // Messages are chronological, while the list is reversed: the LAST message is newest.
    // Track its ID even when the bounded buffer stays the same size.
    LaunchedEffect(messages.lastOrNull()?.id, following, dragging) {
        if (following && !dragging && messages.isNotEmpty()) listState.requestScrollToItem(0)
    }

    Box(modifier) {
        if (messages.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (connection == ChatConnection.Connected) "You're all caught up" else "Connecting to chat…", fontWeight = FontWeight.Medium)
                Text(if (connection == ChatConnection.Connected) "New messages will appear here." else "Messages will appear when connected.", color = SubtleColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        LazyColumn(
            state = listState, reverseLayout = true,
            modifier = Modifier.fillMaxSize().testTag("chat-messages"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.Bottom),
        ) {
            items(messages.asReversed(), key = { it.id }, contentType = { "message" }) { message ->
                ChatRow(message, emotes, preferences.chatFontSize, preferences.animatedEmotes,
                    highlighted = ChatFilters.mentions(message.content, mentionUsername))
            }
        }
        if (!following && messages.isNotEmpty()) {
            FilledTonalButton(onClick = { following = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Jump to live", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SessionChatComposer(
    viewModel: StreamViewModel,
    sample: Boolean,
    emotes: Map<String, Emote>,
    animatedEmotes: Boolean,
    emoteUsage: Map<String, Long>,
    onLogin: () -> Unit,
    onPickerVisibility: (Boolean) -> Unit,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val send by viewModel.sendState.collectAsStateWithLifecycle()
    val stream by viewModel.state.collectAsStateWithLifecycle()
    ChatComposer(stream.channel?.slug, account.account != null, sample, emotes, animatedEmotes, send,
        viewModel::send, onLogin, onPickerVisibility, emoteUsage)
}

@Composable
private fun ConnectionLabel(connection: ChatConnection, sample: Boolean) {
    val color = when (connection) {
        ChatConnection.Connected -> KickGreen
        ChatConnection.Connecting, ChatConnection.Reconnecting -> Color(0xFFFFC46B)
        else -> SubtleColor
    }
    val text = when (connection) {
        ChatConnection.Connected -> if (sample) "Simulated" else "Connected"
        ChatConnection.Connecting -> "Connecting"
        ChatConnection.Reconnecting -> "Reconnecting"
        ChatConnection.Paused -> "Paused"
        ChatConnection.Disconnected -> "Disconnected"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(text, color = color, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ChatRow(message: ChatMessage, emotes: Map<String, Emote>, size: Int, animatedEmotes: Boolean, highlighted: Boolean = false) {
    val tokens = remember(message.content, emotes) { ChatTokenizer.tokenize(message.content, emotes) }
    val usernameColor = remember(message.color) { readableNameColor(message.color) }
    val inline = tokens.mapIndexedNotNull { index, token ->
        if (token !is ChatToken.Emotes) return@mapIndexedNotNull null
        val ratio = token.layers.maxOf { it.aspectRatio }
        "emote:$index" to InlineTextContent(
            Placeholder(width = (size * 1.7f * ratio).sp, height = (size * 1.7f).sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                token.layers.forEach { emote ->
                    EmoteImage(emote, animatedEmotes, Modifier.fillMaxSize())
                }
            }
        }
    }.toMap().toMutableMap()
    val badge = when {
        "moderator" in message.badges -> Icons.Default.Security
        "broadcaster" in message.badges -> Icons.Default.Star
        else -> null
    }
    if (badge != null) inline["badge"] = InlineTextContent(
        Placeholder(size.sp, size.sp, PlaceholderVerticalAlign.TextCenter),
    ) { Icon(badge, contentDescription = if ("moderator" in message.badges) "Moderator" else "Broadcaster", tint = KickGreen, modifier = Modifier.fillMaxSize()) }

    val text = remember(tokens, message.username, usernameColor, badge) {
        buildAnnotatedString {
            if (badge != null) { appendInlineContent("badge", "[badge]"); append(" ") }
            pushStyle(SpanStyle(color = usernameColor, fontWeight = FontWeight.SemiBold))
            append(message.username)
            pop()
            append("  ")
            tokens.forEachIndexed { index, token ->
                when (token) {
                    is ChatToken.Text -> append(token.value)
                    is ChatToken.Emotes -> appendInlineContent("emote:$index", token.layers.joinToString(" ") { it.name })
                }
            }
        }
    }
    Text(text, inlineContent = inline, fontSize = size.sp, lineHeight = (size * 1.65f).sp, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().then(if (highlighted) Modifier.background(KickGreen.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier))
}

@Composable
internal fun EmoteImage(emote: Emote, animated: Boolean, modifier: Modifier, contentDescription: String? = emote.name) {
    val context = LocalContext.current
    val url = if (animated) emote.url else emote.staticUrl
    var failed by remember(url, animated) { mutableStateOf(false) }
    val request = remember(url, animated, context) {
        ImageRequest.Builder(context).data(url).allowHardware(false)
            .apply {
                // Native Kick emotes can also animate. BitmapFactory decodes their first frame.
                if (!animated) decoderFactory(BitmapFactoryDecoder.Factory())
                // Keep decoded static/animated variants distinct in the memory cache.
                memoryCacheKey("$url:animated=$animated")
            }.build()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(model = request, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), onError = { failed = true })
        if (failed) Text(emote.name, color = SubtleColor, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun readableNameColor(value: String?): Color {
    val parsed = runCatching { (value ?: "#B0B8C4").toColorInt() }.getOrDefault(AndroidColor.LTGRAY)
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(parsed, hsv)
    hsv[2] = hsv[2].coerceAtLeast(0.78f)
    hsv[1] = hsv[1].coerceAtMost(0.65f)
    return Color(AndroidColor.HSVToColor(hsv))
}

