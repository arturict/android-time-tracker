package com.simonmettler.timetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.simonmettler.timetracker.ui.TrackerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TrackerApp() }
    }
    override fun onResume() {
        super.onResume()
        (application as TrackerApplication).refreshWidgets()
    }
}
