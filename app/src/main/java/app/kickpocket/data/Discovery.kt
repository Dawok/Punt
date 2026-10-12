package app.kickpocket.data

import java.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class ChannelCard(
    val slug: String,
    val username: String,
    val title: String,
    val category: String,
    val viewers: Long,
    val avatar: String?,
    val thumbnail: String?,
)

data class CategoryCard(val id: Long, val slug: String, val name: String, val image: String?, val viewers: Long)
data class BrowsePage<T>(val items: List<T>, val cursor: String? = null)

object DiscoveryParser {
    fun streams(root: JsonElement): BrowsePage<ChannelCard> {
        val data = root.asObject()?.obj("data") ?: throw IOException("Kick returned an unexpected stream list.")
        val items = data.array("livestreams").mapNotNull { item ->
            val stream = item.asObject() ?: return@mapNotNull null
            val channel = stream.obj("channel") ?: return@mapNotNull null
            val slug = channel.text("slug") ?: return@mapNotNull null
            ChannelCard(slug, channel.text("username") ?: slug, stream.text("title") ?: slug,
                stream.obj("category")?.text("name") ?: "", stream.number("viewer_count") ?: 0,
                channel.text("profile_pic"), stream.obj("thumbnail")?.text("src"))
        }
        return BrowsePage(items.distinctBy { it.slug }, data.obj("pagination")?.text("next_cursor"))
    }

    fun categories(root: JsonElement): BrowsePage<CategoryCard> {
        val data = (root as? JsonArray) ?: root.asObject()?.get("data") as? JsonArray
            ?: throw IOException("Kick returned an unexpected category list.")
        val items = data.mapNotNull { item ->
            val category = item.asObject() ?: return@mapNotNull null
            val id = category.number("id") ?: return@mapNotNull null
            val slug = category.text("slug") ?: return@mapNotNull null
            CategoryCard(id, slug, category.text("name") ?: slug,
                category.obj("thumbnail")?.text("src") ?: category.obj("banner")?.text("src") ?: category.obj("banner")?.text("url"),
                category.number("viewers") ?: 0)
        }
        val obj = root.asObject()
        // Only carry a page number, never follow a server-provided URL with credentials.
        val next = if (!obj?.text("next_page_url").isNullOrBlank()) obj?.number("current_page")?.plus(1)?.toString() else null
        return BrowsePage(items.distinctBy { it.id }, next)
    }

    fun followed(root: JsonElement): BrowsePage<ChannelCard> {
        val obj = root.asObject() ?: throw IOException("Kick returned an unexpected followed-channel list.")
        val data = obj.obj("data") ?: obj
        val channels = data["channels"] as? JsonArray ?: data["data"] as? JsonArray
            ?: throw IOException("Kick returned an unexpected followed-channel list.")
        val items = channels.mapNotNull { element ->
            val channel = element.asObject() ?: return@mapNotNull null
            val live = channel.obj("livestream")
            if (!channel.flag("is_live") && (live == null || !live.flag("is_live"))) return@mapNotNull null
            val slug = channel.text("channel_slug") ?: channel.text("slug") ?: return@mapNotNull null
            ChannelCard(slug, channel.text("user_username") ?: channel.obj("user")?.text("username") ?: slug,
                channel.text("session_title") ?: live?.text("session_title") ?: "Live now",
                channel.text("category_name") ?: channel.obj("category")?.text("name")
                    ?: live?.array("categories")?.firstOrNull()?.asObject()?.text("name") ?: "",
                channel.number("viewer_count") ?: live?.number("viewer_count") ?: 0,
                channel.text("profile_picture") ?: channel.obj("user")?.text("profile_pic"),
                channel.obj("thumbnail")?.text("src") ?: live?.obj("thumbnail")?.text("src"))
        }
        return BrowsePage(items.distinctBy { it.slug }, data.text("nextCursor") ?: data.text("next_cursor"))
    }

    fun account(root: JsonElement): KickAccount? {
        val obj = root.asObject() ?: return null
        val user = obj.obj("user") ?: obj.obj("data")?.obj("user") ?: obj.obj("data") ?: obj
        val id = user.number("id") ?: return null
        val username = user.text("username") ?: return null
        return KickAccount(id, username, user.text("profile_pic") ?: user.text("profile_picture"))
    }
}

data class KickAccount(val id: Long, val username: String, val avatar: String?)
