package com.ithalo.hybridcoach.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.block.ProgressionRules
import com.ithalo.hybridcoach.data.AppSettings
import com.ithalo.hybridcoach.ui.player.formatKg
import com.ithalo.hybridcoach.ui.common.HelpText
import com.ithalo.hybridcoach.ui.common.IntStepper
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.theme.Retro

/**
 * Ajustes > Progressão: the rules the app uses to suggest the next block's start and to check the new plan.
 * Common heuristics, not a prescription; "Voltar ao padrão" drops every change.
 */
@Composable
fun ProgressionPage(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val repository = appContainer().repository
    val r = remember(s.progressionRules) { repository.progressionRules(s.progressionRules) }
    fun set(next: ProgressionRules) = update { it.copy(progressionRules = next.toJson()) }
    HelpText(stringResource(R.string.prog_intro))
    SectionTitle(stringResource(R.string.prog_strength))
    // Loads in half kilos.
    IntStepper(stringResource(R.string.prog_increment), (r.incrementKg * 2).toInt(), 1..20, format = { formatKg(it / 2.0) + " kg" }) { set(r.copy(incrementKg = it / 2.0)) }
    IntStepper(stringResource(R.string.prog_small_increment), (r.smallIncrementKg * 2).toInt(), 1..10, format = { formatKg(it / 2.0) + " kg" }) { set(r.copy(smallIncrementKg = it / 2.0)) }
    IntStepper(stringResource(R.string.prog_heavy_from), r.heavyFromKg.toInt(), 5..60, step = 5, suffix = " kg") { set(r.copy(heavyFromKg = it.toDouble())) }
    IntStepper(stringResource(R.string.prog_max_rpe), r.maxRpeToIncrease.toInt(), 6..10) { set(r.copy(maxRpeToIncrease = it.toDouble())) }
    IntStepper(stringResource(R.string.prog_reduce), r.reduceAfterFailuresPct, 0..20, suffix = "%") { set(r.copy(reduceAfterFailuresPct = it)) }
    SectionTitle(stringResource(R.string.prog_run))
    IntStepper(stringResource(R.string.prog_run_increase), r.maxWeeklyRunIncreasePct, 5..30, suffix = "%") { set(r.copy(maxWeeklyRunIncreasePct = it)) }
    HelpText(stringResource(R.string.prog_run_help))
    SectionTitle(stringResource(R.string.prog_continuity))
    IntStepper(stringResource(R.string.prog_min_adherence), r.minAdherencePct, 30..90, step = 5, suffix = "%") { set(r.copy(minAdherencePct = it)) }
    IntStepper(stringResource(R.string.prog_long_pause), r.longPauseDays, 7..42, step = 7, suffix = " dias") { set(r.copy(longPauseDays = it)) }
    IntStepper(stringResource(R.string.prog_swap), r.maxAccessorySwapPct, 0..100, step = 10, suffix = "%") { set(r.copy(maxAccessorySwapPct = it)) }
    IntStepper(stringResource(R.string.prog_jump), r.loadJumpWarnPct, 5..30, step = 5, suffix = "%") { set(r.copy(loadJumpWarnPct = it)) }
    RetroOutlinedButton(onClick = { update { it.copy(progressionRules = null) } }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.prog_reset))
    }
    Text(stringResource(R.string.prog_disclaimer), style = Retro.type.bodySmall, color = Retro.colors.inkSoft)
}
