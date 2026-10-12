package app.kickpocket.data

import java.net.URI
import java.util.Locale

object ChannelInput {
    fun normalize(input: String): String {
        var value = input.trim()
        if (value.startsWith("kick.com/", ignoreCase = true) || value.startsWith("www.kick.com/", ignoreCase = true)) {
            value = "https://$value"
        }
        if (value.contains("://")) {
            val uri = runCatching { URI(value) }.getOrNull()
                ?: throw IllegalArgumentException("Enter a channel name or a Kick channel link.")
            require(uri.scheme in listOf("https", "http") && uri.host?.lowercase(Locale.ROOT) in listOf("kick.com", "www.kick.com")) {
                "Use a channel link from kick.com."
            }
            value = uri.path.trim('/').also {
                require(!it.contains('/')) { "Use the channel page link, such as kick.com/channel." }
            }
        }
        value = value.removePrefix("@").lowercase(Locale.ROOT)
        require(value.matches(Regex("[a-z0-9][a-z0-9_-]{0,49}"))) {
            "Enter a channel name using letters, numbers, underscores, or hyphens."
        }
        return value
    }
}

