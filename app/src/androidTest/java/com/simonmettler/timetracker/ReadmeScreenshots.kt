package com.simonmettler.timetracker

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.simonmettler.timetracker.data.TimeInterval
import com.simonmettler.timetracker.model.Boundary
import java.io.File
import java.time.LocalDate
import kotlin.random.Random
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Captures the README screenshots from synthetic activities in the isolated
 * in-memory test database. Run it only on demand:
 *
 *   gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.simonmettler.timetracker.ReadmeScreenshots -Pandroid.testInstrumentationRunnerArguments.readme=true
 *
 * Gradle copies the images to app/build/outputs/connected_android_test_additional_output.
 * The suffix follows the device's night mode, so run once in light and once in dark mode.
 */
class ReadmeScreenshots {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<TestTrackerApplication>()

    @Before fun seed() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readme") == "true")
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            val repo = app.repository
            val ids = listOf(
                "Deep work" to 0xFF0B7366, "Meetings" to 0xFF6C4FD8, "Learning" to 0xFF2F6FDB,
                "Exercise" to 0xFFC93C37, "Reading" to 0xFFE8833A, "Break" to 0xFF5D6B78
            ).associate { (title, color) -> title to repo.saveActivity(null, title, color.toInt()) }
            // A plausible day, newest last. The final entry stays open and is the active one.
            val plan = listOf(
                "Exercise" to 40, "Break" to 20, "Deep work" to 95, "Meetings" to 45, "Break" to 50,
                "Learning" to 70, "Meetings" to 30, "Reading" to 25, "Deep work" to 47
            )
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            // Three weeks of plausible history before today, so Insights has real shape.
            val random = Random(7)
            fun minutes(range: IntRange) = random.nextInt(range.first, range.last + 1).toLong()
            var historyIndex = 0
            for (back in 20 downTo 1) {
                val day = LocalDate.now(zone).minusDays(back.toLong())
                val weekend = day.dayOfWeek.value >= 6
                val steps = if (weekend) listOf("Exercise" to 50..90, "Break" to 30..60, "Reading" to 60..120, "Learning" to 30..60, "Break" to 45..90, "Reading" to 30..60)
                    else listOf("Exercise" to 25..45, "Break" to 15..25, "Deep work" to 80..130, "Meetings" to 30..70, "Break" to 40..60,
                        "Learning" to 40..90, "Meetings" to 20..50, "Deep work" to 60..110, "Reading" to 15..40)
                var at = day.atTime(if (weekend) 9 else 7, 0).atZone(zone).toInstant().plusSeconds(minutes(0..40) * 60)
                steps.forEach { (title, length) ->
                    if (title == "Exercise" && !weekend && random.nextInt(3) == 0) return@forEach
                    val start = Boundary.capture(at, zone)
                    at = at.plus(Duration.ofMinutes(minutes(length)))
                    val end = Boundary.capture(at, zone)
                    app.database.dao().insertInterval(TimeInterval("history-${historyIndex++}", ids.getValue(title), start.atMs, start.zoneId, start.offsetSeconds,
                        end.atMs, end.zoneId, end.offsetSeconds, null, start.atMs, start.atMs))
                    at = at.plus(Duration.ofMinutes(minutes(0..12)))
                }
            }
            var cursor = now.minus(Duration.ofMinutes(plan.sumOf { it.second }.toLong()))
            plan.forEachIndexed { index, (title, minutes) ->
                val start = Boundary.capture(cursor, zone)
                val last = index == plan.lastIndex
                cursor = cursor.plus(Duration.ofMinutes(minutes.toLong()))
                val end = if (last) null else Boundary.capture(cursor, zone)
                app.database.dao().insertInterval(TimeInterval(
                    "readme-$index", ids.getValue(title), start.atMs, start.zoneId, start.offsetSeconds,
                    end?.atMs, end?.zoneId, end?.offsetSeconds, if (last) 1 else null, start.atMs, start.atMs
                ))
            }
        }
    }

    @Test fun captureReadmeScreens() {
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Deep work").fetchSemanticsNodes().isNotEmpty() }
        settle()
        capture("home")
        ui.onNodeWithText("Calendar").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("TRACKED").fetchSemanticsNodes().isNotEmpty() }
        settle()
        capture("calendar")
        ui.onAllNodesWithText("Exercise").onFirst().performClick()
        settle()
        capture("entry")
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithText("Buttons").performClick()
        ui.onNodeWithText("Button title").performTextInput("Side project")
        ui.onNodeWithText("Button title").performImeAction()
        ui.onNodeWithContentDescription("Color #E8833A").performClick()
        settle()
        capture("editor")
        ui.onNodeWithText("Insights").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Day by day").fetchSemanticsNodes().isNotEmpty() }
        settle()
        capture("insights")
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Where it flowed"))
        settle()
        capture("insights-flow")
        ui.onNode(hasScrollAction()).performScrollToIndex(0)
        ui.onNodeWithText("Month").performClick()
        settle()
        capture("insights-month")
    }

    /**
     * Thread.sleep alone would not advance the Compose test clock, so animations would stay
     * mid-flight; the short real wait afterwards lets the display present the settled frame.
     */
    private fun settle() { ui.mainClock.advanceTimeBy(1_500); ui.waitForIdle(); Thread.sleep(400) }

    private fun capture(name: String) {
        val night = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val shot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val dir = File(app.externalMediaDirs.first(), "additional_test_output").apply { mkdirs() }
        File(dir, "$name-${if (night) "dark" else "light"}.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shot.recycle()
    }
}
