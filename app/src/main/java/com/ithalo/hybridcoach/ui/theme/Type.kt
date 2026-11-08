package com.ithalo.hybridcoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ithalo.hybridcoach.R

/**
 * DotGothic16 (OFL): clean dot-matrix face with plain, unambiguous letterforms. Used for titles
 * and all reading text. Single weight; hierarchy comes from size and colour, never fake bold.
 */
val DotGothicFamily = FontFamily(Font(R.font.dotgothic16, FontWeight.Normal))

/**
 * Departure Mono (OFL): pixel monospace for caps labels, buttons and numbers. Fixed-width digits
 * keep timers and counters from jittering.
 */
val DepartureFamily = FontFamily(Font(R.font.departure_mono, FontWeight.Normal))

/**
 * Doto (OFL): dot-matrix display face, square dots at the heaviest weight. Only for big numbers
 * (reps, load, timers, level); at small sizes the dots blur, so labels stay on Departure Mono.
 */
@OptIn(ExperimentalTextApi::class)
val DotoFamily = FontFamily(
    Font(
        R.font.doto,
        FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.Setting("ROND", 0f)),
    ),
)

@Immutable
data class RetroTypography(
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val number: TextStyle,
    val mono: TextStyle,
    /** Big dot-matrix numbers (Doto); use at 28sp or more. */
    val display: TextStyle,
)

private val Reading = TextStyle(fontFamily = DotGothicFamily, fontSynthesis = FontSynthesis.None)
private val Pixel = TextStyle(fontFamily = DepartureFamily, fontSynthesis = FontSynthesis.None)
private val Display = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Black, fontSynthesis = FontSynthesis.None)

val DefaultRetroTypography = RetroTypography(
    title = Reading.copy(fontSize = 24.sp, lineHeight = 30.sp),
    heading = Reading.copy(fontSize = 19.sp, lineHeight = 25.sp),
    body = Reading.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodySmall = Reading.copy(fontSize = 15.sp, lineHeight = 21.sp),
    label = Pixel.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    button = Pixel.copy(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp),
    number = Pixel.copy(fontSize = 28.sp, lineHeight = 32.sp),
    mono = Pixel.copy(fontSize = 16.sp, lineHeight = 22.sp),
    display = Display.copy(fontSize = 44.sp, lineHeight = 50.sp),
)

/** Material roles mapped onto the pixel fonts so stock Material widgets match. */
fun RetroTypography.toMaterial(): Typography = Typography(
    displayLarge = number.copy(fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = number.copy(fontSize = 34.sp, lineHeight = 40.sp),
    displaySmall = number,
    headlineLarge = title.copy(fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = title.copy(fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = title,
    titleLarge = title.copy(fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = heading,
    titleSmall = heading.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = body,
    bodyMedium = body.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = bodySmall,
    labelLarge = button,
    labelMedium = label,
    labelSmall = label.copy(fontSize = 11.sp, lineHeight = 14.sp),
)
