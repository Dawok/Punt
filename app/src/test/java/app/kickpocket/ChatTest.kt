package app.kickpocket

import app.kickpocket.chat.ChatBuffer
import app.kickpocket.chat.ChatToken
import app.kickpocket.chat.ChatTokenizer
import app.kickpocket.data.ChatEvent
import app.kickpocket.data.ChatMessage
import app.kickpocket.data.Emote
import org.junit.Assert.*
import org.junit.Test

class ChatTest {
    private fun message(id: String, userId: Long = 1) = ChatMessage(id, userId, "viewer", null, "hello")
    private val base = Emote("base", "Base", "https://example.com/base.webp")
    private val overlay = Emote("overlay", "Hat", "https://example.com/hat.webp", zeroWidth = true)

    @Test fun capsHistoryAndDeduplicatesReconnectMessages() {
        val buffer = ChatBuffer(3)
        for (id in 1..5) buffer.apply(ChatEvent.Message(message("$id")))
        buffer.apply(ChatEvent.Message(message("5")))
        assertEquals(listOf("3", "4", "5"), buffer.snapshot().second.map { it.id })
    }

    @Test fun handlesDeletionBeforeArrivalAndUserModeration() {
        val buffer = ChatBuffer()
        buffer.apply(ChatEvent.Delete("deleted"))
        buffer.apply(ChatEvent.Message(message("deleted")))
        buffer.apply(ChatEvent.Message(message("one", 1)))
        buffer.apply(ChatEvent.Message(message("two", 2)))
        buffer.apply(ChatEvent.Ban(1))
        assertEquals(listOf("two"), buffer.snapshot().second.map { it.id })
        buffer.apply(ChatEvent.Clear)
        assertTrue(buffer.snapshot().second.isEmpty())
    }

    @Test fun tokenizesAdjacentKickMarkupAndPreservesText() {
        val tokens = ChatTokenizer.tokenize("hello[emote:42:Smile]  Base! Base", mapOf("Base" to base))
        assertEquals(ChatToken.Text("hello"), tokens.first())
        assertEquals("kick:42", (tokens[1] as ChatToken.Emotes).layers.single().id)
        assertEquals(ChatToken.Text("  Base! "), tokens[2])
        assertEquals(base, (tokens.last() as ChatToken.Emotes).layers.single())
    }

    @Test fun overlaysZeroWidthEmotesOnlyAcrossSpaces() {
        val catalog = mapOf("Base" to base, "Hat" to overlay)
        val tokens = ChatTokenizer.tokenize("Base Hat Hat", catalog)
        assertEquals(listOf(base, overlay, overlay), (tokens.single() as ChatToken.Emotes).layers)
        assertEquals(3, ChatTokenizer.tokenize("Base text Hat", catalog).size)
        assertEquals(3, ChatTokenizer.tokenize("Base\nHat", catalog).size)
    }

    @Test fun keepsUnknownAndMalformedEmotesReadable() {
        val content = "unknown [emote:no:bad] 🙂\nnormal"
        val tokens = ChatTokenizer.tokenize(content, emptyMap())
        assertEquals(listOf(ChatToken.Text(content)), tokens)
    }
}

