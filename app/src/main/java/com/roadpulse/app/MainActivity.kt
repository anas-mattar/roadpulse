package com.roadpulse.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.roadpulse.app.nav.DisplaySettings
import com.roadpulse.app.nav.NavigationService
import com.roadpulse.app.nav.NavigationSession
import com.roadpulse.app.nav.VoiceProbe
import com.roadpulse.app.nav.VoiceSettings
import com.roadpulse.app.ui.MapScreen
import com.roadpulse.app.ui.RoadPulseTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        VoiceSettings.init(this)
        DisplaySettings.init(this)
        com.roadpulse.app.data.RouteSettings.init(this)
        com.roadpulse.app.data.ServerSettings.init(this)
        VoiceProbe.check(this, VoiceSettings.language.value)
        NavigationSession.init(NavigationService.driveStore(this), SystemClock::elapsedRealtime)
        enableEdgeToEdge()
        setContent {
            RoadPulseTheme {
                MapScreen()
            }
        }
    }
}
