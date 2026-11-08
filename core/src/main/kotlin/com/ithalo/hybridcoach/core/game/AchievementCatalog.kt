package com.ithalo.hybridcoach.core.game

import com.ithalo.hybridcoach.core.game.TrophyTier.BRONZE
import com.ithalo.hybridcoach.core.game.TrophyTier.GOLD
import com.ithalo.hybridcoach.core.game.TrophyTier.PLATINUM
import com.ithalo.hybridcoach.core.game.TrophyTier.SILVER

/** Running totals while the history is replayed; achievements read their progress from it. */
internal class Progress {
    var sessionsDone = 0
    var strengthDone = 0
    var runsDone = 0
    var totalSets = 0
    var tonnageKg = 0.0
    var runMeters = 0.0
    var runSeconds = 0L
    var longestRunM = 0.0
    var bestPace: Int? = null
    var negativeSplits = 0
    var sub5Runs = 0
    var prs = 0
    val exercises = mutableSetOf<String>()
    var maxValidLoadKg = 0.0
    var weekStreak = 0
    var bestWeekStreak = 0
    var perfectWeeks = 0
    var blocksDone = 0
    var blocksPerfect = 0
    var summaries = 0
    var questionnaire = 0
    var sundays = 0
    var holidays = 0
    var rpe10 = 0
    var maxFailsInSession = 0
    var comebacks = 0
    var skips = 0
    var hybridDays = 0
}

/**
 * Tier rewards: bronze 10 XP, silver 30, gold 80, platinum 250.
 * Platinum rule: "Platina" unlocks when every non-hidden bronze, silver and gold trophy is unlocked
 * (hidden trophies are optional).
 */
object AchievementCatalog {
    private class Rule(val def: AchievementDef, val need: Double? = null, val metric: Progress.() -> Number)

    fun xpOf(tier: TrophyTier) = when (tier) {
        BRONZE -> 10
        SILVER -> 30
        GOLD -> 80
        PLATINUM -> 250
    }

    private fun rule(
        id: String, title: String, description: String, tier: TrophyTier, icon: String, target: Int = 1,
        hidden: Boolean = false, need: Double? = null, metric: Progress.() -> Number,
    ) = Rule(AchievementDef(id, title, description, tier, icon, hidden, target, xpOf(tier)), need, metric)

    private const val KM = 1000.0

    private val rules: List<Rule> = listOf(
        // Primeiros passos
        rule("first_session", "Pressione Start", "Conclua sua primeira sessão.", BRONZE, "star") { sessionsDone },
        rule("first_set", "Ferro Frio", "Registre sua primeira série.", BRONZE, "dumbbell") { totalSets },
        rule("first_run", "Primeiros Passos", "Conclua sua primeira corrida.", BRONZE, "shoe") { runsDone },
        rule("questionnaire", "Ficha do Personagem", "Responda ao questionário inicial.", BRONZE, "heart") { questionnaire },
        rule("export_summary", "Salvar Jogo", "Exporte o resumo de um bloco.", BRONZE, "floppy") { summaries },
        // Constância
        rule("sessions_10", "Jogador Assíduo", "Conclua 10 sessões.", BRONZE, "check", 10) { sessionsDone },
        rule("sessions_50", "Veterano", "Conclua 50 sessões.", SILVER, "medal", 50) { sessionsDone },
        rule("sessions_100", "Cem Sessões", "Conclua 100 sessões.", GOLD, "trophy", 100) { sessionsDone },
        rule("streak_4", "Combo x4", "Treine 4 semanas seguidas.", BRONZE, "flame", 4) { bestWeekStreak },
        rule("streak_12", "Combo x12", "Treine 12 semanas seguidas.", SILVER, "flame", 12) { bestWeekStreak },
        rule("perfect_week", "Semana Perfeita", "Conclua todas as sessões planejadas de uma semana.", BRONZE, "calendar") { perfectWeeks },
        rule("perfect_weeks_4", "Quarteto Perfeito", "Tenha 4 semanas perfeitas.", SILVER, "calendar", 4) { perfectWeeks },
        rule("block_done", "Fim de Fase", "Termine um bloco com pelo menos metade das sessões feitas.", BRONZE, "folder") { blocksDone },
        rule("block_perfect", "Fase Sem Dano", "Termine um bloco com 100% das sessões feitas.", GOLD, "crown") { blocksPerfect },
        rule("hybrid_day", "Modo Híbrido", "Faça força e corrida no mesmo dia.", BRONZE, "lightning") { hybridDays },
        // Força
        rule("strength_10", "Rato de Academia", "Conclua 10 sessões de força.", BRONZE, "dumbbell", 10) { strengthDone },
        rule("sets_100", "Cem Séries", "Registre 100 séries.", BRONZE, "dumbbell", 100) { totalSets },
        rule("sets_1000", "Mil Séries", "Registre 1.000 séries.", SILVER, "dumbbell", 1000) { totalSets },
        rule("sets_5000", "Cinco Mil Séries", "Registre 5.000 séries.", GOLD, "dumbbell", 5000) { totalSets },
        rule("tonnage_10t", "Dez Toneladas", "Levante 10 toneladas somadas (reps × carga).", BRONZE, "coin", 10) { tonnageKg / 1000 },
        rule("tonnage_100t", "Cem Toneladas", "Levante 100 toneladas somadas (reps × carga).", SILVER, "coin", 100) { tonnageKg / 1000 },
        rule("first_pr", "Novo Recorde!", "Supere um recorde pessoal.", BRONZE, "medal") { prs },
        rule("prs_10", "Caçador de Recordes", "Supere recordes pessoais 10 vezes.", SILVER, "medal", 10) { prs },
        rule("exercises_10", "Arsenal Variado", "Registre séries de 10 exercícios diferentes.", BRONZE, "star", 10) { exercises.size },
        rule("heavy_100", "Clube dos 100 kg", "Complete uma série com 100 kg ou mais.", BRONZE, "dumbbell", 100) { maxValidLoadKg },
        // Corrida
        rule("run_10", "Asfalto Conhecido", "Conclua 10 corridas.", BRONZE, "shoe", 10) { runsDone },
        rule("km_5", "Aquecendo o Motor", "Acumule 5 km de corrida.", BRONZE, "map", 5) { runMeters / KM },
        rule("km_42", "Maratona Parcelada", "Acumule 42,2 km de corrida.", BRONZE, "map", 42, need = 42.195) { runMeters / KM },
        rule("km_100", "Centenário", "Acumule 100 km de corrida.", SILVER, "map", 100) { runMeters / KM },
        rule("km_500", "Volta ao Mapa", "Acumule 500 km de corrida.", GOLD, "map", 500) { runMeters / KM },
        rule("run_5k", "5K na Conta", "Corra 5 km numa única corrida.", BRONZE, "shoe", 5) { longestRunM / KM },
        rule("run_10k", "Dez Mil Metros", "Corra 10 km numa única corrida.", SILVER, "shoe", 10) { longestRunM / KM },
        rule("run_21k", "Meia Maratona", "Corra 21,1 km numa única corrida.", SILVER, "mountain", 21, need = 21.0975) { longestRunM / KM },
        rule("run_42k", "Maratonista", "Corra 42,2 km numa única corrida.", GOLD, "mountain", 42, need = 42.195) { longestRunM / KM },
        rule("negative_split", "Split Negativo", "Corra a segunda metade mais rápido que a primeira.", BRONZE, "clock") { negativeSplits },
        rule("sub5_pace", "Turbo", "Corra 3 km ou mais com ritmo médio abaixo de 5:00/km.", BRONZE, "lightning") { sub5Runs },
        rule("time_10h", "Horas de Jogo", "Acumule 10 horas correndo.", BRONZE, "hourglass", 10) { runSeconds / 3600.0 },
        // Ocultos
        rule("sunday", "Domingo É Dia de Treino", "Treine num domingo.", BRONZE, "calendar", hidden = true) { sundays },
        rule("rpe10", "No Limite", "Termine uma sessão com RPE 10.", BRONZE, "skull", hidden = true) { rpe10 },
        rule("three_fails", "Persistência", "Falhe 3 séries numa mesma sessão.", BRONZE, "warning", 3, hidden = true) { maxFailsInSession },
        rule("comeback", "Continue?", "Volte a treinar depois de 4 semanas ou mais parado.", BRONZE, "coin", hidden = true) { comebacks },
        rule("holiday", "Feriado? Que Feriado?", "Treine no Natal ou no Ano-Novo.", BRONZE, "star", hidden = true) { holidays },
        rule("first_skip", "Game Over (Por Hoje)", "Pule uma sessão. Acontece!", BRONZE, "hourglass", hidden = true) { skips },
    )

    private val platinum = AchievementDef(
        id = PLATINUM_ID,
        title = "Platina",
        description = "Desbloqueie todos os troféus não ocultos.",
        tier = PLATINUM,
        icon = "trophy",
        hidden = false,
        target = rules.count { !it.def.hidden },
        xp = xpOf(PLATINUM),
    )

    const val PLATINUM_ID = "platina"

    val all: List<AchievementDef> = rules.map { it.def } + platinum

    private val byId = rules.associateBy { it.def.id }

    /**
     * Progress of a non-platinum trophy, capped at its target. When a trophy has an exact threshold
     * (e.g. 21.0975 km) above its integer target, progress stays below target until it is reached.
     */
    internal fun progressOf(def: AchievementDef, p: Progress): Int {
        val rule = byId.getValue(def.id)
        val value = rule.metric(p).toDouble()
        val need = rule.need ?: def.target.toDouble()
        return if (value >= need) def.target else minOf(value.toInt(), def.target - 1).coerceAtLeast(0)
    }
}
