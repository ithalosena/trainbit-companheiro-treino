package com.ithalo.hybridcoach.core.avatar

/** One-shot animations: they play once and the avatar goes back to its idle. */
enum class AvatarEvent { TRAIN_STRENGTH, TRAIN_RUN, CELEBRATE, TAP }

/**
 * Turns time into the [AvatarFrame] to draw. Everything is a pure function of the time except the
 * current mood, the running event and the mood transition, which [setMood] and [play] set.
 * The clock ticks every [TICK_MS] = 200 ms (5 fps); slow clips hold a frame for a few ticks.
 *
 * Idle behaviours (a walk around the stage, a muscle pose, a cheer, dozing off when tired) are scheduled by
 * hashing the time bucket, so they look random but are repeatable and testable.
 */
class AvatarAnimator(mood: AvatarMood = AvatarMood.NORMAL, private val seed: Int = 0) {
    var mood: AvatarMood = mood
        private set

    private var event: AvatarEvent? = null
    private var eventStartMs = 0L
    private var transitionStartMs = Long.MIN_VALUE

    /** Changes the mood; a short transition plays instead of a hard swap (not on the very first set). */
    fun setMood(next: AvatarMood, nowMs: Long) {
        if (next == mood) return
        mood = next
        transitionStartMs = nowMs
    }

    fun play(next: AvatarEvent, nowMs: Long) {
        event = next
        eventStartMs = nowMs
    }

    /** True while an event or a transition is still running at [nowMs]. */
    fun busy(nowMs: Long): Boolean = eventFrame(nowMs) != null || transitionFrame(nowMs) != null

    fun frameAt(nowMs: Long): AvatarFrame = eventFrame(nowMs) ?: transitionFrame(nowMs) ?: idleFrame(nowMs)

    private fun eventFrame(nowMs: Long): AvatarFrame? {
        val e = event ?: return null
        val steps = eventSteps(e)
        val tick = ((nowMs - eventStartMs) / TICK_MS).toInt()
        if (nowMs < eventStartMs || tick >= steps.size) return null
        return steps[tick]
    }

    private fun transitionFrame(nowMs: Long): AvatarFrame? {
        val tick = ((nowMs - transitionStartMs) / TICK_MS)
        if (transitionStartMs == Long.MIN_VALUE || nowMs < transitionStartMs || tick >= TRANSITION.size) return null
        return TRANSITION[tick.toInt()]
    }

    private fun idleFrame(nowMs: Long): AvatarFrame {
        val tick = nowMs / TICK_MS
        return when (mood) {
            AvatarMood.SLEEPING -> sleeping(tick)
            else -> behavior(tick) ?: base(tick)
        }
    }

    private fun base(tick: Long): AvatarFrame = when (mood) {
        AvatarMood.ENERGIZED -> AvatarFrame(FLEX.at(tick))
        // A blink now and then: the blink frames are the idle ones with the eyes shut, in the same phase.
        AvatarMood.NORMAL -> AvatarFrame(if (tick % BLINK_EVERY == BLINK_EVERY - 1) BLINK.at(tick) else IDLE.at(tick))
        AvatarMood.TIRED -> AvatarFrame(TIRED.at(tick))
        AvatarMood.SLUGGISH -> AvatarFrame(SLUGGISH.at(tick))
        AvatarMood.SLEEPING -> sleeping(tick)
    }

    /** Every 9-15 s a short behaviour: a walk, a muscle pose or a cheer when awake, dozing off when tired or sluggish. */
    private fun behavior(tick: Long): AvatarFrame? {
        val bucket = tick / BEHAVIOR_WINDOW
        val start = 3 + hash(bucket, 4) % 15 // ends by tick 59 even for the longest behaviour (the walk, 42 ticks)
        val at = tick % BEHAVIOR_WINDOW - start
        if (at < 0) return null
        val pick = hash(bucket, 5) % 4
        return when (mood) {
            AvatarMood.NORMAL, AvatarMood.ENERGIZED -> when (pick) {
                0L, 1L -> wander(at, if (hash(bucket, 6) % 2 == 0L) 1 else -1)
                2L -> if (at < POSE_TICKS) AvatarFrame(FLEX.at(at)) else null
                else -> if (at < POSE_TICKS) AvatarFrame(CHEER.at(at)) else null
            }
            AvatarMood.TIRED, AvatarMood.SLUGGISH -> if (at < DOZE_TICKS) sleeping(at) else null
            AvatarMood.SLEEPING -> null
        }
    }

    /**
     * Walks [AvatarSprites.WANDER] pixels to one side (1 px per tick), looks around for a moment and walks back
     * home. The art faces right, so it is mirrored while it walks left.
     */
    private fun wander(at: Long, dir: Int): AvatarFrame? {
        val d = AvatarSprites.WANDER.toLong()
        val home = AvatarSprites.HOME_X
        return when {
            at < d -> AvatarFrame(WALK.at(at), x = home + dir * (at + 1).toInt(), flip = dir < 0)
            at < d + PAUSE_TICKS -> AvatarFrame(IDLE.at(at), x = home + dir * d.toInt(), flip = dir < 0)
            at < 2 * d + PAUSE_TICKS -> {
                val back = at - d - PAUSE_TICKS
                AvatarFrame(WALK.at(back), x = home + dir * (d - back - 1).toInt(), flip = dir > 0)
            }
            else -> null
        }
    }

    /** The pose shown when animations are turned off in the system settings. */
    fun staticFrame(): AvatarFrame = staticFrame(mood)

    /** A looping clip: [frames] drawings named `<name>_<n>`, each held for [hold] ticks. */
    private class Clip(val name: String, val frames: Int, val hold: Int) {
        fun at(tick: Long): String = "${name}_${(tick / hold) % frames}"
        fun once(): List<AvatarFrame> = List(frames * hold) { AvatarFrame(at(it.toLong())) }
    }

    companion object {
        const val TICK_MS = 200L
        private const val BEHAVIOR_WINDOW = 60L
        private const val POSE_TICKS = 12
        private const val DOZE_TICKS = 20
        private const val PAUSE_TICKS = 6
        private const val BLINK_EVERY = 23L

        // Frame names and timing match the spritesheet made by tools/avatar/build.py (frameMs = hold x TICK_MS).
        private val IDLE = Clip("idle", 4, 2)
        private val BLINK = Clip("blink", 4, 2)
        private val WALK = Clip("walk", 4, 1)
        private val FLEX = Clip("flex", 4, 1)
        private val RUN = Clip("run", 6, 1)
        private val LIFT = Clip("lift", 6, 1)
        private val CHEER = Clip("cheer", 6, 1)
        private val TIRED = Clip("tired", 4, 2)
        private val SLUGGISH = Clip("sluggish", 4, 3)
        private val SLEEP = Clip("sleep", 2, 4)

        private fun sleeping(tick: Long): AvatarFrame {
            val phase = ((tick / SLEEP.hold) % 2).toInt()
            return AvatarFrame(SLEEP.at(tick), listOf(if (phase == 0) "z_a" else "z_b"))
        }

        fun staticFrame(mood: AvatarMood): AvatarFrame = when (mood) {
            AvatarMood.ENERGIZED -> AvatarFrame("flex_0")
            AvatarMood.NORMAL -> AvatarFrame("idle_0")
            AvatarMood.TIRED -> AvatarFrame("tired_0")
            AvatarMood.SLUGGISH -> AvatarFrame("sluggish_0")
            AvatarMood.SLEEPING -> AvatarFrame("sleep_0", listOf("z_a"))
        }

        private val TRANSITION = listOf("idle_0", "idle_1", "idle_0").map { AvatarFrame(it) }

        /** The ticks of each event, one frame per [TICK_MS]. */
        fun eventSteps(e: AvatarEvent): List<AvatarFrame> = when (e) {
            // pull up, then two rounds of wobbling overhead
            AvatarEvent.TRAIN_STRENGTH -> LIFT.once() + LIFT.once().drop(2 * LIFT.hold)
            AvatarEvent.TRAIN_RUN -> RUN.once() + RUN.once() + RUN.once()
            AvatarEvent.CELEBRATE -> CHEER.once() + CHEER.once()
            AvatarEvent.TAP -> FLEX.once() + FLEX.once() + AvatarFrame("idle_0")
        }

        /** Deterministic non-negative hash of a time bucket; [salt] separates the behaviours. */
        private fun hash(bucket: Long, salt: Int): Long {
            var h = bucket * 0x9E3779B1L + salt * 0x85EBCA6BL + 0x27D4EB2FL
            h = h xor (h ushr 15)
            h *= 0x2C1B3C6DL
            h = h xor (h ushr 12)
            return h and 0x7FFFFFFFL
        }
    }

    private fun hash(bucket: Long, salt: Int): Long = Companion.hash(bucket + seed, salt)
}
