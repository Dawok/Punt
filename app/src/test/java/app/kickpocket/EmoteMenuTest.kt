package app.kickpocket

import app.kickpocket.data.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class EmoteMenuTest {
    private fun emote(id: String, name: String, kind: EmoteKind = EmoteKind.General, preferred: Boolean = false) =
        Emote(id, name, "https://example.com/emote.webp", kind = kind, preferred = preferred)

    @Test fun ownedGroupsComeFirstAndFrequencySortsEveryGroupWithoutDuplicates() {
        val catalog = listOf(
            emote("kick:1", "CollectA", EmoteKind.Collectible, true),
            emote("kick:2", "CollectZ", EmoteKind.Collectible, true),
            emote("kick:3", "Sub", EmoteKind.Channel, true),
            emote("4", "Rare"), emote("5", "Frequent"), emote("6", "Unused"),
        ).associateBy { it.name }
        val usage = mapOf("kick:2|CollectZ" to 3L, "4|Rare" to 2L, "5|Frequent" to 10L)
        val sections = EmoteMenu.sections(catalog, usage)
        assertEquals(listOf("Collectibles", "Channel emotes", "Most used", "Other emotes"), sections.map { it.title })
        assertEquals(listOf("CollectZ", "CollectA", "Sub", "Frequent", "Rare", "Unused"), sections.flatMap { it.emotes }.map { it.first })
        assertEquals(6, sections.flatMap { it.emotes }.map { it.first }.distinct().size)
    }

    @Test fun filtersAndAccessApplyBeforeRanking() {
        val catalog = mapOf(
            "Locked" to emote("kick:1", "Locked", EmoteKind.Channel).copy(usable = false),
            "Native" to emote("kick:2", "Native"), "Alias" to emote("3", "Alias"),
        )
        assertEquals(listOf("Native"), EmoteMenu.sections(catalog, emptyMap(), "nat", kick = true).flatMap { it.emotes }.map { it.first })
        assertEquals(listOf("Alias"), EmoteMenu.sections(catalog, emptyMap(), kick = false).flatMap { it.emotes }.map { it.first })
        assertTrue(EmoteMenu.sections(catalog, emptyMap(), "Locked").isEmpty())
    }

    @Test fun countsRepeatedSentTokensWithStableIdentityAndKeepsAliasesSeparate() {
        val catalog = mapOf("Alias" to emote("1", "Original"), "OtherAlias" to emote("1", "Original"))
        val usage = EmoteMenu.record(emptyMap(), "hello Alias Alias\nOtherAlias", catalog)
        assertEquals(mapOf("1|Alias" to 2L, "1|OtherAlias" to 1L), usage)
        val replaced = catalog + ("Alias" to emote("2", "Alias"))
        assertEquals(1L, EmoteMenu.record(usage, "Alias", replaced)["2|Alias"])
        assertEquals(2L, EmoteMenu.record(usage, "Alias", replaced)["1|Alias"])
        val locked = mapOf("Alias" to catalog.getValue("Alias").copy(usable = false))
        assertTrue(EmoteMenu.record(emptyMap(), "Alias", locked).isEmpty())
    }

    @Test fun preferencesRemainBackwardCompatibleAndUsageSurvivesSerialization() {
        val old = AppJson.decodeFromString<AppPreferences>("""{"animatedEmotes":false,"chatFontSize":16}""")
        assertTrue(old.emoteUsage.isEmpty())
        val updated = old.copy(emoteUsage = mapOf("kick:1|Native" to 9))
        assertEquals(updated, AppJson.decodeFromString<AppPreferences>(AppJson.encodeToString(updated)))
        val many = (0..1100).associate { "id:$it" to 1L }
        assertEquals(1000, EmoteMenu.record(many, "", emptyMap()).size)
        val saturated = EmoteMenu.record(mapOf("kick:1|Native" to Long.MAX_VALUE), "Native", mapOf("Native" to emote("kick:1", "Native")))
        assertEquals(Long.MAX_VALUE, saturated["kick:1|Native"])
    }

    private val nativeGroups = """[
        {"slug":"channel","user":{"username":"Channel"},"emotes":[
            {"id":1,"name":"Free","subscribers_only":false},
            {"id":2,"name":"Sub","subscribers_only":true}]},
        {"name":"Collectibles","emotes":[{"id":3,"name":"collectiblesCard"}]},
        {"slug":"other","user":{"username":"Other"},"emotes":[{"id":4,"name":"OtherSub","subscribers_only":true}]},
        {"name":"Global","emotes":[{"id":5,"name":"Global"}]}
    ]"""

    @Test fun nativeMetadataRequiresAccountAccessForCollectiblesAndSubscriberEmotes() {
        val root = AppJson.parseToJsonElement(nativeGroups)
        val public = KickParser.nativeEmotes(root, currentSlug = "channel")
        assertTrue(public.getValue("Free").usable)
        assertFalse(public.getValue("Sub").usable)
        assertFalse(public.getValue("collectiblesCard").usable)
        assertFalse(public.getValue("OtherSub").usable)
        val signedIn = KickParser.nativeEmotes(root, "channel", authenticated = true, channelAccess = false)
        assertTrue(signedIn.getValue("collectiblesCard").usable)
        assertTrue(signedIn.getValue("OtherSub").usable)
        assertFalse(signedIn.getValue("Sub").usable)
        val subscribed = KickParser.nativeEmotes(root, "channel", authenticated = true, channelAccess = true)
        assertTrue(subscribed.getValue("Sub").preferred)
        assertEquals(EmoteKind.Collectible, subscribed.getValue("collectiblesCard").kind)
        val cleared = EmoteMenu.withoutAccount(subscribed)
        assertFalse(cleared.containsKey("collectiblesCard"))
        assertFalse(cleared.containsKey("OtherSub"))
        assertFalse(cleared.getValue("Sub").usable)
        assertTrue(cleared.getValue("Free").usable)
        assertFalse(cleared.getValue("Free").preferred)
    }

    @Test fun onlyActiveSubscriptionsOrApplicableRolesUnlockCurrentChannelEmotes() {
        val now = Instant.parse("2026-10-13T12:00:00Z")
        fun access(json: String) = EmoteMenu.channelAccess(AppJson.parseToJsonElement(json), now)
        assertTrue(access("""{"subscription":{"expires_at":"2026-11-13T12:00:00Z","type":"gift"}}"""))
        assertFalse(access("""{"data":{"subscription":{"expires_at":"2026-10-12T12:00:00Z"}}}"""))
        assertFalse(access("""{"subscription":{"expires_at":"invalid"}}"""))
        assertFalse(access("""{"subscription":null,"is_moderator":true}"""))
        assertFalse(access("""{"subscription":{}}"""))
        assertTrue(access("""{"is_broadcaster":true}"""))
        assertTrue(access("""{"data":{"channel_roles":{"is_staff_member":true}}}"""))
    }

    @Test fun accountCatalogPreservesSevenTvAliasesExceptForPreferredOwnedEmotes() {
        val alias = emote("seven-tv", "Alias")
        val native = emote("kick:1", "Alias")
        assertEquals(alias, EmoteMenu.merge(mapOf("Alias" to alias), mapOf("Alias" to native))["Alias"])
        val collectible = native.copy(kind = EmoteKind.Collectible, preferred = true)
        assertEquals(collectible, EmoteMenu.merge(mapOf("Alias" to alias), mapOf("Alias" to collectible))["Alias"])
    }
}
