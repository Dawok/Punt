package app.kickpocket.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object KickParser {
    fun channel(root: JsonObject): KickChannel {
        val user = root.obj("user")
        val live = root.obj("livestream")
        val slug = requireNotNull(root.text("slug")) { "Kick returned an incomplete channel." }
        val id = requireNotNull(root.number("id")) { "Kick returned an incomplete channel." }
        val categories = live?.array("categories") ?: root.array("recent_categories")
        return KickChannel(
            id = id,
            userId = root.number("user_id") ?: user?.number("id") ?: error("Missing Kick user ID."),
            chatroomId = root.obj("chatroom")?.number("id") ?: error("This channel has no public chat room."),
            slug = slug,
            displayName = user?.text("username") ?: slug,
            avatarUrl = user?.text("profile_pic"),
            playbackUrl = root.text("playback_url")?.takeIf { it.startsWith("https://") },
            isLive = live != null && (live["is_live"] == null || live.flag("is_live")),
            title = live?.text("session_title") ?: "This channel is offline",
            category = categories.firstOrNull()?.asObject()?.text("name") ?: "",
            viewers = live?.number("viewer_count") ?: live?.number("viewers") ?: 0,
            verified = root.flag("verified"),
            liveSessionId = live?.text("id") ?: live?.text("start_time"),
        )
    }

    fun message(root: JsonObject): ChatMessage? {
        val sender = root.obj("sender") ?: return null
        val identity = sender.obj("identity")
        return ChatMessage(
            id = root.text("id") ?: return null,
            userId = sender.number("id") ?: return null,
            username = sender.text("username") ?: "viewer",
            color = identity?.text("color"),
            content = root.text("content")?.take(4000) ?: return null,
            badges = identity?.array("badges")?.mapNotNull { it.asObject()?.text("type") } ?: emptyList(),
        )
    }

    fun event(name: String, data: JsonObject): ChatEvent? = when (name.substringAfterLast('\\')) {
        "ChatMessageEvent" -> message(data)?.let(ChatEvent::Message)
        "MessageDeletedEvent" -> (data.obj("message")?.text("id") ?: data.text("id"))?.let(ChatEvent::Delete)
        "UserBannedEvent" -> (data.obj("user")?.number("id") ?: data.number("user_id"))?.let(ChatEvent::Ban)
        "ChatroomClearEvent", "ChatroomClearedEvent" -> ChatEvent.Clear
        else -> null
    }

    fun envelope(raw: String): Pair<String, JsonObject?>? = runCatching {
        val root = AppJson.parseToJsonElement(raw).asObject() ?: return null
        (root.text("event") ?: return null) to root["data"]?.decodedObject()
    }.getOrNull()

    fun sevenTvEmotes(root: JsonObject): Map<String, Emote> = root.array("emotes").mapNotNull { item ->
        val active = item.asObject() ?: return@mapNotNull null
        val data = active.obj("data") ?: return@mapNotNull null
        val host = data.obj("host") ?: return@mapNotNull null
        val hostUrl = host.text("url")?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
        val files = host.array("files").mapNotNull { it.asObject() }
        val file = files.firstOrNull { it.text("name") == "2x.webp" }
            ?: files.firstOrNull { it.text("format") == "WEBP" } ?: return@mapNotNull null
        val name = active.text("name") ?: return@mapNotNull null
        name to Emote(
            id = active.text("id") ?: return@mapNotNull null,
            name = name,
            url = "$hostUrl/${file.text("name")}",
            staticUrl = "$hostUrl/${file.text("static_name") ?: file.text("name")}",
            width = (file.number("width") ?: 32).toInt(),
            height = (file.number("height") ?: 32).toInt(),
            animated = data.flag("animated"),
            zeroWidth = ((data.number("flags") ?: 0) and 256L) != 0L,
        )
    }.toMap()

    fun nativeEmotes(root: JsonElement, currentSlug: String? = null, authenticated: Boolean = false, channelAccess: Boolean = false): Map<String, Emote> {
        val groups = root as? kotlinx.serialization.json.JsonArray ?: return emptyMap()
        return groups.flatMap { item ->
            val group = item.asObject() ?: return@flatMap emptyList()
            val collectible = group.text("name") in setOf("Collectibles", "Collectables")
            val channel = group.obj("user") != null || group.text("slug") != null
            val current = currentSlug != null && group.text("slug") == currentSlug
            group.array("emotes").mapNotNull { item ->
                val emote = item.asObject() ?: return@mapNotNull null
                val id = emote.text("id") ?: return@mapNotNull null
                val name = emote.text("name") ?: return@mapNotNull null
                val subscriberOnly = emote.flag("subscribers_only")
                val access = authenticated && channel && (if (current) channelAccess else true)
                name to nativeEmote(id, name).copy(
                    kind = if (collectible) EmoteKind.Collectible else if (channel) EmoteKind.Channel else EmoteKind.General,
                    usable = if (collectible) authenticated else !subscriberOnly || access,
                    preferred = if (collectible) authenticated else access,
                    accountScoped = collectible || (channel && !current && authenticated),
                    requiresSubscription = subscriberOnly,
                )
            }
        }.toMap()
    }

    fun nativeEmote(id: String, name: String) = Emote(
        id = "kick:$id", name = name, url = "https://files.kick.com/emotes/$id/fullsize",
    )
}

