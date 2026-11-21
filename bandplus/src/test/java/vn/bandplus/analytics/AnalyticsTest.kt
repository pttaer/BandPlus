package vn.bandplus.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.bandplus.data.HrPoint
import vn.bandplus.data.Point
import vn.bandplus.data.SleepNight
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AnalyticsTest {
    private val t0 = Instant.parse("2026-11-20T00:00:00Z")
    private fun night(startHourUtc: Double, deep: Int, light: Int, rem: Int, awake: Int): SleepNight {
        val s = t0.plusSeconds((startHourUtc * 3600).toLong())
        return SleepNight(s, s.plus(Duration.ofMinutes((deep + light + rem + awake).toLong())), deep, light, rem, awake)
    }

    @Test fun perfectNightScores100() {
        val n = night(-1.0, 90, 240, 110, 10) // 7.3 h asleep
        val s = score(n, List(5) { n }, ZoneOffset.UTC)
        assertEquals(100, s.total)
    }

    @Test fun shortNightLosesDuration() {
        val n = night(-1.0, 50, 150, 70, 10) // 4.5 h
        assertTrue(score(n, emptyList(), ZoneOffset.UTC).duration < 35)
    }

    @Test fun bedtimeAcrossMidnightIsClose() {
        val a = night(-0.5, 60, 200, 80, 0) // 23:30
        val b = night(0.5, 60, 200, 80, 0)  // 00:30
        val sd = bedtimeSdMin(listOf(a, b, a, b), ZoneOffset.UTC)!!
        assertTrue("sd=$sd", sd < 40)
    }

    @Test fun debtSkipsMissingNights() {
        assertEquals(60, sleepDebtMin(listOf(night(0.0, 60, 300, 60, 0), null)))
    }

    @Test fun zonesIgnoreGaps() {
        val pts = listOf(HrPoint(t0, 60), HrPoint(t0.plusSeconds(3600), 60))
        val z = hrZones(pts, 200)
        assertEquals(Duration.ofMinutes(2), z.getValue(Zone.REST))
    }

    @Test fun restingHrIsLowest30MinMean() {
        val pts = (0 until 120).map { HrPoint(t0.plusSeconds(it * 60L), if (it in 60..95) 50 else 70) }
        assertEquals(50, restingHr(pts, t0, t0.plusSeconds(7200)))
    }

    @Test fun sdannNeedsAnHour() {
        assertNull(sdann((0 until 30).map { HrPoint(t0.plusSeconds(it * 60L), 60) }))
        val flat = sdann((0 until 120).map { HrPoint(t0.plusSeconds(it * 60L), 60) })!!
        assertEquals(0.0, flat, 1e-9)
    }

    @Test fun rrCleaningAndRmssd() {
        assertEquals(listOf(800, 820, 810), cleanRr(listOf(800, 250, 820, 1200, 810)))
        assertEquals(20.0, rmssd(listOf(800, 820, 800)), 1e-9)
    }

    @Test fun spo2Drops() {
        val pts = (0 until 120).map { Point(t0.plusSeconds(it * 60L), if (it % 30 == 10) 90 else 97) }
        assertEquals(4.0 / (119 / 60.0), dropsPerHour(pts)!!, 0.01)
        assertEquals(4, minutesBelow(pts, 95))
    }

    @Test fun readinessRescalesMissing() {
        assertEquals(80, readiness(null, 80, null, null))
        assertNull(readiness(null, null, null, null))
    }

    @Test fun rhrFlag() {
        assertTrue(rhrElevated(List(30) { 55 }, listOf(61, 60)))
        assertFalse(rhrElevated(List(30) { 55 }, listOf(61, 58)))
    }

    @Test fun sustainedHrNeeds10Minutes() {
        val spike = (0 until 30).map { HrPoint(t0.plusSeconds(it * 60L), if (it in 5..9) 120 else 70) }
        assertNull(sustainedHr(spike, 40, 100))
        val long = (0 until 30).map { HrPoint(t0.plusSeconds(it * 60L), if (it in 5..20) 120 else 70) }
        val hit = sustainedHr(long, 40, 100)!!
        assertEquals(t0.plusSeconds(300), hit.first.t)
        assertTrue(hit.second)
    }

    @Test fun csvHasBlankForMissing() {
        val row = vn.bandplus.ui.toCsv(listOf(vn.bandplus.data.DaySummary(java.time.LocalDate.of(2026, 11, 20), 5000, null, null, null, null, null, null, null)))
        assertEquals("2026-11-20,5000,,,,,,,,,,,", row.lines()[1])
    }

    @Test fun meanIgnoresNulls() {
        assertEquals(5.0, listOf(4, null, 6).meanOrNull()!!, 1e-9)
        assertNull(delta(null, 3))
    }
}
