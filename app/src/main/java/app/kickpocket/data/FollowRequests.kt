package app.kickpocket.data

import java.io.IOException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** The viewer website uses channel slugs, rather than broadcaster or chatroom IDs. */
internal object FollowRequests {
    fun url(slug: String, action: String): String {
        require(action == "me" || action == "follow")
        require(slug == ChannelInput.normalize(slug))
        return "https://kick.com/api/v2/channels/$slug/$action"
    }

    fun status(root: JsonElement): Boolean {
        val obj = root.asObject()?.let { it.obj("data") ?: it }
            ?: throw IOException("Could not read your follow status. Try again.")
        (obj["is_following"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull?.let { return it }
        if (obj.containsKey("following_since")) {
            val since = obj["following_since"]
            if (since == JsonNull) return false
            if (since is JsonPrimitive && since.isString && since.content.isNotBlank()) return true
        }
        throw IOException("Could not read your follow status. Try again.")
    }
}
