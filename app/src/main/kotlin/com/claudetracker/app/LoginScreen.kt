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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.claudetracker.app.data.model.Platform

private class OrgApiJsInterface(private val viewModel: LoginViewModel) {
    @JavascriptInterface
    fun onResult(status: Int, json: String) {
        Log.d("JSInterface", "onResult status=$status json=${json.take(100)}")
        // Route to correct handler based on current platform
        if (viewModel.targetPlatform == Platform.CODEX) {
            viewModel.onCodexSessionJson(status, json)
        } else {
            viewModel.onOrganizationsJson(status, json)
        }
    }

    @JavascriptInterface
    fun onAuthenticated(url: String) {
        Log.d("JSInterface", "onAuthenticated: $url")
        viewModel.onAuthenticatedUrlDetected(url)
    }
}

private val WATCHER_JS = """
    (function() {
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

        if (window._ctUrlWatcherActive) return;
        window._ctUrlWatcherActive = true;
        
        function checkUrl() {
            var currentUrl = window.location.href;
            var isAuth = currentUrl.indexOf('claude.ai') !== -1
                && currentUrl.indexOf('/login') === -1
                && currentUrl.indexOf('/oauth') === -1
                && currentUrl.indexOf('/signup') === -1
                && currentUrl.indexOf('/verify') === -1
                && currentUrl.indexOf('/consent') === -1;
            
            if (isAuth && window.AndroidOrg) {
                window._ctUrlWatcherActive = false;
                window.AndroidOrg.onAuthenticated(currentUrl);
                return;
            }
            setTimeout(checkUrl, 500);
        }
        checkUrl();
    })();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    platform: Platform = Platform.CLAUDE,
    clearAllCookies: Boolean = false,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(platform) {
        viewModel.targetPlatform = platform
        viewModel.clearWebViewCookies(clearAll = clearAllCookies)
        if (platform == Platform.CLAUDE && !clearAllCookies) {
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
            when {
                // ── WebView login for Claude and Codex ──
                uiState is LoginState.WaitingForLogin && (platform == Platform.CLAUDE || platform == Platform.CODEX) -> {
                    val loginUrl = when (platform) {
                        Platform.CLAUDE -> "https://claude.ai/login"
                        Platform.CODEX -> "https://chatgpt.com/auth/login"
                        else -> ""
                    }

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
                                // Add JS interface for both platforms
                                addJavascriptInterface(OrgApiJsInterface(viewModel), "AndroidOrg")
                            }

                            mainWebView.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    viewModel.onUrlChanged(request?.url?.toString())
                                    return false
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (platform == Platform.CLAUDE) {
                                        view?.evaluateJavascript(WATCHER_JS, null)
                                    }
                                    viewModel.onPageFinished(url, view)
                                }
                            }

                            mainWebView.webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                                    val popup = WebView(context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        // Add the same JS interface to popup so captures work from here too
                                        addJavascriptInterface(OrgApiJsInterface(viewModel), "AndroidOrg")
                                    }
                                    popup.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val url = request?.url?.toString() ?: ""
                                            val targetDomain = if (platform == Platform.CLAUDE) "claude.ai" else "chatgpt.com"
                                            if (url.contains(targetDomain)) {
                                                // Instead of loading the URL fresh in mainWebView (which loses auth context),
                                                // let the popup handle it naturally — it has the cookies from the OAuth flow
                                                return false
                                            }
                                            return false
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            if (url == null) return
                                            val targetDomain = if (platform == Platform.CLAUDE) "claude.ai" else "chatgpt.com"
                                            if (url.contains(targetDomain) &&
                                                !url.contains("/login") &&
                                                !url.contains("/oauth") &&
                                                !url.contains("/signup") &&
                                                !url.contains("/consent") &&
                                                !url.contains("/verify") &&
                                                !url.contains("/auth")) {
                                                // OAuth completed in popup — close popup and load in main WebView
                                                // The cookies are now set from the popup's natural OAuth flow
                                                Log.d("LoginScreen", "OAuth done in popup, redirecting main WebView to: $url")
                                                CookieManager.getInstance().flush()
                                                mainWebView.loadUrl(url)
                                                (popup.parent as? ViewGroup)?.removeView(popup)
                                            }
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
                            mainWebView.loadUrl(loginUrl)
                            container
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

            }

            // Full-screen spinner while capturing/saving
            AnimatedVisibility(
                visible = uiState is LoginState.CapturingCredentials || uiState is LoginState.Loading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
            AnimatedVisibility(
                visible = uiState is LoginState.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
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
