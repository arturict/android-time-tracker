package com.simonmettler.timetracker.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "activity")
data class TrackerActivity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long
)

@Entity(
    tableName = "time_interval",
    foreignKeys = [ForeignKey(entity = TrackerActivity::class, parentColumns = ["id"], childColumns = ["activity_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["active_slot"], unique = true), Index("start_at_ms"), Index(value = ["activity_id", "start_at_ms"])]
)
data class TimeInterval(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "start_at_ms") val startAtMs: Long,
    @ColumnInfo(name = "start_zone_id") val startZoneId: String,
    @ColumnInfo(name = "start_offset_seconds") val startOffsetSeconds: Int,
    @ColumnInfo(name = "end_at_ms") val endAtMs: Long? = null,
    @ColumnInfo(name = "end_zone_id") val endZoneId: String? = null,
    @ColumnInfo(name = "end_offset_seconds") val endOffsetSeconds: Int? = null,
    @ColumnInfo(name = "active_slot") val activeSlot: Int? = 1,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long
)

data class CalendarEntry(
    @Embedded val interval: TimeInterval,
    @ColumnInfo(name = "activity_title") val title: String,
    @ColumnInfo(name = "activity_color") val colorArgb: Int
)

@Dao
interface TrackerDao {
    @Query("SELECT * FROM activity ORDER BY sort_order, created_at_ms, id")
    fun observeActivities(): Flow<List<TrackerActivity>>
    @Query("SELECT * FROM activity WHERE is_archived = 0 ORDER BY sort_order, created_at_ms, id")
    suspend fun visibleActivities(): List<TrackerActivity>
    @Query("SELECT * FROM activity WHERE id = :id")
    suspend fun activity(id: String): TrackerActivity?
    @Insert suspend fun insertActivity(activity: TrackerActivity)
    @Update suspend fun updateActivity(activity: TrackerActivity)
    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM activity")
    suspend fun nextOrder(): Int
    @Query("SELECT * FROM time_interval WHERE active_slot = 1 LIMIT 1")
    suspend fun active(): TimeInterval?
    @Query("SELECT * FROM time_interval WHERE active_slot = 1 LIMIT 1")
    fun observeActive(): Flow<TimeInterval?>
    @Query("SELECT * FROM time_interval WHERE id = :id")
    suspend fun interval(id: String): TimeInterval?
    @Query("SELECT * FROM time_interval ORDER BY start_at_ms, created_at_ms, id")
    suspend fun intervals(): List<TimeInterval>
    @Query("SELECT * FROM time_interval WHERE id != :exceptId AND (end_at_ms IS NULL OR end_at_ms > start_at_ms) AND (:end IS NULL OR start_at_ms < :end) AND (end_at_ms IS NULL OR end_at_ms > :start)")
    suspend fun conflicts(exceptId: String, start: Long, end: Long?): List<TimeInterval>
    @Query("SELECT * FROM time_interval WHERE end_at_ms = :at AND id != :id AND start_at_ms < end_at_ms")
    suspend fun endingAt(at: Long, id: String): List<TimeInterval>
    @Query("SELECT * FROM time_interval WHERE start_at_ms = :at AND id != :id AND (end_at_ms IS NULL OR end_at_ms > start_at_ms)")
    suspend fun startingAt(at: Long, id: String): List<TimeInterval>
    @Query("SELECT time_interval.*, activity.title AS activity_title, activity.color_argb AS activity_color FROM time_interval JOIN activity ON activity.id = activity_id WHERE start_at_ms < :to AND (end_at_ms IS NULL OR end_at_ms > :from) ORDER BY start_at_ms, time_interval.id")
    fun observeWindow(from: Long, to: Long): Flow<List<CalendarEntry>>
    @Insert suspend fun insertInterval(interval: TimeInterval)
    @Update suspend fun updateInterval(interval: TimeInterval)
}

@Database(entities = [TrackerActivity::class, TimeInterval::class], version = 1, exportSchema = true)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao

    companion object {
        // Room cannot express these cross-column CHECKs in entity annotations.
        // Triggers protect both freshly created files and files reopened later.
        private val integrity = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                for (operation in listOf("INSERT", "UPDATE")) {
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS interval_integrity_${operation.lowercase()}
                        BEFORE $operation ON time_interval
                        WHEN NOT (
                            (NEW.end_at_ms IS NULL AND NEW.end_zone_id IS NULL AND NEW.end_offset_seconds IS NULL AND NEW.active_slot IS 1)
                            OR (NEW.end_at_ms IS NOT NULL AND NEW.end_zone_id IS NOT NULL AND NEW.end_offset_seconds IS NOT NULL AND NEW.active_slot IS NULL AND NEW.end_at_ms >= NEW.start_at_ms)
                        )
                        BEGIN SELECT RAISE(ABORT, 'Invalid interval endpoints or active marker'); END
                    """.trimIndent())
                }
            }
        }
        fun build(context: Context, name: String = "time-tracker.db"): TrackerDatabase =
            Room.databaseBuilder(context.applicationContext, TrackerDatabase::class.java, name)
                .addCallback(integrity).build()

        fun inMemory(context: Context): TrackerDatabase =
            Room.inMemoryDatabaseBuilder(context, TrackerDatabase::class.java).addCallback(integrity).build()
    }
}
