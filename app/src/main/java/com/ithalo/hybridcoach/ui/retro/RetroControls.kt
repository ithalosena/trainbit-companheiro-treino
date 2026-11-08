package com.ithalo.hybridcoach.ui.retro

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlin.math.floor
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Bevel surface shared by buttons, chips and clickable cards
// ---------------------------------------------------------------------------------------------

private class BevelStyle(
    val fill: Color,
    val light: Color,
    val dark: Color,
    val content: Color,
    val border: Color,
    val shadow: Color?,
    val heavy: Boolean = false,
)

/**
 * Raised bevel box that sinks 2dp (and loses its hard shadow) while pressed.
 * [stuckDown] draws it permanently pressed (selected chips/tabs).
 */
@Composable
private fun BevelSurface(
    onClick: (() -> Unit)?,
    enabled: Boolean,
    style: BevelStyle,
    modifier: Modifier,
    textStyle: TextStyle,
    contentPadding: PaddingValues,
    minWidth: Dp,
    minHeight: Dp,
    stuckDown: Boolean = false,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val click = if (onClick != null) {
        Modifier.clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
    } else Modifier
    Box(
        modifier
            .defaultMinSize(minWidth, minHeight)
            .drawBehind {
                val down = stuckDown || pressed
                val t = if (pressed) px(2.dp) else 0f
                translate(0f, t) { drawBevelBox(style.fill, style.border, style.light, style.dark, raised = !down, heavy = style.heavy) }
            }
            .then(click),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides style.content, LocalTextStyle provides textStyle) {
            Row(
                Modifier
                    .offset { if (pressed) IntOffset(0, 2.dp.roundToPx()) else IntOffset.Zero }
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

@Composable
private fun disabledStyle(): BevelStyle {
    val c = Retro.colors
    return BevelStyle(c.window, c.window, c.disabled, c.disabled, c.disabled, shadow = null)
}

/**
 * Primary action: paper key with an ink outline, a highlight row and a double ledge (ink + mid), like
 * the "$0.99" keys of the reference shop. Same parameters as Material `Button`.
 */
@Composable
fun RetroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Retro.colors
    val style = if (enabled) BevelStyle(c.window, c.bevelLight, c.shadow, c.ink, c.border, null, heavy = true) else disabledStyle()
    BevelSurface(onClick, enabled, style, modifier, Retro.type.button, ButtonPadding, 64.dp, 52.dp, content = content)
}

/** Secondary action: paper key, faint outline and a single ledge. Same parameters as Material `OutlinedButton`. */
@Composable
fun RetroOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Retro.colors
    val style = if (enabled) BevelStyle(c.window, c.window, c.shadow, c.ink, c.inkFaint, null) else disabledStyle()
    BevelSurface(onClick, enabled, style, modifier, Retro.type.button, ButtonPadding, 64.dp, 48.dp, content = content)
}

/**
 * Primary key split in two by a thin rule: the action on the left, its reward on the right
 * ("COMEÇAR TREINO | +50 XP"), like the price keys of the reference shop.
 */
@Composable
fun RetroRewardButton(
    label: String,
    reward: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = "play",
    enabled: Boolean = true,
) {
    val c = Retro.colors
    RetroButton(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled) {
        if (icon != null) PixelIcon(icon, size = 14.dp)
        Text(label, Modifier.weight(1f), maxLines = 1)
        Box(Modifier.size(width = 2.dp, height = 24.dp).drawBehind { drawRect(if (enabled) c.inkFaint else c.disabled) })
        Text(reward, Modifier.padding(start = 4.dp), maxLines = 1)
    }
}

/** Flat text action; inverts (ink block) while pressed. Same parameters as Material `TextButton`. */
@Composable
fun RetroTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Retro.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fg = when {
        !enabled -> c.disabled
        pressed -> c.window
        else -> c.ink
    }
    Box(
        modifier
            .defaultMinSize(48.dp, 40.dp)
            .drawBehind {
                if (pressed) drawRect(c.ink)
                else if (enabled) {
                    // Dotted underline, like a hyperlink in an old help file.
                    val w = px(2.dp)
                    var x = px(12.dp)
                    while (x < size.width - px(12.dp)) {
                        drawRect(c.inkFaint, Offset(x, size.height - px(8.dp)), Size(w, w))
                        x += w * 2
                    }
                }
            }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg, LocalTextStyle provides Retro.type.button) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** Square bevel icon button (40dp visual, 48dp touch). Same parameters as Material `IconButton`. */
@Composable
fun RetroIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = Retro.colors
    val style = if (enabled) BevelStyle(c.window, c.window, c.shadow, c.ink, c.border, null) else disabledStyle()
    BevelSurface(
        onClick, enabled, style, modifier.minimumInteractiveComponentSize().size(40.dp),
        Retro.type.button, PaddingValues(0.dp), 40.dp, 40.dp,
    ) { content() }
}

/**
 * The one framed surface: thin border, roomy padding, no shadow. Tappable cards darken slightly
 * while pressed. Never put a card inside another card.
 */
@Composable
fun RetroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Retro.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val click = if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier
    Column(
        modifier
            .drawBehind {
                drawRect(if (pressed) c.windowInset else c.window)
                strokeRectPx(c.border, Offset.Zero, size, px(2.dp))
            }
            .then(click)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides c.ink, LocalTextStyle provides Retro.type.body) { content() }
    }
}

// ---------------------------------------------------------------------------------------------
// Toggles
// ---------------------------------------------------------------------------------------------

/** Toggle tab: faint outline when off, filled when on. Same core parameters as Material `FilterChip`. */
@Composable
fun RetroFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Retro.colors
    val style = when {
        !enabled -> disabledStyle()
        selected -> BevelStyle(c.accent2, c.accent2, c.accent2, c.onAccent2, c.accent2, null)
        else -> BevelStyle(c.window, c.window, c.window, c.inkSoft, c.inkFaint, null)
    }
    BevelSurface(
        onClick, enabled, style, modifier, Retro.type.label,
        PaddingValues(horizontal = 14.dp, vertical = 8.dp), 48.dp, 40.dp,
        stuckDown = selected, role = Role.Checkbox,
    ) { label() }
}

/** Sunken square with a pixel check that wipes in. Same parameters as Material `Checkbox`. */
@Composable
fun RetroCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Retro.colors
    val reveal by animateFloatAsState(if (checked) 1f else 0f, tween(140, easing = LinearEasing), label = "check")
    val glyph = remember { pixelGlyph("check") }
    val ink = if (enabled) c.ink else c.disabled
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked, enabled = enabled, role = Role.Checkbox,
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onValueChange = onCheckedChange,
        )
    } else Modifier
    Box(modifier.minimumInteractiveComponentSize().then(toggle), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            drawBevelBox(c.windowInset, if (enabled) c.border else c.disabled, c.bevelLight, c.bevelDark, raised = false)
            val steps = floor(reveal * 8f) / 8f
            if (steps > 0f) {
                val inner = px(4.dp)
                val cell = floor((size.width - 2 * inner) / 16f).coerceAtLeast(1f)
                val origin = floor((size.width - cell * 16f) / 2f)
                clipRect(right = origin + cell * 16f * steps) {
                    drawPixelGlyph(glyph, ink, Offset(origin, origin), cell)
                }
            }
        }
    }
}

/** Pixel hardware switch: sunken track, sliding raised knob, I/O markings. Same parameters as Material `Switch`. */
@Composable
fun RetroSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Retro.colors
    val pos by animateFloatAsState(if (checked) 1f else 0f, tween(120, easing = LinearEasing), label = "knob")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked, enabled = enabled, role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onValueChange = onCheckedChange,
        )
    } else Modifier
    Box(modifier.minimumInteractiveComponentSize().then(toggle), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 56.dp, height = 28.dp)) {
            val on = enabled && checked
            val track = if (on) c.accent2 else c.windowInset
            val border = if (enabled) c.border else c.disabled
            drawBevelBox(track, border, c.bevelLight, c.bevelDark, raised = false)
            val b = px(2.dp)
            val pad = b * 2
            val knob = size.height - pad * 2
            val mark = if (on) c.onAccent2 else if (enabled) c.inkSoft else c.disabled
            val markH = px(10.dp)
            val my = (size.height - markH) / 2f
            if (checked) {
                // "I" on the left half.
                drawRect(mark, Offset(pad + (knob - b) / 2f, my), Size(b, markH))
            } else {
                strokeRectPx(mark, Offset(size.width - pad - knob + (knob - markH) / 2f, my), Size(markH, markH), b)
            }
            val travel = size.width - pad * 2 - knob
            val x = pad + (travel * pos).roundToInt()
            drawBevelBox(
                if (enabled) c.window else c.windowInset, border, c.bevelLight, c.bevelDark, raised = true,
                topLeft = Offset(x, pad), size = Size(knob, knob),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Text field
// ---------------------------------------------------------------------------------------------

/**
 * Sunken text field with a blinking block cursor. Parameter names follow Material
 * `OutlinedTextField`, with [label]/[supportingText] as plain strings.
 */
@Composable
fun RetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    supportingText: String? = null,
    readOnly: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = Retro.type.body,
) {
    val c = Retro.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (tfv.text == value) tfv else tfv.copy(text = value, selection = TextRange(value.length))
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Only animate while focused, so idle fields cost nothing.
    val blink = if (focused) {
        rememberInfiniteTransition(label = "cursor").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "blink",
        )
    } else null
    val fieldStyle = textStyle.copy(color = c.ink)
    val frame = when {
        isError -> c.danger
        focused -> c.accent2
        else -> c.border
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            Text(
                (if (focused) "> " else "") + label,
                style = Retro.type.label,
                color = if (isError) c.danger else if (focused) c.ink else c.inkSoft,
            )
        }
        BasicTextField(
            value = current,
            onValueChange = { tfv = it; if (it.text != value) onValueChange(it.text) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            textStyle = fieldStyle,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else maxLines.coerceAtLeast(minLines),
            minLines = minLines,
            interactionSource = interaction,
            cursorBrush = SolidColor(Color.Transparent),
            onTextLayout = { layout = it },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .drawBehind { drawBevelBox(c.windowInset, frame, c.bevelLight, c.bevelDark, raised = false) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clipToBounds()
                            .drawWithContent {
                                drawContent()
                                val l = layout ?: return@drawWithContent
                                if (blink == null || readOnly || !current.selection.collapsed || blink.value >= 0.5f) return@drawWithContent
                                val offset = current.selection.start.coerceIn(0, current.text.length)
                                val r = l.getCursorRect(offset)
                                val w = (fieldStyle.fontSize.toPx() * 0.45f).roundToInt().toFloat()
                                drawRect(c.ink.copy(alpha = 0.75f), Offset(r.left, r.top), Size(w, r.height))
                            },
                    ) { inner() }
                    if (trailing != null) {
                        CompositionLocalProvider(LocalContentColor provides c.inkSoft) { trailing() }
                    }
                }
            },
        )
        if (supportingText != null) {
            Text(supportingText, style = Retro.type.bodySmall, color = if (isError) c.danger else c.inkSoft)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------------------------

/** Segmented block progress bar; blocks fill one by one when [progress] changes. */
@Composable
fun RetroLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    segments: Int = 20,
    color: Color = Retro.colors.accent,
) {
    val c = Retro.colors
    val target = progress().coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, tween(600, easing = LinearEasing), label = "progress")
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        drawBevelBox(c.windowInset, c.border, c.bevelLight, c.bevelDark, raised = false)
        drawSegments(segments, color) { i -> i < (animated * segments).roundToInt() }
    }
}

/** Indeterminate loading bar: a group of blocks marches through the trough in hard steps. */
@Composable
fun RetroLoadingBar(modifier: Modifier = Modifier, segments: Int = 20, color: Color = Retro.colors.accent2) {
    val c = Retro.colors
    val t by rememberInfiniteTransition(label = "loading").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "march",
    )
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        drawBevelBox(c.windowInset, c.border, c.bevelLight, c.bevelDark, raised = false)
        val group = 5
        val head = floor(t * (segments + group)).toInt()
        drawSegments(segments, color) { i -> i in (head - group + 1)..head }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSegments(
    segments: Int,
    color: Color,
    filled: (Int) -> Boolean,
) {
    val inset = px(4.dp)
    val gap = px(2.dp)
    val w = size.width - inset * 2
    val segW = ((w - gap * (segments - 1)) / segments).coerceAtLeast(1f)
    val h = size.height - inset * 2
    for (i in 0 until segments) {
        if (!filled(i)) continue
        val x = inset + i * (w - segW) / (segments - 1).coerceAtLeast(1)
        drawRect(color, Offset(floor(x), inset), Size(floor(segW), h))
    }
}
