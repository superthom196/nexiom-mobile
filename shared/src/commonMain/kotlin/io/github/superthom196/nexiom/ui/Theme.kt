package io.github.superthom196.nexiom.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.atkinson_bold
import io.github.superthom196.nexiom.resources.atkinson_regular
import org.jetbrains.compose.resources.Font

// "Living Room", as on the web launcher (the server's static/style.css): evening blue, one
// colour per pillar, Atkinson Hyperlegible.

@Immutable
data class NexiomColors(
    val ok: Color,
    val danger: Color,
    val muted: Color,
    val border: Color,
    val music: Color,
    val video: Color,
    val gaming: Color,
    val backups: Color,
    val home: Color,
    val more: Color,
) {
    /** A tile's pillar, or a section's key, to its colour. */
    fun pillar(key: String): Color = when (key) {
        "music" -> music
        "video", "entertainment" -> video
        "gaming" -> gaming
        "backups" -> backups
        "home" -> home
        else -> more
    }
}

private val DarkColors = NexiomColors(
    ok = Color(0xFF5FD49A),
    danger = Color(0xFFFF7B6E),
    muted = Color(0xFFA5A8BA),
    border = Color(0xFF2D3350),
    music = Color(0xFFFF8A7A),
    video = Color(0xFFB79CFF),
    gaming = Color(0xFF6FE0A6),
    backups = Color(0xFF6ECBFF),
    home = Color(0xFFFFC266),
    more = Color(0xFFA5A8BA),
)

private val LightColors = NexiomColors(
    ok = Color(0xFF1E8E5A),
    danger = Color(0xFFC0392B),
    muted = Color(0xFF5D6178),
    border = Color(0xFFDDE0EA),
    music = Color(0xFFD9534A),
    video = Color(0xFF7C5CE0),
    gaming = Color(0xFF1F9D63),
    backups = Color(0xFF1A7FB8),
    home = Color(0xFFC98A12),
    more = Color(0xFF5D6178),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF2EEE7),
    onPrimary = Color(0xFF141726),
    background = Color(0xFF141726),
    onBackground = Color(0xFFF2EEE7),
    surface = Color(0xFF141726),
    onSurface = Color(0xFFF2EEE7),
    surfaceVariant = Color(0xFF262B43),
    onSurfaceVariant = Color(0xFFA5A8BA),
    surfaceContainerLowest = Color(0xFF141726),
    surfaceContainerLow = Color(0xFF1C2034),
    surfaceContainer = Color(0xFF1C2034),
    surfaceContainerHigh = Color(0xFF262B43),
    surfaceContainerHighest = Color(0xFF262B43),
    outline = Color(0xFF2D3350),
    outlineVariant = Color(0xFF2D3350),
    error = Color(0xFFFF7B6E),
    onError = Color(0xFF141726),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF141726),
    onPrimary = Color(0xFFF2EEE7),
    background = Color(0xFFF3F4F8),
    onBackground = Color(0xFF141726),
    surface = Color(0xFFF3F4F8),
    onSurface = Color(0xFF141726),
    surfaceVariant = Color(0xFFECEEF5),
    onSurfaceVariant = Color(0xFF5D6178),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFECEEF5),
    surfaceContainerHighest = Color(0xFFECEEF5),
    outline = Color(0xFFDDE0EA),
    outlineVariant = Color(0xFFDDE0EA),
    error = Color(0xFFC0392B),
    onError = Color(0xFFFFFFFF),
)

val LocalNexiomColors = staticCompositionLocalOf { DarkColors }

@Composable
fun NexiomTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val atkinson = FontFamily(
        Font(Res.font.atkinson_regular, FontWeight.Normal),
        Font(Res.font.atkinson_bold, FontWeight.Bold),
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.with(atkinson),
        displayMedium = base.displayMedium.with(atkinson),
        displaySmall = base.displaySmall.with(atkinson),
        headlineLarge = base.headlineLarge.with(atkinson, FontWeight.Bold),
        headlineMedium = base.headlineMedium.with(atkinson, FontWeight.Bold),
        headlineSmall = base.headlineSmall.with(atkinson, FontWeight.Bold),
        titleLarge = base.titleLarge.with(atkinson, FontWeight.Bold),
        titleMedium = base.titleMedium.with(atkinson, FontWeight.Bold),
        titleSmall = base.titleSmall.with(atkinson, FontWeight.Bold),
        bodyLarge = base.bodyLarge.with(atkinson),
        bodyMedium = base.bodyMedium.with(atkinson),
        bodySmall = base.bodySmall.with(atkinson),
        labelLarge = base.labelLarge.with(atkinson, FontWeight.Bold),
        labelMedium = base.labelMedium.with(atkinson),
        labelSmall = base.labelSmall.with(atkinson),
    )
    CompositionLocalProvider(LocalNexiomColors provides if (dark) DarkColors else LightColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = typography,
            content = content,
        )
    }
}

private fun TextStyle.with(family: FontFamily, weight: FontWeight? = null) =
    copy(fontFamily = family, fontWeight = weight ?: fontWeight)
