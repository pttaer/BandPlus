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
