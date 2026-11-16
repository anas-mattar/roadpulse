package com.roadpulse.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadpulse.app.data.Route
import com.roadpulse.app.i18n.strings
import com.roadpulse.app.nav.RouteChoices
import com.roadpulse.app.nav.TrafficPiece

val TrafficSlow = Color(0xFFF59F00)
val TrafficJam = Color(0xFFD20F39)

/**
 * The route as a thin bar: slow stretches amber, jams red. [progress] (0..1) marks how far the
 * car has come (driving), or null in the preview.
 */
@Composable
fun TrafficBar(pieces: List<TrafficPiece>, modifier: Modifier = Modifier, progress: Float? = null, track: Color = RP.Field) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val y = size.height / 2
        val w = size.width
        val stroke = 6.dp.toPx()
        drawLine(track, Offset(0f, y), Offset(w, y), strokeWidth = stroke, cap = StrokeCap.Round)
        progress?.let { p -> drawLine(RP.Teal, Offset(0f, y), Offset(w * p.coerceIn(0f, 1f), y), strokeWidth = stroke, cap = StrokeCap.Round) }
        for (piece in pieces) {
            val from = (piece.from * w).toFloat()
            val to = maxOf((piece.to * w).toFloat(), from + 3.dp.toPx())
            drawLine(if (piece.level == "JAM") TrafficJam else TrafficSlow, Offset(from, y), Offset(to, y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        progress?.let { p -> drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(w * p.coerceIn(0f, 1f), y)) }
    }
}

/** Map / List switch above the route choices. */
@Composable
fun MapListSwitch(list: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 6.dp, modifier = modifier) {
        Row(Modifier.padding(4.dp)) {
            listOf(false to strings.mapTab, true to strings.listTab).forEach { (isList, label) ->
                val on = isList == list
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) RP.Brand else RP.TextSecondary,
                    modifier = Modifier
                        .background(if (on) RP.BrandLight else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickable { onChange(isList) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Route choices as a list: one large card each, with arrival, traffic bar, toll and Go. */
@Composable
fun RouteList(
    choices: List<Route>,
    traffic: List<com.roadpulse.app.nav.RouteTrafficInfo>,
    tolls: List<com.roadpulse.app.data.TollEstimate?>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onGo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val labels = RouteChoices.describe(choices, strings)
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEachIndexed { i, r ->
            val delay = traffic.getOrNull(i)?.delaySeconds ?: 0.0
            Surface(
                onClick = { onSelect(i) },
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                border = if (i == selected) BorderStroke(2.dp, RP.Brand) else null,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatDuration(r.timeSeconds + delay), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatArrival(context, r.timeSeconds + delay)} · ${formatKm(r.lengthKm)}",
                                fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            )
                        }
                        Button(
                            onClick = { onGo(i) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RP.Brand, contentColor = Color.White),
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.goShort, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    labels.getOrNull(i)?.via?.let {
                        Text(strings.via(it), fontSize = 14.sp, color = RP.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    com.roadpulse.app.data.TollClient.label(r.hasToll, tolls.getOrNull(i), strings)?.let {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF4C4F69), contentColor = Color.White, modifier = Modifier.padding(top = 6.dp)) {
                            Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    TrafficBar(traffic.getOrNull(i)?.pieces.orEmpty(), Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

/**
 * The drive sheet pulled up: where you are going, the route with traffic and progress, and
 * Routes / Share / Stop. With [alternatives] it lists other routes to switch to.
 */
@Composable
fun DriveSheet(
    state: MapUiState,
    onRoutes: () -> Unit,
    onShare: (() -> Unit)?,
    onStop: () -> Unit,
    onPickRoute: (Route) -> Unit,
    onCloseRoutes: () -> Unit,
) {
    val context = LocalContext.current
    val route = state.route
    val total = route?.lengthKm ?: 0.0
    val left = state.remainingKm ?: total
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        state.destination?.let {
            Text(strings.shareTo(it.name), fontSize = 13.sp, color = RP.NightText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        route?.let { r ->
            RouteChoices.describe(listOf(r), strings).firstOrNull()?.via?.let {
                Text(strings.via(it), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        TrafficBar(state.trafficPieces, Modifier.padding(vertical = 12.dp), progress = if (total > 0) (1 - left / total).toFloat() else 0f, track = RP.NightButton)

        val alternatives = state.driveAlternatives
        if (alternatives == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton(strings.otherRoutes, Icons.Filled.AltRoute, RP.NightButton, Modifier.weight(1f), onRoutes)
                if (onShare != null) SheetButton(strings.shareDrive, Icons.Filled.Share, RP.NightButton, Modifier.weight(1f), onShare)
                SheetButton(strings.notificationStop, Icons.Filled.Close, RP.Danger, Modifier.weight(1f), onStop)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.otherRoutes, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, contentDescription = strings.close, modifier = Modifier.size(22.dp).clickable(onClick = onCloseRoutes))
            }
            if (state.loadingAlternatives) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 10.dp), color = RP.Teal, trackColor = RP.NightButton)
            } else if (alternatives.isEmpty()) {
                Text(strings.noOtherRoutes, fontSize = 14.sp, color = RP.NightText, modifier = Modifier.padding(vertical = 8.dp))
            }
            val labels = RouteChoices.describe(alternatives, strings)
            alternatives.forEachIndexed { i, r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(RP.NightButton, RoundedCornerShape(16.dp)).padding(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${formatDuration(r.timeSeconds)} · ${formatArrival(context, r.timeSeconds)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(formatKm(r.lengthKm), labels.getOrNull(i)?.via?.let { strings.via(it) }).joinToString(" · "),
                            fontSize = 13.sp, color = RP.NightText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { onPickRoute(r) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RP.Brand, contentColor = Color.White),
                    ) { Text(strings.goShort, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SheetButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = color, contentColor = Color.White, modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Grabber at the top of a sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.padding(top = 6.dp).size(width = 40.dp, height = 4.dp).background(Color(0x66FFFFFF), RoundedCornerShape(2.dp)))
}
