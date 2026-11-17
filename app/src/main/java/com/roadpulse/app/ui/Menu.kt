package com.roadpulse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.IconButton
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

/** The grey page background of the menu and settings screens (grouped list, as on iPhone). */
val PageGrey = Color(0xFFF2F2F7)

/**
 * A full-screen page with a large title, a back arrow (when there is a page before it) and "Done":
 * the same shape as the menu and settings screens on iPhone.
 */
@Composable
fun FullPage(title: String, onBack: (() -> Unit)?, onDone: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onBack ?: onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FullPageBody(title, onBack, onDone, content)
    }
}

/** The page itself, without its window (also what the screenshot tests draw). */
@Composable
fun FullPageBody(title: String, onBack: (() -> Unit)?, onDone: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    run {
        Surface(color = PageGrey, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = RP.Brand)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDone) { Text(strings.done, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = RP.Brand) }
                }
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

/** One white rounded group of rows on a [FullPage]. */
@Composable
fun PageCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content)
    }
}

/** A row that opens another page: icon, label, chevron. */
@Composable
fun PageRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = RP.Brand, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 17.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFC4C4C7))
    }
}

/** The menu: who you are, then profile, planned drives, settings, help. Laid out like iPhone. */
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
    FullPage(title = strings.menu, onBack = null, onDone = onDismiss) { MenuRows(profile, onProfile, onPlanned, onSettings, onHelp) }
}

/** What the menu page holds. */
@Composable
fun MenuRows(profile: Profile?, onProfile: (() -> Unit)?, onPlanned: () -> Unit, onSettings: () -> Unit, onHelp: () -> Unit) {
    run {
        PageCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.size(46.dp).background(RP.Brand, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(profile?.nickname?.takeIf { it.isNotEmpty() } ?: "Jalanna", fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (profile != null) Text(strings.levelName(profile.level) + " · " + strings.points(profile.points), fontSize = 15.sp, color = RP.TextSecondary)
                }
            }
        }
        PageCard {
            if (onProfile != null) PageRow(strings.profile, Icons.Filled.AccountCircle, onProfile)
            PageRow(strings.planDrive, Icons.Filled.Alarm, onPlanned)
            PageRow(strings.settings, Icons.Filled.Settings, onSettings)
            PageRow(strings.helpAndFeedback, Icons.AutoMirrored.Filled.HelpOutline, onHelp)
        }
    }
}

/** Planned drives, with how to plan one. */
@Composable
fun PlannedDrivesDialog(planned: List<PlannedDrive>, onOpen: (PlannedDrive) -> Unit, onRemove: (PlannedDrive) -> Unit, onBack: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val upcoming = planned.filter { it.arriveByMillis > now }
    FullPage(title = strings.plannedDrives, onBack = onBack, onDone = onDismiss) {
        if (upcoming.isNotEmpty()) PageCard {
            upcoming.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpen(d) }.padding(vertical = 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(d.destination.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(d.arriveByMillis))
                        val tomorrow = java.time.Instant.ofEpochMilli(d.arriveByMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() !=
                            java.time.LocalDate.now()
                        Text(strings.plannedFor(time, tomorrow), fontSize = 13.sp, color = RP.TextSecondary)
                    }
                    Icon(Icons.Filled.Close, contentDescription = strings.remove(d.destination.name), tint = RP.TextSecondary,
                        modifier = Modifier.clickable { onRemove(d) })
                }
            }
        }
        // How to plan one (no separate planner screen: plan from a route preview).
        Text(strings.helpTopics.getOrNull(2)?.second.orEmpty(), fontSize = 13.sp, color = RP.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/** Short how-tos and a feedback email. */
@Composable
fun HelpDialog(version: String, onBack: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    FullPage(title = strings.helpAndFeedback, onBack = onBack, onDone = onDismiss) {
        PageCard {
            strings.helpTopics.forEach { (title, body) ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(body, fontSize = 15.sp, color = RP.TextSecondary)
                }
            }
        }
        PageCard {
            Text(strings.sendFeedback, fontSize = 17.sp, color = RP.Brand, modifier = Modifier.fillMaxWidth().clickable {
                val mail = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, "Jalanna feedback ($version)")
                runCatching { context.startActivity(mail) }
            }.padding(vertical = 10.dp))
            Text(strings.version(version), fontSize = 13.sp, color = RP.TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
