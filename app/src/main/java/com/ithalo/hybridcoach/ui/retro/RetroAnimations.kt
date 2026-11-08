package com.ithalo.hybridcoach.ui.retro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Types [text] one character at a time. The full text is laid out from the start (remaining
 * characters transparent) so nothing reflows; an ink block marks the next character.
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Retro.type.body,
    color: Color = LocalContentColor.current,
    charDelayMs: Long = 30,
    onDone: () -> Unit = {},
) {
    var shown by remember(text) { mutableIntStateOf(0) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(text) {
        while (shown < text.length) {
            delay(if (text[shown] == ' ') charDelayMs / 2 else charDelayMs)
            shown++
        }
        done()
    }
    val annotated = remember(text, shown, color) {
        buildAnnotatedString {
            append(text.substring(0, shown))
            if (shown < text.length) {
                withStyle(SpanStyle(color = Color.Transparent, background = color)) { append(text[shown]) }
                withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(shown + 1)) }
            }
        }
    }
    Text(annotated, modifier, style = style, color = color)
}

/** Terminal block cursor blinking at ~1Hz in hard on/off steps. */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    width: Dp = 10.dp,
    height: Dp = 20.dp,
) {
    val t by rememberInfiniteTransition(label = "cursor").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "blink",
    )
    Box(modifier.size(width, height).drawBehind { if (t < 0.5f) drawRect(color) })
}

/** Pixel hourglass that rests, then flips 180° in hard 45° steps (Win 3.1 busy cursor). */
@Composable
fun HourglassSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = LocalContentColor.current,
) {
    val glyph = remember { pixelGlyph("hourglass") }
    val t by rememberInfiniteTransition(label = "hourglass").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "flip",
    )
    Canvas(modifier.size(size)) {
        // 0..0.4 rest, 0.4..0.5 flip to 180, 0.5..0.9 rest, 0.9..1 flip to 360.
        fun step(p: Float) = floor(p * 4f).coerceAtMost(4f) * 45f
        val angle = when {
            t < 0.4f -> 0f
            t < 0.5f -> step((t - 0.4f) / 0.1f)
            t < 0.9f -> 180f
            else -> 180f + step((t - 0.9f) / 0.1f)
        }
        rotate(angle) { drawPixelGlyph(glyph, tint) }
    }
}

/** Number that counts up (or down) to [target] whenever it changes. */
@Composable
fun CountUpText(
    target: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = Retro.type.number,
    color: Color = LocalContentColor.current,
    from: Int = 0,
    durationMs: Int = 900,
    format: (Int) -> String = { it.toString() },
) {
    val value = remember { Animatable(from.toFloat()) }
    LaunchedEffect(target) { value.animateTo(target.toFloat(), tween(durationMs, easing = LinearEasing)) }
    Text(format(value.value.roundToInt()), modifier, style = style, color = color)
}

/** Horizontal error shake each time [trigger] changes (not on first composition; null = never). */
fun Modifier.shake(trigger: Any?, distance: Dp = 8.dp): Modifier = composed {
    val offset = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) { first = false; return@LaunchedEffect }
        if (trigger == null) return@LaunchedEffect
        for (f in floatArrayOf(-1f, 1f, -0.75f, 0.75f, -0.5f, 0.5f, -0.25f, 0f)) {
            offset.animateTo(f, tween(40, easing = LinearEasing))
        }
    }
    graphicsLayer { translationX = (offset.value * distance.toPx()).roundToInt().toFloat() }
}

/** Bevel press feedback: content sinks [depth] while [interactionSource] reports a press. */
fun Modifier.pressDown(interactionSource: InteractionSource, depth: Dp = 2.dp): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    graphicsLayer {
        val d = if (pressed) depth.toPx().roundToInt().toFloat() else 0f
        translationX = d
        translationY = d
    }
}

/** Gentle bob in whole-pixel steps (for coins, stars). [phase] (0..1) de-syncs neighbours. */
fun Modifier.pixelFloat(amplitude: Dp = 2.dp, periodMs: Int = 1400, phase: Float = 0f): Modifier = composed {
    val t by rememberInfiniteTransition(label = "float").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "bob",
    )
    graphicsLayer {
        val p = (t + phase) % 1f
        // Triangle wave quantised to 1dp steps: 0 → -amp → 0.
        val tri = if (p < 0.5f) p * 2f else (1f - p) * 2f
        val stepPx = 1.dp.toPx().roundToInt().coerceAtLeast(1)
        val steps = (amplitude.toPx() / stepPx).roundToInt()
        translationY = -(tri * steps).roundToInt().toFloat() * stepPx
    }
}

private class Particle(val vx: Float, val vy: Float, val color: Int, val size: Int, val spin: Boolean)

/**
 * Burst of pixel squares in palette colours, fired each time [trigger] changes (not on first
 * composition). Draws over its bounds — give it `Modifier.fillMaxSize()` in an overlay Box.
 * [origin] is a fraction of the bounds.
 */
@Composable
fun PixelConfetti(
    trigger: Any?,
    modifier: Modifier = Modifier.fillMaxSize(),
    particleCount: Int = 70,
    origin: Offset = Offset(0.5f, 0.35f),
    durationMs: Int = 1800,
) {
    val c = Retro.colors
    val palette = remember(c) { listOf(c.accent, c.accent2, c.gold, c.ink, c.silver, c.accent) }
    val progress = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    val particles = remember(trigger, particleCount) {
        val rnd = Random(trigger.hashCode())
        Array(particleCount) {
            Particle(
                vx = rnd.nextFloat() * 2f - 1f,
                vy = -(0.5f + rnd.nextFloat() * 0.9f),
                color = rnd.nextInt(palette.size),
                size = 2 + rnd.nextInt(2),
                spin = rnd.nextBoolean(),
            )
        }
    }
    LaunchedEffect(trigger) {
        if (first) { first = false; return@LaunchedEffect }
        if (trigger == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val grid = 2.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
        val ox = size.width * origin.x
        val oy = size.height * origin.y
        val spread = size.width * 0.55f
        val lift = size.height * 0.45f
        val gravity = size.height * 1.1f
        // Fade out in the last 25% by dropping particles (dither-like), not by alpha.
        val visible = if (p < 0.75f) particles.size else (particles.size * (1f - p) / 0.25f).toInt()
        for (i in 0 until visible) {
            val pt = particles[i]
            val x = ox + pt.vx * spread * p
            val y = oy + pt.vy * lift * p + gravity * p * p * 0.5f
            val s = grid * pt.size
            val flat = pt.spin && ((p * 20f).toInt() % 2 == 0)
            drawRect(
                palette[pt.color],
                Offset(floor(x / grid) * grid, floor(y / grid) * grid),
                if (flat) Size(s, grid) else Size(s, s),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dither dissolve
// ---------------------------------------------------------------------------------------------

private val Bayer4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/**
 * Reveals the content through an ordered 4x4 Bayer dither: at progress 0 nothing shows, at 1
 * everything. [inverted] shows the complementary cells (use it for the outgoing content).
 */
fun Modifier.ditherReveal(progress: () -> Float, cell: Dp = 4.dp, inverted: Boolean = false): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val cellPx = cell.toPx().roundToInt().coerceAtLeast(1)
            val paint = Paint().apply { color = Color.Black }
            // brushes[level] erases the cells that must stay hidden at that level.
            val brushes = Array(17) { level ->
                val tile = ImageBitmap(cellPx * 4, cellPx * 4)
                val canvas = Canvas(tile)
                for (i in 0 until 16) {
                    val hidden = if (inverted) Bayer4[i] < level else Bayer4[i] >= level
                    if (hidden) {
                        val x = (i % 4) * cellPx
                        val y = (i / 4) * cellPx
                        canvas.drawRect(x.toFloat(), y.toFloat(), (x + cellPx).toFloat(), (y + cellPx).toFloat(), paint)
                    }
                }
                ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
            }
            onDrawWithContent {
                val level = (progress().coerceIn(0f, 1f) * 16f).roundToInt()
                val all = if (inverted) level == 0 else level == 16
                val none = if (inverted) level == 16 else level == 0
                if (none) return@onDrawWithContent
                drawContent()
                if (!all) drawRect(brushes[level], blendMode = BlendMode.DstOut)
            }
        }

/**
 * Crossfade replacement that swaps content with a dither dissolve. Works like `Crossfade`:
 * `DitherCrossfade(tab) { t -> Screen(t) }`.
 */
@Composable
fun <T : Any> DitherCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    durationMs: Int = 420,
    content: @Composable (T) -> Unit,
) {
    var current by remember { mutableStateOf(targetState) }
    var previous by remember { mutableStateOf<T?>(null) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(targetState) {
        if (targetState == current) return@LaunchedEffect
        previous = current
        current = targetState
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        previous = null
    }
    Box(modifier) {
        // Single call site so a state keeps its composition when it moves from "current" to "previous".
        listOfNotNull(previous, current).forEach { state ->
            key(state) {
                Box(Modifier.ditherReveal({ progress.value }, inverted = state != current)) { content(state) }
            }
        }
    }
}
