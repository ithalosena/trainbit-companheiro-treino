package com.ithalo.hybridcoach.ui.avatar

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.util.Log
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.avatar.AvatarAnimator
import com.ithalo.hybridcoach.core.avatar.AvatarEvent
import com.ithalo.hybridcoach.core.avatar.AvatarInput
import com.ithalo.hybridcoach.core.avatar.AvatarMood
import com.ithalo.hybridcoach.core.avatar.AvatarSprites
import com.ithalo.hybridcoach.core.avatar.AvatarState
import com.ithalo.hybridcoach.core.avatar.computeAvatarState
import com.ithalo.hybridcoach.core.game.ItemCatalog
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.RetroColors
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.time.LocalTime

private const val TAG = "Avatar"

/** One-shot avatar animations requested by other screens. A request is shown once, and only while fresh. */
class AvatarEvents {
    private var pending: Pair<AvatarEvent, Long>? = null

    @Synchronized
    fun post(event: AvatarEvent) {
        pending = event to SystemClock.elapsedRealtime()
    }

    /** The waiting event, if it is still fresh; taking it consumes it. */
    @Synchronized
    fun take(nowMs: Long): AvatarEvent? {
        val (event, at) = pending ?: return null
        pending = null
        return event.takeIf { nowMs - at <= FRESH_MS }
    }

    private companion object {
        const val FRESH_MS = 20_000L
    }
}

/** A calm pet with no tip, for screens that have no history to read yet (loading, onboarding). */
val NeutralAvatarState = AvatarState(AvatarMood.NORMAL, vitality = 50, fatigue = 0, tip = "")

/** The avatar's state for [input] at the current hour, refreshed on the hour while the app is open. */
@Composable
fun rememberAvatarState(input: AvatarInput?): AvatarState? {
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                hour = LocalTime.now().hour
                delay(60_000)
            }
        }
    }
    return remember(input, hour) { input?.let { computeAvatarState(it, hour) } }
}

/**
 * The art is drawn dark outline, then shade, base and highlight getting lighter. The theme's dark ramp is
 * the opposite (light ink, dark shades), which turned the face into a negative, so in dark mode the pet is
 * drawn on a light "screen" plate with the same dark-to-light order as in light mode.
 */
private class AvatarTones(val plate: Color?, val tones: Array<Color>)

private fun RetroColors.avatarTones(): AvatarTones =
    if (!isDark) {
        AvatarTones(null, arrayOf(Color.Transparent, ink, spriteMid, spriteLight, spriteHighlight))
    } else {
        AvatarTones(
            plate = ink,
            tones = arrayOf(Color.Transparent, background, spriteLight, spriteHighlight, lerp(ink, Color.White, 0.55f)),
        )
    }

private fun animationsOff(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * The pet avatar: [state] decides the mood, [stickerId] is the equipped sticker item (on the floor beside it).
 * The stage is wider than the pet so it can walk around. One 5 fps clock ([AvatarAnimator.TICK_MS]) drives it; it only runs while the screen is started, and the frame is read in the draw
 * phase so a new frame redraws without recomposing. With animations turned off in the system settings it
 * shows the still pose of the mood. [pixel] is dp per art pixel, rounded to whole device pixels.
 */
@Composable
fun AvatarView(
    state: AvatarState,
    stickerId: String?,
    modifier: Modifier = Modifier,
    pixel: Dp = 2.5.dp,
) {
    val context = LocalContext.current
    val container = appContainer()
    val colors = Retro.colors
    val look = remember(colors) { colors.avatarTones() }
    val density = LocalDensity.current
    val animator = remember { AvatarAnimator(state.mood, seed = (System.nanoTime() and 0xFFFF).toInt()) }
    var frame by remember { mutableStateOf(animator.frameAt(SystemClock.elapsedRealtime())) }
    var still by remember { mutableStateOf(animationsOff(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(state.mood, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            still = animationsOff(context)
            animator.setMood(state.mood, SystemClock.elapsedRealtime())
            if (still) {
                frame = animator.staticFrame()
                awaitCancellation()
            }
            while (true) {
                val now = SystemClock.elapsedRealtime()
                container.avatarEvents.take(now)?.let { animator.play(it, now) }
                frame = animator.frameAt(now)
                delay(AvatarAnimator.TICK_MS - now % AvatarAnimator.TICK_MS)
            }
        }
    }

    if (stickerId != null && !AvatarSprites.hasSticker(stickerId)) {
        LaunchedEffect(stickerId) { Log.w(TAG, "Sticker $stickerId has no avatar art; showing nothing for it") }
    }

    val cell = with(density) { pixel.toPx() }.toInt().coerceAtLeast(1)
    val width = with(density) { (cell * AvatarSprites.WIDTH).toDp() }
    val height = with(density) { (cell * AvatarSprites.HEIGHT).toDp() }
    val moodText = stringResource(
        when (state.mood) {
            AvatarMood.ENERGIZED -> R.string.avatar_desc_energized
            AvatarMood.NORMAL -> R.string.avatar_desc_normal
            AvatarMood.TIRED -> R.string.avatar_desc_tired
            AvatarMood.SLUGGISH -> R.string.avatar_desc_sluggish
            AvatarMood.SLEEPING -> R.string.avatar_desc_sleeping
        },
    )
    val stickerName = stickerId?.let { id -> ItemCatalog.all.firstOrNull { it.id == id }?.name }
    val description = if (stickerName != null) "$moodText " + stringResource(R.string.avatar_desc_sticker, stickerName) else moodText

    Canvas(
        modifier
            .size(width, height)
            .semantics { contentDescription = description }
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                if (!still) {
                    val now = SystemClock.elapsedRealtime()
                    animator.play(AvatarEvent.TAP, now)
                    frame = animator.frameAt(now)
                }
            },
    ) {
        look.plate?.let { drawRect(it) }
        val tones = AvatarSprites.compose(frame, stickerId)
        val w = AvatarSprites.WIDTH
        val c = cell.toFloat()
        for (y in 0 until AvatarSprites.HEIGHT) {
            var x = 0
            while (x < w) {
                val tone = tones[y * w + x].toInt()
                if (tone == 0) { x++; continue }
                var end = x + 1
                while (end < w && tones[y * w + end].toInt() == tone) end++
                drawRect(look.tones[tone], Offset(x * c, y * c), Size((end - x) * c, c))
                x = end
            }
        }
    }
}
