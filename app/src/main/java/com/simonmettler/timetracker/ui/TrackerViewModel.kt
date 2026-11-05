package com.simonmettler.timetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.simonmettler.timetracker.TrackerApplication
import com.simonmettler.timetracker.data.TimeInterval
import com.simonmettler.timetracker.model.Boundary
import com.simonmettler.timetracker.model.TimeRules
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as TrackerApplication
    private val repository = app.repository
    val activities = repository.activities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active = repository.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val dayState = MutableStateFlow(LocalDate.parse(savedState["day"] ?: LocalDate.now().toString()))
    val day = dayState.asStateFlow()
    private val zoneState = MutableStateFlow(ZoneId.systemDefault())
    val zone = zoneState.asStateFlow()
    private var followDeviceZone = true
    val calendar = combine(day, zone, ::Pair).flatMapLatest { (day, zone) ->
        val (from, to) = TimeRules.dayWindow(day, zone)
        repository.window(from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    private val messages = Channel<String>(Channel.BUFFERED)
    val feedback = messages.receiveAsFlow()

    fun setDay(value: LocalDate) { dayState.value = value; savedState["day"] = value.toString() }
    fun setZone(value: ZoneId) { followDeviceZone = false; zoneState.value = value }
    fun followDevice() { followDeviceZone = true; refreshZone() }
    fun refreshZone() { if (followDeviceZone) zoneState.value = ZoneId.systemDefault() }

    fun start(id: String) = perform { "${repository.startActivity(id)} tracker is set" }
    fun save(id: String?, title: String, color: Int, done: () -> Unit) = perform(done) {
        repository.saveActivity(id, title, color); "Button saved"
    }
    fun archive(id: String, archived: Boolean) = perform {
        repository.setArchived(id, archived); if (archived) "Button archived" else "Button restored"
    }
    fun move(id: String, direction: Int) = perform { repository.move(id, direction); "Button order updated" }
    fun edit(old: TimeInterval, start: Boundary, end: Boundary?, shared: Boolean, onError: (String) -> Unit, done: () -> Unit) = perform(done, onError) {
        repository.editInterval(old.id, old, start, end, shared); "Calendar entry saved"
    }

    private fun perform(done: () -> Unit = {}, onError: ((String) -> Unit)? = null, block: suspend () -> String) {
        viewModelScope.launch {
            try {
                val message = block()
                done()
                app.refreshWidgets()
                messages.send(message)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val message = error.message ?: "Could not save. Your existing data is unchanged."
                if (onError != null) onError(message) else messages.send(message)
            }
        }
    }
}
