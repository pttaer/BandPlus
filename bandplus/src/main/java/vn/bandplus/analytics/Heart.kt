package vn.bandplus.analytics

import vn.bandplus.data.HrPoint
import java.time.Duration
import java.time.Instant

enum class Zone { REST, Z1, Z2, Z3, Z4, Z5 }

/**
 * Time per zone. Karvonen when restingHr given, else %maxHr. Each sample counts until the next one;
 * gaps over 5 min (band off) count as 1 min only.
 */
fun hrZones(points: List<HrPoint>, maxHr: Int, restingHr: Int? = null): Map<Zone, Duration> {
    val out = Zone.entries.associateWith { Duration.ZERO }.toMutableMap()
    val sorted = points.filter { it.bpm in 25..250 }.sortedBy { it.t }
    for (i in sorted.indices) {
        val dt = if (i + 1 < sorted.size) Duration.between(sorted[i].t, sorted[i + 1].t) else Duration.ofMinutes(1)
        val step = if (dt > Duration.ofMinutes(5)) Duration.ofMinutes(1) else dt
        val z = zoneOf(sorted[i].bpm, maxHr, restingHr)
        out[z] = out.getValue(z) + step
    }
    return out
}

fun zoneOf(bpm: Int, maxHr: Int, restingHr: Int?): Zone {
    val pct = if (restingHr != null) (bpm - restingHr).toDouble() / (maxHr - restingHr) else bpm.toDouble() / maxHr
    return when {
        pct < 0.5 -> Zone.REST
        pct < 0.6 -> Zone.Z1
        pct < 0.7 -> Zone.Z2
        pct < 0.8 -> Zone.Z3
        pct < 0.9 -> Zone.Z4
        else -> Zone.Z5
    }
}

fun maxHrFor(age: Int) = 220 - age

/** Lowest 30-min rolling mean within [from, to] (e.g. the sleep window). null if < 30 min of data. */
fun restingHr(points: List<HrPoint>, from: Instant, to: Instant): Int? {
    val p = points.filter { it.t >= from && it.t <= to && it.bpm in 25..250 }.sortedBy { it.t }
    var best: Double? = null
    var j = 0
    var sum = 0
    for (i in p.indices) {
        sum += p[i].bpm
        while (Duration.between(p[j].t, p[i].t) >= Duration.ofMinutes(30)) { sum -= p[j].bpm; j++ }
        if (Duration.between(p[j].t, p[i].t) >= Duration.ofMinutes(29)) {
            val m = sum.toDouble() / (i - j + 1)
            if (best == null || m < best) best = m
        }
    }
    return best?.let { Math.round(it).toInt() }
}

/**
 * SDANN (24 h), a circadian HRV proxy: SD of 5-min mean NN intervals (60000 / mean bpm).
 * Not RMSSD. null if < 12 windows (1 h).
 */
fun sdann(points: List<HrPoint>): Double? {
    val windows = points.filter { it.bpm in 25..250 }
        .groupBy { it.t.epochSecond / 300 }
        .values.map { w -> 60000.0 / w.map { it.bpm }.average() }
    return if (windows.size < 12) null else windows.sd()
}

/** (day mean - sleep mean) / day mean. Below 0.10 = non-dipper. */
fun nocturnalDip(dayMean: Double, sleepMean: Double): Double = (dayMean - sleepMean) / dayMean

/** Uth formula. Estimate only. */
fun vo2maxEstimate(maxHr: Int, restingHr: Int): Double = 15.3 * maxHr / restingHr

/** True when each of the last 2 mornings is >= 5 bpm above the 30-day median. */
fun rhrElevated(history: List<Int>, last2: List<Int>): Boolean {
    val base = history.median() ?: return false
    return last2.size >= 2 && last2.all { it >= base + 5 }
}
