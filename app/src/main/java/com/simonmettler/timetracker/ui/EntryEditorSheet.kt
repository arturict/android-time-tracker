package com.simonmettler.timetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simonmettler.timetracker.data.CalendarEntry
import com.simonmettler.timetracker.model.Boundary
import com.simonmettler.timetracker.model.TimeRules
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val chipDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorSheet(entry: CalendarEntry, onDismiss: () -> Unit, onSave: (Boundary, Boundary?, Boolean, (String) -> Unit) -> Unit) {
    val item = entry.interval
    val originalStart = Boundary(item.startAtMs, item.startZoneId, item.startOffsetSeconds)
    val originalEnd = item.endAtMs?.let { Boundary(it, item.endZoneId!!, item.endOffsetSeconds!!) }
    var startText by remember { mutableStateOf(TimeRules.originalLocal(originalStart)) }
    var startZone by remember { mutableStateOf(item.startZoneId) }
    var startOffset by remember { mutableStateOf(ZoneOffset.ofTotalSeconds(item.startOffsetSeconds).id) }
    val proposedEnd = remember { originalEnd ?: Boundary.capture(Instant.now(), ZoneId.systemDefault()) }
    var endText by remember { mutableStateOf(TimeRules.originalLocal(proposedEnd)) }
    var endZone by remember { mutableStateOf(proposedEnd.zoneId) }
    var endOffset by remember { mutableStateOf(ZoneOffset.ofTotalSeconds(proposedEnd.offsetSeconds).id) }
    var keepOpen by remember { mutableStateOf(originalEnd == null) }
    var shared by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun save() {
        try {
            fun resolve(text: String, zone: String, offset: String, original: Boundary?): Boundary =
                if (original != null && text == TimeRules.originalLocal(original) && zone == original.zoneId && offset == ZoneOffset.ofTotalSeconds(original.offsetSeconds).id) original
                else TimeRules.resolve(text, zone, offset)
            val start = resolve(startText, startZone, startOffset, originalStart)
            val end = if (keepOpen) null else resolve(endText, endZone, endOffset, originalEnd)
            require(end == null || end.atMs >= start.atMs) { "End must be at or after start." }
            onSave(start, end, shared) { error = it }
        } catch (problem: IllegalArgumentException) { error = problem.message }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        // Actions stay pinned below the scrolling fields so Save is always reachable.
        Column(Modifier.fillMaxWidth().imePadding()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(entry.colorArgb)), contentAlignment = Alignment.Center) {
                    Text(entry.title.first().uppercase(), style = MaterialTheme.typography.titleMedium, color = contrast(entry.colorArgb))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Edit ${entry.title}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Each endpoint keeps its own time zone, to the millisecond.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            EndpointEditor("Start", startText, startZone, startOffset, { startText = it; startOffset = "" }, { startZone = it; startOffset = "" }, { startOffset = it })
            if (originalEnd == null) ToggleRow("Keep tracking (no end)", "Leave the entry running.", keepOpen) { keepOpen = it }
            if (!keepOpen) EndpointEditor("End", endText, endZone, endOffset, { endText = it; endOffset = "" }, { endZone = it; endOffset = "" }, { endOffset = it })
            ToggleRow("Move shared boundary", "Also adjusts directly adjacent entries. Overlaps are rejected.", shared) { shared = it }
            error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text("Cancel") }
            Button(onClick = ::save, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text("Save entry") }
        }
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(onClick = { onChange(!checked) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked, onCheckedChange = onChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndpointEditor(label: String, text: String, zone: String, offset: String, onText: (String) -> Unit, onZone: (String) -> Unit, onOffset: (String) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val parsed = runCatching { LocalDateTime.parse(text, TimeRules.editorFormat) }.getOrNull()
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickDate = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1.4f)) {
                    Icon(Icons.Filled.DateRange, "Pick date", Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(parsed?.format(chipDate) ?: "Pick date", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(onClick = { pickTime = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f).semantics { contentDescription = "Pick time" }) {
                    Text(parsed?.format(clockLabel) ?: "Pick time", style = LocalTextStyle.current.merge(Numeric))
                }
            }
            OutlinedTextField(text, onText, label = { Text("$label date and time") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.merge(Numeric), shape = MaterialTheme.shapes.small)
            OutlinedTextField(zone, onZone, label = { Text("$label time zone") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
            val offsets = remember(text, zone) {
                runCatching { ZoneId.of(zone).rules.getValidOffsets(LocalDateTime.parse(text, TimeRules.editorFormat)) }.getOrDefault(emptyList())
            }
            if (offsets.size > 1) {
                Text("This time occurs twice. Select its UTC offset:", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    offsets.forEach { option -> FilterChip(selected = offset == option.id, onClick = { onOffset(option.id) }, label = { Text(option.id) }) }
                }
            } else if (offsets.isEmpty()) Text("Check the date, time zone, or daylight-saving gap.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            else Text("UTC offset ${offsets.single().id.let { if (it == "Z") "±00:00" else it }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val base = parsed ?: LocalDateTime.now()
    if (pickDate) DatePickDialog(base.toLocalDate(), onDismiss = { pickDate = false }) {
        onText(base.with(it).format(TimeRules.editorFormat)); pickDate = false
    }
    if (pickTime) {
        val state = rememberTimePickerState(base.hour, base.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = { onText(base.withHour(state.hour).withMinute(state.minute).format(TimeRules.editorFormat)); pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) }
        )
    }
}
