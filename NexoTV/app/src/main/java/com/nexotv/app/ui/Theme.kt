package com.nexotv.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF2EC4B6),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF146C66),
    onPrimaryContainer = Color(0xFFCFFAF5),
    secondary = Color(0xFFFFB454),
    onSecondary = Color(0xFF2A1700),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1D232C),
    onSurfaceVariant = Color(0xFFA9B1BC),
    surfaceContainer = Color(0xFF151A21),
    surfaceContainerHigh = Color(0xFF1B2129),
    surfaceContainerHighest = Color(0xFF222932),
    outline = Color(0xFF3A434F),
    error = Color(0xFFFF6B6B),
)

@Composable
fun NexoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
