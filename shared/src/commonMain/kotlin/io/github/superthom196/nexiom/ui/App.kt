package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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

/** Settings, Files and the services' web pages, inside the app and already signed in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebScreen(page: WebPage, onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.close))
                    }
                },
                title = { Text(page.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        WebView(page, onClose, Modifier.padding(padding).fillMaxSize())
    }
}
