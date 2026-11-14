package com.roadpulse.app.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxHeight
import android.graphics.Bitmap
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadpulse.app.data.Report
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.data.reportAge

val ReportType.icon: ImageVector
    get() = when (this) {
        ReportType.POLICE -> Icons.Filled.LocalPolice
        ReportType.CRASH -> Icons.Filled.CarCrash
        ReportType.HAZARD -> Icons.Filled.Warning
        ReportType.TRAFFIC_JAM -> Icons.Filled.Traffic
        ReportType.ROAD_CLOSED -> Icons.Filled.Block
    }

val ReportType.color: Color get() = Color(android.graphics.Color.parseColor(colorHex))

/** Map image name for a report type (registered on the map style by [rememberReportMarkers]). */
fun markerImageName(type: ReportType) = "report-${type.name}"

/** Round map markers (coloured disc, white ring, white icon) for every report type, as bitmaps. */
@Composable
fun rememberReportMarkers(): Map<ReportType, Bitmap> {
    val density = LocalDensity.current
    // Fixed list, so the remember calls inside the loop always run in the same order.
    val painters = ReportType.entries.associateWith { rememberVectorPainter(it.icon) }
    return remember(density) { painters.mapValues { (type, painter) -> drawMarker(painter, type.color, density) } }
}

private fun drawMarker(painter: VectorPainter, color: Color, density: Density): Bitmap {
    val sizePx = with(density) { 34.dp.toPx() }
    val ring = with(density) { 3.dp.toPx() }
    val iconPx = sizePx * 0.56f
    val image = ImageBitmap(sizePx.toInt(), sizePx.toInt())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(sizePx, sizePx)) {
        val center = Offset(sizePx / 2, sizePx / 2)
        drawCircle(Color(0x33000000), radius = sizePx / 2, center = center.copy(y = center.y + ring / 3)) // soft shadow
        drawCircle(Color.White, radius = sizePx / 2 - ring / 3, center = center)
        drawCircle(color, radius = sizePx / 2 - ring, center = center)
        translate((sizePx - iconPx) / 2, (sizePx - iconPx) / 2) {
            with(painter) { draw(Size(iconPx, iconPx), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
    return image.asAndroidBitmap()
}

/** Card for a tapped report: what it is, how old, and Still there / Not there / Flag. */
@Composable
fun ReportCard(
    report: Report,
    nowMillis: Long,
    own: Boolean,
    busy: Boolean,
    onStillThere: () -> Unit,
    onNotThere: () -> Unit,
    onFlag: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Island(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(report.type.color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(report.type.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    val detail = com.roadpulse.app.data.ReportDetails.label(report.type, report.subtype)
                    Text(
                        if (detail != null) "${report.type.label} · $detail" else report.type.label,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    val details = buildList {
                        add("Reported ${reportAge(report.createdAtMillis, nowMillis)}")
                        if (report.confirmations > 0) add("${report.confirmations} confirmed")
                    }
                    Text(details.joinToString(" · "), color = RP.TextSecondary, fontSize = 14.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            if (own) {
                Text(
                    "Your report. Other drivers can confirm it.",
                    color = RP.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp, end = 8.dp),
                )
            } else {
                Text("Is it still there?", fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp),
                ) {
                    Button(
                        onClick = onStillThere,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = RP.Indigo),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Still there")
                    }
                    OutlinedButton(onClick = onNotThere, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Not there")
                    }
                }
                TextButton(onClick = onFlag, enabled = !busy) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = RP.Danger, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Report abuse", color = RP.Danger)
                }
            }
        }
    }
}

/**
 * Report panel. Step 1: five large tiles. Step 2: optional details as large buttons; tapping one
 * sends at once, "Send" sends without details. Road closures also offer an expected duration.
 * While driving it closes by itself after [autoCloseMillis] with no tap, so it never stays over the map.
 */
@Composable
fun ReportPicker(
    onPick: (ReportType, String?, Int?) -> Unit,
    onDismiss: () -> Unit,
    autoCloseMillis: Long? = null,
    modifier: Modifier = Modifier,
    initialType: ReportType? = null,
) {
    var chosen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(initialType) }
    var duration by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    if (autoCloseMillis != null) {
        // Restarts on every step, so the driver always has the full time to tap.
        androidx.compose.runtime.LaunchedEffect(chosen, duration) {
            kotlinx.coroutines.delay(autoCloseMillis)
            onDismiss()
        }
    }
    Island(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val type = chosen
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(type?.label ?: "Report", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            if (type == null) {
                ReportType.entries.chunked(3).forEach { row ->
                    TileRow(row.size) { i -> BigTile(row[i].label, row[i].icon, row[i].color) { chosen = row[i] } }
                }
            } else {
                val details = com.roadpulse.app.data.ReportDetails.subtypes[type].orEmpty()
                if (type == ReportType.ROAD_CLOSED) {
                    Text("How long?", color = RP.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        com.roadpulse.app.data.ReportDetails.closureDurations.forEach { (minutes, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = duration == minutes,
                                onClick = { duration = minutes },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                details.chunked(3).forEach { row ->
                    TileRow(row.size, height = 76.dp) { i ->
                        BigTile(row[i].second, null, type.color, small = true) { onPick(type, row[i].first, duration) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { chosen = null; duration = null }) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onPick(type, null, duration) },
                        colors = ButtonDefaults.buttonColors(containerColor = RP.Indigo),
                        modifier = Modifier.height(52.dp),
                    ) { Text("Send", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun TileRow(count: Int, height: androidx.compose.ui.unit.Dp = 104.dp, tile: @Composable androidx.compose.foundation.layout.RowScope.(Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(height)) {
        repeat(count) { i -> tile(i) }
        // Keep tiles the same width on a shorter last row.
        repeat(3 - count) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BigTile(
    label: String,
    icon: ImageVector?,
    color: Color,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = color,
        contentColor = Color.White,
        modifier = Modifier.weight(1f).fillMaxHeight(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(38.dp))
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (small) 15.sp else 15.sp,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = if (icon != null) 6.dp else 0.dp),
            )
        }
    }
}

/** Shown right after driving past a report: one big tap to confirm or dismiss it. */
@Composable
fun StillThereCard(report: Report, onAnswer: (Boolean?) -> Unit, modifier: Modifier = Modifier) {
    Island(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(report.type.color, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(report.type.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(
                    "${report.type.label}: still there?",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                TextButton(onClick = { onAnswer(null) }) { Text("Skip") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Button(
                    onClick = { onAnswer(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = RP.Indigo),
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Still there", fontSize = 17.sp)
                }
                OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f).height(64.dp)) {
                    Icon(Icons.Filled.ThumbDown, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Not there", fontSize = 17.sp)
                }
            }
        }
    }
}
