package com.simonmettler.timetracker

import android.graphics.Bitmap
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.glance.appwidget.AppWidgetId
import com.simonmettler.timetracker.widget.TrackerWidget
import com.simonmettler.timetracker.widget.TrackerWidgetReceiver
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.simonmettler.timetracker.data.TimeInterval
import com.simonmettler.timetracker.model.Boundary
import com.simonmettler.timetracker.model.TimeRules
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class TrackerUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<TestTrackerApplication>()
    @Before fun reset() = runBlocking(Dispatchers.IO) { app.database.clearAllTables() }

    @Test fun createColorButtonTrackAndReturnHome() {
        ui.onNodeWithText("Create your first button").performClick()
        ui.onNodeWithText("Button title").performTextInput("Work")
        ui.onNodeWithText("Custom color (hex RGB)").performTextReplacement("FFD540")
        ui.onNodeWithText("Save button").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("Work. Start new interval.").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Work. Start new interval.").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.dao().active() != null } }
        ui.onNodeWithContentDescription("Tracking Work", substring = true).assertIsDisplayed()
        val saved = runBlocking { app.repository.widgetSnapshot() }.first.single()
        assertEquals(0xFFFFD540.toInt(), saved.colorArgb)
        ui.onNodeWithText("Calendar").performClick()
        ui.onNodeWithText("Home").performClick()
        ui.onNodeWithContentDescription("Tracking Work", substring = true).assertIsDisplayed()
    }

    @Test fun calendarEditPersistsChangedStart() {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val start = Boundary.capture(now.minusSeconds(3600), zone)
        val end = Boundary.capture(now.minusSeconds(60), zone)
        runBlocking {
            val id = app.repository.saveActivity(null, "Reading", 0xFF7254B8.toInt())
            app.database.dao().insertInterval(TimeInterval("ui-entry", id, start.atMs, start.zoneId, start.offsetSeconds, end.atMs, end.zoneId, end.offsetSeconds, null, start.atMs, start.atMs))
        }
        ui.onNodeWithText("Calendar").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Reading").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Reading").performClick()
        val changed = Boundary.capture(now.minusSeconds(1800), zone)
        ui.onNodeWithText("Start date and time").performTextReplacement(TimeRules.originalLocal(changed))
        ui.onNodeWithText("Save entry").performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.dao().interval("ui-entry")!!.startAtMs == changed.atMs } }
        ui.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test fun longDashboardCanReachLastButtonAndCaptureScreens() {
        runBlocking {
            repeat(12) { app.repository.saveActivity(null, "Activity ${it + 1}", listOf(0xFF19776A, 0xFF7254B8, 0xFFB6553C)[it % 3].toInt()) }
        }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Activity 1").fetchSemanticsNodes().isNotEmpty() }
        capture("dashboard")
        ui.onNodeWithText("Activity 1").performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Activity 12"))
        ui.onNodeWithText("Activity 12").assertIsDisplayed().performClick()
        ui.waitUntil(10_000) { runBlocking { app.database.dao().active()?.let { app.database.dao().activity(it.activityId)?.title } == "Activity 12" } }
        capture("last-button")
        ui.onNodeWithText("Calendar").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Activity 12").fetchSemanticsNodes().isNotEmpty() }
        capture("calendar")
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun realWidgetHostRendersButtonsAndDeliversRepeatedTaps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun awaitNative(timeout: Long, condition: () -> Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + timeout
            while (!condition()) {
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for the native widget" }
                Thread.sleep(50)
            }
        }
        runBlocking {
            app.repository.saveActivity(null, "Widget Work", 0xFF19776A.toInt())
            app.repository.saveActivity(null, "Widget Travel", 0xFF7254B8.toInt())
        }
        val host = AppWidgetHost(app, 1024)
        var widgetId = -1
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            widgetId = host.allocateAppWidgetId()
            val manager = AppWidgetManager.getInstance(app)
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300)
            }
            assertTrue(manager.bindAppWidgetIdIfAllowed(widgetId, ComponentName(app, TrackerWidgetReceiver::class.java), options))
            host.startListening()
            lateinit var root: View
            instrumentation.runOnMainSync {
                root = host.createView(ui.activity, widgetId, manager.getAppWidgetInfo(widgetId))
                ui.activity.setContentView(root)
            }
            runBlocking { TrackerWidget().update(app, AppWidgetId(widgetId)) }
            fun find(view: View, title: String): TextView? {
                if (view is TextView && view.text.toString().contains(title)) return view
                if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), title)?.let { return it }
                return null
            }
            fun click(title: String) {
                awaitNative(30_000) {
                    var found = false
                    instrumentation.runOnMainSync { found = find(root, title) != null }
                    found
                }
                instrumentation.runOnMainSync {
                    var target: View = requireNotNull(find(root, title))
                    while (!target.hasOnClickListeners() && target.parent is View) target = target.parent as View
                    assertTrue("Widget button should deliver its PendingIntent", target.performClick())
                }
            }
            click("Widget Work")
            awaitNative(15_000) { runBlocking { app.database.dao().active() != null } }
            click("Widget Travel")
            awaitNative(15_000) { runBlocking { app.database.dao().intervals().size == 2 } }
            click("Widget Travel")
            awaitNative(15_000) { runBlocking { app.database.dao().intervals().size == 3 } }
            assertEquals("Widget Travel", runBlocking { app.database.dao().activity(app.database.dao().active()!!.activityId)!!.title })
            capture("widget")
        } finally {
            if (widgetId != -1) host.deleteAppWidgetId(widgetId)
            host.stopListening()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(screenshot)
        val directory = File(app.cacheDir, "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
