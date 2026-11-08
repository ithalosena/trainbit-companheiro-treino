package com.ithalo.hybridcoach.run

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.voice.CoachVoices
import java.util.concurrent.atomic.AtomicInteger

/**
 * pt-BR coach voice for the run. Music is lowered (ducked) while it speaks and restored when the queue empties.
 * Audio goes through the normal output, so a Bluetooth headset is used when connected.
 */
class RunVoice(context: Context) {
    @Volatile var enabled = true
    @Volatile var volume = 1f

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()
    private val pending = AtomicInteger(0)
    @Volatile private var ready = false
    private var tts: TextToSpeech? = null

    /** Phrases asked for before the engine finished starting ("Treino iniciado", first block). */
    private val early = ArrayDeque<String>()
    @Volatile private var initialized = false

    init {
        var created: TextToSpeech? = null
        created = TextToSpeech(context.applicationContext) { status ->
            val engine = created
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val r = engine.setLanguage(PT_BR)
                ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (CoachVoices.apply(engine, voiceName, voiceRate)) ready = true
                engine.setAudioAttributes(attributes)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = finished()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finished()
                })
            }
            initialized = true
            synchronized(early) {
                while (early.isNotEmpty()) speak(early.removeFirst())
            }
        }
        tts = created
    }

    @Volatile private var voiceName: String? = null
    @Volatile private var voiceRate: Float = 1f

    /** Chosen voice (null = best installed) and speech rate; applied now if the engine is up, else on init. */
    fun configure(name: String?, rate: Float) {
        voiceName = name
        voiceRate = rate
        val engine = tts
        if (initialized && engine != null) CoachVoices.apply(engine, name, rate)
    }

    /** True while something is queued or being spoken. */
    val speaking: Boolean get() = pending.get() > 0

    fun speak(text: String) {
        if (!initialized) {
            synchronized(early) { if (early.size < 10) early.addLast(text) }
            return
        }
        val engine = tts
        if (!enabled || !ready || engine == null) return
        if (pending.getAndIncrement() == 0) audio.requestAudioFocus(focusRequest)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        engine.speak(text, TextToSpeech.QUEUE_ADD, params, "run-${System.nanoTime()}")
    }

    private fun finished() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            audio.abandonAudioFocusRequest(focusRequest)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        audio.abandonAudioFocusRequest(focusRequest)
    }
}
