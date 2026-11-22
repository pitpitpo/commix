package com.txor.commix.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.ui.components.*
import com.txor.commix.ui.theme.*
import java.util.Locale

/**
 * Megapixel Limits & Sensor Resolution Driver bottom sheet using unified Cinema tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MegapixelLimitsModal(
    currentMp: Double,
    maxMp: Double = 64.0,
    isHiRes: Boolean = false,
    onMpSelected: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val minBound = 1.0f
    val maxBound = maxMp.toFloat().coerceAtLeast(minBound)
    var sliderValue by remember(currentMp, maxMp) { mutableFloatStateOf(currentMp.toFloat().coerceIn(minBound, maxBound)) }
    val presets = remember(maxMp) {
        val standard = listOf(5.0, 8.0, 12.0, 16.0, 24.0, 48.0, 64.0, 108.0, 200.0)
        (standard.filter { it < maxMp - 0.5 } + maxMp).distinct().sorted()
    }

    CommixModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            CommixSheetHeader(
                title = "Megapixel Limits",
                subtitle = if (isHiRes) "Hi-Res full-sensor readout active" else "Hardware sensor resolution target",
                icon = Icons.Default.HighQuality,
                badgeText = "MAX ${maxMp.toInt()}MP",
                onDismiss = onDismiss
            )

            CommixSectionCard(title = "Hardware Sensor Presets") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { preset ->
                        val isSel = kotlin.math.abs(sliderValue - preset.toFloat()) < 0.6f
                        val isPresetMax = kotlin.math.abs(preset - maxMp) < 0.5
                        val label = when {
                            isPresetMax -> "${preset.toInt()}MP MAX"
                            preset == 16.0 -> "16MP BINNED"
                            else -> "${preset.toInt()}MP"
                        }
                        val activeBg = if (isPresetMax) AppleYellow else CinemaBlue

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) activeBg else CinemaSurfaceElevated)
                                .border(
                                    0.8.dp,
                                    if (isSel) activeBg else CinemaGlassBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    sliderValue = preset.toFloat()
                                    onMpSelected(preset)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSel && isPresetMax) CinemaBlack else CinemaTextLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CommixSectionCard(title = "Sensor Megapixel Stepper") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Target Capture Resolution",
                        color = CinemaTextDim,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f MP", sliderValue),
                        color = AppleYellow,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${minBound.toInt()}M",
                        color = CinemaTextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = sliderValue.coerceIn(minBound, maxBound),
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { onMpSelected(sliderValue.toDouble()) },
                        valueRange = minBound..maxBound,
                        colors = commixSliderColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${maxMp.toInt().coerceAtLeast(1)}M",
                        color = CinemaTextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
