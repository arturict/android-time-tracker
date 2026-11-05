package com.simonmettler.timetracker.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A tracked interval reduced to what the statistics need; an open interval ends at "now". */
data class Span(val activityId: String, val start: Long, val end: Long)

enum class Period { Week, Month }

/** Inclusive start, exclusive end, in calendar days. */
data class DayRange(val start: LocalDate, val endExclusive: LocalDate) {
    val days: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it < endExclusive }.toList()
}

object Insights {
    fun range(period: Period, anchor: LocalDate): DayRange = when (period) {
        Period.Week -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { DayRange(it, it.plusWeeks(1)) }
        Period.Month -> anchor.withDayOfMonth(1).let { DayRange(it, it.plusMonths(1)) }
    }

    fun previous(period: Period, range: DayRange): DayRange = when (period) {
        Period.Week -> DayRange(range.start.minusWeeks(1), range.start)
        Period.Month -> DayRange(range.start.minusMonths(1), range.start)
    }

    fun shift(period: Period, anchor: LocalDate, steps: Long): LocalDate = when (period) {
        Period.Week -> anchor.plusWeeks(steps)
        Period.Month -> anchor.plusMonths(steps)
    }

    fun window(range: DayRange, zone: ZoneId): Pair<Long, Long> =
        range.start.atStartOfDay(zone).toInstant().toEpochMilli() to range.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Milliseconds per activity inside [from, to). */
    fun totals(spans: List<Span>, from: Long, to: Long): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        spans.forEach { span ->
            val ms = minOf(span.end, to) - maxOf(span.start, from)
            if (ms > 0) result[span.activityId] = (result[span.activityId] ?: 0) + ms
        }
        return result
    }

    /** One activity→milliseconds map per calendar day. Day lengths follow the zone, so DST days are 23 or 25 hours. */
    fun perDay(spans: List<Span>, range: DayRange, zone: ZoneId): List<Map<String, Long>> = range.days.map { day ->
        val (from, to) = TimeRules.dayWindow(day, zone)
        totals(spans, from, to)
    }

    /**
     * Time per local hour of day (0–23), summed over [from, to). A repeated hour on a
     * daylight-saving change lands in the same bucket; a skipped hour simply gets nothing.
     */
    fun perHourOfDay(spans: List<Span>, from: Long, to: Long, zone: ZoneId): List<Map<String, Long>> {
        val buckets = List(24) { linkedMapOf<String, Long>() }
        spans.forEach { span ->
            var cursor = maxOf(span.start, from)
            val end = minOf(span.end, to)
            while (cursor < end) {
                val local = Instant.ofEpochMilli(cursor).atZone(zone)
                val next = minOf(local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli(), end)
                val bucket = buckets[local.hour]
                bucket[span.activityId] = (bucket[span.activityId] ?: 0) + (next - cursor)
                cursor = next
            }
        }
        return buckets
    }

    /** Days of the range that have started by [today]; averages ignore the future. */
    fun elapsedDays(range: DayRange, today: LocalDate): Int =
        range.days.count { it <= today }.coerceAtLeast(1)
}
