package app.kickpocket.chat

data class EmoteInsertion(val text: String, val cursor: Int)

/** Replace the selection with a complete emote token, separated from neighboring words. */
fun insertEmote(text: String, selectionStart: Int, selectionEnd: Int, alias: String): EmoteInsertion? {
    if (alias.isBlank() || alias.any(Char::isWhitespace)) return null
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
    val before = text.substring(0, start)
    val after = text.substring(end)
    val leading = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
    val trailing = if (after.isEmpty() || !after.first().isWhitespace()) " " else ""
    val insertion = leading + alias + trailing
    val result = before + insertion + after
    if (result.length > 500) return null
    // Reuse an existing space after the selection, placing the cursor ready for the next word.
    val cursor = before.length + insertion.length + if (trailing.isEmpty() && after.isNotEmpty()) 1 else 0
    return EmoteInsertion(result, cursor)
}
