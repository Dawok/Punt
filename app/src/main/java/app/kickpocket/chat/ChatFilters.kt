package app.kickpocket.chat

import app.kickpocket.data.AppPreferences
import app.kickpocket.data.ChatMessage

object ChatFilters {
    fun terms(value: String): List<String> = value.lines().map { it.trim().take(100) }
        .filter(String::isNotBlank).distinctBy { it.lowercase(java.util.Locale.ROOT) }.take(100)

    fun visible(messages: List<ChatMessage>, preferences: AppPreferences): List<ChatMessage> {
        val users = preferences.hiddenChatUsers.map { it.removePrefix("@").lowercase(java.util.Locale.ROOT) }.toSet()
        val keywords = preferences.hiddenChatKeywords.filter(String::isNotBlank)
        val recent = ArrayDeque<String>()
        return messages.filter { message ->
            if (message.username.lowercase(java.util.Locale.ROOT) in users || keywords.any { message.content.contains(it, ignoreCase = true) }) false
            else if (preferences.reduceRepeatedMessages) {
                val text = message.content.trim().lowercase(java.util.Locale.ROOT)
                val duplicate = text in recent
                recent.addLast(text)
                if (recent.size > 20) recent.removeFirst()
                !duplicate
            } else true
        }
    }

    fun mentions(content: String, username: String?): Boolean {
        if (username.isNullOrBlank()) return false
        return Regex("(?i)(?<![\\p{L}\\p{N}_-])@?${Regex.escape(username)}(?![\\p{L}\\p{N}_-])").containsMatchIn(content)
    }
}
