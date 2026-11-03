package com.aniyomi.cross.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkCinemaScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),            // Violeta Eléctrico Neón
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5B21B6),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFF43F5E),          // Rosa Neón / Coral
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF881337),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF06B6D4),           // Cian / Turquesa
    onTertiary = Color.White,
    background = Color(0xFF0C0D14),         // Fondo Obsidiana Cine
    onBackground = Color(0xFFF8F9FA),
    surface = Color(0xFF141624),            // Superficies y Barras
    onSurface = Color(0xFFF8F9FA),
    surfaceVariant = Color(0xFF1E2134),     // Tarjetas
    onSurfaceVariant = Color(0xFFA0A5B9),
    outline = Color(0xFF2E324E)
)

@Composable
fun AniyomiCrossTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkCinemaScheme,
        typography = Typography,
        content = content
    )
}
