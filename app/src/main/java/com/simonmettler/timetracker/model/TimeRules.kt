package com.simonmettler.timetracker.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlin.math.pow

data class Boundary(val atMs: Long, val zoneId: String, val offsetSeconds: Int) {
    companion object {
        fun capture(instant: Instant, zone: ZoneId) = Boundary(
            instant.toEpochMilli(), zone.id, zone.rules.getOffset(instant).totalSeconds
        )
    }
}

object TimeRules {
    val editorFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS").withResolverStyle(ResolverStyle.STRICT)

    fun foregroundColor(argb: Int): Int {
        fun linear(component: Int): Double {
            val value = component / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * linear((argb shr 16) and 255) +
            0.7152 * linear((argb shr 8) and 255) + 0.0722 * linear(argb and 255)
        return if ((luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    fun resolve(local: String, zoneName: String, offset: String): Boundary {
        val dateTime = try { LocalDateTime.parse(local.trim(), editorFormat) }
        catch (_: Exception) { throw IllegalArgumentException("Use YYYY-MM-DD HH:mm:ss.SSS for the date and time.") }
        val zone = try { ZoneId.of(zoneName.trim()) }
        catch (_: Exception) { throw IllegalArgumentException("Enter a valid time zone, for example Europe/Zurich.") }
        val offsets = zone.rules.getValidOffsets(dateTime)
        require(offsets.isNotEmpty()) { "This local time does not exist because the clocks move forward. Choose another time." }
        val chosen = if (offsets.size == 1) offsets.single() else {
            offsets.find { it.id == offset } ?: throw IllegalArgumentException(
                "This local time occurs twice. Choose ${offsets.joinToString(" or ") { it.id }}."
            )
        }
        return Boundary(dateTime.toInstant(chosen).toEpochMilli(), zone.id, chosen.totalSeconds)
    }

    fun originalLocal(boundary: Boundary): String = Instant.ofEpochMilli(boundary.atMs)
        .atOffset(ZoneOffset.ofTotalSeconds(boundary.offsetSeconds)).format(editorFormat)

    fun dayWindow(day: LocalDate, zone: ZoneId): Pair<Long, Long> =
        day.atStartOfDay(zone).toInstant().toEpochMilli() to day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun overlaps(aStart: Long, aEnd: Long?, bStart: Long, bEnd: Long?): Boolean {
        if (aEnd == aStart || bEnd == bStart) return false
        return (bEnd == null || aStart < bEnd) && (aEnd == null || bStart < aEnd)
    }

    fun clippedDuration(start: Long, end: Long?, from: Long, to: Long, now: Long): Long =
        (minOf(end ?: now, to) - maxOf(start, from)).coerceAtLeast(0)
}
