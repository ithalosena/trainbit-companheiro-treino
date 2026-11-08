package com.ithalo.hybridcoach.core.run

import com.ithalo.hybridcoach.core.plan.PaceRange
import com.ithalo.hybridcoach.core.plan.RunBlockDef
import com.ithalo.hybridcoach.core.plan.RunBlockKind

/** One block of the run as it is actually executed; [repetition] counts from 1 to [repetitions]. */
data class RunStep(
    val kind: RunBlockKind,
    val durationSeconds: Int?,
    val distanceM: Int?,
    val pace: PaceRange?,
    val repetition: Int = 1,
    val repetitions: Int = 1,
)

object RunSteps {
    /**
     * Expands `repeat`. A repeated block followed by a `recovery` with the same `repeat` is interleaved
     * (run, recovery, run, ...): `interval` + `recovery` as the schema documents, and also `steady` + `recovery`,
     * which is how the prompt asks for run/walk sessions. Any other block repeats back to back.
     */
    fun expand(blocks: List<RunBlockDef>): List<RunStep> {
        val out = mutableListOf<RunStep>()
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            val next = blocks.getOrNull(i + 1)
            if (b.kind != RunBlockKind.RECOVERY && b.repeat > 1 && next?.kind == RunBlockKind.RECOVERY && next.repeat == b.repeat) {
                for (r in 1..b.repeat) {
                    out += step(b, r)
                    out += step(next, r)
                }
                i += 2
            } else {
                for (r in 1..b.repeat) out += step(b, r)
                i += 1
            }
        }
        return out
    }

    private fun step(b: RunBlockDef, repetition: Int) =
        RunStep(b.kind, b.durationSeconds, b.distanceM, b.targetPace, repetition, b.repeat)
}
