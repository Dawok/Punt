package app.kickpocket.chat

import app.kickpocket.data.AppJson
import app.kickpocket.data.array
import app.kickpocket.data.asObject
import app.kickpocket.data.awaitBody
import app.kickpocket.data.obj
import app.kickpocket.data.text
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal enum class ChatTransport { Pusher, Centrifugo }
internal data class ChatEndpoint(val transport: ChatTransport, val url: String, val clientId: String = "")

/** Discover the public chat transport in the same way as Kick's website. */
class KickRealtime(private val http: OkHttpClient, private val baseUrl: String = "https://web.kick.com/api/v1") {
    internal suspend fun resolve(channelId: Long): ChatEndpoint {
        val clientId = UUID.randomUUID().toString()
        val data = post("/realtime/channels/$channelId/chat/connection", buildJsonObject {
            put("client", buildJsonObject { put("id", clientId); put("type", "web") })
            put("capabilities", buildJsonObject {
                put("accepted_providers", buildJsonArray {
                    add(buildJsonObject { put("provider", "pusher") })
                    add(buildJsonObject { put("provider", "centrifugo") })
                })
            })
        })
        if (data.text("mode") != "websocket") throw IOException("Kick chat is temporarily unavailable.")
        for (connection in data.array("connections")) {
            val descriptor = connection.asObject() ?: continue
            val credentials = descriptor.obj("credentials") ?: continue
            when (descriptor.text("provider")) {
                "centrifugo" -> credentials.text("url")?.takeIf { it.startsWith("wss://") || it.startsWith("ws://") }
                    ?.let { return ChatEndpoint(ChatTransport.Centrifugo, it, clientId) }
                "pusher" -> {
                    val key = credentials.text("app_key") ?: continue
                    val cluster = credentials.text("cluster") ?: continue
                    if (!key.matches(Regex("[A-Za-z0-9]+")) || !cluster.matches(Regex("[a-z0-9-]+"))) continue
                    return ChatEndpoint(ChatTransport.Pusher,
                        "wss://ws-$cluster.pusher.com/app/$key?protocol=7&client=js&version=8.4.0&flash=false")
                }
            }
        }
        throw IOException("Kick returned an unsupported chat connection.")
    }

    internal suspend fun token(clientId: String): String = post("/realtime/auth/connection", buildJsonObject {
        put("client_id", clientId)
    }).text("token") ?: throw IOException("Could not authorize the chat connection.")

    private suspend fun post(path: String, body: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .header("Accept", "application/json")
            .header("Origin", "https://kick.com")
            .header("Referer", "https://kick.com/")
            .header("User-Agent", "Punt/${app.kickpocket.BuildConfig.VERSION_NAME} (Android)")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val response = http.newCall(request).awaitBody()
        return runCatching { AppJson.parseToJsonElement(response).asObject()?.obj("data") }.getOrNull()
            ?: throw IOException("Kick returned an invalid chat connection response.")
    }
}
