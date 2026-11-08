package com.ithalo.hybridcoach.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroCard
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.pixelBorder
import com.ithalo.hybridcoach.ui.theme.Retro
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.questionnaire.IssueCode
import com.ithalo.hybridcoach.core.questionnaire.Location
import com.ithalo.hybridcoach.core.questionnaire.Priority
import com.ithalo.hybridcoach.core.questionnaire.QuestionnaireIssue
import com.ithalo.hybridcoach.core.questionnaire.RunExperience
import com.ithalo.hybridcoach.core.questionnaire.RunSurface
import com.ithalo.hybridcoach.core.questionnaire.Split
import com.ithalo.hybridcoach.core.strength.TimedTarget
import com.ithalo.hybridcoach.data.PlannedSessionEntity
import com.ithalo.hybridcoach.data.RunBlockEntity
import com.ithalo.hybridcoach.data.SetLogEntity
import com.ithalo.hybridcoach.ui.player.formatKg
import java.time.LocalDate

@Composable
fun dayLabel(code: String): String = stringResource(
    when (code) {
        "mon" -> R.string.day_mon
        "tue" -> R.string.day_tue
        "wed" -> R.string.day_wed
        "thu" -> R.string.day_thu
        "fri" -> R.string.day_fri
        "sat" -> R.string.day_sat
        else -> R.string.day_sun
    },
)

@Composable
fun priorityLabel(p: Priority) = stringResource(
    when (p) {
        Priority.RUN -> R.string.priority_run
        Priority.STRENGTH -> R.string.priority_strength
        Priority.BALANCED -> R.string.priority_balanced
    },
)

@Composable
fun splitLabel(s: Split) = stringResource(
    when (s) {
        Split.AI -> R.string.split_ai
        Split.FULL_BODY -> R.string.split_full_body
        Split.UPPER_LOWER -> R.string.split_upper_lower
        Split.PUSH_PULL_LEGS -> R.string.split_ppl
        Split.ABC -> R.string.split_abc
    },
)

@Composable
fun locationLabel(l: Location) = stringResource(
    when (l) {
        Location.FULL_GYM -> R.string.location_full_gym
        Location.LIMITED -> R.string.location_limited
        Location.HOME -> R.string.location_home
    },
)

@Composable
fun surfaceLabel(s: RunSurface) = stringResource(if (s == RunSurface.STREET) R.string.surface_street else R.string.surface_treadmill)

@Composable
fun runExperienceLabel(e: RunExperience) = stringResource(
    when (e) {
        RunExperience.NEVER -> R.string.run_exp_never
        RunExperience.WALK_RUN -> R.string.run_exp_walk_run
        RunExperience.REGULAR -> R.string.run_exp_regular
    },
)

@Composable
fun variantLabel(variant: String) = stringResource(if (variant == "home") R.string.variant_home else R.string.variant_gym)

@Composable
fun issueText(issue: QuestionnaireIssue): String = when (issue.code) {
    IssueCode.NO_AVAILABLE_DAYS -> stringResource(R.string.issue_no_days)
    IssueCode.DURATION_OUT_OF_RANGE -> stringResource(R.string.issue_duration)
    IssueCode.START_DATE_INVALID -> stringResource(R.string.issue_start_date)
    IssueCode.RACE_DATE_INVALID -> stringResource(R.string.issue_race_date)
    IssueCode.LONG_RUN_DAY_UNAVAILABLE -> stringResource(R.string.issue_long_run_day)
    IssueCode.LEG_DAY_UNAVAILABLE -> stringResource(R.string.issue_leg_day)
    IssueCode.NO_LOCATION -> stringResource(R.string.issue_no_location)
    IssueCode.NO_RUN_SURFACE -> stringResource(R.string.issue_no_surface)
    IssueCode.BEGINNER_RUNNER -> stringResource(R.string.issue_beginner)
}

fun paceText(secPerKm: Int): String = "%d:%02d".format(secPerKm / 60, secPerKm % 60)

@Composable
fun statusLabel(status: String) = stringResource(
    when (status) {
        "done" -> R.string.status_done
        "skipped" -> R.string.status_skipped
        else -> R.string.status_pending
    },
)

@Composable
fun typeLabel(type: String) = stringResource(if (type == "run") R.string.type_run else R.string.type_strength)

@Composable
fun runBlockLine(b: RunBlockEntity): String {
    val kind = stringResource(
        when (b.kind) {
            "warmup" -> R.string.kind_warmup
            "steady" -> R.string.kind_steady
            "interval" -> R.string.kind_interval
            "recovery" -> R.string.kind_recovery
            else -> R.string.kind_cooldown
        },
    )
    val amount = when {
        b.durationSeconds != null -> if (b.durationSeconds % 60 == 0) "${b.durationSeconds / 60} min" else "${b.durationSeconds} s"
        b.distanceM != null -> when {
            b.distanceM < 1000 -> "${b.distanceM} m"
            b.distanceM % 1000 == 0 -> "${b.distanceM / 1000} km"
            else -> "%.1f km".format(PT_BR, b.distanceM / 1000.0)
        }
        else -> ""
    }
    val pace = if (b.paceMin != null && b.paceMax != null) " · ${paceText(b.paceMin)}–${paceText(b.paceMax)}/km" else ""
    val repeat = if (b.repeat > 1) "${b.repeat}× " else ""
    return "$repeat$kind $amount$pace"
}

/**
 * One logged set as text: "8 × 40 kg · falhou · esforço 8", or "8:00 · esforço 4" for timed exercises
 * (whose reps column holds seconds; [plannedReps] is the exercise target that tells them apart).
 */
@Composable
fun setLine(log: SetLogEntity, plannedReps: String?): String {
    val timed = plannedReps != null && TimedTarget.isTimed(plannedReps)
    val parts = buildList {
        if (timed) {
            add(TimedTarget.format(log.repsDone) + if (log.loadKg > 0) " · ${formatKg(log.loadKg)} kg" else "")
        } else {
            add("${log.repsDone} × ${formatKg(log.loadKg)} kg")
        }
        if (log.failed) add(stringResource(R.string.player_failed_short))
        log.rpe?.let { add("esforço $it") }
    }
    return "${log.setIndex + 1}. ${parts.joinToString(" · ")}"
}

/** Pixel icon key of a session type. */
fun typeIcon(type: String) = if (type == "run") "run" else "dumbbell"

@Composable
fun SessionCard(session: PlannedSessionEntity, showDate: Boolean, onClick: () -> Unit) {
    val c = Retro.colors
    val date = LocalDate.parse(session.date)
    val skipped = session.status == "skipped"
    RetroCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PixelIcon(typeIcon(session.type), tint = if (skipped) c.inkFaint else c.inkSoft, size = 28.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(session.title, style = Retro.type.heading, color = if (skipped) c.inkSoft else c.ink)
                val details = buildList {
                    if (session.optional) add(stringResource(R.string.session_optional))
                    add(typeLabel(session.type))
                    if (showDate) add(date.long())
                    session.estimatedMinutes?.let { add("$it min") }
                }
                Text(details.joinToString(" · "), style = Retro.type.bodySmall, color = c.inkSoft)
            }
            StatusBadge(session.status)
        }
    }
}

/** Small status mark: check (done), X (skipped) or clock (pending). Only "done" gets a filled plate. */
@Composable
fun StatusBadge(status: String) {
    val c = Retro.colors
    val bg = if (status == "done") c.success else Color.Transparent
    val fg = when (status) {
        "done" -> readableOn(c.success)
        "skipped" -> c.inkFaint
        else -> c.inkSoft
    }
    Row(
        Modifier.background(bg).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PixelIcon(
            when (status) { "done" -> "check"; "skipped" -> "x"; else -> "clock" },
            tint = fg,
            size = 14.dp,
        )
        Text(statusLabel(status).uppercase(PT_BR), style = Retro.type.label.copy(fontSize = 10.sp), color = fg)
    }
}
