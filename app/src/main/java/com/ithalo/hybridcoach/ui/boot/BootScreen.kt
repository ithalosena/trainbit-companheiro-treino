package com.ithalo.hybridcoach.ui.boot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.retro.PixelSprite
import com.ithalo.hybridcoach.ui.retro.RetroLoadingBar
import com.ithalo.hybridcoach.ui.retro.ditherReveal
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.delay
import kotlin.math.floor

/** Eight hard steps, like a sprite moving at a low frame rate. */
private val Steps8 = Easing { x -> floor(x * 8f) / 8f }

/**
 * Cartridge boot screen shown once per launch: the logo drops in steps (the handheld "ding" logo),
 * then the name dithers in with the tagline and a short loading bar. Tap anywhere to skip.
 */
@Composable
fun BootScreen(onDone: () -> Unit) {
    val c = Retro.colors
    val done by rememberUpdatedState(onDone)
    val drop = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val finished = remember { booleanArrayOf(false) }
    fun finish() {
        if (finished[0]) return
        finished[0] = true
        done()
    }
    LaunchedEffect(Unit) {
        drop.animateTo(1f, tween(720, easing = Steps8))
        name.animateTo(1f, tween(420, easing = LinearEasing))
        delay(900)
        finish()
    }
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { finish() }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PixelSprite(
                "logo",
                Modifier.graphicsLayer { translationY = -(1f - drop.value) * 520.dp.toPx() },
                pixel = 3.dp,
            )
            Column(
                Modifier.ditherReveal({ name.value }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.product_name),
                    style = Retro.type.button.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 2.sp),
                    color = c.ink,
                )
                Text(stringResource(R.string.boot_tagline), style = Retro.type.label, color = c.inkSoft)
            }
            RetroLoadingBar(Modifier.padding(horizontal = 72.dp), segments = 10, color = c.reward)
        }
        Text(
            stringResource(R.string.boot_footer, version),
            Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            style = Retro.type.label.copy(fontSize = 10.sp),
            color = c.inkFaint,
        )
    }
}
