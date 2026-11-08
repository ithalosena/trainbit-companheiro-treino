package com.ithalo.hybridcoach.run

import com.ithalo.hybridcoach.core.run.RunStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Snapshot of the run shown by the screen and the notification. */
data class RunUi(
    val sessionId: Long,
    val title: String,
    val activeSeconds: Long,
    val distanceM: Double,
    val paceSecPerKm: Int?,
    val avgPaceSecPerKm: Int?,
    val step: RunStep?,
    val stepNumber: Int,
    val totalSteps: Int,
    val stepSeconds: Long,
    val stepDistanceM: Double,
    val paused: Boolean,
    val gpsAccuracyM: Float?,
    val splits: List<Int>,
    val finished: Boolean = false,
    val runLogId: Long? = null,
    /** Treadmill speed in km/h; null for a GPS run. */
    val treadmillKmh: Double? = null,
    /** Paused because the runner stood still (moving again resumes it). */
    val autoPaused: Boolean = false,
    /** Every block is done; the run goes on until finished by hand or by standing still. */
    val blocksDone: Boolean = false,
)

/** Shared between [RunService] (writer) and the UI (reader); null when no run is active. */
object RunTracker {
    internal val mutable = MutableStateFlow<RunUi?>(null)
    val state: StateFlow<RunUi?> = mutable

    /** Called by the screen once the finished run has been reviewed. */
    fun clear() {
        if (mutable.value?.finished == true) mutable.value = null
    }
}
