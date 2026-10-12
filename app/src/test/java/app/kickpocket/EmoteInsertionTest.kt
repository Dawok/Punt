package app.kickpocket

import app.kickpocket.chat.EmoteInsertion
import app.kickpocket.chat.insertEmote
import app.kickpocket.data.AppJson
import app.kickpocket.data.KickParser
import app.kickpocket.data.SessionRequests
import app.kickpocket.data.asObject
import app.kickpocket.data.text
import org.junit.Assert.*
import org.junit.Test

class EmoteInsertionTest {
    @Test fun addsWholeTokensAtTheCursorWithWordSeparators() {
        assertEquals(EmoteInsertion("Native ", 7), insertEmote("", 0, 0, "Native"))
        assertEquals(EmoteInsertion("hello Native ", 13), insertEmote("hello", 5, 5, "Native"))
        assertEquals(EmoteInsertion("hello Native world", 13), insertEmote("helloworld", 5, 5, "Native"))
        assertEquals(EmoteInsertion("hello Native world", 13), insertEmote("hello world", 6, 6, "Native"))
    }

    @Test fun replacesSelectionsInEitherDirectionAndReusesWhitespace() {
        assertEquals(EmoteInsertion("one Native two", 11), insertEmote("one replace two", 4, 11, "Native"))
        assertEquals(EmoteInsertion("one Native two", 11), insertEmote("one replace two", 11, 4, "Native"))
        assertEquals(EmoteInsertion("one\nNative\ntwo", 11), insertEmote("one\nreplace\ntwo", 4, 11, "Native"))
    }

    @Test fun enforcesMessageLimitWithoutCuttingAnEmoteOrLosingTheDraft() {
        assertNull(insertEmote("x".repeat(500), 500, 500, "Native"))
        val replacement = insertEmote("x".repeat(500), 0, 7, "Native")!!
        assertEquals(500, replacement.text.length)
        assertTrue(replacement.text.startsWith("Native "))
        assertNull(insertEmote("", 0, 0, ""))
        assertNull(insertEmote("", 0, 0, "two words"))
    }

    @Test fun consecutiveSelectionsRemainSeparateAndNativeNamesEncodeOnlyAtSendTime() {
        val first = insertEmote("hello", 5, 5, "Native")!!
        val second = insertEmote(first.text, first.cursor, first.cursor, "Alias")!!
        assertEquals("hello Native Alias ", second.text)
        val native = KickParser.nativeEmote("123", "Native")
        val body = SessionRequests.chatBody(second.text, mapOf("Native" to native))
        assertEquals("hello [emote:123:Native] Alias ", AppJson.parseToJsonElement(body).asObject()!!.text("content"))
    }
}
