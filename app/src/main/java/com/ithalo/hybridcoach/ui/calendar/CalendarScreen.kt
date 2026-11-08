package com.ithalo.hybridcoach.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.data.PlannedSessionEntity
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.MenuRow
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.retro.RetroTopBarAction
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SessionCard
import com.ithalo.hybridcoach.ui.common.appViewModel
import com.ithalo.hybridcoach.ui.common.long
import com.ithalo.hybridcoach.ui.common.readableOn
import com.ithalo.hybridcoach.ui.common.short
import com.ithalo.hybridcoach.ui.retro.DitherCrossfade
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroFilterChip
import com.ithalo.hybridcoach.ui.retro.RetroIconButton
import com.ithalo.hybridcoach.ui.retro.RetroLoading
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.pixelBorder
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.today.PlanViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun CalendarScreen(onOpenSession: (Long) -> Unit, onBlock: () -> Unit) {
    val vm = appViewModel { PlanViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var monthMode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selectedDate = LocalDate.parse(selected)

    ScreenScaffold(
        stringResource(R.string.program_calendar),
        actions = { RetroTopBarAction("folder", stringResource(R.string.plan_block_tools), onClick = onBlock) },
    ) {
        val current = state
        if (current == null) {
            RetroLoading()
            return@ScreenScaffold
        }
        if (current.plan == null) {
            ScreenColumn {
                InfoCard(stringResource(R.string.today_no_plan))
                MenuRow("folder", stringResource(R.string.plan_block_tools), stringResource(R.string.plan_block_tools_desc), highlight = true, onClick = onBlock)
            }
            return@ScreenScaffold
        }
        val byDate = current.sessions.groupBy { it.date }
        ScreenColumn {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroFilterChip(selected = !monthMode, onClick = { monthMode = false }, label = { Text(stringResource(R.string.calendar_week)) })
                RetroFilterChip(selected = monthMode, onClick = { monthMode = true }, label = { Text(stringResource(R.string.calendar_month)) })
            }
            DitherCrossfade(monthMode, Modifier.fillMaxWidth()) { month ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (month) {
                        MonthView(selectedDate, byDate, onSelect = { selected = it.toString() }, onMove = { selected = it.toString() })
                        Text(selectedDate.long(), style = Retro.type.heading, color = Retro.colors.ink)
                        DaySessions(byDate[selected].orEmpty(), onOpenSession)
                    } else {
                        WeekView(selectedDate, byDate, onMove = { selected = it.toString() }, onOpenSession = onOpenSession)
                    }
                }
            }
            SectionTitle(stringResource(R.string.plan_block_tools))
            MenuRow("folder", stringResource(R.string.plan_block_tools), stringResource(R.string.plan_block_tools_desc), onClick = onBlock)
        }
    }
}

@Composable
private fun DaySessions(sessions: List<PlannedSessionEntity>, onOpenSession: (Long) -> Unit) {
    if (sessions.isEmpty()) {
        Text(stringResource(R.string.calendar_no_sessions), style = Retro.type.body, color = Retro.colors.inkSoft)
    } else {
        sessions.forEach { SessionCard(it, showDate = false) { onOpenSession(it.id) } }
    }
}

@Composable
private fun NavRow(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RetroIconButton(onClick = onPrevious) { PixelIcon("arrow_left", size = 18.dp, contentDescription = stringResource(R.string.calendar_previous)) }
        Text(label, Modifier.weight(1f), style = Retro.type.heading, color = Retro.colors.ink, textAlign = TextAlign.Center)
        RetroIconButton(onClick = onNext) { PixelIcon("arrow_right", size = 18.dp, contentDescription = stringResource(R.string.calendar_next)) }
    }
}

@Composable
private fun WeekView(
    selected: LocalDate,
    byDate: Map<String, List<PlannedSessionEntity>>,
    onMove: (LocalDate) -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val c = Retro.colors
    val monday = selected.minusDays((selected.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    NavRow(
        stringResource(R.string.calendar_week_range, monday.short(), monday.plusDays(6).short()),
        onPrevious = { onMove(selected.minusWeeks(1)) },
        onNext = { onMove(selected.plusWeeks(1)) },
    )
    (0L..6L).map { monday.plusDays(it) }.forEach { day ->
        val sessions = byDate[day.toString()].orEmpty()
        val isToday = day == LocalDate.now()
        val headerBg = if (isToday) c.accent else c.windowInset
        Row(
            Modifier.fillMaxWidth().background(headerBg).pixelBorder(c.border).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fg = readableOn(headerBg)
            PixelIcon(if (isToday) "star" else "calendar", tint = fg, size = 14.dp)
            Text(day.long().uppercase(PT_BR), Modifier.weight(1f), style = Retro.type.label, color = fg)
            if (isToday) Text(stringResource(R.string.calendar_today_tag), style = Retro.type.label, color = fg)
        }
        if (sessions.isEmpty()) {
            Text(stringResource(R.string.calendar_rest), Modifier.padding(start = 8.dp), style = Retro.type.bodySmall, color = c.inkSoft)
        } else {
            sessions.forEach { SessionCard(it, showDate = false) { onOpenSession(it.id) } }
        }
    }
}

@Composable
private fun MonthView(
    selected: LocalDate,
    byDate: Map<String, List<PlannedSessionEntity>>,
    onSelect: (LocalDate) -> Unit,
    onMove: (LocalDate) -> Unit,
) {
    val month = YearMonth.from(selected)
    RetroWindow(stringResource(R.string.calendar_month_window), Modifier.fillMaxWidth()) {
        NavRow(
            month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", PT_BR)).replaceFirstChar { it.uppercase() },
            onPrevious = { onMove(month.minusMonths(1).atDay(1)) },
            onNext = { onMove(month.plusMonths(1).atDay(1)) },
        )
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, PT_BR).uppercase(PT_BR),
                    Modifier.weight(1f),
                    style = Retro.type.label,
                    color = Retro.colors.inkSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val firstOffset = month.atDay(1).dayOfWeek.value - 1
        val cells = firstOffset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = r * 7 + col - firstOffset + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        DayCell(date, date == selected, byDate[date.toString()].orEmpty(), Modifier.weight(1f)) { onSelect(date) }
                    }
                }
            }
        }
    }
}

/** Calendar cell: plain number, filled block when selected, accent outline on today; one dot per session. */
@Composable
private fun DayCell(date: LocalDate, isSelected: Boolean, sessions: List<PlannedSessionEntity>, modifier: Modifier, onClick: () -> Unit) {
    val c = Retro.colors
    val isToday = date == LocalDate.now()
    val fg = if (isSelected) c.onAccent2 else c.ink
    Box(modifier.aspectRatio(1f).padding(2.dp)) {
        Column(
            Modifier
                .matchParentSize()
                .then(if (isSelected) Modifier.background(c.accent2) else Modifier)
                .then(if (isToday) Modifier.pixelBorder(c.accent) else Modifier)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(date.dayOfMonth.toString(), style = Retro.type.label.copy(fontSize = 13.sp), color = fg)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                sessions.take(3).forEach {
                    Box(
                        Modifier.size(5.dp).background(
                            when (it.status) {
                                "done" -> if (isSelected) c.onAccent2 else c.success
                                "skipped" -> c.inkFaint
                                else -> c.accent
                            },
                        ),
                    )
                }
            }
        }
    }
}
