package com.ithalo.hybridcoach.core.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoinSpinTest {
    @Test
    fun `a turn is 12 steps from facing to side-on to mirrored and back`() {
        assertEquals(1f, coinStepToScale(0))
        assertEquals(0.866f, coinStepToScale(1), 0.001f)
        assertEquals(0.5f, coinStepToScale(2), 0.001f)
        assertEquals(0f, coinStepToScale(3))
        assertEquals(1f, coinStepToScale(6))
        assertEquals(coinStepToScale(0), coinStepToScale(12))
        assertEquals(coinStepToScale(1), coinStepToScale(-11))
    }

    @Test
    fun `side-on frames are the gold edge and the back half is mirrored`() {
        assertTrue(CoinSpin.frame(3).edge)
        assertTrue(CoinSpin.frame(9).edge)
        assertFalse(CoinSpin.frame(2).edge)
        assertFalse(CoinSpin.frame(0).mirrored)
        assertTrue(CoinSpin.frame(5).mirrored)
        assertTrue(CoinSpin.frame(6).mirrored)
        assertFalse(CoinSpin.frame(3).mirrored)
        assertEquals(1080L, CoinSpin.STEPS * CoinSpin.STEP_MS)
    }
}
