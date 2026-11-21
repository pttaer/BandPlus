package vn.bandplus.analytics

import vn.bandplus.data.HrPoint
import java.time.Duration

/**
 * First sustained run of HR outside [low, high] lasting >= [minDuration], or null.
 * Gaps over 3 min break the run. Used to fire one alert per episode, not per sample.
 */
fun sustainedHr(points: List<HrPoint>, low: Int, high: Int, minDuration: Duration = Duration.ofMinutes(10)): Pair<HrPoint, Boolean>? {
    val p = points.sortedBy { it.t }
    var startIdx = -1
    var isHigh = false
    for (i in p.indices) {
        val hi = p[i].bpm > high
        val lo = p[i].bpm < low
        val broken = startIdx >= 0 && (Duration.between(p[i - 1].t, p[i].t) > Duration.ofMinutes(3) || (!hi && !lo) || hi != isHigh)
        if (broken) startIdx = -1
        if (startIdx < 0 && (hi || lo)) { startIdx = i; isHigh = hi }
        if (startIdx >= 0 && Duration.between(p[startIdx].t, p[i].t) >= minDuration) return p[startIdx] to isHigh
    }
    return null
}
