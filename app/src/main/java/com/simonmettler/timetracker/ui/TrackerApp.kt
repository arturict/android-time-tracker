package com.simonmettler.timetracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simonmettler.timetracker.data.CalendarEntry

private enum class Screen(val label: String, val selected: ImageVector, val idle: ImageVector) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Calendar("Calendar", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    Insights("Insights", InsightsIcon, InsightsIcon),
    Editor("Buttons", Icons.Filled.Edit, Icons.Outlined.Edit)
}

@Composable
fun TrackerApp(vm: TrackerViewModel = viewModel()) {
    val activities by vm.activities.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val zone by vm.zone.collectAsStateWithLifecycle()
    val todayEntries by vm.todayEntries.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
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
    BackHandler(screen != Screen.Home) { screen = Screen.Home }
    TrackerTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            snackbarHost = {
                SnackbarHost(snackbar) {
                    Snackbar(it, shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.inverseSurface)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    Screen.entries.forEach { item ->
                        val selected = item == screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (item == Screen.Editor && !selected) editActivityId = null; screen = item },
                            icon = { Icon(if (selected) item.selected else item.idle, contentDescription = null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "screen") { target ->
                    when (target) {
                        Screen.Home -> HomeScreen(
                            activities = activities.filterNot { it.isArchived }, active = active, today = today, zone = zone,
                            todayEntries = todayEntries, onStart = vm::start,
                            onCreate = { editActivityId = null; screen = Screen.Editor },
                            onOpenCalendar = { vm.setDay(today); screen = Screen.Calendar }
                        )
                        Screen.Calendar -> {
                            val entries by vm.calendar.collectAsStateWithLifecycle()
                            CalendarScreen(day, zone, entries, vm::setDay, vm::setZone, vm::followDevice) { editing = it }
                        }
                        Screen.Insights -> {
                            val period by vm.period.collectAsStateWithLifecycle()
                            val anchor by vm.anchor.collectAsStateWithLifecycle()
                            val entries by vm.insightEntries.collectAsStateWithLifecycle()
                            InsightsScreen(period, anchor, zone, entries, vm::setPeriod, vm::shiftPeriod, vm::resetPeriod) {
                                vm.setDay(it); screen = Screen.Calendar
                            }
                        }
                        Screen.Editor -> ButtonsScreen(activities, editActivityId,
                            onSelect = { editActivityId = it },
                            onSave = { id, title, color -> vm.save(id, title, color) { screen = Screen.Home } },
                            onArchive = vm::archive, onMove = vm::move)
                    }
                }
            }
        }
        editing?.let { entry ->
            EntryEditorSheet(entry, onDismiss = { editing = null }) { start, end, shared, onError ->
                vm.edit(entry.interval, start, end, shared, onError) { editing = null }
            }
        }
    }
}
