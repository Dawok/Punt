package app.kickpocket

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
class LoginSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun signInRendererFailureShowsRecoveryAndReturnsToBrowsing() {
        compose.onNodeWithText("Log in").performClick()
        compose.onNodeWithText("kick.com").assertIsDisplayed()
        compose.runOnUiThread {
            // The old emulator renderer can also fail naturally before its first URL is committed.
            WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findWebView)?.loadUrl("chrome://crash")
        }
        compose.waitUntil(90_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("The sign-in browser stopped. Tap Reload sign-in. If it happens again, update Android System WebView in the Play Store."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Reload sign-in").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Log in").assertIsDisplayed()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }
}
