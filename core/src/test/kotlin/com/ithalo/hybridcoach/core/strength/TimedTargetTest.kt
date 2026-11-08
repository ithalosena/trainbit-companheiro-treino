package com.ithalo.hybridcoach.core.strength

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimedTargetTest {
    @Test
    fun `parses minutes seconds ranges and clock targets`() {
        assertEquals(480, TimedTarget.seconds("8min"))
        assertEquals(480, TimedTarget.seconds("8 min"))
        assertEquals(600, TimedTarget.seconds("10 minutos"))
        assertEquals(30, TimedTarget.seconds("30s"))
        assertEquals(45, TimedTarget.seconds("45 seg"))
        assertEquals(30, TimedTarget.seconds("30-45 s"))
        assertEquals(40, TimedTarget.seconds("40\""))
        assertEquals(90, TimedTarget.seconds("1:30"))
        assertEquals(120, TimedTarget.seconds("2 MIN"))
    }

    @Test
    fun `rep targets are not timed`() {
        assertNull(TimedTarget.seconds("8"))
        assertNull(TimedTarget.seconds("6-8"))
        assertNull(TimedTarget.seconds("AMRAP"))
        assertNull(TimedTarget.seconds("0s"))
        assertNull(TimedTarget.seconds("1:75"))
    }

    @Test
    fun `formats durations`() {
        assertEquals("8:00", TimedTarget.format(480))
        assertEquals("0:05", TimedTarget.format(5))
        assertEquals("1:02:03", TimedTarget.format(3723))
    }
}
