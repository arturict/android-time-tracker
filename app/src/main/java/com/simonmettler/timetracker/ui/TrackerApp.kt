package com.simonmettler.timetracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simonmettler.timetracker.data.CalendarEntry
import com.simonmettler.timetracker.data.TimeInterval
import com.simonmettler.timetracker.data.TrackerActivity
import com.simonmettler.timetracker.model.Boundary
import com.simonmettler.timetracker.model.TimeRules
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val palette = listOf(0xFF19776A, 0xFF7254B8, 0xFFB6553C, 0xFF2466A0, 0xFF9A5413, 0xFFB43770, 0xFF506873, 0xFF547A20).map { it.toInt() }
private val clockLabel = DateTimeFormatter.ofPattern("HH:mm")
private fun contrast(color: Int) = Color(TimeRules.foregroundColor(color))
private fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 1) "< 1 min" else if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerApp(vm: TrackerViewModel = viewModel()) {
    val activities by vm.activities.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val zone by vm.zone.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("home") }
    var editActivityId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CalendarEntry?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshZone() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(vm) { vm.feedback.collect { snackbar.showSnackbar(it) } }
    BackHandler(screen != "home") { screen = "home" }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF19776A), background = Color(0xFFF3F6FA), surface = Color.White)) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(when (screen) { "calendar" -> "Calendar"; "editor" -> "Your buttons"; else -> "Time Tracker" }) },
                    navigationIcon = {
                        TextButton(onClick = { screen = if (screen == "home") "calendar" else "home" }) {
                            Text(if (screen == "home") "Calendar" else "Home")
                        }
                    },
                    actions = {
                        if (screen == "home") TextButton(onClick = { editActivityId = null; screen = "editor" }) { Text("Create button") }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when (screen) {
                    "home" -> Dashboard(activities.filterNot { it.isArchived }, active?.activityId, vm::start) {
                        editActivityId = null; screen = "editor"
                    }
                    "calendar" -> {
                        val entries by vm.calendar.collectAsStateWithLifecycle()
                        CalendarScreen(day, zone, entries, vm::setDay, vm::setZone, vm::followDevice) { editing = it }
                    }
                    "editor" -> ActivityEditor(activities, editActivityId,
                        onSelect = { editActivityId = it },
                        onSave = { id, title, color -> vm.save(id, title, color) { screen = "home" } },
                        onArchive = vm::archive, onMove = vm::move)
                }
            }
        }
        editing?.let { entry ->
            IntervalEditor(entry, onDismiss = { editing = null }) { start, end, shared, onError ->
                vm.edit(entry.interval, start, end, shared, onError) { editing = null }
            }
        }
    }
}

@Composable
private fun Dashboard(activities: List<TrackerActivity>, activeId: String?, onStart: (String) -> Unit, onCreate: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Where is your time going?", style = MaterialTheme.typography.headlineSmall)
                Text(activities.find { it.id == activeId }?.let { "Tracking ${it.title}" } ?: "Choose an activity to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (activities.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Make your first tracker", style = MaterialTheme.typography.titleLarge)
                    Text("Give it a name and a color. One tap starts tracking; your next tap starts the next entry.")
                    Button(onClick = onCreate) { Text("Create your first button") }
                }
            }
        }
        items(activities, key = { it.id }) { activity ->
            val isActive = activeId == activity.id
            Button(
                onClick = { onStart(activity.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).semantics {
                    contentDescription = "${activity.title}${if (isActive) ", active" else ""}. Start new interval."
                },
                shape = RoundedCornerShape(22.dp),
                border = if (isActive) BorderStroke(3.dp, contrast(activity.colorArgb)) else null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(activity.colorArgb), contentColor = contrast(activity.colorArgb)),
                contentPadding = PaddingValues(24.dp)
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(activity.title, style = MaterialTheme.typography.headlineSmall)
                    if (isActive) Text("✓ Active · tap again for a new entry", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (activities.isNotEmpty()) item {
            TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Edit, reorder or add buttons") }
        }
    }
}

@Composable
private fun ActivityEditor(
    activities: List<TrackerActivity>, selectedId: String?, onSelect: (String?) -> Unit,
    onSave: (String?, String, Int) -> Unit, onArchive: (String, Boolean) -> Unit, onMove: (String, Int) -> Unit
) {
    val selected = activities.find { it.id == selectedId }
    var title by rememberSaveable(selected?.id) { mutableStateOf(selected?.title ?: "") }
    var hex by rememberSaveable(selected?.id) { mutableStateOf("%06X".format((selected?.colorArgb ?: palette.first()) and 0xFFFFFF)) }
    val color = hex.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLong(16)?.or(0xFF000000)?.toInt()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (selected == null) "Create a tracker button" else "Edit ${selected.title}", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { title = it }, label = { Text("Button title") }, supportingText = { Text("${title.length}/60 characters") }, isError = title.length > 60, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Choose a color", style = MaterialTheme.typography.titleMedium)
        palette.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    Button(
                        onClick = { hex = "%06X".format(option and 0xFFFFFF) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { contentDescription = "Color #%06X".format(option and 0xFFFFFF) },
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(option), contentColor = contrast(option))
                    ) { Text(if (color == option) "✓" else "") }
                }
            }
        }
        OutlinedTextField(hex, { hex = it }, label = { Text("Custom color (hex RGB)") }, isError = color == null, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (color != null) Surface(color = Color(color), contentColor = contrast(color), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Text(title.ifBlank { "Button preview" }, Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
        }
        Button(onClick = { color?.let { onSave(selected?.id, title, it) } }, enabled = title.trim().isNotEmpty() && title.trim().length <= 60 && color != null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save button") }
        HorizontalDivider()
        Text("Manage buttons", style = MaterialTheme.typography.titleLarge)
        Text("Archived buttons keep their calendar history.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onSelect(null) }) { Text("New button") }
        activities.forEach { activity ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(activity.title + if (activity.isArchived) " (archived)" else "", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onSelect(activity.id) }) { Text("Edit") }
                        TextButton(onClick = { onArchive(activity.id, !activity.isArchived) }) { Text(if (activity.isArchived) "Restore" else "Archive") }
                    }
                    if (!activity.isArchived) Row {
                        TextButton(onClick = { onMove(activity.id, -1) }, modifier = Modifier.semantics { contentDescription = "Move ${activity.title} up" }) { Text("Move up") }
                        TextButton(onClick = { onMove(activity.id, 1) }, modifier = Modifier.semantics { contentDescription = "Move ${activity.title} down" }) { Text("Move down") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CalendarScreen(day: LocalDate, zone: ZoneId, entries: List<CalendarEntry>, onDay: (LocalDate) -> Unit, onZone: (ZoneId) -> Unit, onDevice: () -> Unit, onEdit: (CalendarEntry) -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { now = System.currentTimeMillis(); delay(60_000) }
        }
    }
    var zoneDialog by remember { mutableStateOf(false) }
    val renderNow = maxOf(now, System.currentTimeMillis())
    val (from, to) = TimeRules.dayWindow(day, zone)
    val visible = entries.filter {
        val item = it.interval
        TimeRules.clippedDuration(item.startAtMs, item.endAtMs, from, to, renderNow) > 0 ||
            (item.endAtMs == null && item.startAtMs < to && renderNow >= item.startAtMs && renderNow >= from)
    }
    val total = visible.sumOf { TimeRules.clippedDuration(it.interval.startAtMs, it.interval.endAtMs, from, to, renderNow) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onDay(day.minusDays(1)) }) { Text("Previous") }
                    TextButton(onClick = {
                        DatePickerDialog(context, { _, y, m, d -> onDay(LocalDate.of(y, m + 1, d)) }, day.year, day.monthValue - 1, day.dayOfMonth).show()
                    }) { Text(day.toString()) }
                    TextButton(onClick = { onDay(day.plusDays(1)) }) { Text("Next") }
                }
                TextButton(onClick = { onDay(LocalDate.now(zone)) }) { Text("Today") }
                TextButton(onClick = { zoneDialog = true }) { Text("Time zone: ${zone.id}") }
                Text(if (total == 0L) "No tracked time" else "${duration(total)} tracked", style = MaterialTheme.typography.headlineSmall)
                Text("Tap an entry to edit its start and end.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (visible.isEmpty()) item {
            OutlinedCard(Modifier.fillMaxWidth()) { Text("No entries for this day. Start a tracker from Home or your widget.", Modifier.padding(24.dp)) }
        }
        items(visible, key = { it.interval.id }) { entry ->
            val item = entry.interval
            val start = Instant.ofEpochMilli(maxOf(item.startAtMs, from)).atZone(zone).format(clockLabel)
            val end = Instant.ofEpochMilli(minOf(item.endAtMs ?: renderNow, to)).atZone(zone).format(clockLabel)
            val elapsed = TimeRules.clippedDuration(item.startAtMs, item.endAtMs, from, to, renderNow)
            Card(onClick = { onEdit(entry) }, colors = CardDefaults.cardColors(containerColor = Color(entry.colorArgb), contentColor = contrast(entry.colorArgb)), modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$start – $end${if (item.endAtMs == null) " · Active" else ""}", style = MaterialTheme.typography.labelLarge)
                    Text(entry.title, style = MaterialTheme.typography.titleLarge)
                    Text(duration(elapsed))
                    if (item.startAtMs < from) Text("Continues from a previous day", style = MaterialTheme.typography.labelMedium)
                    if ((item.endAtMs ?: renderNow) > to) Text("Continues into the next day", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (zoneDialog) ZoneDialog(zone, onDismiss = { zoneDialog = false }, onSave = { onZone(it); zoneDialog = false }, onDevice = { onDevice(); zoneDialog = false })
}

@Composable
private fun ZoneDialog(zone: ZoneId, onDismiss: () -> Unit, onSave: (ZoneId) -> Unit, onDevice: () -> Unit) {
    var value by remember { mutableStateOf(zone.id) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Calendar time zone") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value, { value = it }, label = { Text("IANA zone, e.g. Europe/Zurich") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onDevice) { Text("Follow device time zone") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        try { onSave(ZoneId.of(value.trim())) } catch (_: Exception) { error = "Enter a valid time zone." }
    }) { Text("Apply") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun IntervalEditor(entry: CalendarEntry, onDismiss: () -> Unit, onSave: (Boundary, Boundary?, Boolean, (String) -> Unit) -> Unit) {
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
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Edit ${entry.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Each endpoint keeps its own time zone. Dates and times include milliseconds to preserve exact boundaries.")
                EndpointEditor("Start", startText, startZone, startOffset, { startText = it; startOffset = "" }, { startZone = it; startOffset = "" }, { startOffset = it })
                if (originalEnd == null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(keepOpen, { keepOpen = it }); Text("Keep tracking (no end)", Modifier.weight(1f))
                }
                if (!keepOpen) EndpointEditor("End", endText, endZone, endOffset, { endText = it; endOffset = "" }, { endZone = it; endOffset = "" }, { endOffset = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(shared, { shared = it }); Text("Move shared boundary", Modifier.weight(1f))
                }
                Text("Also adjusts directly adjacent entries. Overlaps are rejected.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            try {
                fun resolve(text: String, zone: String, offset: String, original: Boundary?): Boundary =
                    if (original != null && text == TimeRules.originalLocal(original) && zone == original.zoneId && offset == ZoneOffset.ofTotalSeconds(original.offsetSeconds).id) original
                    else TimeRules.resolve(text, zone, offset)
                val start = resolve(startText, startZone, startOffset, originalStart)
                val end = if (keepOpen) null else resolve(endText, endZone, endOffset, originalEnd)
                require(end == null || end.atMs >= start.atMs) { "End must be at or after start." }
                onSave(start, end, shared) { error = it }
            } catch (problem: IllegalArgumentException) { error = problem.message }
        }) { Text("Save entry") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EndpointEditor(label: String, text: String, zone: String, offset: String, onText: (String) -> Unit, onZone: (String) -> Unit, onOffset: (String) -> Unit) {
    val context = LocalContext.current
    Text(label, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(text, onText, label = { Text("$label date and time") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row {
        TextButton(onClick = {
            val parsed = runCatching { LocalDateTime.parse(text, TimeRules.editorFormat) }.getOrDefault(LocalDateTime.now())
            DatePickerDialog(context, { _, y, m, d -> onText(parsed.withYear(y).withMonth(m + 1).withDayOfMonth(1).withDayOfMonth(d).format(TimeRules.editorFormat)) }, parsed.year, parsed.monthValue - 1, parsed.dayOfMonth).show()
        }) { Text("Pick date") }
        TextButton(onClick = {
            val parsed = runCatching { LocalDateTime.parse(text, TimeRules.editorFormat) }.getOrDefault(LocalDateTime.now())
            TimePickerDialog(context, { _, h, m -> onText(parsed.withHour(h).withMinute(m).format(TimeRules.editorFormat)) }, parsed.hour, parsed.minute, true).show()
        }) { Text("Pick time") }
    }
    OutlinedTextField(zone, onZone, label = { Text("$label time zone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val offsets = remember(text, zone) {
        runCatching { ZoneId.of(zone).rules.getValidOffsets(LocalDateTime.parse(text, TimeRules.editorFormat)) }.getOrDefault(emptyList())
    }
    if (offsets.size > 1) {
        Text("This time occurs twice. Select its UTC offset:")
        Row { offsets.forEach { option -> TextButton(onClick = { onOffset(option.id) }) { Text("${if (offset == option.id) "✓ " else ""}${option.id}") } } }
    } else if (offsets.isEmpty()) Text("Check the date, time zone, or daylight-saving gap.", color = MaterialTheme.colorScheme.error)
    else Text("UTC offset ${offsets.single().id}", style = MaterialTheme.typography.bodySmall)
}
