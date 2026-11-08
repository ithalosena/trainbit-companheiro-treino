package com.ithalo.hybridcoach.core.avatar

import com.ithalo.hybridcoach.core.game.GameFacts
import com.ithalo.hybridcoach.core.game.ItemCatalog
import com.ithalo.hybridcoach.core.game.ItemKind
import com.ithalo.hybridcoach.core.game.SessionFact
import com.ithalo.hybridcoach.core.game.SessionKind
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AvatarSpritesTest {
    @Test
    fun `parses a valid sprite and keeps the tones`() {
        val s = AvatarSprites.parse(listOf(".1", "34"), 2, 2)
        assertEquals(listOf(0, 1, 3, 4), s.cells.map { it.toInt() })
    }

    @Test
    fun `rejects wrong size, ragged rows and unknown characters`() {
        assertFailsWith<IllegalArgumentException> { AvatarSprites.parse(listOf(".1", "34"), 3, 2) }
        assertFailsWith<IllegalArgumentException> { AvatarSprites.parse(listOf(".1", "34"), 2, 3) }
        assertFailsWith<IllegalArgumentException> { AvatarSprites.parse(listOf(".1", "3")) }
        assertFailsWith<IllegalArgumentException> { AvatarSprites.parse(listOf(".5")) }
        assertFailsWith<IllegalArgumentException> { AvatarSprites.parse(emptyList()) }
    }

    @Test
    fun `every baked body parses at the body size and every layer parses inside it`() {
        assertEquals(44, AvatarSprites.bodyNames.size)
        // Touching the lazy maps parses (and validates) all the art.
        assertTrue(AvatarSprites.effectNames.isNotEmpty())
        AvatarSprites.bodyNames.forEach { assertTrue(AvatarSprites.compose(AvatarFrame(it)).any { t -> t != 0.toByte() }, it) }
    }

    @Test
    fun `every frame the animations can produce uses art that exists`() {
        val frames = mutableListOf<AvatarFrame>()
        AvatarEvent.entries.forEach { frames += AvatarAnimator.eventSteps(it) }
        AvatarMood.entries.forEach { mood ->
            frames += AvatarAnimator.staticFrame(mood)
            val a = AvatarAnimator(mood)
            for (t in 0 until 600L) frames += a.frameAt(t * AvatarAnimator.TICK_MS)
        }
        frames.forEach { f ->
            assertTrue(f.body in AvatarSprites.bodyNames, "body ${f.body}")
            f.effects.forEach { assertTrue(it in AvatarSprites.effectNames, "effect $it") }
        }
    }

    @Test
    fun `unknown names fall back instead of crashing`() {
        val cells = AvatarSprites.compose(AvatarFrame("nope", listOf("nope")), "sticker_nope")
        assertEquals(AvatarSprites.WIDTH * AvatarSprites.HEIGHT, cells.size)
        assertTrue(cells.any { it != 0.toByte() })
    }

    @Test
    fun `sticker items that can be worn all have art, and equipping one changes the picture`() {
        val stickers = ItemCatalog.all.filter { it.kind == ItemKind.STICKER }
        assertTrue(stickers.isNotEmpty())
        val bare = AvatarSprites.compose(AvatarFrame("idle_0"))
        stickers.forEach {
            assertTrue(AvatarSprites.hasSticker(it.id), "no art for ${it.id}")
            assertFalse(AvatarSprites.compose(AvatarFrame("idle_0"), it.id).contentEquals(bare), it.id)
        }
        assertFalse(AvatarSprites.hasSticker(null))
    }

    @Test
    fun `the sticker stays on the floor, in the corner, whatever the pet does`() {
        val w = AvatarSprites.WIDTH
        listOf(AvatarFrame("idle_0"), AvatarFrame("walk_1", x = AvatarSprites.HOME_X + 10), AvatarFrame("cheer_2")).forEach { f ->
            val bare = AvatarSprites.compose(f)
            val worn = AvatarSprites.compose(f, "sticker_heart")
            val changed = worn.indices.filter { worn[it] != bare[it] }
            assertTrue(changed.isNotEmpty())
            assertTrue(changed.all { it % w < 8 && it / w >= AvatarSprites.HEIGHT - 8 }, f.body)
        }
    }

    @Test
    fun `art read from the assets replaces the baked one only when it fits`() {
        val blank = AvatarSprite(AvatarSprites.BODY_WIDTH, AvatarSprites.HEIGHT, ByteArray(AvatarSprites.BODY_WIDTH * AvatarSprites.HEIGHT))
        val dot = AvatarSprite(1, 1, byteArrayOf(4))
        val effects = AvatarSprites.effectNames.associateWith { AvatarLayer(0, 0, dot) }
        val bodies = AvatarSprites.bodyNames.associateWith { blank }
        try {
            // A frame of another size, or a missing frame, is refused and the baked art stays.
            assertFalse(AvatarSprites.install(AvatarArtSet(bodies + ("idle_0" to AvatarSprite(2, 2, ByteArray(4))), effects, emptyMap())))
            assertFalse(AvatarSprites.install(AvatarArtSet(bodies - "walk_0", effects, emptyMap())))
            assertTrue(AvatarSprites.compose(AvatarFrame("idle_0")).any { it != 0.toByte() })
            // A matching set is used: here every frame is blank, with one item worn on the body.
            val hat = AvatarItem(ItemAnchor.BODY, 10, 2, AvatarSprite(2, 1, byteArrayOf(1, 2)))
            assertTrue(AvatarSprites.install(AvatarArtSet(bodies, effects, mapOf("hat" to hat))))
            assertTrue(AvatarSprites.compose(AvatarFrame("idle_0")).all { it == 0.toByte() })
            val w = AvatarSprites.WIDTH
            val home = AvatarSprites.compose(AvatarFrame("idle_0"), "hat")
            assertEquals(1, home[2 * w + AvatarSprites.HOME_X + 10].toInt())
            assertEquals(2, home[2 * w + AvatarSprites.HOME_X + 11].toInt())
            // The body item follows the body: moved with it, and mirrored when it faces left.
            val moved = AvatarSprites.compose(AvatarFrame("idle_0", x = AvatarSprites.HOME_X + 5), "hat")
            assertEquals(1, moved[2 * w + AvatarSprites.HOME_X + 15].toInt())
            val left = AvatarSprites.compose(AvatarFrame("idle_0", flip = true), "hat")
            val mx = AvatarSprites.HOME_X + AvatarSprites.BODY_WIDTH - 10 - 2
            assertEquals(listOf(2, 1), listOf(left[2 * w + mx].toInt(), left[2 * w + mx + 1].toInt()))
        } finally {
            AvatarSprites.uninstall()
        }
        assertTrue(AvatarSprites.compose(AvatarFrame("idle_0")).any { it != 0.toByte() })
    }

    @Test
    fun `a body moved or mirrored stays inside the stage`() {
        val home = AvatarSprites.compose(AvatarFrame("walk_0"))
        val right = AvatarSprites.compose(AvatarFrame("walk_0", x = AvatarSprites.HOME_X + AvatarSprites.WANDER))
        val mirrored = AvatarSprites.compose(AvatarFrame("walk_0", flip = true))
        assertNotEquals(home.toList(), right.toList())
        assertNotEquals(home.toList(), mirrored.toList())
        assertEquals(home.count { it != 0.toByte() }, mirrored.count { it != 0.toByte() })
        assertEquals(home.count { it != 0.toByte() }, right.count { it != 0.toByte() })
    }

    @Test
    fun `the sticker lands on every pose`() {
        AvatarSprites.bodyNames.forEach {
            val bare = AvatarSprites.compose(AvatarFrame(it))
            assertFalse(AvatarSprites.compose(AvatarFrame(it), "sticker_coin").contentEquals(bare), it)
        }
    }
}

class AvatarStateTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val noon = 12

    private fun input(
        done: Int = 3,
        planned: Int = 3,
        since: Int? = 1,
        streak: Int = 2,
        rpes: List<Int> = listOf(7),
        consecutive: Int = 1,
        done3: Int = 1,
        plannedToday: Boolean = false,
        doneToday: Boolean = false,
    ) = AvatarInput(today, done, planned, since, streak, rpes, consecutive, done3, plannedToday, doneToday)

    @Test
    fun `a good week is energized`() {
        val s = computeAvatarState(input(done = 3, planned = 3, since = 1, streak = 3), noon)
        assertEquals(AvatarMood.ENERGIZED, s.mood)
        assertTrue(s.vitality >= AvatarTuning.VITALITY_ENERGIZED)
    }

    @Test
    fun `a middling week is normal`() {
        val s = computeAvatarState(input(done = 1, planned = 3, since = 4, streak = 0, rpes = listOf(6)), noon)
        assertEquals(AvatarMood.NORMAL, s.mood)
    }

    @Test
    fun `hard back to back sessions make it tired`() {
        val s = computeAvatarState(input(rpes = listOf(9, 9), since = 0, consecutive = 2, done3 = 2), noon) // 56 + 12 + 7 = 75
        assertEquals(AvatarMood.TIRED, s.mood)
        assertTrue(s.fatigue >= AvatarTuning.FATIGUE_TIRED)
    }

    @Test
    fun `a rest day takes the tiredness down`() {
        val tired = input(rpes = listOf(9, 9), since = 0, consecutive = 2, done3 = 2)
        val rested = tired.copy(daysSinceLastWorkout = 2, consecutiveTrainingDays = 0, doneLast3Days = 2)
        assertTrue(computeAvatarState(rested, noon).fatigue < computeAvatarState(tired, noon).fatigue)
    }

    @Test
    fun `many idle days are sluggish and never negative`() {
        val s = computeAvatarState(input(done = 0, planned = 3, since = 12, streak = 0, rpes = emptyList(), consecutive = 0, done3 = 0), noon)
        assertEquals(AvatarMood.SLUGGISH, s.mood)
        assertTrue(s.vitality in 0..100 && s.fatigue in 0..100)
    }

    @Test
    fun `sluggish starts exactly at the inactivity threshold`() {
        val base = input(done = 0, planned = 3, streak = 0, rpes = emptyList(), consecutive = 0, done3 = 0)
        assertNotEquals(AvatarMood.SLUGGISH, computeAvatarState(base.copy(daysSinceLastWorkout = AvatarTuning.INACTIVE_DAYS - 1), noon).mood)
        assertEquals(AvatarMood.SLUGGISH, computeAvatarState(base.copy(daysSinceLastWorkout = AvatarTuning.INACTIVE_DAYS), noon).mood)
    }

    @Test
    fun `tired starts exactly at the fatigue threshold`() {
        // rpe 9 -> (9-5)*14 = 56; one extra streak day adds 7 -> 63 with no rest: tired. Without the streak: 56, not tired.
        val under = input(rpes = listOf(9), since = 0, consecutive = 1, done3 = 1)
        val over = under.copy(consecutiveTrainingDays = 2)
        assertEquals(56, computeAvatarState(under, noon).fatigue)
        assertNotEquals(AvatarMood.TIRED, computeAvatarState(under, noon).mood)
        assertEquals(63, computeAvatarState(over, noon).fatigue)
        assertEquals(AvatarMood.TIRED, computeAvatarState(over, noon).mood)
    }

    @Test
    fun `energized starts exactly at the vitality threshold`() {
        // 30 + 35*1 + 4*streak + 11 (trained yesterday) = 76 with streak 0 -> energized already; lower adherence to probe the edge.
        val probe = input(done = 2, planned = 3, since = 2, streak = 0, rpes = emptyList(), consecutive = 0, done3 = 1)
        val s = computeAvatarState(probe, noon)
        assertEquals(AvatarMood.NORMAL, s.mood) // 30 + 23 + 5 = 58
        val edge = computeAvatarState(probe.copy(doneLast7 = 3, plannedLast7 = 3, weekStreak = 0), noon) // 30 + 35 + 5 = 70
        assertEquals(70, edge.vitality)
        assertEquals(AvatarMood.ENERGIZED, edge.mood)
    }

    @Test
    fun `sleeping at night and when exhausted, with the clock bounds`() {
        assertEquals(AvatarMood.SLEEPING, computeAvatarState(input(), 23).mood)
        assertEquals(AvatarMood.SLEEPING, computeAvatarState(input(), 3).mood)
        assertEquals(AvatarMood.SLEEPING, computeAvatarState(input(), 0).mood)
        assertNotEquals(AvatarMood.SLEEPING, computeAvatarState(input(), 6).mood)
        assertNotEquals(AvatarMood.SLEEPING, computeAvatarState(input(), 22).mood)
        val exhausted = input(rpes = listOf(10, 10, 10), since = 0, consecutive = 5, done3 = 3)
        val s = computeAvatarState(exhausted, noon)
        assertTrue(s.fatigue >= AvatarTuning.FATIGUE_SLEEP)
        assertEquals(AvatarMood.SLEEPING, s.mood)
    }

    @Test
    fun `a brand new player is neutral, not punished`() {
        val s = computeAvatarState(input(done = 0, planned = 0, since = null, streak = 0, rpes = emptyList(), consecutive = 0, done3 = 0), noon)
        assertEquals(AvatarMood.NORMAL, s.mood)
        assertEquals(0, s.fatigue)
    }

    @Test
    fun `tips are short, positive and change with the plan of the day`() {
        assertEquals("Hoje tem treino. Vamos?", computeAvatarState(input(done = 1, planned = 3, since = 4, streak = 0, plannedToday = true), noon).tip)
        assertEquals("Treino de hoje feito!", computeAvatarState(input(done = 1, planned = 3, since = 0, streak = 0, rpes = emptyList(), plannedToday = true, doneToday = true), noon).tip)
        AvatarMood.entries.forEach { mood ->
            // every fixed line stays non-blank and fits one short line
            val s = computeAvatarState(input(), when (mood) { AvatarMood.SLEEPING -> 2; else -> noon })
            assertTrue(s.tip.isNotBlank() && s.tip.length <= 40, s.tip)
        }
    }

    // --- reading the inputs from the history ---

    private fun session(id: Long, daysAgo: Long, done: Boolean = true, rpe: Int? = null) =
        SessionFact(id, 1, today.minusDays(daysAgo), SessionKind.STRENGTH, done, skipped = false, rpe = rpe)

    private fun facts(vararg s: SessionFact) = GameFacts(s.toList(), emptyList(), emptyList(), emptyList(), 0, false, today)

    @Test
    fun `inputs count the last 7 days, the rest, the run of days and the latest rpes`() {
        val f = facts(
            session(1, 0, rpe = 8), session(2, 1, rpe = 9), session(3, 2, rpe = 7), session(4, 5), session(5, 9),
            session(6, 3, done = false),
        )
        val i = AvatarInputs.from(f, weekStreak = 2)
        assertEquals(4, i.doneLast7)
        assertEquals(5, i.plannedLast7)
        assertEquals(0, i.daysSinceLastWorkout)
        assertEquals(3, i.consecutiveTrainingDays)
        assertEquals(3, i.doneLast3Days)
        assertEquals(listOf(8, 9, 7), i.recentRpes)
        assertTrue(i.doneToday && i.plannedToday)
    }

    @Test
    fun `inputs with no history are empty and a gap breaks the run`() {
        val empty = AvatarInputs.from(facts(), 0)
        assertEquals(null, empty.daysSinceLastWorkout)
        assertEquals(0, empty.consecutiveTrainingDays)
        val gap = AvatarInputs.from(facts(session(1, 1), session(2, 3)), 0)
        assertEquals(1, gap.consecutiveTrainingDays)
        assertEquals(1, gap.daysSinceLastWorkout)
    }
}

class AvatarAnimatorTest {
    private val tick = AvatarAnimator.TICK_MS

    @Test
    fun `every event plays and then returns to the idle frame`() {
        AvatarEvent.entries.forEach { e ->
            val a = AvatarAnimator(AvatarMood.NORMAL)
            val steps = AvatarAnimator.eventSteps(e)
            assertTrue(steps.size in 4..30, "$e has ${steps.size} steps")
            a.play(e, 10_000)
            steps.forEachIndexed { i, f -> assertEquals(f, a.frameAt(10_000 + i * tick + 1), "$e step $i") }
            assertTrue(a.busy(10_000 + (steps.size - 1) * tick))
            val after = 10_000 + steps.size * tick
            assertFalse(a.busy(after))
            assertEquals(AvatarAnimator(AvatarMood.NORMAL).frameAt(after), a.frameAt(after))
        }
    }

    @Test
    fun `events use few distinct frames`() {
        AvatarEvent.entries.forEach { e ->
            assertTrue(AvatarAnimator.eventSteps(e).distinct().size <= 6, "$e")
        }
    }

    @Test
    fun `a mood change plays a short transition, not a hard swap`() {
        val a = AvatarAnimator(AvatarMood.NORMAL)
        a.setMood(AvatarMood.TIRED, 5_000)
        assertTrue(a.busy(5_000))
        val first = a.frameAt(5_000)
        assertEquals("idle_0", first.body)
        assertFalse(a.busy(5_000 + 3 * tick))
        // (a tired avatar may be dozing off by then, which also uses the sleep pose)
        assertTrue(a.frameAt(5_000 + 4 * tick).body.substringBefore('_') in setOf("tired", "sleep"))
        // Setting the same mood again does nothing.
        val b = AvatarAnimator(AvatarMood.TIRED)
        b.setMood(AvatarMood.TIRED, 1_000)
        assertFalse(b.busy(1_000))
    }

    @Test
    fun `each mood has its own idle look`() {
        val looks = AvatarMood.entries.map { m -> AvatarAnimator(m).frameAt(0).body.substringBefore('_') }
        assertEquals(listOf("flex", "idle", "tired", "sluggish", "sleep"), looks)
        // The sleeping avatar always shows zZ.
        val sleeping = AvatarAnimator(AvatarMood.SLEEPING)
        for (t in 0 until 400L) {
            val f = sleeping.frameAt(t * tick)
            assertTrue(f.effects.any { it.startsWith("z_") })
        }
    }

    @Test
    fun `it bobs and does a random behaviour from time to time`() {
        val a = AvatarAnimator(AvatarMood.NORMAL)
        val frames = (0 until 1800L).map { a.frameAt(it * tick) }
        assertTrue(frames.map { it.body }.toSet().containsAll(listOf("idle_0", "idle_1", "idle_2", "idle_3")))
        val calm = setOf("idle", "blink")
        val stretchOrScratch = frames.indices.filter { frames[it].body.substringBefore('_') !in calm || frames[it].x != AvatarSprites.HOME_X }
        assertTrue(stretchOrScratch.isNotEmpty())
        // Behaviours start 9 to 15 s apart.
        val starts = stretchOrScratch.filter { it == 0 || it - 1 !in stretchOrScratch }
        val gaps = starts.zipWithNext { x, y -> (y - x) * tick }
        assertTrue(gaps.all { it in 8_000..16_000 }, "gaps $gaps")
    }

    @Test
    fun `a tired avatar dozes off instead of stretching`() {
        val a = AvatarAnimator(AvatarMood.TIRED)
        val frames = (0 until 600L).map { a.frameAt(it * tick) }
        assertTrue(frames.any { it.body.startsWith("sleep") && it.effects.isNotEmpty() })
        assertFalse(frames.any { it.body.startsWith("cheer") || it.body.startsWith("flex") || it.body.startsWith("walk") })
    }

    @Test
    fun `it blinks now and then, in the phase of its breathing`() {
        val a = AvatarAnimator(AvatarMood.NORMAL)
        val frames = (0 until 300L).map { a.frameAt(it * tick) }
        val blinks = frames.indices.filter { frames[it].body.startsWith("blink") }
        assertTrue(blinks.size in 5..20, "blinks ${blinks.size}")
        // A blink lasts one tick and shows the idle drawing of that moment with the eyes shut.
        blinks.forEach { i ->
            assertTrue(i == 0 || !frames[i - 1].body.startsWith("blink"))
            assertEquals("blink_${(i / 2) % 4}", frames[i].body) // idle holds each of its 4 frames for 2 ticks
        }
        assertTrue(AvatarAnimator(AvatarMood.TIRED).let { t -> (0 until 300L).none { t.frameAt(it * tick).body.startsWith("blink") } })
    }

    @Test
    fun `it walks to both sides, mirrored when going left, and comes back home`() {
        val frames = (0..20).flatMap { seed -> val a = AvatarAnimator(AvatarMood.NORMAL, seed); (0 until 1200L).map { a.frameAt(it * tick) } }
        val walking = frames.filter { it.body.startsWith("walk") }
        assertTrue(walking.any { it.flip } && walking.any { !it.flip })
        assertTrue(frames.all { it.x in 0..AvatarSprites.WIDTH - AvatarSprites.BODY_WIDTH })
        assertTrue(frames.any { it.body.startsWith("idle") && it.x != AvatarSprites.HOME_X }) // looks around away from home
        // one step per tick: the position never jumps
        frames.zipWithNext().filter { (p, q) -> p.body.startsWith("walk") && q.body.startsWith("walk") }
            .forEach { (p, q) -> assertTrue(kotlin.math.abs(p.x - q.x) <= 1) }
    }

    @Test
    fun `the animation is deterministic for a given seed`() {
        val a = AvatarAnimator(AvatarMood.NORMAL, seed = 3)
        val b = AvatarAnimator(AvatarMood.NORMAL, seed = 3)
        for (t in 0 until 300L) assertEquals(a.frameAt(t * tick), b.frameAt(t * tick))
    }

    @Test
    fun `reduced motion has one still pose per mood`() {
        AvatarMood.entries.forEach { m ->
            assertEquals(AvatarAnimator.staticFrame(m), AvatarAnimator(m).staticFrame())
        }
        assertEquals(5, AvatarMood.entries.map { AvatarAnimator.staticFrame(it) }.distinct().size)
    }
}
