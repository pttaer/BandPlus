package vn.bandplus.analytics

import kotlin.math.abs
import kotlin.math.sqrt

/** Drop RR outside 300-2000 ms or differing > 20 % from the previous kept interval. */
fun cleanRr(rrMs: List<Int>): List<Int> {
    val out = ArrayList<Int>(rrMs.size)
    for (rr in rrMs) {
        if (rr !in 300..2000) continue
        val prev = out.lastOrNull()
        if (prev != null && abs(rr - prev) > prev * 0.2) continue
        out += rr
    }
    return out
}

fun rmssd(rrMs: List<Int>): Double {
    if (rrMs.size < 2) return 0.0
    val d = rrMs.zipWithNext { a, b -> (b - a).toDouble() }
    return sqrt(d.sumOf { it * it } / d.size)
}

fun sdnn(rrMs: List<Int>): Double = rrMs.map { it.toDouble() }.sd()
