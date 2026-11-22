package vn.bandplus.analytics

import kotlin.math.sqrt

/** Mean of non-null values; null if none (days without data never drag averages down). */
fun List<Int?>.meanOrNull(): Double? = filterNotNull().takeIf { it.isNotEmpty() }?.average()

fun List<Double>.sd(): Double {
    if (size < 2) return 0.0
    val m = average()
    return sqrt(sumOf { (it - m) * (it - m) } / (size - 1))
}

fun List<Int>.median(): Double? = sorted().let { s ->
    if (s.isEmpty()) null else if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
}

/** today - reference, rounded; null when either side missing. */
fun delta(today: Number?, ref: Number?): Int? =
    if (today == null || ref == null) null else Math.round(today.toDouble() - ref.toDouble()).toInt()

/** R-7 (linear interpolation, same as HeartRatePercentiles/Excel PERCENTILE.INC); [p] in 0..1. */
fun List<Int>.percentile(p: Double): Double? {
    if (isEmpty()) return null
    val s = sorted()
    val h = (s.size - 1) * p
    val lo = h.toInt()
    return s[lo] + (h - lo) * (s[minOf(lo + 1, s.size - 1)] - s[lo])
}
