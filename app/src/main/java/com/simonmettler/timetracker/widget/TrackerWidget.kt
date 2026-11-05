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
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.LocalSize
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as SolidColor
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.simonmettler.timetracker.TrackerApplication
import com.simonmettler.timetracker.data.TrackerActivity
import com.simonmettler.timetracker.model.TimeRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map

val ActivityIdKey = ActionParameters.Key<String>("activity_id")

class TrackerWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

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

private val WidgetSurface = ColorProvider(day = Color(0xF2F5F6F4), night = Color(0xF2171A1C))
private val WidgetText = ColorProvider(day = Color(0xFF15191B), night = Color(0xFFE6E9E8))

@Composable
private fun WidgetButtons(activities: List<TrackerActivity>, activeId: String?) {
    // Two columns once the widget is wide enough, so a 3x2 widget shows six buttons without scrolling.
    val columns = if (LocalSize.current.width >= 240.dp) 2 else 1
    Box(GlanceModifier.fillMaxSize().background(WidgetSurface).cornerRadius(24.dp).padding(6.dp)) {
        if (activities.isEmpty()) {
            Text("Create tracker buttons in Time Tracker first.", GlanceModifier.padding(16.dp), style = TextStyle(color = WidgetText, fontSize = 14.sp))
        } else LazyVerticalGrid(GridCells.Fixed(columns), GlanceModifier.fillMaxSize()) {
            items(activities, itemId = { it.id.hashCode().toLong() }) { activity ->
                val active = activeId == activity.id
                val fg = SolidColor(Color(TimeRules.foregroundColor(activity.colorArgb)))
                Box(GlanceModifier.fillMaxWidth().padding(3.dp)) {
                    Column(
                        GlanceModifier.fillMaxWidth().height(52.dp).background(SolidColor(Color(activity.colorArgb)))
                            .cornerRadius(18.dp)
                            .clickable(actionRunCallback<StartTrackerAction>(actionParametersOf(ActivityIdKey to activity.id)))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (active) "✓ ${activity.title}" else activity.title,
                            style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                            maxLines = 1
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
