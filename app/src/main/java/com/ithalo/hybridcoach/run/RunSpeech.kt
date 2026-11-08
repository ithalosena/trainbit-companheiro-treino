package com.ithalo.hybridcoach.run

import android.content.res.Resources
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.plan.RunBlockKind
import com.ithalo.hybridcoach.core.run.Cue
import com.ithalo.hybridcoach.core.run.PaceAdvice
import com.ithalo.hybridcoach.core.run.RunEngine
import com.ithalo.hybridcoach.core.run.RunStep
import com.ithalo.hybridcoach.ui.common.PT_BR

/** Speed with one decimal, rounded to the treadmill's usual 0.1 km/h steps ("9,5"). */
fun formatKmh(v: Double): String = "%.1f".format(PT_BR, v)

/**
 * Turns coach cues into short pt-BR sentences. Paces are said as "5 e 30" (min e seg por km); on the treadmill,
 * block targets are said as speeds, which is what the user sets on the machine.
 */
class RunSpeech(private val res: Resources, private val treadmill: Boolean = false) {
    fun text(cue: Cue): String = when (cue) {
        is Cue.StepStart -> {
            val s = cue.step
            val name = if (s.repetitions > 1) res.getString(R.string.voice_step_rep, kindName(s.kind), s.repetition, s.repetitions) else kindName(s.kind)
            val target = s.pace?.let {
                if (treadmill) {
                    res.getString(R.string.voice_target_speed, kmh(RunEngine.kmhForPace(it.max)), kmh(RunEngine.kmhForPace(it.min)))
                } else {
                    res.getString(R.string.voice_target, pace(it.min), pace(it.max))
                }
            }.orEmpty()
            res.getString(R.string.voice_step_start, name, amount(s)) + target
        }
        is Cue.StepEnd -> res.getString(R.string.voice_step_end, kindName(cue.step.kind))
        is Cue.StepHalf -> res.getString(R.string.voice_step_half, kindName(cue.step.kind))
        Cue.BlocksDone -> res.getString(R.string.voice_blocks_done)
        Cue.AutoPaused -> res.getString(R.string.voice_auto_paused)
        Cue.AutoResumed -> res.getString(R.string.voice_auto_resumed)
        is Cue.StepEndingSoon -> cue.next?.let { res.getString(R.string.voice_step_ending_next, cue.seconds, kindName(it.kind)) }
            ?: res.getString(R.string.voice_step_ending_last, cue.seconds)
        is Cue.Kilometer -> res.getString(R.string.voice_km, cue.km, duration(cue.splitSeconds.toLong()), pace(cue.avgPaceSecPerKm))
        is Cue.Pace -> when (cue.advice) {
            PaceAdvice.SPEED_UP -> if (treadmill) res.getString(R.string.voice_speed_up_treadmill, kmh(RunEngine.kmhForPace(cue.paceSecPerKm)))
            else res.getString(R.string.voice_speed_up, pace(cue.paceSecPerKm))
            PaceAdvice.SLOW_DOWN -> if (treadmill) res.getString(R.string.voice_slow_down_treadmill, kmh(RunEngine.kmhForPace(cue.paceSecPerKm)))
            else res.getString(R.string.voice_slow_down, pace(cue.paceSecPerKm))
            PaceAdvice.ON_PACE -> res.getString(R.string.voice_on_pace)
        }
        is Cue.Finished -> res.getString(
            R.string.voice_finished,
            km(cue.distanceM),
            duration(cue.activeSeconds),
            cue.avgPaceSecPerKm?.let { res.getString(R.string.voice_avg_pace, pace(it)) }.orEmpty(),
        )
    }

    fun kindName(kind: RunBlockKind): String = res.getString(
        when (kind) {
            RunBlockKind.WARMUP -> R.string.voice_kind_warmup
            RunBlockKind.STEADY -> R.string.voice_kind_steady
            RunBlockKind.INTERVAL -> R.string.voice_kind_interval
            RunBlockKind.RECOVERY -> R.string.voice_kind_recovery
            RunBlockKind.COOLDOWN -> R.string.voice_kind_cooldown
        },
    )

    private fun amount(s: RunStep): String {
        val seconds = s.durationSeconds
        val meters = s.distanceM
        return when {
            seconds != null -> duration(seconds.toLong())
            meters != null -> if (meters < 1000) res.getString(R.string.voice_meters, meters) else res.getString(R.string.voice_km_amount, km(meters.toDouble()))
            else -> ""
        }
    }

    private fun pace(secPerKm: Int) = "${secPerKm / 60} e ${secPerKm % 60}"

    private fun kmh(v: Double) = formatKmh(v)

    private fun km(meters: Double): String {
        val v = meters / 1000.0
        return if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(PT_BR, v)
    }

    private fun duration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return listOfNotNull(
            if (h > 0) res.getQuantityString(R.plurals.voice_hours, h.toInt(), h) else null,
            if (m > 0) res.getQuantityString(R.plurals.voice_minutes, m.toInt(), m) else null,
            if (s > 0 || (h == 0L && m == 0L)) res.getQuantityString(R.plurals.voice_seconds, s.toInt(), s) else null,
        ).joinToString(" e ")
    }
}
