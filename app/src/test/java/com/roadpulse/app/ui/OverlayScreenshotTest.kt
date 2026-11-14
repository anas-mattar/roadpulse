package com.roadpulse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.EnglishNarrator
import com.roadpulse.app.nav.MalayNarrator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen's overlay on a plain background (the map itself needs a device) and saves
 * PNGs under app/build/outputs/roborazzi/. Catches layout breakage in CI without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [35])
class OverlayScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val place = Place("M City", "Jalan Ampang, Kuala Lumpur", GeoPoint(3.1579, 101.7116))
    private val route = Route(
        shape = listOf(GeoPoint(3.139, 101.6869), GeoPoint(3.1579, 101.7116)),
        maneuvers = listOf(Maneuver("Drive east on Jalan Damansara.", 11.3, 1140.0, 0, type = 1)),
        lengthKm = 11.3,
        timeSeconds = 1140.0,
    )

    private fun shot(name: String, mapColor: Color, content: @Composable () -> Unit) {
        compose.setContent {
            RoadPulseTheme {
                Box(Modifier.fillMaxSize().background(mapColor)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun explore() = shot("1_explore", RP.Field) {
        ExploreOverlay("", emptyList(), {}, {}, {}, {}, {}, {})
    }

    @Test
    fun exploreWithResults() = shot("1b_explore_search", RP.Field) {
        ExploreOverlay(
            "M City",
            listOf(place, Place("M City Kopitiam", "Selayang, Selangor", GeoPoint(3.25, 101.65))),
            {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun preview() = shot("2_preview", RP.Field) {
        val choices = listOf(
            route.copy(hasToll = true, maneuvers = listOf(Maneuver("x", 9.0, 900.0, 0, streetNames = listOf("E37")))),
            route.copy(lengthKm = 10.1, timeSeconds = 1380.0, maneuvers = listOf(Maneuver("x", 8.0, 1300.0, 0, streetNames = listOf("Jalan Tun Razak")))),
            route.copy(lengthKm = 12.4, timeSeconds = 1500.0, hasToll = true, maneuvers = listOf(Maneuver("x", 10.0, 1400.0, 0, streetNames = listOf("Jalan Belfield")))),
        )
        PreviewOverlay(place, choices[0], choices, selected = 0, onSelectRoute = {}, busy = false, onBack = {}, onGo = {}, onSimulate = {})
    }

    @Test
    fun driving() = shot("3_driving", RP.Field) {
        DrivingOverlay(
            state = MapUiState(
                navigating = true, route = route, destination = place,
                currentInstruction = "Turn right onto Jalan Ampang.", distanceToTurnMeters = 350.0,
                remainingKm = 7.2, remainingSeconds = 720.0,
                turnType = 10, thenType = 16, thenGapMeters = 200.0, speedKmh = 57,
            ),
            narrator = EnglishNarrator, muted = false, onToggleMute = {}, onReport = {}, onStop = {},
        )
    }

    @Test
    fun drivingMalayWithStatus() = shot("3b_driving_malay_gps_lost", RP.NightSurface) {
        DrivingOverlay(
            state = MapUiState(
                navigating = true, route = route, destination = place,
                currentInstruction = "Masuk ke bulatan dan ambil jalan keluar kedua ke Jalan P18 C1.",
                distanceToTurnMeters = 120.0, remainingKm = 3.4, remainingSeconds = 400.0,
                turnType = 26, navStatus = MalayNarrator.statusGpsLost, speedKmh = 32,
            ),
            narrator = MalayNarrator, muted = true, onToggleMute = {}, onReport = {}, onStop = {},
            following = false,
        )
    }
}
