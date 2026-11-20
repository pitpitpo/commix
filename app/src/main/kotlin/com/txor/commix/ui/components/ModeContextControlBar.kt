package com.txor.commix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.model.CameraUiState
import com.txor.commix.ui.CommixViewModel
import com.txor.commix.ui.theme.*
import java.util.Locale

/**
 * Dedicated per-mode contextual control strip rendered above the mode carousel.
 * Gives every single camera mode instant, 1-tap access to its real end-to-end engine parameters
 * without hunting through nested menus.
 */
@Composable
fun ModeContextControlBar(
    state: CameraUiState,
    viewModel: CommixViewModel,
    onOpenProTab: (ProControlTab) -> Unit,
    onOpenFormatModal: () -> Unit,
    onOpenSlateSheet: () -> Unit,
    onOpenAutomationModal: () -> Unit,
    onOpenKeyframeModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag("mode_control_bar")
    ) {
        when (state.activeMode) {
            "PHOTO" -> {
                item {
                    ContextChip(
                        icon = if (state.isProcessZeroActive) Icons.Default.Grain else Icons.Default.AutoAwesome,
                        label = if (state.isProcessZeroActive) "P0 PURE" else "AETHER",
                        isActive = true,
                        accentColor = if (state.isProcessZeroActive) CinemaCyan else AppleYellow,
                        onClick = { viewModel.onToggleProcessZero() }
                    )
                }
                item {
                    val hdrLabel = when {
                        !state.aebEnabled -> "HDR OFF"
                        state.aebHdrFusion -> "HDR+ ${state.aebShots}×"
                        else -> "AEB ${state.aebShots}×"
                    }
                    ContextChip(
                        icon = Icons.Default.HdrOn,
                        label = hdrLabel,
                        isActive = state.aebEnabled,
                        onClick = { viewModel.onCycleAebQuick() }
                    )
                }
                item {
                    val enhLabel = when (state.photoEnhanceAlgo.lowercase()) {
                        "aether_master" -> "AETHER PRO"
                        "super_res" -> "SUPER RES"
                        "clarity" -> "CLARITY"
                        "detail" -> "DETAIL"
                        else -> "ENH OFF"
                    }
                    ContextChip(
                        icon = Icons.Default.HighQuality,
                        label = enhLabel,
                        isActive = state.photoEnhanceAlgo.lowercase() != "off",
                        onClick = { viewModel.onCyclePhotoEnhanceAlgo() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.CenterFocusStrong,
                        label = if (state.focusStackEnabled) "F-STACK ${state.focusStackSlices}" else "F-STACK",
                        isActive = state.focusStackEnabled,
                        onClick = { viewModel.onToggleFocusStack() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.LensBlur,
                        label = "OPTICS",
                        isActive = state.lensCorrectionEnabled,
                        onClick = { viewModel.onToggleLensCorrection() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Palette,
                        label = if (state.lutLook == "none") "LUT" else state.lutLook.replace("_", " ").uppercase(),
                        isActive = state.lutLook != "none",
                        onClick = { onOpenProTab(ProControlTab.LUT) }
                    )
                }
            }

            "PRO" -> {
                item {
                    val isoStr = if (state.iso > 0) "ISO ${state.iso}" else "ISO A"
                    ContextChip(
                        label = isoStr,
                        isActive = state.iso > 0,
                        monospace = true,
                        onClick = { onOpenProTab(ProControlTab.ISO) }
                    )
                }
                item {
                    val sStr = if (state.shutterNs <= 0L) "S AUTO"
                    else if (state.shutterNs >= 1_000_000_000L) String.format(Locale.US, "%.1fs", state.shutterNs / 1e9)
                    else "1/${(1_000_000_000L / state.shutterNs.coerceAtLeast(1L))}"
                    ContextChip(
                        label = sStr,
                        isActive = state.shutterNs > 0L,
                        monospace = true,
                        onClick = { onOpenProTab(ProControlTab.SHUTTER) }
                    )
                }
                item {
                    ContextChip(
                        label = String.format(Locale.US, "EV %+.1f", state.evStops),
                        isActive = kotlin.math.abs(state.evStops) > 0.05f,
                        monospace = true,
                        onClick = { onOpenProTab(ProControlTab.EV) }
                    )
                }
                item {
                    ContextChip(
                        label = if (state.wbKelvin > 0) "${state.wbKelvin}K" else "AWB",
                        isActive = state.wbKelvin > 0,
                        monospace = true,
                        onClick = { onOpenProTab(ProControlTab.WB) }
                    )
                }
                item {
                    ContextChip(
                        label = if (state.focusDiopters >= 0f) String.format(Locale.US, "MF %.1fD", state.focusDiopters) else "AF",
                        isActive = state.focusDiopters >= 0f,
                        monospace = true,
                        onClick = { onOpenProTab(ProControlTab.FOCUS) }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Lock,
                        label = if (state.aeAfLocked) "LOCKED" else "AE/AF",
                        isActive = state.aeAfLocked,
                        accentColor = CinemaAmber,
                        onClick = { viewModel.onToggleAeAfLock() }
                    )
                }
                item {
                    ContextChip(
                        label = if (state.isProcessZeroActive) "P0 ON" else "P0",
                        isActive = state.isProcessZeroActive,
                        accentColor = CinemaCyan,
                        onClick = { viewModel.onToggleProcessZero() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.ShowChart,
                        label = "SCOPES",
                        isActive = state.showScopes || state.showWaveform || state.showHistogram,
                        onClick = { onOpenProTab(ProControlTab.SCOPES) }
                    )
                }
            }

            "VIDEO" -> {
                item {
                    val codecLabel = when (state.videoCodec.lowercase()) {
                        "all_i" -> "ALL-I"
                        "avc", "h264" -> "H.264"
                        else -> "HEVC 10B"
                    }
                    ContextChip(
                        icon = Icons.Default.Videocam,
                        label = codecLabel,
                        isActive = true,
                        onClick = { viewModel.onCycleVideoCodec() }
                    )
                }
                item {
                    ContextChip(
                        label = "${state.videoBitrateBps / 1_000_000} Mbps",
                        isActive = state.videoBitrateBps >= 100_000_000,
                        monospace = true,
                        onClick = { viewModel.onCycleVideoBitrate() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Speed,
                        label = "${state.videoFps} FPS",
                        isActive = state.videoFps >= 60,
                        monospace = true,
                        onClick = { onOpenFormatModal() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Shield,
                        label = "EIS ${state.eisMode.replace("_", " ").uppercase()}",
                        isActive = state.eisMode != "off",
                        onClick = { viewModel.onCycleEis() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.VisibilityOff,
                        label = if (state.viewfinderDisabled) "BLIND VF ON" else "BLIND VF",
                        isActive = state.viewfinderDisabled,
                        accentColor = CinemaAmber,
                        onClick = { viewModel.onToggleViewfinderDisabled() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Mic,
                        label = if (state.uncompressedAudio) "384K AAC" else "AUDIO",
                        isActive = state.uncompressedAudio || state.audioLowCut,
                        onClick = { onOpenProTab(ProControlTab.AUDIO) }
                    )
                }
            }

            "CINEMATIC" -> {
                item {
                    val matteStr = if (state.cinemaMatteRatio == "off") "MATTE OFF" else state.cinemaMatteRatio
                    ContextChip(
                        icon = Icons.Default.Movie,
                        label = matteStr,
                        isActive = state.cinemaMatteRatio != "off",
                        onClick = { viewModel.onCycleCinemaMatte() }
                    )
                }
                item {
                    val anaStr = if (state.anamorphicRatio <= 1.01f) "ANA 1.0×" else String.format(Locale.US, "ANA %.2f×", state.anamorphicRatio)
                    ContextChip(
                        label = anaStr,
                        isActive = state.anamorphicRatio > 1.01f,
                        monospace = true,
                        onClick = { viewModel.onCycleAnamorphic() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Lock,
                        label = "180°",
                        isActive = state.shutterAngleLocked,
                        monospace = true,
                        onClick = { viewModel.onToggleShutter180Lock() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Palette,
                        label = if (state.toneCurve != "neutral") state.toneCurve.replace("_", " ").uppercase() else "LOG / CURVE",
                        isActive = state.toneCurve != "neutral",
                        onClick = { onOpenProTab(ProControlTab.CURVE) }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.MovieFilter,
                        label = "SLATE R${state.slateRoll}·S${state.slateScene}",
                        isActive = false,
                        accentColor = CinemaCyan,
                        onClick = { onOpenSlateSheet() }
                    )
                }
                item {
                    ContextChip(
                        label = "${state.videoBitrateBps / 1_000_000}M",
                        isActive = state.videoBitrateBps >= 150_000_000,
                        monospace = true,
                        onClick = { viewModel.onCycleVideoBitrate() }
                    )
                }
            }

            "NIGHT" -> {
                item {
                    ContextChip(
                        icon = Icons.Default.Nightlight,
                        label = "${state.nightFrameCount} FRAMES",
                        isActive = true,
                        monospace = true,
                        onClick = { viewModel.onCycleNightFrames() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Star,
                        label = if (state.nightAstroAlignment) "ASTRO ALIGN" else "HANDHELD",
                        isActive = state.nightAstroAlignment,
                        accentColor = CinemaCyan,
                        onClick = { viewModel.onToggleNightAstro() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Tonality,
                        label = "TONE ${state.nightTone.uppercase()}",
                        isActive = state.nightTone != "neutral",
                        onClick = { viewModel.onCycleNightTone() }
                    )
                }
                item {
                    ContextChip(
                        label = String.format(Locale.US, "EV %+.1f", state.evStops),
                        isActive = kotlin.math.abs(state.evStops) > 0.05f,
                        monospace = true,
                        onClick = { onOpenProTab(ProControlTab.EV) }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Lock,
                        label = if (state.aeAfLocked) "TRIPOD LOCK" else "AE/AF",
                        isActive = state.aeAfLocked,
                        onClick = { viewModel.onToggleAeAfLock() }
                    )
                }
            }

            "LONG EXP" -> {
                item {
                    val styleName = state.longExposureStyle.replace("_", " ").uppercase()
                    ContextChip(
                        icon = Icons.Default.BlurOn,
                        label = styleName,
                        isActive = true,
                        onClick = { viewModel.onCycleLongExposureStyle() }
                    )
                }
                item {
                    ContextChip(
                        label = "${state.longExposureFrames} STACK",
                        isActive = true,
                        monospace = true,
                        onClick = { viewModel.onCycleLongExposureFrames() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.FilterBAndW,
                        label = if (state.ndFilter == "off") "e-ND OFF" else "e-${state.ndFilter.uppercase()}",
                        isActive = state.ndFilter != "off",
                        accentColor = CinemaCyan,
                        onClick = { viewModel.onCycleNdFilter() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Lock,
                        label = if (state.aeAfLocked) "LOCKED" else "TRIPOD",
                        isActive = state.aeAfLocked,
                        onClick = { viewModel.onToggleAeAfLock() }
                    )
                }
            }

            "SLOW-MO" -> {
                item {
                    ContextChip(
                        icon = Icons.Default.SlowMotionVideo,
                        label = "${state.slowMoFps} FPS",
                        isActive = true,
                        monospace = true,
                        onClick = { viewModel.onCycleSlowMoFps() }
                    )
                }
                item {
                    val dilation = (state.slowMoFps.toDouble() / 30.0).coerceAtLeast(1.0)
                    ContextChip(
                        label = String.format(Locale.US, "1/%.0f× SPEED", dilation),
                        isActive = true,
                        accentColor = CinemaCyan,
                        monospace = true,
                        onClick = { viewModel.onCycleSlowMoFps() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.AspectRatio,
                        label = state.videoResolution,
                        isActive = false,
                        monospace = true,
                        onClick = { onOpenFormatModal() }
                    )
                }
                item {
                    ContextChip(
                        label = "SHUTTER",
                        isActive = state.shutterNs > 0L,
                        onClick = { onOpenProTab(ProControlTab.SHUTTER) }
                    )
                }
            }

            "TIME-LAPSE" -> {
                item {
                    ContextChip(
                        icon = Icons.Default.Timelapse,
                        label = "EVERY ${state.timeLapseIntervalSec}s",
                        isActive = true,
                        monospace = true,
                        onClick = { viewModel.onCycleTimeLapseInterval() }
                    )
                }
                item {
                    ContextChip(
                        label = "${state.timeLapseFrames} FRAMES",
                        isActive = state.timeLapseFrames > 0,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = {}
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Lock,
                        label = if (state.aeAfLocked) "DE-FLICKER ON" else "LOCK AE/AF",
                        isActive = state.aeAfLocked,
                        accentColor = CinemaAmber,
                        onClick = { viewModel.onToggleAeAfLock() }
                    )
                }
                item {
                    ContextChip(
                        label = state.photoFormat.uppercase(),
                        isActive = false,
                        onClick = { viewModel.onCyclePhotoFormat() }
                    )
                }
            }

            "PANORAMA" -> {
                item {
                    ContextChip(
                        icon = Icons.Default.PanoramaPhotosphere,
                        label = if (state.is360SphereMode) "360° SPHERE" else "WIDE SWEEP",
                        isActive = true,
                        accentColor = if (state.is360SphereMode) CinemaCyan else AppleYellow,
                        onClick = { viewModel.onToggle360SphereMode() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Lock,
                        label = if (state.aeAfLocked) "EXP LOCKED" else "LOCK EXP",
                        isActive = state.aeAfLocked,
                        onClick = { viewModel.onToggleAeAfLock() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Straighten,
                        label = "LEVEL",
                        isActive = state.showHorizonLeveler,
                        onClick = { viewModel.toggleHorizon() }
                    )
                }
                item {
                    ContextChip(
                        label = "${state.panoramaCapturedFrames} SLICES",
                        isActive = state.panoramaCapturedFrames > 0,
                        monospace = true,
                        onClick = {}
                    )
                }
            }

            "SPATIAL" -> {
                val is360 = state.spatialFovDeg >= 270
                item {
                    ContextChip(
                        icon = if (is360) Icons.Default.Public else Icons.Default.ViewInAr,
                        label = if (is360) "360° SPHERE" else "180° VR",
                        isActive = true,
                        accentColor = CinemaCyan,
                        onClick = { viewModel.onToggleSpatialFov() }
                    )
                }
                item {
                    ContextChip(
                        icon = if (state.spatialAutoCapture) Icons.Default.CenterFocusStrong else Icons.Default.TouchApp,
                        label = if (state.spatialAutoCapture) "AUTO LOCK" else "MANUAL TAP",
                        isActive = state.spatialAutoCapture,
                        accentColor = AppleYellow,
                        onClick = { viewModel.onToggleSpatialAutoCapture() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Lock,
                        label = if (state.aeAfLocked) "EXP LOCKED" else "LOCK EXP",
                        isActive = state.aeAfLocked,
                        onClick = { viewModel.onToggleAeAfLock() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Straighten,
                        label = "LEVEL",
                        isActive = state.showHorizonLeveler,
                        onClick = { viewModel.toggleHorizon() }
                    )
                }
                item {
                    ContextChip(
                        label = "${state.spatialCapturedCount}/${state.spatialTotalNodes} NODES",
                        isActive = state.spatialCapturedCount > 0,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = {
                            if (state.isSpatialCapturing && state.spatialCapturedCount > 0) {
                                viewModel.finishSpatialCapture()
                            }
                        }
                    )
                }
            }

            "AUTOMATION" -> {
                item {
                    val preset = com.txor.commix.automation.AutomationEngine.getPresetById(state.automationPresetId)
                    ContextChip(
                        icon = Icons.Default.ElectricBolt,
                        label = preset.title.uppercase(),
                        isActive = true,
                        onClick = { onOpenAutomationModal() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Sensors,
                        label = "TRIG: ${state.automationTriggerMode.replace("_", " ").uppercase()}",
                        isActive = state.automationTriggerMode != "immediate",
                        accentColor = CinemaCyan,
                        onClick = { viewModel.onCycleAutomationTrigger() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Timeline,
                        label = "KEYFRAMES",
                        isActive = false,
                        onClick = { onOpenKeyframeModal() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.MovieFilter,
                        label = "SLATE",
                        isActive = false,
                        onClick = { onOpenSlateSheet() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    accentColor: Color = AppleYellow,
    monospace: Boolean = false
) {
    val bgColor = if (isActive) accentColor.copy(alpha = 0.18f) else CinemaSurface.copy(alpha = 0.85f)
    val borderColor = if (isActive) accentColor.copy(alpha = 0.72f) else CinemaGlassBorder
    val fgColor = if (isActive) accentColor else CinemaTextLight

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bgColor)
            .border(0.9.dp, borderColor, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            color = fgColor,
            fontSize = 10.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            letterSpacing = 0.35.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}
