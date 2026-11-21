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

/** Band-computed values; all null when the band did not send them. */
data class BandExtras(
    val vitality: Int? = null,          // Xiaomi Vitality / PAI, 7-day rolling
    val vitalityToday: Int? = null,     // light + moderate + high gained today
    val trainingLoadDay: Int? = null,
    val trainingLoadWeek: Int? = null,
    val recoveryHours: Int? = null,
    val standingHours: Int? = null,
    val hrMin: Int? = null,
    val hrMax: Int? = null,
    val hrAvg: Int? = null,
    val respiratoryAvg: Double? = null, // breaths/min during sleep
    val bodyEnergy: Int? = null,        // band's own energy 0-100, latest sample of the day
)

data class BandStatus(val name: String?, val connected: Boolean, val battery: Int?)
