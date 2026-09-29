package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.superthom196.nexiom.AppModel
import io.github.superthom196.nexiom.HomeState
import io.github.superthom196.nexiom.api.Attention
import io.github.superthom196.nexiom.api.Dashboard
import io.github.superthom196.nexiom.api.Section
import io.github.superthom196.nexiom.api.Stats
import io.github.superthom196.nexiom.api.Status
import io.github.superthom196.nexiom.api.Tile
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.admin
import io.github.superthom196.nexiom.resources.attention_more
import io.github.superthom196.nexiom.resources.attention_title
import io.github.superthom196.nexiom.resources.logo
import io.github.superthom196.nexiom.resources.more_options
import io.github.superthom196.nexiom.resources.nexiom
import io.github.superthom196.nexiom.resources.percent
import io.github.superthom196.nexiom.resources.retry
import io.github.superthom196.nexiom.resources.ring_cpu
import io.github.superthom196.nexiom.resources.ring_disk
import io.github.superthom196.nexiom.resources.ring_ram
import io.github.superthom196.nexiom.resources.ring_storage
import io.github.superthom196.nexiom.resources.settings
import io.github.superthom196.nexiom.resources.setup_title
import io.github.superthom196.nexiom.resources.sign_out
import io.github.superthom196.nexiom.resources.status_blocked
import io.github.superthom196.nexiom.resources.status_services
import io.github.superthom196.nexiom.resources.unreachable_body
import io.github.superthom196.nexiom.resources.unreachable_title
import io.github.superthom196.nexiom.resources.wrong_box_body
import io.github.superthom196.nexiom.resources.wrong_box_title
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A ring turns red at 90% full, as on the web launcher. */
private const val FULL = 0.9f

/** The household's front door: what needs attention, the status strip, the tiles and the rings, live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(model: AppModel) {
    // The box's event stream is open only while the dashboard is on screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, model.attempt) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { model.follow() }
    }
    // Back from the app store, an app may have turned up.
    LifecycleResumeEffect(Unit) {
        model.checkInstalled()
        onPauseOrDispose {}
    }
    val settingsTitle = stringResource(Res.string.settings)
    val adminTitle = stringResource(Res.string.admin)
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(Res.drawable.logo), contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(Res.string.nexiom))
                    }
                },
                actions = {
                    // A long press is nexiom0's secret way into Admin; other boxes have no admin login.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                role = Role.Button,
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    model.openWeb(adminTitle, "/admin")
                                },
                                onClick = { model.openWeb(settingsTitle, "/settings") },
                            ),
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = settingsTitle)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.more_options))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (model.setupApps.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.setup_title)) },
                                    onClick = {
                                        menu = false
                                        model.openSetup()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.sign_out)) },
                                onClick = {
                                    menu = false
                                    model.signOut()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val home = model.home) {
            HomeState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            HomeState.Unreachable -> MessageScreen(
                title = stringResource(Res.string.unreachable_title),
                body = stringResource(Res.string.unreachable_body),
                action = stringResource(Res.string.retry),
                onAction = model::retry,
                modifier = modifier,
            )
            HomeState.WrongBox -> MessageScreen(
                title = stringResource(Res.string.wrong_box_title),
                body = stringResource(Res.string.wrong_box_body),
                action = stringResource(Res.string.retry),
                onAction = model::retry,
                modifier = modifier,
                secondAction = stringResource(Res.string.sign_out),
                onSecondAction = model::signOut,
            )
            is HomeState.Ready -> Dashboard(home.dashboard, model, modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(dashboard: Dashboard, model: AppModel, modifier: Modifier) {
    var showAttention by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (dashboard.attention.isNotEmpty()) {
            item(key = "attention") { AttentionBanner(dashboard.attention, onClick = { showAttention = true }) }
        }
        val setupDone = model.setupApps.count { it.id in model.installed }
        if (setupDone < model.setupApps.size) {
            item(key = "setup") { SetupCard(setupDone, model.setupApps.size, onClick = model::openSetup) }
        }
        item(key = "scenes") { ScenesBlock(model) }
        dashboard.sections.forEach { section ->
            item(key = "section:${section.key}") { SectionHeader(section) }
            items(section.tiles, key = { "tile:${section.key}:${it.id}" }) { tile ->
                TileRow(tile, onClick = { model.openTile(tile) })
            }
        }
        // The box's own health at the bottom, as on the web launcher: the rings, then the strip.
        item(key = "rings") { Rings(dashboard.stats, Modifier.padding(top = 12.dp)) }
        item(key = "status") { StatusStrip(dashboard.status) }
    }

    if (showAttention) {
        ModalBottomSheet(onDismissRequest = { showAttention = false }) {
            Text(
                stringResource(Res.string.attention_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            dashboard.attention.forEach { item ->
                val link = item.link
                ListItem(
                    headlineContent = { Text(item.text) },
                    trailingContent = link?.let {
                        { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = if (link == null) Modifier else Modifier.clickable {
                        showAttention = false
                        model.openWeb(item.text, link)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AttentionBanner(items: List<Attention>, onClick: () -> Unit) {
    val danger = LocalNexiomColors.current.danger
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = danger.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, danger.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = danger)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.attention_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (items.size == 1) items[0].text
                    else stringResource(Res.string.attention_more, items[0].text, items.size - 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusStrip(status: Status) {
    val colors = LocalNexiomColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        status.services?.let { s ->
            val healthy = s.running == s.total
            StatusPill(
                icon = if (healthy) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                tint = if (healthy) colors.ok else colors.danger,
                text = stringResource(Res.string.status_services, s.running, s.total),
            )
        }
        status.blocked?.let { n ->
            StatusPill(Icons.Rounded.Shield, colors.muted, stringResource(Res.string.status_blocked, grouped(n)))
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, tint: Color, text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionHeader(section: Section) {
    Row(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(pillarIcon(section.key), contentDescription = null, tint = LocalNexiomColors.current.pillar(section.key))
        Spacer(Modifier.width(10.dp))
        Text(section.title, style = MaterialTheme.typography.titleLarge)
    }
}

/** A tile without a link (waiting for a drive, not set up yet) is dimmed and says why. */
@Composable
private fun TileRow(tile: Tile, onClick: () -> Unit) {
    val colour = LocalNexiomColors.current.pillar(tile.pillar)
    val open = tile.link != null
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, LocalNexiomColors.current.border),
        modifier = Modifier.fillMaxWidth().alpha(if (open) 1f else 0.6f).clickable(enabled = open, onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = colour.copy(alpha = 0.16f), modifier = Modifier.fillMaxSize()) {}
                Icon(pillarIcon(tile.pillar), contentDescription = null, tint = colour)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(tile.name, style = MaterialTheme.typography.titleMedium)
                if (tile.description.isNotBlank()) {
                    Text(
                        tile.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** CPU, RAM, Disk, and Storage while a data drive is mounted, as at the bottom of the web launcher. */
@Composable
private fun Rings(stats: Stats, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val cell = Modifier.weight(1f)
        stats.cpu?.let { RingCard(stringResource(Res.string.ring_cpu), (it / 100).toFloat(), cell) }
        stats.ram?.let { RingCard(stringResource(Res.string.ring_ram), it.fraction, cell) }
        stats.disk?.let { RingCard(stringResource(Res.string.ring_disk), it.fraction, cell) }
        stats.storage?.let { RingCard(stringResource(Res.string.ring_storage), it.fraction, cell) }
    }
}

@Composable
private fun RingCard(label: String, fraction: Float, modifier: Modifier) {
    val colors = LocalNexiomColors.current
    val fill = if (fraction >= FULL) colors.danger else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier,
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // As wide as the card allows, up to 72dp: four across a phone make them a little smaller.
            Box(Modifier.widthIn(max = 72.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(colors.border, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(
                        fill,
                        -90f,
                        360f * fraction.coerceIn(0f, 1f),
                        false,
                        Offset(inset, inset),
                        arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    stringResource(Res.string.percent, (fraction * 100).roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
