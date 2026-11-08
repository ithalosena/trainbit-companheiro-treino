package com.ithalo.hybridcoach.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroCard
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.data.ExerciseEntity
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.data.SetLogRow
import com.ithalo.hybridcoach.data.PlannedSessionEntity
import com.ithalo.hybridcoach.ui.common.ChoiceChips
import com.ithalo.hybridcoach.ui.common.SessionCard
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.setLine
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appViewModel
import com.ithalo.hybridcoach.ui.common.short
import com.ithalo.hybridcoach.ui.player.formatKg
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class HistoryViewModel(repository: PlanRepository) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>?> = repository.observeExercisesWithLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sessions: StateFlow<List<PlannedSessionEntity>> = repository.observeDoneSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class ExerciseHistoryViewModel(repository: PlanRepository, exerciseId: String) : ViewModel() {
    val rows: StateFlow<List<SetLogRow>?> = repository.observeExerciseHistory(exerciseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onExercise: (String) -> Unit, onSession: (Long) -> Unit) {
    val vm = appViewModel { HistoryViewModel(it) }
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var bySession by rememberSaveable { mutableStateOf(true) }
    ScreenScaffold(stringResource(R.string.history_program), onBack) {
        val list = exercises ?: return@ScreenScaffold
        val c = Retro.colors
        ScreenColumn {
            ChoiceChips(listOf(true, false), bySession, { stringResource(if (it) R.string.history_by_session else R.string.history_by_exercise) }) { bySession = it }
            if (bySession) {
                Text(stringResource(R.string.history_sessions_intro), style = Retro.type.body, color = c.inkSoft)
                if (sessions.isEmpty()) InfoCard(stringResource(R.string.history_sessions_empty))
                sessions.forEach { s -> SessionCard(s, showDate = true) { onSession(s.id) } }
                return@ScreenColumn
            }
            Text(stringResource(R.string.history_intro), style = Retro.type.body, color = c.inkSoft)
            if (list.isEmpty()) InfoCard(stringResource(R.string.history_empty))
            list.forEach { exercise ->
                RetroCard(Modifier.fillMaxWidth(), onClick = { onExercise(exercise.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelIcon("chart", tint = c.accent2, size = 22.dp)
                        Text(exercise.canonicalName, Modifier.weight(1f), style = Retro.type.heading)
                        PixelIcon("arrow_right", tint = c.inkSoft, size = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseHistoryScreen(exerciseId: String, onBack: () -> Unit) {
    val vm = appViewModel { ExerciseHistoryViewModel(it, exerciseId) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    ScreenScaffold(rows?.firstOrNull()?.exerciseName ?: stringResource(R.string.history_title), onBack) {
        val list = rows ?: return@ScreenScaffold
        val c = Retro.colors
        ScreenColumn {
            val bySession = list.groupBy { it.log.sessionId }.values.toList()
            val points = bySession.map { LocalDate.parse(it.first().sessionDate) to it.maxOf { r -> r.log.loadKg } }
            if (points.isNotEmpty()) {
                RetroWindow(stringResource(R.string.history_chart_window), Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.history_load_over_time), style = Retro.type.label, color = c.inkSoft)
                    LoadChart(points)
                }
            }
            bySession.reversed().forEach { sets ->
                val date = LocalDate.parse(sets.first().sessionDate)
                SectionTitle(date.short())
                sets.forEach { row -> Text(setLine(row.log, row.plannedReps), style = Retro.type.body) }
            }
        }
    }
}

/** Pixel line chart of the top load per session: stepped segments and square points, no library. */
@Composable
private fun LoadChart(points: List<Pair<LocalDate, Double>>) {
    val c = Retro.colors
    val line = c.accent2
    val grid = c.inkFaint
    val point = c.accent
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${formatKg(max)} kg", style = Retro.type.label)
        Text("${formatKg(min)} kg", style = Retro.type.label)
    }
    Canvas(Modifier.fillMaxWidth().height(160.dp).bevel(c.windowInset, c.border, c.bevelLight, c.bevelDark, raised = false)) {
        val pad = 14.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val px = 2.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        fun pointAt(i: Int, value: Double) = Offset(
            x = pad + if (points.size == 1) w / 2 else w * i / (points.size - 1),
            y = pad + h * (1 - ((value - min) / range).toFloat()).let { if (max == min) 0.5f else it },
        )
        // Dotted grid lines.
        for (k in 0..3) {
            val y = pad + h * k / 3f
            var x = pad
            while (x < size.width - pad) {
                drawRect(grid, Offset(x, y), Size(px, px))
                x += px * 3
            }
        }
        // Stairs between points: horizontal then vertical, like an 8-bit plot.
        for (i in 1 until points.size) {
            val a = pointAt(i - 1, points[i - 1].second)
            val b = pointAt(i, points[i].second)
            drawRect(line, Offset(a.x, a.y - px / 2), Size(b.x - a.x, px))
            drawRect(line, Offset(b.x - px / 2, minOf(a.y, b.y)), Size(px, kotlin.math.abs(b.y - a.y) + px / 2))
        }
        points.forEachIndexed { i, (_, value) ->
            val p = pointAt(i, value)
            val s = px * 3
            drawRect(point, Offset(p.x - s / 2, p.y - s / 2), Size(s, s))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(points.first().first.short(), style = Retro.type.label)
        if (points.size > 1) Text(points.last().first.short(), style = Retro.type.label)
    }
}
