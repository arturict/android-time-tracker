package com.simonmettler.timetracker.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.simonmettler.timetracker.data.CalendarEntry
import com.simonmettler.timetracker.model.TimeRules
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Curated presets: saturated enough to tell apart at a glance, each readable with white or black text. */
val palette = listOf(
    0xFF0B7366, 0xFF2F6FDB, 0xFF6C4FD8, 0xFFB03C9A, 0xFFC93C37, 0xFFE8833A,
    0xFFF2C14E, 0xFF5BA444, 0xFF1E9BB8, 0xFF8A6A50, 0xFF5D6B78, 0xFF26292D
).map { it.toInt() }

val clockLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun contrast(color: Int) = Color(TimeRules.foregroundColor(color))

fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 1) "< 1 min" else if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

/** "7h 02m" with de-emphasized unit letters, for large figures. */
fun compactDuration(ms: Long) = buildAnnotatedString {
    val minutes = ms / 60_000
    val unit = SpanStyle(fontSize = 0.62.em)
    if (minutes >= 60) {
        append("${minutes / 60}"); withStyle(unit) { append("h ") }
        append("%02d".format(minutes % 60)); withStyle(unit) { append("m") }
    } else {
        append("$minutes"); withStyle(unit) { append("m") }
    }
}

fun stopwatch(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}

/** Current time that ticks while the screen is visible. */
@Composable
fun rememberNow(periodMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, periodMs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { now = System.currentTimeMillis(); delay(periodMs - now % periodMs) }
        }
    }
    return maxOf(now, System.currentTimeMillis())
}

data class ActivityTotal(val activityId: String, val title: String, val colorArgb: Int, val ms: Long)

fun totals(entries: List<CalendarEntry>, from: Long, to: Long, now: Long): List<ActivityTotal> =
    entries.groupBy { it.interval.activityId }.map { (id, items) ->
        ActivityTotal(id, items.last().title, items.last().colorArgb,
            items.sumOf { TimeRules.clippedDuration(it.interval.startAtMs, it.interval.endAtMs, from, to, now) })
    }.filter { it.ms > 0 }.sortedByDescending { it.ms }

/** Proportional bar of time per activity. */
@Composable
fun StackedBar(parts: List<ActivityTotal>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val total = parts.sumOf { it.ms }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Spacer(Modifier.width(2.dp))
            Box(Modifier.fillMaxHeight().weight(part.ms.toFloat() / total).background(Color(part.colorArgb)))
        }
    }
}

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Softly breathing dot that marks something as live. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(0.7f, 1.15f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")
    Box(modifier.size(10.dp).scale(scale).clip(CircleShape).background(color))
}
