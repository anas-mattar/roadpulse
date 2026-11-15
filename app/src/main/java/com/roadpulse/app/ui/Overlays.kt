package com.roadpulse.app.ui

import com.roadpulse.app.i18n.strings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.SavedPlaces
import com.roadpulse.app.data.Shortcut
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.Narrator
import com.roadpulse.app.nav.RouteChoice
import com.roadpulse.app.nav.RouteChoices
import com.roadpulse.app.nav.TurnIcon

enum class ScreenMode { EXPLORE, PREVIEW, DRIVING }

/** Credits required by the map data and tile licences. */
internal const val MAP_CREDITS = "© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap"

// ---------------------------------------------------------------- Explore

@Composable
fun ExploreOverlay(
    query: String,
    results: List<Place>,
    /** Where the search was made from: each result shows its distance (not for category lists, which already do). */
    searchFrom: com.roadpulse.app.data.GeoPoint? = null,
    saved: SavedPlaces = SavedPlaces(),
    settingShortcut: Shortcut? = null,
    onShortcut: (Shortcut) -> Unit = {},
    onChangeShortcut: (Shortcut) -> Unit = {},
    onRecent: (Place) -> Unit = {},
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (Place) -> Unit,
    onOpenSettings: () -> Unit,
    onRecenter: () -> Unit,
    onReport: () -> Unit,
    onFavourite: (Place) -> Unit = {},
    onRemoveFavourite: (Place) -> Unit = {},
    /** Shows the parked car on the map. */
    onShowParking: () -> Unit = {},
    onClearParking: () -> Unit = {},
    /** Voice search; null hides the microphone (no speech recognizer on the device). */
    onVoiceSearch: (() -> Unit)? = null,
    nowMillis: Long = System.currentTimeMillis(),
    /** Category buttons under the search box; null hides them (no places server). */
    onCategory: ((com.roadpulse.app.data.PlaceCategory) -> Unit)? = null,
    categoryTitle: String? = null,
    onCloseCategory: () -> Unit = {},
    onPlanned: (com.roadpulse.app.data.PlannedDrive) -> Unit = {},
    onRemovePlanned: (com.roadpulse.app.data.PlannedDrive) -> Unit = {},
    /** Points, level and badges; null hides the button (no server). */
    onOpenProfile: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
        Column(Modifier.align(Alignment.TopEnd), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CircleButton(
                icon = Icons.Filled.Menu,
                description = strings.menu,
                onClick = onOpenSettings,
                content = RP.TextSecondary,
            )
            if (onOpenProfile != null) {
                CircleButton(icon = Icons.Filled.Person, description = strings.profile, onClick = onOpenProfile)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(Modifier.align(Alignment.End), horizontalAlignment = Alignment.End) {
                CircleButton(icon = Icons.Filled.MyLocation, description = strings.showMyLocation, onClick = onRecenter)
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = onReport,
                    shape = RoundedCornerShape(22.dp),
                    color = RP.Amber,
                    contentColor = RP.AmberDark,
                    shadowElevation = 6.dp,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.report, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Island(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (categoryTitle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                            Text(categoryTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.Close, contentDescription = strings.close, tint = RP.TextSecondary,
                                modifier = Modifier.size(20.dp).clickable(onClick = onCloseCategory))
                        }
                    }
                    if (results.isNotEmpty()) {
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(results) { place ->
                                val km = if (categoryTitle == null) searchFrom?.let { com.roadpulse.app.data.Geo.distanceMeters(it, place.location) / 1000 } else null
                                ResultRow(place, distance = km?.let { formatKm(it) }) { onSelect(place) }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = RP.Field)
                    }
                    if (settingShortcut != null) {
                        Text(
                            strings.setShortcutHint(settingShortcut == Shortcut.HOME),
                            fontSize = 13.sp,
                            color = RP.Indigo,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                    }
                    SearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onClear = onClear,
                        placeholder = when (settingShortcut) {
                            Shortcut.HOME -> strings.yourHomeAddress
                            Shortcut.WORK -> strings.yourWorkAddress
                            null -> strings.whereTo
                        },
                        showClear = query.isNotEmpty() || settingShortcut != null,
                        onVoice = if (settingShortcut == null) onVoiceSearch else null,
                    )
                    if (onCategory != null && query.isEmpty() && settingShortcut == null && categoryTitle == null) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CATEGORY_CHIPS.forEach { (category, icon) ->
                                Surface(onClick = { onCategory(category) }, shape = RoundedCornerShape(18.dp), color = RP.IndigoLight, contentColor = RP.Indigo) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(strings.category(category), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (query.isEmpty() && results.isEmpty() && settingShortcut == null && categoryTitle == null) {
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShortcutChip(strings.home, Icons.Filled.Home, saved.home, { onShortcut(Shortcut.HOME) }, { onChangeShortcut(Shortcut.HOME) })
                            ShortcutChip(strings.work, Icons.Filled.Work, saved.work, { onShortcut(Shortcut.WORK) }, { onChangeShortcut(Shortcut.WORK) })
                        }
                        saved.parking?.let { parked ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onShowParking),
                            ) {
                                Icon(Icons.Filled.LocalParking, contentDescription = null, tint = RP.Indigo, modifier = Modifier.size(20.dp))
                                Text(
                                    strings.yourCarParked(com.roadpulse.app.data.reportAge(parked.savedAtMillis, nowMillis, strings)),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                                )
                                Icon(Icons.Filled.Close, contentDescription = strings.forgetParking, tint = RP.TextSecondary,
                                    modifier = Modifier.size(18.dp).clickable(onClick = onClearParking))
                            }
                        }
                        saved.planned.filter { it.arriveByMillis > nowMillis }.forEach { drive ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    ResultRow(
                                        Place(drive.destination.name, plannedLabel(drive, nowMillis), drive.destination.location),
                                        icon = Icons.Filled.Schedule,
                                    ) { onPlanned(drive) }
                                }
                                Icon(Icons.Filled.Close, contentDescription = strings.remove(drive.destination.name), tint = RP.TextSecondary,
                                    modifier = Modifier.size(18.dp).clickable { onRemovePlanned(drive) })
                            }
                        }
                        saved.favourites.forEach { place ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { ResultRow(place, icon = Icons.Filled.Star) { onFavourite(place) } }
                                Icon(Icons.Filled.Close, contentDescription = strings.remove(place.name), tint = RP.TextSecondary,
                                    modifier = Modifier.size(18.dp).clickable { onRemoveFavourite(place) })
                            }
                        }
                        saved.recent.take(if (saved.favourites.size > 2) 1 else 3).forEach { place ->
                            ResultRow(place, icon = Icons.Filled.History) { onRecent(place) }
                        }
                    }
                    Text(MAP_CREDITS, fontSize = 9.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String = strings.whereTo,
    showClear: Boolean = query.isNotEmpty(),
    onVoice: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().fieldBackground().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = RP.Indigo, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                cursorBrush = SolidColor(RP.Indigo),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showClear) {
            Icon(
                Icons.Filled.Close,
                contentDescription = strings.clear,
                tint = RP.TextSecondary,
                modifier = Modifier.size(20.dp).clickable(onClick = onClear),
            )
        } else if (onVoice != null) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = strings.searchByVoice,
                tint = RP.Indigo,
                modifier = Modifier.size(22.dp).clickable(onClick = onVoice),
            )
        }
    }
}

@Composable
private fun ResultRow(place: Place, icon: ImageVector = Icons.Filled.LocationOn, distance: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).fieldBackground(),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = RP.Indigo, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (place.description.isNotBlank()) {
                Text(place.description, fontSize = 12.sp, color = RP.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (distance != null) {
            Text(distance, fontSize = 13.sp, color = RP.TextSecondary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// ---------------------------------------------------------------- Route preview

@Composable
fun PreviewOverlay(
    destination: Place?,
    route: Route?,
    choices: List<Route> = listOfNotNull(route),
    /** Toll estimate per choice (same order); missing entries show "Toll road" for toll routes. */
    tolls: List<com.roadpulse.app.data.TollEstimate?> = emptyList(),
    selected: Int = 0,
    onSelectRoute: (Int) -> Unit = {},
    busy: Boolean,
    onBack: () -> Unit,
    onGo: () -> Unit,
    onSimulate: (() -> Unit)?,
    stops: List<Place> = emptyList(),
    onRemoveStop: (Place) -> Unit = {},
    /** e.g. "Motorcycle · avoiding tolls"; null when the defaults are used. */
    optionsSummary: String? = null,
    parkingNearby: List<Place> = emptyList(),
    onChooseParking: (Place) -> Unit = {},
    /** Plan this drive for later (arrive-by time and a reminder); null hides the button. */
    onLeaveLater: (() -> Unit)? = null,
    /** Live traffic per choice (traffic bar and delay on the cards). */
    traffic: List<com.roadpulse.app.nav.RouteTrafficInfo> = emptyList(),
    /** Go with route [index] (from the list view). */
    onGoRoute: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    var listMode by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
        CircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = strings.back,
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart),
            content = Color.Black,
        )
        if (choices.size > 1) {
            MapListSwitch(listMode, { listMode = it }, Modifier.align(Alignment.TopCenter))
        }

        if (listMode && choices.size > 1) {
            RouteList(
                choices = choices, traffic = traffic, tolls = tolls, selected = selected,
                onSelect = onSelectRoute, onGo = onGoRoute,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            )
        } else Island(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(destination?.name ?: strings.destination, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!destination?.description.isNullOrBlank()) {
                    Text(destination!!.description, fontSize = 13.sp, color = RP.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                stops.forEachIndexed { i, stop ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(strings.stop(i + 1, stop.name), fontSize = 14.sp, color = RP.Indigo, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Close, contentDescription = strings.removeStop, tint = RP.TextSecondary, modifier = Modifier.size(18.dp).clickable { onRemoveStop(stop) })
                    }
                }
                if (optionsSummary != null) {
                    Text(optionsSummary, fontSize = 13.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                if (parkingNearby.isNotEmpty()) {
                    var open by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { open = !open },
                    ) {
                        Icon(Icons.Filled.LocalParking, contentDescription = null, tint = RP.TealDark, modifier = Modifier.size(20.dp))
                        Text(
                            strings.carParksNearDestination(parkingNearby.size),
                            fontSize = 14.sp, color = RP.TealDark, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        Text(if (open) strings.hide else strings.show, fontSize = 13.sp, color = RP.Indigo)
                    }
                    if (open) parkingNearby.forEach { p -> ResultRow(p, icon = Icons.Filled.LocalParking) { onChooseParking(p) } }
                }
                if (stops.isEmpty() && parkingNearby.isEmpty()) {
                    Text(strings.tipAddStop, fontSize = 12.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))

                if (route == null) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = RP.Indigo, trackColor = RP.IndigoLight)
                } else {
                    val labels = RouteChoices.describe(choices.ifEmpty { listOf(route) }, strings)
                    val cards = choices.ifEmpty { listOf(route) }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        cards.forEachIndexed { i, r ->
                            RouteCard(
                                route = r,
                                choice = labels.getOrNull(i),
                                selected = i == selected,
                                single = cards.size == 1,
                                arrival = formatArrival(context, r.timeSeconds + (traffic.getOrNull(i)?.delaySeconds ?: 0.0)),
                                toll = com.roadpulse.app.data.TollClient.label(r.hasToll, tolls.getOrNull(i), strings),
                                traffic = traffic.getOrNull(i),
                                onClick = { onSelectRoute(i) },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onSimulate != null) {
                            CircleButton(
                                icon = Icons.Filled.PlayArrow,
                                description = strings.simulateDrive,
                                onClick = onSimulate,
                                container = Color.White,
                                content = Color.Black,
                                size = 52.dp,
                                border = BorderStroke(1.dp, RP.Field),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        if (onLeaveLater != null) {
                            CircleButton(
                                icon = Icons.Filled.Schedule,
                                description = strings.leaveLater,
                                onClick = onLeaveLater,
                                container = Color.White,
                                content = RP.Indigo,
                                size = 52.dp,
                                border = BorderStroke(1.dp, RP.Field),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Button(
                            onClick = onGo,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RP.Indigo, contentColor = Color.White),
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.goNow, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(MAP_CREDITS, fontSize = 9.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Driving

@Composable
fun DrivingOverlay(
    state: MapUiState,
    narrator: Narrator,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onReport: () -> Unit,
    onStop: () -> Unit,
    following: Boolean = true,
    onRecenter: () -> Unit = {},
    /** Shows the whole remaining route (then Re-center returns to the car). */
    onOverview: () -> Unit = {},
    /** Music playing in another app (null: nothing playing or music controls off). */
    track: com.roadpulse.app.nav.Track? = null,
    onPlayPause: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    onPreviousTrack: () -> Unit = {},
    /** Share this drive as a live link; null hides the button (no server). */
    onShare: (() -> Unit)? = null,
    /** A live link is active: shows "Sharing live" with a stop action. */
    sharing: Boolean = false,
    /** People with the share page open. */
    shareViewers: Int = 0,
    /** Tap on "Sharing live": the shared-drive panel (watchers, stop sharing). */
    onStopSharing: () -> Unit = {},
    onTakeFasterRoute: () -> Unit = {},
    onKeepRoute: () -> Unit = {},
    /** Debug builds: passenger issue report for field tests. */
    onFlag: (() -> Unit)? = null,
    /** Drive sheet: other routes from here, pick one, close the list. */
    onLoadRoutes: () -> Unit = {},
    onPickRoute: (Route) -> Unit = {},
    onCloseRoutes: () -> Unit = {},
    showSpeedometer: Boolean = true,
    limitDisplay: com.roadpulse.app.nav.LimitDisplay = com.roadpulse.app.nav.LimitDisplay.ALWAYS,
    speedingMarginKmh: Int = DEFAULT_SPEEDING_MARGIN_KMH,
) {
    val context = LocalContext.current
    var sheetOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp)) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            TurnIsland(
                turnType = state.turnType,
                distanceLabel = state.distanceToTurnMeters?.let { formatKm(it / 1000) },
                instruction = state.currentInstruction ?: narrator.followRoute,
            )
            val toTurn = state.distanceToTurnMeters
            if (com.roadpulse.app.data.Lane.worthShowing(state.lanes) && toTurn != null && toTurn <= LANES_SHOW_M) {
                LaneStrip(state.lanes, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val gap = state.thenGapMeters
                    if (state.thenType != null && gap != null && gap <= THEN_HINT_MAX_GAP_M) {
                        Pill(
                            text = narrator.thenLabel,
                            icon = TurnIcon.forType(state.thenType).vector(),
                            trailing = formatKm(gap / 1000),
                        )
                    }
                    state.reportAhead?.let { ahead ->
                        Pill(
                            text = narrator.reportName(ahead.report.type),
                            icon = ahead.report.type.icon,
                            trailing = formatKm(ahead.metersAhead / 1000),
                            color = ahead.report.type.color,
                            contentColor = Color.White,
                        )
                    }
                    state.cameraAhead?.let { cam ->
                        Pill(
                            text = narrator.speedCamera + (cam.maxSpeedKph?.let { " $it" } ?: ""),
                            icon = Icons.Filled.CameraAlt,
                            trailing = formatKm(cam.metersAhead / 1000),
                            color = Color(0xFF26215C),
                            contentColor = Color.White,
                        )
                    }
                    state.schoolAhead?.let { school ->
                        Pill(
                            text = narrator.schoolZone,
                            icon = Icons.Filled.School,
                            trailing = formatKm(school.metersAhead / 1000),
                            color = Color(0xFFFAC775),
                            contentColor = Color(0xFF633806),
                        )
                    }
                    state.navStatus?.let { Pill(text = it, color = RP.Amber, contentColor = RP.AmberDark) }
                    if (sharing) {
                        Surface(onClick = onStopSharing, shape = RoundedCornerShape(14.dp), color = RP.TealDark, contentColor = Color.White, shadowElevation = 4.dp) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.sharingLive + " · " + strings.viewers(shareViewers), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (onFlag != null) {
                        Surface(onClick = onFlag, shape = RoundedCornerShape(14.dp), color = Color.White, contentColor = RP.Danger, shadowElevation = 4.dp) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Flag issue", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleButton(
                        icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        description = if (muted) strings.unmuteVoice else strings.muteVoice,
                        onClick = onToggleMute,
                        container = RP.NightPanel,
                        content = Color.White,
                    )
                    if (onShare != null && !sharing) {
                        CircleButton(icon = Icons.Filled.Share, description = strings.shareDrive, onClick = onShare, container = Color.White, content = RP.Indigo)
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                if (showSpeedometer) {
                    state.speedKmh?.let { SpeedRing(speedKmh = it, limitKph = state.speedLimitKph, marginKmh = speedingMarginKmh) }
                }
                val speeding = state.speedKmh?.let { isSpeeding(it, state.speedLimitKph, speedingMarginKmh) } == true
                val showLimit = when (limitDisplay) {
                    com.roadpulse.app.nav.LimitDisplay.ALWAYS -> true
                    com.roadpulse.app.nav.LimitDisplay.WHEN_SPEEDING -> speeding
                    com.roadpulse.app.nav.LimitDisplay.NEVER -> false
                }
                if (showLimit) state.speedLimitKph?.let { SpeedLimitSign(it, Modifier.padding(start = 8.dp, bottom = 4.dp)) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (following) {
                        CircleButton(
                            icon = Icons.Filled.Route,
                            description = strings.overview,
                            onClick = onOverview,
                            container = Color.White,
                            content = RP.Indigo,
                        )
                    } else {
                        // Shown after the driver moves the map; brings the camera back to the car.
                        Surface(
                            onClick = onRecenter,
                            shape = RoundedCornerShape(22.dp),
                            color = Color.White,
                            contentColor = RP.Indigo,
                            shadowElevation = 6.dp,
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(narrator.recenter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                    CircleButton(
                        icon = Icons.Filled.ReportProblem,
                        description = strings.report,
                        onClick = onReport,
                        container = RP.Amber,
                        content = RP.AmberDark,
                        size = 56.dp,
                    )
                }
            }
            val total = state.route?.lengthKm ?: 0.0
            val left = state.remainingKm ?: total
            val seconds = state.remainingSeconds ?: state.route?.timeSeconds ?: 0.0
            state.fasterRoute?.let { FasterRouteCard(it, onTakeFasterRoute, onKeepRoute, Modifier.padding(bottom = 8.dp)) }
            track?.let { MediaBar(it, onPlayPause, onNextTrack, onPreviousTrack, Modifier.padding(bottom = 8.dp)) }
            TripIsland(
                // Arrival includes the delay from live traffic on the route ahead.
                arrival = formatArrival(context, seconds + state.trafficDelaySeconds),
                remaining = "${formatDuration(seconds)} · ${formatKm(left)}" +
                    (state.trafficDelaySeconds.takeIf { it >= 60 }?.let { " · " + strings.trafficDelay(Math.round(it / 60).toInt()) } ?: ""),
                progress = if (total > 0) (1 - left / total).toFloat() else 0f,
                onStop = onStop,
                attribution = MAP_CREDITS,
                trafficPieces = state.trafficPieces,
                onToggle = {
                    sheetOpen = !sheetOpen
                    if (!sheetOpen) onCloseRoutes()
                },
                expandedContent = if (sheetOpen) ({
                    DriveSheet(
                        state = state,
                        onRoutes = onLoadRoutes,
                        onShare = if (onShare != null && !sharing) ({ sheetOpen = false; onShare() }) else null,
                        onStop = onStop,
                        onPickRoute = { sheetOpen = false; onPickRoute(it) },
                        onCloseRoutes = onCloseRoutes,
                    )
                }) else null,
            )
        }
    }
}

/** "Arrive 08:30" or "Arrive 08:30 tomorrow" for the planned-drive row. */
@Composable
private fun plannedLabel(drive: com.roadpulse.app.data.PlannedDrive, nowMillis: Long): String {
    val time = android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date(drive.arriveByMillis))
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(drive.arriveByMillis).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return strings.plannedFor(time, tomorrow = day.isAfter(today))
}

/** "Faster route found · saves 6 min" with Take it / No thanks; closes by itself (keeps the route) after 20 s. */
@Composable
private fun FasterRouteCard(faster: com.roadpulse.app.nav.FasterRoute, onTake: () -> Unit, onKeep: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.runtime.LaunchedEffect(faster) {
        kotlinx.coroutines.delay(20_000)
        onKeep()
    }
    Surface(shape = RoundedCornerShape(20.dp), color = RP.TealDark, contentColor = Color.White, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(strings.fasterRoute(Math.round(faster.savedSeconds / 60).toInt()), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onTake, modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = RP.TealDark)) {
                    Text(strings.takeRoute, fontWeight = FontWeight.SemiBold)
                }
                androidx.compose.material3.OutlinedButton(onClick = onKeep, modifier = Modifier.weight(1f).height(48.dp),
                    border = BorderStroke(1.dp, Color.White), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text(strings.keepRoute)
                }
            }
        }
    }
}

/** Compact music controls above the trip card: title, previous, play/pause, next (large touch targets). */
@Composable
private fun MediaBar(track: com.roadpulse.app.nav.Track, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = RP.NightSurface, contentColor = Color.White, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = RP.Teal, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                track.artist?.let { Text(it, fontSize = 12.sp, color = RP.NightText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            // Media buttons keep their usual order in right-to-left layouts too.
            val previous = strings.previousTrack
            val playLabel = if (track.playing) strings.pause else strings.play
            val next = strings.nextTrack
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = previous)
                    }
                    androidx.compose.material3.IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                        Icon(if (track.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = playLabel, modifier = Modifier.size(30.dp))
                    }
                    androidx.compose.material3.IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = next)
                    }
                }
            }
        }
    }
}

/** Lane arrows appear this close to the turn. */
private const val LANES_SHOW_M = 1500.0

/** Show "Then …" only when the next-but-one turn follows within this distance. */
private const val THEN_HINT_MAX_GAP_M = 300.0

@Composable
private fun RouteCard(
    route: Route,
    choice: RouteChoice?,
    selected: Boolean,
    single: Boolean,
    arrival: String,
    toll: String? = null,
    traffic: com.roadpulse.app.nav.RouteTrafficInfo? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) RP.IndigoLight else RP.Field,
        border = if (selected) BorderStroke(1.5.dp, RP.Indigo) else null,
        modifier = if (single) Modifier.width(320.dp) else Modifier.width(160.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            choice?.tag?.let { tag ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) RP.Indigo else Color.White,
                    contentColor = if (selected) Color.White else RP.TextSecondary,
                ) { Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)) }
            }
            Text(
                formatDuration(route.timeSeconds + (traffic?.delaySeconds ?: 0.0)),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) RP.IndigoDeep else Color.Black,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "${formatKm(route.lengthKm)} · $arrival",
                fontSize = 13.sp,
                color = if (selected) RP.Indigo else RP.TextSecondary,
                maxLines = 1,
            )
            choice?.via?.let {
                Text(strings.via(it), fontSize = 12.sp, color = RP.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            toll?.let {
                Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = RP.AmberDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (traffic != null) TrafficBar(traffic.pieces, Modifier.padding(top = 6.dp), track = Color.White)
        }
    }
}

/** Home/Work shortcut: tap to go (or set if empty), long-press to change. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.ShortcutChip(
    label: String,
    icon: ImageVector,
    place: Place?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = RP.Field, modifier = Modifier.weight(1f)) {
        Row(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = RP.Indigo, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    place?.name ?: strings.setAddress,
                    fontSize = 11.sp,
                    color = if (place == null) RP.Indigo else RP.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Categories offered under the search box, with their icons. */
private val CATEGORY_CHIPS = listOf(
    com.roadpulse.app.data.PlaceCategory.FUEL to Icons.Filled.LocalGasStation,
    com.roadpulse.app.data.PlaceCategory.PARKING to Icons.Filled.LocalParking,
    com.roadpulse.app.data.PlaceCategory.FOOD to Icons.Filled.Restaurant,
    com.roadpulse.app.data.PlaceCategory.EV_CHARGING to Icons.Filled.EvStation,
    com.roadpulse.app.data.PlaceCategory.REST_AREA to Icons.Filled.LocalCafe,
)

