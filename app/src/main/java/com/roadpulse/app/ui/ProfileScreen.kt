package com.roadpulse.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadpulse.app.data.Profile
import com.roadpulse.app.i18n.strings

/** Points, level, statistics and badges for this (anonymous) device, with an optional nickname. */
@Composable
fun ProfileDialog(profile: Profile?, error: Boolean, onSaveNickname: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.profile) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    profile != null -> ProfileContent(profile, onSaveNickname)
                    error -> Text(strings.profileNeedsServer, color = MaterialTheme.colorScheme.error)
                    else -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = RP.Indigo, trackColor = RP.IndigoLight)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.done) } },
    )
}

@Composable
fun ProfileContent(profile: Profile, onSaveNickname: (String) -> Unit) {
    var name by remember { mutableStateOf(profile.nickname.orEmpty()) }
    LaunchedEffect(profile.nickname) { name = profile.nickname.orEmpty() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).background(RP.IndigoLight, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = RP.Indigo, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(strings.levelName(profile.level), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(strings.points(profile.points), fontSize = 14.sp, color = RP.TextSecondary)
        }
    }
    LinearProgressIndicator(
        progress = { profile.progress() },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp),
        color = RP.Indigo,
        trackColor = RP.IndigoLight,
    )
    val next = profile.nextLevelPoints
    Text(
        if (next == null) strings.topLevel else strings.pointsToNext(next - profile.points, strings.levelName(nextLevelCode(profile.levelIndex))),
        fontSize = 13.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 4.dp),
    )

    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(profile.stats.reports, strings.statReports, Modifier.weight(1f))
        Stat(profile.stats.confirmationsReceived, strings.statConfirmed, Modifier.weight(1f))
        Stat(profile.stats.votes, strings.statVotes, Modifier.weight(1f))
    }

    Text(strings.badges, style = MaterialTheme.typography.labelLarge, color = RP.TextSecondary, modifier = Modifier.padding(top = 16.dp))
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Profile.ALL_BADGES.forEach { code ->
            val earned = code in profile.badges
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(40.dp).background(if (earned) RP.Amber else RP.Field, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (earned) Icons.Filled.EmojiEvents else Icons.Filled.Lock, contentDescription = null,
                        tint = if (earned) RP.AmberDark else RP.TextSecondary, modifier = Modifier.size(20.dp),
                    )
                }
                Text(strings.badge(code), fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp,
                    color = if (earned) Color.Unspecified else RP.TextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }

    Text(strings.nickname, style = MaterialTheme.typography.labelLarge, color = RP.TextSecondary, modifier = Modifier.padding(top = 16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TextButton(enabled = name.trim() != profile.nickname.orEmpty(), onClick = { onSaveNickname(name) }) { Text(strings.save) }
    }
    Text(strings.nicknameHint, fontSize = 12.sp, color = RP.TextSecondary)
    Text(strings.howPointsWork, fontSize = 12.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = RP.IndigoDeep)
        Text(label, fontSize = 11.sp, color = RP.TextSecondary, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

private fun nextLevelCode(index: Int) = listOf("LEARNER", "ROAD_SCOUT", "NAVIGATOR", "ROAD_GUARDIAN", "ROAD_LEGEND").getOrElse(index + 1) { "ROAD_LEGEND" }
