package com.ithalo.hybridcoach.media

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * The music the player listens to while training: play/pause and next as media keys (the same a Bluetooth
 * headset sends, no permission needed) and whether some app is playing.
 *
 * It does not read the song name on purpose: that needs a notification listener, and Play Protect blocks
 * sideloaded apps that declare one (it blocked the 0.14.0 install). Revisit when the app is on the Play Store.
 */
object NowPlaying {
    private const val POLL_MS = 1000L

    fun playPause(context: Context) = key(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    fun next(context: Context) = key(context, KeyEvent.KEYCODE_MEDIA_NEXT)

    fun isPlaying(context: Context): Boolean = context.getSystemService(AudioManager::class.java).isMusicActive

    private fun key(context: Context, code: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    /** Whether music is playing, checked every second while the screen is visible. */
    @Composable
    fun rememberPlaying(): State<Boolean> {
        val context = LocalContext.current
        val state = remember { mutableStateOf(isPlaying(context)) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    state.value = isPlaying(context)
                    delay(POLL_MS)
                }
            }
        }
        return state
    }
}
