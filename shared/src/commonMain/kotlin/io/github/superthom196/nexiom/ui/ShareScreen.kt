package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.superthom196.nexiom.Destination
import io.github.superthom196.nexiom.ShareModel
import io.github.superthom196.nexiom.ShareState
import io.github.superthom196.nexiom.api.FolderView
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.close
import io.github.superthom196.nexiom.resources.retry
import io.github.superthom196.nexiom.resources.share_count
import io.github.superthom196.nexiom.resources.share_drives
import io.github.superthom196.nexiom.resources.share_empty_folder
import io.github.superthom196.nexiom.resources.share_no_drives
import io.github.superthom196.nexiom.resources.share_nothing
import io.github.superthom196.nexiom.resources.share_open_app
import io.github.superthom196.nexiom.resources.share_send_here
import io.github.superthom196.nexiom.resources.share_sending_toast
import io.github.superthom196.nexiom.resources.share_signed_out
import io.github.superthom196.nexiom.resources.share_title
import io.github.superthom196.nexiom.resources.unreachable_body
import io.github.superthom196.nexiom.resources.unreachable_title
import io.github.superthom196.nexiom.resources.up
import org.jetbrains.compose.resources.stringResource

/**
 * Nexiom in the share sheet: pick a folder on a drive and send the files there. `onSend` gets
 * the folder and the line to tell the person; the upload carries on without this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    model: ShareModel,
    fileNames: List<String>,
    onSend: (Destination, String) -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
) {
    PlatformBackHandler { if (!model.up()) onClose() }
    val state = model.state

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.close))
                    }
                },
                title = {
                    Column {
                        Text(stringResource(Res.string.share_title))
                        Text(
                            if (fileNames.size == 1) fileNames[0] else stringResource(Res.string.share_count, fileNames.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            val view = (state as? ShareState.Folder)?.view
            if (view != null && fileNames.isNotEmpty()) SendBar(view, model, onSend)
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            fileNames.isEmpty() -> MessageScreen(
                title = stringResource(Res.string.share_title),
                body = stringResource(Res.string.share_nothing),
                action = stringResource(Res.string.close),
                onAction = onClose,
                modifier = modifier,
            )
            state == ShareState.SignedOut -> MessageScreen(
                title = stringResource(Res.string.share_title),
                body = stringResource(Res.string.share_signed_out),
                action = stringResource(Res.string.share_open_app),
                onAction = onOpenApp,
                modifier = modifier,
            )
            state == ShareState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state == ShareState.Unreachable -> MessageScreen(
                title = stringResource(Res.string.unreachable_title),
                body = stringResource(Res.string.unreachable_body),
                action = stringResource(Res.string.retry),
                onAction = model::start,
                modifier = modifier,
            )
            state is ShareState.Refused -> MessageScreen(
                title = stringResource(Res.string.share_title),
                body = state.message,
                action = stringResource(Res.string.share_drives),
                onAction = model::showDrives,
                modifier = modifier,
            )
            state is ShareState.Drives -> LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                item { Heading(stringResource(Res.string.share_drives)) }
                if (state.drives.isEmpty()) {
                    item { Note(stringResource(Res.string.share_no_drives)) }
                }
                items(state.drives, key = { it.device }) { drive ->
                    FolderRow(Icons.Rounded.Storage, drive.name) { model.open(drive.device, "") }
                }
            }
            state is ShareState.Folder -> LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                val view = state.view
                item { Heading(listOf(view.drive, view.path).filter { it.isNotEmpty() }.joinToString(" / ")) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.up)) },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { model.up() },
                    )
                    HorizontalDivider()
                }
                if (view.folders.isEmpty()) {
                    item { Note(stringResource(Res.string.share_empty_folder)) }
                }
                items(view.folders, key = { it.path }) { folder ->
                    FolderRow(Icons.Rounded.Folder, folder.name) { model.open(view.device, folder.path) }
                }
            }
        }
    }
}

@Composable
private fun SendBar(view: FolderView, model: ShareModel, onSend: (Destination, String) -> Unit) {
    val sending = stringResource(Res.string.share_sending_toast, Destination.of(view).label)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            // Why files can't go here, such as a folder a service owns.
            view.frozen?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            }
            Button(
                onClick = { model.choose()?.let { onSend(it, sending) } },
                enabled = view.frozen == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(Res.string.share_send_here))
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
}

@Composable
private fun FolderRow(icon: ImageVector, name: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
