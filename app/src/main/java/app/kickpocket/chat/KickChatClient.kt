package app.kickpocket.chat

import app.kickpocket.data.ChatConnection
import app.kickpocket.data.ChatEvent
import app.kickpocket.data.KickParser
import app.kickpocket.data.AppJson
import app.kickpocket.data.asObject
import app.kickpocket.data.decodedObject
import app.kickpocket.data.flag
import app.kickpocket.data.number
import app.kickpocket.data.obj
import app.kickpocket.data.text
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class KickChatClient(
    private val http: OkHttpClient,
    private val socketUrl: String? = null,
    private val realtime: KickRealtime = KickRealtime(http),
) {
    suspend fun collect(
        chatroomId: Long,
        onState: (ChatConnection) -> Unit,
        onEvent: (ChatEvent) -> Unit,
        channelId: Long = chatroomId,
    ) {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            onState(if (failures == 0) ChatConnection.Connecting else ChatConnection.Reconnecting)
            try {
                val endpoint = socketUrl?.let { ChatEndpoint(ChatTransport.Pusher, it) } ?: realtime.resolve(channelId)
                session(chatroomId, endpoint, {
                    failures = 0
                    onState(ChatConnection.Connected)
                }, onEvent)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Retry temporary network failures with a bounded exponential backoff.
            }
            onState(ChatConnection.Reconnecting)
            failures++
            delay(min(30_000L, 1_000L shl failures.coerceAtMost(5)))
        }
    }

    private suspend fun session(chatroomId: Long, endpoint: ChatEndpoint, onReady: () -> Unit, onEvent: (ChatEvent) -> Unit) = coroutineScope {
        val subscribed = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val lastActivity = AtomicLong(System.nanoTime())
        val activityTimeout = AtomicLong(60_000_000_000L)
        val wantsPong = AtomicBoolean(true)
        val refreshIntervals = Channel<Long>(Channel.CONFLATED)
        val nextCommand = AtomicLong(3)
        val channel = "chatrooms.$chatroomId.v2"
        val token = if (endpoint.transport == ChatTransport.Centrifugo) realtime.token(endpoint.clientId) else null
        fun fail(message: String) {
            val error = IOException(message)
            closed.completeExceptionally(error)
            subscribed.completeExceptionally(error)
        }
        val socket = http.newWebSocket(Request.Builder().url(endpoint.url)
            .header("Origin", "https://kick.com").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (endpoint.transport == ChatTransport.Centrifugo) webSocket.send(buildJsonObject {
                    put("id", 1)
                    put("connect", buildJsonObject { put("token", requireNotNull(token)); put("name", "punt") })
                }.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                lastActivity.set(System.nanoTime())
                if (endpoint.transport == ChatTransport.Centrifugo) {
                    text.lineSequence().filter { it.isNotBlank() }.forEach { frame ->
                        val root = runCatching { AppJson.parseToJsonElement(frame).asObject() }.getOrNull() ?: return@forEach
                        if (root.isEmpty()) {
                            if (wantsPong.get()) webSocket.send("{}")
                            return@forEach
                        }
                        if (root.obj("error") != null) {
                            fail("Chat connection was rejected. Reconnecting…")
                            return@forEach
                        }
                        val connect = root.obj("connect")
                        if (root.number("id") == 1L && connect != null) {
                            connect.number("ping")?.let { activityTimeout.set((it + 10).coerceAtLeast(60) * 1_000_000_000L) }
                            wantsPong.set(connect.flag("pong"))
                            webSocket.send(buildJsonObject {
                                put("id", 2)
                                put("subscribe", buildJsonObject { put("channel", channel) })
                            }.toString())
                        }
                        val expiry = connect ?: root.obj("refresh")
                        if (expiry?.flag("expires") == true) expiry.number("ttl")?.let { refreshIntervals.trySend(it) }
                        if (root.number("id") == 2L && root.obj("subscribe") != null) subscribed.complete(Unit)
                        root.obj("push")?.let { push ->
                            if (push.obj("disconnect") != null || push.obj("unsubscribe") != null) fail("Chat disconnected.")
                            if (push.text("channel") == channel) push.obj("pub")?.get("data")?.decodedObject()?.let { envelope ->
                                val name = envelope.text("event") ?: return@let
                                envelope["data"]?.decodedObject()?.let { KickParser.event(name, it)?.let(onEvent) }
                            }
                        }
                    }
                    return
                }
                val (name, data) = KickParser.envelope(text) ?: return
                when (name) {
                    "pusher:connection_established" -> webSocket.send(buildJsonObject {
                        put("event", "pusher:subscribe")
                        put("data", buildJsonObject { put("auth", ""); put("channel", channel) })
                    }.toString())
                    "pusher_internal:subscription_succeeded" -> subscribed.complete(Unit)
                    "pusher:ping" -> webSocket.send("{\"event\":\"pusher:pong\",\"data\":{}}")
                    "pusher:error", "pusher:subscription_error" -> fail("Chat subscription failed.")
                    else -> if (data != null) KickParser.event(name, data)?.let(onEvent)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                closed.completeExceptionally(t)
                subscribed.completeExceptionally(t)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(Unit)
                subscribed.completeExceptionally(IOException("Chat disconnected."))
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
        })
        try {
            if (withTimeoutOrNull(15_000) { subscribed.await(); true } != true) {
                throw IOException("Chat subscription timed out.")
            }
            onReady()
            val heartbeat = launch {
                while (isActive) {
                    delay(25_000)
                    if (System.nanoTime() - lastActivity.get() > activityTimeout.get()) {
                        closed.completeExceptionally(IOException("Chat heartbeat timed out."))
                        break
                    }
                    if (endpoint.transport == ChatTransport.Pusher) socket.send("{\"event\":\"pusher:ping\",\"data\":{}}")
                }
            }
            val refresh = launch {
                try {
                    for (ttl in refreshIntervals) {
                        delay((ttl * 1_000 - 10_000).coerceAtLeast(1_000))
                        val freshToken = realtime.token(endpoint.clientId)
                        if (!socket.send(buildJsonObject {
                            put("id", nextCommand.getAndIncrement())
                            put("refresh", buildJsonObject { put("token", freshToken) })
                        }.toString())) fail("Chat token refresh failed.")
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { fail("Chat token refresh failed.") }
            }
            try { closed.await() } finally { heartbeat.cancel(); refresh.cancel() }
        } finally {
            refreshIntervals.close()
            socket.close(1000, "Leaving channel")
            socket.cancel()
        }
    }

}
