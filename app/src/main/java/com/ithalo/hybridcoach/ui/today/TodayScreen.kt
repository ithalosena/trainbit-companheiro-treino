package com.ithalo.hybridcoach.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.core.game.GameEngine
import com.ithalo.hybridcoach.core.game.MissionKind
import com.ithalo.hybridcoach.core.game.MissionState
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.retro.PixelSprite
import com.ithalo.hybridcoach.ui.retro.RetroDivider
import com.ithalo.hybridcoach.ui.retro.RetroLoading
import com.ithalo.hybridcoach.ui.retro.RetroRewardButton
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import androidx.compose.ui.res.pluralStringResource
import com.ithalo.hybridcoach.core.block.BlockState
import com.ithalo.hybridcoach.core.game.GameState
import com.ithalo.hybridcoach.core.schedule.PlanScheduler
import com.ithalo.hybridcoach.data.PlanEntity
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.data.PlannedSessionEntity
import com.ithalo.hybridcoach.data.RunProgressEntity
import com.ithalo.hybridcoach.run.RunTracker
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SessionCard
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.common.appViewModel
import com.ithalo.hybridcoach.ui.common.short
import com.ithalo.hybridcoach.ui.common.typeIcon
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroLinearProgress
import com.ithalo.hybridcoach.ui.retro.TypewriterText
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.ithalo.hybridcoach.core.schedule.Modality
import com.ithalo.hybridcoach.core.schedule.ModalityQueue
import com.ithalo.hybridcoach.core.schedule.QueueSession
import com.ithalo.hybridcoach.core.schedule.Reentry
import com.ithalo.hybridcoach.data.REENTRY_DECLINED
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import kotlinx.coroutines.launch

data class PlanWithSessions(val plan: PlanEntity?, val sessions: List<PlannedSessionEntity>)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(repository: PlanRepository) : ViewModel() {
    val state: StateFlow<PlanWithSessions?> = repository.observeActivePlan()
        .flatMapLatest { plan ->
            if (plan == null) flowOf(PlanWithSessions(null, emptyList()))
            else repository.observeSessions(plan.id).map { PlanWithSessions(plan, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val runProgress: StateFlow<RunProgressEntity?> = repository.observeRunProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun TodayScreen(onOpenSession: (Long) -> Unit, onStart: (Long, String) -> Unit, onCreateBlock: () -> Unit, onProfile: () -> Unit, onCloseBlock: () -> Unit = {}) {
    val vm = appViewModel { PlanViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val run by RunTracker.state.collectAsStateWithLifecycle()
    val progress by vm.runProgress.collectAsStateWithLifecycle()
    val game by appContainer().gameState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val dateTag = today.format(DateTimeFormatter.ofPattern("EEE · d MMM", PT_BR)).replace(".", "")
    ScreenScaffold(stringResource(R.string.program_today), subtitle = dateTag) {
        val current = state
        when {
            current == null -> RetroLoading()
            current.plan == null -> ScreenColumn {
                Hero("logo")
                TypewriterText(stringResource(R.string.today_no_plan), Modifier.fillMaxWidth())
                RetroRewardButton(
                    stringResource(R.string.today_create_block).uppercase(PT_BR),
                    stringResource(R.string.game_xp_plus, GameEngine.XP_QUESTIONNAIRE),
                    onClick = onCreateBlock,
                    icon = "folder",
                )
                game?.let { Missions(it.missions) }
            }
            else -> {
                // A run in progress (or interrupted) gets a shortcut back to its screen.
                val activeRun = run?.sessionId ?: progress?.sessionId
                TodayContent(current.plan, current.sessions, today, activeRun, game, onOpenSession, onStart, onCloseBlock, onCreateBlock)
            }
        }
    }
}

/** Big multi-tone illustration with a dithered floor shadow already drawn in. */
@Composable
private fun Hero(name: String, modifier: Modifier = Modifier, pixel: Dp = 2.dp) {
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        PixelSprite(name, Modifier.pixelFloat(amplitude = 1.dp), pixel = pixel)
    }
}

@Composable
private fun TodayContent(
    plan: PlanEntity,
    sessions: List<PlannedSessionEntity>,
    today: LocalDate,
    activeRun: Long?,
    game: GameState?,
    onOpenSession: (Long) -> Unit,
    onStart: (Long, String) -> Unit,
    onCloseBlock: () -> Unit,
    onCreateBlock: () -> Unit,
) {
    val c = Retro.colors
    val start = LocalDate.parse(plan.startDate)
    val anchor = PlanScheduler.weekAnchor(start)
    val end = PlanScheduler.endDate(start, plan.durationWeeks)
    val todays = sessions.filter { it.date == today.toString() }
    // The stage goes to the day's required session; a complementary one (optional) only when it is all there is.
    val required = todays.filter { !it.optional }
    val main = required.firstOrNull { it.status == "pending" } ?: required.firstOrNull() ?: todays.firstOrNull { it.status == "pending" } ?: todays.firstOrNull()
    // The next main session; an extra home session only when nothing else is planned.
    val upcoming = sessions.filter { it.date > today.toString() && it.status == "pending" }
    val next = upcoming.firstOrNull { !it.optional } ?: upcoming.firstOrNull()
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    // The block ends when every queue is done (Item 8): dates are projections, the weeks are the plan's estimate.
    val repository = appContainer().repository
    LaunchedEffect(plan.id, today) { repository.refreshQueue(today) }
    val queue = remember(sessions) { repository.queueOf(sessions) }
    val total = queue.count { !it.optional }
    val remaining = ModalityQueue.remaining(queue)
    val ceiling = ModalityQueue.ceiling(start, plan.durationWeeks)
    val weekNumber = (ChronoUnit.DAYS.between(anchor, today) / 7 + 1).toInt()
    val weekLabel = when {
        today < anchor -> stringResource(R.string.today_starts_on, anchor.short())
        total > 0 && remaining == 0 -> stringResource(R.string.today_block_finished)
        today > end -> stringResource(R.string.today_week_over, weekNumber, plan.durationWeeks)
        else -> stringResource(R.string.today_week_of, weekNumber, plan.durationWeeks)
    }

    ScreenColumn {
        // About 20% of the sessions left (or past the ceiling) and not closed yet: prepare the next one.
        val blockState = when {
            plan.closedAt != null -> BlockState.CLOSED
            today < start -> BlockState.PLANNED
            ModalityQueue.closing(total, remaining, today, ceiling) -> BlockState.CLOSING
            else -> BlockState.ACTIVE
        }
        if (blockState == BlockState.CLOSING) {
            RetroWindow(stringResource(R.string.today_block_closing_window), Modifier.fillMaxWidth()) {
                Text(
                    when {
                        today > ceiling && remaining > 0 -> pluralStringResource(R.plurals.today_block_ceiling, remaining, ceiling.short(), remaining)
                        remaining > 0 -> pluralStringResource(R.plurals.today_block_closing, remaining, remaining)
                        else -> stringResource(R.string.today_block_over)
                    },
                    style = Retro.type.body,
                )
                RetroButton(onClick = onCloseBlock, modifier = Modifier.fillMaxWidth()) {
                    PixelIcon("medal", size = 16.dp)
                    Text(stringResource(R.string.today_block_prepare))
                }
            }
        } else if (blockState == BlockState.CLOSED) {
            InfoCard(stringResource(R.string.today_block_closed))
            RetroButton(onClick = onCreateBlock, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.today_block_next)) }
        }
        if (activeRun != null) {
            InfoCard(stringResource(R.string.today_run_active))
            RetroButton(onClick = { onStart(activeRun, "run") }, modifier = Modifier.fillMaxWidth()) {
                PixelIcon("run", size = 16.dp)
                Text(stringResource(R.string.today_run_open))
            }
        }

        if (main != null && main.status == "pending" && main.startedAt == null && !main.optional && main.reentryApplied == null && main.id != activeRun) {
            ReentryCard(main, onCloseBlock)
        }

        // Today's session as the screen's "stage": illustration, name, start key with its reward.
        // Two or more sessions today and none left: the day is done, every session as the same card.
        val dayDone = todays.size >= 2 && required.isNotEmpty() && required.none { it.status == "pending" }
        if (dayDone) {
            TodayHero(if (todays.any { it.status == "done" }) HeroScene.DONE_STRENGTH else HeroScene.REST)
            Text(stringResource(R.string.today_day_done_title), Modifier.fillMaxWidth(), style = Retro.type.title, color = c.ink, textAlign = TextAlign.Center)
            Text(plan.name, Modifier.fillMaxWidth(), style = Retro.type.bodySmall, color = c.inkSoft, textAlign = TextAlign.Center)
            InfoCard(stringResource(if (required.all { it.status == "done" }) R.string.today_day_done else R.string.today_day_closed))
            todays.forEach { s -> SessionCard(s, showDate = false) { onOpenSession(s.id) } }
        } else if (main == null) {
            TodayHero(HeroScene.REST)
            Text(stringResource(R.string.today_rest_day), Modifier.fillMaxWidth(), style = Retro.type.heading, color = c.ink, textAlign = TextAlign.Center)
            Text(plan.name, Modifier.fillMaxWidth(), style = Retro.type.bodySmall, color = c.inkSoft, textAlign = TextAlign.Center)
        } else {
            Column(
                Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) { onOpenSession(main.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TodayHero(
                    when {
                        main.status == "done" -> if (main.type == "run") HeroScene.DONE_RUN else HeroScene.DONE_STRENGTH
                        main.status == "skipped" -> HeroScene.REST
                        main.type == "run" -> HeroScene.RUN
                        else -> HeroScene.STRENGTH
                    },
                )
                Text(main.title, style = Retro.type.title, color = c.ink, textAlign = TextAlign.Center)
                Text(plan.name, style = Retro.type.bodySmall, color = c.inkSoft, textAlign = TextAlign.Center)
            }
            when {
                main.status == "pending" && main.id != activeRun -> RetroRewardButton(
                    stringResource(if (main.startedAt != null) R.string.today_continue else R.string.today_start).uppercase(PT_BR),
                    stringResource(R.string.game_xp_plus, GameEngine.XP_SESSION),
                    onClick = { onStart(main.id, main.type) },
                )
                main.status == "done" -> InfoCard(stringResource(R.string.today_done))
                else -> Unit
            }
            todays.filter { it.id != main.id }.forEach { s -> SessionCard(s, showDate = false) { onOpenSession(s.id) } }
        }

        SectionTitle(weekLabel)
        QueueProgress(queue)
        WeekStrip(weekStart, today, sessions, onOpenSession)

        game?.let { Missions(it.missions) }

        if (next != null) {
            SectionTitle(stringResource(R.string.today_window_next))
            SessionCard(next, showDate = true) { onOpenSession(next.id) }
        }
    }
}

/** "Musculação 10/16 · Corrida 7/12 · fim previsto 12 dez": progress of each queue and the projected end. */
@Composable
private fun QueueProgress(queue: List<QueueSession>) {
    val parts = buildList {
        val (sd, st) = ModalityQueue.progress(queue, Modality.STRENGTH)
        if (st > 0) add(stringResource(R.string.today_progress_strength, sd, st))
        val (rd, rt) = ModalityQueue.progress(queue, Modality.RUN)
        if (rt > 0) add(stringResource(R.string.today_progress_run, rd, rt))
        if (ModalityQueue.remaining(queue) > 0) ModalityQueue.projectedEnd(queue)?.let { add(stringResource(R.string.today_projected_end, it.short())) }
    }
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = Retro.type.bodySmall, color = Retro.colors.inkSoft)
}

/**
 * Coming back after a pause in this session's modality: the app proposes the lighter start (core Reentry) and the
 * user confirms or keeps the plan. Shown once per session; the answer is kept on the session.
 */
@Composable
private fun ReentryCard(session: PlannedSessionEntity, onNewBlock: () -> Unit) {
    val repository = appContainer().repository
    val scope = rememberCoroutineScope()
    var found by remember(session.id) { mutableStateOf<Pair<Int, Reentry>?>(null) }
    LaunchedEffect(session.id) { found = repository.reentryFor(session) }
    val (gap, band) = found ?: return
    val run = session.type == "run"
    val text = when (band) {
        Reentry.NONE -> return
        Reentry.SHORT -> if (run) R.string.reentry_run_short else return
        Reentry.MEDIUM -> if (run) R.string.reentry_run_medium else R.string.reentry_strength_medium
        Reentry.LONG -> if (run) R.string.reentry_run_long else R.string.reentry_strength_long
        Reentry.RESTART -> if (run) R.string.reentry_restart_run else R.string.reentry_restart_strength
    }
    RetroWindow(stringResource(R.string.reentry_window), Modifier.fillMaxWidth()) {
        Text(stringResource(text, gap), style = Retro.type.body)
        if (band == Reentry.RESTART) {
            RetroButton(onClick = { scope.launch { repository.setReentry(session.id, band.code) } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reentry_resume_block))
            }
            RetroOutlinedButton(onClick = onNewBlock, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reentry_new_block)) }
        } else {
            RetroButton(onClick = { scope.launch { repository.setReentry(session.id, band.code) } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reentry_apply))
            }
            RetroOutlinedButton(onClick = { scope.launch { repository.setReentry(session.id, REENTRY_DECLINED) } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reentry_keep_plan))
            }
        }
    }
}

/** Seven day cells (S T Q Q S S D): check when done, X when skipped, type icon when planned; today boxed. */
@Composable
private fun WeekStrip(weekStart: LocalDate, today: LocalDate, sessions: List<PlannedSessionEntity>, onOpenSession: (Long) -> Unit) {
    val c = Retro.colors
    val byDate = sessions.groupBy { it.date }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0L..6L).forEach { i ->
            val day = weekStart.plusDays(i)
            // A pending complementary session does not keep the day from showing its check.
            val all = byDate[day.toString()].orEmpty()
            val daySessions = all.filter { !it.optional }.ifEmpty { all }
            val first = daySessions.firstOrNull()
            val isToday = day == today
            Column(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .then(if (isToday) Modifier.bevel(c.window, c.border, c.bevelLight, c.bevelDark, raised = true) else Modifier)
                    .then(
                        if (first != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { onOpenSession(first.id) }
                        else Modifier,
                    )
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, PT_BR).uppercase(PT_BR), style = Retro.type.label, color = c.ink)
                when {
                    first == null -> Box(Modifier.size(4.dp).background(c.inkFaint))
                    daySessions.all { it.status == "done" } -> PixelIcon("check", tint = c.ink, size = 18.dp)
                    daySessions.all { it.status == "skipped" } -> PixelIcon("x", tint = c.inkFaint, size = 18.dp)
                    else -> PixelIcon(typeIcon(first.type), tint = c.inkSoft, size = 18.dp)
                }
            }
        }
    }
}

/** This week's three missions: icon, goal, segmented bar and count. */
@Composable
private fun Missions(missions: List<MissionState>) {
    if (missions.isEmpty()) return
    val c = Retro.colors
    SectionTitle(stringResource(R.string.today_missions))
    missions.forEachIndexed { i, m ->
        if (i > 0) RetroDivider(dashed = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                when (m.kind) {
                    MissionKind.SESSIONS -> PixelSprite("flame", pixel = 2.dp)
                    MissionKind.RUN_KM -> PixelSprite("shoe", pixel = 2.dp)
                    MissionKind.RECORD -> PixelSprite("medal", pixel = 2.dp)
                    MissionKind.SETS -> PixelIcon("dumbbell", tint = c.ink, size = 22.dp)
                    MissionKind.EARLY -> PixelSprite("hourglass", pixel = 2.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(m.title, style = Retro.type.body, color = if (m.done) c.inkSoft else c.ink)
                RetroLinearProgress(
                    progress = { m.progress / m.target.toFloat() },
                    modifier = Modifier.height(14.dp),
                    segments = m.target.coerceIn(3, 16),
                    color = c.reward,
                )
            }
            Text(
                if (m.done) "✓" else "${m.progress}/${m.target}",
                style = Retro.type.button,
                color = c.ink,
            )
        }
    }
}
