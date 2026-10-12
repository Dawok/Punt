package app.kickpocket.chat

import app.kickpocket.data.ChatEvent
import app.kickpocket.data.ChatMessage

/** Applies moderation before publishing bounded snapshots to the UI. */
class ChatBuffer(private val capacity: Int = 500) {
    private val messages = LinkedHashMap<String, ChatMessage>()
    private val deleted = LinkedHashSet<String>()
    private var version = 0L

    init { require(capacity > 0) }

    @Synchronized
    fun apply(event: ChatEvent) {
        when (event) {
            is ChatEvent.Message -> {
                val message = event.message
                if (message.id in deleted || message.id in messages) return
                messages[message.id] = message
                while (messages.size > capacity) messages.remove(messages.keys.first())
            }
            is ChatEvent.Delete -> {
                deleted.add(event.messageId)
                while (deleted.size > capacity) deleted.remove(deleted.first())
                messages.remove(event.messageId)
            }
            is ChatEvent.Ban -> {
                // A ban removes existing messages; subsequent messages can be valid after an unban.
                messages.entries.removeAll { it.value.userId == event.userId }
            }
            ChatEvent.Clear -> {
                messages.clear()
                deleted.clear()
            }
        }
        version++
    }

    @Synchronized fun snapshot(): Pair<Long, List<ChatMessage>> = version to messages.values.toList()
}

