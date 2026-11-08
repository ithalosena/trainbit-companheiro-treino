package com.ithalo.hybridcoach.ui.retro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.mix
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Pixel drawing primitives
// ---------------------------------------------------------------------------------------------

/** [dp] rounded to whole device pixels (at least 1) so lines stay crisp. */
internal fun Density.px(dp: Dp): Float = dp.toPx().roundToInt().coerceAtLeast(1).toFloat()

/** Rectangle outline made of 4 filled rects (crisper than a stroked path). */
internal fun DrawScope.strokeRectPx(color: Color, topLeft: Offset, size: Size, w: Float) {
    drawRect(color, topLeft, Size(size.width, w))
    drawRect(color, Offset(topLeft.x, topLeft.y + size.height - w), Size(size.width, w))
    drawRect(color, Offset(topLeft.x, topLeft.y + w), Size(w, size.height - 2 * w))
    drawRect(color, Offset(topLeft.x + size.width - w, topLeft.y + w), Size(w, size.height - 2 * w))
}

/**
 * Flat pixel box: [fill] and a thin [border] with clipped (1-pixel round) corners. When [raised] it
 * gets a [light] highlight row under the top edge and a [dark] ledge at the bottom, like the keys
 * of a handheld console; [heavy] doubles the ledge (border colour + dark) for primary keys.
 * Sunken boxes are just filled and outlined.
 */
internal fun DrawScope.drawBevelBox(
    fill: Color,
    border: Color,
    light: Color,
    dark: Color,
    raised: Boolean,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    borderWidth: Dp = 2.dp,
    bevelWidth: Dp = 2.dp,
    heavy: Boolean = false,
) {
    val b = px(borderWidth)
    val v = px(bevelWidth)
    val ledge = if (raised && heavy) v * 2 else if (raised) v else 0f
    val body = Size(size.width, size.height - ledge)
    drawRect(fill, topLeft, body)
    if (raised && body.height > 2 * b + 2 * v) {
        drawRect(light, Offset(topLeft.x + b, topLeft.y + b), Size(body.width - 2 * b, v))
    }
    strokeRectRounded(border, topLeft, body, b)
    if (ledge > 0f) {
        if (heavy) {
            drawRect(border, Offset(topLeft.x + b, topLeft.y + body.height), Size(body.width - 2 * b, v))
            drawRect(dark, Offset(topLeft.x + 2 * b, topLeft.y + body.height + v), Size(body.width - 4 * b, v))
        } else {
            drawRect(dark, Offset(topLeft.x + b, topLeft.y + body.height), Size(body.width - 2 * b, v))
        }
    }
}

/** Outline without its four corner pixels, so boxes read as slightly rounded pixel art. */
internal fun DrawScope.strokeRectRounded(color: Color, topLeft: Offset, size: Size, w: Float) {
    drawRect(color, Offset(topLeft.x + w, topLeft.y), Size(size.width - 2 * w, w))
    drawRect(color, Offset(topLeft.x + w, topLeft.y + size.height - w), Size(size.width - 2 * w, w))
    drawRect(color, Offset(topLeft.x, topLeft.y + w), Size(w, size.height - 2 * w))
    drawRect(color, Offset(topLeft.x + size.width - w, topLeft.y + w), Size(w, size.height - 2 * w))
}

/** 4×4 ordered (Bayer) dither thresholds, 0..15. */
private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/**
 * Title-bar fill like the XP-style windows of the references: solid [color] up to [solidPx], then an
 * ordered-dither fade to transparent over [fadePx]. Drawn on a coarse grid of [cell] (2dp) pixels.
 */
internal fun DrawScope.drawDitherFade(color: Color, solidPx: Float, fadePx: Float, cellDp: Dp = 2.dp) {
    val cell = px(cellDp)
    val cols = ceil(size.width / cell).toInt()
    val rows = ceil(size.height / cell).toInt()
    val solidEnd = (solidPx / cell)
    val fade = (fadePx / cell).coerceAtLeast(1f)
    drawRect(color, Offset.Zero, Size(minOf(solidPx, size.width), size.height))
    for (x in solidEnd.toInt().coerceAtLeast(0) until cols) {
        val f = 1f - (x - solidEnd) / fade
        if (f <= 0f) break
        val level = (f * 16f)
        for (y in 0 until rows) {
            if (level > BAYER[(y % 4) * 4 + (x % 4)]) {
                drawRect(color, Offset(x * cell, y * cell), Size(cell, minOf(cell, size.height - y * cell)))
            }
        }
    }
}

/** Dashed 2dp square outline (HUD and top-bar buttons). */
fun Modifier.dashedOutline(color: Color): Modifier = drawBehind {
    val w = px(2.dp)
    val dash = px(4.dp)
    var x = 0f
    while (x < size.width) {
        val len = minOf(dash, size.width - x)
        drawRect(color, Offset(x, 0f), Size(len, w))
        drawRect(color, Offset(x, size.height - w), Size(len, w))
        x += dash * 2
    }
    var y = 0f
    while (y < size.height) {
        val len = minOf(dash, size.height - y)
        drawRect(color, Offset(0f, y), Size(w, len))
        drawRect(color, Offset(size.width - w, y), Size(w, len))
        y += dash * 2
    }
}

/** 2dp hard pixel border drawn on top of the background, behind children. */
fun Modifier.pixelBorder(color: Color, width: Dp = 2.dp): Modifier =
    drawBehind { strokeRectPx(color, Offset.Zero, size, px(width)) }

/**
 * Small ledge under a surface (no blur, no sideways offset). Put it before `background` in the
 * chain; it draws outside bounds. Half of [offset] so surfaces barely lift off the paper.
 */
fun Modifier.hardShadow(color: Color, offset: Dp = 4.dp): Modifier =
    drawBehind { val o = px(offset / 2); drawRect(color, Offset(0f, o), size) }

/**
 * Dashed corner brackets drawn [gap] outside the bounds, like the frame marks of the reference
 * art: a frame suggested by its corners instead of a full border.
 */
fun Modifier.cornerMarks(color: Color, gap: Dp = 6.dp, arm: Dp = 14.dp): Modifier = drawBehind {
    val w = px(2.dp)
    val g = px(gap)
    val a = px(arm)
    val dash = px(2.dp)
    val l = -g
    val t = -g
    val r = size.width + g - w
    val bt = size.height + g - w
    fun hLine(x: Float, y: Float, dir: Int) {
        var d = 0f
        while (d < a) { drawRect(color, Offset(if (dir > 0) x + d else x - d - dash + w, y), Size(dash, w)); d += dash * 2 }
    }
    fun vLine(x: Float, y: Float, dir: Int) {
        var d = 0f
        while (d < a) { drawRect(color, Offset(x, if (dir > 0) y + d else y - d - dash + w), Size(w, dash)); d += dash * 2 }
    }
    hLine(l, t, 1); vLine(l, t, 1)
    hLine(r, t, -1); vLine(r, t, 1)
    hLine(l, bt, 1); vLine(l, bt, -1)
    hLine(r, bt, -1); vLine(r, bt, -1)
}

/**
 * Raised panel background, or a sunken well. Wells (icon slots, chart areas) are just a tinted fill
 * with no outline, so they never add a second frame inside a card.
 */
fun Modifier.bevel(fill: Color, border: Color, light: Color, dark: Color, raised: Boolean = true): Modifier =
    drawBehind { if (raised) drawBevelBox(fill, border, light, dark, raised = true) else drawRect(fill) }

/** Subtle repeating dither dot pattern (checker of single pixels). Cached as a shader tile. */
fun Modifier.ditherDots(color: Color, spacing: Dp = 6.dp): Modifier = drawWithCache {
    val dot = 1.dp.toPx().roundToInt().coerceAtLeast(1)
    val step = spacing.toPx().roundToInt().coerceAtLeast(dot * 2)
    val tile = ImageBitmap(step * 2, step * 2)
    val paint = Paint().apply { this.color = color }
    Canvas(tile).apply {
        drawRect(0f, 0f, dot.toFloat(), dot.toFloat(), paint)
        drawRect(step.toFloat(), step.toFloat(), (step + dot).toFloat(), (step + dot).toFloat(), paint)
    }
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(brush) }
}

// ---------------------------------------------------------------------------------------------
// Background
// ---------------------------------------------------------------------------------------------

/**
 * Screen background: paper colour + dither dots. [scanlines] adds very faint CRT lines with a slow
 * rolling band over the content (off by default).
 */
@Composable
fun RetroBackground(
    modifier: Modifier = Modifier,
    scanlines: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Retro.colors
    val overlay = if (scanlines) {
        val roll by rememberInfiniteTransition(label = "scan").animateFloat(
            0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "roll",
        )
        val line = c.ink.copy(alpha = if (c.isDark) 0.06f else 0.05f)
        val band = (if (c.isDark) c.ink else Color.White).copy(alpha = 0.035f)
        Modifier.drawWithCache {
            val step = px(3.dp).toInt()
            val tile = ImageBitmap(1, step * 2).also {
                Canvas(it).drawRect(0f, 0f, 1f, px(1.dp), Paint().apply { color = line })
            }
            val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
            val bandH = px(48.dp)
            onDrawWithContent {
                drawContent()
                drawRect(brush)
                val y = (roll * (size.height + bandH)).roundToInt() - bandH
                drawRect(band, Offset(0f, y), Size(size.width, bandH))
            }
        }
    } else Modifier
    Box(modifier.background(c.background).then(overlay), content = content)
}

// ---------------------------------------------------------------------------------------------
// Window
// ---------------------------------------------------------------------------------------------

/**
 * Base component. By default a frameless section: a small caps title with a dotted rule, then the
 * content (cards inside it carry the only border, so frames never nest). With [framed] it becomes a
 * panel with a thin border, roomy padding and dashed corner marks (dialogs, overlays).
 * With [animateOpen] it zooms in as an expanding outline, then fades its content in.
 */
@Composable
fun RetroWindow(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    active: Boolean = true,
    animateOpen: Boolean = true,
    framed: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Retro.colors
    // Static previews would otherwise capture the first (invisible) frame.
    val start = if (animateOpen && !LocalInspectionMode.current) 0f else 1f
    val zoom = remember { Animatable(start) }
    val fade = remember { Animatable(start) }
    LaunchedEffect(Unit) {
        if (zoom.value < 1f) {
            zoom.animateTo(1f, tween(220, easing = LinearEasing))
            fade.animateTo(1f, tween(160, easing = LinearEasing))
        }
    }
    if (framed) {
        // XP-style window: hard drop shadow, thin border, dithered title bar with the window buttons.
        Column(
            modifier
                .drawWithContent {
                    val p = zoom.value
                    if (p >= 1f) drawContent() else drawZoomOutline(p, c.inkFaint)
                }
                .drawBehind {
                    val o = px(4.dp)
                    drawRect(c.shadow, Offset(o, o), size)
                }
                .background(c.window)
                .pixelBorder(c.border),
        ) {
            RetroTitleStrip(title, Modifier.padding(2.dp), onClose = onClose, windowButtons = true)
            Column(
                Modifier.graphicsLayer { alpha = fade.value }.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
        return
    }
    Column(
        modifier.drawWithContent {
            val p = zoom.value
            if (p >= 1f) drawContent() else drawZoomOutline(p, c.inkFaint)
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RetroTitleBar(title = title, active = active, onClose = onClose, actions = actions)
        Column(
            Modifier.graphicsLayer { alpha = fade.value },
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * The v3 header: [text] in paper on a solid ink bar that fades out in ordered dither right after the
 * text (the XP title bars of the references). [trailing] sits on the clean paper end. With
 * [windowButtons] it shows the `_ □ X` boxes (X closes when [onClose] is set).
 */
@Composable
fun RetroTitleStrip(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClose: (() -> Unit)? = null,
    windowButtons: Boolean = false,
) {
    val c = Retro.colors
    var textWidth by remember { mutableIntStateOf(0) }
    Row(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .drawBehind {
                val solid = textWidth + px(28.dp)
                drawDitherFade(c.ink, solid, px(96.dp))
            }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(Locale.ROOT),
            Modifier.widthIn(max = 260.dp).onSizeChanged { textWidth = it.width },
            style = Retro.type.button,
            color = c.background,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing.uppercase(Locale.ROOT), Modifier.padding(end = 8.dp), style = Retro.type.label, color = c.ink, maxLines = 1)
        }
        if (windowButtons) {
            TitleBarBox(icon = "minus", ink = c.ink, bar = c.window, onClick = null)
            TitleBarBox(icon = "square", ink = c.ink, bar = c.window, onClick = null)
            TitleBarBox(icon = "x", ink = c.ink, bar = c.window, onClick = onClose)
        } else if (onClose != null) {
            TitleBarBox(icon = "x", ink = c.ink, bar = c.window, onClick = onClose)
        }
    }
}

/** Win 3.1 zoom: outlines growing from the centre in 6 hard steps, with a fainter trail. */
private fun DrawScope.drawZoomOutline(p: Float, color: Color) {
    val steps = 6f
    val s = ceil(p * steps) / steps
    val w = px(2.dp)
    for (k in 0..1) {
        val scale = s - k / steps
        if (scale <= 0f) continue
        val sz = Size((size.width * scale).coerceAtLeast(3 * w), (size.height * scale).coerceAtLeast(3 * w))
        val tl = Offset((size.width - sz.width) / 2f, (size.height - sz.height) / 2f)
        strokeRectPx(if (k == 0) color else color.copy(alpha = 0.4f), tl, sz, w)
    }
}

/** Section header used by [RetroWindow] and dialogs: small caps title and a dotted rule to the right. */
@Composable
fun RetroTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onClose: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Retro.colors
    val ink = if (active) c.ink else c.inkFaint
    val rule = Modifier
        .height(2.dp)
        .drawBehind {
            val w = px(2.dp)
            var x = 0f
            while (x < size.width) {
                drawRect(c.inkFaint, Offset(x, 0f), Size(w, w))
                x += w * 2
            }
        }
    // Centred caps label between two dotted rules ("··· SEMANA 2 DE 4 ···").
    Row(
        modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(rule.weight(1f))
        Text(
            title.uppercase(Locale.ROOT),
            Modifier.widthIn(max = 260.dp),
            style = Retro.type.label,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(rule.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        if (onClose != null) TitleBarBox(icon = "x", ink = ink, bar = c.window, onClick = onClose)
    }
}

/** Small square title-bar button (`_`, `□`, `X`). Decorative when [onClick] is null. */
@Composable
internal fun TitleBarBox(icon: String, ink: Color, bar: Color, onClick: (() -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val click = if (onClick != null) {
        Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
    } else Modifier
    // Real buttons get a 48dp touch target; decorative ones stay compact.
    val outer = if (onClick != null) Modifier.size(48.dp) else Modifier.padding(start = 2.dp)
    Box(outer.then(click), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (pressed) ink else bar)
                .pixelBorder(ink),
            contentAlignment = Alignment.Center,
        ) {
            if (icon == "square") {
                Box(Modifier.size(8.dp).pixelBorder(if (pressed) bar else ink))
            } else {
                PixelIcon(icon, tint = if (pressed) bar else ink, size = 10.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Divider
// ---------------------------------------------------------------------------------------------

/** Etched 2dp line (ink + highlight), or a dashed pixel line. */
@Composable
fun RetroDivider(modifier: Modifier = Modifier, dashed: Boolean = false) {
    val c = Retro.colors
    Spacer(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(2.dp)
            .drawBehind {
                val w = px(2.dp)
                val dash = if (dashed) px(6.dp) else w
                var x = 0f
                while (x < size.width) {
                    drawRect(c.inkFaint, Offset(x, 0f), Size(minOf(dash, size.width - x), w))
                    x += dash + w * 2
                }
            },
    )
}

/** Mix helper for components that need a lighter/darker shade of a fill. */
internal fun Color.shade(amount: Float): Color =
    if (amount >= 0f) mix(this, Color.White, amount) else mix(this, Color.Black, -amount)
