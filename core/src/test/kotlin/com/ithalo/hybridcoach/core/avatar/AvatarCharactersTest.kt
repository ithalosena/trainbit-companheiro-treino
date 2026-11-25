package com.ithalo.hybridcoach.core.avatar

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The extra characters (tools/avatar/characters.py) share the boy's sheet layout, so the app can swap only the bodies. */
class AvatarCharactersTest {
    private val dir = File("../app/src/main/assets/avatar")
    private val tones = setOf(0x3E3D36, 0x8C8573, 0xB8B09B, 0xEAE4D4)

    @Test
    fun `every character has a frame for every clip the animator plays, drawn only in the four tones`() {
        val boy = ImageIO.read(File(dir, "spritesheet.png"))
        listOf("cat", "penguin", "ghost").forEach { id ->
            val img = ImageIO.read(File(dir, "char_$id.png"))
            assertTrue(boy.height >= 10 * AvatarSprites.HEIGHT)
            assertEquals(10 * AvatarSprites.HEIGHT, img.height, id)
            assertEquals(6 * AvatarArt.BODY_WIDTH, img.width, id)
            val clips = listOf("idle" to 4, "blink" to 4, "walk" to 4, "flex" to 4, "run" to 6, "lift" to 6, "cheer" to 6, "tired" to 4, "sluggish" to 4, "sleep" to 2)
            clips.forEachIndexed { row, (name, frames) ->
                for (f in 0 until frames) {
                    var inked = 0
                    for (y in 0 until AvatarSprites.HEIGHT) for (x in 0 until AvatarArt.BODY_WIDTH) {
                        val argb = img.getRGB(f * AvatarArt.BODY_WIDTH + x, row * AvatarSprites.HEIGHT + y)
                        if ((argb ushr 24) < 128) continue
                        inked++
                        assertTrue((argb and 0xFFFFFF) in tones, "$id $name $f has a color outside the tones")
                    }
                    assertTrue(inked > 200, "$id $name $f is empty")
                }
            }
        }
    }
}
