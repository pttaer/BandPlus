package vn.bandplus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.bandplus.data.Stage
import vn.bandplus.data.StageSpan
import java.time.Instant

val Cyan = Color(0xFF8BDCFB)
val Muted = Color(0xFF9A9A9A)
val CardBg = Color(0xFF161616)
val Good = Color(0xFF7BD88F)
val Bad = Color(0xFFFF8A80)

/** Runs [block] on IO whenever [key] changes; null while loading. */
@Composable
fun <T> load(vararg key: Any?, block: () -> T): State<T?> =
    produceState<T?>(null, *key) { value = withContext(Dispatchers.IO) { block() } }

/**
 * One metric. value null = no data ("—"). Deltas are signed differences in the metric's unit.
 * higherIsBetter colours the deltas (null = neutral).
 */
@Composable
fun MetricCard(
    title: String,
    value: String?,
    unit: String = "",
    deltaYesterday: Int? = null,
    delta7d: Int? = null,
    higherIsBetter: Boolean? = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp)
    ) {
        Text(title, color = Muted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: "—", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            if (value != null && unit.isNotEmpty()) Text(" $unit", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (value != null && (deltaYesterday != null || delta7d != null)) {
            Row {
                DeltaText("hôm qua", deltaYesterday, higherIsBetter)
                Spacer(Modifier.width(12.dp))
                DeltaText("TB 7 ngày", delta7d, higherIsBetter)
            }
        }
    }
}

@Composable
private fun DeltaText(label: String, d: Int?, higherIsBetter: Boolean?) {
    if (d == null) return
    val c = when {
        d == 0 || higherIsBetter == null -> Muted
        (d > 0) == higherIsBetter -> Good
        else -> Bad
    }
    Text("${if (d > 0) "+" else ""}$d vs $label", color = c, fontSize = 12.sp)
}

@Composable
fun SectionTitle(text: String) =
    Text(text, color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))

@Composable
fun Line(label: String, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
    Text(value, color = Color.White, fontSize = 14.sp)
}

/**
 * Time-series line. Gaps > [gapSec] break the line (band off) instead of being bridged.
 * [xs] epoch seconds, [ys] values.
 */
@Composable
fun LineChart(xs: List<Long>, ys: List<Int>, color: Color = Cyan, gapSec: Long = 600, minY: Int? = null, maxY: Int? = null, refs: List<Pair<Double, Color>> = emptyList()) {
    if (xs.size < 2) {
        Text("Không có dữ liệu", color = Muted, fontSize = 13.sp)
        return
    }
    val lo = (minY ?: ys.min()).toFloat()
    val hi = (maxY ?: ys.max()).toFloat().let { if (it <= lo) lo + 1 else it }
    val x0 = xs.first().toFloat()
    val x1 = xs.last().toFloat().let { if (it <= x0) x0 + 1 else it }
    Canvas(Modifier.fillMaxWidth().height(140.dp).background(CardBg, RoundedCornerShape(10.dp)).padding(8.dp)) {
        fun p(i: Int) = Offset((xs[i] - x0) / (x1 - x0) * size.width, size.height - (ys[i] - lo) / (hi - lo) * size.height)
        for ((v, c) in refs) {
            val y = size.height - (v.toFloat() - lo) / (hi - lo) * size.height
            drawLine(c.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        }
        for (i in 1 until xs.size) {
            if (xs[i] - xs[i - 1] > gapSec) continue
            drawLine(color, p(i - 1), p(i), strokeWidth = 3f)
        }
    }
}

/** Daily bars; null = gap. */
@Composable
fun BarChart(values: List<Int?>, color: Color = Cyan) {
    val hi = (values.filterNotNull().maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
    Canvas(Modifier.fillMaxWidth().height(110.dp).background(CardBg, RoundedCornerShape(10.dp)).padding(8.dp)) {
        val w = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val h = v / hi * size.height
            drawRect(color, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h))
        }
    }
}

private fun stageRow(s: Stage) = when (s) { Stage.AWAKE -> 0; Stage.REM -> 1; Stage.LIGHT -> 2; Stage.DEEP -> 3 }
private fun stageColor(s: Stage) = when (s) {
    Stage.AWAKE -> Color(0xFFFFB74D); Stage.REM -> Color(0xFF64B5F6); Stage.LIGHT -> Color(0xFF9575CD); Stage.DEEP -> Color(0xFF3949AB)
}

/** Hypnogram with optional HR overlay (bpm points within the night). */
@Composable
fun Hypnogram(stages: List<StageSpan>, hr: List<Pair<Instant, Int>> = emptyList()) {
    if (stages.isEmpty()) return
    val t0 = stages.first().start.epochSecond.toFloat()
    val t1 = stages.last().end.epochSecond.toFloat().let { if (it <= t0) t0 + 1 else it }
    Canvas(Modifier.fillMaxWidth().height(150.dp).background(CardBg, RoundedCornerShape(10.dp)).padding(8.dp)) {
        val rowH = size.height / 4
        fun x(i: Instant) = (i.epochSecond - t0) / (t1 - t0) * size.width
        stages.forEach { s ->
            drawRect(stageColor(s.stage), Offset(x(s.start), stageRow(s.stage) * rowH), Size((x(s.end) - x(s.start)).coerceAtLeast(1f), rowH))
        }
        if (hr.size > 1) {
            val lo = hr.minOf { it.second }.toFloat()
            val hi = hr.maxOf { it.second }.toFloat().let { if (it <= lo) lo + 1 else it }
            for (i in 1 until hr.size) {
                val a = Offset(x(hr[i - 1].first), size.height - (hr[i - 1].second - lo) / (hi - lo) * size.height)
                val b = Offset(x(hr[i].first), size.height - (hr[i].second - lo) / (hi - lo) * size.height)
                drawLine(Bad, a, b, strokeWidth = 2f)
            }
        }
        drawRect(Muted, style = Stroke(1f))
    }
    Row {
        listOf(Stage.AWAKE to "Thức", Stage.REM to "REM", Stage.LIGHT to "Nông", Stage.DEEP to "Sâu").forEach { (s, n) ->
            Text("■ $n  ", color = stageColor(s), fontSize = 12.sp)
        }
        if (hr.isNotEmpty()) Text("— nhịp tim", color = Bad, fontSize = 12.sp)
    }
}

@Composable
fun Footnote(text: String) = Text(text, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp, bottom = 32.dp))
