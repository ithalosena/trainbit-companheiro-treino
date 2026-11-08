package com.ithalo.hybridcoach.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * One handheld-screen ramp: paper, highlight, light, mid and ink tones, plus a darker-than-paper
 * reading tone ([soft]). Sprites are painted with w/l/m/d; text only uses [ink] and [soft].
 */
@Immutable
data class Ramp(val paper: Color, val highlight: Color, val light: Color, val mid: Color, val ink: Color, val soft: Color)

/**
 * The v3 palettes (docs/DESIGN.md): near-monochrome like a Game Boy screen. Only FLORESTA has an
 * extra hue ([gold]), used for the XP coin and progress fills.
 */
enum class RetroPalette(val label: String, val light: Ramp, val dark: Ramp, val gold: Color? = null) {
    BEGE(
        "Bege",
        Ramp(Color(0xFFD4CCB8), Color(0xFFEAE4D4), Color(0xFFB8B09B), Color(0xFF8C8573), Color(0xFF3E3D36), Color(0xFF524F45)),
        Ramp(Color(0xFF1E1F1B), Color(0xFF96948A), Color(0xFF626358), Color(0xFF3E4038), Color(0xFFD6CEBA), Color(0xFFAAA594)),
    ),
    ALMOND(
        "Almond",
        Ramp(Color(0xFFD6BD98), Color(0xFFE8D6B8), Color(0xFFB0A886), Color(0xFF677D6A), Color(0xFF1A3636), Color(0xFF3A4B45)),
        Ramp(Color(0xFF1A3636), Color(0xFF96A080), Color(0xFF677D6A), Color(0xFF40534C), Color(0xFFD6BD98), Color(0xFFB4A888)),
    ),
    FLORESTA(
        "Floresta",
        Ramp(Color(0xFFE0DED1), Color(0xFFF2F1EA), Color(0xFFB4BDB0), Color(0xFF4E6A5B), Color(0xFF283B28), Color(0xFF3E5446)),
        Ramp(Color(0xFF161A13), Color(0xFF80927E), Color(0xFF4E6A5B), Color(0xFF283B28), Color(0xFFE0DED1), Color(0xFFACB2A2)),
        gold = Color(0xFFA68D4A),
    ),
}

enum class DarkMode { SYSTEM, LIGHT, DARK }

/** Semantic colour slots of the retro design system. Read them through `Retro.colors`. */
@Immutable
data class RetroColors(
    val isDark: Boolean,
    val background: Color,
    /** Dot colour of the dither pattern drawn over [background]. */
    val backgroundPattern: Color,
    val window: Color,
    /** Sunken areas: text fields, progress troughs, list wells. */
    val windowInset: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val border: Color,
    /** Hard offset shadow (no blur). */
    val shadow: Color,
    val titleBar: Color,
    val titleBarInk: Color,
    val titleBarInactive: Color,
    val bevelLight: Color,
    val bevelDark: Color,
    val accent: Color,
    val onAccent: Color,
    val accent2: Color,
    val onAccent2: Color,
    val success: Color,
    val danger: Color,
    val onDanger: Color,
    val disabled: Color,
    val bronze: Color,
    val silver: Color,
    val gold: Color,
    val platinum: Color,
    /** The four sprite tones (highlight, light, mid, ink) for multi-tone pixel art. */
    val spriteHighlight: Color,
    val spriteLight: Color,
    val spriteMid: Color,
    /** XP coin fill and filled progress segments. */
    val reward: Color,
)

/** Plain per-channel sRGB mix (Compose's lerp works in Oklab, which crushes darks toward black). */
fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** WCAG contrast ratio between two opaque colours (1..21). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** Whichever of [dark] / [light] reads better on [bg]. */
private fun onColor(bg: Color, dark: Color, light: Color): Color =
    if (contrastRatio(bg, dark) >= contrastRatio(bg, light)) dark else light

/**
 * Maps a palette ramp onto the semantic slots. Everything is paper + shades of one ink; the danger
 * red and the trophy tiers are pulled toward the ink so they sit inside the monochrome look.
 */
fun retroColors(palette: RetroPalette, dark: Boolean): RetroColors {
    val r = if (dark) palette.dark else palette.light
    val p = r.paper
    val d = r.ink
    val tier = { c: Color -> mix(mix(c, d, if (dark) 0.15f else 0.3f), p, 0.08f) }
    val gold = palette.gold
    val accent = gold ?: d
    val danger = if (dark) mix(Color(0xFFE07A6E), d, 0.2f) else mix(Color(0xFFA83A30), d, 0.25f)
    val colors = RetroColors(
        isDark = dark,
        background = p,
        backgroundPattern = mix(p, d, 0.05f),
        window = p,
        windowInset = mix(p, r.light, 0.45f),
        ink = d,
        inkSoft = r.soft,
        inkFaint = r.mid,
        border = d,
        shadow = r.mid,
        titleBar = p,
        titleBarInk = d,
        titleBarInactive = r.mid,
        bevelLight = r.highlight,
        bevelDark = r.mid,
        accent = accent,
        onAccent = onColor(accent, if (dark) p else d, if (dark) d else p),
        accent2 = d,
        onAccent2 = p,
        success = d,
        danger = danger,
        onDanger = onColor(danger, if (dark) p else d, if (dark) d else p),
        disabled = r.mid,
        bronze = tier(Color(0xFF9A5A2C)),
        silver = tier(Color(0xFF8C9298)),
        gold = gold ?: tier(Color(0xFFC8921E)),
        platinum = tier(Color(0xFF5E9CB0)),
        spriteHighlight = r.highlight,
        spriteLight = r.light,
        spriteMid = r.mid,
        reward = gold ?: d,
    )
    colors.assertReadable()
    return colors
}

/** Contrast guarantee for reading text (ink and inkSoft on paper). Cheap; runs once per palette. */
private fun RetroColors.assertReadable() {
    check(contrastRatio(ink, background) >= 4.5f) { "ink on background below 4.5:1" }
    check(contrastRatio(ink, window) >= 4.5f) { "ink on window below 4.5:1" }
    check(contrastRatio(inkSoft, background) >= 4.5f) { "inkSoft on background below 4.5:1" }
}
