package com.mrboora.plate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.mrboora.plate.R

val Display = FontFamily(
    Font(R.font.bricolage_500, FontWeight.Medium),
    Font(R.font.bricolage_600, FontWeight.SemiBold),
    Font(R.font.bricolage_700, FontWeight.Bold),
    Font(R.font.bricolage_800, FontWeight.ExtraBold),
)

val Body = FontFamily(
    Font(R.font.instrument_400, FontWeight.Normal),
    Font(R.font.instrument_500, FontWeight.Medium),
    Font(R.font.instrument_600, FontWeight.SemiBold),
)

/**
 * Poster cut: Bricolage Grotesque at its narrowest width (75), 96 pt optical size, ExtraBold.
 * The same family as [Display], squeezed like a food-label headline. Use it for one big number
 * or name per screen, set in capitals; never for running text.
 */
val Poster = FontFamily(Font(R.font.bricolage_condensed_800, FontWeight.ExtraBold))

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

val PlateTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = (-1.6).sp, lineHeightStyle = tight),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-1.1).sp, lineHeightStyle = tight),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

/** Poster styles; see [Poster]. Sizes step down by about 0.6. */
object PosterType {
    val hero = TextStyle(fontFamily = Poster, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 58.sp, letterSpacing = (-0.5).sp, lineHeightStyle = tight)
    val title = TextStyle(fontFamily = Poster, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 38.sp, letterSpacing = (-0.2).sp, lineHeightStyle = tight)
    val section = TextStyle(fontFamily = Poster, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 26.sp, letterSpacing = 0.2.sp, lineHeightStyle = tight)
    val ticker = TextStyle(fontFamily = Poster, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp)
    val index = TextStyle(fontFamily = Poster, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
}
