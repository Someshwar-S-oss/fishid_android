package com.example.fishid.ui.theme

import androidx.compose.ui.graphics.Color

// Tokens from the FishID visual design (Foundations board). Contrast ratios are against white.
val Teal = Color(0xFF0E6E7E)          // primary, 5.9:1
val TealPressed = Color(0xFF0A5562)
val TealTint = Color(0xFFE3F1F3)
val Marigold = Color(0xFFF2A541)      // scan brackets / highlights only, never text on light
val MarigoldTint = Color(0xFFFDF1DC)
val MarigoldInk = Color(0xFF8A5A00)
val LockGreen = Color(0xFF2E9E5B)
val Ink = Color(0xFF0F1B2D)           // 17:1
val InkMuted = Color(0xFF4A5565)      // 7.6:1
val InkSubtle = Color(0xFF6B7684)     // 4.6:1, captions >= 13sp only
val Line = Color(0xFFDDE3E8)
val LineSoft = Color(0xFFE8ECEF)
val Background = Color(0xFFF5F7F8)
val Surface = Color(0xFFFFFFFF)
val ErrorRed = Color(0xFFB3261E)

val NightBackground = Color(0xFF0B141C)
val NightSurface = Color(0xFF14212C)
val NightSurface2 = Color(0xFF1C2C38)
val NightPrimary = Color(0xFF5CC4D2)
val NightInk = Color(0xFFE8EEF2)
val NightInkMuted = Color(0xFFA9B6C1)
val NightLine = Color(0xFF2A3A47)

val CameraScrim = Color(0x9E080F1A)   // #080F1A at 62%

// Nutrition series: distinct in lightness, not hue alone.
val NutProtein = Teal
val NutFat = Marigold
val NutMoisture = Color(0xFFCFE6EC)
val NutAsh = InkMuted
val NutCarb = Color(0xFF9A6BC7)

// Official IUCN Red List colours; second value is the text colour on it.
val IucnColors = mapOf(
    "ex" to (Color(0xFF000000) to Color.White),
    "ew" to (Color(0xFF542344) to Color.White),
    "cr" to (Color(0xFFD81E05) to Color.White),
    "en" to (Color(0xFFFC7F3F) to Ink),
    "vu" to (Color(0xFFF9E814) to Ink),
    "nt" to (Color(0xFFCCE226) to Ink),
    "lc" to (Color(0xFF60C659) to Ink),
    "dd" to (Color(0xFFD1D1C6) to Ink),
    "ne" to (Color(0xFFFFFFFF) to Ink),
)
