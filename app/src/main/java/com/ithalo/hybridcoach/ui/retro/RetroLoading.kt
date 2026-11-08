package com.ithalo.hybridcoach.ui.retro

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.delay

/**
 * Loading screen like a cartridge game: floating hourglass, "CARREGANDO..." with stepping dots, a
 * marching block bar and a random tip. Waits [showAfterMs] before appearing so fast loads don't flash.
 */
@Composable
fun RetroLoading(modifier: Modifier = Modifier.fillMaxSize(), showAfterMs: Long = 250, tip: Boolean = true) {
    var visible by remember { mutableStateOf(showAfterMs <= 0) }
    LaunchedEffect(Unit) {
        delay(showAfterMs)
        visible = true
    }
    if (!visible) return
    val c = Retro.colors
    val tips = stringArrayResource(R.array.loading_tips)
    val tipText = remember { tips.random() }
    val dots by rememberInfiniteTransition(label = "dots").animateFloat(
        0f, 4f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "dots",
    )
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        PixelSprite("hourglass", Modifier.pixelFloat(), pixel = 4.dp)
        Text(
            stringResource(R.string.loading) + ".".repeat(dots.toInt().coerceAtMost(3)).padEnd(3, ' '),
            style = Retro.type.button,
            color = c.ink,
        )
        RetroLoadingBar(Modifier.width(220.dp), segments = 12, color = c.reward)
        if (tip) {
            Text(
                stringResource(R.string.loading_tip_prefix) + " " + tipText,
                style = Retro.type.bodySmall,
                color = c.inkSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}
