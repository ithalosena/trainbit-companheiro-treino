package com.ithalo.hybridcoach.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.ithalo.hybridcoach.core.avatar.AvatarArtSet
import com.ithalo.hybridcoach.core.avatar.AvatarItem
import com.ithalo.hybridcoach.core.avatar.AvatarLayer
import com.ithalo.hybridcoach.core.avatar.AvatarSprite
import com.ithalo.hybridcoach.core.avatar.AvatarSprites
import com.ithalo.hybridcoach.core.avatar.ItemAnchor
import org.json.JSONObject

/**
 * Loads the avatar art made by tools/avatar/build.py (assets/avatar/spritesheet.png + spritesheet.json) and
 * installs it in [AvatarSprites]. The PNG stores the four tones with the colours listed in the JSON; any other
 * colour goes to the nearest of them. When the asset is missing or does not fit, the art baked in the core stays.
 */
object AvatarAssets {
    private const val TAG = "AvatarAssets"
    private const val DIR = "avatar"

    /** The characters: id to the sheet of its bodies. The boy's sheet also holds the effects and items all of them share. */
    val CHARACTERS = listOf("boy", "cat", "penguin", "ghost")

    @Volatile private var base: AvatarArtSet? = null

    /** True when the asset art was installed. Safe to call from a background thread, once at start-up. */
    fun install(context: Context, character: String = "boy"): Boolean {
        base = runCatching { read(context, "spritesheet.png", withExtras = true) }
            .onFailure { Log.w(TAG, "Avatar spritesheet not loaded; using the baked art", it) }
            .getOrNull() ?: return false
        return select(context, character)
    }

    /** Switches the pet. The other characters use the boy's effects (the Zs) and items (stickers). */
    fun select(context: Context, character: String): Boolean {
        val boy = base ?: return false
        val set = if (character == "boy" || character !in CHARACTERS) {
            boy
        } else {
            val bodies = runCatching { read(context, "char_$character.png", withExtras = false).bodies }
                .onFailure { Log.w(TAG, "Character $character not loaded; keeping the current pet", it) }
                .getOrNull() ?: return false
            AvatarArtSet(bodies, boy.effects, boy.items)
        }
        val ok = AvatarSprites.install(set)
        if (!ok) Log.w(TAG, "Avatar art does not match the app (frame size or names); keeping the previous art")
        return ok
    }

    private fun read(context: Context, png: String, withExtras: Boolean): AvatarArtSet {
        val json = JSONObject(context.assets.open("$DIR/spritesheet.json").bufferedReader().use { it.readText() })
        val bitmap = context.assets.open("$DIR/$png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPremultiplied = false; inScaled = false })
        } ?: error("$png could not be decoded")
        try {
            val toneArray = json.getJSONArray("tones")
            val tones = IntArray(toneArray.length()) { android.graphics.Color.parseColor(toneArray.getString(it)) and 0xFFFFFF }
            val w = json.getInt("frameWidth")
            val h = json.getInt("frameHeight")
            fun cut(x: Int, y: Int, cw: Int, ch: Int) = sprite(bitmap, tones, x, y, cw, ch)

            val bodies = HashMap<String, AvatarSprite>()
            val anims = json.getJSONArray("animations")
            for (i in 0 until anims.length()) {
                val a = anims.getJSONObject(i)
                val prefix = a.getString("frame")
                val row = a.getInt("row")
                for (f in 0 until a.getInt("frames")) bodies["${prefix}_$f"] = cut(f * w, row * h, w, h)
            }
            if (!withExtras) return AvatarArtSet(bodies, emptyMap(), emptyMap())
            val effects = HashMap<String, AvatarLayer>()
            json.optJSONObject("effects")?.let { e ->
                e.keys().forEach { k ->
                    val o = e.getJSONObject(k)
                    val r = o.getJSONArray("rect")
                    effects[k] = AvatarLayer(o.getInt("x"), o.getInt("y"), cut(r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3)))
                }
            }
            val items = HashMap<String, AvatarItem>()
            json.optJSONObject("items")?.let { e ->
                e.keys().forEach { k ->
                    val o = e.getJSONObject(k)
                    val r = o.getJSONArray("rect")
                    val anchor = if (o.optString("anchor") == "floor") ItemAnchor.FLOOR else ItemAnchor.BODY
                    items[k] = AvatarItem(anchor, o.getInt("x"), o.getInt("y"), cut(r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3)))
                }
            }
            return AvatarArtSet(bodies, effects, items)
        } finally {
            bitmap.recycle()
        }
    }

    /** Tones of a rectangle of the sheet: transparent is 0, each colour the index of the nearest tone + 1. */
    private fun sprite(bitmap: Bitmap, tones: IntArray, x: Int, y: Int, w: Int, h: Int): AvatarSprite {
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, x, y, w, h)
        val cells = ByteArray(w * h) { i ->
            val c = px[i]
            if ((c ushr 24) < 128) 0 else (nearest(tones, c and 0xFFFFFF) + 1).toByte()
        }
        return AvatarSprite(w, h, cells)
    }

    private fun nearest(tones: IntArray, rgb: Int): Int {
        var best = 0
        var bestD = Int.MAX_VALUE
        tones.forEachIndexed { i, t ->
            val dr = (t shr 16 and 0xFF) - (rgb shr 16 and 0xFF)
            val dg = (t shr 8 and 0xFF) - (rgb shr 8 and 0xFF)
            val db = (t and 0xFF) - (rgb and 0xFF)
            val d = dr * dr + dg * dg + db * db
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}
