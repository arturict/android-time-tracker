package com.simonmettler.timetracker

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
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
        ui.onNodeWithText("Tracking Work").assertIsDisplayed()
        val saved = runBlocking { app.repository.widgetSnapshot() }.first.single()
        assertEquals(0xFFFFD540.toInt(), saved.colorArgb)
        ui.onNodeWithText("Calendar").performClick()
        ui.onNodeWithText("Home").performClick()
        ui.onNodeWithText("Tracking Work").assertIsDisplayed()
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

    private fun capture(name: String) {
        ui.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(screenshot)
        val directory = File(app.cacheDir, "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
