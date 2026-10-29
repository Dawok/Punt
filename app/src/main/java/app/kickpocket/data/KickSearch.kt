package app.kickpocket.data

import java.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

data class SearchChannel(val slug: String, val name: String, val avatar: String?, val live: Boolean)
data class KickSearchResult(val channels: List<SearchChannel> = emptyList(), val categories: List<CategoryCard> = emptyList())
data class KickSearchState(val query: String = "", val loading: Boolean = false,
    val result: KickSearchResult = KickSearchResult(), val error: String? = null)

object KickSearch {
    fun query(value: String): String? = value.trim().removePrefix("@").take(100)
        .takeIf { it.length >= 3 && !it.contains('/') && !it.contains("://") }

    fun url(query: String): String = "https://kick.com/api/search".toHttpUrl().newBuilder()
        .addQueryParameter("searched_word", query).build().toString()

    fun parse(root: JsonElement): KickSearchResult {
        val data = root.asObject() ?: throw IOException("Kick returned an unexpected search response.")
        val channels = data["channels"] as? JsonArray ?: throw IOException("Kick search is unavailable.")
        val categories = data["categories"] as? JsonArray ?: throw IOException("Kick search is unavailable.")
        return KickSearchResult(channels.mapNotNull {
            val item = it.asObject() ?: return@mapNotNull null
            val slug = item.text("slug") ?: return@mapNotNull null
            val user = item.obj("user")
            SearchChannel(slug, user?.text("username") ?: slug, user?.text("profilePic") ?: user?.text("profile_pic"),
                item.flag("isLive") || item.flag("is_live"))
        }.distinctBy { it.slug }, categories.mapNotNull {
            val item = it.asObject() ?: return@mapNotNull null
            CategoryCard(item.number("id") ?: return@mapNotNull null, item.text("slug") ?: return@mapNotNull null,
                item.text("name") ?: return@mapNotNull null, item.obj("banner")?.text("src"), item.number("viewers") ?: 0)
        }.distinctBy { it.id })
    }
}
