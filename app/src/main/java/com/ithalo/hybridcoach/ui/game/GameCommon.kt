package com.ithalo.hybridcoach.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.game.AchievementCatalog
import com.ithalo.hybridcoach.core.game.GameState
import com.ithalo.hybridcoach.core.game.ItemDef
import com.ithalo.hybridcoach.core.game.ItemKind
import com.ithalo.hybridcoach.core.game.ItemUnlock
import com.ithalo.hybridcoach.core.game.TrophyTier
import com.ithalo.hybridcoach.data.GameSeen
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.common.readableOn
import com.ithalo.hybridcoach.ui.retro.BadgeTier
import com.ithalo.hybridcoach.ui.retro.CountUpText
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.hardShadow
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.mix
import kotlin.math.roundToInt

fun TrophyTier.badge(): BadgeTier = BadgeTier.valueOf(name)

/** Game state and seen/equipped data shared by the game screens. */
@Composable
fun rememberGame(): Pair<GameState?, GameSeen?> {
    val container = appContainer()
    val state by container.gameState.collectAsStateWithLifecycle()
    val seen by container.settings.gameSeen.collectAsStateWithLifecycle(initialValue = null)
    return state to seen
}

/** The equipped item id, if it is (still) unlocked in [state]. */
fun GameState.equipped(id: String?): ItemDef? =
    id?.let { want -> items.firstOrNull { it.def.id == want && it.unlocked }?.def }

@Composable
fun itemKindLabel(kind: ItemKind) = stringResource(
    when (kind) {
        ItemKind.TITLE -> R.string.game_kind_title
        ItemKind.FRAME -> R.string.game_kind_frame
        ItemKind.STICKER -> R.string.game_kind_sticker
    },
)

/** How an item is unlocked, without spoiling hidden trophies. */
@Composable
fun unlockText(def: ItemDef): String = when (val u = def.unlock) {
    is ItemUnlock.Level -> stringResource(R.string.game_unlock_level, u.level)
    is ItemUnlock.Achievement -> {
        val a = AchievementCatalog.all.firstOrNull { it.id == u.id }
        if (a == null || a.hidden) stringResource(R.string.game_unlock_hidden) else stringResource(R.string.game_unlock_trophy, a.title)
    }
}

@Composable
fun itemTint(kind: ItemKind): Color {
    val c = Retro.colors
    return when (kind) {
        ItemKind.TITLE -> c.accent2
        ItemKind.FRAME -> c.gold
        ItemKind.STICKER -> c.accent
    }
}

// ---------------------------------------------------------------------------------------------
// Window frames (cosmetic items)
// ---------------------------------------------------------------------------------------------

/** Visual styles for the FRAME items; unknown/none = classic. */
enum class FrameStyle { CLASSIC, DOTTED, STRIPED, FLAMES, SILVER, GOLD, PLATINUM }

fun frameStyleOf(itemId: String?): FrameStyle = when (itemId) {
    "frame_dotted" -> FrameStyle.DOTTED
    "frame_striped" -> FrameStyle.STRIPED
    "frame_flames" -> FrameStyle.FLAMES
    "frame_silver" -> FrameStyle.SILVER
    "frame_gold" -> FrameStyle.GOLD
    "frame_platinum" -> FrameStyle.PLATINUM
    else -> FrameStyle.CLASSIC
}

private class FrameColors(val border: Color, val bar: Color, val stripe: Color, val double: Boolean, val dashed: Boolean, val icon: String?)

@Composable
private fun frameColors(style: FrameStyle): FrameColors {
    val c = Retro.colors
    return when (style) {
        FrameStyle.CLASSIC -> FrameColors(c.border, c.titleBar, c.titleBarInk.copy(alpha = 0.45f), double = false, dashed = false, icon = null)
        FrameStyle.DOTTED -> FrameColors(c.border, c.window, c.inkFaint, double = false, dashed = true, icon = null)
        FrameStyle.STRIPED -> FrameColors(c.border, c.titleBar, c.accent, double = false, dashed = false, icon = "star")
        FrameStyle.FLAMES -> FrameColors(c.accent, c.accent, mix(c.accent, c.gold, 0.6f), double = false, dashed = false, icon = "flame")
        FrameStyle.SILVER -> FrameColors(c.silver, mix(c.silver, c.window, 0.25f), c.window.copy(alpha = 0.5f), double = true, dashed = false, icon = "medal")
        FrameStyle.GOLD -> FrameColors(c.gold, c.gold, mix(c.gold, c.window, 0.5f), double = true, dashed = false, icon = "trophy")
        FrameStyle.PLATINUM -> FrameColors(c.platinum, c.platinum, mix(c.platinum, c.window, 0.55f), double = true, dashed = false, icon = "crown")
    }
}

/** Dashed 2dp outline. */
private fun DrawScope.dashedRect(color: Color, w: Float, dash: Float) {
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

private fun DrawScope.solidRect(color: Color, inset: Float, w: Float) {
    val tl = Offset(inset, inset)
    val s = Size(size.width - 2 * inset, size.height - 2 * inset)
    drawRect(color, tl, Size(s.width, w))
    drawRect(color, Offset(tl.x, tl.y + s.height - w), Size(s.width, w))
    drawRect(color, Offset(tl.x, tl.y), Size(w, s.height))
    drawRect(color, Offset(tl.x + s.width - w, tl.y), Size(w, s.height))
}

/** A window whose border and title bar follow the equipped FRAME item. */
@Composable
fun FramedWindow(title: String, style: FrameStyle, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Retro.colors
    val f = frameColors(style)
    val pad = if (f.double) 6.dp else 2.dp
    Column(
        modifier
            .background(c.window)
            .drawBehind {
                val w = 2.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
                when {
                    f.dashed -> dashedRect(f.border, w, w * 3)
                    f.double -> {
                        solidRect(f.border, 0f, w)
                        solidRect(c.border, w * 2, w)
                    }
                    else -> solidRect(f.border, 0f, w)
                }
            }
            .padding(pad),
    ) {
        // Plain header (no bar fill): the frame item shows in the outline and its corner icons.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            f.icon?.let { PixelIcon(it, Modifier.padding(end = 6.dp), tint = f.border, size = 16.dp) }
            Text(
                title.uppercase(PT_BR),
                Modifier.widthIn(max = 220.dp),
                style = Retro.type.label,
                color = c.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            f.icon?.let { PixelIcon(it, Modifier.padding(start = 6.dp), tint = f.border, size = 16.dp) }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** HUD tile like `StatTile`, with a number that counts up. [format] turns the raw int into text. */
@Composable
fun CountStatTile(icon: String, value: Int, label: String, modifier: Modifier = Modifier, tint: Color = Retro.colors.accent2, format: (Int) -> String = { it.toString() }) {
    val c = Retro.colors
    Column(
        modifier
            .defaultMinSize(minWidth = 96.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PixelIcon(icon, tint = tint, size = 24.dp)
        CountUpText(target = value, style = Retro.type.heading, color = c.ink, format = format)
        Text(label, style = Retro.type.label.copy(fontSize = 10.sp), color = c.inkSoft, maxLines = 1)
    }
}
