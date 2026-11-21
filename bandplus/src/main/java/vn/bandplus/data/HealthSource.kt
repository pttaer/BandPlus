package vn.bandplus.data

import java.time.Instant
import java.time.LocalDate

/**
 * Implemented in the app module (vn.bandplus.backend.GbHealthSource) because only it can see
 * Gadgetbridge's DB classes. All calls are blocking; callers use Dispatchers.IO.
 */
interface HealthSource {
    fun day(date: LocalDate): DaySummary
    fun hr(date: LocalDate): List<HrPoint>
    fun spo2(date: LocalDate): List<Point>
    fun stress(date: LocalDate): List<Point>
    fun lastSyncedAt(): Instant?
    fun status(): BandStatus
    fun sync()
    /** Opens an existing Gadgetbridge screen. */
    fun open(screen: GbScreen)
}

enum class GbScreen { DEVICE_SETTINGS, ALARMS, REMINDERS, NOTIFICATIONS, APP_NOTIFICATION_RULES, WATCHFACE_INSTALL, HEALTH_CONNECT, WORKOUTS, HR_ZONES, GB_HOME, EXPORT_DB }

object BandPlus {
    @Volatile var source: HealthSource? = null
}
