package io.ruv.ruflo.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RufloDarkScheme = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF15215A),
    primaryContainer = Color(0xFF2C3974),
    secondary = Color(0xFFB9C8DB),
    secondaryContainer = Color(0xFF354452),
    tertiary = Color(0xFFDABEEA),
    background = Color(0xFF10131A),
    surface = Color(0xFF10131A),
    surfaceVariant = Color(0xFF252A35),
    onSurface = Color(0xFFE1E2EB),
    onSurfaceVariant = Color(0xFFC4C6D2),
    error = Color(0xFFFFB4AB)
)

@Composable
fun RufloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RufloDarkScheme,
        typography = RufloTypography,
        content = content
    )
}
