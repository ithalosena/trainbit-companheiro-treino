package com.ithalo.hybridcoach.ui.retro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.RetroColors

/**
 * Multi-tone pixel illustrations (the "hero" art of each screen), painted with the palette's four
 * sprite tones so they follow every palette and dark mode. Maps are generated from the approved
 * mockup drawings (scratch script gen_sprites.py): `.` transparent, `w` highlight, `l` light,
 * `m` mid, `d` ink, `p` paper.
 */
@Immutable
class PixelSpriteMap(val width: Int, val height: Int, internal val cells: CharArray)

private val cache = HashMap<String, PixelSpriteMap>()

fun pixelSprite(name: String): PixelSpriteMap = synchronized(cache) {
    cache.getOrPut(name) {
        val rows = SPRITES[name] ?: error("Unknown sprite $name")
        val w = rows.maxOf { it.length }
        PixelSpriteMap(w, rows.size, CharArray(w * rows.size) { i -> rows[i / w].getOrElse(i % w) { '.' } })
    }
}

private fun RetroColors.tone(ch: Char, reward: Boolean): Color? = when (ch) {
    'w' -> spriteHighlight
    'l' -> if (reward) this.reward else spriteLight
    'm' -> spriteMid
    'd' -> ink
    'p' -> window
    else -> null
}

/** Draws [map] with its top-left at [origin], one sprite pixel = [cell] px. */
fun DrawScope.drawPixelSprite(map: PixelSpriteMap, colors: RetroColors, origin: Offset, cell: Float, reward: Boolean = false) {
    for (y in 0 until map.height) {
        var x = 0
        while (x < map.width) {
            val ch = map.cells[y * map.width + x]
            val color = colors.tone(ch, reward)
            if (color == null) { x++; continue }
            // Merge horizontal runs of the same tone into one rect.
            var end = x + 1
            while (end < map.width && map.cells[y * map.width + end] == ch) end++
            drawRect(color, Offset(origin.x + x * cell, origin.y + y * cell), Size((end - x) * cell, cell))
            x = end
        }
    }
}

/**
 * A sprite at [pixel] dp per sprite pixel (rounded to whole device pixels so edges stay crisp).
 * [reward] paints the light tone with the palette's reward colour (the gold coin in Floresta).
 */
@Composable
fun PixelSprite(name: String, modifier: Modifier = Modifier, pixel: Dp = 2.dp, reward: Boolean = false, contentDescription: String? = null) {
    val map = pixelSprite(name)
    val colors = Retro.colors
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(pixel * map.width, pixel * map.height).then(semantics)) {
        val cell = px(pixel)
        drawPixelSprite(map, colors, Offset.Zero, cell, reward)
    }
}

private val SPRITES: Map<String, List<String>> = mapOf(
    "dumbbell" to listOf(
        "...............................pdddddddp.......................pdddddddp...............................",
        "...............................dlwwwwwld.......................dlwwwwwld...............................",
        "...............................dwlllllmd.......................dwlllllmd...............................",
        "...............................dwllmllmd.......................dwllmllmd...............................",
        "........................pddddddpwllmllmd.......................dwllmllmpddddddp........................",
        "........................dlwwwwldwllmllmd.......................dwllmllmdlwwwwld........................",
        "........................dwllllmdwllmllmd.......................dwllmllmdwllllmd........................",
        "........................dwlmllmdwllmllmd.......................dwllmllmdwlmllmd........................",
        "........................dwlmllmdwllmllmd.......................dwllmllmdwlmllmd........................",
        "........................dwlmllmdwllmllmd.......................dwllmllmdwlmllmd........................",
        "....................pdddpwlmllmdwllmllmd.......................dwllmllmdwlmllmpdddp....................",
        "....................dllldwlmllmdwllmllmd.......................dwllmllmdwlmllmdllld....................",
        "....................dwlmdwlmllmdwllmllmd.......................dwllmllmdwlmllmdwlmd....................",
        "....................dwlmdwlmllmdwllmllmd.......................dwllmllmdwlmllmdwlmd....................",
        ".....ddddddddddddddddwlmdwlmllmdwllmllmdddddddddddddddddddddddddwllmllmdwlmllmdwlmdddddddddddddddd.....",
        ".....dwwwwwwwwwwwwwwdwlmdwlmllmdwllmllmdwwwwwwwwwwwwwwwwwwwwwwwdwllmllmdwlmllmdwlmdwwwwwwwwwwwwwwd.....",
        ".....dlllllllllllllldwlmdwlmllmdwllmllmdlllmlmlmlmlmlmlmlmlmllldwllmllmdwlmllmdwlmdlllllllllllllld.....",
        ".....dmmmmmmmmmmmmmmdwlmdwlmllmdwllmllmdmmmmmmmmmmmmmmmmmmmmmmmdwllmllmdwlmllmdwlmdmmmmmmmmmmmmmmd.....",
        ".....ddddddddddddddddwlmdwlmllmdwllmllmdddddddddddddddddddddddddwllmllmdwlmllmdwlmdddddddddddddddd.....",
        "....................dwlmdwlmllmdwllmllmd.......................dwllmllmdwlmllmdwlmd....................",
        "....................dllmdwlmllmdwllmllmd.......................dwllmllmdwlmllmdllmd....................",
        "....................dmmmdwlmllmdwllmllmd.......................dwllmllmdwlmllmdmmmd....................",
        "....................pdddpwlmllmdwllmllmd.......................dwllmllmdwlmllmpdddp....................",
        "........................dwlmllmdwllmllmd.......................dwllmllmdwlmllmd........................",
        "........................dwlmllmdwllmllmd.......................dwllmllmdwlmllmd........................",
        "........................dwlmllmdwllmllmd.......................dwllmllmdwlmllmd........................",
        "........................dlllllmdwllmllmd.......................dwllmllmdlllllmd........................",
        "........................dmmmmmmdwllmllmd.......................dwllmllmdmmmmmmd........................",
        "........................pddddddpwllmllmd.......................dwllmllmpddddddp........................",
        "...............................dwllmllmd.......................dwllmllmd...............................",
        "...............................dllllllmd.......................dllllllmd...............................",
        "...............................dmmmmmmmd.......................dmmmmmmmd...............................",
        "...............................pdddddddp.......................pdddddddp...............................",
        ".......................................................................................................",
        "...................................................m...................................................",
        "..............m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m..............",
        "...m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m...",
        "m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m",
        "...m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m...",
        "..............m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m..............",
        "...................................................m...................................................",
    ),
    "barbell" to listOf(
        "...................................pddddddp.......................................................................pddddddp...................................",
        "...................................dlwwwwld.......................................................................dlwwwwld...................................",
        "...................................dwllllmd.......................................................................dwllllmd...................................",
        "...................................dwlmllmd.......................................................................dwlmllmd...................................",
        ".............................pdddddpwlmllmd.......................................................................dwlmllmpdddddp.............................",
        ".............................dlwwwldwlmllmd.......................................................................dwlmllmdlwwwld.............................",
        ".............................dwlllmdwlmllmd.......................................................................dwlmllmdwlllmd.............................",
        ".............................dwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmd.............................",
        ".............................dwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmd.............................",
        ".............................dwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmd.............................",
        ".........................pdddpwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmpdddp.........................",
        ".........................dllldwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdllld.........................",
        ".........................dwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmd.........................",
        ".........................dwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmd.........................",
        ".........................dwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmd.........................",
        ".........................dwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmd.........................",
        ".......................pdpwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmpdp.......................",
        ".......................dldwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmdld.......................",
        ".......................dldwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmdld.......................",
        "ddddddddddddddddddddddddldwlmdwlmlmdwlmllmdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddwlmllmdwlmlmdwlmdldddddddddddddddddddddddd",
        "dwwwwwwwwwwwwwwwwwwwwwwdldwlmdwlmlmdwlmllmdwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwdwlmllmdwlmlmdwlmdldwwwwwwwwwwwwwwwwwwwwwwd",
        "dlllllllllllllllllllllldldwlmdwlmlmdwlmllmdlllllllllllllllllllllllllllmlmlmlmlmlmlmlmlmllllllllllllllllllllllllllldwlmllmdwlmlmdwlmdldlllllllllllllllllllllld",
        "dmmmmmmmmmmmmmmmmmmmmmmdldwlmdwlmlmdwlmllmdmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmdwlmllmdwlmlmdwlmdldmmmmmmmmmmmmmmmmmmmmmmd",
        "ddddddddddddddddddddddddldwlmdwlmlmdwlmllmdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddwlmllmdwlmlmdwlmdldddddddddddddddddddddddd",
        ".......................dldwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmdld.......................",
        ".......................dmdwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmdmd.......................",
        ".......................pdpwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmpdp.......................",
        ".........................dwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmd.........................",
        ".........................dwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmd.........................",
        ".........................dwlmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdwlmd.........................",
        ".........................dllmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdllmd.........................",
        ".........................dmmmdwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmdmmmd.........................",
        ".........................pdddpwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmpdddp.........................",
        ".............................dwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmd.............................",
        ".............................dwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmd.............................",
        ".............................dwlmlmdwlmllmd.......................................................................dwlmllmdwlmlmd.............................",
        ".............................dllllmdwlmllmd.......................................................................dwlmllmdllllmd.............................",
        ".............................dmmmmmdwlmllmd.......................................................................dwlmllmdmmmmmd.............................",
        ".............................pdddddpwlmllmd.......................................................................dwlmllmpdddddp.............................",
        "...................................dwlmllmd.......................................................................dwlmllmd...................................",
        "...................................dlllllmd.......................................................................dlllllmd...................................",
        "...................................dmmmmmmd.......................................................................dmmmmmmd...................................",
        "...................................pddddddp.......................................................................pddddddp...................................",
        ".............................................................................................................................................................",
        ".............................................................................................................................................................",
        "..........................m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m..........................",
        ".............m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.............",
        "........m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m........",
        ".............m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.............",
        "..........................m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m..........................",
    ),
    "coin" to listOf(
        "...dddd...",
        "..dlllld..",
        ".dlllllld.",
        ".dwlldlmd.",
        "dlwlldlmld",
        "dlwlldlmld",
        "dlwlldlmld",
        "dlwlldlmld",
        ".dwlldlmd.",
        ".dlllllld.",
        "..dlllld..",
        "...dddd...",
    ),
    "logo" to listOf(
        "...................pdddddp.............pdddddp...................",
        "...................dlwwwld.............dlwwwld...................",
        "...................dwlllmd.............dwlllmd...................",
        "...................dwlmlmd.............dwlmlmd...................",
        "..............pddddpwlmlmd.............dwlmlmpddddp..............",
        "..............dlllldwlmlmd.............dwlmlmdlllld..............",
        "..............dwllmdwlmlmd.............dwlmlmdwllmd..............",
        "..............dwllmdwlmlmd.............dwlmlmdwllmd..............",
        "...........pddpwllmdwlmlmd.............dwlmlmdwllmpddp...........",
        "...........dlldwllmdwlmlmd.............dwlmlmdwllmdlld...........",
        "...........dwmdwllmdwlmlmd.............dwlmlmdwllmdwmd...........",
        "......ddddddwmdwllmdwlmlmdddddddddddddddwlmlmdwllmdwmdddddd......",
        "......dwwwwdwmdwllmdwlmlmdwwwwwwwwwwwwwdwlmlmdwllmdwmdwwwwd......",
        "......dlllldwmdwllmdwlmlmdmlmlmlmlmlmlmdwlmlmdwllmdwmdlllld......",
        "......dmmmmdwmdwllmdwlmlmdmmmmmmmmmmmmmdwlmlmdwllmdwmdmmmmd......",
        "......ddddddwmdwllmdwlmlmdddddddddddddddwlmlmdwllmdwmdddddd......",
        "...........dlmdwllmdwlmlmd.............dwlmlmdwllmdlmd...........",
        "...........dmmdwllmdwlmlmd.............dwlmlmdwllmdmmd...........",
        "...........pddpwllmdwlmlmd.............dwlmlmdwllmpddp...........",
        "..............dwllmdwlmlmd.............dwlmlmdwllmd..............",
        "..............dlllmdwlmlmd.............dwlmlmdlllmd..............",
        "..............dmmmmdwlmlmd.............dwlmlmdmmmmd..............",
        "..............pddddpwlmlmd.............dwlmlmpddddp..............",
        "...................dwlmlmd.............dwlmlmd...................",
        "...................dllllmd.............dllllmd...................",
        "...................dmmmmmd.............dmmmmmd...................",
        "...................pdddddp.............pdddddp...................",
        ".................................................................",
        ".................................................................",
        "................m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m................",
        "...........m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m...........",
        "........m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m........",
        "...........m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m...........",
        "................m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m.m................",
        ".................................................................",
        "ddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
        "dllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllld",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmwwwwmmmmwwwwmmmmwwwwmmmmwwwwmmmmwwwwmmmmwwwwmmmmwwwwmmmmwwwwd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "dmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmd",
        "ddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
    ),
    "flame" to listOf(
        "....d......",
        "....dd.....",
        "...dld..d..",
        "...dlld.dd.",
        "..dllld.dld",
        "..dlwlldlld",
        ".dllwwllld.",
        ".dlwwwllmd.",
        "dllwwlllmmd",
        "dlllwllmmmd",
        ".dllllmmmd.",
        "..ddddddd..",
    ),
    "trophy" to listOf(
        "..ddddddddd...",
        "dddwwllllmddd.",
        "d.dwllllllmd.d",
        "d.dwllllllmd.d",
        ".ddwlllllmdd..",
        "...dwllllmd...",
        "....dllllmd...",
        ".....dlmd.....",
        "......dd......",
        ".....dlmd.....",
        "....dddddd....",
        "...dllllllmd..",
        "...dddddddddd.",
    ),
    "medal" to listOf(
        ".dd...dd.",
        ".dld.dld.",
        "..dlddld.",
        "...dldd..",
        "..ddddd..",
        ".dwwllmd.",
        "dwwllllmd",
        "dwlldllmd",
        "dwllllmmd",
        ".dllllmd.",
        "..ddddd..",
    ),
    "hourglass" to listOf(
        "ddddddddd",
        ".dwlllld.",
        ".dwllllmd",
        "..dwllmd.",
        "...dlmd..",
        "....dd...",
        "...dmmd..",
        "..dmlmmd.",
        ".dmmllmmd",
        ".dmmmmmmd",
        "ddddddddd",
    ),
    "shoe" to listOf(
        "...dddd......",
        "...dlld......",
        "...dlldddd...",
        "..dlwllllldd.",
        ".dlwlllllllld",
        "ddddddddddddd",
        "dmmmmmmmmmmmd",
        ".ddddddddddd.",
    ),
)
