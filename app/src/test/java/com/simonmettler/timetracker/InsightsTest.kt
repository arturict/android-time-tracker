package com.simonmettler.timetracker

import com.simonmettler.timetracker.model.DayRange
import com.simonmettler.timetracker.model.Insights
import com.simonmettler.timetracker.model.Period
import com.simonmettler.timetracker.model.Span
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsTest {
    private val zurich = ZoneId.of("Europe/Zurich")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zurich).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    @Test fun weekStartsOnMondayAndMonthOnTheFirst() {
        assertEquals(DayRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9)), Insights.range(Period.Week, LocalDate.of(2026, 11, 5)))
        assertEquals(DayRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)), Insights.range(Period.Month, LocalDate.of(2026, 2, 17)))
        assertEquals(LocalDate.of(2026, 10, 26), Insights.previous(Period.Week, Insights.range(Period.Week, LocalDate.of(2026, 11, 5))).start)
    }

    @Test fun intervalAcrossMidnightIsSplitBetweenDays() {
        val spans = listOf(Span("sleep", at("2026-11-02T22:00"), at("2026-11-03T06:00")))
        val days = Insights.perDay(spans, Insights.range(Period.Week, LocalDate.of(2026, 11, 2)), zurich)
        assertEquals(2 * hour, days[0]["sleep"])
        assertEquals(6 * hour, days[1]["sleep"])
        assertEquals(null, days[2]["sleep"])
    }

    @Test fun hourOfDaySplitsAtLocalHourBoundaries() {
        val spans = listOf(Span("work", at("2026-11-02T08:30"), at("2026-11-02T10:15")))
        val (from, to) = Insights.window(Insights.range(Period.Week, LocalDate.of(2026, 11, 2)), zurich)
        val hours = Insights.perHourOfDay(spans, from, to, zurich)
        assertEquals(hour / 2, hours[8]["work"])
        assertEquals(hour, hours[9]["work"])
        assertEquals(hour / 4, hours[10]["work"])
    }

    @Test fun repeatedAutumnHourCountsTwiceInItsBucket() {
        // 25-hour day: 02:00–03:00 local happens twice.
        val day = LocalDate.of(2026, 10, 25)
        val (from, to) = Insights.window(DayRange(day, day.plusDays(1)), zurich)
        val hours = Insights.perHourOfDay(listOf(Span("night", from, to)), from, to, zurich)
        assertEquals(2 * hour, hours[2]["night"])
        assertEquals(25 * hour, hours.sumOf { it["night"] ?: 0 })
    }

    @Test fun averagesIgnoreFutureDays() {
        val week = Insights.range(Period.Week, LocalDate.of(2026, 11, 5))
        assertEquals(4, Insights.elapsedDays(week, LocalDate.of(2026, 11, 5)))
        assertEquals(7, Insights.elapsedDays(week, LocalDate.of(2026, 12, 1)))
    }
}
