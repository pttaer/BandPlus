package vn.bandplus.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import vn.bandplus.analytics.meanOrNull
import vn.bandplus.analytics.sustainedHr
import vn.bandplus.data.BandPlus
import vn.bandplus.ui.BandPlusActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.concurrent.TimeUnit

private const val CHANNEL = "bandplus"

fun bpPrefs(ctx: Context) = ctx.getSharedPreferences("bandplus", Context.MODE_PRIVATE)

/** Idempotent; call on every app open. */
fun scheduleWork(ctx: Context) {
    val wm = WorkManager.getInstance(ctx)
    wm.enqueueUniquePeriodicWork("bp-sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).build())
    wm.enqueueUniquePeriodicWork("bp-weekly", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<WeeklyReportWorker>(1, TimeUnit.HOURS).build())
}

private fun notify(ctx: Context, id: Int, title: String, text: String) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "BandPlus", NotificationManager.IMPORTANCE_DEFAULT))
    val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, BandPlusActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(ctx, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title).setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(pi).setAutoCancel(true).build()
    try { nm.notify(id, n) } catch (_: SecurityException) { } // POST_NOTIFICATIONS denied
}

/**
 * Every 30 min: ask the band for new data (keeps sleep flowing in overnight), then check HR alerts
 * on what earlier fetches stored. One alert per episode: the episode start is remembered.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val src = BandPlus.source ?: return Result.success()
        src.sync()
        vn.bandplus.widget.TodayWidget.refresh(applicationContext)
        val p = bpPrefs(applicationContext)
        val high = p.getInt("hr_high", 100)
        val low = p.getInt("hr_low", 40)
        val today = LocalDate.now()
        // resting alerts only: drop HR within 5 min after any minute with >= 30 steps (walking, workouts)
        val activeMin = src.steps(today).filter { it.v >= 30 }.map { it.t.epochSecond / 60 }.toSet()
        val resting = src.hr(today).filter { p -> val m = p.t.epochSecond / 60; (0..5).none { (m - it) in activeMin } }
        val hit = sustainedHr(resting, low, high) ?: return Result.success()
        val key = hit.first.t.epochSecond
        if (p.getLong("hr_alert_last", 0) == key) return Result.success()
        p.edit().putLong("hr_alert_last", key).apply()
        val at = hit.first.t.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        notify(applicationContext, 1, "Cảnh báo nhịp tim",
            if (hit.second) "Nhịp tim trên $high bpm ≥10 phút từ $at." else "Nhịp tim dưới $low bpm ≥10 phút từ $at.")
        return Result.success()
    }
}

/** Hourly check; posts the weekly report once, Monday from 08:00. */
class WeeklyReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        if (now.dayOfWeek != DayOfWeek.MONDAY || now.hour < 8) return Result.success()
        val week = "${now.get(IsoFields.WEEK_BASED_YEAR)}-${now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        val p = bpPrefs(applicationContext)
        if (p.getString("weekly_sent", null) == week) return Result.success()
        val src = BandPlus.source ?: return Result.success()
        val today = now.toLocalDate()
        val cur = (1..7).map { src.day(today.minusDays(it.toLong())) }
        val prev = (8..14).map { src.day(today.minusDays(it.toLong())) }
        fun line(label: String, unit: String, f: (vn.bandplus.data.DaySummary) -> Int?): String {
            val a = cur.map(f).meanOrNull() ?: return "$label: —"
            val b = prev.map(f).meanOrNull()
            val d = b?.let { Math.round(a - it) }
            return "$label: ${Math.round(a)}$unit" + (d?.let { " (${if (it > 0) "+" else ""}$it)" } ?: "")
        }
        val best = cur.filter { it.steps != null }.maxByOrNull { it.steps!! }
        val text = listOf(
            line("Ngủ TB", " phút") { it.sleep?.asleepMin },
            line("Bước TB", "") { it.steps },
            line("Nhịp tim nghỉ", " bpm") { it.restingHr },
            best?.let { "Ngày nhiều bước nhất: ${it.date.format(DateTimeFormatter.ofPattern("dd/MM"))} (${it.steps})" },
        ).filterNotNull().joinToString("\n")
        notify(applicationContext, 2, "Báo cáo tuần", text)
        p.edit().putString("weekly_sent", week).apply()
        return Result.success()
    }
}
