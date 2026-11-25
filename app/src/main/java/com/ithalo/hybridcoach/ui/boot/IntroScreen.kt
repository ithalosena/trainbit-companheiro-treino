package com.ithalo.hybridcoach.ui.boot

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.game.CoinSpin
import com.ithalo.hybridcoach.ui.retro.CoinArt
import com.ithalo.hybridcoach.ui.retro.PixelCoin
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.delay

/** True only for the first screen of a process: the opening plays on a cold start, not when the app is reopened. */
object ColdStart {
    private var pending = true

    fun consume(): Boolean = pending.also { pending = false }
}

/**
 * Opening after the system splash, on the theme's background: the coin turns once in 12 steps,
 * lands with a 1 px hop and a glint, "TrainBit.exe" is typed with a blinking cursor. At most 1.8 s, skipped by a tap;
 * with animations off in the system, the still coin for 300 ms. It is drawn over the app, which loads beneath it.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val name = stringResource(R.string.product_name)
    var step by remember { mutableIntStateOf(0) }
    var hop by remember { mutableIntStateOf(0) }
    var glint by remember { mutableStateOf(false) }
    var typed by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableStateOf(false) }
    val finished = remember { booleanArrayOf(false) }
    fun finish() {
        if (finished[0]) return
        finished[0] = true
        done()
    }
    LaunchedEffect(Unit) {
        if (still) {
            delay(300)
            finish()
            return@LaunchedEffect
        }
        for (s in 1..CoinSpin.STEPS) { // 1 080 ms
            delay(CoinSpin.STEP_MS)
            step = s
        }
        hop = 1 // 180 ms: hop and glint
        delay(70)
        hop = 0
        glint = true
        delay(110)
        glint = false
        for (i in 1..name.length) { // about 260 ms
            typed = i
            delay(22)
        }
        repeat(2) { // 240 ms
            cursor = true
            delay(60)
            cursor = false
            delay(60)
        }
        finish()
    }
    val pixel = (288f / 108f * 2f).dp
    val coinHalf = pixel * CoinArt.SIZE / 2
    Box(
        Modifier
            .fillMaxSize()
            .background(Retro.colors.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { finish() }
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        PixelCoin(step, pixel, Modifier.offset { IntOffset(0, -(pixel * hop).roundToPx()) }, glint = glint)
        // Below the coin without moving it: the coin stays where the splash left it.
        Text(
            name.take(typed) + if (cursor) "_" else " ",
            Modifier.padding(top = coinHalf * 2 + 32.dp),
            style = Retro.type.button.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 1.sp),
            color = Retro.colors.ink,
        )
    }
}
