package app.kickpocket

import app.kickpocket.data.*
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class DiscoveryTest {
    @Test fun readsLiveCardsAndOpaqueCursor() {
        val page = DiscoveryParser.streams(AppJson.parseToJsonElement("""{"data":{"livestreams":[
            {"title":"Stream title","viewer_count":42,"channel":{"id":1,"slug":"user","username":"User","profile_pic":"https://example.com/avatar.webp"},"category":{"id":15,"name":"Category"},"thumbnail":{"src":"https://example.com/preview.webp"}},
            {"title":"duplicate","channel":{"slug":"user"}}, {"title":"missing channel"}],"pagination":{"next_cursor":"opaque/+="}}}"""))
        assertEquals(1, page.items.size)
        assertEquals("user", page.items.single().slug)
        assertEquals("Stream title", page.items.single().title)
        assertEquals(42, page.items.single().viewers)
        assertEquals("opaque/+=", page.cursor)
    }

    @Test fun followsIncludeOnlyLiveChannelsAndKeepPagingPastOfflineOnes() {
        val page = DiscoveryParser.followed(AppJson.parseToJsonElement("""{"channels":[
            {"channel_slug":"live-user","user_username":"Live user","is_live":true,"viewer_count":8,"session_title":"Hello","profile_picture":"https://example.com/avatar.webp"},
            {"channel_slug":"offline-user","user_username":"Offline user","is_live":false},
            {"slug":"nested-user","livestream":{"is_live":true,"session_title":"Nested","categories":[{"name":"Category"}]}}
        ],"nextCursor":"next"}"""))
        assertEquals(listOf("live-user", "nested-user"), page.items.map { it.slug })
        assertEquals("Category", page.items.last().category)
        assertEquals("next", page.cursor)
        val offline = DiscoveryParser.followed(AppJson.parseToJsonElement("""{"channels":[{"channel_slug":"offline-user","is_live":false}],"nextCursor":"next"}"""))
        assertTrue(offline.items.isEmpty())
        assertEquals("next", offline.cursor)
    }

    @Test fun categoryPagingUsesOnlyPageNumber() {
        val page = DiscoveryParser.categories(AppJson.parseToJsonElement("""{"current_page":2,"next_page_url":"https://example.com/unsafe","data":[{"id":15,"name":"Category","slug":"category","viewers":100,"banner":{"src":"https://example.com/category.webp"}}]}"""))
        assertEquals("3", page.cursor)
        assertEquals(15, page.items.single().id)
        assertEquals("https://example.com/category.webp", page.items.single().image)
    }

    @Test fun recommendedCategoriesKeepTheServiceOrderInsteadOfViewerOrder() {
        val page = DiscoveryParser.categories(AppJson.parseToJsonElement("""{"data":[
            {"id":1,"slug":"first","name":"First","viewers":2,"thumbnail":{"src":"https://example.com/first.webp"}},
            {"id":2,"slug":"second","name":"Second","viewers":100}
        ]}"""))
        assertEquals(listOf(1L, 2L), page.items.map { it.id })
        assertEquals("https://example.com/first.webp", page.items.first().image)
        assertNull(page.cursor)
    }

    @Test fun unexpectedFollowResponseIsAnErrorInsteadOfEmptySuccess() {
        assertThrows(IOException::class.java) { DiscoveryParser.followed(AppJson.parseToJsonElement("{}")) }
    }

    @Test fun accountRequiresVerifiedUserData() {
        assertNull(DiscoveryParser.account(AppJson.parseToJsonElement("{}")))
        assertEquals(12, DiscoveryParser.account(AppJson.parseToJsonElement("""{"id":12,"username":"User"}"""))!!.id)
    }
}
