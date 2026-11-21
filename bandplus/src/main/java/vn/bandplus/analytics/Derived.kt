package vn.bandplus.analytics

import vn.bandplus.data.HrPoint
import vn.bandplus.data.Point
import vn.bandplus.data.SleepNight
import vn.bandplus.data.Stage
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Banister TRIMP (male coefficients 0.64 / 1.92). Gaps > 5 min count 1 min. */
fun trimp(points: List<HrPoint>, restingHr: Int, maxHr: Int): Double {
    val p = points.filter { it.bpm in 25..250 }.sortedBy { it.t }
    var total = 0.0
    for (i in p.indices) {
        val hrr = (p[i].bpm - restingHr).toDouble() / (maxHr - restingHr)
        if (hrr <= 0) continue
        val dt = if (i + 1 < p.size) Duration.between(p[i].t, p[i + 1].t).seconds / 60.0 else 1.0
        total += (if (dt > 5) 1.0 else dt) * hrr * 0.64 * exp(1.92 * hrr)
    }
    return total
}

/** Acute (last 7) : chronic (last 28) daily load. Newest first. 0.8-1.3 = safe. null if < 14 days. */
fun acwr(dailyLoadNewestFirst: List<Double>): Double? {
    if (dailyLoadNewestFirst.size < 14) return null
    val chronic = dailyLoadNewestFirst.take(28).average()
    return if (chronic <= 0) null else dailyLoadNewestFirst.take(7).average() / chronic
}

/**
 * Sleep Regularity Index (-100..100): probability of the same sleep/wake state 24 h apart, minute by minute.
 * [nights] oldest first, consecutive days. null if < 7 nights.
 */
fun sleepRegularityIndex(nights: List<SleepNight>, windowStart: Instant, days: Int): Double? {
    if (nights.size < 7 || days < 2) return null
    val spans = nights.flatMap { it.stages }.filter { it.stage != Stage.AWAKE }
        .map { it.start.epochSecond / 60 to it.end.epochSecond / 60 }
    val start = windowStart.epochSecond / 60
    val total = days * 1440
    val asleep = BooleanArray(total)
    for ((a, b) in spans) for (m in maxOf(a, start)..<minOf(b, start + total)) asleep[(m - start).toInt()] = true
    var same = 0
    val n = total - 1440
    for (i in 0 until n) if (asleep[i] == asleep[i + 1440]) same++
    return 200.0 * same / n - 100
}

/** Inactive runs (< 10 steps/min) during waking hours longer than [minLen]. Gaps > 5 min end a run. */
fun sedentaryBouts(stepsPerMin: List<Point>, minLen: Duration = Duration.ofMinutes(60)): List<Pair<Instant, Duration>> {
    val p = stepsPerMin.sortedBy { it.t }
    val out = mutableListOf<Pair<Instant, Duration>>()
    var start: Instant? = null
    var last: Instant? = null
    // end = first active sample, or last inactive sample + 1 min when the run ends in a gap / end of data
    fun close(end: Instant?) {
        val s = start
        if (s != null && end != null && Duration.between(s, end) >= minLen) out += s to Duration.between(s, end)
        start = null
    }
    for (pt in p) {
        if (last != null && Duration.between(last, pt.t) > Duration.ofMinutes(5)) close(last.plusSeconds(60))
        if (pt.v < 10) { if (start == null) start = pt.t } else close(pt.t)
        last = pt.t
    }
    close(last?.plusSeconds(60))
    return out
}

/** Steps after [hour] local, given per-minute steps already filtered to that day. */
fun stepsAfter(stepsPerMin: List<Point>, cutoff: Instant): Int = stepsPerMin.filter { it.t >= cutoff }.sumOf { it.v }

fun pearson(xs: List<Double>, ys: List<Double>): Double? {
    if (xs.size != ys.size || xs.size < 5) return null
    val mx = xs.average(); val my = ys.average()
    val cov = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
    val vx = xs.sumOf { (it - mx) * (it - mx) }; val vy = ys.sumOf { (it - my) * (it - my) }
    return if (vx == 0.0 || vy == 0.0) null else cov / sqrt(vx * vy)
}

/** Top factors by |r|, each pair = factor values aligned with sleep scores. */
fun topFactors(factors: Map<String, List<Pair<Double, Double>>>, n: Int = 3): List<Pair<String, Double>> =
    factors.mapNotNull { (name, pairs) -> pearson(pairs.map { it.first }, pairs.map { it.second })?.let { name to it } }
        .sortedByDescending { abs(it.second) }.take(n)

/**
 * Body-battery-style energy 0-100. Weights: sleep recharge up to +70 (asleep/8 h x efficiency) from a base of 30,
 * drain 0.3 per stress point above 25 per hour awake, 0.15 per active minute.
 */
fun energy(lastNight: SleepNight?, stressAvg: Int?, awakeHours: Double, activeMin: Int?): Int? {
    val n = lastNight ?: return null
    val start = 30 + 70 * minOf(1.0, n.asleepMin / 480.0) * efficiency(n)
    val drain = maxOf(0, (stressAvg ?: 25) - 25) * 0.3 * awakeHours + (activeMin ?: 0) * 0.15
    return (start - drain).coerceIn(0.0, 100.0).toInt()
}
