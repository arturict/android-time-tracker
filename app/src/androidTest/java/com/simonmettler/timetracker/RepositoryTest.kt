package com.simonmettler.timetracker

import android.content.Context
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.AppWidgetId
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simonmettler.timetracker.data.*
import com.simonmettler.timetracker.model.Boundary
import com.simonmettler.timetracker.widget.ActivityIdKey
import com.simonmettler.timetracker.widget.StartTrackerAction
import java.time.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TrackerDatabase
    private lateinit var repository: TrackerRepository
    private val clock = MutableClock()
    private var zone = ZoneId.of("Europe/Zurich")
    private lateinit var work: String
    private lateinit var travel: String
    @Before fun setup() = runBlocking {
        clock.time = Instant.parse("2026-11-05T09:00:00Z")
        db = TrackerDatabase.inMemory(context)
        repository = TrackerRepository(db, clock) { zone }
        work = repository.saveActivity(null, "Work", 0xFF19776A.toInt())
        travel = repository.saveActivity(null, "Travel", 0xFF7254B8.toInt())
    }
    @After fun close() { db.close() }
    private fun boundary(ms: Long) = Boundary.capture(Instant.ofEpochMilli(ms), zone)
    private suspend fun failure(block: suspend () -> Unit) {
        try { block(); fail("Expected operation to fail") } catch (_: IllegalArgumentException) { }
    }
    @Test fun sameAndDifferentButtonsShareExactBoundaries() = runBlocking {
        repository.startActivity(work)
        clock.time = clock.time.plusSeconds(60)
        repository.startActivity(travel)
        clock.time = clock.time.plusSeconds(60)
        repository.startActivity(travel)
        val rows = db.dao().intervals()
        assertEquals(3, rows.size)
        assertEquals(rows[0].endAtMs, rows[1].startAtMs)
        assertEquals(rows[1].endAtMs, rows[2].startAtMs)
        assertEquals(travel, rows[2].activityId)
        assertEquals(1, rows.count { it.endAtMs == null })
    }
    @Test fun concurrentAndIdenticalMillisecondPressesRemainDistinct() = runBlocking {
        coroutineScope { repeat(16) { launch(Dispatchers.Default) { repository.startActivity(work) } } }
        val rows = db.dao().intervals()
        assertEquals(16, rows.size)
        assertEquals(15, rows.count { it.endAtMs == it.startAtMs })
        assertEquals(1, rows.count { it.activeSlot == 1 })
    }
    @Test fun clockRollbackPreservesActiveInterval() = runBlocking {
        repository.startActivity(work)
        val before = db.dao().active()
        clock.time = clock.time.minusSeconds(60)
        failure { repository.startActivity(travel) }
        assertEquals(before, db.dao().active())
        assertEquals(1, db.dao().intervals().size)
    }
    @Test fun transactionRollsBackClosureWhenInsertFails() = runBlocking {
        repository.startActivity(work)
        val before = db.dao().active()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_insert BEFORE INSERT ON time_interval BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        clock.time = clock.time.plusSeconds(60)
        try { repository.startActivity(travel); fail("Expected SQLite failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, db.dao().active())
        assertEquals(1, db.dao().intervals().size)
    }
    @Test fun storageRejectsMultipleOpenIntervalsAndMalformedMarkers() = runBlocking {
        repository.startActivity(work)
        val active = db.dao().active()!!
        try { db.dao().insertInterval(active.copy(id = "second")); fail("Expected unique constraint") } catch (_: android.database.sqlite.SQLiteException) { }
        try { db.dao().updateInterval(active.copy(activeSlot = null)); fail("Expected integrity trigger") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(active, db.dao().active())
    }
    @Test fun sharedBoundaryEditMovesBothEntriesAtomically() = runBlocking {
        repository.startActivity(work)
        clock.time = clock.time.plusSeconds(120)
        repository.startActivity(travel)
        clock.time = clock.time.plusSeconds(120)
        val first = db.dao().intervals().first()
        val shifted = boundary(first.endAtMs!! + 60_000)
        failure { repository.editInterval(first.id, first, boundary(first.startAtMs), shifted, false) }
        repository.editInterval(first.id, first, boundary(first.startAtMs), shifted, true)
        val rows = db.dao().intervals()
        assertEquals(shifted.atMs, rows.first().endAtMs)
        assertEquals(shifted.atMs, rows.last().startAtMs)
        assertEquals(1, rows.last().activeSlot)
    }
    @Test fun staleCalendarEditorCannotOverwriteAWidgetSwitch() = runBlocking {
        repository.startActivity(work)
        val before = db.dao().active()!!
        clock.time = clock.time.plusSeconds(60)
        repository.startActivity(travel)
        failure { repository.editInterval(before.id, before, boundary(before.startAtMs), null, false) }
        assertEquals(travel, db.dao().active()!!.activityId)
    }
    @Test fun calendarCanCloseActiveEntryAndRejectFutureEnd() = runBlocking {
        repository.startActivity(work)
        val before = db.dao().active()!!
        clock.time = clock.time.plusSeconds(120)
        failure { repository.editInterval(before.id, before, boundary(before.startAtMs), boundary(clock.millis() + 1), false) }
        repository.editInterval(before.id, before, boundary(before.startAtMs), boundary(clock.millis()), false)
        assertNull(db.dao().active())
    }
    @Test fun archivingPreservesHistoryAndRejectsActiveActivity() = runBlocking {
        repository.startActivity(work)
        failure { repository.setArchived(work, true) }
        clock.time = clock.time.plusSeconds(60)
        repository.startActivity(travel)
        repository.setArchived(work, true)
        failure { repository.startActivity(work) }
        assertEquals(2, db.dao().intervals().size)
        assertTrue(db.dao().activity(work)!!.isArchived)
    }
    @Test fun travelCapturesDifferentStartAndEndZones() = runBlocking {
        repository.startActivity(work)
        clock.time = clock.time.plusSeconds(60)
        zone = ZoneId.of("Asia/Tokyo")
        repository.startActivity(travel)
        val first = db.dao().intervals().first()
        assertEquals("Europe/Zurich", first.startZoneId)
        assertEquals("Asia/Tokyo", first.endZoneId)
        assertEquals(60_000L, first.endAtMs!! - first.startAtMs)
    }
    @Test fun databaseReopenRestoresActiveTracker() = runBlocking {
        val name = "instrumentation-reopen-only.db"
        context.deleteDatabase(name)
        try {
            val disk = TrackerDatabase.build(context, name)
            val repo = TrackerRepository(disk, clock)
            val id = repo.saveActivity(null, "Synthetic test", 0xFF19776A.toInt())
            repo.startActivity(id)
            disk.close()
            val reopened = TrackerDatabase.build(context, name)
            try { assertEquals(id, reopened.dao().active()!!.activityId) } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun actualWidgetCallbackUsesApplicationRepository() = runBlocking {
        val app = context as TestTrackerApplication
        val id = app.repository.saveActivity(null, "Synthetic widget test", 0xFF19776A.toInt())
        StartTrackerAction().onAction(context, AppWidgetId(1), actionParametersOf(ActivityIdKey to id))
        assertEquals(id, app.database.dao().active()!!.activityId)
    }
}

private class MutableClock : Clock() {
    var time: Instant = Instant.EPOCH
    override fun instant(): Instant = time
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
