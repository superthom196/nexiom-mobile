package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Nexiom logo (the server's static/logo.svg): the four pillars as one window. */
@Composable
fun Logo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 48f
        val quarter = Size(36 * u, 36 * u)
        fun quarter(color: Long, left: Float, top: Float, start: Float) =
            drawArc(Color(color), start, 90f, useCenter = true, topLeft = Offset(left * u, top * u), size = quarter)
        quarter(0xFFFF8A7A, 4f, 4f, 180f)
        quarter(0xFFB79CFF, 8f, 4f, 270f)
        quarter(0xFF6FE0A6, 8f, 8f, 0f)
        quarter(0xFFFFC266, 4f, 8f, 90f)
    }
}

/** A tile's pillar, or a section's key, to its icon. */
fun pillarIcon(key: String): ImageVector = when (key) {
    "music" -> Icons.Rounded.MusicNote
    "video" -> Icons.Rounded.Movie
    "gaming" -> Icons.Rounded.SportsEsports
    "entertainment" -> Icons.Rounded.Theaters
    "backups" -> Icons.Rounded.Backup
    "home" -> Icons.Rounded.Home
    else -> Icons.Rounded.Apps
}

/** A whole-screen message with up to two actions: can't reach, not set up, and the like. */
@Composable
fun MessageScreen(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondAction: String? = null,
    onSecondAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo()
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)) { Text(action) }
        if (secondAction != null) {
            TextButton(onClick = onSecondAction) { Text(secondAction) }
        }
    }
}

/** 12345 → "12,345". */
fun grouped(n: Long): String {
    val digits = kotlin.math.abs(n).toString()
    val groups = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$groups" else groups
}
