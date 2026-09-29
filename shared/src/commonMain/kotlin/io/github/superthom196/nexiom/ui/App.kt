package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * The page gets the whole screen; a small see-through X in the corner closes it.
 */
@Composable
private fun WebScreen(page: WebPage, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        WebView(page, onClose, Modifier.fillMaxSize())
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.35f),
                contentColor = Color.White,
            ),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.close) + " " + page.title,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
