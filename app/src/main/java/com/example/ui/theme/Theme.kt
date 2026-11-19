package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = OnForestPrimary,
    primaryContainer = ForestPrimaryContainer,
    onPrimaryContainer = OnForestPrimaryContainer,
    secondary = AmberSecondary,
    onSecondary = OnAmberSecondary,
    secondaryContainer = AmberSecondaryContainer,
    onSecondaryContainer = OnAmberSecondaryContainer,
    tertiary = CobaltTertiary,
    onTertiary = OnCobaltTertiary,
    tertiaryContainer = CobaltTertiaryContainer,
    onTertiaryContainer = OnCobaltTertiaryContainer,
    background = AlabasterBackground,
    onBackground = CharcoalOnBackground,
    surface = WarmSurface,
    onSurface = CharcoalOnBackground,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmOnSurfaceVariant,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkForestPrimary,
    onPrimary = DarkOnForestPrimary,
    primaryContainer = DarkForestPrimaryContainer,
    onPrimaryContainer = DarkOnForestPrimaryContainer,
    secondary = DarkAmberSecondary,
    onSecondary = DarkOnAmberSecondary,
    secondaryContainer = DarkAmberSecondaryContainer,
    onSecondaryContainer = DarkOnAmberSecondaryContainer,
    tertiary = DarkCobaltTertiary,
    onTertiary = DarkOnCobaltTertiary,
    tertiaryContainer = DarkCobaltTertiaryContainer,
    onTertiaryContainer = DarkOnCobaltTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0D100E),
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DziennikZdarzenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
