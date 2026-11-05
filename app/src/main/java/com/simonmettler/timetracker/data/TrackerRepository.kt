package com.simonmettler.timetracker.data

import androidx.room.withTransaction
import com.simonmettler.timetracker.model.Boundary
import com.simonmettler.timetracker.model.TimeRules
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrackerRepository(
    private val database: TrackerDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val currentZone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private val dao = database.dao()
    private val writes = Mutex()
    val activities = dao.observeActivities()
    val active = dao.observeActive()
    fun window(from: Long, to: Long) = dao.observeWindow(from, to)

    suspend fun widgetSnapshot() = database.withTransaction { dao.visibleActivities() to dao.active()?.activityId }

    suspend fun saveActivity(id: String?, title: String, color: Int): String = writes.withLock {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 60) { "Enter a title between 1 and 60 characters." }
        database.withTransaction {
            val now = clock.millis()
            val old = id?.let { requireNotNull(dao.activity(it)) { "This activity no longer exists." } }
            val saved = old?.copy(title = trimmed, colorArgb = color, updatedAtMs = now)
                ?: TrackerActivity(UUID.randomUUID().toString(), trimmed, color, dao.nextOrder(), createdAtMs = now, updatedAtMs = now)
            if (old == null) dao.insertActivity(saved) else dao.updateActivity(saved)
            saved.id
        }
    }

    suspend fun setArchived(id: String, archived: Boolean) = writes.withLock {
        database.withTransaction {
            val item = requireNotNull(dao.activity(id)) { "This activity no longer exists." }
            require(!archived || dao.active()?.activityId != id) { "Switch activity or end its calendar entry before archiving it." }
            dao.updateActivity(item.copy(isArchived = archived, updatedAtMs = clock.millis()))
        }
    }

    suspend fun move(id: String, direction: Int) = writes.withLock {
        database.withTransaction {
            val items = dao.visibleActivities()
            val index = items.indexOfFirst { it.id == id }
            require(index >= 0) { "This activity is not visible." }
            val target = index + direction.coerceIn(-1, 1)
            if (target in items.indices) {
                val reordered = items.toMutableList().apply { add(target, removeAt(index)) }
                val now = clock.millis()
                reordered.forEachIndexed { order, item -> dao.updateActivity(item.copy(sortOrder = order, updatedAtMs = now)) }
            }
        }
    }

    suspend fun startActivity(id: String): String = writes.withLock {
        database.withTransaction {
            val item = requireNotNull(dao.activity(id)) { "This activity no longer exists." }
            require(!item.isArchived) { "This activity was archived. Open the app to restore it." }
            val boundary = Boundary.capture(clock.instant(), currentZone())
            val previous = dao.active()
            require(previous == null || boundary.atMs >= previous.startAtMs) { "The device clock is before the active start. Correct the clock or calendar entry." }
            if (previous != null) dao.updateInterval(previous.closedAt(boundary, boundary.atMs))
            require(dao.conflicts("", boundary.atMs, null).isEmpty()) { "The device clock conflicts with saved history. Correct the clock or calendar entry." }
            dao.insertInterval(TimeInterval(
                id = UUID.randomUUID().toString(), activityId = id,
                startAtMs = boundary.atMs, startZoneId = boundary.zoneId, startOffsetSeconds = boundary.offsetSeconds,
                createdAtMs = boundary.atMs, updatedAtMs = boundary.atMs
            ))
            item.title
        }
    }

    suspend fun editInterval(id: String, expected: TimeInterval, start: Boundary, end: Boundary?, moveShared: Boolean) = writes.withLock {
        database.withTransaction {
            val old = requireNotNull(dao.interval(id)) { "This entry no longer exists." }
            require(old == expected) { "This entry changed while you were editing. Reopen it to load the saved times." }
            require(end != null || old.endAtMs == null) { "A closed entry cannot be reopened. Press a tracker button to start tracking." }
            val now = clock.millis()
            val updates = linkedMapOf<String, TimeInterval>()
            updates[id] = old.copy(
                startAtMs = start.atMs, startZoneId = start.zoneId, startOffsetSeconds = start.offsetSeconds,
                endAtMs = end?.atMs, endZoneId = end?.zoneId, endOffsetSeconds = end?.offsetSeconds,
                activeSlot = if (end == null) 1 else null, updatedAtMs = now
            )
            if (moveShared && start.atMs != old.startAtMs) {
                dao.endingAt(old.startAtMs, id).forEach { updates[it.id] = it.closedAt(start, now) }
            }
            if (moveShared && old.endAtMs != null && end != null && old.endAtMs != end.atMs) {
                dao.startingAt(old.endAtMs, id).forEach {
                    updates[it.id] = it.copy(startAtMs = end.atMs, startZoneId = end.zoneId, startOffsetSeconds = end.offsetSeconds, updatedAtMs = now)
                }
            }
            updates.values.forEach { candidate ->
                require(candidate.startAtMs <= now && (candidate.endAtMs == null || candidate.endAtMs <= now)) { "Calendar times cannot be in the future." }
                require(candidate.endAtMs == null || candidate.endAtMs >= candidate.startAtMs) { "End must be at or after start, including adjacent entries." }
                if (candidate.endAtMs != candidate.startAtMs) {
                    require(dao.conflicts(candidate.id, candidate.startAtMs, candidate.endAtMs).none { it.id !in updates }) {
                        "These times overlap another entry. Enable Move shared boundary, or choose different times."
                    }
                }
            }
            val candidates = updates.values.toList()
            for (i in candidates.indices) for (j in i + 1 until candidates.size) {
                require(!TimeRules.overlaps(candidates[i].startAtMs, candidates[i].endAtMs, candidates[j].startAtMs, candidates[j].endAtMs)) { "The moved entries would overlap." }
            }
            updates.values.forEach { dao.updateInterval(it) }
        }
    }

    private fun TimeInterval.closedAt(boundary: Boundary, updated: Long) = copy(
        endAtMs = boundary.atMs, endZoneId = boundary.zoneId, endOffsetSeconds = boundary.offsetSeconds,
        activeSlot = null, updatedAtMs = updated
    )
}
