package com.simonmettler.timetracker.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simonmettler.timetracker.data.CalendarEntry
import com.simonmettler.timetracker.data.TimeInterval
import com.simonmettler.timetracker.data.TrackerActivity
import com.simonmettler.timetracker.model.TimeRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateLabel = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
fun HomeScreen(
    activities: List<TrackerActivity>, active: TimeInterval?, today: LocalDate, zone: ZoneId,
    todayEntries: List<CalendarEntry>, onStart: (String) -> Unit, onCreate: () -> Unit, onOpenCalendar: () -> Unit
) {
    val now = rememberNow(if (active != null) 1_000 else 30_000)
    val (from, to) = TimeRules.dayWindow(today, zone)
    val dayTotals = totals(todayEntries, from, to, now)
    val perActivity = dayTotals.associate { it.activityId to it.ms }
    val activeActivity = activities.find { it.id == active?.activityId }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        full { Greeting(today, now, zone) }
        full { NowCard(activeActivity, active, now, zone) }
        if (activities.isEmpty()) full { WelcomeCard(onCreate) }
        else full {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Activities", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                Text("Tap to switch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(activities, key = { it.id }) { activity ->
            ActivityTile(activity, activity.id == active?.activityId, perActivity[activity.id] ?: 0) { onStart(activity.id) }
        }
        if (activities.isNotEmpty()) item(key = "new") { NewTile(onCreate) }
        if (dayTotals.isNotEmpty()) full { Box(Modifier.padding(top = 8.dp)) { TodayCard(dayTotals, onOpenCalendar) } }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Greeting(today: LocalDate, now: Long, zone: ZoneId) {
    val hour = Instant.ofEpochMilli(now).atZone(zone).hour
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Text(today.format(dateLabel).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(greeting, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun NowCard(activity: TrackerActivity?, active: TimeInterval?, now: Long, zone: ZoneId) {
    val base = activity?.let { Color(it.colorArgb) }
    val animated by animateColorAsState(base ?: MaterialTheme.colorScheme.surfaceContainer, label = "hero")
    val fg = if (activity != null) contrast(activity.colorArgb) else MaterialTheme.colorScheme.onSurface
    val shape = MaterialTheme.shapes.extraLarge
    val description = if (activity != null && active != null) "Tracking ${activity.title} for ${duration(now - active.startAtMs)}" else "Nothing is being tracked"
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (activity != null) Brush.linearGradient(listOf(lerp(animated, Color.White, 0.08f), lerp(animated, Color.Black, 0.22f)))
                else Brush.linearGradient(listOf(animated, animated)))
            .then(if (activity == null) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
            .drawBehind {
                // Decorative clock rings bleeding off the corner.
                val ring = fg.copy(alpha = if (activity != null) 0.10f else 0.05f)
                listOf(0.55f, 0.78f, 1.0f).forEach { f ->
                    drawCircle(ring, radius = size.height * f, center = Offset(size.width * 0.98f, size.height * 0.05f), style = Stroke(width = 1.5.dp.toPx()))
                }
            }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(24.dp)
    ) {
        AnimatedContent(activity?.id, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "now") { id ->
            if (id != null && activity != null && active != null) Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(fg)
                    Spacer(Modifier.width(8.dp))
                    Text("NOW TRACKING", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(14.dp))
                Text(activity.title, style = MaterialTheme.typography.headlineMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stopwatch(now - active.startAtMs), style = MaterialTheme.typography.displayMedium.merge(Numeric), color = fg)
                Spacer(Modifier.height(6.dp))
                Text("Since ${Instant.ofEpochMilli(active.startAtMs).atZone(zone).format(clockLabel)}  ·  tap another activity to switch",
                    style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.8f))
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Nothing running", style = MaterialTheme.typography.titleLarge)
                    Text("Choose an activity to begin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TodayCard(parts: List<ActivityTotal>, onOpenCalendar: () -> Unit) {
    val total = parts.sumOf { it.ms }
    Surface(
        onClick = onOpenCalendar, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("TRACKED TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(compactDuration(total), style = MaterialTheme.typography.headlineLarge.merge(Numeric))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Day view", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(14.dp))
            StackedBar(parts, height = 14.dp)
            Spacer(Modifier.height(14.dp))
            parts.take(4).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    pair.forEach { part ->
                        Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(Color(part.colorArgb))
                            Spacer(Modifier.width(8.dp))
                            Text(part.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
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

@Composable
private fun ActivityTile(activity: TrackerActivity, isActive: Boolean, todayMs: Long, onClick: () -> Unit) {
    val color = Color(activity.colorArgb)
    val fg = contrast(activity.colorArgb)
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    val ring by animateColorAsState(if (isActive) color else Color.Transparent, label = "ring")
    Box(Modifier.scale(scale).border(2.5.dp, ring, RoundedCornerShape(30.dp)).padding(5.dp)) {
        Card(
            onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() },
            interactionSource = interaction,
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(containerColor = color, contentColor = fg),
            modifier = Modifier.fillMaxWidth().height(128.dp).semantics {
                contentDescription = "${activity.title}${if (isActive) ", active" else ""}. Start new interval."
            }
        ) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(fg.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            if (isActive) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = fg)
                            else Text(activity.title.first().uppercase(), style = MaterialTheme.typography.labelLarge, color = fg)
                        }
                        Spacer(Modifier.weight(1f))
                        if (isActive) Row(
                            Modifier.clip(CircleShape).background(fg.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PulseDot(fg, Modifier.size(6.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("LIVE", style = MaterialTheme.typography.labelSmall, color = fg)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(activity.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (todayMs > 0) "${duration(todayMs)} today" else "Not yet today",
                        style = MaterialTheme.typography.labelMedium.merge(Numeric), color = fg.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun NewTile(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Box(Modifier.padding(5.dp)) {
        Column(
            Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(25.dp)).clickable(onClick = onClick)
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(outline, topLeft = Offset(stroke / 2, stroke / 2),
                        size = size.copy(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(25.dp.toPx()),
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text("New activity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WelcomeCard(onCreate: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                palette.take(5).forEach { Box(Modifier.size(width = 28.dp, height = 10.dp).clip(CircleShape).background(Color(it))) }
            }
            Text("Make your first tracker", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Give it a name and a color. One tap starts tracking; your next tap starts the next entry.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            Button(onClick = onCreate, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Create your first button")
            }
        }
    }
}
