package com.ithalo.hybridcoach.core.game

/** One line of the points table: what gives XP, and how much. */
data class XpRule(val label: String, val amount: String)

/** The points table shown next to the level, built from the engine's own constants so it cannot drift. */
object XpTable {
    val rules: List<XpRule> = listOf(
        XpRule("Sessão concluída", "${GameEngine.XP_SESSION}"),
        XpRule("Cada série registrada", "${GameEngine.XP_PER_SET}"),
        XpRule("Recorde pessoal (força ou corrida)", "${GameEngine.XP_PR}"),
        XpRule("Corrida, por km", "${GameEngine.XP_PER_200M * 5}"),
        XpRule("Missão da semana", "${WeeklyMissions.XP_MISSION}"),
        XpRule("Todas as missões", "${WeeklyMissions.XP_ALL_MISSIONS}"),
        XpRule("Semana perfeita", "${GameEngine.XP_PERFECT_WEEK}"),
        XpRule("Sequência, por semana", "${GameEngine.XP_STREAK_PER_WEEK} (até ${GameEngine.XP_STREAK_PER_WEEK * GameEngine.STREAK_CAP_WEEKS})"),
        XpRule("Bloco concluído", "${GameEngine.XP_BLOCK}"),
        XpRule("Bloco 100% concluído", "+${GameEngine.XP_BLOCK_PERFECT}"),
        XpRule("Questionário respondido", "${GameEngine.XP_QUESTIONNAIRE}"),
        XpRule("Resumo exportado", "${GameEngine.XP_SUMMARY}"),
        XpRule("Troféu de bronze", "${AchievementCatalog.xpOf(TrophyTier.BRONZE)}"),
        XpRule("Troféu de prata", "${AchievementCatalog.xpOf(TrophyTier.SILVER)}"),
        XpRule("Troféu de ouro", "${AchievementCatalog.xpOf(TrophyTier.GOLD)}"),
        XpRule("Troféu de platina", "${AchievementCatalog.xpOf(TrophyTier.PLATINUM)}"),
    )
}
