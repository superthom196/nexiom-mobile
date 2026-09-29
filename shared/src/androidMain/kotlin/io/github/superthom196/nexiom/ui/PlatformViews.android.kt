package io.github.superthom196.nexiom.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView as AndroidWebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.superthom196.nexiom.WebPage
import io.github.superthom196.nexiom.api.NexiomApi
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.retry
import io.github.superthom196.nexiom.resources.unreachable_body
import io.github.superthom196.nexiom.resources.web_failed
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

/** A video the page has made fullscreen: the WebView's own view for it, and how to tell the page it's over. */
private class Fullscreen(val view: View, val callback: WebChromeClient.CustomViewCallback)

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebView(page: WebPage, onClose: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var failed by remember(page) { mutableStateOf(false) }
    var fullscreen by remember(page) { mutableStateOf<Fullscreen?>(null) }

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

                // A video's fullscreen button, or the page's requestFullscreen().
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreen != null || activity == null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    fullscreen = Fullscreen(view, callback)
                }

                override fun onHideCustomView() {
                    fullscreen = null
                }
            }
            loadUrl(page.url, page.headers)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            // Closed while a video is fullscreen: tell the page, so it doesn't wait for a view that's gone.
            fullscreen?.callback?.onCustomViewHidden()
            fullscreen = null
            CookieManager.getInstance().flush()
            webView.destroy()
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onClose()
    }
    // After the one above, so it's asked first: Back leaves fullscreen and nothing else.
    BackHandler(enabled = fullscreen != null) {
        fullscreen?.callback?.onCustomViewHidden()
        fullscreen = null
    }

    // The video goes over the whole window, above the X bar, with the system bars hidden
    // and the space around the camera used. All of it comes back when it ends, however it ends.
    val shown = fullscreen
    if (shown != null && activity != null) {
        DisposableEffect(shown) {
            val window = activity.window
            val decor = window.decorView as ViewGroup
            val frame = FrameLayout(activity).apply {
                setBackgroundColor(Color.BLACK)
                addView(shown.view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            decor.addView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val bars = WindowInsetsControllerCompat(window, decor)
            val barsBehavior = bars.systemBarsBehavior
            val cutoutMode = window.attributes.layoutInDisplayCutoutMode
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            onDispose {
                frame.removeView(shown.view)
                decor.removeView(frame)
                bars.show(WindowInsetsCompat.Type.systemBars())
                bars.systemBarsBehavior = barsBehavior
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = cutoutMode }
            }
        }
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
