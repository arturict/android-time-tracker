package com.simonmettler.timetracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simonmettler.timetracker.data.TrackerActivity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonsScreen(
    activities: List<TrackerActivity>, selectedId: String?, onSelect: (String?) -> Unit,
    onSave: (String?, String, Int) -> Unit, onArchive: (String, Boolean) -> Unit, onMove: (String, Int) -> Unit
) {
    val selected = activities.find { it.id == selectedId }
    var title by rememberSaveable(selected?.id) { mutableStateOf(selected?.title ?: "") }
    var hex by rememberSaveable(selected?.id) { mutableStateOf("%06X".format((selected?.colorArgb ?: palette.first()) and 0xFFFFFF)) }
    val color = hex.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLong(16)?.or(0xFF000000)?.toInt()
    val valid = title.trim().isNotEmpty() && title.trim().length <= 60 && color != null
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
            Text(if (selected == null) "NEW BUTTON" else "EDITING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(if (selected == null) "Create a tracker button" else "Edit ${selected.title}", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Preview(title, color)
                OutlinedTextField(title, { title = it }, label = { Text("Button title") }, supportingText = { Text("${title.length}/60 characters") },
                    isError = title.length > 60, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done))
                Text("Color", style = MaterialTheme.typography.titleMedium)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 6) {
                    palette.forEach { option -> Swatch(option, color == option) { hex = "%06X".format(option and 0xFFFFFF) } }
                }
                OutlinedTextField(hex, { hex = it.removePrefix("#").take(7) }, label = { Text("Custom color (hex RGB)") }, isError = color == null, singleLine = true,
                    prefix = { Text("#") }, shape = MaterialTheme.shapes.small,
                    trailingIcon = { ColorDot(color?.let(::Color) ?: Color.Transparent, Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), 22.dp) },
                    modifier = Modifier.fillMaxWidth())
                Button(onClick = { color?.let { onSave(selected?.id, title, it) } }, enabled = valid, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save button")
                }
                if (selected != null) TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel editing") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Manage buttons", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text("Archived buttons keep their calendar history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { onSelect(null) }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New button") }
        }
        Spacer(Modifier.height(8.dp))
        val visible = activities.filterNot { it.isArchived }
        activities.sortedBy { it.isArchived }.forEach { activity ->
            ManageRow(activity, activity.id == selectedId, activity == visible.firstOrNull(), activity == visible.lastOrNull(), onSelect, onArchive, onMove)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Preview(title: String, color: Int?) {
    val bg by animateColorAsState(color?.let(::Color) ?: MaterialTheme.colorScheme.surfaceVariant, label = "preview")
    val fg = color?.let(::contrast) ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(25.dp)).background(bg)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent))).padding(16.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(fg.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(title.trim().firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.labelLarge, color = fg)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(title.ifBlank { "Button preview" }, style = MaterialTheme.typography.titleLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Preview", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun Swatch(option: Int, selected: Boolean, onClick: () -> Unit) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, label = "ring")
    Box(
        Modifier.size(46.dp).clip(CircleShape).border(ring, MaterialTheme.colorScheme.onSurface, CircleShape).padding(if (selected) 5.dp else 0.dp)
            .clip(CircleShape).background(Color(option)).clickable(onClick = onClick)
            .semantics { contentDescription = "Color #%06X".format(option and 0xFFFFFF) },
        contentAlignment = Alignment.Center
    ) { if (selected) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = contrast(option)) }
}

@Composable
private fun ManageRow(
    activity: TrackerActivity, editing: Boolean, first: Boolean, last: Boolean,
    onSelect: (String?) -> Unit, onArchive: (String, Boolean) -> Unit, onMove: (String, Int) -> Unit
) {
    Surface(
        onClick = { onSelect(activity.id) }, shape = MaterialTheme.shapes.medium,
        color = if (editing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).alpha(if (activity.isArchived) 0.45f else 1f).clip(RoundedCornerShape(12.dp)).background(Color(activity.colorArgb)), contentAlignment = Alignment.Center) {
                Text(activity.title.first().uppercase(), style = MaterialTheme.typography.labelLarge, color = contrast(activity.colorArgb))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(activity.title + if (activity.isArchived) " (archived)" else "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (activity.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("  ·  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Text(if (activity.isArchived) "Restore" else "Archive", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(6.dp)).clickable { onArchive(activity.id, !activity.isArchived) }.padding(horizontal = 4.dp, vertical = 6.dp))
                }
            }
            if (!activity.isArchived) {
                IconButton(onClick = { onMove(activity.id, -1) }, enabled = !first) { Icon(Icons.Filled.KeyboardArrowUp, "Move ${activity.title} up") }
                IconButton(onClick = { onMove(activity.id, 1) }, enabled = !last) { Icon(Icons.Filled.KeyboardArrowDown, "Move ${activity.title} down") }
            }
        }
    }
}
