package com.roadpulse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.roadpulse.app.data.PlannedDrive
import com.roadpulse.app.data.Profile
import com.roadpulse.app.i18n.strings

/** The side menu: profile, planned drives, settings, help. */
@Composable
fun MenuPanel(
    profile: Profile?,
    /** Null hides the profile row (no server). */
    onProfile: (() -> Unit)?,
    onPlanned: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxHeight().width(300.dp).align(Alignment.CenterStart).clickable(enabled = false) {},
            ) {
                Column(Modifier.statusBarsPadding().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(RP.IndigoLight, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = RP.Indigo, modifier = Modifier.size(30.dp))
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(profile?.nickname ?: "Jalanna", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (profile != null) Text(strings.levelName(profile.level) + " · " + strings.points(profile.points), fontSize = 13.sp, color = RP.TextSecondary)
                        }
                        Icon(Icons.Filled.Close, contentDescription = strings.close, modifier = Modifier.clickable(onClick = onDismiss))
                    }
                    if (onProfile != null) {
                        TextButton(onClick = onProfile, modifier = Modifier.padding(top = 4.dp)) { Text(strings.profile) }
                    }
                    MenuRow(strings.planDrive, Icons.Filled.Schedule, onPlanned)
                    MenuRow(strings.settings, Icons.Filled.Settings, onSettings)
                    MenuRow(strings.helpAndFeedback, Icons.AutoMirrored.Filled.HelpOutline, onHelp)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = RP.Indigo)
        Text(label, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp))
    }
}

/** Planned drives, with how to plan one. */
@Composable
fun PlannedDrivesDialog(planned: List<PlannedDrive>, onOpen: (PlannedDrive) -> Unit, onRemove: (PlannedDrive) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val upcoming = planned.filter { it.arriveByMillis > now }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.plannedDrives) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                upcoming.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpen(d) }) {
                        Column(Modifier.weight(1f)) {
                            Text(d.destination.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(d.arriveByMillis))
                            val tomorrow = java.time.Instant.ofEpochMilli(d.arriveByMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() !=
                                java.time.LocalDate.now()
                            Text(strings.plannedFor(time, tomorrow), fontSize = 13.sp, color = RP.TextSecondary)
                        }
                        Icon(Icons.Filled.Close, contentDescription = strings.remove(d.destination.name), tint = RP.TextSecondary,
                            modifier = Modifier.clickable { onRemove(d) })
                    }
                }
                // How to plan one (no separate planner screen: plan from a route preview).
                Text(strings.helpTopics.getOrNull(2)?.second.orEmpty(), fontSize = 13.sp, color = RP.TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.done) } },
    )
}

/** Short how-tos and a feedback email. */
@Composable
fun HelpDialog(version: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.helpAndFeedback) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                strings.helpTopics.forEach { (title, body) ->
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(body, fontSize = 14.sp, color = RP.TextSecondary)
                    }
                }
                TextButton(onClick = {
                    val mail = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Jalanna feedback ($version)")
                    runCatching { context.startActivity(mail) }
                }) { Text(strings.sendFeedback) }
                Text(strings.version(version), fontSize = 12.sp, color = RP.TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.done) } },
    )
}
