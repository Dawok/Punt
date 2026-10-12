package app.kickpocket.data

import kotlinx.serialization.Serializable

data class KickChannel(
    val id: Long,
    val userId: Long,
    val chatroomId: Long,
    val slug: String,
    val displayName: String,
    val avatarUrl: String?,
    val playbackUrl: String?,
    val isLive: Boolean,
    val title: String,
    val category: String,
    val viewers: Long,
    val verified: Boolean = false,
    val isDemo: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val userId: Long,
    val username: String,
    val color: String?,
    val content: String,
    val badges: List<String> = emptyList(),
)

enum class EmoteKind { General, Collectible, Channel }

data class Emote(
    val id: String,
    val name: String,
    val url: String,
    val staticUrl: String = url,
    val width: Int = 32,
    val height: Int = 32,
    val animated: Boolean = false,
    val zeroWidth: Boolean = false,
    val kind: EmoteKind = EmoteKind.General,
    val usable: Boolean = true,
    val preferred: Boolean = false,
    val accountScoped: Boolean = false,
    val requiresSubscription: Boolean = false,
) {
    val aspectRatio: Float get() = (width.toFloat() / height.coerceAtLeast(1)).coerceIn(0.5f, 4f)
}

sealed interface ChatEvent {
    data class Message(val message: ChatMessage) : ChatEvent
    data class Delete(val messageId: String) : ChatEvent
    data class Ban(val userId: Long) : ChatEvent
    data object Clear : ChatEvent
}

enum class ChatConnection { Connecting, Connected, Reconnecting, Paused, Disconnected }

@Serializable
data class RecentChannel(val slug: String, val displayName: String, val avatarUrl: String? = null)

@Serializable
data class AppPreferences(
    val animatedEmotes: Boolean = true,
    val chatFontSize: Int = 14,
    val recentChannels: List<RecentChannel> = emptyList(),
    val emoteUsage: Map<String, Long> = emptyMap(),
)

