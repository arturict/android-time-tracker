package com.simonmettler.timetracker

import android.app.Application
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.simonmettler.timetracker.data.TrackerDatabase
import com.simonmettler.timetracker.data.TrackerRepository
import com.simonmettler.timetracker.widget.TrackerWidget
import kotlinx.coroutines.*

open class TrackerApplication : Application() {
    open val database by lazy { TrackerDatabase.build(this) }
    val repository by lazy { TrackerRepository(database) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refreshWidgets() {
        scope.launch {
            try { TrackerWidget().updateAll(this@TrackerApplication) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { Log.w("TimeTracker", "Widget refresh deferred until next update", error) }
        }
    }
}
