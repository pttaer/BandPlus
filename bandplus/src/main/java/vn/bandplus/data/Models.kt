package vn.bandplus.data

import java.time.Instant
import java.time.LocalDate

/** null = no data that day, never 0. */
data class DaySummary(
    val date: LocalDate,
    val steps: Int?,
    val kcal: Int?,
    val activeMin: Int?,
    val restingHr: Int?,
    val sleep: SleepNight?,
    val spo2Avg: Int?,
    val spo2Min: Int?,
    val stressAvg: Int?,
)

enum class Stage { DEEP, LIGHT, REM, AWAKE }

data class StageSpan(val start: Instant, val end: Instant, val stage: Stage)

data class SleepNight(
    val start: Instant,
    val end: Instant,
    val deepMin: Int,
    val lightMin: Int,
    val remMin: Int,
    val awakeMin: Int,
    val stages: List<StageSpan> = emptyList(),
) {
    val asleepMin get() = deepMin + lightMin + remMin
}

data class HrPoint(val t: Instant, val bpm: Int)

/** Generic timed value (SpO2 %, stress 0-100). */
data class Point(val t: Instant, val v: Int)

data class BandStatus(val name: String?, val connected: Boolean, val battery: Int?)
