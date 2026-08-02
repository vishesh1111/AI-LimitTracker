package com.claudetracker.app

import android.annotation.SuppressLint
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private class OrgApiJsInterface(private val viewModel: LoginViewModel) {
    @JavascriptInterface
    fun onResult(status: Int, json: String) {
        Log.d("JSInterface", "onResult status=$status json=${json.take(100)}")
        viewModel.onOrganizationsJson(status, json)
    }

    /** Called by the URL watcher when it detects navigation away from login pages */
    @JavascriptInterface
    fun onAuthenticated(url: String) {
        Log.d("JSInterface", "onAuthenticated: $url")
        viewModel.onAuthenticatedUrlDetected(url)
    }
}

/**
 * This JS is injected on EVERY claude.ai page load. It:
 * 1. Dismisses cookie consent banners
 * 2. Polls window.location.href every 500ms to detect SPA (pushState) navigation
 * 3. When a non-login URL is detected, calls AndroidOrg.onAuthenticated(url)
 *    which triggers the automatic org capture
 */
private val WATCHER_JS = """
    (function() {
        // --- Cookie banner dismissal ---
        function nuke() {
            document.body && (document.body.style.overflow='auto');
            ['[class*="cookie"],[class*="consent"],[id*="cookie"],[id*="consent"]',
             '.osano-cm-window','#onetrust-banner-sdk','.cc-window'].forEach(function(s){
                document.querySelectorAll(s).forEach(function(el){
                    var cs=window.getComputedStyle(el);
                    if(cs.position==='fixed'||cs.position==='sticky') el.remove();
                });
            });
        }
        nuke(); setTimeout(nuke,800); setTimeout(nuke,2500);

        // --- URL watcher for SPA navigation ---
        if (window._ctUrlWatcherActive) return; // Don't install twice
        window._ctUrlWatcherActive = true;
        
        var lastUrl = window.location.href;
        console.log('[ClaudeTracker] URL watcher installed. Current URL: ' + lastUrl);
        
        function checkUrl() {
            var currentUrl = window.location.href;
            
            // Check if we're on an authenticated page (not login/oauth/signup)
            var isAuth = currentUrl.indexOf('claude.ai') !== -1
                && currentUrl.indexOf('/login') === -1
                && currentUrl.indexOf('/oauth') === -1
                && currentUrl.indexOf('/signup') === -1
                && currentUrl.indexOf('/verify') === -1
                && currentUrl.indexOf('/consent') === -1;
            
            if (isAuth && window.AndroidOrg) {
                console.log('[ClaudeTracker] Authenticated URL detected: ' + currentUrl);
                window._ctUrlWatcherActive = false; // Stop watching
                window.AndroidOrg.onAuthenticated(currentUrl);
                return; // Stop polling
            }
            
            // Keep polling
            setTimeout(checkUrl, 500);
        }
        
        // Start polling immediately
        checkUrl();
    })();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    clearAllCookies: Boolean = false,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.clearWebViewCookies(clearAll = clearAllCookies)
        if (!clearAllCookies) {
            val cm = CookieManager.getInstance()
            cm.setCookie("https://claude.ai", "OptanonAlertBoxClosed=2024-01-01; Path=/; Domain=.claude.ai")
            cm.setCookie("https://claude.ai", "OptanonConsent=groups=C0001:1,C0002:1,C0003:1,C0004:1; Path=/; Domain=.claude.ai")
            cm.setCookie("https://claude.ai", "cookieConsent=accepted; Path=/; Domain=.claude.ai")
            cm.flush()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginState.Success) onLoginSuccess()
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show WebView while waiting for login
            if (uiState is LoginState.WaitingForLogin) {
                AndroidView(
                    factory = { context ->
                        val container = android.widget.FrameLayout(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        val mainWebView = WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(true)
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                            addJavascriptInterface(OrgApiJsInterface(viewModel), "AndroidOrg")
                        }

                        mainWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                viewModel.onUrlChanged(request?.url?.toString())
                                return false
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d("LoginScreen", "Page finished: $url")
                                // Inject the URL watcher + cookie dismissal on EVERY page load
                                // The watcher will poll for SPA navigation and auto-trigger capture
                                view?.evaluateJavascript(WATCHER_JS, null)
                                // Also notify ViewModel in case this page load IS the authenticated page
                                viewModel.onPageFinished(url, view)
                            }
                        }

                        mainWebView.webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                                Log.d("LoginScreen", "onCreateWindow")
                                val popup = WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                }
                                popup.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: ""
                                        if (url.contains("claude.ai")) {
                                            mainWebView.loadUrl(url)
                                            (popup.parent as? ViewGroup)?.removeView(popup)
                                            return true
                                        }
                                        return false
                                    }
                                }
                                popup.webChromeClient = object : WebChromeClient() {
                                    override fun onCloseWindow(window: WebView?) {
                                        (popup.parent as? ViewGroup)?.removeView(popup)
                                    }
                                }
                                container.addView(popup)
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = popup
                                resultMsg?.sendToTarget()
                                return true
                            }
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(mainWebView, true)
                        container.addView(mainWebView)
                        mainWebView.loadUrl("https://claude.ai/login")
                        container
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Full-screen spinner while capturing/saving account
            if (uiState is LoginState.CapturingCredentials || uiState is LoginState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121218)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF90CAF9))
                        Text(
                            "Saving your account...",
                            modifier = Modifier.padding(top = 16.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Error screen
            if (uiState is LoginState.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("Login Failed", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            (uiState as LoginState.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                        )
                        Button(onClick = { viewModel.resetState() }) { Text("Try Again") }
                    }
                }
            }
        }
    }
}
