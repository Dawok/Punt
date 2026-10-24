package app.kickpocket.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import androidx.core.content.edit
import java.io.IOException
import java.net.URLDecoder
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class AccountState(val account: KickAccount? = null, val checking: Boolean = false, val error: String? = null)

/** Website sessions stay in Android's app-private WebView cookie store, never preferences or logs. */
class KickSession(context: Context) {
    private val cookies by lazy { CookieManager.getInstance() }
    private val userAgent by lazy { WebSettings.getDefaultUserAgent(context) }
    private val savedState = context.getSharedPreferences("account-state", Context.MODE_PRIVATE)
    private val http = KickRepository.defaultHttpClient().newBuilder()
        .followRedirects(false).followSslRedirects(false).build()
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()
    private val mutableFollowingRevision = MutableStateFlow(0L)
    val followingRevision = mutableFollowingRevision.asStateFlow()
    @Volatile private var revision = 0L

    val hasSavedSession: Boolean get() = savedState.getBoolean("has-session", false)

    suspend fun restore() {
        // This flag contains no credentials. Anonymous viewing never starts the WebView engine.
        if (hasSavedSession) refresh()
    }

    suspend fun refresh(reportMissingSession: Boolean = false) {
        if (mutable.value.checking) return
        val version = revision
        if (!SessionRequests.hasToken(cookies.getCookie("https://kick.com"))) {
            savedState.edit { putBoolean("has-session", false) }
            mutable.value = AccountState(error = if (reportMissingSession) "You are not signed in yet. Complete Kick’s sign-in form below, then continue." else mutable.value.error)
            return
        }
        mutable.update { it.copy(checking = true, error = null) }
        try {
            val account = DiscoveryParser.account(request("https://kick.com/api/v1/user"))
                ?: throw ApiException(401, "Sign in on Kick first, then continue.")
            if (revision == version) {
                cookies.flush()
                savedState.edit { putBoolean("has-session", true) }
                mutable.value = AccountState(account)
            }
        } catch (error: CancellationException) {
            if (revision == version) mutable.update { it.copy(checking = false) }
            throw error
        } catch (error: Exception) {
            if (revision == version) mutable.update { it.copy(checking = false, account = if (error is ApiException && error.status == 401) null else it.account,
                error = error.message ?: "Could not verify your Kick session. Try again.") }
        }
    }

    suspend fun followed(cursor: String?): BrowsePage<ChannelCard> {
        checkSignedIn()
        val url = "https://kick.com/api/v2/channels/followed".toHttpUrl().newBuilder()
        cursor?.let { url.addQueryParameter("cursor", it) }
        return DiscoveryParser.followed(request(url.build().toString()))
    }

    suspend fun isFollowing(slug: String): Boolean {
        checkSignedIn()
        return FollowRequests.status(request(FollowRequests.url(slug, "me")))
    }

    suspend fun emotes(slug: String): Map<String, Emote> = coroutineScope {
        checkSignedIn()
        require(slug == ChannelInput.normalize(slug))
        val access = async { EmoteMenu.channelAccess(request(FollowRequests.url(slug, "me"))) }
        val catalog = async { request("https://kick.com/emotes/$slug") }
        val groups = catalog.await() as? kotlinx.serialization.json.JsonArray
            ?: throw IOException("Kick returned an unexpected account emote list.")
        KickParser.nativeEmotes(groups, currentSlug = slug, authenticated = true, channelAccess = access.await())
    }

    suspend fun setFollowing(slug: String, following: Boolean) {
        checkSignedIn()
        val accountId = mutable.value.account?.id
        request(FollowRequests.url(slug, "follow"), method = if (following) "POST" else "DELETE", allowEmpty = true)
        if (mutable.value.account?.id == accountId) mutableFollowingRevision.update { it + 1 }
    }

    suspend fun recommendedStreams(category: CategoryCard?, cursor: String?): BrowsePage<ChannelCard> {
        checkSignedIn()
        return DiscoveryParser.streams(request(DiscoveryRequests.streams(category, cursor, BrowseSort.Recommended).toString()))
    }

    suspend fun recommendedCategories(): BrowsePage<CategoryCard> {
        checkSignedIn()
        return DiscoveryParser.categories(request(DiscoveryRequests.categories(null, BrowseSort.Recommended).toString()))
    }

    suspend fun send(chatroomId: Long, content: String, emotes: Map<String, Emote>) {
        checkSignedIn()
        require(chatroomId > 0) { "This channel's chat is unavailable." }
        require(content.isNotBlank() && content.length <= 500) { "Messages must be between 1 and 500 characters." }
        request("https://kick.com/api/v2/messages/send/$chatroomId", SessionRequests.chatBody(content, emotes))
    }

    suspend fun videoPlayback(recording: ChannelRecording): ChannelRecording =
        MediaParser.playback(request(MediaRequests.playback(recording.id), MediaRequests.playbackBody), recording)

    fun signOut() {
        revision++
        savedState.edit { putBoolean("has-session", false) }
        mutable.value = AccountState()
        cookies.removeAllCookies { cookies.flush() }
        WebStorage.getInstance().deleteAllData()
    }

    private fun checkSignedIn() {
        if (mutable.value.account == null) throw ApiException(401, "Sign in to your Kick account first.")
    }

    private suspend fun request(url: String, body: String? = null, method: String = if (body == null) "GET" else "POST", allowEmpty: Boolean = false): JsonElement {
        // Read WebView cookies on the UI thread. Public streams, emotes and images use a separate client.
        val cookie = withContext(Dispatchers.Main.immediate) { cookies.getCookie("https://kick.com") }
        val request = SessionRequests.build(url.toHttpUrl(), cookie, userAgent, body, method)
        val version = revision
        return withContext(Dispatchers.IO) {
            try {
                val text = http.newCall(request).awaitBody()
                SessionRequests.response(text, allowEmpty)
            } catch (error: ApiException) {
                if (revision == version && (error.status == 401 || error.status == 419)) {
                    savedState.edit { putBoolean("has-session", false) }
                    mutable.update { it.copy(account = null, error = error.message) }
                }
                throw error
            }
        }
    }
}

internal object SessionRequests {
    private fun cookieValue(cookie: String?, name: String): String? = cookie?.split(';')?.firstNotNullOfOrNull {
        val parts = it.trim().split('=', limit = 2)
        if (parts.size == 2 && parts[0] == name) parts[1].takeIf(String::isNotBlank) else null
    }?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() }

    fun hasToken(cookie: String?) = cookieValue(cookie, "session_token") != null

    fun fingerprint(cookie: String?): String? = cookieValue(cookie, "session_token")?.let { token ->
        java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun build(url: HttpUrl, cookie: String?, userAgent: String, body: String? = null, method: String = if (body == null) "GET" else "POST"): Request {
        require(method in setOf("GET", "POST", "DELETE") && (method != "GET" || body == null))
        val allowed = url.host == "kick.com" || (url.host == "web.kick.com" && method == "GET" && body == null &&
            url.encodedPath in setOf("/api/v1/recommendations/categories", "/api/v1/recommendations/livestreams/list")) ||
            (method == "POST" && MediaRequests.isPlayback(url))
        require(url.isHttps && allowed && url.port == 443) { "Account requests must use Kick's secure website." }
        val token = cookieValue(cookie, "session_token") ?: throw ApiException(401, "Sign in to your Kick account first.")
        val csrf = cookieValue(cookie, "XSRF-TOKEN")
        // Reject malformed values before OkHttp can include a credential in a header error.
        if (listOfNotNull(token, csrf, cookie).any { value -> value.any { it.code !in 32..126 } }) {
            throw ApiException(401, "Kick returned an invalid session. Sign in again.")
        }
        return Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", userAgent).header("Origin", "https://kick.com").header("Referer", "https://kick.com/")
            .header("x-app-platform", "web").header("Cookie", requireNotNull(cookie)).header("Authorization", "Bearer $token")
            .apply {
                csrf?.let { header("X-XSRF-TOKEN", it) }
                val payload = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
                method(method, payload ?: if (method == "POST") ByteArray(0).toRequestBody() else null)
            }.build()
    }

    fun response(text: String, allowEmpty: Boolean = false): JsonElement {
        if (allowEmpty && text.isBlank()) return JsonNull
        return try { AppJson.parseToJsonElement(text) } catch (_: Exception) {
            throw IOException("Kick returned a sign-in page. Sign in again or retry later.")
        }
    }

    fun chatBody(content: String, emotes: Map<String, Emote>): String {
        // Kick's native emotes use markup; 7TV aliases remain ordinary words on the wire.
        val encoded = Regex("\\S+").replace(content) { match ->
            val emote = emotes[match.value]?.takeIf { it.usable }
            val nativeId = emote?.id?.takeIf { it.startsWith("kick:") }?.removePrefix("kick:")
            if (!nativeId.isNullOrEmpty() && nativeId.all(Char::isDigit)) "[emote:$nativeId:${emote.name}]" else match.value
        }
        return buildJsonObject { put("content", encoded); put("type", "message"); put("message_ref", UUID.randomUUID().toString()) }.toString()
    }
}
