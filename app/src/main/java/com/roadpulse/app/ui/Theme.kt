package com.roadpulse.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Jalanna palette: the icon's teal for routes and guidance, amber for reports, green for progress. */
object RP {
    val Brand = Color(0xFF0B7F78)
    val BrandDark = Color(0xFF0A5A5C)
    val BrandDeep = Color(0xFF08343A)
    val BrandLight = Color(0xFFE3F5F2)
    val BrandSoft = Color(0xFFB5E3DC)
    val BrandBright = Color(0xFF3FC1B0)
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
            primary = RP.Brand,
            onPrimary = Color.White,
            primaryContainer = RP.BrandLight,
            onPrimaryContainer = RP.BrandDeep,
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
