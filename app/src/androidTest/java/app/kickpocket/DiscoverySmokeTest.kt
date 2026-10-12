package app.kickpocket

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import app.kickpocket.data.BrowseSort
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DiscoverySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realLiveFeedAndCategoryFilteringLoadAndFollowingRequestsLogin() {
        val browse = ViewModelProvider(compose.activity)[BrowseViewModel::class.java]
        compose.waitUntil(40_000) { !browse.state.value.loading }
        assertNull(browse.state.value.error)
        assertTrue(browse.state.value.channels.isNotEmpty())
        val counts = browse.state.value.channels.map { it.viewers }
        assertEquals(counts.sortedDescending(), counts)
        compose.onNodeWithTag("browse-sort").performClick()
        compose.onNodeWithText("Recommended").performClick()
        compose.onNodeWithText("Recommended for you").assertIsDisplayed()
        assertEquals(BrowseSort.Recommended, browse.state.value.sort)
        assertTrue(browse.state.value.channels.isEmpty())
        compose.onNodeWithTag("browse-sort").performClick()
        compose.onNodeWithText("Viewers: high to low").performClick()
        compose.waitUntil(40_000) { !browse.state.value.loading }
        compose.onNodeWithText("Categories").performClick()
        compose.waitUntil(40_000) { !browse.state.value.loading }
        assertNull(browse.state.value.error)
        assertTrue(browse.state.value.categories.isNotEmpty())
        val category = browse.state.value.categories.first()
        compose.runOnUiThread { browse.category(category) }
        compose.waitUntil(40_000) { !browse.state.value.loading }
        assertNull(browse.state.value.error)
        assertTrue(browse.state.value.channels.isNotEmpty())
        assertTrue(browse.state.value.channels.all { it.category == category.name })
        compose.onNodeWithContentDescription("Back to categories").assertIsDisplayed().performClick()
        compose.onNodeWithText("Following").performClick()
        compose.onNodeWithText("Log in to Kick").assertIsDisplayed().performClick()
        compose.onNodeWithText("kick.com").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.waitUntil(10_000) { browse.account.value.error != null }
        compose.onNodeWithText("You are not signed in yet. Complete Kick’s sign-in form below, then continue.").assertIsDisplayed()
        compose.onNodeWithText("Reload sign-in").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Following · live now").assertIsDisplayed()
    }
}
