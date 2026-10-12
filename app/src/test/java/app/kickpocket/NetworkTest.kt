package app.kickpocket

import app.kickpocket.chat.KickChatClient
import app.kickpocket.data.ApiException
import app.kickpocket.data.ChatConnection
import app.kickpocket.data.ChatEvent
import app.kickpocket.data.KickRepository
import app.kickpocket.data.awaitBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NetworkTest {
    @Test fun reportsForbiddenAccessClearly() = runBlocking {
        val server = MockWebServer()
        server.use {
            server.enqueue(MockResponse().setResponseCode(403))
            try {
                KickRepository().getJson(server.url("/channel").toString())
                fail("Expected forbidden response")
            } catch (error: ApiException) {
                assertEquals(403, error.status)
                assertTrue(error.message!!.contains("refused access"))
            }
        }
    }

    @Test fun waitsForSubscriptionThenReceivesMessagesAndAnswersPing() = runBlocking {
        val server = MockWebServer()
        val subscribed = CompletableDeferred<String>()
        val pong = CompletableDeferred<Unit>()
        val message = CompletableDeferred<ChatEvent>()
        val ready = CompletableDeferred<Unit>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"event":"pusher:connection_established","data":"{}"}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("pusher:subscribe")) {
                    subscribed.complete(text)
                    webSocket.send("""{"event":"pusher_internal:subscription_succeeded","data":{}}""")
                    webSocket.send("""{"event":"App\\Events\\ChatMessageEvent","data":{"id":"one","content":"hello","sender":{"id":1,"username":"viewer"}}}""")
                    webSocket.send("""{"event":"pusher:ping","data":{}}""")
                } else if (text.contains("pusher:pong")) pong.complete(Unit)
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
        }))
        server.start()
        val http = OkHttpClient()
        val job = launch {
            KickChatClient(http, server.url("/chat").toString().replace("http://", "ws://"))
                .collect(30, { if (it == ChatConnection.Connected) ready.complete(Unit) }, { message.complete(it) })
        }
        try {
            withTimeout(5_000) {
                ready.await()
                assertTrue(subscribed.await().contains("chatrooms.30.v2"))
                assertEquals("hello", (message.await() as ChatEvent.Message).message.content)
                pong.await()
            }
        } finally {
            job.cancelAndJoin()
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test fun cancelsAnOutstandingHttpCall() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{}").setBodyDelay(1, TimeUnit.SECONDS))
        server.start()
        val http = OkHttpClient()
        val call = http.newCall(Request.Builder().url(server.url("/slow")).build())
        val job = async(Dispatchers.IO) { call.awaitBody() }
        server.takeRequest(2, TimeUnit.SECONDS)
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
        server.shutdown()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }
}

