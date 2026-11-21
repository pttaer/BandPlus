package vn.bandplus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import vn.bandplus.R
import vn.bandplus.data.BandPlus
import vn.bandplus.ui.BandPlusActivity
import java.time.LocalDate
import kotlin.concurrent.thread

/** Home-screen widget: steps, last night's sleep, band battery. Plain AppWidget, no Glance dependency. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        thread {
            try { render(ctx, mgr, ids) } finally { pending.finish() }
        }
    }

    companion object {
        /** Call from background threads (workers) to refresh all widgets. */
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val src = BandPlus.source
            val d = src?.day(LocalDate.now())
            val battery = src?.status()?.battery
            val v = RemoteViews(ctx.packageName, R.layout.bp_widget_today)
            v.setTextViewText(R.id.bp_widget_steps, "${d?.steps ?: "—"} bước")
            v.setTextViewText(R.id.bp_widget_sleep, "Ngủ " + (d?.sleep?.asleepMin?.let { "${it / 60}h ${it % 60}m" } ?: "—"))
            v.setTextViewText(R.id.bp_widget_battery, "Pin " + (battery?.let { "$it%" } ?: "—"))
            val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, BandPlusActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.bp_widget_root, pi)
            mgr.updateAppWidget(ids, v)
        }
    }
}
