package io.github.superthom196.nexiom.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.superthom196.nexiom.WebPage

/** The system back gesture, while `enabled`. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * A web page inside the app. Back goes back a page, and past the first page calls `onClose`.
 * Pages on other sites open in the browser.
 */
@Composable
expect fun WebView(page: WebPage, onClose: () -> Unit, modifier: Modifier = Modifier)
