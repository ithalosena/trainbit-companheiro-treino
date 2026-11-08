package com.ithalo.hybridcoach.ui.retro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.contrastRatio
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.floor

/** Trophy tiers (PlayStation style). */
enum class BadgeTier { BRONZE, SILVER, GOLD, PLATINUM }

/** Palette-tinted colour of the tier. */
@Composable
fun BadgeTier.color(): Color {
    val c = Retro.colors
    return when (this) {
        BadgeTier.BRONZE -> c.bronze
        BadgeTier.SILVER -> c.silver
        BadgeTier.GOLD -> c.gold
        BadgeTier.PLATINUM -> c.platinum
    }
}

@Composable
fun BadgeTier.label(): String = stringResource(
    when (this) {
        BadgeTier.BRONZE -> R.string.retro_tier_bronze
        BadgeTier.SILVER -> R.string.retro_tier_silver
        BadgeTier.GOLD -> R.string.retro_tier_gold
        BadgeTier.PLATINUM -> R.string.retro_tier_platinum
    },
)

/** Four "+" sparkles twinkling around a square area, one after another. */
private fun DrawScope.drawSparkles(t: Float, color: Color) {
    val u = px(2.dp)
    val spots = arrayOf(Offset(0.1f, 0.12f), Offset(0.86f, 0.2f), Offset(0.8f, 0.86f), Offset(0.14f, 0.78f))
    val active = floor(t * spots.size).toInt().coerceIn(0, spots.size - 1)
    val phase = (t * spots.size) % 1f
    val big = phase in 0.3f..0.7f
    val s = spots[active]
    val cx = floor(size.width * s.x / u) * u
    val cy = floor(size.height * s.y / u) * u
    drawRect(color, Offset(cx, cy), Size(u, u))
    val arm = if (big) 2 else 1
    for (k in 1..arm) {
        drawRect(color, Offset(cx - k * u, cy), Size(u, u))
        drawRect(color, Offset(cx + k * u, cy), Size(u, u))
        drawRect(color, Offset(cx, cy - k * u), Size(u, u))
        drawRect(color, Offset(cx, cy + k * u), Size(u, u))
    }
}

/**
 * PlayStation-like "trophy unlocked" toast that slides down from the top, dither-reveals a tier
 * tinted pixel trophy with sparkles, and calls [onFinished] after ~3.5s (set [visible] false then).
 * Place it at the top of a full-screen Box; it pads for the status bar.
 */
@Composable
fun TrophyUnlockedBanner(
    tier: BadgeTier,
    title: String,
    subtitle: String,
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Retro.colors
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(visible) {
        if (visible) {
            delay(3500)
            finished()
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(320, easing = LinearEasing)) { -it * 2 },
        exit = slideOutVertically(tween(240, easing = LinearEasing)) { -it * 2 },
    ) {
        val tierColor = tier.color()
        val reveal = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(250)
            reveal.animateTo(1f, tween(500, easing = LinearEasing))
        }
        val sparkle by rememberInfiniteTransition(label = "sparkle").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t",
        )
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth(),
        ) {
            // XP-style pop-up: hard shadow, border, dithered title bar naming the tier.
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { val o = px(4.dp); drawRect(c.shadow, Offset(o, o), size) }
                    .background(c.window)
                    .pixelBorder(c.border),
            ) {
                RetroTitleStrip(stringResource(R.string.retro_trophy_unlocked), Modifier.padding(2.dp), trailing = tier.label())
                Row(
                    Modifier.padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .drawBehind { drawSparkles(sparkle, tierColor) },
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon("trophy", Modifier.ditherReveal({ reveal.value }), tint = tierColor, size = 36.dp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = Retro.type.heading, color = c.ink, maxLines = 2)
                        Text(subtitle, style = Retro.type.bodySmall, color = c.inkSoft, maxLines = 2)
                    }
                }
            }
        }
    }
}

/**
 * Full-screen "LEVEL UP!" celebration: flashing pixel headline, level count-up, rank title typed
 * out and pixel confetti. Tap anywhere (or back) calls [onDismiss].
 */
@Composable
fun LevelUpOverlay(level: Int, rankTitle: String, visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val c = Retro.colors
        var burst by remember { mutableIntStateOf(0) }
        val pop = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            pop.animateTo(1f, tween(360, easing = LinearEasing))
            burst++
            delay(1500)
            burst++
        }
        val flash by rememberInfiniteTransition(label = "flash").animateFloat(
            0f, 1f, infiniteRepeatable(tween(400, easing = LinearEasing)), label = "f",
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(c.background.copy(alpha = 0.96f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(R.string.retro_level_up),
                    Modifier.graphicsLayer {
                        // Pops in 4 hard steps.
                        val s = 0.25f + floor(pop.value * 3f) * 0.25f
                        scaleX = s
                        scaleY = s
                    },
                    style = Retro.type.number.copy(fontSize = 28.sp, lineHeight = 36.sp),
                    color = if (flash < 0.5f) c.accent else c.ink,
                    textAlign = TextAlign.Center,
                )
                RetroWindow(title = stringResource(R.string.retro_level_window), modifier = Modifier.width(260.dp), framed = true) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(stringResource(R.string.retro_lv_prefix), style = Retro.type.heading, color = c.inkSoft)
                        CountUpText(
                            target = level,
                            Modifier.padding(start = 8.dp),
                            style = Retro.type.display.copy(fontSize = 40.sp, lineHeight = 44.sp),
                            color = c.ink,
                            from = (level - 1).coerceAtLeast(0),
                            durationMs = 700,
                            format = { String.format(Locale.ROOT, "%02d", it) },
                        )
                    }
                    TypewriterText(
                        rankTitle,
                        Modifier.fillMaxWidth(),
                        style = Retro.type.title.copy(textAlign = TextAlign.Center),
                        color = c.accent2,
                        charDelayMs = 55,
                    )
                }
                val blink by rememberInfiniteTransition(label = "tap").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "b",
                )
                Text(
                    stringResource(R.string.retro_tap_to_continue),
                    Modifier.graphicsLayer { alpha = if (blink < 0.6f) 1f else 0f },
                    style = Retro.type.body,
                    color = c.inkSoft,
                )
            }
            PixelConfetti(burst, Modifier.fillMaxSize(), particleCount = 90)
        }
    }
}

/** "LV 07" plate + segmented XP bar + "120/300 XP". The plate shakes when [level] changes. */
@Composable
fun XpBar(level: Int, xpIntoLevel: Int, xpForNext: Int, modifier: Modifier = Modifier) {
    val c = Retro.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.retro_lv, level),
            Modifier.shake(level, 4.dp).background(c.accent2).padding(horizontal = 6.dp, vertical = 2.dp),
            style = Retro.type.button,
            color = c.onAccent2,
        )
        RetroLinearProgress(
            progress = { if (xpForNext <= 0) 1f else xpIntoLevel.toFloat() / xpForNext },
            modifier = Modifier.weight(1f),
            segments = 14,
            color = c.inkSoft,
        )
        Text(stringResource(R.string.retro_xp, xpIntoLevel, xpForNext), style = Retro.type.bodySmall, color = c.inkSoft)
    }
}

/** HUD coin counter: bobbing pixel coin + number that rolls to new values. */
@Composable
fun CoinCounter(count: Int, modifier: Modifier = Modifier, icon: String = "coin", tint: Color = Retro.colors.gold) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PixelIcon(icon, Modifier.pixelFloat(), tint = tint, size = 24.dp)
        CountUpText(
            target = count,
            style = Retro.type.number.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = Retro.colors.ink,
            from = count,
            durationMs = 600,
        )
    }
}

/** Row of hearts (filled = danger colour, empty = faint), like the inspiration HUD. */
@Composable
fun HeartMeter(current: Int, max: Int, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val c = Retro.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(max) { i ->
            val full = i < current
            PixelIcon(
                "heart",
                if (full) Modifier.pixelFloat(amplitude = 1.dp, phase = i * 0.15f) else Modifier,
                tint = if (full) c.danger else c.inkFaint,
                size = size,
            )
        }
    }
}

/** Small HUD stat (no frame): icon, big value, caption. */
@Composable
fun StatTile(
    icon: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val c = Retro.colors
    Column(
        modifier
            .defaultMinSize(minWidth = 88.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PixelIcon(icon, tint = tint, size = 24.dp)
        Text(value, style = Retro.type.heading, color = c.ink, maxLines = 1)
        Text(label, style = Retro.type.label.copy(fontSize = 10.sp), color = c.inkSoft, maxLines = 1)
    }
}

/** Tiny palette canvas used by previews to show a swatch with a border. */
@Composable
internal fun Swatch(color: Color, modifier: Modifier = Modifier) {
    val border = Retro.colors.border
    Canvas(modifier.size(24.dp)) {
        drawRect(color)
        strokeRectPx(border, Offset.Zero, size, px(2.dp))
    }
}
