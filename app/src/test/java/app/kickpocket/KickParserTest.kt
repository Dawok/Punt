package app.kickpocket

import app.kickpocket.data.AppJson
import app.kickpocket.data.ChatEvent
import app.kickpocket.data.KickParser
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class KickParserTest {
    @Test fun keepsChannelUserAndChatroomIdsSeparate() {
        val channel = KickParser.channel(AppJson.parseToJsonElement("""
            {"id":10,"user_id":20,"slug":"user","chatroom":{"id":30},
             "playback_url":"https://example.com/live.m3u8","user":{"username":"User"},
             "livestream":{"session_title":"Live stream","viewer_count":500,"categories":[{"name":"Games"}]}}
        """).jsonObject)
        assertEquals(10L, channel.id)
        assertEquals(20L, channel.userId)
        assertEquals(30L, channel.chatroomId)
        assertTrue(channel.isLive)
        assertEquals("Games", channel.category)
    }

    @Test fun handlesOfflineChannelAndUnknownFields() {
        val channel = KickParser.channel(AppJson.parseToJsonElement("""
            {"id":10,"user_id":20,"slug":"user","chatroom":{"id":30},"livestream":null,"future_field":true}
        """).jsonObject)
        assertFalse(channel.isLive)
        assertNull(channel.playbackUrl)
    }

    @Test fun parsesNestedPusherDataAndModerationEvents() {
        val raw = """{"event":"App\\Events\\ChatMessageEvent","data":"{\"id\":\"m1\",\"content\":\"Hello\",\"sender\":{\"id\":5,\"username\":\"viewer\",\"identity\":{\"color\":\"#123456\",\"badges\":[{\"type\":\"moderator\"}]}}}"}"""
        val (name, data) = requireNotNull(KickParser.envelope(raw))
        val event = KickParser.event(name, requireNotNull(data)) as ChatEvent.Message
        assertEquals("Hello", event.message.content)
        assertEquals(listOf("moderator"), event.message.badges)
        val deletion = KickParser.event("App\\Events\\MessageDeletedEvent", AppJson.parseToJsonElement("""{"message":{"id":"m1"}}""").jsonObject)
        assertEquals(ChatEvent.Delete("m1"), deletion)
        assertNull(KickParser.envelope("not json"))
    }

    @Test fun selectsWebpAndStaticVariantsWithChannelAlias() {
        val emotes = KickParser.sevenTvEmotes(AppJson.parseToJsonElement("""
          {"emotes":[{"id":"e1","name":"Alias","data":{"name":"Original","animated":true,"flags":256,
          "host":{"url":"//cdn.7tv.app/emote/e1","files":[
          {"name":"4x.avif","format":"AVIF"},
          {"name":"2x.webp","static_name":"2x_static.webp","format":"WEBP","width":128,"height":64}]}}}]}
        """).jsonObject)
        val emote = requireNotNull(emotes["Alias"])
        assertEquals("https://cdn.7tv.app/emote/e1/2x.webp", emote.url)
        assertEquals("https://cdn.7tv.app/emote/e1/2x_static.webp", emote.staticUrl)
        assertEquals(2f, emote.aspectRatio)
        assertTrue(emote.zeroWidth)
        assertTrue(emote.animated)
        assertFalse(emotes.containsKey("Original"))
    }
}

