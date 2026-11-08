package com.ithalo.hybridcoach.ui.today

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ithalo.hybridcoach.ui.retro.PixelSprite
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.delay

/** What the Today screen's illustration shows. */
enum class HeroScene { STRENGTH, RUN, DONE_STRENGTH, DONE_RUN, REST }

/** One frame index that loops 0 until [count] every [stepMs], only while the screen is visible; stuck at 0 with animations off. */
@Composable
private fun rememberStepFrame(count: Int, stepMs: Long): Int {
    val context = LocalContext.current
    val off = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var frame by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(count, stepMs, off) {
        if (off) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(stepMs)
                frame = (frame + 1) % count
            }
        }
    }
    return frame
}

/**
 * The Today screen's animated illustration, picked by the situation: the dumbbell lifting (strength to do), the
 * shoe hopping with dust (run to do), the trophy sparkling (strength done), the medal swinging (run done) and
 * the hourglass turning with "z"s (rest day). Moves in whole pixel steps, like the rest of the pixel art.
 */
@Composable
fun TodayHero(scene: HeroScene, modifier: Modifier = Modifier) {
    val c = Retro.colors
    val frame = rememberStepFrame(count = 16, stepMs = 160)
    Box(modifier.fillMaxWidth().height(116.dp), contentAlignment = Alignment.Center) {
        when (scene) {
            HeroScene.STRENGTH -> {
                // Up in 2 dp steps, a short hold at the top with a glint on the plates, then down.
                val lift = intArrayOf(0, 0, 2, 4, 6, 8, 10, 10, 10, 10, 8, 6, 4, 2, 0, 0)[frame]
                PixelSprite("dumbbell", Modifier.offset(y = (4 - lift).dp), pixel = 2.dp)
                if (frame in 6..9) Sparkles(c.ink, listOf(-0.36f to -0.34f, 0.36f to -0.36f), big = frame % 2 == 0)
            }
            HeroScene.RUN -> {
                val hop = intArrayOf(0, 6, 10, 6, 0, 0, 6, 10, 6, 0, 0, 6, 10, 6, 0, 0)[frame]
                PixelSprite("shoe", Modifier.offset(x = 6.dp, y = (8 - hop).dp), pixel = 6.dp)
                Canvas(Modifier.fillMaxSize()) {
                    val u = 3.dp.toPx()
                    val cx = size.width / 2
                    val ground = size.height / 2 + 32.dp.toPx()
                    // dust puffs drifting back while the shoe is up
                    if (hop > 0) for (k in 0..2) {
                        val back = (frame % 5 + k * 3) * u
                        square(c.inkSoft, cx - 44.dp.toPx() - back, ground - k * u, u)
                    }
                    // speed lines
                    for (k in 0..2) {
                        val y = size.height / 2 - 14.dp.toPx() + k * 12.dp.toPx()
                        val shift = ((frame + k) % 3) * u
                        drawRect(c.inkSoft, Offset(cx - 86.dp.toPx() + shift, y), Size(u * (5 - k), u))
                    }
                }
            }
            HeroScene.DONE_STRENGTH -> {
                val bob = intArrayOf(0, 0, 1, 1, 2, 2, 1, 1)[frame % 8]
                PixelSprite("trophy", Modifier.offset(y = (-bob * 2).dp), pixel = 6.dp, reward = true)
                Sparkles(c.reward, sparkleRing(frame), big = frame % 4 < 2)
            }
            HeroScene.DONE_RUN -> {
                // A swinging medal, hung from its ribbon.
                val angle = floatArrayOf(0f, 6f, 10f, 6f, 0f, -6f, -10f, -6f)[frame % 8]
                PixelSprite(
                    "medal",
                    Modifier.graphicsLayer { rotationZ = angle; transformOrigin = TransformOrigin(0.5f, 0f) },
                    pixel = 8.dp,
                    reward = true,
                )
                Sparkles(c.reward, sparkleRing(frame + 3), big = frame % 4 < 2)
            }
            HeroScene.REST -> {
                // The hourglass waits, then turns over; "z"s float up.
                val angle = if (frame < 12) 0f else (frame - 11) * 45f
                PixelSprite("hourglass", Modifier.graphicsLayer { rotationZ = angle }, pixel = 8.dp)
                Canvas(Modifier.fillMaxSize()) {
                    val u = 2.dp.toPx()
                    for (k in 0..1) {
                        val rise = ((frame + k * 8) % 16) * u
                        zLetter(c.inkSoft, size.width / 2 + 48.dp.toPx() + k * 14.dp.toPx(), size.height / 2 + 10.dp.toPx() - rise - k * 12.dp.toPx(), u)
                    }
                }
            }
        }
    }
}

/** Positions (fraction of the half width/height from the centre) of the sparkles around a reward, shifting each frame. */
private fun sparkleRing(frame: Int): List<Pair<Float, Float>> {
    val all = listOf(-0.42f to -0.5f, 0.4f to -0.42f, -0.5f to 0.15f, 0.48f to 0.22f, -0.2f to -0.75f, 0.22f to 0.62f)
    return all.filterIndexed { i, _ -> (i + frame / 2) % 3 != 0 }
}

/** Little "+" stars, each pixel 3 dp. */
@Composable
private fun Sparkles(color: Color, at: List<Pair<Float, Float>>, big: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val u = 3.dp.toPx()
        at.forEach { (fx, fy) ->
            val x = size.width / 2 + fx * 130.dp.toPx()
            val y = size.height / 2 + fy * 58.dp.toPx()
            square(color, x, y, u)
            val arms = if (big) 2 else 1
            for (a in 1..arms) {
                square(color, x - a * u, y, u); square(color, x + a * u, y, u)
                square(color, x, y - a * u, u); square(color, x, y + a * u, u)
            }
        }
    }
}

private fun DrawScope.square(color: Color, x: Float, y: Float, u: Float) {
    drawRect(color, Offset((x / u).toInt() * u, (y / u).toInt() * u), Size(u, u))
}

/** A 4x4 pixel "z". */
private fun DrawScope.zLetter(color: Color, x: Float, y: Float, u: Float) {
    val rows = listOf("1111", "..1.", ".1..", "1111")
    rows.forEachIndexed { r, row -> row.forEachIndexed { k, ch -> if (ch == '1') square(color, x + k * u, y + r * u, u) } }
}
