package com.example.fishid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.fishid.R
import kotlin.math.roundToInt

// One family for Latin + Tamil so mixed-script lines match in weight and height.
val Hind = FontFamily(
    Font(R.font.hind_madurai_regular, FontWeight.Normal),
    Font(R.font.hind_madurai_medium, FontWeight.Medium),
    Font(R.font.hind_madurai_semibold, FontWeight.SemiBold),
    Font(R.font.hind_madurai_bold, FontWeight.Bold),
)

// Market design's display face: rounded, chunky, Latin + Tamil drawn together (Ek Type, OFL).
// Static instances cut from the variable font (minSdk 24 has no variable-font support).
val Baloo = FontFamily(
    Font(R.font.baloo_thambi2_semibold, FontWeight.SemiBold),
    Font(R.font.baloo_thambi2_extrabold, FontWeight.ExtraBold),
)

// Binomial names are conventionally italic; Hind has no italic.
val SerifItalic = FontFamily(Font(R.font.noto_serif_italic, FontWeight.Normal, FontStyle.Italic))

val ScientificStyle = TextStyle(fontFamily = SerifItalic, fontStyle = FontStyle.Italic, letterSpacing = 0.sp)

/** Tamil vowel signs need ~1.5x line height; English keeps the spec line heights. */
fun tamilLineHeight(style: TextStyle): TextStyle =
    style.copy(lineHeight = maxOf(style.lineHeight.value, style.fontSize.value * 1.5f).roundToInt().sp)

fun fishTypography(tamil: Boolean, design: Design = Design.SEA): Typography {
    val market = design == Design.MARKET
    fun s(size: Int, line: Int, weight: FontWeight, display: Boolean = false): TextStyle {
        // Tamil glyphs read ~10% larger than Latin at the same sp; headings shrink to match, body text stays put.
        val sz = if (tamil && size >= 18) (size * 0.9f).roundToInt() else size
        // Baloo has two cuts: SemiBold for titles and buttons, ExtraBold for anything Bold or heavier.
        val family = if (market && display) Baloo else Hind
        val w = if (market && display) (if (weight >= FontWeight.Bold) FontWeight.ExtraBold else FontWeight.SemiBold) else weight
        // Baloo's tall vertical metrics already add air: Hind's line heights left wrapped names looking gappy.
        val lh = if (market && display) minOf(line, (sz * 1.2f).roundToInt()) else line
        val base = TextStyle(fontFamily = family, fontWeight = w, fontSize = sz.sp, lineHeight = lh.sp, letterSpacing = 0.sp)
        return if (tamil) tamilLineHeight(base) else base
    }
    // Market runs headings a size up: signage is read at a glance, often at arm's length.
    val up = if (market) 2 else 0
    return Typography(
        displaySmall = s(40 + up, 44 + up, FontWeight.Bold, display = true),      // price figure
        headlineMedium = s(28 + up, 38 + up, FontWeight.Bold, display = true),    // fish name (display)
        headlineSmall = s(22 + up, 32 + up, FontWeight.SemiBold, display = true), // section headline
        titleLarge = s(22, 30, FontWeight.Bold, display = true),                  // top bar title
        titleMedium = s(18, 28, FontWeight.SemiBold, display = true),             // card title
        titleSmall = s(16, 24, FontWeight.SemiBold),
        bodyLarge = s(16, 26, FontWeight.Normal),                                 // never smaller for prose
        bodyMedium = s(15, 24, FontWeight.Normal),
        bodySmall = s(13, 20, FontWeight.Normal),                                 // captions
        labelLarge = s(17 + up / 2, 24, FontWeight.SemiBold, display = true),     // buttons
        labelMedium = s(14, 20, FontWeight.Medium),                               // tabs, chips
        labelSmall = s(13, 18, FontWeight.Medium),
    )
}
