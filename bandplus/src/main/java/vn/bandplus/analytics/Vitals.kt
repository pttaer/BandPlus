package vn.bandplus.analytics

import vn.bandplus.data.Point
import java.time.Duration

/** Minutes with value below threshold; each sample counts until the next, capped at 10 min. */
fun minutesBelow(points: List<Point>, threshold: Int): Long {
    val p = points.sortedBy { it.t }
    var total = 0L
    for (i in p.indices) {
        if (p[i].v >= threshold) continue
        val dt = if (i + 1 < p.size) Duration.between(p[i].t, p[i + 1].t).toMinutes() else 1
        total += dt.coerceAtMost(10)
    }
    return total
}

/**
 * ODI-style estimate: drops >= 3 points below the previous 5-sample mean, per hour of data.
 * Estimate, not a diagnosis. null unless sampling is <= 90 s and >= 1 h of data.
 */
fun dropsPerHour(points: List<Point>): Double? {
    val p = points.sortedBy { it.t }
    if (p.size < 10) return null
    val gaps = p.zipWithNext { a, b -> Duration.between(a.t, b.t).seconds }.sorted()
    if (gaps[gaps.size / 2] > 90) return null
    var drops = 0
    var inDrop = false
    for (i in 5 until p.size) {
        val base = p.subList(i - 5, i).map { it.v }.average()
        val isDrop = p[i].v <= base - 3
        if (isDrop && !inDrop) drops++
        inDrop = isDrop
    }
    val hours = Duration.between(p.first().t, p.last().t).toMinutes() / 60.0
    return if (hours < 1) null else drops / hours
}

/**
 * Readiness 0-100. Weights: RHR deviation 35, sleep score 35, SpO2 min 15, yesterday stress 15.
 * Missing inputs are dropped and the rest rescaled; null if nothing is known.
 */
fun readiness(rhrDeviation: Double?, sleepScore: Int?, spo2Min: Int?, stressAvg: Int?): Int? {
    val parts = listOfNotNull(
        rhrDeviation?.let { 35 to (1 - (it / 10).coerceIn(0.0, 1.0)) },
        sleepScore?.let { 35 to it / 100.0 },
        spo2Min?.let { 15 to ((it - 85) / 10.0).coerceIn(0.0, 1.0) },
        stressAvg?.let { 15 to (1 - it / 100.0).coerceIn(0.0, 1.0) },
    )
    if (parts.isEmpty()) return null
    val w = parts.sumOf { it.first }
    return Math.round(parts.sumOf { it.first * it.second } * 100 / w).toInt()
}
