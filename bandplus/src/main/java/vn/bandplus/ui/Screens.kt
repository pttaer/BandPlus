package vn.bandplus.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.bandplus.analytics.Zone
import vn.bandplus.analytics.bedtimeSdMin
import vn.bandplus.analytics.delta
import vn.bandplus.analytics.dropsPerHour
import vn.bandplus.analytics.efficiency
import vn.bandplus.analytics.hrZones
import vn.bandplus.analytics.maxHrFor
import vn.bandplus.analytics.meanOrNull
import vn.bandplus.analytics.median
import vn.bandplus.analytics.minutesBelow
import vn.bandplus.analytics.readiness
import vn.bandplus.analytics.restingHr
import vn.bandplus.analytics.rhrElevated
import vn.bandplus.analytics.score
import vn.bandplus.analytics.sdann
import vn.bandplus.analytics.sleepDebtMin
import vn.bandplus.data.DaySummary
import vn.bandplus.data.GbScreen
import vn.bandplus.data.HealthSource
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val DM = DateTimeFormatter.ofPattern("dd/MM")
private fun hm(min: Int?) = min?.let { "${it / 60}h ${it % 60}m" }
private fun today() = LocalDate.now()

/** Days [today-n+1 .. today]; DaySummary per day. */
private fun HealthSource.lastDays(n: Int): List<DaySummary> =
    (n - 1 downTo 0).map { day(today().minusDays(it.toLong())) }

private fun prefs(ctx: Context) = ctx.getSharedPreferences("bandplus", Context.MODE_PRIVATE)
fun userAge(ctx: Context) = prefs(ctx).getInt("age", 30)

// ---------------------------------------------------------------- Dashboard

enum class Card { STEPS, KCAL, ACTIVE, RHR, SLEEP, SPO2, STRESS, READINESS, BATTERY }

@Composable
fun DashboardScreen(src: HealthSource, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var order by remember {
        mutableStateOf(prefs(ctx).getString("cards", null)?.split(",")?.mapNotNull { n -> Card.entries.find { it.name == n } }
            ?.let { saved -> saved + (Card.entries - saved.toSet()) } ?: Card.entries.toList())
    }
    val week by load(refresh) { src.lastDays(8) }
    val synced by load(refresh) { src.lastSyncedAt() }
    val status by load(refresh) { src.status() }
    val nights by load(refresh) { (1..14).mapNotNull { src.day(today().minusDays(it.toLong())).sleep } }

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(status?.name ?: "BandPlus", color = Color.White, fontSize = 22.sp)
            Text(
                (if (status?.connected == true) "Đã kết nối" else "Chưa kết nối") +
                    " · Đồng bộ lúc " + (synced?.atZone(ZoneId.systemDefault())?.format(HM) ?: "—"),
                color = Muted, fontSize = 13.sp
            )
        }
        Button(onClick = { src.sync(); refresh++ }) { Text("Đồng bộ") }
    }
    Spacer(Modifier.height(12.dp))
    val w = week ?: return Text("Đang tải…", color = Muted)
    val t = w.last()
    val y = w[w.size - 2]
    val prev7 = w.dropLast(1)
    fun avg(f: (DaySummary) -> Int?) = prev7.map(f).meanOrNull()
    val sleepScore = t.sleep?.let { score(it, nights.orEmpty()).total }

    order.forEachIndexed { idx, c ->
        val m = Modifier.padding(vertical = 4.dp)
        when (c) {
            Card.STEPS -> MetricCard("Bước chân", t.steps?.toString(), "", delta(t.steps, y.steps), delta(t.steps, avg { it.steps }), modifier = m)
            Card.KCAL -> MetricCard("Calo vận động", t.kcal?.toString(), "kcal", delta(t.kcal, y.kcal), delta(t.kcal, avg { it.kcal }), modifier = m)
            Card.ACTIVE -> MetricCard("Phút vận động", t.activeMin?.toString(), "phút", delta(t.activeMin, y.activeMin), delta(t.activeMin, avg { it.activeMin }), modifier = m)
            Card.RHR -> MetricCard("Nhịp tim nghỉ", t.restingHr?.toString(), "bpm", delta(t.restingHr, y.restingHr), delta(t.restingHr, avg { it.restingHr }), higherIsBetter = false, modifier = m) { go("heart") }
            Card.SLEEP -> MetricCard(
                "Giấc ngủ" + (sleepScore?.let { " · điểm $it" } ?: ""), hm(t.sleep?.asleepMin), "",
                delta(t.sleep?.asleepMin, y.sleep?.asleepMin), delta(t.sleep?.asleepMin, avg { it.sleep?.asleepMin }), modifier = m
            ) { go("sleep") }
            Card.SPO2 -> MetricCard("SpO2 TB / thấp nhất", t.spo2Avg?.let { "$it / ${t.spo2Min}" }, "%", delta(t.spo2Avg, y.spo2Avg), delta(t.spo2Avg, avg { it.spo2Avg }), modifier = m) { go("vitals") }
            Card.STRESS -> MetricCard("Căng thẳng TB", t.stressAvg?.toString(), "", delta(t.stressAvg, y.stressAvg), delta(t.stressAvg, avg { it.stressAvg }), higherIsBetter = false, modifier = m) { go("vitals") }
            Card.READINESS -> {
                val base = prev7.mapNotNull { it.restingHr }.median()
                val r = readiness(t.restingHr?.let { rh -> base?.let { rh - it } }, sleepScore, t.spo2Min, y.stressAvg)
                MetricCard("Sẵn sàng", r?.toString(), "/100", higherIsBetter = null, modifier = m) { go("heart") }
            }
            Card.BATTERY -> MetricCard("Pin vòng", status?.battery?.toString(), "%", higherIsBetter = null, modifier = m)
        }
        if (editing) Row {
            TextButton(onClick = { order = order.toMutableList().apply { if (idx > 0) add(idx - 1, removeAt(idx)) } }) { Text("▲ Lên") }
            TextButton(onClick = { order = order.toMutableList().apply { if (idx < size - 1) add(idx + 1, removeAt(idx)) } }) { Text("▼ Xuống") }
        }
    }
    TextButton(onClick = {
        if (editing) prefs(ctx).edit().putString("cards", order.joinToString(",") { it.name }).apply()
        editing = !editing
    }) { Text(if (editing) "Lưu thứ tự" else "Sắp xếp thẻ") }
    Footnote("Ngày không đeo vòng hiển thị “—” và không tính vào trung bình 7 ngày.")
}

// ---------------------------------------------------------------- Sleep

@Composable
fun SleepScreen(src: HealthSource) {
    var day by remember { mutableStateOf(today()) }
    val d by load(day) { src.day(day) }
    val history by load(day) { (1..90).map { src.day(day.minusDays(it.toLong())) } }
    val hr by load(day) { src.hr(day) + src.hr(day.minusDays(1)) }
    DayPicker(day) { day = it }
    val n = d?.sleep ?: return Text(if (d == null) "Đang tải…" else "Không có dữ liệu giấc ngủ cho ngày này.", color = Muted)
    val last14 = history.orEmpty().take(14).mapNotNull { it.sleep }
    val s = score(n, last14)
    val z = ZoneId.systemDefault()

    SectionTitle("Điểm giấc ngủ: ${s.total}/100")
    val asleep = n.asleepMin.coerceAtLeast(1)
    Line("Thời lượng (${hm(n.asleepMin)}, mục tiêu 7–9h)", "${s.duration}/35")
    Line("Ngủ sâu (${n.deepMin * 100 / asleep}%, mục tiêu ≥15%)", "${s.deep}/20")
    Line("REM (${n.remMin * 100 / asleep}%, mục tiêu ≥20%)", "${s.rem}/20")
    Line("Liên tục (thức ${n.awakeMin} phút, mục tiêu <5%)", "${s.continuity}/15")
    Line("Đều đặn (lệch giờ ngủ ${bedtimeSdMin(last14 + n)?.toInt() ?: "—"} phút, mục tiêu <30)", "${s.consistency}/10")

    SectionTitle("Biểu đồ giai đoạn + nhịp tim")
    val nightHr = hr.orEmpty().filter { it.t >= n.start && it.t <= n.end }.sortedBy { it.t }
    Hypnogram(n.stages, nightHr.map { it.t to it.bpm })

    SectionTitle("Chi tiết đêm")
    Line("Lên giường → dậy", "${n.start.atZone(z).format(HM)} → ${n.end.atZone(z).format(HM)}")
    Line("Hiệu quả giấc ngủ", "${(efficiency(n) * 100).toInt()}%")
    val firstAsleep = n.stages.firstOrNull { it.stage != vn.bandplus.data.Stage.AWAKE }
    Line("Thời gian vào giấc", firstAsleep?.let { "${Duration.between(n.start, it.start).toMinutes()} phút" } ?: "—")
    val asleepStart = firstAsleep?.start ?: n.start
    val awakenings = n.stages.filter { it.stage == vn.bandplus.data.Stage.AWAKE && it.start > asleepStart }
    Line("Số lần thức / WASO", "${awakenings.size} lần / ${awakenings.sumOf { Duration.between(it.start, it.end).toMinutes() }} phút")
    val firstRem = n.stages.firstOrNull { it.stage == vn.bandplus.data.Stage.REM }
    Line("Độ trễ REM", firstRem?.let { "${Duration.between(asleepStart, it.start).toMinutes()} phút" } ?: "—")
    val low = nightHr.minByOrNull { it.bpm }
    Line("Nhịp tim thấp nhất khi ngủ", low?.let {
        val pct = Duration.between(n.start, it.t).toMinutes() * 100 / Duration.between(n.start, n.end).toMinutes().coerceAtLeast(1)
        "${it.bpm} bpm lúc ${it.t.atZone(z).format(HM)} (${pct}% đêm)"
    } ?: "—")
    Line("Nhịp tim nghỉ (30 phút thấp nhất)", restingHr(nightHr, n.start, n.end)?.let { "$it bpm" } ?: "—")
    val mid = n.start.plus(Duration.between(n.start, n.end).dividedBy(2)).atZone(z)
    Line("Điểm giữa giấc ngủ", mid.format(HM))
    val week = listOfNotNull(n) + history.orEmpty().take(6).mapNotNull { it.sleep }
    Line("Nợ ngủ 7 ngày (mục tiêu 8h)", hm(sleepDebtMin(week)) ?: "—")
    val (weekend, weekday) = (listOf(d!!) + history.orEmpty().take(27)).filter { it.sleep != null }
        .partition { it.date.dayOfWeek.value >= 6 }
    fun midMin(l: List<DaySummary>) = l.map { x -> x.sleep!!.let { s2 -> s2.start.plus(Duration.between(s2.start, s2.end).dividedBy(2)).atZone(z).toLocalTime().toSecondOfDay() / 60 } }
        .map { if (it < 12 * 60) it + 24 * 60 else it }.takeIf { it.isNotEmpty() }?.average()
    val jet = midMin(weekend)?.let { a -> midMin(weekday)?.let { b -> (a - b).toInt() } }
    Line("Lệch giờ cuối tuần (social jetlag)", jet?.let { "$it phút" } ?: "—")

    SectionTitle("Xu hướng 90 ngày (giờ ngủ)")
    BarChart((history.orEmpty().reversed() + d!!).map { it.sleep?.asleepMin })
    Footnote("Điểm giấc ngủ là ước lượng cá nhân, không phải chẩn đoán y khoa. Đêm được tính cho ngày thức dậy.")
}

@Composable
private fun DayPicker(day: LocalDate, onChange: (LocalDate) -> Unit) = Row {
    TextButton(onClick = { onChange(day.minusDays(1)) }) { Text("◀") }
    Text(if (day == today()) "Hôm nay" else day.format(DM), color = Color.White, modifier = Modifier.padding(top = 12.dp))
    TextButton(onClick = { if (day < today()) onChange(day.plusDays(1)) }) { Text("▶") }
}

// ---------------------------------------------------------------- Heart

@Composable
fun HeartScreen(src: HealthSource) {
    val ctx = LocalContext.current
    var day by remember { mutableStateOf(today()) }
    val hr by load(day) { src.hr(day) }
    val hist by load(day) { (0..89).map { src.day(day.minusDays(it.toLong())) } }
    DayPicker(day) { day = it }
    val pts = hr ?: return Text("Đang tải…", color = Muted)
    val maxHr = maxHrFor(userAge(ctx))
    val rhrToday = hist?.firstOrNull()?.restingHr

    SectionTitle("Nhịp tim trong ngày")
    LineChart(pts.map { it.t.epochSecond }, pts.map { it.bpm })
    if (pts.isNotEmpty()) Line("Thấp / TB / cao", "${pts.minOf { it.bpm }} / ${pts.map { it.bpm }.average().toInt()} / ${pts.maxOf { it.bpm }} bpm")

    SectionTitle("Vùng nhịp tim (Karvonen, max $maxHr)")
    val zones = hrZones(pts, maxHr, rhrToday)
    val names = mapOf(Zone.REST to "Nghỉ", Zone.Z1 to "Khởi động", Zone.Z2 to "Đốt mỡ", Zone.Z3 to "Aerobic", Zone.Z4 to "Ngưỡng", Zone.Z5 to "Tối đa")
    zones.forEach { (z, dur) -> Line(names.getValue(z), "${dur.toMinutes()} phút") }

    SectionTitle("HRV")
    Line("SDANN (24h), HRV theo nhịp ngày", sdann(pts)?.let { "%.0f ms".format(it) } ?: "—")
    Text("Vòng Band 9 không gửi khoảng RR, nên không có RMSSD thật. Dùng đai ngực để đo RMSSD.", color = Muted, fontSize = 12.sp)

    SectionTitle("Nhịp tim nghỉ 90 ngày")
    val h = hist.orEmpty()
    BarChart(h.reversed().map { it.restingHr })
    val rhrs = h.mapNotNull { it.restingHr }
    Line("Mốc 30 ngày (trung vị)", h.take(30).mapNotNull { it.restingHr }.median()?.let { "%.0f bpm".format(it) } ?: "—")
    if (rhrElevated(h.drop(2).take(30).mapNotNull { it.restingHr }, h.take(2).mapNotNull { it.restingHr }))
        MetricCard("Cảnh báo", "Nhịp tim nghỉ cao +5 bpm 2 sáng liền", higherIsBetter = null, modifier = Modifier.padding(top = 8.dp))
    if (rhrs.isNotEmpty()) Line("VO2max ước tính (Uth)", "%.0f".format(vn.bandplus.analytics.vo2maxEstimate(maxHr, rhrs.first())))
    TextButton(onClick = { src.open(GbScreen.HR_ZONES) }) { Text("Cài đặt vùng nhịp tim / cảnh báo trên vòng") }
    Footnote("Các chỉ số là ước lượng, không phải chẩn đoán y khoa. Tuổi dùng để tính nhịp tối đa: ${userAge(ctx)} (đổi trong Cài đặt).")
}

// ---------------------------------------------------------------- SpO2 + stress

@Composable
fun VitalsScreen(src: HealthSource) {
    var day by remember { mutableStateOf(today()) }
    val spo2 by load(day) { src.spo2(day) }
    val stress by load(day) { src.stress(day) }
    val hist by load(day) { (0..89).map { src.day(day.minusDays(it.toLong())) } }
    val nights by load(day) { (0..29).mapNotNull { src.day(day.minusDays(it.toLong())).let { d -> d.sleep?.let { s -> d to score(s, emptyList()).total } } } }
    DayPicker(day) { day = it }

    SectionTitle("SpO2")
    val sp = spo2.orEmpty()
    LineChart(sp.map { it.t.epochSecond }, sp.map { it.v }, gapSec = 3600, minY = 85, maxY = 100)
    if (sp.isNotEmpty()) {
        Line("TB / thấp nhất", "${sp.map { it.v }.average().toInt()}% / ${sp.minOf { it.v }}%")
        Line("Thời gian < 95% / < 90%", "${minutesBelow(sp, 95)} / ${minutesBelow(sp, 90)} phút")
        Line("Số lần giảm ≥3%/giờ (ước lượng)", dropsPerHour(sp)?.let { "%.1f".format(it) } ?: "— (mẫu thưa hơn 1 phút)")
    }
    SectionTitle("SpO2 thấp nhất mỗi đêm (90 ngày)")
    BarChart(hist.orEmpty().reversed().map { it.spo2Min?.let { v -> v - 80 } })

    SectionTitle("Căng thẳng trong ngày")
    val st = stress.orEmpty()
    LineChart(st.map { it.t.epochSecond }, st.map { it.v }, gapSec = 3600, minY = 0, maxY = 100)
    if (st.isNotEmpty()) {
        val bands = listOf("Thư giãn" to 1..39, "Bình thường" to 40..59, "Trung bình" to 60..79, "Cao" to 80..100)
        bands.forEach { (name, r) -> Line(name, "${st.count { it.v in r } * 100 / st.size}%") }
    }
    SectionTitle("Căng thẳng ngày trước vs điểm ngủ (30 đêm)")
    val pairs = nights.orEmpty().mapNotNull { (d, sc) ->
        hist.orEmpty().find { it.date == d.date.minusDays(1) }?.stressAvg?.let { it to sc }
    }
    if (pairs.size < 5) Text("Cần ít nhất 5 đêm có dữ liệu.", color = Muted, fontSize = 13.sp)
    else {
        val (lo, hi) = pairs.partition { it.first < pairs.map { p -> p.first }.average() }
        Line("Ngày căng thẳng thấp → điểm ngủ TB", "%.0f".format(lo.map { it.second }.average()))
        Line("Ngày căng thẳng cao → điểm ngủ TB", "%.0f".format(hi.map { it.second }.average()))
    }
    Footnote("Ước lượng, không phải chẩn đoán y khoa.")
}

// ---------------------------------------------------------------- Band + settings

@Composable
fun BandScreen(src: HealthSource, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var age by remember { mutableIntStateOf(userAge(ctx)) }
    SectionTitle("Vòng đeo")
    listOf(
        "Cài đặt vòng" to GbScreen.DEVICE_SETTINGS,
        "Báo thức" to GbScreen.ALARMS,
        "Nhắc nhở" to GbScreen.REMINDERS,
        "Cài mặt đồng hồ từ tệp (.bin)" to GbScreen.WATCHFACE_INSTALL,
        "Bài tập (GPS, vùng nhịp tim, xuất GPX)" to GbScreen.WORKOUTS,
    ).forEach { (label, s) -> OutlinedButton(onClick = { src.open(s) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(label) } }
    SectionTitle("Thông báo")
    OutlinedButton(onClick = { src.open(GbScreen.NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Ứng dụng, giờ yên lặng") }
    OutlinedButton(onClick = { src.open(GbScreen.APP_NOTIFICATION_RULES) }, modifier = Modifier.fillMaxWidth()) { Text("Lọc theo từ khoá từng ứng dụng") }
    SectionTitle("Dữ liệu")
    OutlinedButton(onClick = { go("reports") }, modifier = Modifier.fillMaxWidth()) { Text("Báo cáo tuần / tháng, xuất CSV") }
    OutlinedButton(onClick = { src.open(GbScreen.HEALTH_CONNECT) }, modifier = Modifier.fillMaxWidth()) { Text("Đồng bộ Health Connect") }
    OutlinedButton(onClick = { go("reliability") }, modifier = Modifier.fillMaxWidth()) { Text("Giữ kết nối qua đêm") }
    OutlinedButton(onClick = { src.open(GbScreen.GB_HOME) }, modifier = Modifier.fillMaxWidth()) { Text("Mở Gadgetbridge (ghép đôi, nâng cao)") }
    SectionTitle("Hồ sơ")
    Row {
        Text("Tuổi: $age", color = Color.White, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { age--; prefs(ctx).edit().putInt("age", age).apply() }) { Text("−") }
        TextButton(onClick = { age++; prefs(ctx).edit().putInt("age", age).apply() }) { Text("+") }
    }
    Footnote("Dựa trên Gadgetbridge (AGPLv3) — codeberg.org/Freeyourgadget/Gadgetbridge. Không tài khoản, không đám mây.")
}

// ---------------------------------------------------------------- Reports + CSV

@Composable
fun ReportsScreen(src: HealthSource) {
    val ctx = LocalContext.current
    var monthly by remember { mutableStateOf(false) }
    val n = if (monthly) 30 else 7
    val cur by load(n) { src.lastDays(n) }
    val prev by load(n) { (2 * n - 1 downTo n).map { src.day(today().minusDays(it.toLong())) } }
    var msg by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val rows = src.lastDays(365)
        ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w -> w.write(toCsv(rows)) }
        msg = "Đã xuất ${rows.size} ngày"
    }
    Row {
        TextButton(onClick = { monthly = false }) { Text(if (!monthly) "● Tuần" else "Tuần") }
        TextButton(onClick = { monthly = true }) { Text(if (monthly) "● Tháng" else "Tháng") }
    }
    val c = cur ?: return Text("Đang tải…", color = Muted)
    val p = prev.orEmpty()
    @Composable fun row(label: String, unit: String, f: (DaySummary) -> Int?) {
        val a = c.map(f).meanOrNull()
        val b = p.map(f).meanOrNull()
        Line(label, (a?.let { "%.0f $unit".format(it) } ?: "—") + (delta(a, b)?.let { "  (${if (it > 0) "+" else ""}$it)" } ?: ""))
    }
    SectionTitle(if (monthly) "30 ngày qua vs 30 ngày trước" else "7 ngày qua vs 7 ngày trước")
    row("Giấc ngủ TB", "phút") { it.sleep?.asleepMin }
    row("Bước chân TB", "") { it.steps }
    row("Nhịp tim nghỉ TB", "bpm") { it.restingHr }
    row("SpO2 TB", "%") { it.spo2Avg }
    row("Căng thẳng TB", "") { it.stressAvg }
    val withSteps = c.filter { it.steps != null }
    withSteps.maxByOrNull { it.steps!! }?.let { Line("Ngày nhiều bước nhất", "${it.date.format(DM)}: ${it.steps}") }
    withSteps.minByOrNull { it.steps!! }?.let { Line("Ngày ít bước nhất", "${it.date.format(DM)}: ${it.steps}") }
    SectionTitle("Bước chân mỗi ngày")
    BarChart(c.map { it.steps })
    Spacer(Modifier.height(12.dp))
    Button(onClick = { export.launch("bandplus-${today()}.csv") }) { Text("Xuất CSV (365 ngày)") }
    msg?.let { Text(it, color = Good) }
}

fun toCsv(rows: List<DaySummary>): String = buildString {
    appendLine("date,steps,active_kcal,active_min,resting_hr,sleep_min,deep_min,light_min,rem_min,awake_min,spo2_avg,spo2_min,stress_avg")
    rows.forEach { d ->
        val s = d.sleep
        appendLine(listOf(d.date, d.steps, d.kcal, d.activeMin, d.restingHr, s?.asleepMin, s?.deepMin, s?.lightMin, s?.remMin, s?.awakeMin, d.spo2Avg, d.spo2Min, d.stressAvg)
            .joinToString(",") { it?.toString() ?: "" })
    }
}
