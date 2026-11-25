package com.ithalo.hybridcoach.core.avatar

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymTest {
    @Test
    fun `the gym fills up with the level and a level 1 player has only the window`() {
        assertEquals(listOf("window"), GymCatalog.unlocked(1).map { it.id })
        assertEquals(listOf("window", "mat"), GymCatalog.unlocked(3).map { it.id })
        assertEquals(GymCatalog.pieces.map { it.id }, GymCatalog.unlocked(99).map { it.id })
    }

    @Test
    fun `every piece has art that fits the stage, and the ids are unique`() {
        assertEquals(GymCatalog.pieces.size, GymCatalog.pieces.map { it.id }.toSet().size)
        GymCatalog.pieces.forEach { p ->
            val art = GymArt.pieces[p.id]
            assertTrue(art != null, "no art for ${p.id}")
            assertTrue(art.x >= 0 && art.y >= 0 && art.x + art.rows[0].length <= AvatarSprites.WIDTH, "${p.id} is outside the stage")
            assertTrue(art.y + art.rows.size <= AvatarSprites.HEIGHT, "${p.id} is below the stage")
            assertTrue(art.rows.all { it.length == art.rows[0].length && it.all { c -> c in ".12" } }, "${p.id} has a bad row")
        }
    }

    @Test
    fun `more pieces draw more ink and the floor is always there`() {
        val bare = GymScene.compose(emptyList())
        val full = GymScene.compose(GymCatalog.pieces.map { it.id })
        assertTrue(bare.count { it.toInt() == 1 } > AvatarSprites.WIDTH)
        assertTrue(full.count { it.toInt() == 1 } > bare.count { it.toInt() == 1 } + 100)
        assertTrue(full.all { it.toInt() == 0 || it.toInt() == 1 })
    }

    @Test
    fun `the pet clears the gym behind it so equipment does not show through`() {
        val w = AvatarSprites.WIDTH
        val gym = ByteArray(w * AvatarSprites.HEIGHT) { 1 }
        val pet = AvatarSprites.compose(AvatarFrame("idle_0"))
        val out = GymScene.withPet(pet, gym, w)
        for (i in pet.indices) if (pet[i].toInt() != 0 && pet[i].toInt() != 1 && pet[i].toInt() != 2) assertEquals(0, out[i].toInt(), "pet pixel $i leaks")
        assertTrue(out.indices.any { pet[it].toInt() == 0 && out[it].toInt() == 1 }, "the gym is gone outside the pet")
    }

    @Test
    fun `writes the gym preview at some levels`() {
        System.setProperty("java.awt.headless", "true")
        val levels = listOf(1, 4, 8, 12, 99)
        val scale = 4
        val w = AvatarSprites.WIDTH
        val h = AvatarSprites.HEIGHT
        val sheet = BufferedImage(levels.size * (w * scale + 8) + 8, 2 * (h * scale + 8) + 8, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = Color(0x141412)
        g.fillRect(0, 0, sheet.width, sheet.height)
        listOf(0xD4CCB8 to 0x3E3D36, 0x1E1F1B to 0xD6CEBA).forEachIndexed { r, (bg, ink) ->
            levels.forEachIndexed { i, level ->
                val ox = 8 + i * (w * scale + 8)
                val oy = 8 + r * (h * scale + 8)
                g.color = Color(bg)
                g.fillRect(ox, oy, w * scale, h * scale)
                val ids = GymCatalog.unlocked(level).map { it.id }
                val pet = AvatarSprites.compose(AvatarFrame("idle_1", x = AvatarSprites.HOME_X + if (i % 2 == 0) 0 else -14))
                val one = GymScene.withPet(pet, GymScene.compose(ids), w)
                g.color = Color(ink)
                for (y in 0 until h) for (x in 0 until w) if (one[y * w + x].toInt() == 1) g.fillRect(ox + x * scale, oy + y * scale, scale, scale)
            }
        }
        g.dispose()
        val out = File("../build/previews/avatar_gym_scene.png")
        out.parentFile.mkdirs()
        ImageIO.write(sheet, "png", out)
        assertTrue(out.length() > 1_000)
    }
}
