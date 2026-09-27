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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.superthom196.nexiom.AppModel
import io.github.superthom196.nexiom.SceneRun
import io.github.superthom196.nexiom.ScenesState
import io.github.superthom196.nexiom.api.Scene
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.close
import io.github.superthom196.nexiom.resources.edit_scenes_chosen
import io.github.superthom196.nexiom.resources.edit_scenes_others
import io.github.superthom196.nexiom.resources.edit_scenes_title
import io.github.superthom196.nexiom.resources.reorder
import io.github.superthom196.nexiom.resources.retry
import io.github.superthom196.nexiom.resources.scene_failed
import io.github.superthom196.nexiom.resources.scenes_choose_body
import io.github.superthom196.nexiom.resources.scenes_choose_title
import io.github.superthom196.nexiom.resources.scenes_edit
import io.github.superthom196.nexiom.resources.scenes_failed
import io.github.superthom196.nexiom.resources.scenes_none
import io.github.superthom196.nexiom.resources.scenes_title
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** The phone's chosen scenes at the top of the dashboard, as a grid of big buttons. */
@Composable
fun ScenesBlock(model: AppModel) {
    val state = model.scenes
    if (state == ScenesState.Off) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(pillarIcon("home"), contentDescription = null, tint = LocalNexiomColors.current.home)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(Res.string.scenes_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state is ScenesState.Ready && model.favourites.isNotEmpty()) {
                TextButton(onClick = model::editScenes) { Text(stringResource(Res.string.scenes_edit)) }
            }
        }
        when (state) {
            ScenesState.Off -> {}
            ScenesState.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            ScenesState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.scenes_failed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = model::retryScenes) { Text(stringResource(Res.string.retry)) }
            }
            is ScenesState.Ready -> {
                val byId = state.all.associateBy { it.id }
                val chosen = model.favourites.mapNotNull(byId::get)
                if (chosen.isEmpty()) {
                    ChooseScenesCard(onClick = model::editScenes)
                } else {
                    chosen.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { scene ->
                                SceneButton(scene, model.sceneRuns[scene.id], { model.runScene(scene) }, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseScenesCard(onClick: () -> Unit) {
    val colour = LocalNexiomColors.current.home
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colour.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colour.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.scenes_choose_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.scenes_choose_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SceneButton(scene: Scene, run: SceneRun?, onClick: () -> Unit, modifier: Modifier) {
    val colours = LocalNexiomColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colours.home.copy(alpha = 0.16f),
        modifier = modifier.height(96.dp).clickable(enabled = run != SceneRun.Running, onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                when (run) {
                    SceneRun.Running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colours.home)
                    SceneRun.Done -> Icon(Icons.Rounded.Check, contentDescription = null, tint = colours.ok)
                    SceneRun.Failed -> Icon(Icons.Rounded.Error, contentDescription = null, tint = colours.danger)
                    null -> Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colours.home)
                }
            }
            Text(
                if (run == SceneRun.Failed) stringResource(Res.string.scene_failed) else scene.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Every Home Assistant scene with a tick; the chosen ones first, dragged into order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScenesScreen(model: AppModel) {
    PlatformBackHandler(onBack = model::closeOverlay)
    val all = (model.scenes as? ScenesState.Ready)?.all.orEmpty()
    val byId = all.associateBy { it.id }
    val chosen = model.favourites.mapNotNull(byId::get)
    val others = all.filter { it.id !in model.favourites }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        model.moveFavourite(model.favourites.indexOf(from.key), model.favourites.indexOf(to.key))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = model::closeOverlay) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.close))
                    }
                },
                title = { Text(stringResource(Res.string.edit_scenes_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (all.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(Res.string.scenes_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            if (chosen.isNotEmpty()) {
                item(key = "chosen") { ListHeading(stringResource(Res.string.edit_scenes_chosen)) }
                items(chosen, key = { it.id }) { scene ->
                    ReorderableItem(reorder, key = scene.id) { dragging ->
                        Surface(color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background) {
                            SceneChoice(scene, chosen = true, onChange = { model.setFavourite(scene.id, it) }) {
                                IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                    Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(Res.string.reorder))
                                }
                            }
                        }
                    }
                }
            }
            if (others.isNotEmpty()) {
                item(key = "others") { ListHeading(stringResource(Res.string.edit_scenes_others)) }
                items(others, key = { it.id }) { scene ->
                    SceneChoice(scene, chosen = false, onChange = { model.setFavourite(scene.id, it) })
                }
            }
        }
    }
}

@Composable
private fun ListHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SceneChoice(
    scene: Scene,
    chosen: Boolean,
    onChange: (Boolean) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!chosen) }.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = chosen, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Text(scene.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}
