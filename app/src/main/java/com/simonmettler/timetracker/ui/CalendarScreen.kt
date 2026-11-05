package com.simonmettler.timetracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simonmettler.timetracker.data.CalendarEntry
import com.simonmettler.timetracker.model.TimeRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val weekdayLabel = DateTimeFormatter.ofPattern("EEEE")
private val fullDate = DateTimeFormatter.ofPattern("d MMMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(day: LocalDate, zone: ZoneId, entries: List<CalendarEntry>, onDay: (LocalDate) -> Unit, onZone: (ZoneId) -> Unit, onDevice: () -> Unit, onEdit: (CalendarEntry) -> Unit) {
    val now = rememberNow(60_000)
    var zoneDialog by remember { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }
    val today = LocalDate.now(zone)
    val (from, to) = TimeRules.dayWindow(day, zone)
    val visible = entries.filter {
        val item = it.interval
        TimeRules.clippedDuration(item.startAtMs, item.endAtMs, from, to, now) > 0 ||
            (item.endAtMs == null && item.startAtMs < to && now >= item.startAtMs && now >= from)
    }
    val parts = totals(visible, from, to, now)
    val total = parts.sumOf { it.ms }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { dateDialog = true }.padding(vertical = 2.dp)) {
                    Text((if (day == today) "Today · " else "") + day.format(weekdayLabel).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(day.format(fullDate), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                }
                FilledTonalIconButton(onClick = { onDay(day.minusDays(1)) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous day") }
                FilledTonalIconButton(onClick = { onDay(day.plusDays(1)) }) { Icon(Icons.Filled.KeyboardArrowRight, "Next day") }
            }
        }
        item { WeekStrip(day, today, onDay) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onDay(today) }, enabled = day != today, label = { Text("Today") },
                    leadingIcon = { Icon(Icons.Filled.DateRange, null, Modifier.size(AssistChipDefaults.IconSize)) })
                AssistChip(onClick = { zoneDialog = true }, label = { Text("Time zone: ${zone.id}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.Place, null, Modifier.size(AssistChipDefaults.IconSize)) })
            }
        }
        item { SummaryCard(parts, total, visible, from, to, now) }
        if (visible.isEmpty()) item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A blank page", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("No entries for this day. Start a tracker from Home or your widget.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else item {
            Text("Tap an entry to edit its start and end.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
        }
        items(visible, key = { it.interval.id }) { entry -> TimelineRow(entry, from, to, now, zone) { onEdit(entry) } }
    }
    if (zoneDialog) ZoneDialog(zone, onDismiss = { zoneDialog = false }, onSave = { onZone(it); zoneDialog = false }, onDevice = { onDevice(); zoneDialog = false })
    if (dateDialog) DatePickDialog(day, onDismiss = { dateDialog = false }) { onDay(it); dateDialog = false }
}

@Composable
private fun WeekStrip(day: LocalDate, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0L until 7L).map { monday.plusDays(it) }.forEach { date ->
            val isSelected = date == day
            val bg by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "day")
            val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(bg).clickable { onDay(date) }
                    .semantics { selected = isSelected }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                Spacer(Modifier.height(2.dp))
                Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium.merge(Numeric), color = fg)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (date == today) (if (isSelected) fg else MaterialTheme.colorScheme.primary) else Color.Transparent))
            }
        }
    }
}

@Composable
private fun SummaryCard(parts: List<ActivityTotal>, total: Long, entries: List<CalendarEntry>, from: Long, to: Long, now: Long) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).animateContentSize()) {
            Text("TRACKED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (total == 0L) Text("No tracked time", style = MaterialTheme.typography.headlineSmall)
            else Text(compactDuration(total), style = MaterialTheme.typography.headlineLarge.merge(Numeric),
                modifier = Modifier.semantics { contentDescription = "${duration(total)} tracked" })
            if (parts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                DayRibbon(entries, from, to, now)
                Spacer(Modifier.height(16.dp))
                StackedBar(parts, height = 10.dp)
                Spacer(Modifier.height(12.dp))
                parts.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth()) {
                        pair.forEach { part ->
                            val share = (part.ms * 100 / total.coerceAtLeast(1)).toInt()
                            // Summary rows speak as one phrase and keep entry titles unique for lookup by name.
                            Row(Modifier.weight(1f).padding(vertical = 4.dp, horizontal = 2.dp).clearAndSetSemantics { contentDescription = "${part.title}, ${duration(part.ms)}, $share percent" },
                                verticalAlignment = Alignment.CenterVertically) {
                                ColorDot(Color(part.colorArgb))
                                Spacer(Modifier.width(8.dp))
                                Text(part.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(6.dp))
                                Text(compactDuration(part.ms), style = MaterialTheme.typography.bodyMedium.merge(Numeric), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The day as a 24-hour strip: each entry sits where it happened, with a marker for now. */
@Composable
private fun DayRibbon(entries: List<CalendarEntry>, from: Long, to: Long, now: Long) {
    val measurer = rememberTextMeasurer()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.outline
    val marker = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val span = (to - from).toFloat()
    Canvas(Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = "Day timeline from midnight to midnight" }) {
        val barTop = 0f
        val barHeight = 26.dp.toPx()
        val radius = CornerRadius(8.dp.toPx())
        clipPath(Path().apply { addRoundRect(RoundRect(Rect(0f, barTop, size.width, barTop + barHeight), radius)) }) {
            drawRect(track, Offset(0f, barTop), Size(size.width, barHeight))
            entries.forEach { entry ->
                val start = maxOf(entry.interval.startAtMs, from)
                val end = minOf(entry.interval.endAtMs ?: now, to)
                if (end > start) {
                    val x0 = size.width * (start - from) / span
                    val x1 = size.width * (end - from) / span
                    drawRect(Color(entry.colorArgb), Offset(x0, barTop), Size(maxOf(x1 - x0, 1.5f), barHeight))
                }
            }
        }
        listOf(0, 6, 12, 18, 24).forEach { hour ->
            val x = size.width * hour / 24f
            if (hour in 1..23) drawLine(tick.copy(alpha = 0.5f), Offset(x, barTop + barHeight), Offset(x, barTop + barHeight + 4.dp.toPx()), 1.dp.toPx())
            val text = measurer.measure("%02d".format(hour), labelStyle)
            drawText(text, topLeft = Offset((x - text.size.width / 2).coerceIn(0f, size.width - text.size.width), barTop + barHeight + 5.dp.toPx()))
        }
        if (now in from until to) {
            val x = size.width * (now - from) / span
            drawLine(marker, Offset(x, barTop - 2.dp.toPx()), Offset(x, barTop + barHeight + 2.dp.toPx()), 2.dp.toPx())
        }
    }
}

@Composable
private fun TimelineRow(entry: CalendarEntry, from: Long, to: Long, now: Long, zone: ZoneId, onClick: () -> Unit) {
    val item = entry.interval
    val color = Color(entry.colorArgb)
    val start = Instant.ofEpochMilli(maxOf(item.startAtMs, from)).atZone(zone).format(clockLabel)
    val end = Instant.ofEpochMilli(minOf(item.endAtMs ?: now, to)).atZone(zone).format(clockLabel)
    val elapsed = TimeRules.clippedDuration(item.startAtMs, item.endAtMs, from, to, now)
    val open = item.endAtMs == null
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(48.dp).padding(top = 14.dp)) {
            Text(start, style = MaterialTheme.typography.labelLarge.merge(Numeric))
            Text(if (open) "now" else end, style = MaterialTheme.typography.labelMedium.merge(Numeric), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = 22.dp).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.padding(top = 16.dp).size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp).clip(CircleShape).background(color))
        }
        Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(color))
                Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (open) Row(Modifier.clip(CircleShape).background(color).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(contrast(entry.colorArgb), Modifier.size(6.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Active", style = MaterialTheme.typography.labelSmall, color = contrast(entry.colorArgb))
                        }
                    }
                    Text("$start – $end  ·  ${duration(elapsed)}", style = MaterialTheme.typography.bodyMedium.merge(Numeric), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.startAtMs < from) Text("Continues from a previous day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if ((item.endAtMs ?: now) > to) Text("Continues into the next day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state) }
}

@Composable
private fun ZoneDialog(zone: ZoneId, onDismiss: () -> Unit, onSave: (ZoneId) -> Unit, onDevice: () -> Unit) {
    var value by remember { mutableStateOf(zone.id) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Calendar time zone") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value, { value = it }, label = { Text("IANA zone, e.g. Europe/Zurich") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onDevice) { Text("Follow device time zone") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        try { onSave(ZoneId.of(value.trim())) } catch (_: Exception) { error = "Enter a valid time zone." }
    }) { Text("Apply") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
