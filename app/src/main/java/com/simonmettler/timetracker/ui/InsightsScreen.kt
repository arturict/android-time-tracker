package com.simonmettler.timetracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simonmettler.timetracker.data.CalendarEntry
import com.simonmettler.timetracker.model.DayRange
import com.simonmettler.timetracker.model.Insights
import com.simonmettler.timetracker.model.Period
import com.simonmettler.timetracker.model.Span
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** Bar-chart glyph for the navigation bar; the core icon set has none. */
val InsightsIcon: ImageVector = ImageVector.Builder("Insights", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 20f); lineTo(4f, 12f); lineTo(7.5f, 12f); lineTo(7.5f, 20f); close()
        moveTo(10.25f, 20f); lineTo(10.25f, 4f); lineTo(13.75f, 4f); lineTo(13.75f, 20f); close()
        moveTo(16.5f, 20f); lineTo(16.5f, 8.5f); lineTo(20f, 8.5f); lineTo(20f, 20f); close()
    }
}.build()

private val shortDay = DateTimeFormatter.ofPattern("d MMM")
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy")

private data class Named(val title: String, val color: Color)

@Composable
fun InsightsScreen(
    period: Period, anchor: LocalDate, zone: ZoneId, loaded: List<CalendarEntry>?,
    onPeriod: (Period) -> Unit, onShift: (Long) -> Unit, onReset: () -> Unit, onOpenDay: (LocalDate) -> Unit
) {
    val now = rememberNow(60_000)
    val today = LocalDate.now(zone)
    val entries = loaded.orEmpty()
    val range = Insights.range(period, anchor)
    val previous = Insights.previous(period, range)
    val (from, to) = Insights.window(range, zone)
    val (prevFrom, prevTo) = Insights.window(previous, zone)
    val names = entries.associate { it.interval.activityId to Named(it.title, Color(it.colorArgb)) }
    val spans = entries.map { Span(it.interval.activityId, it.interval.startAtMs, it.interval.endAtMs ?: now) }
    val current = Insights.totals(spans, from, to)
    // Compare like with like: a half-finished week against the same stretch of the week before.
    val elapsed = minOf(now, to) - from
    val before = Insights.totals(spans, prevFrom, minOf(prevFrom + elapsed, prevTo))
    val order = current.entries.sortedByDescending { it.value }.map { it.key }
    val total = current.values.sum()
    val previousTotal = before.values.sum()
    val days = Insights.elapsedDays(range, today)
    val perDay = Insights.perDay(spans, range, zone)
    val perHour = Insights.perHourOfDay(spans, from, to, zone).map { bucket -> bucket.mapValues { it.value / days } }
    val unit = if (period == Period.Week) "week" else "month"
    // Charts grow in on every period change.
    val progress = remember(period, range) { Animatable(0f) }
    LaunchedEffect(period, range) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WHERE YOUR TIME WENT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(rangeTitle(period, range, today), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(rangeDates(period, range), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { onShift(-1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous $unit") }
                FilledTonalIconButton(onClick = { onShift(1) }, enabled = range.endExclusive <= today) { Icon(Icons.Filled.KeyboardArrowRight, "Next $unit") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    Period.entries.forEachIndexed { index, option ->
                        SegmentedButton(selected = option == period, onClick = { onPeriod(option) }, shape = SegmentedButtonDefaults.itemShape(index, Period.entries.size)) {
                            Text(option.name)
                        }
                    }
                }
                if (today !in range.start..range.endExclusive.minusDays(1)) TextButton(onClick = onReset) { Text("Now") }
            }
        }
        if (loaded == null) item {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (total == 0L) item {
            Card(title = "Nothing tracked yet") {
                Text("Tap an activity on Home and this page will show where your time flows, day by day and hour by hour.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Donut(order.map { names.getValue(it).color to current.getValue(it) }, progress.value, Modifier.size(148.dp)) {
                            Text(compactDuration(total), style = MaterialTheme.typography.headlineSmall.merge(Numeric))
                            Text("tracked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Stat("Daily average", compactDuration(total / days))
                            Stat("Top activity", names.getValue(order.first()).title, "${current.getValue(order.first()) * 100 / total}% of all time")
                            if (previousTotal > 0) {
                                val delta = total - previousTotal
                                Stat("vs last $unit", (if (delta >= 0) "+" else "−") + duration(kotlin.math.abs(delta)), "${if (delta >= 0) "+" else "−"}${kotlin.math.abs(delta) * 100 / previousTotal}%", compact = true)
                            }
                        }
                    }
                }
            }
            item {
                Card(title = "Day by day", subtitle = "Tap a day to open it in the calendar") {
                    StackedColumns(
                        values = perDay, order = order, colors = names.mapValues { it.value.color }, progress = progress.value,
                        labels = range.days.map { day ->
                            if (period == Period.Week) day.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()).take(2)
                            else day.dayOfMonth.takeIf { it == 1 || it % 5 == 0 || day == today }?.toString()
                        },
                        highlight = range.days.indexOf(today).takeIf { it >= 0 },
                        onTap = { onOpenDay(range.days[it]) },
                        description = "Tracked time per day, highest ${duration(perDay.maxOf { it.values.sum() })}"
                    )
                }
            }
            item {
                Card(title = "Your average day", subtitle = "When you spend your time, averaged over $days ${if (days == 1) "day" else "days"}") {
                    StackedColumns(
                        values = perHour, order = order, colors = names.mapValues { it.value.color }, progress = progress.value,
                        labels = (0 until 24).map { if (it % 6 == 0) "%02d".format(it) else null },
                        highlight = null, onTap = null,
                        description = "Average tracked time per hour of day"
                    )
                }
            }
            item {
                Card(title = "Where it flowed", subtitle = if (now < to) "Compared with the same point last $unit" else "Compared with the previous $unit") {
                    val max = current.values.max()
                    order.forEach { id ->
                        RankRow(names.getValue(id), current.getValue(id), max, total, before[id] ?: 0, progress.value)
                    }
                }
            }
        }
    }
}

private fun rangeTitle(period: Period, range: DayRange, today: LocalDate): String = when (period) {
    Period.Week -> when {
        today in range.days -> "This week"
        today.minusWeeks(1) in range.days -> "Last week"
        else -> "Week of ${range.start.format(shortDay)}"
    }
    Period.Month -> if (today in range.days) "This month" else range.start.format(monthYear)
}

private fun rangeDates(period: Period, range: DayRange): String = when (period) {
    Period.Week -> "${range.start.format(shortDay)} – ${range.endExclusive.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
    Period.Month -> range.start.format(monthYear)
}

@Composable
private fun Card(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String, detail: String? = null, compact: Boolean = false) = Stat(label, androidx.compose.ui.text.AnnotatedString(value), detail, compact)

@Composable
private fun Stat(label: String, value: androidx.compose.ui.text.AnnotatedString, detail: String? = null, compact: Boolean = false) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).merge(Numeric), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (compact && detail != null) {
                Spacer(Modifier.width(6.dp))
                Text(detail, style = MaterialTheme.typography.labelMedium.merge(Numeric), color = MaterialTheme.colorScheme.primary)
            }
        }
        if (!compact && detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ring of activity shares with hairline gaps; [progress] sweeps it in from twelve o'clock. */
@Composable
private fun Donut(parts: List<Pair<Color, Long>>, progress: Float, modifier: Modifier, center: @Composable ColumnScope.() -> Unit) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val total = parts.sumOf { it.second }.coerceAtLeast(1).toFloat()
    Box(modifier.semantics(mergeDescendants = true) {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            val gap = if (parts.size > 1) 2.2f else 0f
            var start = -90f
            parts.forEach { (color, ms) ->
                val sweep = 360f * ms / total * progress
                if (sweep > gap) drawArc(color, start + gap / 2, sweep - gap, false, topLeft, arc, style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = center)
    }
}

/** Picks a round axis step so the chart shows at most four gridlines. */
private fun axisStep(max: Long): Long {
    val minute = 60_000L
    return listOf(5, 10, 15, 30, 60, 120, 180, 240, 360, 480, 720).map { it * minute }.firstOrNull { max / it < 4 } ?: (1440 * minute)
}

private fun axisLabel(ms: Long): String = if (ms % 3_600_000L == 0L) "${ms / 3_600_000L}h" else "${ms / 60_000L}m"

@Composable
private fun StackedColumns(
    values: List<Map<String, Long>>, order: List<String>, colors: Map<String, Color>, progress: Float,
    labels: List<String?>, highlight: Int?, onTap: ((Int) -> Unit)?, description: String
) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted, letterSpacing = 0.sp)
    val max = values.maxOfOrNull { it.values.sum() }?.coerceAtLeast(1) ?: 1
    val step = axisStep(max)
    val top = ((max + step - 1) / step) * step
    Canvas(
        Modifier.fillMaxWidth().height(184.dp).semantics { contentDescription = description }
            .then(if (onTap != null) Modifier.pointerInput(values.size) {
                detectTapGestures { offset ->
                    val axis = 34.dp.toPx()
                    val slot = (size.width - axis) / values.size
                    val index = ((offset.x - axis) / slot).toInt()
                    if (offset.x >= axis && index in values.indices) onTap(index)
                }
            } else Modifier)
    ) {
        val axis = 34.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val chartTop = 8.dp.toPx()
        val height = bottom - chartTop
        val slot = (size.width - axis) / values.size
        val barWidth = slot * if (values.size <= 7) 0.52f else 0.66f
        // Gridlines with hour or minute labels.
        var line = 0L
        while (line <= top) {
            val y = bottom - height * line / top
            drawLine(grid, Offset(axis, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                pathEffect = if (line == 0L) null else PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            val text = measurer.measure(axisLabel(line), labelStyle)
            drawText(text, topLeft = Offset(0f, y - text.size.height / 2))
            line += step
        }
        values.forEachIndexed { index, bucket ->
            val x = axis + slot * index + (slot - barWidth) / 2
            val sum = bucket.values.sum()
            if (sum == 0L) {
                drawCircle(empty, radius = 2.dp.toPx(), center = Offset(x + barWidth / 2, bottom - 4.dp.toPx()))
            } else {
                val barHeight = height * sum / top * progress
                val radius = minOf(barWidth / 2, 6.dp.toPx())
                val bar = Path().apply {
                    addRoundRect(RoundRect(Rect(x, bottom - barHeight, x + barWidth, bottom), topLeft = CornerRadius(radius), topRight = CornerRadius(radius)))
                }
                clipPath(bar) {
                    var y = bottom
                    order.forEach { id ->
                        val ms = bucket[id] ?: return@forEach
                        val h = barHeight * ms / sum
                        drawRect(colors[id] ?: muted, Offset(x, y - h), Size(barWidth, h + 0.5f))
                        y -= h
                    }
                }
            }
            labels.getOrNull(index)?.let { label ->
                val style = if (index == highlight) labelStyle.copy(color = accent, fontWeight = FontWeight.Bold) else labelStyle
                val text = measurer.measure(label, style)
                drawText(text, topLeft = Offset(axis + slot * index + (slot - text.size.width) / 2, bottom + 6.dp.toPx()))
            }
        }
    }
}

@Composable
private fun RankRow(named: Named, ms: Long, max: Long, total: Long, before: Long, progress: Float) {
    val delta = ms - before
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).semantics(mergeDescendants = true) {
        contentDescription = "${named.title}, ${duration(ms)}, ${ms * 100 / total} percent" +
            if (before > 0) ", ${if (delta >= 0) "up" else "down"} ${duration(kotlin.math.abs(delta))}" else ""
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(named.color)
            Spacer(Modifier.width(10.dp))
            Text(named.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (before > 0 && kotlin.math.abs(delta) >= 60_000) {
                Text((if (delta > 0) "▲ " else "▼ ") + duration(kotlin.math.abs(delta)), style = MaterialTheme.typography.labelMedium.merge(Numeric),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
            } else if (before == 0L) {
                Text("new", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
            }
            Text(compactDuration(ms), style = MaterialTheme.typography.titleSmall.merge(Numeric))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(ms.toFloat() / max * progress).fillMaxHeight().clip(CircleShape).background(named.color))
        }
    }
}
