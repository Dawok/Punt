package app.kickpocket

import app.kickpocket.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class SessionRequestsTest {
    private val cookies = "session_token=example%7Ctoken; XSRF-TOKEN=example%2Bcsrf"

    @Test fun subscriberNamesAreEncodedOnlyWhenAvailable() {
        val locked = KickParser.nativeEmote("123", "Sub").copy(usable = false)
        val json = AppJson.parseToJsonElement(SessionRequests.chatBody("Sub", mapOf("Sub" to locked))).asObject()!!
        assertEquals("Sub", json.text("content"))
        val owned = locked.copy(usable = true, kind = EmoteKind.Collectible)
        val sent = AppJson.parseToJsonElement(SessionRequests.chatBody("Sub", mapOf("Sub" to owned))).asObject()!!
        assertEquals("[emote:123:Sub]", sent.text("content"))
    }

    @Test fun malformedCredentialsFailWithoutAppearingInTheError() {
        val error = assertThrows(ApiException::class.java) {
            SessionRequests.build("https://kick.com/api/v1/user".toHttpUrl(), "session_token=example-token%0A", "test")
        }
        assertFalse(error.message!!.contains("example-token"))
        assertFalse(SessionRequests.hasToken("session_token=malformed%"))
    }

    @Test fun authenticatedRequestsStayOnKickHttps() {
        listOf("https://example.com/", "https://files.kick.com/", "https://kick.com.example.com/", "http://kick.com/", "https://kick.com:444/")
            .forEach { url -> assertThrows(IllegalArgumentException::class.java) { SessionRequests.build(url.toHttpUrl(), cookies, "test") } }
        assertThrows(ApiException::class.java) { SessionRequests.build("https://kick.com/api/v1/user".toHttpUrl(), null, "test") }
    }

    @Test fun recommendationsOnlyAllowAuthenticatedReadsOnTheirExactWebHostAndPaths() {
        for (path in listOf("categories", "livestreams/list")) {
            val url = "https://web.kick.com/api/v1/recommendations/$path".toHttpUrl()
            assertEquals("Bearer example|token", SessionRequests.build(url, cookies, "test").header("Authorization"))
            assertThrows(IllegalArgumentException::class.java) { SessionRequests.build(url, cookies, "test", "{}") }
        }
        listOf("https://web.kick.com/api/v1/user", "https://web.kick.com.example.com/api/v1/recommendations/categories",
            "http://web.kick.com/api/v1/recommendations/categories", "https://web.kick.com:444/api/v1/recommendations/categories")
            .forEach { url -> assertThrows(IllegalArgumentException::class.java) { SessionRequests.build(url.toHttpUrl(), cookies, "test") } }
    }

    @Test fun chatRequestCarriesCookieBearerCsrfAndEncodedMessage() {
        val native = KickParser.nativeEmote("123", "Native")
        val sevenTv = Emote("abc", "Alias", "https://example.com/emote.webp")
        val body = SessionRequests.chatBody("Native Alias \"hello\"", mapOf("Native" to native, "Alias" to sevenTv))
        val request = SessionRequests.build("https://kick.com/api/v2/messages/send/10".toHttpUrl(), cookies, "test", body)
        assertEquals("POST", request.method)
        assertEquals("Bearer example|token", request.header("Authorization"))
        assertEquals("example+csrf", request.header("X-XSRF-TOKEN"))
        assertEquals(cookies, request.header("Cookie"))
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        val json = AppJson.parseToJsonElement(buffer.readUtf8()).asObject()!!
        assertEquals("[emote:123:Native] Alias \"hello\"", json.text("content"))
        assertEquals("message", json.text("type"))
        assertFalse(json.text("message_ref").isNullOrBlank())
    }
}
