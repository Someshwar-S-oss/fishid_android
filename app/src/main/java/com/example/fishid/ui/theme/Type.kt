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

// Binomial names are conventionally italic; Hind has no italic.
val SerifItalic = FontFamily(Font(R.font.noto_serif_italic, FontWeight.Normal, FontStyle.Italic))

val ScientificStyle = TextStyle(fontFamily = SerifItalic, fontStyle = FontStyle.Italic, letterSpacing = 0.sp)

/** Tamil vowel signs need ~1.5x line height; English keeps the spec line heights. */
fun tamilLineHeight(style: TextStyle): TextStyle =
    style.copy(lineHeight = maxOf(style.lineHeight.value, style.fontSize.value * 1.5f).roundToInt().sp)

fun fishTypography(tamil: Boolean): Typography {
    fun s(size: Int, line: Int, weight: FontWeight): TextStyle {
        // Tamil glyphs read ~10% larger than Latin at the same sp; headings shrink to match, body text stays put.
        val sz = if (tamil && size >= 18) (size * 0.9f).roundToInt() else size
        val base = TextStyle(fontFamily = Hind, fontWeight = weight, fontSize = sz.sp, lineHeight = line.sp, letterSpacing = 0.sp)
        return if (tamil) tamilLineHeight(base) else base
    }
    return Typography(
        displaySmall = s(40, 44, FontWeight.Bold),      // price figure
        headlineMedium = s(28, 38, FontWeight.Bold),    // fish name (display)
        headlineSmall = s(22, 32, FontWeight.SemiBold), // section headline
        titleLarge = s(22, 30, FontWeight.Bold),        // top bar title
        titleMedium = s(18, 28, FontWeight.SemiBold),   // card title
        titleSmall = s(16, 24, FontWeight.SemiBold),
        bodyLarge = s(16, 26, FontWeight.Normal),       // never smaller for prose
        bodyMedium = s(15, 24, FontWeight.Normal),
        bodySmall = s(13, 20, FontWeight.Normal),       // captions
        labelLarge = s(17, 24, FontWeight.SemiBold),    // buttons
        labelMedium = s(14, 20, FontWeight.Medium),     // tabs, chips
        labelSmall = s(13, 18, FontWeight.Medium),
    )
}
