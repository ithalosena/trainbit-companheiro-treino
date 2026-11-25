package com.ithalo.hybridcoach.core.avatar

/**
 * The pet in two colors: the outline stays solid, the shade becomes a checkerboard dither and the lit tones are left
 * empty, so the figure is drawn with the ink only and the screen shows through.
 *
 * Tones are the parsed art's: 0 transparent, 1 outline, 2 shade, 3 base, 4 highlight. The result uses only 0 (empty)
 * and 1 (ink). The dither is anchored to the stage grid, not to the body, so it does not shimmer while the pet walks.
 */
object AvatarOneBit {
    /** Fraction of the base tone that gets ink, as a repeating pattern over (x, y); 0 = none. */
    fun convert(tones: ByteArray, width: Int): ByteArray {
        val out = ByteArray(tones.size)
        for (i in tones.indices) {
            val x = i % width
            val y = i / width
            val checker = (x + y) % 2 == 0
            out[i] = when (tones[i].toInt()) {
                1 -> 1
                2 -> if (checker) 1 else 0
                else -> 0
            }
        }
        return out
    }
}
