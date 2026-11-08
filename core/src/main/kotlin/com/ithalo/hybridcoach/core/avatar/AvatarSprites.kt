package com.ithalo.hybridcoach.core.avatar

/**
 * One thing to draw: which body frame (the face is part of the drawing), the effects on top of it, where the
 * body stands ([x], the left edge of the body inside the stage) and whether it is mirrored to face left.
 */
data class AvatarFrame(
    val body: String,
    val effects: List<String> = emptyList(),
    val x: Int = AvatarSprites.HOME_X,
    val flip: Boolean = false,
)

/** Parsed sprite: [cells] hold a tone per pixel, 0 = transparent, 1..4 = outline, shade, base, highlight. */
class AvatarSprite(val width: Int, val height: Int, val cells: ByteArray)

/** A sparse layer (effect) placed at [x], [y] of the body frame. */
class AvatarLayer(val x: Int, val y: Int, val sprite: AvatarSprite)

/** Where an item is drawn: on the stage floor (stays put) or on the body frame (follows its position and mirroring). */
enum class ItemAnchor { FLOOR, BODY }

/** An item's art: at [x], [y] of the stage ([ItemAnchor.FLOOR]) or of the body frame ([ItemAnchor.BODY]). */
class AvatarItem(val anchor: ItemAnchor, val x: Int, val y: Int, val sprite: AvatarSprite)

/** A complete set of avatar art: body frames by name (`idle_0`...), effects and items by id. */
class AvatarArtSet(val bodies: Map<String, AvatarSprite>, val effects: Map<String, AvatarLayer>, val items: Map<String, AvatarItem>)

/**
 * Parses the string art and composes a frame into one WIDTH x HEIGHT grid of tones: a stage wider than the
 * body, so the pet can walk around. Layer order: floor items, body, body items, effects.
 *
 * The art baked in [AvatarArt] is used until the app [install]s the same art read from its assets
 * (assets/avatar/spritesheet.png, made by tools/avatar/build.py); the baked copy stays as the fallback.
 */
object AvatarSprites {
    const val BODY_WIDTH = AvatarArt.BODY_WIDTH
    const val HEIGHT = AvatarArt.BODY_HEIGHT

    /** How far the pet walks to each side of its home spot. */
    const val WANDER = 18
    const val WIDTH = BODY_WIDTH + 2 * WANDER + 4
    const val HOME_X = (WIDTH - BODY_WIDTH) / 2

    /** Where stickers stand: on the floor, at the left corner of the stage. */
    private const val STICKER_X = 1
    private const val STICKER_Y = HEIGHT - 8

    private const val VALID = ".1234"

    /**
     * Parses [rows] ('.' transparent, '1'..'4' tones). When [width] and [height] are given the sprite must
     * have exactly that size; every row must be as wide as the first.
     */
    fun parse(rows: List<String>, width: Int? = null, height: Int? = null, label: String = "sprite"): AvatarSprite {
        require(rows.isNotEmpty()) { "$label: no rows" }
        val w = rows[0].length
        require(width == null || w == width) { "$label: width $w, expected $width" }
        require(height == null || rows.size == height) { "$label: ${rows.size} rows, expected $height" }
        val cells = ByteArray(w * rows.size)
        rows.forEachIndexed { y, row ->
            require(row.length == w) { "$label: row $y has ${row.length} characters, expected $w" }
            row.forEachIndexed { x, ch ->
                val tone = VALID.indexOf(ch)
                require(tone >= 0) { "$label: invalid character '$ch' at ($x, $y)" }
                cells[y * w + x] = tone.toByte()
            }
        }
        return AvatarSprite(w, rows.size, cells)
    }

    private val baked: AvatarArtSet by lazy {
        AvatarArtSet(
            bodies = AvatarArt.bodies.mapValues { (k, v) -> parse(v, BODY_WIDTH, HEIGHT, "body $k") },
            effects = AvatarArt.effects.mapValues { (k, v) -> AvatarLayer(v.x, v.y, parse(v.rows, label = "effect $k")) }
                .onEach { (k, l) -> require(fitsBody(l.x, l.y, l.sprite)) { "effect $k: outside the body frame" } },
            items = AvatarArt.stickers.mapValues { (k, v) -> AvatarItem(ItemAnchor.FLOOR, STICKER_X, STICKER_Y, parse(v, label = "sticker $k")) },
        )
    }

    @Volatile
    private var installed: AvatarArtSet? = null

    private val art: AvatarArtSet get() = installed ?: baked

    private fun fitsBody(x: Int, y: Int, s: AvatarSprite) = x >= 0 && y >= 0 && x + s.width <= BODY_WIDTH && y + s.height <= HEIGHT

    /**
     * Uses [set] instead of the baked art. Refused (false, the baked art stays) when a body frame has another
     * size, a frame the animator needs is missing, or a layer falls outside its frame.
     */
    fun install(set: AvatarArtSet): Boolean {
        val ok = set.bodies.values.all { it.width == BODY_WIDTH && it.height == HEIGHT } &&
            set.bodies.keys.containsAll(baked.bodies.keys) &&
            set.effects.keys.containsAll(baked.effects.keys) &&
            set.effects.values.all { fitsBody(it.x, it.y, it.sprite) } &&
            set.items.values.all {
                it.x >= 0 && it.y >= 0 && it.y + it.sprite.height <= HEIGHT &&
                    it.x + it.sprite.width <= (if (it.anchor == ItemAnchor.FLOOR) WIDTH else BODY_WIDTH)
            }
        if (ok) installed = set
        return ok
    }

    /** Back to the baked art (tests). */
    fun uninstall() {
        installed = null
    }

    val bodyNames: Set<String> get() = art.bodies.keys
    val effectNames: Set<String> get() = art.effects.keys
    val stickerIds: Set<String> get() = art.items.keys

    /** True when an item has a drawing; items without art simply show nothing. */
    fun hasSticker(itemId: String?): Boolean = itemId != null && itemId in art.items

    /** Composes [frame] (plus the optional equipped item) into WIDTH*HEIGHT tones. Unknown names fall back to the default pose. */
    fun compose(frame: AvatarFrame, stickerId: String? = null): ByteArray {
        val a = art
        val out = ByteArray(WIDTH * HEIGHT)
        val item = stickerId?.let { a.items[it] }
        if (item?.anchor == ItemAnchor.FLOOR) draw(out, item.sprite, item.x, item.y, flip = false)
        val bx = frame.x.coerceIn(0, WIDTH - BODY_WIDTH)
        draw(out, a.bodies[frame.body] ?: a.bodies.getValue(DEFAULT_BODY), bx, 0, frame.flip)
        // Body items and effects ride on the body frame: same position, mirrored with it.
        fun onBody(x: Int, s: AvatarSprite) = bx + if (frame.flip) BODY_WIDTH - x - s.width else x
        if (item?.anchor == ItemAnchor.BODY) draw(out, item.sprite, onBody(item.x, item.sprite), item.y, frame.flip)
        frame.effects.forEach { name -> a.effects[name]?.let { draw(out, it.sprite, onBody(it.x, it.sprite), it.y, flip = false) } }
        return out
    }

    private fun draw(out: ByteArray, s: AvatarSprite, ox: Int, oy: Int, flip: Boolean) {
        for (y in 0 until s.height) for (x in 0 until s.width) {
            val tone = s.cells[y * s.width + (if (flip) s.width - 1 - x else x)]
            val tx = ox + x
            val ty = oy + y
            if (tone != 0.toByte() && tx in 0 until WIDTH && ty in 0 until HEIGHT) out[ty * WIDTH + tx] = tone
        }
    }

    const val DEFAULT_BODY = "idle_0"
}
