package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.superthom196.nexiom.AppModel
import io.github.superthom196.nexiom.Overlay
import io.github.superthom196.nexiom.Platform
import io.github.superthom196.nexiom.Screen
import io.github.superthom196.nexiom.WebPage
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.close
import org.jetbrains.compose.resources.stringResource

@Composable
fun App(platform: Platform) {
    val model = viewModel { AppModel(platform, platform.newHttpClient()) }
    NexiomTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val screen = model.screen) {
                Screen.Find -> FindScreen(model)
                is Screen.NotSetUp -> NotSetUpScreen(model)
                is Screen.SignIn -> SignInScreen(model, screen)
                Screen.Home -> when (val overlay = model.overlay) {
                    null -> HomeScreen(model)
                    is Overlay.Web -> WebScreen(overlay.page, onClose = model::closeOverlay)
                    Overlay.Setup -> SetupScreen(model)
                    Overlay.EditScenes -> EditScenesScreen(model)
                }
            }
        }
    }
}

/**
 * Settings, Files and the services' web pages, inside the app and already signed in.
 * A slim bar with just an X keeps the page's own corner buttons clear.
 */
@Composable
private fun WebScreen(page: WebPage, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Not an IconButton: that pads itself to 48dp tall. This is 24dp, and wide to be easy to hit.
        // The space below sets the X off from the page, since the status bar above looks part of the bar.
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).height(24.dp), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.fillMaxHeight().width(56.dp).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.close) + " " + page.title,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        WebView(page, onClose, Modifier.weight(1f).fillMaxWidth())
    }
}
