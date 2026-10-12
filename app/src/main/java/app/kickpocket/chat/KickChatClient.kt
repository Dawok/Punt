package app.kickpocket.chat

import app.kickpocket.data.ChatConnection
import app.kickpocket.data.ChatEvent
import app.kickpocket.data.KickParser
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class KickChatClient(private val http: OkHttpClient, private val socketUrl: String = DEFAULT_URL) {
    suspend fun collect(
        chatroomId: Long,
        onState: (ChatConnection) -> Unit,
        onEvent: (ChatEvent) -> Unit,
    ) {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            onState(if (failures == 0) ChatConnection.Connecting else ChatConnection.Reconnecting)
            try {
                session(chatroomId, {
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

    private suspend fun session(chatroomId: Long, onReady: () -> Unit, onEvent: (ChatEvent) -> Unit) = coroutineScope {
        val subscribed = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val lastActivity = AtomicLong(System.nanoTime())
        val channel = "chatrooms.$chatroomId.v2"
        val socket = http.newWebSocket(Request.Builder().url(socketUrl).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                lastActivity.set(System.nanoTime())
                val (name, data) = KickParser.envelope(text) ?: return
                when (name) {
                    "pusher:connection_established" -> webSocket.send(buildJsonObject {
                        put("event", "pusher:subscribe")
                        put("data", buildJsonObject { put("auth", ""); put("channel", channel) })
                    }.toString())
                    "pusher_internal:subscription_succeeded" -> subscribed.complete(Unit)
                    "pusher:ping" -> webSocket.send("{\"event\":\"pusher:pong\",\"data\":{}}")
                    "pusher:error" -> closed.completeExceptionally(IOException("Chat subscription failed."))
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
                    if (System.nanoTime() - lastActivity.get() > 60_000_000_000L) {
                        closed.completeExceptionally(IOException("Chat heartbeat timed out."))
                        break
                    }
                    socket.send("{\"event\":\"pusher:ping\",\"data\":{}}")
                }
            }
            try { closed.await() } finally { heartbeat.cancel() }
        } finally {
            socket.close(1000, "Leaving channel")
            socket.cancel()
        }
    }

    companion object {
        // Public Pusher application key used by Kick's website; this is not an OAuth secret.
        const val DEFAULT_URL = "wss://ws-us2.pusher.com/app/32cbd69e4b950bf97679?protocol=7&client=js&version=8.4.0&flash=false"
    }
}
