package com.roadpulse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.SavedPlaces
import com.roadpulse.app.data.Shortcut
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

    private fun shot(name: String, mapColor: Color, arabic: Boolean = false, content: @Composable () -> Unit) {
        val strings = if (arabic) com.roadpulse.app.i18n.ArabicStrings else com.roadpulse.app.i18n.EnglishStrings
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                com.roadpulse.app.i18n.LocalStrings provides strings,
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (arabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                RoadPulseTheme {
                    Box(Modifier.fillMaxSize().background(mapColor)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun explore() = shot("1_explore", RP.Field) {
        ExploreOverlay(
            query = "", results = emptyList(),
            saved = SavedPlaces(
                home = Place("Jalan Bangsar 12", "Bangsar", GeoPoint(3.13, 101.67)),
                recent = listOf(place, Place("KLCC", "Kuala Lumpur", GeoPoint(3.158, 101.712)), Place("Sunway Pyramid", "Subang Jaya", GeoPoint(3.073, 101.607))),
            ),
            onQueryChange = {}, onClear = {}, onSelect = {}, onOpenSettings = {}, onRecenter = {}, onReport = {},
        )
    }

    @Test
    fun settingHome() = shot("1c_explore_set_home", RP.Field) {
        ExploreOverlay(
            query = "", results = emptyList(), settingShortcut = Shortcut.HOME,
            onQueryChange = {}, onClear = {}, onSelect = {}, onOpenSettings = {}, onRecenter = {}, onReport = {},
        )
    }

    @Test
    fun exploreWithManyResults() = shot("1h_explore_search_many", RP.Field) {
        ExploreOverlay(
            query = "mcity",
            results = listOf(
                place, Place("M City Kopitiam", "Jalan MR 1/3, Selayang, Selangor, Malaysia", GeoPoint(3.25, 101.65)),
                Place("Mcity Inn", "Jalan South Yu Seng, Miri, Sarawak, Malaysia", GeoPoint(4.39, 113.99)),
                Place("MCity Plastic & Packaging", "Jalan Haji Lampam, Miri, Sarawak, Malaysia", GeoPoint(4.40, 113.98)),
                Place("M City Dental Clinic", "Clinic · Jalan Ampang, Kuala Lumpur", GeoPoint(3.1587, 101.748)),
                Place("Sky Gym at M-City Residence", "Gym · Jalan Ampang, Kuala Lumpur", GeoPoint(3.1589, 101.7474)),
            ),
            searchFrom = GeoPoint(3.21, 101.75),
            onQueryChange = {}, onClear = {}, onSelect = {}, onOpenSettings = {}, onRecenter = {}, onReport = {},
        )
    }

    @Test
    fun exploreWithResults() = shot("1b_explore_search", RP.Field) {
        ExploreOverlay(
            query = "M City",
            results = listOf(place, Place("M City Kopitiam", "Selayang, Selangor", GeoPoint(3.25, 101.65))),
            onQueryChange = {}, onClear = {}, onSelect = {}, onOpenSettings = {}, onRecenter = {}, onReport = {},
        )
    }

    @Test
    fun preview() = shot("2_preview", RP.Field) {
        val choices = listOf(
            route.copy(hasToll = true, maneuvers = listOf(Maneuver("x", 9.0, 900.0, 0, streetNames = listOf("E37")))),
            route.copy(lengthKm = 10.1, timeSeconds = 1380.0, maneuvers = listOf(Maneuver("x", 8.0, 1300.0, 0, streetNames = listOf("Jalan Tun Razak")))),
            route.copy(lengthKm = 12.4, timeSeconds = 1500.0, hasToll = true, maneuvers = listOf(Maneuver("x", 10.0, 1400.0, 0, streetNames = listOf("Jalan Belfield")))),
        )
        PreviewOverlay(
            place, choices[0], choices, selected = 0, onSelectRoute = {}, busy = false, onBack = {}, onGo = {}, onSimulate = {},
            tolls = listOf(
                com.roadpulse.app.data.TollEstimate(totalRm = 6.8, complete = true,
                    plazas = listOf(com.roadpulse.app.data.TollPlaza("Gombak", status = "PRICED", priceRm = 6.8))),
                null,
                com.roadpulse.app.data.TollEstimate(totalRm = 2.5, complete = false,
                    plazas = listOf(com.roadpulse.app.data.TollPlaza("Pantai", status = "PRICED", priceRm = 2.5), com.roadpulse.app.data.TollPlaza(null, status = "NOT_IN_TABLE"))),
            ),
        )
    }

    @Test
    fun driving() = shot("3_driving", RP.Field) {
        DrivingOverlay(
            state = MapUiState(
                navigating = true, route = route, destination = place,
                currentInstruction = "Turn right onto Jalan Ampang.", distanceToTurnMeters = 350.0,
                remainingKm = 7.2, remainingSeconds = 720.0,
                turnType = 10, thenType = 16, thenGapMeters = 200.0, speedKmh = 57, speedLimitKph = 60,
            ),
            narrator = EnglishNarrator, muted = false, onToggleMute = {}, onReport = {}, onStop = {},
        )
    }

    @Test
    fun drivingArabic() = shot("ar_3_driving", RP.Field, arabic = true) {
        val n = com.roadpulse.app.nav.ArabicNarrator
        DrivingOverlay(
            state = MapUiState(
                navigating = true, route = route, destination = place,
                currentInstruction = n.atTurn(Maneuver("Turn right onto Jalan Ampang.", 0.5, 30.0, 0, type = 10, streetNames = listOf("Jalan Ampang"))),
                distanceToTurnMeters = 350.0, remainingKm = 7.2, remainingSeconds = 720.0,
                turnType = 10, thenType = 16, thenGapMeters = 200.0, speedKmh = 57, speedLimitKph = 60,
            ),
            narrator = n, muted = false, onToggleMute = {}, onReport = {}, onStop = {},
        )
    }

    @Test
    fun exploreArabic() = shot("ar_1_explore", RP.Field, arabic = true) {
        ExploreOverlay(
            query = "", results = emptyList(),
            saved = SavedPlaces(
                home = Place("Jalan Bangsar 12", "Bangsar", GeoPoint(3.13, 101.67)),
                recent = listOf(place),
                favourites = listOf(Place("Gym", "Jalan Telawi, Bangsar", GeoPoint(3.13, 101.67))),
                parking = com.roadpulse.app.data.ParkedCar(GeoPoint(3.14, 101.69), 1_794_660_000_000L),
            ),
            onQueryChange = {}, onClear = {}, onSelect = {}, onOpenSettings = {}, onRecenter = {}, onReport = {},
            onVoiceSearch = {}, onCategory = {}, nowMillis = 1_794_660_000_000L + 25 * 60_000,
        )
    }

    @Test
    fun previewArabic() = shot("ar_2_preview", RP.Field, arabic = true) {
        val choices = listOf(
            route.copy(hasToll = true, maneuvers = listOf(Maneuver("x", 9.0, 900.0, 0, streetNames = listOf("E37")))),
            route.copy(lengthKm = 10.1, timeSeconds = 1380.0, maneuvers = listOf(Maneuver("x", 8.0, 1300.0, 0, streetNames = listOf("Jalan Tun Razak")))),
        )
        PreviewOverlay(
            place, choices[0], choices, selected = 0, onSelectRoute = {}, busy = false, onBack = {}, onGo = {}, onSimulate = null,
            tolls = listOf(com.roadpulse.app.data.TollEstimate(totalRm = 6.8, complete = true,
                plazas = listOf(com.roadpulse.app.data.TollPlaza("Gombak", status = "PRICED", priceRm = 6.8))), null),
            parkingNearby = listOf(Place("Pavilion Car Park", "240 m", GeoPoint(3.149, 101.713))),
        )
    }

    @Test
    fun reportCardArabic() = shot("ar_6_report_card", RP.Field, arabic = true) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            ReportCard(
                report = reports[0], nowMillis = 1_794_660_000_000L + 12 * 60_000, own = false, busy = false,
                onStillThere = {}, onNotThere = {}, onFlag = {}, onClose = {},
            )
        }
    }

    @Test
    fun reportPickerArabic() = shot("ar_9_report_details", RP.NightSurface, arabic = true) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            ReportPicker(onPick = { _, _, _ -> }, onDismiss = {}, initialType = com.roadpulse.app.data.ReportType.ROAD_CLOSED)
        }
    }

    private val laneState = MapUiState(
        navigating = true, route = route, destination = place,
        currentInstruction = "Keep left to stay on E10.", distanceToTurnMeters = 400.0,
        remainingKm = 7.2, remainingSeconds = 720.0, turnType = 24, speedKmh = 48, speedLimitKph = 60,
        lanes = listOf(
            com.roadpulse.app.data.Lane(16, active = 16), com.roadpulse.app.data.Lane(16, active = 16),
            com.roadpulse.app.data.Lane(2 or 32, valid = 2), com.roadpulse.app.data.Lane(32),
        ),
        schoolAhead = com.roadpulse.app.nav.SchoolAhead("w1", 350.0),
    )

    @Test
    fun drivingWithLanesAndSchool() = shot("3e_driving_lanes_school", RP.Field) {
        DrivingOverlay(state = laneState, narrator = EnglishNarrator, muted = false, onToggleMute = {}, onReport = {}, onStop = {})
    }

    @Test
    fun drivingWithLanesArabic() = shot("ar_3e_driving_lanes", RP.Field, arabic = true) {
        val n = com.roadpulse.app.nav.ArabicNarrator
        DrivingOverlay(state = laneState.copy(currentInstruction = "الزم اليسار للبقاء على E10."), narrator = n,
            muted = false, onToggleMute = {}, onReport = {}, onStop = {})
    }

    private val song = com.roadpulse.app.nav.Track("Sejati", "Wings", playing = true, app = "com.spotify.music")

    @Test
    fun drivingWithMusic() = shot("3f_driving_music", RP.Field) {
        DrivingOverlay(state = laneState.copy(lanes = emptyList(), schoolAhead = null), narrator = EnglishNarrator, muted = false,
            onToggleMute = {}, onReport = {}, onStop = {}, track = song)
    }

    @Test
    fun drivingWithMusicArabic() = shot("ar_3f_driving_music", RP.Field, arabic = true) {
        DrivingOverlay(state = laneState.copy(lanes = emptyList(), schoolAhead = null, currentInstruction = "الزم اليسار للبقاء على E10."),
            narrator = com.roadpulse.app.nav.ArabicNarrator, muted = false, onToggleMute = {}, onReport = {}, onStop = {}, track = song)
    }

    private val now = 1_794_660_000_000L

    @Test
    fun exploreWithPlannedDrive() = shot("1g_explore_planned", RP.Field) {
        ExploreOverlay(
            query = "", results = emptyList(),
            saved = SavedPlaces(
                work = Place("Menara KL", "Kuala Lumpur", GeoPoint(3.15, 101.70)),
                planned = listOf(com.roadpulse.app.data.PlannedDrive(1, Place("Menara KL", "", GeoPoint(3.15, 101.70)), now + 20 * 3_600_000L, 1500.0)),
            ),
            onQueryChange = {}, onClear = {}, onSelect = {}, onOpenSettings = {}, onRecenter = {}, onReport = {}, nowMillis = now,
        )
    }

    @Test
    fun previewWithLeaveLater() = shot("2d_preview_leave_later", RP.Field) {
        PreviewOverlay(destination = place, route = route, busy = false, onBack = {}, onGo = {}, onSimulate = {}, onLeaveLater = {}, onMenu = {}, onSave = {})
    }

    @Test
    fun previewWithPlaceProblemLink() = shot("2e_preview_place_problem", RP.Field) {
        PreviewOverlay(destination = place.copy(directoryId = "o1"), route = route, busy = false, onBack = {}, onGo = {}, onSimulate = null,
            onPlaceProblem = { _, _, _ -> })
    }

    private val sampleProfile = com.roadpulse.app.data.Profile(
        nickname = "Ahmad", points = 215, level = "ROAD_SCOUT", levelIndex = 1, nextLevelPoints = 500,
        stats = com.roadpulse.app.data.ProfileStats(reports = 14, confirmationsReceived = 9, votes = 12),
        badges = listOf("FIRST_REPORT", "REPORTER_10"),
    )

    @Test
    fun menuPage() = shot("13_menu", Color.White) {
        FullPageBody(title = "Menu", onBack = null, onDone = {}) { MenuRows(sampleProfile, {}, {}, {}, {}) }
    }

    @Test
    fun profilePage() = shot("13b_profile_page", Color.White) {
        FullPageBody(title = "Profile", onBack = {}, onDone = {}) {
            PageCard { androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 8.dp)) { ProfileContent(sampleProfile, onSaveNickname = {}) } }
        }
    }

    @Test
    fun profile() = shot("12_profile", Color.White) {
        androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) { ProfileContent(sampleProfile, onSaveNickname = {}) }
    }

    @Test
    fun profileArabic() = shot("ar_12_profile", Color.White, arabic = true) {
        androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) { ProfileContent(sampleProfile, onSaveNickname = {}) }
    }

    @Test
    fun drivingSharing() = shot("3g_driving_sharing", RP.Field) {
        DrivingOverlay(state = laneState.copy(lanes = emptyList(), schoolAhead = null), narrator = EnglishNarrator, muted = false,
            onToggleMute = {}, onReport = {}, onStop = {}, onShare = {}, sharing = true)
    }

    @Test
    fun drivingShareButton() = shot("3h_driving_share_button", RP.Field) {
        DrivingOverlay(state = laneState.copy(lanes = emptyList(), schoolAhead = null), narrator = EnglishNarrator, muted = false,
            onToggleMute = {}, onReport = {}, onStop = {}, onShare = {})
    }

    private val fasterState = laneState.copy(lanes = emptyList(), schoolAhead = null, trafficDelaySeconds = 540.0,
        fasterRoute = com.roadpulse.app.nav.FasterRoute(route, 360.0))

    @Test
    fun drivingFasterRoute() = shot("3i_driving_faster_route", RP.Field) {
        DrivingOverlay(state = fasterState, narrator = EnglishNarrator, muted = false, onToggleMute = {}, onReport = {}, onStop = {})
    }

    @Test
    fun drivingFasterRouteArabic() = shot("ar_3i_driving_faster_route", RP.Field, arabic = true) {
        DrivingOverlay(state = fasterState.copy(currentInstruction = "الزم اليسار للبقاء على E10."), narrator = com.roadpulse.app.nav.ArabicNarrator,
            muted = false, onToggleMute = {}, onReport = {}, onStop = {})
    }

    @Test
    fun drivingMalayWithStatus() = shot("3b_driving_malay_gps_lost", RP.NightSurface) {
        DrivingOverlay(
            state = MapUiState(
                navigating = true, route = route, destination = place,
                currentInstruction = "Masuk ke bulatan dan ambil jalan keluar kedua ke Jalan P18 C1.",
                distanceToTurnMeters = 120.0, remainingKm = 3.4, remainingSeconds = 400.0,
                turnType = 26, navStatus = MalayNarrator.statusGpsLost, speedKmh = 72, speedLimitKph = 60,
            ),
            narrator = MalayNarrator, muted = true, onToggleMute = {}, onReport = {}, onStop = {},
            following = false,
        )
    }

    private val reports = listOf(
        com.roadpulse.app.data.Report("r1", com.roadpulse.app.data.ReportType.CRASH, GeoPoint(3.1466, 101.7108), 1_794_660_000_000L, confirmations = 2),
    )

    @Test
    fun reportCard() = shot("6_report_card", RP.Field) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            ReportCard(
                report = reports[0], nowMillis = 1_794_660_000_000L + 12 * 60_000, own = false, busy = false,
                onStillThere = {}, onNotThere = {}, onFlag = {}, onClose = {},
            )
        }
    }

    @Test
    fun ownReportCard() = shot("7_report_card_own", RP.Field) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            ReportCard(
                report = reports[0].copy(confirmations = 0), nowMillis = 1_794_660_000_000L + 20_000, own = true, busy = false,
                onStillThere = {}, onNotThere = {}, onFlag = {}, onClose = {},
            )
        }
    }

    /** The map marker bitmaps, as the map will draw them. */
    @Test
    fun reportMarkers() = shot("8_report_markers", Color(0xFFE8E4DA)) {
        val markers = rememberReportMarkers()
        androidx.compose.foundation.layout.Row(
            Modifier.padding(24.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            markers.values.forEach { bitmap ->
                androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = null)
            }
        }
    }

    @Test
    fun drivingWithReportAhead() = shot("3c_driving_report_ahead", RP.NightSurface) {
        DrivingOverlay(
            state = MapUiState(
                navigating = true, route = route, destination = place,
                currentInstruction = "Continue on E6.", distanceToTurnMeters = 2400.0,
                remainingKm = 22.0, remainingSeconds = 1100.0, turnType = 8, speedKmh = 92, speedLimitKph = 110,
                reportAhead = com.roadpulse.app.nav.ReportAhead(reports[0].copy(type = com.roadpulse.app.data.ReportType.POLICE), 640.0),
            ),
            narrator = EnglishNarrator, muted = false, onToggleMute = {}, onReport = {}, onStop = {},
        )
    }

    @Test
    fun reportPicker() = shot("9_report_picker", RP.NightSurface) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            ReportPicker(onPick = { _, _, _ -> }, onDismiss = {})
        }
    }

    @Test
    fun reportDetails() = shot("9b_report_details", RP.NightSurface) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            ReportPicker(onPick = { _, _, _ -> }, onDismiss = {}, initialType = com.roadpulse.app.data.ReportType.ROAD_CLOSED)
        }
    }

    @Test
    fun stillThere() = shot("10_still_there", RP.NightSurface) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            StillThereCard(reports[0].copy(type = com.roadpulse.app.data.ReportType.POLICE), onAnswer = {})
        }
    }

    @Test
    fun pinnedPlace() = shot("11_pinned_place", RP.Field) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            PinnedPlaceCard(
                place = Place("Jalan Bukit Bintang 88", "Kuala Lumpur, Malaysia", GeoPoint(3.146, 101.71)),
                canAddStop = true, onDrive = {}, onAddStop = {}, onSave = {}, onPark = {}, onClose = {},
            )
        }
    }

    @Test
    fun previewWithStopAndOptions() = shot("2b_preview_stop_options", RP.Field) {
        PreviewOverlay(
            destination = place, route = route, busy = false, onBack = {}, onGo = {}, onSimulate = null,
            stops = listOf(Place("Petronas Jalan Ampang", "", GeoPoint(3.16, 101.72))),
            optionsSummary = "Motorcycle · avoiding tolls",
        )
    }

    @Test
    fun exploreWithFavouritesAndParking() = shot("1d_explore_favourites", RP.Field) {
        ExploreOverlay(
            query = "", results = emptyList(),
            saved = SavedPlaces(
                home = Place("Jalan Bangsar 12", "Bangsar", GeoPoint(3.13, 101.67)),
                recent = listOf(place),
                favourites = listOf(Place("Gym", "Jalan Telawi, Bangsar", GeoPoint(3.13, 101.67)), Place("Mum's house", "Shah Alam", GeoPoint(3.07, 101.52))),
                parking = com.roadpulse.app.data.ParkedCar(GeoPoint(3.14, 101.69), 1_794_660_000_000L),
            ),
            onQueryChange = {}, onClear = {}, onSelect = {}, onOpenSettings = {}, onRecenter = {}, onReport = {},
            onVoiceSearch = {}, nowMillis = 1_794_660_000_000L + 25 * 60_000,
        )
    }

    @Test
    fun exploreCategories() = shot("1e_explore_categories", RP.Field) {
        ExploreOverlay(
            query = "", results = emptyList(), saved = SavedPlaces(home = place),
            onQueryChange = {}, onClear = {}, onSelect = {}, onOpenSettings = {}, onRecenter = {}, onReport = {},
            onCategory = {},
        )
    }

    @Test
    fun categoryResults() = shot("1f_category_results", RP.Field) {
        ExploreOverlay(
            query = "",
            results = listOf(
                Place("Petronas Jalan Ampang", "650 m", GeoPoint(3.16, 101.72)),
                Place("Shell Jalan Tun Razak", "1.4 km", GeoPoint(3.16, 101.72)),
                Place("Caltex", "2.2 km", GeoPoint(3.16, 101.72)),
            ),
            onQueryChange = {}, onClear = {}, onSelect = {}, onOpenSettings = {}, onRecenter = {}, onReport = {},
            onCategory = {}, categoryTitle = "Fuel nearby",
        )
    }

    @Test
    fun previewWithParking() = shot("2c_preview_parking", RP.Field) {
        PreviewOverlay(
            destination = place, route = route, busy = false, onBack = {}, onGo = {}, onSimulate = null,
            parkingNearby = listOf(Place("Pavilion Car Park", "240 m · paid", GeoPoint(3.149, 101.713))),
        )
    }

    @Test
    fun drivingWithCamera() = shot("3d_driving_camera", RP.NightSurface) {
        DrivingOverlay(
            state = MapUiState(
                navigating = true, route = route, destination = place,
                currentInstruction = "Continue on E6.", distanceToTurnMeters = 5200.0,
                remainingKm = 40.0, remainingSeconds = 1800.0, turnType = 8, speedKmh = 96, speedLimitKph = 90,
                cameraAhead = com.roadpulse.app.nav.CameraAhead("n9", 420.0, 90),
            ),
            narrator = MalayNarrator, muted = false, onToggleMute = {}, onReport = {}, onStop = {},
        )
    }
}
