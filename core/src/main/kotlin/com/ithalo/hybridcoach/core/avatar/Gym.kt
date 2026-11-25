package com.ithalo.hybridcoach.core.avatar

import com.ithalo.hybridcoach.core.game.GameState
import com.ithalo.hybridcoach.core.game.ItemUnlock

/** A piece of the pet's gym, liberated by level or by a trophy, in the order the gym fills up. */
data class GymPiece(val id: String, val name: String, val unlock: ItemUnlock)

/**
 * The pet's gym: an empty room with a window at first, equipment appearing as the player levels up. The art is
 * [GymArt] (tools/avatar/gym.py); nothing here is equipped, whatever is liberated is simply there.
 */
object GymCatalog {
    val pieces: List<GymPiece> = listOf(
        GymPiece("window", "Janela", ItemUnlock.Level(1)),
        GymPiece("mat", "Tapete", ItemUnlock.Level(2)),
        GymPiece("wall_rack", "Suporte de halteres", ItemUnlock.Level(4)),
        GymPiece("poster", "Pôster de campeão", ItemUnlock.Level(6)),
        GymPiece("bike", "Bike ergométrica", ItemUnlock.Level(8)),
        GymPiece("mirror", "Espelho", ItemUnlock.Level(10)),
        GymPiece("treadmill", "Esteira", ItemUnlock.Level(12)),
        GymPiece("kettlebell", "Kettlebell", ItemUnlock.Level(15)),
    )

    fun isUnlocked(piece: GymPiece, level: Int, trophies: Set<String>): Boolean = when (val u = piece.unlock) {
        is ItemUnlock.Level -> level >= u.level
        is ItemUnlock.Achievement -> u.id in trophies
    }

    fun unlocked(level: Int, trophies: Set<String> = emptySet()): List<GymPiece> = pieces.filter { isUnlocked(it, level, trophies) }

    fun unlocked(state: GameState): List<GymPiece> =
        unlocked(state.level, state.achievements.filter { it.unlocked }.map { it.def.id }.toSet())
}

/** Draws the gym of the pieces liberated so far on the pet's stage: ink (1) or empty (0), WIDTH x HEIGHT. */
object GymScene {
    /** The floor: a line under the pet's feet and a dithered strip below it. */
    private const val FLOOR_Y = 74

    fun compose(ids: Collection<String>): ByteArray {
        val w = AvatarSprites.WIDTH
        val out = ByteArray(w * AvatarSprites.HEIGHT)
        for (x in 0 until w) {
            out[FLOOR_Y * w + x] = 1
            if ((x + FLOOR_Y + 1) % 2 == 0) out[(FLOOR_Y + 1) * w + x] = 1
        }
        for (id in ids) {
            val art = GymArt.pieces[id] ?: continue
            art.rows.forEachIndexed { dy, row ->
                row.forEachIndexed { dx, ch ->
                    val x = art.x + dx
                    val y = art.y + dy
                    if (x !in 0 until w || y !in 0 until AvatarSprites.HEIGHT) return@forEachIndexed
                    if (ch == '1' || (ch == '2' && (x + y) % 2 == 0)) out[y * w + x] = 1
                }
            }
        }
        return out
    }

    /**
     * The pet over the gym, all in ink: where the pet has any pixel (its outline, its hollow inside) the gym is
     * cleared, so the equipment never shows through the figure.
     */
    fun withPet(petTones: ByteArray, gym: ByteArray, width: Int): ByteArray {
        val pet = AvatarOneBit.convert(petTones, width)
        return ByteArray(pet.size) { i -> if (petTones[i].toInt() != 0) pet[i] else gym[i] }
    }
}
