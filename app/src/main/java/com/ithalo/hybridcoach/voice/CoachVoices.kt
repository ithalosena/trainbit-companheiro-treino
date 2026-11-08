package com.ithalo.hybridcoach.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

/**
 * Picks the coach voice among the engine's installed pt-BR voices. The engine default is often an
 * old, robotic one while better (neural) voices sit unused, so we rank them ourselves:
 * offline first (a run can lose signal), then quality, then latency.
 */
object CoachVoices {
    /** Installed pt-BR voices (pt-* as a fallback), best first. Network voices are listed after offline ones. */
    fun available(engine: TextToSpeech): List<Voice> {
        val all = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        val br = all.filter { it.locale.language == "pt" && it.locale.country.equals("BR", ignoreCase = true) }
        val pool = br.ifEmpty { all.filter { it.locale.language == "pt" } }
        return pool.sortedWith(
            compareBy<Voice> { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
                .thenBy { it.latency }
                .thenBy { it.name },
        )
    }

    /** The voice named [preferred] when still installed, else the best available; null keeps the engine default. */
    fun choose(engine: TextToSpeech, preferred: String?): Voice? {
        val voices = available(engine)
        return voices.firstOrNull { it.name == preferred } ?: voices.firstOrNull()
    }

    /** Applies [preferred] (or the best voice) and [rate]; true when a pt voice is active. */
    fun apply(engine: TextToSpeech, preferred: String?, rate: Float): Boolean {
        engine.setSpeechRate(rate)
        val voice = choose(engine, preferred)
        if (voice != null && runCatching { engine.setVoice(voice) }.getOrDefault(TextToSpeech.ERROR) == TextToSpeech.SUCCESS) return true
        return false
    }

    /** Short label: "Voz 3 · alta qualidade" / "· precisa de internet". */
    fun label(index: Int, voice: Voice): String {
        val quality = when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> "qualidade máxima"
            voice.quality >= Voice.QUALITY_HIGH -> "alta qualidade"
            voice.quality >= Voice.QUALITY_NORMAL -> "qualidade normal"
            else -> "qualidade baixa"
        }
        val net = if (voice.isNetworkConnectionRequired) " · precisa de internet" else ""
        return "Voz ${index + 1} · $quality$net"
    }
}
