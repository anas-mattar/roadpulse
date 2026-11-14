package com.roadpulse.app.ui

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
) {
    var naming by remember { mutableStateOf(false) }
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
        }
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

@Composable
private fun Action(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = RP.IndigoLight, contentColor = RP.Indigo),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
    }
}
