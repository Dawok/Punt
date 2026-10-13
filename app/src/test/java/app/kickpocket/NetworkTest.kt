package app.kickpocket

import app.kickpocket.chat.KickChatClient
import app.kickpocket.chat.KickRealtime
import app.kickpocket.data.AppJson
import app.kickpocket.data.asObject
import app.kickpocket.data.obj
import app.kickpocket.data.text
import app.kickpocket.data.ApiException
import app.kickpocket.data.ChatConnection
import app.kickpocket.data.ChatEvent
import app.kickpocket.data.KickRepository
import app.kickpocket.data.awaitBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class NetworkTest {
    @Test fun discoversPusherKeyAndClusterInsteadOfUsingACompiledEndpoint() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"data":{"mode":"websocket","connections":[{"provider":"pusher","credentials":{"app_key":"publickey","cluster":"eu"}}]}}"""))
        server.start()
        val http = OkHttpClient()
        try {
            val endpoint = KickRealtime(http, server.url("/").toString()).resolve(20)
            assertEquals("wss://ws-eu.pusher.com/app/publickey?protocol=7&client=js&version=8.4.0&flash=false", endpoint.url)
            assertEquals("/realtime/channels/20/chat/connection", server.takeRequest().path)
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test fun discoversCentrifugoReceivesBatchedMessagesAnswersHeartbeatAndRefreshesToken() = runBlocking {
        val server = MockWebServer()
        val descriptorBody = CompletableDeferred<String>()
        val pong = CompletableDeferred<Unit>()
        val refreshed = CompletableDeferred<Unit>()
        val messages = Channel<ChatEvent>(Channel.UNLIMITED)
        val ready = CompletableDeferred<Unit>()
        val tokens = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/realtime/channels/20/chat/connection" -> {
                    descriptorBody.complete(request.body.readUtf8())
                    MockResponse().setBody("""{"data":{"mode":"websocket","connections":[{"provider":"centrifugo","credentials":{"url":"${server.url("/socket").toString().replace("http://", "ws://")}"}}]}}""")
                }
                "/realtime/auth/connection" -> MockResponse().setBody("""{"data":{"token":"token-${tokens.incrementAndGet()}"}}""")
                "/socket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val root = AppJson.parseToJsonElement(text).asObject()!!
                        when {
                            root.obj("connect") != null -> {
                                assertEquals("token-1", root.obj("connect")!!.text("token"))
                                webSocket.send("""{"id":1,"connect":{"ping":25,"pong":true,"expires":true,"ttl":11}}""")
                            }
                            root.obj("subscribe") != null -> {
                                assertEquals("chatrooms.30.v2", root.obj("subscribe")!!.text("channel"))
                                webSocket.send("""{"id":2,"subscribe":{}}
                                    |{"push":{"channel":"chatrooms.30.v2","pub":{"data":{"event":"App\\Events\\ChatMessageEvent","data":{"id":"one","content":"hello","sender":{"id":1,"username":"viewer"}}}}}}
                                    |{"push":{"channel":"chatrooms.30.v2","pub":{"data":{"event":"App\\Events\\ChatMessageEvent","data":"{\"id\":\"two\",\"content\":\"again\",\"sender\":{\"id\":1,\"username\":\"viewer\"}}"}}}}
                                    |{}
                                """.trimMargin())
                            }
                            root.obj("refresh") != null -> {
                                assertEquals("token-2", root.obj("refresh")!!.text("token"))
                                webSocket.send("""{"id":3,"refresh":{"expires":false}}""")
                                refreshed.complete(Unit)
                            }
                            root.isEmpty() -> pong.complete(Unit)
                        }
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                })
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val http = OkHttpClient()
        val job = launch {
            KickChatClient(http, realtime = KickRealtime(http, server.url("/").toString()))
                .collect(30, { if (it == ChatConnection.Connected) ready.complete(Unit) }, { messages.trySend(it) }, channelId = 20)
        }
        try {
            withTimeout(5_000) {
                ready.await()
                assertTrue(descriptorBody.await().contains("accepted_providers"))
                assertEquals("hello", (messages.receive() as ChatEvent.Message).message.content)
                assertEquals("again", (messages.receive() as ChatEvent.Message).message.content)
                pong.await()
                refreshed.await()
                assertEquals(2, tokens.get())
            }
        } finally {
            job.cancelAndJoin()
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test fun pusherRejectionReconnectsImmediatelyInsteadOfWaitingForSubscriptionTimeout() = runBlocking {
        val server = MockWebServer()
        val retry = CompletableDeferred<Unit>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"event":"pusher:error","data":{"code":4001,"message":"Invalid cluster"}}""")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
        }))
        server.start()
        val http = OkHttpClient()
        val job = launch {
            KickChatClient(http, server.url("/socket").toString().replace("http://", "ws://"))
                .collect(30, { if (it == ChatConnection.Reconnecting) retry.complete(Unit) }, {})
        }
        try {
            withTimeout(2_000) { retry.await() }
        } finally {
            job.cancelAndJoin()
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

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

