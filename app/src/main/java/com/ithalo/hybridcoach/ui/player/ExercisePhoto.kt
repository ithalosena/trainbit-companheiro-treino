package com.ithalo.hybridcoach.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.RetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Two photos of an exercise (start and end of the movement) as tone levels, made by tools/exercises/build_photos.py. */
class ExerciseFrames(val width: Int, val height: Int, val levels: Int, val frames: List<ByteArray>)

/**
 * Reads assets/exercise_frames/<id>.png: the two frames side by side, tone i stored as gray i * 255 / (LEVELS - 1).
 * The app paints the tones with the theme colours, so the photos follow the palette and the dark mode.
 */
object ExercisePhotos {
    private const val TAG = "ExercisePhotos"
    private const val DIR = "exercise_frames"
    const val LEVELS = 5
    const val FRAME_MS = 800L

    @Volatile
    private var available: Set<String>? = null
    private val cache = HashMap<String, ExerciseFrames?>()

    /** Exercise ids that have photos (read once from the assets). */
    fun ids(context: Context): Set<String> = available ?: runCatching {
        context.assets.list(DIR).orEmpty().filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }.toSet()
    }.getOrDefault(emptySet()).also { available = it }

    fun load(context: Context, id: String): ExerciseFrames? = synchronized(cache) {
        cache.getOrPut(id) {
            runCatching {
                val bmp = context.assets.open("$DIR/$id.png").use { BitmapFactory.decodeStream(it) } ?: error("not decodable")
                try {
                    val w = bmp.width / 2
                    val h = bmp.height
                    val px = IntArray(w * h)
                    List(2) { f ->
                        bmp.getPixels(px, 0, w, f * w, 0, w, h)
                        ByteArray(w * h) { i -> ((px[i] and 0xFF) * (LEVELS - 1) / 255f + 0.5f).toInt().coerceIn(0, LEVELS - 1).toByte() }
                    }.let { ExerciseFrames(w, h, LEVELS, it) }
                } finally {
                    bmp.recycle()
                }
            }.onFailure { Log.w(TAG, "No photo for $id", it) }.getOrNull()
        }
    }
}

/** Dark to light, in the theme: ink to highlight in light mode; on the dark screen, background to ink (never a negative). */
private fun RetroColors.photoRamp(levels: Int): IntArray {
    val dark = if (isDark) background else ink
    val light = if (isDark) ink else spriteHighlight
    return IntArray(levels) { lerp(dark, light, it / (levels - 1f)).toArgb() }
}

private fun bitmapOf(f: ExerciseFrames, frame: Int, ramp: IntArray): ImageBitmap {
    val cells = f.frames[frame]
    val px = IntArray(cells.size) { ramp[cells[it].toInt()] }
    return Bitmap.createBitmap(px, f.width, f.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * The exercise as a 2-frame pixel animation (start and end of the movement), in the theme's tones. Still on
 * the end frame when animations are off in the system. Returns without drawing when the photos are missing.
 */
@Composable
fun ExercisePhoto(exerciseId: String, exerciseName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var frames by remember(exerciseId) { mutableStateOf<ExerciseFrames?>(null) }
    LaunchedEffect(exerciseId) { frames = withContext(Dispatchers.IO) { ExercisePhotos.load(context, exerciseId) } }
    val f = frames ?: return
    val colors = Retro.colors
    val images = remember(f, colors) {
        val ramp = colors.photoRamp(f.levels)
        List(f.frames.size) { bitmapOf(f, it, ramp) }
    }
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var index by remember(exerciseId) { mutableIntStateOf(if (still) images.size - 1 else 0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(exerciseId, still) {
        if (still) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(ExercisePhotos.FRAME_MS)
                index = (index + 1) % images.size
            }
        }
    }
    val description = stringResource(R.string.player_photo_desc, exerciseName)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(f.width / f.height.toFloat())
                .semantics { contentDescription = description },
        ) {
            drawImage(
                images[index],
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(f.width, f.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
        Text(stringResource(R.string.player_photo_credit), style = Retro.type.label, color = Retro.colors.inkFaint)
    }
}
