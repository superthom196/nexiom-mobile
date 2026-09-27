package io.github.superthom196.nexiom.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView as AndroidWebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.superthom196.nexiom.WebPage
import io.github.superthom196.nexiom.api.NexiomApi
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.retry
import io.github.superthom196.nexiom.resources.unreachable_body
import io.github.superthom196.nexiom.resources.web_failed
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebView(page: WebPage, onClose: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var failed by remember(page) { mutableStateOf(false) }

    // A file input on the page (the Files page's upload) asks the phone's own picker.
    var pendingFiles by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingFiles?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        pendingFiles = null
    }

    val startHost = remember(page) { Uri.parse(page.url).host.orEmpty().lowercase() }
    val webView = remember(page) {
        AndroidWebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: AndroidWebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    val host = url.host.orEmpty().lowercase()
                    val inApp = url.scheme in setOf("http", "https") &&
                        (host == startHost || NexiomApi.isNexiomAddress(url.toString()))
                    if (inApp) return false
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: ActivityNotFoundException) {
                        // Nothing on the phone opens it.
                    }
                    return true
                }

                override fun onReceivedError(view: AndroidWebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failed = true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    view: AndroidWebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams,
                ): Boolean {
                    pendingFiles?.onReceiveValue(null)
                    pendingFiles = callback
                    return try {
                        pickFiles.launch(params.createIntent())
                        true
                    } catch (_: ActivityNotFoundException) {
                        pendingFiles = null
                        false
                    }
                }
            }
            loadUrl(page.url, page.headers)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            CookieManager.getInstance().flush()
            webView.destroy()
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onClose()
    }

    Box(modifier) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        if (failed) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MessageScreen(
                    title = stringResource(Res.string.web_failed),
                    body = stringResource(Res.string.unreachable_body),
                    action = stringResource(Res.string.retry),
                    onAction = {
                        failed = false
                        webView.loadUrl(page.url, page.headers)
                    },
                )
            }
        }
    }
}
