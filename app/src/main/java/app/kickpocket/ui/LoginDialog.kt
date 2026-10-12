package app.kickpocket.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.kickpocket.data.AccountState
import app.kickpocket.data.SessionRequests
import kotlinx.coroutines.delay

internal const val KickLoginUrl = "https://kick.com/?auth=sign-in"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginDialog(
    account: AccountState,
    onCheck: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    loadSignInPage: (WebView) -> Unit = { it.loadUrl(KickLoginUrl) },
) {
    var webLoading by remember { mutableStateOf(true) }
    var webError by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var rendererGone by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val check by rememberUpdatedState(onCheck)
    val currentAccount by rememberUpdatedState(account)
    LaunchedEffect(account.account?.id) { if (account.account != null) onDismiss() }
    LaunchedEffect(Unit) {
        var previous: String? = null
        while (true) {
            val fingerprint = SessionRequests.fingerprint(CookieManager.getInstance().getCookie("https://kick.com"))
            if (fingerprint != null && fingerprint != previous) { previous = fingerprint; check(false) }
            delay(2_000)
        }
    }
    LaunchedEffect(webView, attempt) {
        val view = webView ?: return@LaunchedEffect
        // Wait for Kick's client-side form, rather than treating the static homepage as a successful load.
        var foundForm = false
        repeat(30) {
            delay(2_000)
            if (foundForm || currentAccount.account != null || webError != null) return@LaunchedEffect
            if (allowedLoginNavigation(Uri.parse(view.url ?: ""))) {
                view.evaluateJavascript(KickSignInPageScript) {
                    if (it == "true") { foundForm = true; webLoading = false }
                }
            }
        }
        if (!foundForm && currentAccount.account == null && webError == null) {
            webLoading = false
            webError = "Kick’s sign-in form did not load. Try Reload sign-in. If it still fails, update Android System WebView in the Play Store."
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = AppBackground) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text("kick.com", fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                    TextButton(onClick = { check(true) }, enabled = !account.checking) { Text("Continue") }
                }
                Text("Sign in using Kick’s form below.",
                    color = SubtleColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (webLoading || account.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (account.checking) Text("Verifying your account…", color = SubtleColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                (webError ?: account.error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                TextButton(onClick = {
                    webError = null
                    webLoading = true
                    rendererGone = false
                    attempt++
                    webView?.let(loadSignInPage)
                }) { Text("Reload sign-in", fontSize = 12.sp) }
                if (rendererGone) Spacer(Modifier.weight(1f).fillMaxWidth())
                else AndroidView(factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(android.graphics.Color.rgb(11, 14, 16))
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = WebChromeClient()
                        // No JavaScript bridge and no native password/token entry.
                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                webLoading = false
                                webError = "The sign-in browser stopped. Tap Reload sign-in. If it happens again, update Android System WebView in the Play Store."
                                rendererGone = true
                                return true
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.isForMainFrame && !allowedLoginNavigation(request.url)
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { webLoading = true }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (view != null && allowedLoginNavigation(Uri.parse(url ?: ""))) {
                                    view.evaluateJavascript(KickSignInPageScript) { if (it == "true") webLoading = false }
                                }
                                check(false)
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError?) {
                                if (request.isForMainFrame) {
                                    webLoading = false
                                    webError = "Could not load Kick’s sign-in page. Check your connection and tap Reload sign-in."
                                }
                            }
                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame && response.statusCode >= 400) {
                                    webLoading = false
                                    webError = "Kick’s sign-in page is unavailable (HTTP ${response.statusCode}). Try another network or reload later."
                                }
                            }
                        }
                        webView = this
                        loadSignInPage(this)
                    }
                }, onRelease = { if (webView === it) webView = null; it.stopLoading(); it.destroy() }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

internal fun allowedLoginNavigation(uri: Uri): Boolean = uri.scheme == "https" &&
    (uri.host == "kick.com" || uri.host?.endsWith(".kick.com") == true)
