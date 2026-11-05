package com.simonmettler.timetracker

import com.simonmettler.timetracker.model.Boundary
import com.simonmettler.timetracker.model.TimeRules
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class TimeRulesTest {
    @Test fun travelUsesInstantsInsteadOfLocalLabels() {
        val start = TimeRules.resolve("2026-11-05 10:00:00.000", "Europe/Zurich", "")
        val end = TimeRules.resolve("2026-11-05 20:00:00.000", "Asia/Tokyo", "")
        assertEquals(2 * 3_600_000L, end.atMs - start.atMs)
    }
    @Test fun daylightSavingDaysHaveRealLengths() {
        val zone = ZoneId.of("Europe/Zurich")
        val spring = TimeRules.dayWindow(LocalDate.of(2026, 3, 29), zone)
        val autumn = TimeRules.dayWindow(LocalDate.of(2026, 10, 25), zone)
        assertEquals(23 * 3_600_000L, spring.second - spring.first)
        assertEquals(25 * 3_600_000L, autumn.second - autumn.first)
    }
    @Test fun nonexistentLocalTimeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { TimeRules.resolve("2026-03-29 02:30:00.000", "Europe/Zurich", "") }
    }
    @Test fun repeatedLocalTimeRequiresExplicitOffset() {
        assertThrows(IllegalArgumentException::class.java) { TimeRules.resolve("2026-10-25 02:30:00.000", "Europe/Zurich", "") }
        val early = TimeRules.resolve("2026-10-25 02:30:00.000", "Europe/Zurich", "+02:00")
        val late = TimeRules.resolve("2026-10-25 02:30:00.000", "Europe/Zurich", "+01:00")
        assertEquals(3_600_000L, late.atMs - early.atMs)
    }
    @Test fun invalidCalendarDatesAreNotSilentlyAdjusted() {
        assertThrows(IllegalArgumentException::class.java) { TimeRules.resolve("2026-02-30 12:00:00.000", "UTC", "") }
    }
    @Test fun boundaryRetainsCapturedOffsetAndMilliseconds() {
        val instant = Instant.parse("2026-11-05T09:12:13.456Z")
        val boundary = Boundary.capture(instant, ZoneId.of("Asia/Kathmandu"))
        assertEquals(20_700, boundary.offsetSeconds)
        assertEquals("2026-11-05 14:57:13.456", TimeRules.originalLocal(boundary))
        assertEquals(instant.toEpochMilli(), boundary.atMs)
    }
    @Test fun adjacentHalfOpenIntervalsDoNotOverlap() {
        assertFalse(TimeRules.overlaps(0, 10, 10, 20))
        assertTrue(TimeRules.overlaps(0, 11, 10, 20))
        assertTrue(TimeRules.overlaps(10, null, 11, 20))
    }
    @Test fun zeroDurationRecordsAreEmptyEvenInsideAnotherInterval() {
        assertFalse(TimeRules.overlaps(5, 5, 0, 10))
        assertFalse(TimeRules.overlaps(0, null, 5, 5))
    }
    @Test fun dailyTotalsClipWithoutSplittingRows() {
        assertEquals(10L, TimeRules.clippedDuration(0, 30, 10, 20, 40))
        assertEquals(5L, TimeRules.clippedDuration(0, null, 10, 20, 15))
        assertEquals(0L, TimeRules.clippedDuration(30, null, 10, 20, 15))
    }
    @Test fun foregroundChoosesReadableBlackOrWhite() {
        assertEquals(0xFF000000.toInt(), TimeRules.foregroundColor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), TimeRules.foregroundColor(0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(), TimeRules.foregroundColor(0xFFFFFF00.toInt()))
    }
}
