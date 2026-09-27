package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.superthom196.nexiom.AppModel
import io.github.superthom196.nexiom.api.SetupApp
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.close
import io.github.superthom196.nexiom.resources.settings
import io.github.superthom196.nexiom.resources.setup_body
import io.github.superthom196.nexiom.resources.setup_copied
import io.github.superthom196.nexiom.resources.setup_copy
import io.github.superthom196.nexiom.resources.setup_for_bitwarden
import io.github.superthom196.nexiom.resources.setup_for_homeassistant
import io.github.superthom196.nexiom.resources.setup_for_immich
import io.github.superthom196.nexiom.resources.setup_for_music_assistant
import io.github.superthom196.nexiom.resources.setup_for_tailscale
import io.github.superthom196.nexiom.resources.setup_install
import io.github.superthom196.nexiom.resources.setup_installed
import io.github.superthom196.nexiom.resources.setup_login
import io.github.superthom196.nexiom.resources.setup_network
import io.github.superthom196.nexiom.resources.setup_open
import io.github.superthom196.nexiom.resources.setup_passwords_off
import io.github.superthom196.nexiom.resources.setup_progress
import io.github.superthom196.nexiom.resources.setup_server
import io.github.superthom196.nexiom.resources.setup_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val COPIED_FOR_MS = 2_000L

/** "Set up this phone" and how far along it is, on the dashboard until every app is installed. */
@Composable
fun SetupCard(done: Int, total: Int, onClick: () -> Unit) {
    val colour = LocalNexiomColors.current.backups
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colour.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colour.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = colour)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.setup_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.setup_progress, done, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    color = colour,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** The official apps this box wants on the phone, each ticked once it's installed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(model: AppModel) {
    PlatformBackHandler(onBack = model::closeOverlay)
    // Coming back from the app store is when an app turns up.
    LifecycleResumeEffect(Unit) {
        model.checkInstalled()
        onPauseOrDispose {}
    }
    val apps = model.setupApps
    val settingsTitle = stringResource(Res.string.settings)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = model::closeOverlay) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.close))
                    }
                },
                title = { Text(stringResource(Res.string.setup_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.setup_progress, apps.count { it.id in model.installed }, apps.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(Res.string.setup_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(apps, key = { it.id }) { app ->
                SetupStep(
                    app = app,
                    installed = app.id in model.installed,
                    onInstall = { model.installApp(app.id) },
                    onOpen = { model.openApp(app.id) },
                    onCopy = model::copy,
                    onOpenSettings = { model.openWeb(settingsTitle, "/settings") },
                )
            }
        }
    }
}

@Composable
private fun SetupStep(
    app: SetupApp,
    installed: Boolean,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colours = LocalNexiomColors.current
    val (icon, tint, purpose) = stepLook(app.id)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, colours.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.16f), modifier = Modifier.fillMaxSize()) {}
                    Icon(icon, contentDescription = null, tint = tint)
                }
                Spacer(Modifier.width(12.dp))
                Text(app.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (installed) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colours.ok, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.setup_installed), color = colours.ok, style = MaterialTheme.typography.labelLarge)
                }
            }
            purpose?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            app.tailnet?.let { Value(stringResource(Res.string.setup_network), it, onCopy) }
            app.server?.let { Value(stringResource(Res.string.setup_server), it, onCopy) }
            app.login?.let { Value(stringResource(Res.string.setup_login), it, onCopy) }
            // Bitwarden's address exists only once Passwords is turned on.
            if (app.id == "bitwarden" && app.server == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.setup_passwords_off), modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenSettings) { Text(stringResource(Res.string.settings)) }
                }
            }
            if (installed) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.setup_open)) }
            } else {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.setup_install)) }
            }
        }
    }
}

/** A value the app takes, with Copy. */
@Composable
private fun Value(label: String, value: String, onCopy: (String) -> Unit) {
    var copied by remember(value) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FOR_MS)
            copied = false
        }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = {
                onCopy(value)
                copied = true
            }) {
                Text(stringResource(if (copied) Res.string.setup_copied else Res.string.setup_copy))
            }
        }
    }
}

@Composable
private fun stepLook(id: String): Triple<ImageVector, Color, StringResource?> {
    val c = LocalNexiomColors.current
    return when (id) {
        "tailscale" -> Triple(Icons.Rounded.VpnLock, c.more, Res.string.setup_for_tailscale)
        "bitwarden" -> Triple(Icons.Rounded.Key, c.video, Res.string.setup_for_bitwarden)
        "homeassistant" -> Triple(Icons.Rounded.Home, c.home, Res.string.setup_for_homeassistant)
        "music-assistant" -> Triple(Icons.Rounded.MusicNote, c.music, Res.string.setup_for_music_assistant)
        "immich" -> Triple(Icons.Rounded.PhotoLibrary, c.backups, Res.string.setup_for_immich)
        else -> Triple(Icons.Rounded.PhoneAndroid, c.more, null)
    }
}
