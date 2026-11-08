package com.ithalo.hybridcoach.ui.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.TextToSpeech
import com.ithalo.hybridcoach.HybridCoachApp
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.voice.CoachVoices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * pt-BR text to speech with the voice and speed chosen in Settings (best installed voice by default).
 * Anything spoken before the engine is ready is queued.
 *
 * Like the run voice, it speaks as "navigation guidance" and asks for transient audio focus that lets the
 * music duck, so it is heard over Spotify instead of at the music's level.
 */
class Speaker(context: Context) {
    private val _available = MutableStateFlow(true)
    /** False when the device has no pt-BR voice installed. */
    val available: StateFlow<Boolean> = _available

    @Volatile var enabled = true

    private val settings = (context.applicationContext as HybridCoachApp).container.settings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ready = false
    private val queued = ArrayDeque<String>()
    private var tts: TextToSpeech? = null

    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()
    private val pending = AtomicInteger(0)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                engine.setAudioAttributes(attributes)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = finished()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finished()
                })
                val result = engine.setLanguage(PT_BR)
                val languageOk = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                // Stay "not ready" until the chosen voice is set, so nothing is said in the default voice.
                scope.launch {
                    val s = settings.current()
                    ready = CoachVoices.apply(engine, s.voiceName, s.voiceRate) || languageOk
                    _available.value = ready
                    flushQueue()
                }
            } else {
                _available.value = false
            }
        }
    }

    private fun flushQueue() = synchronized(queued) {
        while (queued.isNotEmpty()) say(queued.removeFirst())
    }

    fun speak(text: String) {
        if (!enabled) return
        if (!ready) {
            synchronized(queued) { if (tts != null && queued.size < 5) queued.addLast(text) }
            return
        }
        say(text)
    }

    private fun say(text: String) {
        val engine = tts
        if (!ready || engine == null) return
        if (pending.getAndIncrement() == 0) audio.requestAudioFocus(focusRequest)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        engine.speak(text, TextToSpeech.QUEUE_ADD, params, "coach-${System.nanoTime()}")
    }

    private fun finished() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            audio.abandonAudioFocusRequest(focusRequest)
        }
    }

    fun shutdown() {
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending.set(0)
        audio.abandonAudioFocusRequest(focusRequest)
    }
}
