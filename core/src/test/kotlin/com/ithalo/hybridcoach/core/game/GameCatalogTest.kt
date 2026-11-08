package com.ithalo.hybridcoach.core.game

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameCatalogTest {
    private val start = LocalDate.of(2026, 1, 5) // Monday

    /** 30 weeks, every week: 2 strength + 2 run sessions, one hybrid day; everything done. */
    private fun completeHistory(summaries: Int = 1): GameFacts {
        val sessions = mutableListOf<SessionFact>()
        val sets = mutableListOf<SetFact>()
        val runs = mutableListOf<RunFact>()
        var id = 1L
        repeat(30) { w ->
            val monday = start.plusWeeks(w.toLong())
            listOf(0L, 2L).forEach { d ->
                val s = SessionFact(id++, 1, monday.plusDays(d), SessionKind.STRENGTH, true, false, 8)
                sessions += s
                repeat(90) { k -> sets += SetFact(s.sessionId, "ex${k % 10}", "Exercício ${k % 10}", 5, 100.0 + w, false, s.date) }
            }
            listOf(2L, 5L).forEach { d ->
                val s = SessionFact(id++, 1, monday.plusDays(d), SessionKind.RUN, true, false, 6)
                sessions += s
                val meters = if (w == 29 && d == 5L) 42300.0 else 9000.0
                runs += RunFact(s.sessionId, s.date, meters, (meters * 0.29).toLong(), null, listOf(300, 290))
            }
        }
        val end = start.plusWeeks(30).minusDays(1)
        return GameFacts(
            sessions, sets, runs,
            plans = listOf(PlanFact(1, start, end, sessions.size, sessions.size)),
            summariesExported = summaries, questionnaireDone = true, today = end.plusDays(1),
        )
    }

    @Test
    fun `platinum unlocks with the last non hidden trophy`() {
        val state = GameEngine.evaluate(completeHistory())
        val required = state.achievements.filter { !it.def.hidden && it.def.tier != TrophyTier.PLATINUM }
        assertTrue(required.all { it.unlocked }, required.filter { !it.unlocked }.joinToString { it.def.id })
        val platinum = state.achievements.single { it.def.id == AchievementCatalog.PLATINUM_ID }
        assertEquals(required.maxOf { it.unlockedOn!! }, platinum.unlockedOn)
        assertEquals(required.size, platinum.progress)
        assertEquals(required.size, platinum.def.target)
        assertTrue(state.items.single { it.def.id == "frame_platinum" }.unlocked)
        // Hidden ones are optional: the history never skips a session.
        assertFalse(state.achievements.single { it.def.id == "first_skip" }.unlocked)
    }

    @Test
    fun `platinum stays locked while one trophy is missing`() {
        val state = GameEngine.evaluate(completeHistory(summaries = 0))
        val platinum = state.achievements.single { it.def.id == AchievementCatalog.PLATINUM_ID }
        assertNull(platinum.unlockedOn)
        assertEquals(platinum.def.target - 1, platinum.progress)
    }

    @Test
    fun `evaluation is deterministic`() {
        assertEquals(GameEngine.evaluate(completeHistory()), GameEngine.evaluate(completeHistory()))
    }

    @Test
    fun `unlocks since lists only unseen things in order`() {
        val state = GameEngine.evaluate(completeHistory())
        val unlocks = GameEngine.unlocksSince(1, emptySet(), emptySet(), state)
        assertEquals(Unlock.LevelUp(state.level, state.rankTitle), unlocks.first())
        val trophies = unlocks.filterIsInstance<Unlock.AchievementUnlocked>()
        assertEquals(state.achievements.count { it.unlocked }, trophies.size)
        val tiers = trophies.map { it.achievement.def.tier.ordinal }
        assertEquals(tiers.sorted(), tiers)
        assertEquals(TrophyTier.PLATINUM, trophies.last().achievement.def.tier)
        val firstItem = unlocks.indexOfFirst { it is Unlock.ItemUnlocked }
        assertTrue(firstItem > unlocks.indexOfLast { it is Unlock.AchievementUnlocked })
        assertEquals(state.items.count { it.unlocked }, unlocks.size - firstItem)

        val seenTrophies = state.achievements.filter { it.unlocked }.map { it.def.id }.toSet()
        val seenItems = state.items.filter { it.unlocked }.map { it.def.id }.toSet()
        assertTrue(GameEngine.unlocksSince(state.level, seenTrophies, seenItems, state).isEmpty())

        val one = GameEngine.unlocksSince(state.level, seenTrophies - "first_run", seenItems, state)
        assertEquals("first_run", (one.single() as Unlock.AchievementUnlocked).achievement.def.id)
    }

    @Test
    fun `level curve boundaries`() {
        assertEquals(0, Leveling.xpForLevel(1))
        assertEquals(50, Leveling.stepXp(1))
        assertEquals(141, Leveling.stepXp(2))
        assertEquals(50, Leveling.xpForLevel(2))
        assertEquals(191, Leveling.xpForLevel(3))
        assertEquals(1, Leveling.levelFor(0))
        assertEquals(1, Leveling.levelFor(49))
        assertEquals(2, Leveling.levelFor(50))
        assertEquals(2, Leveling.levelFor(190))
        assertEquals(3, Leveling.levelFor(191))
        for (l in 2..Leveling.MAX_LEVEL) {
            assertTrue(Leveling.stepXp(l) > Leveling.stepXp(l - 1))
            assertEquals(l, Leveling.levelFor(Leveling.xpForLevel(l)))
            assertEquals(l - 1, Leveling.levelFor(Leveling.xpForLevel(l) - 1))
        }
        assertEquals(Leveling.MAX_LEVEL, Leveling.levelFor(Int.MAX_VALUE))
    }

    @Test
    fun `rank titles change by band`() {
        assertEquals("Novato 8-bit", Leveling.rankTitle(1))
        assertEquals("Novato 8-bit", Leveling.rankTitle(4))
        assertEquals("Aprendiz de Disquete", Leveling.rankTitle(5))
        assertEquals("Herói do Fliperama", Leveling.rankTitle(98))
        assertEquals("Lenda do Pixel", Leveling.rankTitle(99))
        assertEquals(10, (1..99).map { Leveling.rankTitle(it) }.distinct().size)
    }

    @Test
    fun `achievement catalog is sane`() {
        val all = AchievementCatalog.all
        assertTrue(all.size in 35..45)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.title }.toSet().size)
        assertTrue(all.all { it.icon in GameIcons.all }, all.filter { it.icon !in GameIcons.all }.joinToString { it.id })
        assertTrue(all.all { it.title.isNotBlank() && it.description.isNotBlank() && it.target >= 1 && it.xp > 0 })
        val byTier = all.groupingBy { it.tier }.eachCount()
        assertEquals(1, byTier[TrophyTier.PLATINUM])
        assertTrue(byTier.getValue(TrophyTier.BRONZE) > byTier.getValue(TrophyTier.SILVER))
        assertTrue(byTier.getValue(TrophyTier.SILVER) > byTier.getValue(TrophyTier.GOLD))
        assertTrue(all.any { it.hidden })
        assertFalse(all.single { it.tier == TrophyTier.PLATINUM }.hidden)
    }

    @Test
    fun `item catalog is sane`() {
        val all = ItemCatalog.all
        assertTrue(all.size in 18..26)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.icon in GameIcons.all && it.name.isNotBlank() && it.description.isNotBlank() })
        val trophyIds = AchievementCatalog.all.map { it.id }.toSet()
        all.forEach { item ->
            when (val u = item.unlock) {
                is ItemUnlock.Achievement -> assertTrue(u.id in trophyIds, "${item.id} -> ${u.id}")
                is ItemUnlock.Level -> assertTrue(u.level in 1..Leveling.MAX_LEVEL)
            }
        }
        assertEquals(ItemKind.entries.toSet(), all.map { it.kind }.toSet())
    }
}

class XpTableTest {
    @Test
    fun `the points table follows the engine constants`() {
        val rules = XpTable.rules.associate { it.label to it.amount }
        assertEquals(GameEngine.XP_SESSION.toString(), rules["Sessão concluída"])
        assertEquals(GameEngine.XP_PER_SET.toString(), rules["Cada série registrada"])
        assertEquals(WeeklyMissions.XP_MISSION.toString(), rules["Missão da semana"])
        assertEquals("10", rules["Troféu de bronze"])
        assertEquals("250", rules["Troféu de platina"])
        assertTrue(XpTable.rules.size >= 14 && XpTable.rules.all { it.amount.isNotBlank() })
    }
}
