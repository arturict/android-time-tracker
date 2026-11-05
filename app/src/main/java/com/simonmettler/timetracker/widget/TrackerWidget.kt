package com.simonmettler.timetracker.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.simonmettler.timetracker.TrackerApplication
import com.simonmettler.timetracker.data.TrackerActivity
import com.simonmettler.timetracker.model.TimeRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map

val ActivityIdKey = ActionParameters.Key<String>("activity_id")

class TrackerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as TrackerApplication).repository
        val initial = repository.widgetSnapshot()
        val activeIds = repository.active.map { it?.activityId }
        provideContent {
            val activities by repository.activities.collectAsState(initial = initial.first)
            val activeId by activeIds.collectAsState(initial = initial.second)
            WidgetButtons(activities.filterNot { it.isArchived }, activeId)
        }
    }
}

@Composable
private fun WidgetButtons(activities: List<TrackerActivity>, activeId: String?) {
    LazyColumn(GlanceModifier.fillMaxSize().padding(4.dp)) {
        if (activities.isEmpty()) item {
            Text("Create tracker buttons in Time Tracker first.", GlanceModifier.padding(16.dp), style = TextStyle(color = ColorProvider(Color(0xFF17283F))))
        }
        activities.forEach { activity ->
            item {
                Box(GlanceModifier.fillMaxWidth().padding(4.dp)) {
                    Box(
                        GlanceModifier.fillMaxWidth().background(ColorProvider(Color(activity.colorArgb)))
                            .cornerRadius(18.dp)
                            .clickable(actionRunCallback<StartTrackerAction>(actionParametersOf(ActivityIdKey to activity.id)))
                            .padding(18.dp)
                    ) {
                        Text(
                            text = if (activeId == activity.id) "✓ ${activity.title}" else activity.title,
                            style = TextStyle(color = ColorProvider(Color(TimeRules.foregroundColor(activity.colorArgb))), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
            }
        }
    }
}

class TrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrackerWidget()
}

class StartTrackerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as TrackerApplication
        val message = try {
            val id = requireNotNull(parameters[ActivityIdKey]) { "This button is unavailable. Re-add the widget." }
            "${app.repository.startActivity(id)} tracker is set"
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { error.message ?: "Could not save. Your existing data is unchanged." }
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        try { TrackerWidget().updateAll(context) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { Log.w("TimeTracker", "Widget refresh deferred until next update", error) }
    }
}
