package com.jf.checkin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PaperColorScheme = lightColorScheme(
    primary = InkPrimary,
    onPrimary = InkOnDark,
    primaryContainer = BadgeBackground,
    onPrimaryContainer = InkPrimary,
    secondary = InkSecondary,
    onSecondary = InkOnDark,
    secondaryContainer = BadgeBackground,
    onSecondaryContainer = InkPrimary,
    tertiary = InkSecondary,
    onTertiary = InkOnDark,
    tertiaryContainer = PaperSunken,
    onTertiaryContainer = InkPrimary,
    background = PaperBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = InkSecondary,
    surfaceTint = Color.Transparent,
    surfaceDim = PaperSunken,
    surfaceBright = PaperSurface,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurface,
    surfaceContainerHighest = PaperSunken,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Danger,
    onError = InkOnDark,
    errorContainer = DangerSoft,
    onErrorContainer = Danger,
    inverseSurface = InkPrimary,
    inverseOnSurface = PaperSurface,
    inversePrimary = PaperSurface
)

private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun JFCheckInTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColorScheme,
        typography = Typography,
        shapes = PaperShapes,
        content = content
    )
}
