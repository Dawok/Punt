package app.kickpocket.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

class ApiException(val status: Int, message: String) : IOException(message)
data class EmoteCatalog(val emotes: Map<String, Emote>, val warnings: List<String>)

class KickRepository(val http: OkHttpClient = defaultHttpClient()) {
    suspend fun streams(category: CategoryCard? = null, cursor: String? = null): BrowsePage<ChannelCard> {
        return DiscoveryParser.streams(getJson(DiscoveryRequests.streams(category, cursor, BrowseSort.Viewers).toString()))
    }

    suspend fun categories(page: String? = null): BrowsePage<CategoryCard> = DiscoveryParser.categories(
        getJson(DiscoveryRequests.categories(page, BrowseSort.Viewers).toString()),
    )

    suspend fun channel(slug: String): KickChannel = KickParser.channel(
        getJson("https://kick.com/api/v2/channels/$slug").asObject()
            ?: throw IOException("Kick returned an unexpected channel response."),
    )

    suspend fun emotes(channel: KickChannel): EmoteCatalog = withContext(Dispatchers.Default) {
        val global = async { optional("7TV global emotes") { KickParser.sevenTvEmotes(getJson("https://7tv.io/v3/emote-sets/global").asObject() ?: error("Invalid emote set")) } }
        val channelEmotes = async { optional("7TV channel emotes") {
            val root = getJson("https://7tv.io/v3/users/kick/${channel.userId}").asObject()
            val set = root?.obj("emote_set") ?: root?.text("emote_set_id")?.let { getJson("https://7tv.io/v3/emote-sets/$it").asObject() }
            set?.let(KickParser::sevenTvEmotes) ?: emptyMap()
        } }
        val native = async { optional("Kick emotes") { KickParser.nativeEmotes(getJson("https://kick.com/emotes/${channel.slug}"), currentSlug = channel.slug) } }
        val results = listOf(global.await(), native.await(), channelEmotes.await())
        EmoteCatalog(results.fold(emptyMap()) { acc, result -> acc + result.first }, results.mapNotNull { it.second })
    }

    private suspend fun optional(label: String, block: suspend () -> Map<String, Emote>): Pair<Map<String, Emote>, String?> = try {
        block() to null
    } catch (error: CancellationException) {
        throw error
    } catch (error: ApiException) {
        if (error.status == 404) emptyMap<String, Emote>() to null
        else emptyMap<String, Emote>() to "$label are unavailable."
    } catch (_: Exception) {
        emptyMap<String, Emote>() to "$label are unavailable."
    }

    internal suspend fun getJson(url: String): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Punt/${app.kickpocket.BuildConfig.VERSION_NAME} (Android)")
            .build()
        val body = http.newCall(request).awaitBody()
        try { AppJson.parseToJsonElement(body) } catch (_: Exception) {
            throw IOException("The service returned a page instead of data. Try again later.")
        }
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}

internal suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!continuation.isActive) return
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        401 -> "Your Kick session expired. Sign in again."
                        419 -> "Your Kick session expired. Sign in again."
                        403 -> "Kick refused access from this connection. Try another network or try again later."
                        422 -> "Kick did not accept this request. Check the channel rules and try again."
                        404 -> "That channel could not be found. Check the name and try again."
                        429 -> "Too many requests. Wait a moment, then try again."
                        else -> "The service is unavailable (HTTP ${response.code}). Try again later."
                    }
                    continuation.resumeWithException(ApiException(response.code, message))
                } else {
                    try {
                        continuation.resume(response.body?.string() ?: throw IOException("The service returned no data."))
                    } catch (error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        }
    })
}

