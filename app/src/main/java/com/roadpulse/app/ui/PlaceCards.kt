package com.roadpulse.app.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.roadpulse.app.i18n.strings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadpulse.app.data.Place

/** Actions for a long-pressed point on the map: drive there, add as a stop, save, park here. */
@Composable
fun PinnedPlaceCard(
    place: Place,
    canAddStop: Boolean,
    onDrive: () -> Unit,
    onAddStop: () -> Unit,
    onSave: (String) -> Unit,
    onPark: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** "Add a missing place" (name, kind, note); null hides it (no server). */
    onAddPlace: ((String, com.roadpulse.app.data.CommunityPlaceKind, String) -> Unit)? = null,
) {
    var naming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    Island(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1)
                    Text(place.description, color = RP.TextSecondary, fontSize = 13.sp, maxLines = 1)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = strings.close) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp)) {
                Action(strings.driveHere, Icons.Filled.Navigation, true, onDrive, Modifier.weight(1f))
                Action(strings.addStop, Icons.Filled.AddLocationAlt, canAddStop, onAddStop, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp)) {
                Action(strings.save, Icons.Filled.Star, true, { naming = true }, Modifier.weight(1f))
                Action(strings.parkedHere, Icons.Filled.LocalParking, true, onPark, Modifier.weight(1f))
            }
            if (onAddPlace != null) {
                TextButton(onClick = { adding = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(strings.addPlace, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
    if (adding && onAddPlace != null) {
        AddPlaceDialog(
            initialName = if (place.name == strings.droppedPin) "" else place.name,
            onAdd = { name, kind, note -> adding = false; onAddPlace(name, kind, note) },
            onDismiss = { adding = false },
        )
    }
    if (naming) {
        val pinLabel = strings.droppedPin
        var name by remember { mutableStateOf(if (place.name == pinLabel) "" else place.name) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(strings.savePlace) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(strings.placeNameHint) }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { naming = false; onSave(name) }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(strings.cancel) } },
        )
    }
}

/** Name, kind and an optional note for a place search does not know. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddPlaceDialog(initialName: String, onAdd: (String, com.roadpulse.app.data.CommunityPlaceKind, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<com.roadpulse.app.data.CommunityPlaceKind?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addPlace) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text(strings.placeName) }, singleLine = true)
                Text(strings.whatKindOfPlace, fontSize = 14.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.roadpulse.app.data.CommunityPlaceKind.entries.forEach { k ->
                        androidx.compose.material3.FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(strings.placeKind(k)) })
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it.take(120) }, label = { Text(strings.placeNoteHint) },
                    modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = name.count { it.isLetterOrDigit() } >= 2 && kind != null, onClick = { onAdd(name.trim(), kind!!, note.trim()) }) {
                Text(strings.save)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun Action(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = RP.BrandLight, contentColor = RP.Brand),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
    }
}
