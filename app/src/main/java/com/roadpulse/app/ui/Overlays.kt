package com.roadpulse.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.Narrator
import com.roadpulse.app.nav.TurnIcon

enum class ScreenMode { EXPLORE, PREVIEW, DRIVING }

/** Credits required by the map data and tile licences. */
internal const val MAP_CREDITS = "© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap"

// ---------------------------------------------------------------- Explore

@Composable
fun ExploreOverlay(
    query: String,
    results: List<Place>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (Place) -> Unit,
    onOpenSettings: () -> Unit,
    onRecenter: () -> Unit,
    onReport: () -> Unit,
) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
        CircleButton(
            icon = Icons.Filled.Settings,
            description = "Voice guidance settings",
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd),
            content = RP.TextSecondary,
        )

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(Modifier.align(Alignment.End), horizontalAlignment = Alignment.End) {
                CircleButton(icon = Icons.Filled.MyLocation, description = "Show my location", onClick = onRecenter)
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
                        Text("Report", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Island(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (results.isNotEmpty()) {
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(results) { place -> ResultRow(place) { onSelect(place) } }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = RP.Field)
                    }
                    SearchField(query, onQueryChange, onClear)
                    Text(MAP_CREDITS, fontSize = 9.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().fieldBackground().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = RP.Indigo, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Where to?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
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
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear",
                tint = RP.TextSecondary,
                modifier = Modifier.size(20.dp).clickable(onClick = onClear),
            )
        }
    }
}

@Composable
private fun ResultRow(place: Place, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).fieldBackground(),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = RP.Indigo, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (place.description.isNotBlank()) {
                Text(place.description, fontSize = 12.sp, color = RP.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------- Route preview

@Composable
fun PreviewOverlay(
    destination: Place?,
    route: Route?,
    busy: Boolean,
    onBack: () -> Unit,
    onGo: () -> Unit,
    onSimulate: (() -> Unit)?,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
        CircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart),
            content = Color.Black,
        )

        Island(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(destination?.name ?: "Destination", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!destination?.description.isNullOrBlank()) {
                    Text(destination!!.description, fontSize = 13.sp, color = RP.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))

                if (route == null) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = RP.Indigo, trackColor = RP.IndigoLight)
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.5.dp, RP.Indigo), RoundedCornerShape(18.dp))
                            .padding(0.dp),
                    ) {
                        Surface(color = RP.IndigoLight, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(formatDuration(route.timeSeconds), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = RP.IndigoDeep)
                                }
                                Text(
                                    "${formatKm(route.lengthKm)} · arrive ${formatArrival(context, route.timeSeconds)}",
                                    fontSize = 13.sp,
                                    color = RP.Indigo,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onSimulate != null) {
                            CircleButton(
                                icon = Icons.Filled.PlayArrow,
                                description = "Simulate a drive",
                                onClick = onSimulate,
                                container = Color.White,
                                content = Color.Black,
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
                            Text("Go now", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp)) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            TurnIsland(
                turnType = state.turnType,
                distanceLabel = state.distanceToTurnMeters?.let { formatKm(it / 1000) },
                instruction = state.currentInstruction ?: narrator.followRoute,
            )
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
                    state.navStatus?.let { Pill(text = it, color = RP.Amber, contentColor = RP.AmberDark) }
                }
                CircleButton(
                    icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    description = if (muted) "Unmute voice" else "Mute voice",
                    onClick = onToggleMute,
                    container = RP.NightPanel,
                    content = Color.White,
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                state.speedKmh?.let { SpeedRing(speedKmh = it) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!following) {
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
                        description = "Report",
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
            TripIsland(
                arrival = formatArrival(context, seconds),
                remaining = "${formatDuration(seconds)} · ${formatKm(left)}",
                progress = if (total > 0) (1 - left / total).toFloat() else 0f,
                onStop = onStop,
                attribution = MAP_CREDITS,
            )
        }
    }
}

/** Show "Then …" only when the next-but-one turn follows within this distance. */
private const val THEN_HINT_MAX_GAP_M = 300.0
