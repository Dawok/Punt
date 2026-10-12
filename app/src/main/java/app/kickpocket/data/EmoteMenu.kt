package app.kickpocket.data

import java.io.IOException
import java.time.Instant

data class EmoteSection(val title: String, val emotes: List<Pair<String, Emote>>)

object EmoteMenu {
    fun usageKey(alias: String, emote: Emote) = "${emote.id}|$alias"

    fun record(usage: Map<String, Long>, content: String, emotes: Map<String, Emote>): Map<String, Long> {
        val next = usage.filterValues { it > 0 }.toMutableMap()
        Regex("\\S+").findAll(content).forEach { token ->
            val alias = token.value
            emotes[alias]?.takeIf { it.usable }?.let { emote ->
                val key = usageKey(alias, emote)
                next[key] = (next[key] ?: 0).coerceAtMost(Long.MAX_VALUE - 1) + 1
            }
        }
        // Bound device-local history without persisting message text or account details.
        return next.entries.sortedByDescending { it.value }.take(1000).associate { it.key to it.value }
    }

    fun sections(emotes: Map<String, Emote>, usage: Map<String, Long>, query: String = "", kick: Boolean? = null): List<EmoteSection> {
        val sorted = emotes.entries.filter { (alias, emote) ->
            emote.usable && alias.isNotBlank() && alias.none(Char::isWhitespace) &&
                alias.contains(query.trim(), ignoreCase = true) && (kick == null || emote.id.startsWith("kick:") == kick)
        }.sortedWith(compareByDescending<Map.Entry<String, Emote>> { usage[usageKey(it.key, it.value)] ?: 0L }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.key })
        val groups = sorted.groupBy { (alias, emote) ->
            when {
                emote.kind == EmoteKind.Collectible -> "Collectibles"
                emote.kind == EmoteKind.Channel && emote.preferred -> "Channel emotes"
                (usage[usageKey(alias, emote)] ?: 0) > 0 -> "Most used"
                else -> "Other emotes"
            }
        }
        return listOf("Collectibles", "Channel emotes", "Most used", "Other emotes").mapNotNull { title ->
            groups[title]?.let { entries -> EmoteSection(title, entries.map { it.key to it.value }) }
        }
    }

    fun merge(public: Map<String, Emote>, owned: Map<String, Emote>): Map<String, Emote> = public + owned.filter { (alias, emote) ->
        val existing = public[alias]
        existing == null || existing.id.startsWith("kick:") || emote.preferred
    }

    fun withoutAccount(emotes: Map<String, Emote>): Map<String, Emote> = emotes.filterValues { !it.accountScoped }.mapValues { (_, emote) ->
        if (emote.preferred) emote.copy(usable = !emote.requiresSubscription, preferred = false) else emote
    }

    fun channelAccess(root: kotlinx.serialization.json.JsonElement, now: Instant = Instant.now()): Boolean {
        val obj = root.asObject()?.let { it.obj("data") ?: it } ?: throw IOException("Could not verify subscriber emotes.")
        if (obj.flag("is_broadcaster") || obj.flag("is_super_admin") || obj.obj("channel_roles")?.flag("is_broadcaster") == true ||
            obj.obj("channel_roles")?.flag("is_staff_member") == true) return true
        val subscription = obj.obj("subscription") ?: return false
        if (subscription.isEmpty()) return false
        val expires = subscription.text("expires_at") ?: return true
        return runCatching { Instant.parse(expires).isAfter(now) }.getOrDefault(false)
    }
}
