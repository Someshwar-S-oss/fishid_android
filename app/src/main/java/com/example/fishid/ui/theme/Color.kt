package com.example.fishid.ui.theme

import androidx.compose.ui.graphics.Color

// Two designs (see Design.kt). Contrast ratios are WCAG, against the background the colour is used on.
// Body text is >= 7:1 everywhere: the app is read in market sunlight on budget screens.

/** A · Sea (Kadal): calm field guide. Warm sand, ocean blue actions, coral for scanning. */
object Sea {
    val Ocean = Color(0xFF0E5A8A)          // primary, white text 7.4:1
    val OceanDeep = Color(0xFF0A3D5E)      // on ocean tint, 9.4:1
    val OceanTint = Color(0xFFDCEBF5)
    val Coral = Color(0xFFF0644A)          // brackets / highlights, never text on light
    val CoralInk = Color(0xFFB3402A)       // coral as text, 5.7:1 on white
    val CoralTint = Color(0xFFFDECE6)
    val Ink = Color(0xFF0D2235)            // 14.6:1 on sand
    val InkMuted = Color(0xFF4A5A6A)       // 7.1:1 on white
    val Sand = Color(0xFFF6F3EC)           // background
    val Sand2 = Color(0xFFEFEAE0)
    val Line = Color(0xFFDDD5C7)
    val LineSoft = Color(0xFFE9E3D8)
    val Sure = Color(0xFF127A52)
    val SureTint = Color(0xFFDCF2E7)
    val SureInk = Color(0xFF0B5137)        // 8.0:1 on its tint
    val Likely = Color(0xFFB26A00)
    val LikelyTint = Color(0xFFFFEFD2)
    val LikelyInk = Color(0xFF7A4A00)      // 6.6:1 on its tint
    val Lock = Color(0xFF2FB37A)           // bracket lock over the camera

    val NightBackground = Color(0xFF0A1622)
    val NightSurface = Color(0xFF122130)
    val NightSurface2 = Color(0xFF1A2D3E)
    val NightOcean = Color(0xFF8CC8F0)     // on night surface 9.1:1
    val NightOceanDeep = Color(0xFF00304D)
    val NightOceanTint = Color(0xFF164766)
    val NightOceanPale = Color(0xFFD2E9F9)
    val NightCoral = Color(0xFFFF8A70)
    val NightInk = Color(0xFFE9F0F6)
    val NightInkMuted = Color(0xFFA9B8C6)  // 8.1:1 on night surface
    val NightLine = Color(0xFF2B4053)
    val NightLineSoft = Color(0xFF213446)
    val NightPhoto = Color(0xFF0E2233)
}

/** B · Market (Sandhai): bold signage. Cream paper, black ink borders, turmeric calls to action. */
object Market {
    val Turmeric = Color(0xFFFFC21A)       // CTA fill and scan brackets; ink text on it 10.7:1
    val TurmericTint = Color(0xFFFFE8A3)
    val Ink = Color(0xFF1B1B1B)            // 16.3:1 on cream; also borders and hard shadows
    val InkMuted = Color(0xFF4D4637)       // 9.4:1 on white
    val Cream = Color(0xFFFFF8E8)          // background
    val Cream2 = Color(0xFFF5EDD9)
    val LineSoft = Color(0xFFE6DCC6)       // dividers only; card edges are ink
    val BoatBlue = Color(0xFF1D4AC4)       // primary: links, tabs, radios; white text 7.4:1
    val BoatBlueDeep = Color(0xFF0F2A78)
    val BoatBlueTint = Color(0xFFDCE4FB)
    val Vermilion = Color(0xFFC2321F)      // logo and rare emphasis, 5.6:1 on white
    val Sure = Color(0xFF1E7F3B)
    val SureTint = Color(0xFFD9F2DF)
    val SureInk = Color(0xFF0E4D22)        // 8.4:1 on its tint
    val Likely = Color(0xFF9A6A00)
    val LikelyTint = Color(0xFFFFE8A3)
    val LikelyInk = Color(0xFF5C4300)      // 7.7:1 on its tint
    val Lock = Color(0xFF3DDC84)

    val NightBackground = Color(0xFF141414)
    val NightSurface = Color(0xFF1F1F1F)
    val NightSurface2 = Color(0xFF2A2A2A)
    val NightInk = Color(0xFFF5F0E4)       // 16.2:1
    val NightInkMuted = Color(0xFFC9C0AC)  // 9.1:1 on night surface
    val NightBorder = Color(0xFFCFC6B0)
    val NightLineSoft = Color(0xFF3A372F)
    val NightBoatBlue = Color(0xFF9DB6FF)  // 8.3:1 on night surface
    val NightBoatBlueTint = Color(0xFF233A80)
    val NightVermilion = Color(0xFFFF7A66)
}

val ErrorRed = Color(0xFFB3261E)

// Official IUCN Red List colours; second value is the text colour on it.
val IucnColors = mapOf(
    "ex" to (Color(0xFF000000) to Color.White),
    "ew" to (Color(0xFF542344) to Color.White),
    "cr" to (Color(0xFFD81E05) to Color.White),
    "en" to (Color(0xFFFC7F3F) to Color(0xFF0F1B2D)),
    "vu" to (Color(0xFFF9E814) to Color(0xFF0F1B2D)),
    "nt" to (Color(0xFFCCE226) to Color(0xFF0F1B2D)),
    "lc" to (Color(0xFF60C659) to Color(0xFF0F1B2D)),
    "dd" to (Color(0xFFD1D1C6) to Color(0xFF0F1B2D)),
    "ne" to (Color(0xFFFFFFFF) to Color(0xFF0F1B2D)),
)
