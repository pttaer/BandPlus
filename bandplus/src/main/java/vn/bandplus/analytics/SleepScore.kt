package vn.bandplus.analytics

import vn.bandplus.data.SleepNight
import java.time.Duration
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** Personal heuristic, not medical. Each part is 0..its weight. */
data class SleepScore(val total: Int, val duration: Int, val deep: Int, val rem: Int, val continuity: Int, val consistency: Int)

private fun frac(v: Double, target: Double) = min(1.0, max(0.0, v / target))

/** Weights: duration 35 (7-9 h), deep 20 (>=15 %), REM 20 (>=20 %), continuity 15 (awake < 5 %), consistency 10 (bedtime SD < 30 min). */
fun score(night: SleepNight, last14: List<SleepNight>, zone: ZoneId = ZoneId.systemDefault()): SleepScore {
    val asleepH = night.asleepMin / 60.0
    val durF = when {
        asleepH < 7 -> frac(asleepH - 3, 4.0)
        asleepH > 9 -> max(0.0, 1 - (asleepH - 9) / 4)
        else -> 1.0
    }
    val asleep = max(1, night.asleepMin).toDouble()
    val inBed = max(1, night.asleepMin + night.awakeMin).toDouble()
    val deepF = frac(night.deepMin / asleep, 0.15)
    val remF = frac(night.remMin / asleep, 0.20)
    val awakePct = night.awakeMin / inBed
    val contF = if (awakePct <= 0.05) 1.0 else max(0.0, 1 - (awakePct - 0.05) / 0.15)
    val sd = bedtimeSdMin(last14 + night, zone)
    val consF = if (sd == null || sd <= 30) 1.0 else max(0.0, 1 - (sd - 30) / 60)

    val d = Math.round(35 * durF).toInt()
    val de = Math.round(20 * deepF).toInt()
    val r = Math.round(20 * remF).toInt()
    val c = Math.round(15 * contF).toInt()
    val co = Math.round(10 * consF).toInt()
    return SleepScore(d + de + r + c + co, d, de, r, c, co)
}

/** SD of bedtime in minutes, measured from noon so 23:30 and 00:30 are 60 min apart. null if < 3 nights. */
fun bedtimeSdMin(nights: List<SleepNight>, zone: ZoneId = ZoneId.systemDefault()): Double? {
    if (nights.size < 3) return null
    return nights.map {
        val m = it.start.atZone(zone).toLocalTime().toSecondOfDay() / 60
        ((m - 12 * 60 + 24 * 60) % (24 * 60)).toDouble()
    }.sd()
}

/** Rolling sleep debt in minutes vs target (default 8 h). Missing nights are skipped, not counted as 0. */
fun sleepDebtMin(nights: List<SleepNight?>, targetMin: Int = 480): Int =
    nights.filterNotNull().sumOf { max(0, targetMin - it.asleepMin) }

/** Asleep / in bed, 0..1. */
fun efficiency(n: SleepNight): Double {
    val inBed = Duration.between(n.start, n.end).toMinutes()
    return if (inBed <= 0) 0.0 else min(1.0, n.asleepMin / inBed.toDouble())
}
