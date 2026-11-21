package vn.bandplus.backend

import android.content.Context
import android.content.Intent
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import org.slf4j.LoggerFactory
import nodomain.freeyourgadget.gadgetbridge.devices.XiaomiDailySummarySampleProvider
import vn.bandplus.data.BandExtras
import vn.bandplus.data.BandStatus
import vn.bandplus.data.DaySummary
import vn.bandplus.data.GbScreen
import vn.bandplus.data.HealthSource
import vn.bandplus.data.HrPoint
import vn.bandplus.data.Point
import vn.bandplus.data.SleepNight
import vn.bandplus.data.Stage
import vn.bandplus.data.StageSpan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * BandPlus view of Gadgetbridge's DB. Read-only, uses the same coordinator providers as GB's own charts.
 * Day boundaries in phone local time; a sleep belongs to the day it ends.
 */
class GbHealthSource(private val ctx: Context) : HealthSource {
    private val log = LoggerFactory.getLogger(GbHealthSource::class.java)

    init {
        // Task 2: GB auto-fetch on by default (30 min limit), only if the user never touched it.
        val p = GBApplication.getPrefs().preferences
        if (!p.contains(GBPrefs.PREF_AUTO_FETCH_ENABLED)) {
            p.edit().putBoolean(GBPrefs.PREF_AUTO_FETCH_ENABLED, true)
                .putString(GBPrefs.PREF_AUTO_FETCH_INTERVAL_LIMIT, "30").apply()
        }
    }

    private fun device(): GBDevice? {
        val all = GBApplication.app().deviceManager.devices
        return all.firstOrNull { it.isInitialized } ?: all.firstOrNull()
    }

    private fun <T> db(default: T, block: (DBHandler, GBDevice) -> T): T {
        val d = device() ?: return default
        return try {
            GBApplication.acquireDbReadOnly().use { block(it, d) }
        } catch (e: Exception) {
            log.warn("BandPlus query failed", e)
            default
        }
    }

    private fun bounds(date: LocalDate): Pair<Long, Long> {
        val z = ZoneId.systemDefault()
        return date.atStartOfDay(z).toEpochSecond() to date.plusDays(1).atStartOfDay(z).toEpochSecond() - 1
    }

    private fun validHr(v: Int) = v in 25..250

    override fun day(date: LocalDate): DaySummary = db(DaySummary(date, null, null, null, null, null, null, null, null)) { h, d ->
        val (from, to) = bounds(date)
        val c = d.deviceCoordinator
        val s = h.daoSession
        val samples = c.getSampleProvider(d, s)!!.getAllActivitySamples(from.toInt(), to.toInt())
        val hasData = samples.any { it.steps > 0 || validHr(it.heartRate) }
        val steps = if (hasData) samples.sumOf { it.steps.coerceAtLeast(0) } else null
        val kcal = if (hasData) samples.sumOf { it.activeCalories.coerceAtLeast(0) } else null
        // ponytail: cadence >= 100 steps/min as MET>=3 proxy; replace with band's own active-minutes if it starts sending them
        val activeMin = if (hasData) samples.count { it.steps >= 100 } else null

        val resting = runCatching {
            c.getHeartRateRestingSampleProvider(d, s)!!.getAllSamples(from * 1000, to * 1000)
                .map { it.heartRate }.filter(::validHr).minOrNull()
        }.getOrNull() ?: runCatching {
            XiaomiDailySummarySampleProvider(d, s).getAllSamples(from * 1000, to * 1000).lastOrNull()?.hrResting?.takeIf(::validHr)
        }.getOrNull()

        val spo2 = runCatching {
            c.getSpo2SampleProvider(d, s)!!.getAllSamples(from * 1000, to * 1000).map { it.spo2 }.filter { it in 50..100 }
        }.getOrDefault(emptyList())
        val stress = runCatching {
            c.getStressSampleProvider(d, s)!!.getAllSamples(from * 1000, to * 1000).map { it.stress }.filter { it in 1..100 }
        }.getOrDefault(emptyList())

        DaySummary(
            date = date,
            steps = steps,
            kcal = kcal,
            activeMin = activeMin,
            restingHr = resting,
            sleep = sleep(h, d, from, to),
            spo2Avg = spo2.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            spo2Min = spo2.minOrNull(),
            stressAvg = stress.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        )
    }

    private fun sleep(h: DBHandler, d: GBDevice, from: Long, to: Long): SleepNight? {
        val sessions = runCatching {
            d.deviceCoordinator.getSleepSessionProvider(d, h.daoSession)!!.getSleepSessionsEndingIn(from.toInt(), to.toInt())
        }.getOrDefault(emptyList())
        val main = sessions.filter { it.stages.isNotEmpty() }.maxByOrNull { it.totalSleepDuration } ?: return null
        val spans = main.stages.mapNotNull { st ->
            val k = when (st.kind) {
                ActivityKind.DEEP_SLEEP -> Stage.DEEP
                ActivityKind.LIGHT_SLEEP -> Stage.LIGHT
                ActivityKind.REM_SLEEP -> Stage.REM
                ActivityKind.AWAKE_SLEEP -> Stage.AWAKE
                else -> null
            } ?: return@mapNotNull null
            StageSpan(Instant.ofEpochMilli(st.startTime), Instant.ofEpochMilli(st.endTime), k)
        }
        return SleepNight(
            start = Instant.ofEpochMilli(main.startTime),
            end = Instant.ofEpochMilli(main.endTime),
            deepMin = (main.deepSleepDuration / 60).toInt(),
            lightMin = (main.lightSleepDuration / 60).toInt(),
            remMin = (main.remSleepDuration / 60).toInt(),
            awakeMin = (main.awakeSleepDuration / 60).toInt(),
            stages = spans,
        )
    }

    override fun hr(date: LocalDate): List<HrPoint> = db(emptyList()) { h, d ->
        val (from, to) = bounds(date)
        d.deviceCoordinator.getSampleProvider(d, h.daoSession)!!.getAllActivitySamples(from.toInt(), to.toInt())
            .filter { validHr(it.heartRate) }
            .map { HrPoint(Instant.ofEpochSecond(it.timestamp.toLong()), it.heartRate) }
    }

    override fun bandExtras(date: LocalDate): BandExtras = db(BandExtras()) { h, d ->
        val (from, to) = bounds(date)
        val x = runCatching {
            XiaomiDailySummarySampleProvider(d, h.daoSession).getAllSamples(from * 1000, to * 1000).lastOrNull()
        }.getOrNull()
        val resp = runCatching {
            // sleep breathing belongs to the wake-up day: look from 18:00 the previous evening
            d.deviceCoordinator.getRespiratoryRateSampleProvider(d, h.daoSession)!!
                .getAllSamples((from - 6 * 3600) * 1000, to * 1000).map { it.respiratoryRate }.filter { it in 4f..40f }
        }.getOrDefault(emptyList())
        fun pos(v: Int?) = v?.takeIf { it > 0 }
        BandExtras(
            vitality = pos(x?.vitalityCurrent),
            vitalityToday = x?.let { listOfNotNull(it.vitalityIncreaseLight, it.vitalityIncreaseModerate, it.vitalityIncreaseHigh).sum() },
            trainingLoadDay = pos(x?.trainingLoadDay),
            trainingLoadWeek = pos(x?.trainingLoadWeek),
            recoveryHours = x?.recoveryHours?.takeIf { it >= 0 },
            standingHours = x?.standing?.takeIf { it >= 0 },
            hrMin = x?.hrMin?.takeIf(::validHr),
            hrMax = x?.hrMax?.takeIf(::validHr),
            hrAvg = x?.hrAvg?.takeIf(::validHr),
            respiratoryAvg = resp.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    override fun steps(date: LocalDate): List<Point> = db(emptyList()) { h, d ->
        val (from, to) = bounds(date)
        d.deviceCoordinator.getSampleProvider(d, h.daoSession)!!.getAllActivitySamples(from.toInt(), to.toInt())
            .filter { it.steps >= 0 }
            .map { Point(Instant.ofEpochSecond(it.timestamp.toLong()), it.steps) }
    }

    override fun spo2(date: LocalDate): List<Point> = db(emptyList()) { h, d ->
        val (from, to) = bounds(date)
        d.deviceCoordinator.getSpo2SampleProvider(d, h.daoSession)!!.getAllSamples(from * 1000, to * 1000)
            .filter { it.spo2 in 50..100 }.map { Point(Instant.ofEpochMilli(it.timestamp), it.spo2) }
    }

    override fun stress(date: LocalDate): List<Point> = db(emptyList()) { h, d ->
        val (from, to) = bounds(date)
        d.deviceCoordinator.getStressSampleProvider(d, h.daoSession)!!.getAllSamples(from * 1000, to * 1000)
            .filter { it.stress in 1..100 }.map { Point(Instant.ofEpochMilli(it.timestamp), it.stress) }
    }

    override fun lastSyncedAt(): Instant? = db(null) { h, d ->
        d.deviceCoordinator.getSampleProvider(d, h.daoSession)!!.latestActivitySample
            ?.let { Instant.ofEpochSecond(it.timestamp.toLong()) }
    }

    override fun status(): BandStatus {
        val d = device() ?: return BandStatus(null, false, null)
        return BandStatus(d.aliasOrName, d.isInitialized, d.getBatteryLevel(0).takeIf { it in 0..100 })
    }

    override fun sync() {
        val d = device() ?: return
        GBApplication.deviceService(d).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
    }

    override fun open(screen: GbScreen) {
        val pkg = "nodomain.freeyourgadget.gadgetbridge.activities."
        val (cls, needsDevice) = when (screen) {
            GbScreen.DEVICE_SETTINGS -> "devicesettings.DeviceSettingsActivity" to true
            GbScreen.ALARMS -> "ConfigureAlarms" to true
            GbScreen.REMINDERS -> "ConfigureReminders" to true
            GbScreen.NOTIFICATIONS -> "NotificationManagementActivity" to false
            GbScreen.APP_NOTIFICATION_RULES -> "notifications.apps.NotificationAppSettingsActivity" to false
            GbScreen.WATCHFACE_INSTALL -> "install.FwAppInstallerActivity" to true
            GbScreen.HEALTH_CONNECT -> "preferences.HealthConnectPreferencesActivity" to false
            GbScreen.WORKOUTS -> "workouts.WorkoutListActivity" to true
            GbScreen.HR_ZONES -> "heartratezones.HeartRateSettingsActivity" to false
            GbScreen.GB_HOME -> "ControlCenterv2" to false
            GbScreen.EXPORT_DB -> "SettingsActivity" to false
        }
        val i = Intent().setClassName(ctx, pkg + cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (needsDevice) device()?.let { i.putExtra(GBDevice.EXTRA_DEVICE, it) }
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            log.warn("BandPlus could not open {}", cls, e)
        }
    }
}
