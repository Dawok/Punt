package app.kickpocket

import app.kickpocket.data.AppJson
import app.kickpocket.data.FollowRequests
import app.kickpocket.data.SessionRequests
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FollowTest {
    @Test fun statusDistinguishesUnfollowedFromAnUnexpectedResponse() {
        assertTrue(FollowRequests.status(AppJson.parseToJsonElement("""{"is_following":true}""")))
        assertFalse(FollowRequests.status(AppJson.parseToJsonElement("""{"is_following":false,"following_since":null}""")))
        assertTrue(FollowRequests.status(AppJson.parseToJsonElement("""{"following_since":"2026-01-01T12:00:00Z"}""")))
        assertFalse(FollowRequests.status(AppJson.parseToJsonElement("""{"data":{"following_since":null}}""")))
        for (response in listOf("{}", "null", "{\"is_following\":\"false\"}", "{\"following_since\":false}")) {
            assertThrows(IOException::class.java) { FollowRequests.status(AppJson.parseToJsonElement(response)) }
        }
    }

    @Test fun followAndUnfollowUseSlugAndCorrectAuthenticatedMethods() {
        val cookies = "session_token=example-token; XSRF-TOKEN=example-csrf"
        assertEquals("https://kick.com/api/v2/channels/channel/me", FollowRequests.url("channel", "me"))
        val url = FollowRequests.url("channel", "follow").toHttpUrl()
        for (method in listOf("POST", "DELETE")) {
            val request = SessionRequests.build(url, cookies, "test", method = method)
            assertEquals(method, request.method)
            assertEquals("Bearer example-token", request.header("Authorization"))
            assertEquals("example-csrf", request.header("X-XSRF-TOKEN"))
            assertEquals(cookies, request.header("Cookie"))
            assertThrows(IllegalArgumentException::class.java) {
                SessionRequests.build("https://web.kick.com/api/v1/recommendations/categories".toHttpUrl(), cookies, "test", method = method)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { FollowRequests.url("channel/other", "follow") }
        assertThrows(IllegalArgumentException::class.java) { FollowRequests.url("https://example.com/channel", "follow") }
    }

    @Test fun emptyMutationSuccessIsAcceptedButHtmlIsNot() {
        assertEquals(JsonNull, SessionRequests.response("", allowEmpty = true))
        assertEquals(JsonNull, SessionRequests.response("  ", allowEmpty = true))
        assertThrows(IOException::class.java) { SessionRequests.response("", allowEmpty = false) }
        assertThrows(IOException::class.java) { SessionRequests.response("<html>Sign in</html>", allowEmpty = true) }
    }

    @Test fun loadsExistingStatusAndSerializesFollowThenUnfollow() = runTest {
        val writes = mutableListOf<Pair<String, Boolean>>()
        val response = CompletableDeferred<Unit>()
        val controller = FollowController(this, { false }, { slug, following ->
            writes += slug to following
            if (writes.size == 1) response.await()
        })
        controller.select(FollowTarget("channel", 1))
        assertTrue(controller.state.value.checking)
        controller.toggle() // Cannot act before status is known.
        runCurrent()
        assertEquals(false, controller.state.value.following)
        controller.toggle(); controller.toggle(); runCurrent()
        assertEquals(listOf("channel" to true), writes)
        assertTrue(controller.state.value.changing)
        assertEquals(false, controller.state.value.following)
        response.complete(Unit); runCurrent()
        assertEquals(true, controller.state.value.following)
        controller.toggle(); runCurrent()
        assertEquals(listOf("channel" to true, "channel" to false), writes)
        assertEquals(false, controller.state.value.following)
    }

    @Test fun failedMutationRequiresStatusReloadBeforeRetrying() = runTest {
        var serverFollowing = false
        var writes = 0
        val controller = FollowController(this, { serverFollowing }, { _, _ ->
            writes++
            serverFollowing = true
            throw IOException("Connection lost")
        })
        controller.select(FollowTarget("channel", 1)); runCurrent()
        controller.toggle(); runCurrent()
        assertEquals("Connection lost", controller.state.value.error)
        assertNull(controller.state.value.following)
        controller.toggle(); runCurrent()
        assertEquals(1, writes)
        controller.refresh(); runCurrent()
        assertEquals(true, controller.state.value.following)
        assertNull(controller.state.value.error)
    }

    @Test fun failedStatusCanBeRetriedAndSigningOutClearsState() = runTest {
        var shouldFail = true
        val controller = FollowController(this, { if (shouldFail) throw IOException("Unavailable") else true }, { _, _ -> fail("Unexpected mutation") })
        controller.select(FollowTarget("channel", 1)); runCurrent()
        assertEquals("Unavailable", controller.state.value.error)
        shouldFail = false
        controller.refresh(); runCurrent()
        assertEquals(true, controller.state.value.following)
        controller.select(null)
        assertEquals(FollowState(), controller.state.value)
    }

    @Test fun staleChannelOrAccountResponseCannotOverwriteCurrentStatus() = runTest {
        val oldResponse = CompletableDeferred<Boolean>()
        var reads = 0
        val controller = FollowController(this, {
            if (++reads == 1) withContext(NonCancellable) { oldResponse.await() } else false
        }, { _, _ -> })
        controller.select(FollowTarget("channel", 1)); runCurrent()
        controller.select(FollowTarget("channel", 2)); runCurrent()
        assertEquals(false, controller.state.value.following)
        oldResponse.complete(true); runCurrent()
        assertEquals(false, controller.state.value.following)
        controller.select(FollowTarget("other", 2)); runCurrent()
        assertEquals(3, reads)
    }

    @Test fun staleMutationCannotSetFollowingForAnotherChannel() = runTest {
        val response = CompletableDeferred<Unit>()
        val controller = FollowController(this, { false }, { _, _ -> withContext(NonCancellable) { response.await() } })
        controller.select(FollowTarget("channel", 1)); runCurrent()
        controller.toggle(); runCurrent()
        controller.select(FollowTarget("other", 1)); runCurrent()
        response.complete(Unit); runCurrent()
        assertEquals(FollowState(following = false), controller.state.value)
    }
}
