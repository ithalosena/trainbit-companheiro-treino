package com.ithalo.hybridcoach.ui.run

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.game.RunRecord
import com.ithalo.hybridcoach.core.game.RunRecordKind
import com.ithalo.hybridcoach.run.RunService
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.common.paceText

/** Name of a running record, as the Recordes tab and the run summary show it. */
@Composable
fun runRecordLabel(kind: RunRecordKind): String = stringResource(
    when (kind) {
        RunRecordKind.LONGEST_DISTANCE -> R.string.game_run_record_distance
        RunRecordKind.LONGEST_TIME -> R.string.game_run_record_time
        RunRecordKind.BEST_PACE -> R.string.game_run_record_pace
        RunRecordKind.FASTEST_KM -> R.string.game_run_record_km
    },
)

/** "5,02 km", "32:10" or "5:40/km". */
fun runRecordValue(r: RunRecord): String = when (r.kind) {
    RunRecordKind.LONGEST_DISTANCE -> "%.2f km".format(PT_BR, r.value / 1000)
    RunRecordKind.LONGEST_TIME -> RunService.elapsedText(r.value.toLong())
    RunRecordKind.BEST_PACE, RunRecordKind.FASTEST_KM -> "${paceText(r.value.toInt())}/km"
}
