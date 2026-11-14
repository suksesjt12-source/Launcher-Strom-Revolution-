package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StormDarkColorScheme = darkColorScheme(
    primary = StormAmber,
    onPrimary = StormObsidian,
    primaryContainer = StormAmber.copy(alpha = 0.18f),
    onPrimaryContainer = StormAmberBright,
    secondary = StormCyan,
    onSecondary = StormObsidian,
    secondaryContainer = StormCyan.copy(alpha = 0.16f),
    onSecondaryContainer = StormCyan,
    tertiary = StormSuccess,
    background = StormObsidian,
    onBackground = StormTextPrimary,
    surface = StormSurface,
    onSurface = StormTextPrimary,
    surfaceVariant = StormSurfaceVariant,
    onSurfaceVariant = StormTextSecondary,
    outline = StormCardBorder,
    error = StormError,
    onError = StormTextPrimary
)

private val StormShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun StormLauncherTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StormDarkColorScheme,
        typography = StormTypography,
        shapes = StormShapes,
        content = content
    )
}
