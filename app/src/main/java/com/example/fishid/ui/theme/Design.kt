package com.example.fishid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The two candidate designs, switchable in Settings so the team can compare them on one phone.
 * To keep only one: delete the other's palette (Color.kt), its branch in [fishStyle] / colour schemes,
 * its `when` branches in the UI, and the Design card in Settings.
 */
enum class Design { SEA, MARKET }

/** The chosen design. Compose state, so switching restyles every open screen at once (no activity restart). */
object DesignChoice {
    var current by mutableStateOf(Design.SEA)
}

/** Colours Material 3 has no role for. */
@Immutable
class FishColors(
    val accent: Color,            // scan brackets while searching, small highlights; never body text on light
    val accentInk: Color,         // accent used as text/icon on light surfaces
    val lock: Color,              // brackets once a fish is identified
    val cta: Color,               // main call-to-action fill
    val onCta: Color,
    val secondaryButton: Color,
    val onSecondaryButton: Color,
    val sure: Color, val sureContainer: Color, val onSureContainer: Color,       // "High match"
    val likely: Color, val likelyContainer: Color, val onLikelyContainer: Color, // "Likely match"
    val priceContainer: Color,
    val onPriceContainer: Color,
    val iconBadge: Color,         // circle behind section icons
    val onIconBadge: Color,
    val cameraScrim: Color,       // bars and chips over the viewfinder
    val cameraBar: Color,         // bottom bar while the camera shows
    val cameraIndicator: Color,   // selected tab pill on that bar
    val onCameraIndicator: Color,
    val cameraHighlight: Color,   // tappable text / active toggles over the camera
    val photoBackdrop: Color,     // behind photos while they load
    val border: Color,            // card edges: a hairline (Sea) or an ink rule (Market)
    val shadow: Color,            // solid offset shadow (Market); transparent = none
    val nutrition: List<Color>,   // protein, fat, moisture, ash, carbohydrate
)

@Immutable
class FishStyle(
    val design: Design,
    val dark: Boolean,
    val colors: FishColors,
    val cardRadius: Dp,
    val cardBorder: Dp,
    val cardElevation: Dp,   // soft shadow
    val hardShadow: Dp,      // solid offset "sticker" shadow
    val buttonRadius: Dp,
    val buttonHeight: Dp,
    val buttonBorder: Dp,
    val bracketStroke: Dp,
    val display: FontFamily, // headings, buttons, prices
) {
    val isMarket get() = design == Design.MARKET
}

val LocalFishStyle = staticCompositionLocalOf { fishStyle(Design.SEA, dark = false) }

/** Design tokens beyond MaterialTheme: `FishTheme.style.cardRadius`, `FishTheme.colors.accent`. */
object FishTheme {
    val style: FishStyle
        @Composable @ReadOnlyComposable get() = LocalFishStyle.current
    val colors: FishColors
        @Composable @ReadOnlyComposable get() = LocalFishStyle.current.colors
}

fun fishStyle(design: Design, dark: Boolean): FishStyle = when (design) {
    Design.SEA -> FishStyle(
        design, dark,
        colors = if (dark) seaNight else seaDay,
        cardRadius = 20.dp, cardBorder = 1.dp, cardElevation = if (dark) 0.dp else 1.dp, hardShadow = 0.dp,
        buttonRadius = 50.dp, buttonHeight = 56.dp, buttonBorder = 0.dp,
        bracketStroke = 5.dp,
        display = Hind,
    )
    Design.MARKET -> FishStyle(
        design, dark,
        colors = if (dark) marketNight else marketDay,
        cardRadius = 14.dp, cardBorder = 2.dp, cardElevation = 0.dp, hardShadow = if (dark) 0.dp else 3.dp,
        buttonRadius = 14.dp, buttonHeight = 60.dp, buttonBorder = 2.dp,
        bracketStroke = 7.dp,
        display = Baloo,
    )
}

private val seaDay = FishColors(
    accent = Sea.Coral, accentInk = Sea.CoralInk, lock = Sea.Lock,
    cta = Sea.Ocean, onCta = Color.White,
    secondaryButton = Sea.OceanTint, onSecondaryButton = Sea.OceanDeep,
    sure = Sea.Sure, sureContainer = Sea.SureTint, onSureContainer = Sea.SureInk,
    likely = Sea.Likely, likelyContainer = Sea.LikelyTint, onLikelyContainer = Sea.LikelyInk,
    priceContainer = Sea.CoralTint, onPriceContainer = Sea.Ink,
    iconBadge = Sea.OceanTint, onIconBadge = Sea.Ocean,
    cameraScrim = Color(0xA60A1622), cameraBar = Sea.NightBackground,
    cameraIndicator = Sea.NightOceanTint, onCameraIndicator = Color.White, cameraHighlight = Sea.NightOcean,
    photoBackdrop = Color(0xFF102A3E),
    border = Sea.Line, shadow = Color.Transparent,
    nutrition = listOf(Sea.Ocean, Sea.Coral, Color(0xFFCFE3EE), Sea.InkMuted, Color(0xFF8A6FD1)),
)

private val seaNight = FishColors(
    accent = Sea.NightCoral, accentInk = Sea.NightCoral, lock = Sea.Lock,
    cta = Sea.NightOcean, onCta = Sea.NightOceanDeep,
    secondaryButton = Sea.NightOceanTint, onSecondaryButton = Sea.NightOceanPale,
    sure = Color(0xFF5FD39C), sureContainer = Color(0xFF123F2E), onSureContainer = Color(0xFFB7EBD0),
    likely = Color(0xFFFFB84D), likelyContainer = Color(0xFF4A3510), onLikelyContainer = Color(0xFFFFDDA0),
    priceContainer = Color(0xFF3B2420), onPriceContainer = Sea.NightInk,
    iconBadge = Sea.NightOceanTint, onIconBadge = Sea.NightOceanPale,
    cameraScrim = Color(0xA60A1622), cameraBar = Sea.NightBackground,
    cameraIndicator = Sea.NightOceanTint, onCameraIndicator = Color.White, cameraHighlight = Sea.NightOcean,
    photoBackdrop = Sea.NightPhoto,
    border = Sea.NightLine, shadow = Color.Transparent,
    nutrition = listOf(Sea.NightOcean, Sea.NightCoral, Color(0xFF2E5A73), Sea.NightInkMuted, Color(0xFFB79CF0)),
)

private val marketDay = FishColors(
    accent = Market.Turmeric, accentInk = Market.Vermilion, lock = Market.Lock,
    cta = Market.Turmeric, onCta = Market.Ink,
    secondaryButton = Color.White, onSecondaryButton = Market.Ink,
    sure = Market.Sure, sureContainer = Market.SureTint, onSureContainer = Market.SureInk,
    likely = Market.Likely, likelyContainer = Market.LikelyTint, onLikelyContainer = Market.LikelyInk,
    priceContainer = Market.Turmeric, onPriceContainer = Market.Ink,
    iconBadge = Market.Turmeric, onIconBadge = Market.Ink,
    cameraScrim = Color(0xB01B1B1B), cameraBar = Market.Ink,
    cameraIndicator = Market.Turmeric, onCameraIndicator = Market.Ink, cameraHighlight = Market.Turmeric,
    photoBackdrop = Color(0xFF242424),
    border = Market.Ink, shadow = Market.Ink,
    nutrition = listOf(Market.BoatBlue, Market.Turmeric, Color(0xFFDCE4FB), Market.InkMuted, Market.Vermilion),
)

private val marketNight = FishColors(
    accent = Market.Turmeric, accentInk = Market.NightVermilion, lock = Market.Lock,
    cta = Market.Turmeric, onCta = Market.Ink,
    secondaryButton = Market.NightSurface, onSecondaryButton = Market.NightInk,
    sure = Color(0xFF6FD08C), sureContainer = Color(0xFF173D22), onSureContainer = Color(0xFFC4EFCF),
    likely = Market.Turmeric, likelyContainer = Color(0xFF4A3A0A), onLikelyContainer = Color(0xFFFFE8A3),
    priceContainer = Market.Turmeric, onPriceContainer = Market.Ink,
    iconBadge = Market.Turmeric, onIconBadge = Market.Ink,
    cameraScrim = Color(0xB01B1B1B), cameraBar = Market.Ink,
    cameraIndicator = Market.Turmeric, onCameraIndicator = Market.Ink, cameraHighlight = Market.Turmeric,
    photoBackdrop = Color(0xFF242424),
    border = Market.NightBorder, shadow = Color.Transparent,
    nutrition = listOf(Market.NightBoatBlue, Market.Turmeric, Color(0xFF3A4A70), Market.NightInkMuted, Market.NightVermilion),
)
