package com.example.fishid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

private val SeaLight = lightColorScheme(
    primary = Sea.Ocean, onPrimary = Color.White,
    primaryContainer = Sea.OceanTint, onPrimaryContainer = Sea.OceanDeep,
    secondary = Sea.Coral, onSecondary = Sea.Ink,
    secondaryContainer = Sea.OceanTint, onSecondaryContainer = Sea.OceanDeep, // tonal buttons/chips are ocean; coral is used directly
    background = Sea.Sand, onBackground = Sea.Ink,
    surface = Color.White, onSurface = Sea.Ink,
    surfaceVariant = Sea.Sand2, onSurfaceVariant = Sea.InkMuted,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Sea.Sand2,
    outline = Sea.Line, outlineVariant = Sea.LineSoft,
    error = ErrorRed, onError = Color.White,
)

private val SeaDark = darkColorScheme(
    primary = Sea.NightOcean, onPrimary = Sea.NightOceanDeep,
    primaryContainer = Sea.NightOceanTint, onPrimaryContainer = Sea.NightOceanPale,
    secondary = Sea.NightCoral, onSecondary = Sea.Ink,
    secondaryContainer = Sea.NightOceanTint, onSecondaryContainer = Sea.NightOceanPale,
    background = Sea.NightBackground, onBackground = Sea.NightInk,
    surface = Sea.NightSurface, onSurface = Sea.NightInk,
    surfaceVariant = Sea.NightSurface2, onSurfaceVariant = Sea.NightInkMuted,
    surfaceContainerLowest = Sea.NightSurface, surfaceContainerLow = Sea.NightSurface, surfaceContainer = Sea.NightSurface,
    surfaceContainerHigh = Sea.NightSurface2, surfaceContainerHighest = Sea.NightSurface2,
    outline = Sea.NightLine, outlineVariant = Sea.NightLineSoft,
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
)

// Market keeps `primary` a readable blue: Material draws text, radios and tab labels in primary,
// and turmeric on white would fail contrast. Turmeric reaches buttons through FishColors.cta.
private val MarketLight = lightColorScheme(
    primary = Market.BoatBlue, onPrimary = Color.White,
    primaryContainer = Market.BoatBlueTint, onPrimaryContainer = Market.BoatBlueDeep,
    secondary = Market.Turmeric, onSecondary = Market.Ink,
    secondaryContainer = Market.TurmericTint, onSecondaryContainer = Market.Ink,
    background = Market.Cream, onBackground = Market.Ink,
    surface = Color.White, onSurface = Market.Ink,
    surfaceVariant = Market.Cream2, onSurfaceVariant = Market.InkMuted,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Market.Cream2,
    outline = Market.Ink, outlineVariant = Market.LineSoft,
    error = ErrorRed, onError = Color.White,
)

private val MarketDark = darkColorScheme(
    primary = Market.NightBoatBlue, onPrimary = Market.BoatBlueDeep,
    primaryContainer = Market.NightBoatBlueTint, onPrimaryContainer = Market.BoatBlueTint,
    secondary = Market.Turmeric, onSecondary = Market.Ink,
    secondaryContainer = Color(0xFF4A3A0A), onSecondaryContainer = Market.TurmericTint,
    background = Market.NightBackground, onBackground = Market.NightInk,
    surface = Market.NightSurface, onSurface = Market.NightInk,
    surfaceVariant = Market.NightSurface2, onSurfaceVariant = Market.NightInkMuted,
    surfaceContainerLowest = Market.NightSurface, surfaceContainerLow = Market.NightSurface, surfaceContainer = Market.NightSurface,
    surfaceContainerHigh = Market.NightSurface2, surfaceContainerHighest = Market.NightSurface2,
    outline = Market.NightBorder, outlineVariant = Market.NightLineSoft,
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
)

private fun colorScheme(design: Design, dark: Boolean): ColorScheme = when (design) {
    Design.SEA -> if (dark) SeaDark else SeaLight
    Design.MARKET -> if (dark) MarketDark else MarketLight
}

/** True when the app UI language is Tamil. Species names and line heights follow it. */
@Composable
@ReadOnlyComposable
fun isTamil(): Boolean = LocalConfiguration.current.locales[0].language == "ta"

// Dynamic colour (Material You) is deliberately off: FishID must look the same on every phone.
@Composable
fun FishIDTheme(
    design: Design = DesignChoice.current,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tamil = isTamil()
    val style = remember(design, darkTheme) { fishStyle(design, darkTheme) }
    val typography = remember(design, tamil) { fishTypography(tamil, design) }
    CompositionLocalProvider(LocalFishStyle provides style) {
        MaterialTheme(colorScheme = colorScheme(design, darkTheme), typography = typography, content = content)
    }
}
