package com.roadpulse.app.ui

import com.roadpulse.app.i18n.strings
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Alarm
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
import androidx.compose.foundation.layout.imePadding
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
internal const val MAP_CREDITS = "© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap · Places: Overture Maps Foundation"

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
    // Laid out like the iPhone app: menu top left; Report (left) and my location (right) just above
    // the card; in the card the search box first, then saved places as chips, categories, planned
    // drives and recent places, or the results.
    // Typing a search with results: the card grows to the full height above the keyboard and the
    // map buttons step aside, so the results are easy to read. (The search box keeps its place in
    // the layout, so the keyboard stays open while this changes.)
    val searching = results.isNotEmpty() && query.isNotEmpty() && categoryTitle == null
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp)) {
        if (!searching) {
            CircleButton(icon = Icons.Filled.Menu, description = strings.menu, onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopStart))
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (!searching) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                Surface(
                    onClick = onReport,
                    shape = RoundedCornerShape(22.dp),
                    color = RP.Amber,
                    contentColor = RP.AmberDark,
                    shadowElevation = 6.dp,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.report, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                CircleButton(icon = Icons.Filled.MyLocation, description = strings.showMyLocation, onClick = onRecenter)
            }

            Island(if (searching) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), radius = 22.dp) {
                Column(Modifier.padding(14.dp)) {
                    SearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onClear = onClear,
                        placeholder = when (settingShortcut) {
                            Shortcut.HOME -> strings.yourHomeAddress
                            Shortcut.WORK -> strings.yourWorkAddress
                            null -> strings.whereTo
                        },
                        showClear = query.isNotEmpty() || settingShortcut != null || categoryTitle != null,
                        onVoice = if (settingShortcut == null) onVoiceSearch else null,
                        leadingIcon = if (settingShortcut == null) Icons.Filled.Search else Icons.Filled.Home,
                    )
                    if (settingShortcut != null) {
                        Text(
                            strings.setShortcutHint(settingShortcut == Shortcut.HOME),
                            fontSize = 13.sp,
                            color = RP.Brand,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    if (categoryTitle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 10.dp)) {
                            Text(categoryTitle, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.Close, contentDescription = strings.close, tint = RP.TextSecondary,
                                modifier = Modifier.size(20.dp).clickable(onClick = onCloseCategory))
                        }
                    }
                    if (results.isNotEmpty()) {
                        LazyColumn(if (searching) Modifier.weight(1f).padding(top = 6.dp) else Modifier.heightIn(max = 220.dp).padding(top = 6.dp)) {
                            items(results) { place ->
                                val km = if (categoryTitle == null) searchFrom?.let { com.roadpulse.app.data.Geo.distanceMeters(it, place.location) / 1000 } else null
                                ResultRow(place, distance = km?.let { formatKm(it) }) { onSelect(place) }
                            }
                        }
                    }
                    if (query.isEmpty() && results.isEmpty() && settingShortcut == null && categoryTitle == null) {
                        // Saved places as chips: the parked car, Home, Work, favourites. Press and hold for more.
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            saved.parking?.let { parked ->
                                PlaceChip(
                                    strings.yourCarParked(com.roadpulse.app.data.reportAge(parked.savedAtMillis, nowMillis, strings)),
                                    Icons.Filled.DirectionsCar, onClick = onShowParking, menuLabel = strings.forgetParking, onMenu = onClearParking,
                                )
                            }
                            PlaceChip(
                                if (saved.home == null) "${strings.home} · ${strings.setAddress}" else strings.home, Icons.Filled.Home,
                                onClick = { onShortcut(Shortcut.HOME) }, menuLabel = strings.setAddress, onMenu = { onChangeShortcut(Shortcut.HOME) },
                            )
                            PlaceChip(
                                if (saved.work == null) "${strings.work} · ${strings.setAddress}" else strings.work, Icons.Filled.Work,
                                onClick = { onShortcut(Shortcut.WORK) }, menuLabel = strings.setAddress, onMenu = { onChangeShortcut(Shortcut.WORK) },
                            )
                            saved.favourites.forEach { place ->
                                PlaceChip(place.name, Icons.Filled.Star, onClick = { onFavourite(place) },
                                    menuLabel = strings.remove(place.name), onMenu = { onRemoveFavourite(place) })
                            }
                        }
                        if (onCategory != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CATEGORY_CHIPS.forEach { (category, _) ->
                                    Surface(
                                        onClick = { onCategory(category) }, shape = RoundedCornerShape(50), color = Color.Transparent, contentColor = RP.Brand,
                                        border = BorderStroke(1.dp, RP.Brand.copy(alpha = 0.4f)),
                                    ) {
                                        Text(strings.category(category), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                                    }
                                }
                            }
                        }
                        val planned = saved.planned.filter { it.arriveByMillis > nowMillis }.take(3)
                        if (planned.isNotEmpty()) {
                            Text(strings.plannedDrives, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RP.TextSecondary,
                                modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                        }
                        planned.forEach { drive ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    ResultRow(
                                        Place(drive.destination.name, plannedLabel(drive, nowMillis), drive.destination.location),
                                        icon = Icons.Filled.Alarm,
                                    ) { onPlanned(drive) }
                                }
                                Icon(Icons.Filled.Close, contentDescription = strings.remove(drive.destination.name), tint = RP.TextSecondary,
                                    modifier = Modifier.size(18.dp).clickable { onRemovePlanned(drive) })
                            }
                        }
                        if (saved.recent.isNotEmpty()) Spacer(Modifier.height(4.dp))
                        saved.recent.take(4).forEach { place ->
                            ResultRow(place, icon = Icons.Filled.History) { onRecent(place) }
                        }
                    }
                    Text(MAP_CREDITS, fontSize = 9.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
            }
        }
    }
}

/** A saved place as a chip (tinted pill with an icon). Press and hold opens a one-line menu, as on iPhone. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceChip(text: String, icon: ImageVector, onClick: () -> Unit, menuLabel: String, onMenu: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(RP.Brand.copy(alpha = 0.12f))
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = RP.Brand, modifier = Modifier.size(16.dp))
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RP.Brand, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text(menuLabel) }, onClick = { menu = false; onMenu() })
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
    leadingIcon: ImageVector = Icons.Filled.Search,
) {
    Row(
        Modifier.fillMaxWidth().fieldBackground().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(leadingIcon, contentDescription = null, tint = RP.Brand, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, fontSize = 16.sp, color = RP.TextSecondary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                cursorBrush = SolidColor(RP.Brand),
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
                tint = RP.Brand,
                modifier = Modifier.size(22.dp).clickable(onClick = onVoice),
            )
        }
    }
}

@Composable
private fun ResultRow(place: Place, icon: ImageVector = Icons.Filled.LocationOn, distance: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = RP.TextSecondary, modifier = Modifier.size(22.dp))
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
    /** "Report a problem with this place" (kind, use my position, note); null hides it. */
    onPlaceProblem: ((com.roadpulse.app.data.PlaceProblemKind, Boolean, String) -> Unit)? = null,
    /** Opens the menu (top left, as on the map screen). */
    onMenu: (() -> Unit)? = null,
    /** Saves the destination as a favourite under the given name; null hides the star. */
    onSave: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var listMode by remember { mutableStateOf(false) }
    var showPlaceProblem by remember { mutableStateOf(false) }
    if (showPlaceProblem && destination != null && onPlaceProblem != null) {
        PlaceProblemDialog(destination.name, onSend = { kind, here, note -> showPlaceProblem = false; onPlaceProblem(kind, here, note) },
            onDismiss = { showPlaceProblem = false })
    }
    var askName by remember { mutableStateOf(false) }
    if (askName && destination != null && onSave != null) {
        var name by remember { mutableStateOf(destination.name) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askName = false },
            title = { Text(strings.savePlace) },
            text = { androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text(strings.placeNameHint) }) },
            confirmButton = { androidx.compose.material3.TextButton(enabled = name.isNotBlank(), onClick = { askName = false; onSave(name.trim()) }) { Text(strings.save) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askName = false }) { Text(strings.cancel) } },
        )
    }
    // Laid out like the iPhone app: menu top left, Map/List switch top centre; in the card the
    // search box with the destination (X clears the route), then route options, stops, the route
    // choices side by side, and one row: save, leave later, (try the drive), Go now.
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
        if (onMenu != null) {
            CircleButton(icon = Icons.Filled.Menu, description = strings.menu, onClick = onMenu, modifier = Modifier.align(Alignment.TopStart))
        } else {
            CircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, description = strings.back, onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart), content = Color.Black)
        }
        if (choices.size > 1) {
            MapListSwitch(listMode, { listMode = it }, Modifier.align(Alignment.TopCenter))
        }

        if (listMode && choices.size > 1) {
            RouteList(
                choices = choices, traffic = traffic, tolls = tolls, selected = selected,
                onSelect = onSelectRoute, onGo = onGoRoute,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            )
        } else Island(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), radius = 22.dp) {
            Column(Modifier.padding(14.dp)) {
                // The search box, holding the destination; the X (or a tap) goes back to searching.
                Row(
                    Modifier.fillMaxWidth().fieldBackground().clickable(onClick = onBack).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = RP.Brand, modifier = Modifier.size(20.dp))
                    Text(destination?.name ?: strings.destination, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Icon(Icons.Filled.Cancel, contentDescription = strings.clear, tint = RP.TextSecondary, modifier = Modifier.size(20.dp))
                }
                if (optionsSummary != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = RP.TextSecondary, modifier = Modifier.size(14.dp))
                        Text(optionsSummary, fontSize = 12.sp, color = RP.TextSecondary, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                stops.forEachIndexed { i, stop ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Filled.Flag, contentDescription = null, tint = ROUTE_ORANGE, modifier = Modifier.size(18.dp))
                        Text(strings.stop(i + 1, stop.name), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Icon(Icons.Filled.RemoveCircle, contentDescription = strings.removeStop, tint = RP.TextSecondary,
                            modifier = Modifier.size(20.dp).clickable { onRemoveStop(stop) })
                    }
                }
                if (stops.isEmpty()) {
                    Text(strings.tipAddStop, fontSize = 12.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 10.dp))
                    // Only for places from the server's own data, which a moderator can correct.
                    if (onPlaceProblem != null && destination?.directoryId != null) {
                        Text(
                            strings.reportPlaceProblem, fontSize = 12.sp, color = RP.Brand,
                            modifier = Modifier.padding(top = 6.dp).clickable { showPlaceProblem = true },
                        )
                    }
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
                        Text(if (open) strings.hide else strings.show, fontSize = 13.sp, color = RP.Brand)
                    }
                    if (open) parkingNearby.forEach { p -> ResultRow(p, icon = Icons.Filled.LocalParking) { onChooseParking(p) } }
                }
                Spacer(Modifier.height(10.dp))

                if (route == null) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = RP.Brand, trackColor = RP.BrandLight)
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
                                toll = com.roadpulse.app.data.TollClient.label(r.hasToll, tolls.getOrNull(i), strings),
                                traffic = traffic.getOrNull(i),
                                onClick = { onSelectRoute(i) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onSave != null) SmallRoundButton(Icons.Filled.StarBorder, strings.savePlace) { askName = true }
                        if (onLeaveLater != null) SmallRoundButton(Icons.Filled.Alarm, strings.leaveLater, onLeaveLater)
                        if (onSimulate != null) SmallRoundButton(Icons.Filled.PlayArrow, strings.simulateDrive, onSimulate)
                        Button(
                            onClick = onGo,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RP.Brand, contentColor = Color.White),
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

/** A round grey button with a dark icon (save, leave later, try the drive), as on iPhone. */
@Composable
private fun SmallRoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(RP.Field).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Color.Black, modifier = Modifier.size(22.dp)) }
}

/** Orange for stops and tolls, as on iPhone. */
private val ROUTE_ORANGE = Color(0xFFE8830C)

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
                    if (sharing) {
                        Surface(onClick = onStopSharing, shape = RoundedCornerShape(14.dp), color = RP.TealDark, contentColor = Color.White, shadowElevation = 4.dp) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.sharingLive + " · " + strings.viewers(shareViewers), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
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
                            color = Color(0xFF08343A),
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
                    // Same four buttons, in the same order, as on iPhone.
                    CircleButton(
                        icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        description = if (muted) strings.unmuteVoice else strings.muteVoice,
                        onClick = onToggleMute,
                    )
                    CircleButton(icon = Icons.Filled.MyLocation, description = strings.showMyLocation, onClick = onRecenter)
                    CircleButton(icon = Icons.Filled.Map, description = strings.overview, onClick = onOverview)
                    if (onShare != null && !sharing) {
                        CircleButton(icon = Icons.Filled.Share, description = strings.shareDrive, onClick = onShare)
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
                CircleButton(
                    icon = Icons.Filled.Feedback,
                    description = strings.report,
                    onClick = onReport,
                    container = RP.Amber,
                    content = RP.AmberDark,
                    size = 56.dp,
                )
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
            val minutes = Math.round(faster.savedSeconds / 60).toInt()
            Text(if (faster.closureAhead) strings.closureDetour(-minutes) else strings.fasterRoute(minutes), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
    toll: String? = null,
    traffic: com.roadpulse.app.nav.RouteTrafficInfo? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) RP.BrandLight else RP.Field,
        border = if (selected) BorderStroke(1.5.dp, RP.Brand) else null,
        modifier = Modifier.width(160.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            choice?.tag?.let { tag ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) RP.Brand else Color.White,
                    contentColor = if (selected) Color.White else RP.TextSecondary,
                ) { Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)) }
            }
            Text(
                formatDuration(route.timeSeconds + (traffic?.delaySeconds ?: 0.0)),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(formatKm(route.lengthKm), fontSize = 13.sp, color = RP.TextSecondary, maxLines = 1)
            choice?.via?.let {
                Text(strings.via(it), fontSize = 12.sp, color = RP.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            toll?.let {
                Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ROUTE_ORANGE, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Icon(icon, contentDescription = null, tint = RP.Brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    place?.name ?: strings.setAddress,
                    fontSize = 11.sp,
                    color = if (place == null) RP.Brand else RP.TextSecondary,
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


/** "Report a problem with this place": what is wrong, whether it is where the driver is now, and a note. */
@Composable
private fun PlaceProblemDialog(
    placeName: String, onSend: (com.roadpulse.app.data.PlaceProblemKind, Boolean, String) -> Unit, onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf<com.roadpulse.app.data.PlaceProblemKind?>(null) }
    var here by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val moving = kind == com.roadpulse.app.data.PlaceProblemKind.WRONG_POSITION
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.reportPlaceProblem) },
        text = {
            Column {
                Text(placeName, fontWeight = FontWeight.Medium)
                Text(strings.whatIsWrong, fontSize = 14.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 12.dp))
                com.roadpulse.app.data.PlaceProblemKind.entries.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { kind = k }) {
                        androidx.compose.material3.RadioButton(selected = kind == k, onClick = null)
                        Text(strings.placeProblem(k), modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp))
                    }
                }
                if (moving) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { here = !here }) {
                        androidx.compose.material3.Checkbox(checked = here, onCheckedChange = null)
                        Text(strings.placeIsWhereIAm, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp))
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = note, onValueChange = { note = it.take(200) },
                    label = { Text(if (moving) strings.placeProblemNoteHint else strings.placeNoteHint) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = com.roadpulse.app.data.PlaceProblemsClient.canSend(kind, moving && here, note),
                onClick = { onSend(kind!!, moving && here, note.trim()) },
            ) { Text(strings.send) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
