package com.ithalo.hybridcoach.ui.retro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.ithalo.hybridcoach.core.game.CoinSpin
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The app's coin (the launcher icon) drawn pixel by pixel, no filtering, at [step] of its spin
 * ([CoinSpin]: 12 steps a turn). The visible width is |cos| of the angle; side-on it is a 2 px gold bar, and the
 * back half of the turn is mirrored. [glint] adds a small gold sparkle on the rim. [pixelSize] is rounded to whole
 * device pixels so every coin pixel stays square and sharp.
 */
@Composable
fun PixelCoin(step: Int, pixelSize: Dp, modifier: Modifier = Modifier, glint: Boolean = false, grid: List<String> = CoinArt.GRID) {
    val colors = remember { CoinArt.COLORS.mapValues { Color(it.value) } }
    val gold = colors.getValue('G')
    val rows = remember(grid) { grid.indices.filter { y -> grid[y].any { it != '.' } } }
    Canvas(modifier.size(pixelSize * CoinArt.SIZE)) {
        val px = max(1, pixelSize.toPx().roundToInt()).toFloat()
        val n = CoinArt.SIZE
        val ox = (size.width - n * px) / 2
        val oy = (size.height - n * px) / 2
        val frame = CoinSpin.frame(step)
        if (frame.edge) {
            val top = rows.first()
            val bottom = rows.last()
            drawRect(gold, Offset(ox + (n / 2 - 1) * px, oy + top * px), Size(2 * px, (bottom - top + 1) * px))
            return@Canvas
        }
        val visible = max(2, (n * frame.scale).roundToInt())
        val start = (n - visible) / 2
        for (j in 0 until visible) {
            var src = floor((j + 0.5f) / visible * n).toInt().coerceIn(0, n - 1)
            if (frame.mirrored) src = n - 1 - src
            for (y in rows) {
                val c = grid[y][src]
                if (c == '.') continue
                drawRect(colors[c] ?: continue, Offset(ox + (start + j) * px, oy + y * px), Size(px, px))
            }
        }
        if (glint) {
            for ((x, y) in listOf(8 to 2, 7 to 3, 8 to 3, 9 to 3, 8 to 4)) {
                drawRect(gold, Offset(ox + x * px, oy + y * px), Size(px, px))
            }
        }
    }
}
