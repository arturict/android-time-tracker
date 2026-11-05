package com.simonmettler.timetracker

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.simonmettler.timetracker.data.TrackerDatabase

// Instrumentation always uses an isolated in-memory database, including UI and
// widget callbacks. Running these tests never opens a user's tracking database.
class TestTrackerApplication : TrackerApplication() {
    override val database by lazy { TrackerDatabase.inMemory(this) }
}

class TrackerTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, TestTrackerApplication::class.java.name, context)
}
