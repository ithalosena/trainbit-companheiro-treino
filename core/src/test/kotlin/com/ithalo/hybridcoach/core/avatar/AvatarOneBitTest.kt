package com.ithalo.hybridcoach.core.avatar

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvatarOneBitTest {
    @Test
    fun `only ink and empty remain, the outline is kept whole and the shade is half dithered`() {
        val tones = byteArrayOf(0, 1, 2, 2, 3, 4, 2, 2)
        val one = AvatarOneBit.convert(tones, width = 4)
        assertTrue(one.all { it.toInt() == 0 || it.toInt() == 1 })
        assertEquals(1, one[1].toInt())
        assertEquals(0, one[4].toInt())
        assertEquals(0, one[5].toInt())
        // checkerboard: neighbours of the shade differ
        assertEquals(listOf(1, 0, 0, 1), listOf(one[2], one[3], one[6], one[7]).map { it.toInt() })
    }

    @Test
    fun `writes the one bit preview`() {
        System.setProperty("java.awt.headless", "true")
        val frames = listOf("idle_0", "idle_2", "flex_1", "walk_1", "run_2", "lift_3", "cheer_2", "tired_1", "sluggish_0", "sleep_0")
        val scale = 6
        val w = AvatarSprites.WIDTH
        val h = AvatarSprites.HEIGHT
        val sheet = BufferedImage(5 * (w * scale + 10) + 10, 2 * 2 * (h * scale + 10) + 10, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = Color(0x141412)
        g.fillRect(0, 0, sheet.width, sheet.height)
        frames.forEachIndexed { i, name ->
            val col = i % 5
            val row = i / 5
            val effects = if (name.startsWith("sleep")) listOf("z_a") else emptyList()
            val tones = AvatarSprites.compose(AvatarFrame(name, effects), null)
            val one = AvatarOneBit.convert(tones, w)
            listOf(0xD4CCB8 to 0x3E3D36, 0x1E1F1B to 0xD6CEBA).forEachIndexed { r, (bg, ink) ->
                val ox = 10 + col * (w * scale + 10)
                val oy = 10 + (row * 2 + r) * (h * scale + 10)
                g.color = Color(bg)
                g.fillRect(ox, oy, w * scale, h * scale)
                g.color = Color(ink)
                for (y in 0 until h) for (x in 0 until w) if (one[y * w + x].toInt() == 1) g.fillRect(ox + x * scale, oy + y * scale, scale, scale)
            }
        }
        g.dispose()
        val out = File("../build/previews/avatar_1bit.png")
        out.parentFile.mkdirs()
        ImageIO.write(sheet, "png", out)
        assertTrue(out.length() > 1_000)
    }
}
