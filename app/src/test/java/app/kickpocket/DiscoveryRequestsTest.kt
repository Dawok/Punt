package app.kickpocket

import app.kickpocket.data.*
import org.junit.Assert.*
import org.junit.Test

class DiscoveryRequestsTest {
    private val category = CategoryCard(15, "category", "Category", null, 10)

    @Test fun viewerAndRecommendedStreamsUseDifferentEndpointsAndCursorNames() {
        val viewers = DiscoveryRequests.streams(category, "opaque/+ =", BrowseSort.Viewers)
        assertEquals("/api/v1/livestreams", viewers.encodedPath)
        assertEquals("viewer_count_desc", viewers.queryParameter("sort"))
        assertEquals("15", viewers.queryParameter("category_id"))
        assertEquals("opaque/+ =", viewers.queryParameter("after"))
        assertNull(viewers.queryParameter("cursor"))
        val recommended = DiscoveryRequests.streams(category, "opaque/+ =", BrowseSort.Recommended)
        assertEquals("/api/v1/recommendations/livestreams/list", recommended.encodedPath)
        assertEquals("15", recommended.queryParameter("category_id"))
        assertEquals("opaque/+ =", recommended.queryParameter("cursor"))
        assertNull(recommended.queryParameter("after"))
        assertNull(recommended.queryParameter("sort"))
    }

    @Test fun recommendationCategoriesAreNotArtificiallyPaginatedOrSorted() {
        val url = DiscoveryRequests.categories("2", BrowseSort.Recommended)
        assertEquals("web.kick.com", url.host)
        assertEquals("/api/v1/recommendations/categories", url.encodedPath)
        assertNull(url.queryParameter("page"))
        val ranked = DiscoveryRequests.categories("2", BrowseSort.Viewers)
        assertEquals("kick.com", ranked.host)
        assertEquals("2", ranked.queryParameter("page"))
    }
}
