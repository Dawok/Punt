package app.kickpocket

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.kickpocket.data.AccountState
import app.kickpocket.ui.PuntTheme
import app.kickpocket.ui.LoginDialog
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
class LoginFormSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realSignInFieldsAreVisibleInTheBrowserViewport() {
        assumeTrue("Opt-in real Kick form check", InstrumentationRegistry.getArguments().getString("verifyKickSignIn") == "true")
        val view = openBrowser()
        awaitVisibleFields(view, 180_000)
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test fun missingFormIsOpenedExpandedAndStaysVisibleAfterContinueFeedback() {
        val view = openBrowser(useFixture = true)
        awaitVisibleFields(view, 30_000)
        compose.onNodeWithText("Continue").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("You are not signed in yet. Complete Kick’s sign-in form below, then continue."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        awaitVisibleFields(view, 20_000)
        compose.onNodeWithText("Cancel").performClick()
    }

    private fun openBrowser(useFixture: Boolean = false): WebView {
        if (useFixture) {
            var account by mutableStateOf(AccountState())
            var visible by mutableStateOf(true)
            compose.runOnUiThread {
                compose.activity.setContent {
                    PuntTheme {
                        if (visible) LoginDialog(account, onCheck = { report ->
                            if (report) account = AccountState(error = "You are not signed in yet. Complete Kick’s sign-in form below, then continue.")
                        }, onDismiss = { visible = false }, loadSignInPage = {
                            it.loadDataWithBaseURL("https://kick.com/", ClippedForm, "text/html", "UTF-8", "https://kick.com/")
                        })
                    }
                }
            }
        } else compose.onNodeWithText("Log in").performClick()
        var browser: WebView? = null
        compose.waitUntil(20_000) {
            compose.runOnUiThread {
                browser = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findWebView)
            }
            browser?.height?.let { it > 0 } == true
        }
        val view = requireNotNull(browser)
        compose.runOnUiThread {
            val visible = Rect()
            assertTrue(view.getGlobalVisibleRect(visible))
            Log.i("LoginFormSmoke", "Browser ${view.width}x${view.height}, visible $visible")
            assertTrue("Browser is clipped: ${visible.height()} of ${view.height}px", visible.height() >= view.height * 0.9)
            assertTrue("Browser is only ${view.height}px tall", view.height >= view.resources.displayMetrics.heightPixels / 2)
        }
        return view
    }

    private fun awaitVisibleFields(view: WebView, timeoutMs: Long) {
        val geometry = AtomicReference("{}")
        var previous = ""
        var lastProbe = 0L
        compose.waitUntil(timeoutMs) {
            val now = SystemClock.uptimeMillis()
            if (now - lastProbe >= 1_000) {
                lastProbe = now
                compose.runOnUiThread {
                    view.evaluateJavascript(FormGeometry) { geometry.set(it) }
                }
            }
            if (geometry.get() != previous) {
                previous = geometry.get()
                Log.i("LoginFormSmoke", previous)
            }
            runCatching { JSONObject(geometry.get()).getBoolean("visible") }.getOrDefault(false)
        }
        assertTrue("Sign-in fields are outside the viewport: ${geometry.get()}", JSONObject(geometry.get()).getBoolean("visible"))
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }

    private val FormGeometry = """
        (() => {
          const fields = ['input[name=emailOrUsername]', 'input[type=password]'].map(s => document.querySelector(s));
          const bounds = fields.map(e => e ? e.getBoundingClientRect().toJSON() : null);
          const visible = fields.every(e => {
            if (!e) return false;
            const r = e.getBoundingClientRect(), style = getComputedStyle(e);
            const x = r.left + r.width / 2, y = r.top + r.height / 2;
            return r.width > 0 && r.height > 0 && r.left >= 0 && r.right <= innerWidth &&
              r.top >= 0 && r.bottom <= innerHeight && style.visibility !== 'hidden' &&
              document.elementFromPoint(x, y) === e;
          });
          return { visible, width: innerWidth, height: innerHeight, bounds };
        })()
    """.trimIndent()

    private val ClippedForm = """
        <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          body { margin: 0; background: #0c0e10; color: white; }
          [data-testid="auth-modal"] { position: fixed; top: 0; left: 0; width: 100%; height: 32px; overflow: hidden; background: #14171b; }
          h1 { margin: 0 0 24px; font-size: 26px; }
          input { display: block; margin: 16px; width: calc(100% - 40px); height: 36px; }
        </style>
        <button data-testid="login" onclick="if(!window.handlersReady){window.handlersReady=true;return;}this.remove();document.body.appendChild(document.querySelector('template').content.cloneNode(true))">Log in</button>
        <template><div data-testid="auth-modal">
          <h1>Sign in</h1>
          <label>Email or username<input name="emailOrUsername"></label>
          <label>Password<input type="password"></label>
        </div></template>
    """.trimIndent()
}
