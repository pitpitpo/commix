package com.txor.commix.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Cinema Mode Carousel supporting all 11 camera modes with unified glass/accent tokens.
 */
@Composable
fun ModeCarousel(
    modes: List<String>,
    activeMode: String,
    onModeSelected: (String) -> Unit,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val activeIndex = modes.indexOf(activeMode).coerceAtLeast(0)

    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("mode_carousel")
    ) {
        itemsIndexed(
            items = modes,
            key = { _, mode -> mode }
        ) { index, mode ->
            val isSelected = (mode == activeMode)
            val icon = getModeIcon(mode)
            val activeAccent = if (mode == "SPATIAL") CinemaCyan else AppleYellow

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) activeAccent.copy(alpha = 0.20f) else CinemaSurface.copy(alpha = 0.85f)
                    )
                    .border(
                        width = if (isSelected) 1.3.dp else 0.8.dp,
                        color = if (isSelected) activeAccent else CinemaGlassBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("mode_pill_${mode.lowercase().replace(" ", "_").replace("-", "_")}")
                    .clickable {
                        if (!isSelected) {
                            onModeSelected(mode)
                            if (hapticsEnabled) {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                                    }
                                } catch (_: Throwable) {}
                            }
                            scope.launch {
                                listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = mode,
                        tint = if (isSelected) activeAccent else CinemaGrey,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = mode,
                        color = if (isSelected) activeAccent else CinemaTextLight,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                        letterSpacing = 0.55.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private fun getModeIcon(mode: String): ImageVector {
    return when (mode) {
        "NIGHT" -> Icons.Default.Bedtime
        "LONG EXP" -> Icons.Default.AutoAwesome
        "SLOW-MO" -> Icons.Default.Speed
        "VIDEO" -> Icons.Default.Videocam
        "CINEMATIC" -> Icons.Default.Movie
        "PHOTO" -> Icons.Default.CameraAlt
        "PRO" -> Icons.Default.Tune
        "TIME-LAPSE" -> Icons.Default.Timer
        "AUTOMATION" -> Icons.Default.ElectricBolt
        "PANORAMA" -> Icons.Default.PanoramaHorizontal
        "SPATIAL" -> Icons.Default.ViewInAr
        else -> Icons.Default.CameraAlt
    }
}
