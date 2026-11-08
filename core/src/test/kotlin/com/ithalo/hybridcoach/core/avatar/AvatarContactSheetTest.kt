package com.ithalo.hybridcoach.core.avatar

import com.ithalo.hybridcoach.core.game.ItemCatalog
import com.ithalo.hybridcoach.core.game.ItemKind
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws every mood and event frame by frame (5x, nearest neighbour), with and without each sticker, on the
 * BEGE light and dark ramps, into build/previews/avatar_states_sheet.png so the animator can be reviewed by eye
 * (build/previews/avatar_contact_sheet.png is the pipeline's sheet of the raw frames, see tools/avatar/build.py).
 */
class AvatarContactSheetTest {
    private class Ramp(val bg: Int, val tones: IntArray)

    private val light = Ramp(0xD4CCB8, intArrayOf(0, 0x3E3D36, 0x8C8573, 0xB8B09B, 0xEAE4D4))
    private val dark = Ramp(0x1E1F1B, intArrayOf(0, 0xD6CEBA, 0x3E4038, 0x626358, 0x96948A))

    private class Cell(val label: String, val frame: AvatarFrame, val sticker: String? = null, val ramp: Ramp)

    @Test
    fun `writes the contact sheet`() {
        System.setProperty("java.awt.headless", "true")
        val sections = mutableListOf<Pair<String, List<Cell>>>()

        for (ramp in listOf(light, dark)) {
            val tag = if (ramp === light) "claro" else "escuro"
            AvatarMood.entries.forEach { mood ->
                val animator = AvatarAnimator(mood)
                val frames = (0 until 240L).map { animator.frameAt(it * AvatarAnimator.TICK_MS) }.distinct().take(12)
                sections += "Estado ${mood.name} ($tag)" to frames.mapIndexed { i, f -> Cell("${i + 1}", f, null, ramp) }
            }
        }
        AvatarEvent.entries.forEach { e ->
            val steps = AvatarAnimator.eventSteps(e)
            sections += "Evento ${e.name} (${steps.size} quadros)" to steps.mapIndexed { i, f -> Cell("${i + 1}", f, null, light) }
        }
        val stickers = ItemCatalog.all.filter { it.kind == ItemKind.STICKER }
        for (ramp in listOf(light, dark)) {
            val idle = AvatarFrame("idle_0")
            val cells = listOf(Cell("sem item", idle, null, ramp)) + stickers.map { Cell(it.id.removePrefix("sticker_"), idle, it.id, ramp) }
            sections += "Itens vestidos (${if (ramp === light) "claro" else "escuro"})" to cells
        }
        // The item stays on the floor while the pet walks around it.
        val poses = listOf(AvatarFrame("walk_0", x = 2, flip = true), AvatarFrame("walk_2", x = 10, flip = true), AvatarFrame("idle_1"), AvatarFrame("walk_1", x = 38))
        sections += "Item no chão com o bichinho andando" to poses.map { Cell("${it.body} x=${it.x}", it, "sticker_heart", light) }

        val scale = 5
        val w = AvatarSprites.WIDTH * scale
        val size = AvatarSprites.HEIGHT * scale
        val cols = 4
        val gap = 12
        val titleH = 24
        val labelH = 16
        val width = cols * (w + gap) + gap
        val height = sections.sumOf { (_, cells) -> titleH + ((cells.size + cols - 1) / cols) * (size + labelH + gap) } + gap
        val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = Color(0x141412)
        g.fillRect(0, 0, width, height)
        var y = gap
        sections.forEach { (title, cells) ->
            g.color = Color(0xE6E2D6)
            g.drawString(title, gap, y + 14)
            y += titleH
            cells.forEachIndexed { i, cell ->
                val x = gap + (i % cols) * (w + gap)
                val cy = y + (i / cols) * (size + labelH + gap)
                val tones = AvatarSprites.compose(cell.frame, cell.sticker)
                g.color = Color(cell.ramp.bg)
                g.fillRect(x, cy, w, size)
                for (py in 0 until AvatarSprites.HEIGHT) for (px in 0 until AvatarSprites.WIDTH) {
                    val tone = tones[py * AvatarSprites.WIDTH + px].toInt()
                    if (tone != 0) {
                        g.color = Color(cell.ramp.tones[tone])
                        g.fillRect(x + px * scale, cy + py * scale, scale, scale)
                    }
                }
                g.color = Color(0xB0AC9E)
                g.drawString(cell.label, x, cy + size + 12)
            }
            y += ((cells.size + cols - 1) / cols) * (size + labelH + gap)
        }
        g.dispose()

        val out = File("../build/previews/avatar_states_sheet.png")
        out.parentFile.mkdirs()
        ImageIO.write(sheet, "png", out)
        assertTrue(out.length() > 10_000)
    }
}
