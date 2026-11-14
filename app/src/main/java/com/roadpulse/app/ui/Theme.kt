package com.roadpulse.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** RoadPulse palette: deep indigo for routes and guidance, amber for reports, teal for progress. */
object RP {
    val Indigo = Color(0xFF534AB7)
    val IndigoDark = Color(0xFF3C3489)
    val IndigoDeep = Color(0xFF26215C)
    val IndigoLight = Color(0xFFEEEDFE)
    val IndigoSoft = Color(0xFFCECBF6)
    val IndigoBright = Color(0xFF7F77DD)
    val Amber = Color(0xFFFAC775)
    val AmberDark = Color(0xFF633806)
    val Teal = Color(0xFF5DCAA5)
    val TealDark = Color(0xFF0F6E56)
    val Danger = Color(0xFFE24B4A)
    val Field = Color(0xFFF1EFE8)
    val TextSecondary = Color(0xFF5F5E5A)

    // Night driving panels
    val NightSurface = Color(0xFF2C2C2A)
    val NightPanel = Color(0xFF444441)
    val NightButton = Color(0xFF5F5E5A)
    val NightText = Color(0xFFB4B2A9)
}

@Composable
fun RoadPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RP.Indigo,
            onPrimary = Color.White,
            primaryContainer = RP.IndigoLight,
            onPrimaryContainer = RP.IndigoDeep,
            secondary = RP.TealDark,
            surface = Color.White,
            surfaceVariant = RP.Field,
            onSurfaceVariant = RP.TextSecondary,
            error = RP.Danger,
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
