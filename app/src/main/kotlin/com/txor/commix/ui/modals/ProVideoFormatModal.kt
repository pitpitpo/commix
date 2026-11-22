package com.txor.commix.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.BroadcastFpsStandard
import com.txor.commix.MagneticFpsSnapper
import com.txor.commix.PipelineThroughputEngine
import com.txor.commix.PipelineZone
import com.txor.commix.VideoQualityProfile
import com.txor.commix.ui.CommixHaptics
import com.txor.commix.ui.components.*
import com.txor.commix.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Precision Broadcast Standards, Dynamic Factor-Based Sensor/Encoder Ceilings,
 * 4-Zone Pipeline Throughput Monitor, and Cinema Headless (Blind VF) Support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProVideoFormatModal(
    currentResolution: String,
    currentFpsFloat: Double,
    currentBitrateBps: Int,
    isViewfinderDisabled: Boolean = false,
    availableResolutions: List<String> = emptyList(),
    availableFpsList: List<Double> = emptyList(),
    hapticsEnabled: Boolean = true,
    onResolutionSelected: (String) -> Unit,
    onFpsSelected: (Double) -> Unit,
    onBitrateSelected: (Int) -> Unit,
    onToggleDisableVf: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allResolutions = remember(availableResolutions) {
        PipelineThroughputEngine.getStandardResolutions(availableResolutions)
    }

    val maxFps = remember(currentResolution, isViewfinderDisabled) {
        PipelineThroughputEngine.getMaxFpsForResolution(
            resolution = currentResolution,
            isVfDisabled = isViewfinderDisabled
        )
    }

    val throughput = remember(currentResolution, currentFpsFloat, isViewfinderDisabled, maxFps) {
        PipelineThroughputEngine.evaluate(
            resolution = currentResolution,
            fps = currentFpsFloat,
            isVfDisabled = isViewfinderDisabled,
            hardwareMaxFps = maxFps
        )
    }

    val zoneColor = when (throughput.zone) {
        PipelineZone.OPTIMAL -> CinemaGreen
        PipelineZone.RENDER_LAG -> AppleYellow
        PipelineZone.BLIND_VF_WORK -> CinemaAmber
        PipelineZone.UNSUPPORTED -> RecordingRed
    }

    CommixModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            CommixSheetHeader(
                title = "Pro Video Format & FPS",
                subtitle = "Broadcast standards with live pipeline limits",
                icon = Icons.Default.Videocam,
                iconTint = AppleYellow,
                onDismiss = {
                    CommixHaptics.click(context, hapticsEnabled)
                    onDismiss()
                }
            )

            // 1. PIPELINE THROUGHPUT & OVERLOAD MONITOR
            CommixSectionCard(title = "Pipeline Throughput", accentColor = zoneColor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = throughput.statusTitle,
                        color = zoneColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.1f Mpx/s · %.0f%%",
                            throughput.currentMegaPixelsPerSecond,
                            throughput.overloadPercentage
                        ),
                        color = zoneColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4-Zone Segmented Meter Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(CinemaBlack.copy(alpha = 0.55f))
                        .border(0.8.dp, CinemaGlassBorder, RoundedCornerShape(5.dp))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.40f).fillMaxHeight().background(CinemaGreen.copy(alpha = 0.16f)))
                        Box(modifier = Modifier.weight(0.25f).fillMaxHeight().background(AppleYellow.copy(alpha = 0.16f)))
                        Box(modifier = Modifier.weight(0.25f).fillMaxHeight().background(CinemaAmber.copy(alpha = 0.16f)))
                        Box(modifier = Modifier.weight(0.10f).fillMaxHeight().background(RecordingRed.copy(alpha = 0.16f)))
                    }
                    val fillFraction = (throughput.overloadPercentage / 100.0).toFloat().coerceIn(0.05f, 1.0f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFraction)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(zoneColor.copy(alpha = 0.6f), zoneColor)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("● OPTIMAL", color = CinemaGreen, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    Text("▲ VF LAG", color = AppleYellow, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    Text("⚡ BLIND VF", color = CinemaAmber, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    Text("✕ LIMIT", color = RecordingRed, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = throughput.statusDescription,
                    color = CinemaTextLight,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = throughput.mitigationAction,
                    color = CinemaTextDim,
                    fontSize = 9.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. VIEWFINDER PIPELINE MODE & ISP SOLO PASS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isViewfinderDisabled) CinemaAmber.copy(alpha = 0.16f) else CinemaSurface)
                    .border(
                        1.dp,
                        if (isViewfinderDisabled) CinemaAmber else CinemaGlassBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Viewfinder: ${if (isViewfinderDisabled) "Headless Cinema" else "Dual-Stream Live"}",
                            color = if (isViewfinderDisabled) CinemaAmber else CinemaTextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        CommixBadgePill(
                            text = if (isViewfinderDisabled) "⚡ 100% ISP BOOST" else "● DUAL STREAM",
                            accentColor = if (isViewfinderDisabled) CinemaAmber else CinemaGreen,
                            filled = true
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = if (isViewfinderDisabled) {
                                "Viewfinder preview detached during recording. 100% of ISP bandwidth allocated to encoder for maximum frame rate."
                            } else {
                                "Live viewfinder preview active. ISP bandwidth is shared between preview and encoder streams."
                            },
                            color = CinemaTextDim,
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = isViewfinderDisabled,
                        onCheckedChange = {
                            CommixHaptics.click(context, hapticsEnabled)
                            onToggleDisableVf(it)
                            val newMax = PipelineThroughputEngine.getMaxFpsForResolution(
                                currentResolution,
                                isVfDisabled = it
                            )
                            if (currentFpsFloat > newMax) {
                                onFpsSelected(newMax)
                            }
                        },
                        colors = commixSwitchColors(CinemaAmber)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. MEGAPIXEL SLIDER & REAL SENSOR RESOLUTION
            val snapPoints = remember(allResolutions) {
                allResolutions.map { res ->
                    PipelineThroughputEngine.getResolutionDetail(res)
                }.sortedBy { it.megapixels }
            }
            val minMp = snapPoints.firstOrNull()?.megapixels?.toFloat() ?: 0.3f
            val maxMp = snapPoints.lastOrNull()?.megapixels?.toFloat()?.coerceAtLeast(minMp + 0.1f) ?: 64.0f

            val currentDetail = remember(currentResolution) {
                PipelineThroughputEngine.getResolutionDetail(currentResolution)
            }

            var mpSliderValue by remember(currentResolution) {
                mutableFloatStateOf(currentDetail.megapixels.toFloat())
            }

            val snapThreshold = 0.35f
            val closestSnap = remember(mpSliderValue, snapPoints) {
                snapPoints.minByOrNull { abs(it.megapixels - mpSliderValue) }
            }
            val isNearSnap = closestSnap != null && abs(closestSnap.megapixels - mpSliderValue) <= snapThreshold
            val displayedDetail = remember(mpSliderValue, isNearSnap, closestSnap) {
                if (isNearSnap) {
                    closestSnap!!
                } else {
                    val (cw, ch) = PipelineThroughputEngine.calculateResolutionForMp(mpSliderValue.toDouble())
                    PipelineThroughputEngine.ResolutionDetail(
                        resolution = "${cw}x${ch}",
                        width = cw,
                        height = ch,
                        megapixels = mpSliderValue.toDouble(),
                        standardName = String.format(Locale.US, "%.1fMP CUSTOM SENSOR", mpSliderValue),
                        isRecognizedStandard = false
                    )
                }
            }

            val displayedMaxFps = PipelineThroughputEngine.getMaxFpsForResolution(
                displayedDetail.resolution,
                isVfDisabled = isViewfinderDisabled
            )
            val displayedEval = PipelineThroughputEngine.evaluate(
                resolution = displayedDetail.resolution,
                fps = minOf(currentFpsFloat, displayedMaxFps),
                isVfDisabled = isViewfinderDisabled,
                hardwareMaxFps = displayedMaxFps
            )

            val displayedZoneColor = when (displayedEval.zone) {
                PipelineZone.OPTIMAL -> CinemaGreen
                PipelineZone.RENDER_LAG -> AppleYellow
                PipelineZone.BLIND_VF_WORK -> CinemaAmber
                PipelineZone.UNSUPPORTED -> RecordingRed
            }
            val displayedZoneBadge = when (displayedEval.zone) {
                PipelineZone.OPTIMAL -> "● OPTIMAL"
                PipelineZone.RENDER_LAG -> "▲ VF LOAD"
                PipelineZone.BLIND_VF_WORK -> "⚡ VF OFF"
                PipelineZone.UNSUPPORTED -> "✕ LIMIT"
            }

            CommixSectionCard(title = "Capture Resolution") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        val mpText = String.format(Locale.US, if (displayedDetail.megapixels >= 10.0) "%.1f" else "%.2f", displayedDetail.megapixels)
                        Text(
                            text = mpText,
                            color = CinemaTextLight,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "MP",
                            color = AppleYellow,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${displayedDetail.width}×${displayedDetail.height}",
                            color = CinemaTextDim,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    CommixBadgePill(
                        text = displayedDetail.standardName,
                        accentColor = if (displayedDetail.isRecognizedStandard) CinemaGreen else CinemaAmber
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Max Capable: ${if (displayedMaxFps % 1.0 == 0.0) "${displayedMaxFps.toInt()}" else String.format(Locale.US, "%.1f", displayedMaxFps)} FPS",
                        color = CinemaTextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = displayedZoneBadge,
                        color = displayedZoneColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1fM", minMp),
                        color = CinemaTextDim,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = mpSliderValue.coerceIn(minMp, maxMp),
                        onValueChange = { newMp ->
                            mpSliderValue = newMp
                            val near = snapPoints.minByOrNull { abs(it.megapixels - newMp) }
                            if (near != null && abs(near.megapixels - newMp) <= snapThreshold) {
                                if (near.resolution != currentResolution) {
                                    CommixHaptics.tick(context, hapticsEnabled)
                                    onResolutionSelected(near.resolution)
                                    val newMax = PipelineThroughputEngine.getMaxFpsForResolution(
                                        near.resolution,
                                        isVfDisabled = isViewfinderDisabled
                                    )
                                    if (currentFpsFloat > newMax) {
                                        onFpsSelected(newMax)
                                    }
                                }
                            }
                        },
                        onValueChangeFinished = {
                            val near = snapPoints.minByOrNull { abs(it.megapixels - mpSliderValue) }
                            if (near != null && abs(near.megapixels - mpSliderValue) <= snapThreshold) {
                                mpSliderValue = near.megapixels.toFloat()
                                onResolutionSelected(near.resolution)
                                val newMax = PipelineThroughputEngine.getMaxFpsForResolution(
                                    near.resolution,
                                    isVfDisabled = isViewfinderDisabled
                                )
                                if (currentFpsFloat > newMax) {
                                    onFpsSelected(newMax)
                                }
                            } else {
                                val (cw, ch) = PipelineThroughputEngine.calculateResolutionForMp(mpSliderValue.toDouble())
                                val customRes = "${cw}x${ch}"
                                onResolutionSelected(customRes)
                                val newMax = PipelineThroughputEngine.getMaxFpsForResolution(
                                    customRes,
                                    isVfDisabled = isViewfinderDisabled
                                )
                                if (currentFpsFloat > newMax) {
                                    onFpsSelected(newMax)
                                }
                            }
                            CommixHaptics.click(context, hapticsEnabled)
                        },
                        valueRange = minMp..maxMp,
                        colors = commixSliderColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.1fM", maxMp),
                        color = CinemaTextDim,
                        fontSize = 10.sp
                    )
                }

                // Quick Resolution Snap Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(snapPoints.size) { idx ->
                        val snap = snapPoints[snapPoints.size - 1 - idx]
                        val isSelected = (snap.resolution == currentResolution)
                        val chipMaxFps = PipelineThroughputEngine.getMaxFpsForResolution(
                            snap.resolution,
                            isVfDisabled = isViewfinderDisabled
                        )
                        val evalFps = if (isSelected) currentFpsFloat else minOf(currentFpsFloat, chipMaxFps)
                        val chipEval = PipelineThroughputEngine.evaluate(
                            resolution = snap.resolution,
                            fps = evalFps,
                            isVfDisabled = isViewfinderDisabled,
                            hardwareMaxFps = chipMaxFps
                        )

                        val chipZoneColor = when (chipEval.zone) {
                            PipelineZone.OPTIMAL -> CinemaGreen
                            PipelineZone.RENDER_LAG -> AppleYellow
                            PipelineZone.BLIND_VF_WORK -> CinemaAmber
                            PipelineZone.UNSUPPORTED -> RecordingRed
                        }

                        val chipZoneBadge = when (chipEval.zone) {
                            PipelineZone.OPTIMAL -> "● OPTIMAL"
                            PipelineZone.RENDER_LAG -> "▲ VF LOAD"
                            PipelineZone.BLIND_VF_WORK -> "⚡ VF OFF"
                            PipelineZone.UNSUPPORTED -> "✕ LIMIT"
                        }

                        val shortLabel = when {
                            snap.megapixels >= 50.0 -> String.format(Locale.US, "%.0fMP UHD", snap.megapixels)
                            snap.resolution.contains("4096") -> "4K DCI"
                            snap.resolution.contains("3840") || snap.resolution.contains("4K") -> "4K UHD"
                            snap.resolution.contains("2560") || snap.resolution.contains("1440") -> "1440p QHD"
                            snap.resolution.contains("1920") || snap.resolution.contains("1080") -> "1080p FHD"
                            snap.resolution.contains("1280") || snap.resolution.contains("720") -> "720p HD"
                            snap.resolution.contains("640") || snap.resolution.contains("480") -> "480p SD"
                            snap.megapixels >= 10.0 -> String.format(Locale.US, "%.0fMP", snap.megapixels)
                            snap.megapixels >= 1.0 -> String.format(Locale.US, "%.1fMP", snap.megapixels)
                            else -> snap.resolution
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AppleYellow else CinemaSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) AppleYellow else chipZoneColor.copy(alpha = 0.5f),
                                    RoundedCornerShape(10.dp)
                                )
                                .testTag("video_res_chip_${snap.resolution}")
                                .clickable {
                                    CommixHaptics.click(context, hapticsEnabled)
                                    onResolutionSelected(snap.resolution)
                                    mpSliderValue = snap.megapixels.toFloat()
                                    val newMax = PipelineThroughputEngine.getMaxFpsForResolution(
                                        snap.resolution,
                                        isVfDisabled = isViewfinderDisabled
                                    )
                                    if (currentFpsFloat > newMax) {
                                        onFpsSelected(newMax)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = shortLabel,
                                    color = if (isSelected) CinemaBlack else CinemaTextLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = "${snap.width}x${snap.height}",
                                    color = if (isSelected) CinemaBlack.copy(alpha = 0.72f) else CinemaTextDim,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Max: ${if (chipMaxFps % 1.0 == 0.0) "${chipMaxFps.toInt()}" else String.format(Locale.US, "%.1f", chipMaxFps)} FPS",
                                    color = if (isSelected) CinemaBlack.copy(alpha = 0.8f) else CinemaTextLight.copy(alpha = 0.75f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = chipZoneBadge,
                                    color = if (isSelected) CinemaBlack else chipZoneColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. PRECISION FLOAT FRAME RATE
            val matchedStandard = BroadcastFpsStandard.findClosestStandard(currentFpsFloat, tolerance = 0.05)
            CommixSectionCard(title = "Frame Rate (Broadcast Precision)") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = String.format(Locale.US, if (currentFpsFloat % 1.0 == 0.0) "%.1f" else "%.2f", currentFpsFloat),
                            color = CinemaTextLight,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "FPS",
                            color = AppleYellow,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    CommixBadgePill(
                        text = matchedStandard?.standardName ?: "CUSTOM FLOAT",
                        accentColor = if (matchedStandard != null) CinemaGreen else CinemaAmber
                    )
                }

                var fpsSliderValue by remember(currentFpsFloat) { mutableFloatStateOf(currentFpsFloat.toFloat()) }
                val minFps = 1.0f
                val maxFpsFloat = maxFps.toFloat().coerceAtLeast(1.0f)
                val sliderMin = if (maxFpsFloat <= 1.0f) 0.5f else minFps
                val sliderMax = maxFpsFloat

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, if (sliderMin % 1f == 0f) "%.0f" else "%.1f", sliderMin),
                        color = CinemaTextDim,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = fpsSliderValue.coerceIn(sliderMin, sliderMax),
                        onValueChange = { newFps ->
                            fpsSliderValue = newFps
                            onFpsSelected(round(newFps.toDouble() * 100.0) / 100.0)
                        },
                        onValueChangeFinished = {
                            CommixHaptics.tick(context, hapticsEnabled)
                            val snapResult = MagneticFpsSnapper.snap(
                                fpsSliderValue.toDouble(),
                                snapThreshold = 0.85
                            )
                            val chosenFps = if (snapResult.didSnap) {
                                snapResult.fps
                            } else {
                                round(fpsSliderValue.toDouble() * 10.0) / 10.0
                            }
                            onFpsSelected(chosenFps)
                        },
                        valueRange = sliderMin..sliderMax,
                        colors = commixSliderColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.US, if (sliderMax % 1f == 0f) "%.0f" else "%.1f", sliderMax),
                        color = CinemaTextDim,
                        fontSize = 10.sp
                    )
                }

                // Dynamic Sensor & Broadcast FPS Snap Chips
                val dynamicFpsList = remember(maxFps) {
                    PipelineThroughputEngine.getDynamicFpsChipsForResolution(maxFps)
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(dynamicFpsList.size) { idx ->
                        val fpsVal = dynamicFpsList[idx]
                        val isSelected = (abs(fpsVal - currentFpsFloat) < 0.05)
                        val isExceedingHardware = fpsVal > (maxFps + 0.05)

                        val chipEval = PipelineThroughputEngine.evaluate(
                            resolution = currentResolution,
                            fps = fpsVal,
                            isVfDisabled = isViewfinderDisabled,
                            hardwareMaxFps = maxFps
                        )

                        val chipColor = when {
                            isExceedingHardware -> RecordingRed.copy(alpha = 0.28f)
                            chipEval.zone == PipelineZone.BLIND_VF_WORK -> CinemaAmber.copy(alpha = 0.28f)
                            else -> CinemaSurfaceElevated
                        }

                        val chipLabel = if (fpsVal % 1.0 == 0.0) {
                            "${fpsVal.toInt()}"
                        } else {
                            String.format(Locale.US, "%.2f", fpsVal)
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AppleYellow else chipColor)
                                .border(
                                    0.6.dp,
                                    if (isSelected) AppleYellow else CinemaGlassBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    CommixHaptics.click(context, hapticsEnabled)
                                    onFpsSelected(fpsVal)
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = chipLabel,
                                color = if (isSelected) CinemaBlack else if (isExceedingHardware) RecordingRed else CinemaTextLight,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. BITRATE & STORAGE FOOTPRINT
            val activeProfile = VideoQualityProfile.fromBitrate(currentBitrateBps)
            CommixSectionCard(title = "Encoder Bitrate · ${activeProfile.label} (${activeProfile.formattedEstimate(1)})") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VideoQualityProfile.profiles.forEach { profile ->
                        val isSelected = (profile.bitrateBps == currentBitrateBps)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) AppleYellow else CinemaSurfaceElevated)
                                .border(
                                    0.6.dp,
                                    if (isSelected) AppleYellow else CinemaGlassBorder,
                                    RoundedCornerShape(9.dp)
                                )
                                .clickable {
                                    CommixHaptics.click(context, hapticsEnabled)
                                    onBitrateSelected(profile.bitrateBps)
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "${profile.bitrateBps / 1_000_000}M",
                                color = if (isSelected) CinemaBlack else CinemaTextLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
