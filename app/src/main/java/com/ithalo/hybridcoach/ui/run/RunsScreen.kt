package com.ithalo.hybridcoach.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.strength.TimedTarget
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.data.PlannedSessionEntity
import com.ithalo.hybridcoach.data.RunHistoryRow
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.SessionCard
import com.ithalo.hybridcoach.ui.common.appViewModel
import com.ithalo.hybridcoach.ui.common.long
import com.ithalo.hybridcoach.ui.common.paceText
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroCard
import com.ithalo.hybridcoach.ui.retro.RetroLoading
import com.ithalo.hybridcoach.ui.retro.StatTile
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class RunsUi(val upcoming: List<PlannedSessionEntity>, val history: List<RunHistoryRow>)

@OptIn(ExperimentalCoroutinesApi::class)
class RunsViewModel(repository: PlanRepository) : ViewModel() {
    private val upcoming = repository.observeActivePlan().flatMapLatest { plan ->
        if (plan == null) flowOf(emptyList()) else repository.observeSessions(plan.id)
    }

    val ui: StateFlow<RunsUi?> = combine(upcoming, repository.observeRunHistory()) { sessions, history ->
        val today = LocalDate.now().toString()
        RunsUi(
            upcoming = sessions.filter { it.type == "run" && it.status == "pending" && it.date >= today },
            history = history,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Corrida tab: the next planned run (with a start button), the rest of the upcoming runs, totals and past runs. */
@Composable
fun RunsScreen(onStart: (Long) -> Unit, onOpenSession: (Long) -> Unit) {
    val vm = appViewModel { RunsViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.program_runs)) {
        val state = ui ?: run {
            RetroLoading()
            return@ScreenScaffold
        }
        ScreenColumn {
            SectionTitle(stringResource(R.string.runs_next))
            val next = state.upcoming.firstOrNull()
            if (next == null) {
                InfoCard(stringResource(R.string.runs_none_planned))
            } else {
                SessionCard(next, showDate = true) { onOpenSession(next.id) }
                RetroButton(onClick = { onStart(next.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    PixelIcon("play", size = 18.dp)
                    Text(stringResource(R.string.runs_start))
                }
            }
            if (state.upcoming.size > 1) {
                SectionTitle(stringResource(R.string.runs_upcoming))
                state.upcoming.drop(1).take(5).forEach { s -> SessionCard(s, showDate = true) { onOpenSession(s.id) } }
            }

            SectionTitle(stringResource(R.string.runs_history))
            if (state.history.isEmpty()) {
                Text(stringResource(R.string.runs_history_empty), style = Retro.type.body, color = Retro.colors.inkSoft)
            } else {
                val km = state.history.sumOf { it.log.distanceM } / 1000.0
                val seconds = state.history.sumOf { it.log.durationSeconds }.toInt()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatTile("run", state.history.size.toString(), stringResource(R.string.runs_total_count))
                    StatTile("shoe", "%.1f".format(PT_BR, km), stringResource(R.string.runs_total_km))
                    StatTile("clock", TimedTarget.format(seconds), stringResource(R.string.runs_total_time))
                }
                state.history.forEach { RunHistoryCard(it) { onOpenSession(it.log.sessionId) } }
            }
        }
    }
}

@Composable
private fun RunHistoryCard(row: RunHistoryRow, onClick: () -> Unit) {
    val c = Retro.colors
    val log = row.log
    RetroCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PixelIcon("run", tint = c.inkSoft, size = 24.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = Retro.type.heading, color = c.ink)
                Text(LocalDate.parse(row.date).long(), style = Retro.type.bodySmall, color = c.inkSoft)
                val parts = buildList {
                    add("%.2f km".format(PT_BR, log.distanceM / 1000.0))
                    add(TimedTarget.format(log.durationSeconds.toInt()))
                    log.avgPaceSecPerKm?.let { add("${paceText(it)}/km") }
                }
                Text(parts.joinToString(" · "), style = Retro.type.body, color = c.ink)
            }
            PixelIcon("arrow_right", tint = c.inkFaint, size = 16.dp)
        }
    }
}
