package app.kickpocket.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class BrowseSort(val label: String) { Viewers("Viewers: high to low"), Recommended("Recommended") }

internal object DiscoveryRequests {
    fun streams(category: CategoryCard?, cursor: String?, sort: BrowseSort): HttpUrl {
        val recommended = sort == BrowseSort.Recommended
        val path = if (recommended) "recommendations/livestreams/list" else "livestreams"
        return "https://web.kick.com/api/v1/$path".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "20")
            .apply {
                if (!recommended) addQueryParameter("sort", "viewer_count_desc")
                category?.let { addQueryParameter("category_id", it.id.toString()) }
                cursor?.let { addQueryParameter(if (recommended) "cursor" else "after", it) }
            }.build()
    }

    fun categories(page: String?, sort: BrowseSort): HttpUrl =
        if (sort == BrowseSort.Recommended) "https://web.kick.com/api/v1/recommendations/categories".toHttpUrl().newBuilder()
            .addQueryParameter("lang_code", "en").build()
        else "https://kick.com/api/v1/subcategories".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "20").addQueryParameter("page", page ?: "1").build()
}
