package com.example.fishid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

private val LightColors = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = TealTint, onPrimaryContainer = TealPressed,
    secondary = Marigold, onSecondary = Ink,
    secondaryContainer = TealTint, onSecondaryContainer = TealPressed, // tonal buttons/chips are teal; marigold is used directly
    background = Background, onBackground = Ink,
    surface = Surface, onSurface = Ink,
    surfaceVariant = Background, onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Surface, surfaceContainerLow = Surface, surfaceContainer = Surface,
    surfaceContainerHigh = Surface, surfaceContainerHighest = LineSoft,
    outline = Line, outlineVariant = LineSoft,
    error = ErrorRed, onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = NightPrimary, onPrimary = NightBackground,
    primaryContainer = Color(0xFF1F4F58), onPrimaryContainer = Color(0xFFCDEFF4),
    secondary = Marigold, onSecondary = Ink,
    secondaryContainer = Color(0xFF1F4F58), onSecondaryContainer = Color(0xFFCDEFF4),
    background = NightBackground, onBackground = NightInk,
    surface = NightSurface, onSurface = NightInk,
    surfaceVariant = NightSurface2, onSurfaceVariant = NightInkMuted,
    surfaceContainerLowest = NightSurface, surfaceContainerLow = NightSurface, surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurface2, surfaceContainerHighest = NightSurface2,
    outline = NightLine, outlineVariant = Color(0xFF22313D),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
)

/** True when the app UI language is Tamil. Species names and line heights follow it. */
@Composable
@ReadOnlyComposable
fun isTamil(): Boolean = LocalConfiguration.current.locales[0].language == "ta"

// Dynamic colour (Material You) is deliberately off: FishID must look the same on every phone.
@Composable
fun FishIDTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = fishTypography(isTamil()),
        content = content,
    )
}
