package com.ithalo.hybridcoach.core.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * One frame of the spinning coin: [scale] is the visible width (|cos| of the angle), [edge] when the coin is seen
 * side-on (drawn as a 2 px gold bar), [mirrored] for the back half of the turn.
 */
data class CoinFrame(val scale: Float, val edge: Boolean, val mirrored: Boolean)

/** The coin turns in hard steps, like a sprite: 12 per turn (30 degrees each), 90 ms per step. */
object CoinSpin {
    const val STEPS = 12
    const val STEP_MS = 90L
    private const val EDGE_BELOW = 0.2

    fun frame(step: Int): CoinFrame {
        val c = cos(Math.toRadians(30.0 * Math.floorMod(step, STEPS)))
        // Rounded so 90 and 270 degrees are exactly side-on.
        val scale = (abs(c) * 1000).roundToInt() / 1000.0
        return CoinFrame(scale.toFloat(), scale < EDGE_BELOW, c < -1e-9)
    }
}

/** Visible width of the coin at [step] of the spin (1 = facing, 0 = side-on). */
fun coinStepToScale(step: Int): Float = CoinSpin.frame(step).scale
