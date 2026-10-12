package app.kickpocket.chat

import app.kickpocket.data.Emote
import app.kickpocket.data.KickParser

sealed interface ChatToken {
    data class Text(val value: String) : ChatToken
    data class Emotes(val layers: List<Emote>) : ChatToken
}

object ChatTokenizer {
    private val parts = Regex("\\[emote:(\\d{1,20}):([^\\]\\r\\n]{1,100})\\]|\\s+|[^\\s\\[]+|\\[")

    fun tokenize(content: String, emotes: Map<String, Emote>): List<ChatToken> {
        val tokens = mutableListOf<ChatToken>()
        for (match in parts.findAll(content)) {
            val text = match.value
            val emote = if (match.groupValues[1].isNotEmpty()) {
                KickParser.nativeEmote(match.groupValues[1], match.groupValues[2])
            } else emotes[text]
            if (emote == null) {
                val last = tokens.lastOrNull()
                if (last is ChatToken.Text) tokens[tokens.lastIndex] = ChatToken.Text(last.value + text)
                else tokens.add(ChatToken.Text(text))
            } else {
                // Zero-width modifiers overlay the preceding emote, including across spaces.
                val index = if (tokens.lastOrNull() is ChatToken.Text &&
                    (tokens.last() as ChatToken.Text).value.all { it == ' ' }) tokens.lastIndex - 1 else tokens.lastIndex
                val previous = tokens.getOrNull(index)
                if (emote.zeroWidth && previous is ChatToken.Emotes) {
                    if (index != tokens.lastIndex) tokens.removeAt(tokens.lastIndex)
                    tokens[index] = ChatToken.Emotes(previous.layers + emote)
                } else tokens.add(ChatToken.Emotes(listOf(emote)))
            }
        }
        return tokens
    }
}

