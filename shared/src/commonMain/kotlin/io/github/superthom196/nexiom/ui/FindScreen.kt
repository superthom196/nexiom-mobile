package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.superthom196.nexiom.AppModel
import io.github.superthom196.nexiom.FoundBox
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.find_household_unnamed
import io.github.superthom196.nexiom.resources.find_looking
import io.github.superthom196.nexiom.resources.find_none
import io.github.superthom196.nexiom.resources.find_not_set_up_badge
import io.github.superthom196.nexiom.resources.find_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val HELP_AFTER_MS = 8_000L

/** The boxes on the home network, by household name. Searching stops while the app is in the background. */
@Composable
fun FindScreen(model: AppModel) {
    val found = remember { model.boxesNearby() }
    val boxes by found.collectAsStateWithLifecycle(emptyList())
    var waitedLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(HELP_AFTER_MS)
        waitedLong = true
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Logo()
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.find_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        boxes.forEach { box ->
            BoxRow(box, onClick = { model.choose(box) })
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(Res.string.find_looking),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (boxes.isEmpty() && waitedLong) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.find_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BoxRow(box: FoundBox, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    box.household.ifBlank { stringResource(Res.string.find_household_unnamed) },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (!box.ready) {
                    Text(
                        stringResource(Res.string.find_not_set_up_badge),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}
