package com.roadpulse.app.ui

import com.roadpulse.app.i18n.strings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadpulse.app.nav.TurnIcon

/** A floating rounded panel over the map. */
@Composable
fun Island(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    contentColor: Color = Color.Black,
    radius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = color,
        contentColor = contentColor,
        shadowElevation = 10.dp,
        content = content,
    )
}

/** A round floating button. */
@Composable
fun CircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Color.White,
    content: Color = RP.Indigo,
    size: Dp = 44.dp,
    border: BorderStroke? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = container,
        contentColor = content,
        shadowElevation = if (border == null) 6.dp else 0.dp,
        border = border,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(size * 0.48f))
        }
    }
}

/** A small rounded label, e.g. "Then ↰ in 200 m" or a status message. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RP.IndigoDeep,
    contentColor: Color = RP.IndigoSoft,
    icon: ImageVector? = null,
    trailing: String? = null,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = color, contentColor = contentColor, shadowElevation = 4.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 13.sp)
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.padding(horizontal = 4.dp).size(16.dp))
            if (trailing != null) Text(trailing, fontSize = 13.sp)
        }
    }
}

fun TurnIcon.vector(): ImageVector = when (this) {
    TurnIcon.DEPART -> Icons.Filled.Navigation
    TurnIcon.STRAIGHT -> Icons.Filled.Straight
    TurnIcon.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    TurnIcon.RIGHT -> Icons.Filled.TurnRight
    TurnIcon.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    TurnIcon.UTURN_RIGHT -> Icons.Filled.UTurnRight
    TurnIcon.UTURN_LEFT -> Icons.Filled.UTurnLeft
    TurnIcon.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
    TurnIcon.LEFT -> Icons.Filled.TurnLeft
    TurnIcon.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    TurnIcon.KEEP_RIGHT -> Icons.Filled.ForkRight
    TurnIcon.KEEP_LEFT -> Icons.Filled.ForkLeft
    TurnIcon.RAMP_RIGHT -> Icons.Filled.RampRight
    TurnIcon.RAMP_LEFT -> Icons.Filled.RampLeft
    TurnIcon.MERGE -> Icons.Filled.Merge
    TurnIcon.ROUNDABOUT -> Icons.Filled.RoundaboutLeft // clockwise roundabouts (left-hand traffic)
    TurnIcon.ARRIVE -> Icons.Filled.SportsScore
    TurnIcon.FERRY -> Icons.Filled.DirectionsBoat
}

/** Big turn card at the top while driving. */
@Composable
fun TurnIsland(turnType: Int?, distanceLabel: String?, instruction: String, modifier: Modifier = Modifier) {
    Island(modifier = modifier.fillMaxWidth(), color = RP.Indigo, contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                TurnIcon.forType(turnType).vector(),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                if (distanceLabel != null) {
                    Text(distanceLabel, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
                }
                Text(
                    instruction,
                    color = RP.IndigoSoft,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // Arabic instructions read right to left, even with Latin street names inside.
                    style = androidx.compose.material3.LocalTextStyle.current.copy(
                        textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                    ),
                )
            }
        }
    }
}

/** True when noticeably above the posted limit (small tolerance for speedometer/GPS noise). */
fun isSpeeding(speedKmh: Int, limitKph: Int?, marginKmh: Int = DEFAULT_SPEEDING_MARGIN_KMH): Boolean =
    limitKph != null && speedKmh > limitKph + marginKmh

const val DEFAULT_SPEEDING_MARGIN_KMH = 3

/** Round speed-limit sign (white with a red border). */
@Composable
fun SpeedLimitSign(limitKph: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(46.dp), shape = CircleShape, color = Color.White, border = BorderStroke(5.dp, RP.Danger), shadowElevation = 4.dp) {
        Box(contentAlignment = Alignment.Center) {
            Text("$limitKph", color = Color(0xFF2C2C2A), fontSize = if (limitKph >= 100) 15.sp else 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Speed in a ring that fills towards the limit (or [scaleKmh]); red when speeding. */
@Composable
fun SpeedRing(
    speedKmh: Int,
    modifier: Modifier = Modifier,
    limitKph: Int? = null,
    scaleKmh: Int = limitKph ?: 120,
    marginKmh: Int = DEFAULT_SPEEDING_MARGIN_KMH,
) {
    val speeding = isSpeeding(speedKmh, limitKph, marginKmh)
    Box(modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(68.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            drawCircle(RP.NightSurface)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(RP.NightPanel, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val sweep = (speedKmh.coerceIn(0, scaleKmh) / scaleKmh.toFloat()) * 360f
            drawArc(if (speeding) RP.Danger else RP.Teal, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$speedKmh", color = if (speeding) Color(0xFFF09595) else Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            Text(strings.kmh, color = RP.NightText, fontSize = 10.sp, lineHeight = 10.sp)
        }
    }
}

/** Trip summary while driving: arrival, time and distance left, progress, stop. */
@Composable
fun TripIsland(
    arrival: String,
    remaining: String,
    progress: Float,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    attribution: String,
) {
    Island(modifier = modifier.fillMaxWidth(), color = RP.NightPanel, contentColor = Color.White) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(arrival, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                    Text(remaining, color = RP.NightText, fontSize = 13.sp)
                }
                CircleButton(
                    icon = Icons.Filled.Close,
                    description = strings.stopNavigation,
                    onClick = onStop,
                    container = RP.Danger,
                    content = Color.White,
                    size = 44.dp,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(5.dp),
                color = RP.Teal,
                trackColor = RP.NightButton,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(attribution, color = RP.NightText, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Grey rounded field background used inside light islands. */
fun Modifier.fieldBackground(): Modifier = this.background(RP.Field, RoundedCornerShape(18.dp))
