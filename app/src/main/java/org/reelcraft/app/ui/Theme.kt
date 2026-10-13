package org.reelcraft.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Ink = Color(0xFF141519)
    val Panel = Color(0xFF1C1E24)
    val Raise = Color(0xFF262932)
    val Line = Color(0xFF323541)
    val Fg = Color(0xFFEDEBE6)
    val Muted = Color(0xFF9A9CA8)
    val Amber = Color(0xFFF4B53F)
    val AmberInk = Color(0xFF1E1606)
    val Teal = Color(0xFF5FD0C3)
    val Danger = Color(0xFFF07167)
    val Track = Color(0xFF0E0F12)
}

private val scheme = darkColorScheme(
    primary = Palette.Amber,
    onPrimary = Palette.AmberInk,
    secondary = Palette.Teal,
    onSecondary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Palette.Fg,
    surface = Palette.Panel,
    onSurface = Palette.Fg,
    surfaceVariant = Palette.Raise,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.Raise,
    surfaceContainerHighest = Palette.Raise,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Danger,
)

@Composable
fun ReelCraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
