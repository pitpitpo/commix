package com.txor.commix.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = AppleYellow,
    onPrimary = CinemaBlack,
    primaryContainer = AppleYellow.copy(alpha = 0.18f),
    onPrimaryContainer = AppleYellow,
    secondary = CinemaCyan,
    onSecondary = CinemaBlack,
    secondaryContainer = CinemaCyan.copy(alpha = 0.18f),
    onSecondaryContainer = CinemaCyan,
    tertiary = CinemaGreen,
    onTertiary = CinemaBlack,
    background = CinemaBlack,
    onBackground = CinemaTextLight,
    surface = CinemaCardDark,
    onSurface = CinemaTextLight,
    surfaceVariant = CinemaSurface,
    onSurfaceVariant = CinemaTextDim,
    surfaceContainer = CinemaCardDark,
    surfaceContainerHigh = CinemaSurface,
    surfaceContainerHighest = CinemaSurfaceElevated,
    outline = CinemaBorder,
    outlineVariant = CinemaGlassBorder,
    error = RecordingRed,
    onError = CinemaTextLight
)

val CommixShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun CommixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = CommixShapes,
        content = content
    )
}
