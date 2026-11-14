package com.roadpulse.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.roadpulse.app.nav.NavigationService
import com.roadpulse.app.nav.NavigationSession
import com.roadpulse.app.ui.MapScreen
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        NavigationSession.init(NavigationService.driveStore(this), SystemClock::elapsedRealtime)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MapScreen()
            }
        }
    }
}
