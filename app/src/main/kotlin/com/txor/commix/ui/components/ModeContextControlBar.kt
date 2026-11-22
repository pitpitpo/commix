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
import com.txor.commix.StabilizationPolicyEngine
import com.txor.commix.model.CameraUiState
import com.txor.commix.ui.CommixViewModel
import com.txor.commix.ui.theme.*
import java.util.Locale

/**
 * Dedicated per-mode contextual control strip rendered above the mode carousel.
 * Gives every single camera mode instant, 1-tap access to its real end-to-end engine parameters,
 * including unified Hardware+Software Stabilization & Apply-When controls, Burst, Auto-Level,
 * Zebras, False Color, Safe Area, Focus Pull A->B, Custom Rig Presets C1-C3, Slow-Mo Playback FPS,
 * Time-Lapse Shot Limit & Output Duration, and Night Red Vision UI.
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
    onOpenSettingsTab: (String) -> Unit = {},
    onOpenStabilizationSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val stabEngine = StabilizationPolicyEngine.normalizeEngineMode(state.stabilizationEngineMode)
    val stabWhen = StabilizationPolicyEngine.normalizeApplyWhen(state.stabilizationApplyWhen)
    val stabEngineShort = when (stabEngine) {
        StabilizationPolicyEngine.ENGINE_OFF -> "OFF"
        StabilizationPolicyEngine.ENGINE_HARDWARE_OIS -> "OIS"
        StabilizationPolicyEngine.ENGINE_SOFTWARE_EIS -> "EIS"
        StabilizationPolicyEngine.ENGINE_GYRO_POST -> "GYRO"
        else -> "HYB"
    }
    val stabWhenShort = when (stabWhen) {
        StabilizationPolicyEngine.APPLY_RECORDING_ONLY -> "REC"
        StabilizationPolicyEngine.APPLY_PREVIEW_ONLY -> "VF"
        StabilizationPolicyEngine.APPLY_POST_EXPORT -> "POST"
        else -> "ALL"
    }
    val stabIsActive = stabEngine != StabilizationPolicyEngine.ENGINE_OFF

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
                        icon = Icons.Default.Shield,
                        label = if (stabIsActive) "STAB $stabEngineShort·$stabWhenShort" else "STAB OFF",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = onOpenStabilizationSheet
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.BurstMode,
                        label = if (state.burstShotCount >= 2) "BURST ${state.burstShotCount}×" else "BURST OFF",
                        isActive = state.burstShotCount >= 2,
                        accentColor = CinemaAmber,
                        monospace = true,
                        onClick = { viewModel.onCycleBurstShotCount() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Straighten,
                        label = if (state.photoAutoLevelEnabled) "AUTO-LVL ON" else "AUTO-LVL",
                        isActive = state.photoAutoLevelEnabled,
                        accentColor = CinemaCyan,
                        onClick = { viewModel.onTogglePhotoAutoLevel() }
                    )
                }
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
                        icon = Icons.Default.Bookmark,
                        label = if (state.activeCustomSlot > 0) "C${state.activeCustomSlot}" else "C1-C3",
                        isActive = state.activeCustomSlot > 0 || state.savedCustomSlots.isNotEmpty(),
                        accentColor = CinemaCyan,
                        monospace = true,
                        onClick = { onOpenProTab(ProControlTab.PRESETS) }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Gradient,
                        label = if (state.showZebras) "ZEBRA ${state.zebraThresholdIre}%" else "ZEBRA",
                        isActive = state.showZebras,
                        accentColor = RecordingRed,
                        monospace = true,
                        onClick = { viewModel.onToggleZebras() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.ColorLens,
                        label = "F-COLOR",
                        isActive = state.showFalseColor,
                        accentColor = CinemaPurple,
                        onClick = { viewModel.onToggleFalseColor() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Shield,
                        label = if (stabIsActive) "STAB $stabEngineShort·$stabWhenShort" else "STAB OFF",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = onOpenStabilizationSheet
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
                        label = if (stabIsActive) "STAB $stabEngineShort·$stabWhenShort" else "STAB OFF",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = onOpenStabilizationSheet
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Gradient,
                        label = if (state.showZebras) "ZEBRA ${state.zebraThresholdIre}%" else "ZEBRA",
                        isActive = state.showZebras,
                        accentColor = RecordingRed,
                        monospace = true,
                        onClick = { viewModel.onToggleZebras() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.ColorLens,
                        label = "F-COLOR",
                        isActive = state.showFalseColor,
                        accentColor = CinemaPurple,
                        onClick = { viewModel.onToggleFalseColor() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.CropFree,
                        label = "SAFE AREA",
                        isActive = state.showSafeAreaGuides,
                        accentColor = CinemaCyan,
                        onClick = { viewModel.onToggleSafeAreaGuides() }
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
                    val pullReady = state.focusPointA != null && state.focusPointB != null
                    ContextChip(
                        icon = Icons.Default.CenterFocusWeak,
                        label = when {
                            state.isFocusPullRunning -> "PULLING…"
                            pullReady -> "PULL A→B"
                            else -> "FOCUS A/B"
                        },
                        isActive = pullReady || state.isFocusPullRunning,
                        accentColor = CinemaCyan,
                        onClick = {
                            if (pullReady) viewModel.onTriggerFocusPull(true)
                            else onOpenProTab(ProControlTab.FOCUS)
                        }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Shield,
                        label = if (stabIsActive) "STAB $stabEngineShort·$stabWhenShort" else "STAB OFF",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = onOpenStabilizationSheet
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
                        icon = Icons.Default.ColorLens,
                        label = "F-COLOR",
                        isActive = state.showFalseColor,
                        accentColor = CinemaPurple,
                        onClick = { viewModel.onToggleFalseColor() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.CropFree,
                        label = "SAFE AREA",
                        isActive = state.showSafeAreaGuides,
                        accentColor = CinemaCyan,
                        onClick = { viewModel.onToggleSafeAreaGuides() }
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
                        icon = Icons.Default.Visibility,
                        label = if (state.nightRedUiEnabled) "RED UI ON" else "RED UI",
                        isActive = state.nightRedUiEnabled,
                        accentColor = RecordingRed,
                        onClick = { viewModel.onToggleNightRedUi() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Shield,
                        label = if (stabIsActive) "STAB $stabEngineShort" else "TRIPOD OFF",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = onOpenStabilizationSheet
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
                        icon = Icons.Default.Straighten,
                        label = if (state.photoAutoLevelEnabled) "AUTO-LVL ON" else "AUTO-LVL",
                        isActive = state.photoAutoLevelEnabled,
                        accentColor = CinemaCyan,
                        onClick = { viewModel.onTogglePhotoAutoLevel() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Shield,
                        label = if (stabIsActive) "STAB $stabEngineShort" else "TRIPOD OFF",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = onOpenStabilizationSheet
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
                    ContextChip(
                        icon = Icons.Default.PlayArrow,
                        label = "PLAY ${state.slowMoPlaybackFps}p",
                        isActive = true,
                        accentColor = AppleYellow,
                        monospace = true,
                        onClick = { viewModel.onCycleSlowMoPlaybackFps() }
                    )
                }
                item {
                    val dilation = (state.slowMoFps.toDouble() / state.slowMoPlaybackFps.coerceAtLeast(1).toDouble()).coerceAtLeast(1.0)
                    val speedStr = if (dilation % 1.0 < 0.05) {
                        String.format(Locale.US, "1/%.0f× SPEED", dilation)
                    } else {
                        String.format(Locale.US, "1/%.1f× SPEED", dilation)
                    }
                    ContextChip(
                        label = speedStr,
                        isActive = true,
                        accentColor = CinemaCyan,
                        monospace = true,
                        onClick = { viewModel.onCycleSlowMoFps() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Shield,
                        label = if (stabIsActive) "STAB $stabEngineShort·$stabWhenShort" else "STAB OFF",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = onOpenStabilizationSheet
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
                    val limitLabel = if (state.timeLapseShotLimit > 0) {
                        val outSec = state.timeLapseShotLimit / 30.0
                        String.format(Locale.US, "LIMIT %d (%.0fs)", state.timeLapseShotLimit, outSec)
                    } else {
                        "LIMIT ∞"
                    }
                    ContextChip(
                        icon = Icons.Default.Flag,
                        label = limitLabel,
                        isActive = state.timeLapseShotLimit > 0,
                        accentColor = CinemaCyan,
                        monospace = true,
                        onClick = { viewModel.onCycleTimeLapseShotLimit() }
                    )
                }
                item {
                    val clipSec = state.timeLapseFrames / 30.0
                    val frameLabel = if (state.timeLapseFrames > 0) {
                        String.format(Locale.US, "%dF (%.1fs)", state.timeLapseFrames, clipSec)
                    } else {
                        "0 FRAMES"
                    }
                    ContextChip(
                        label = frameLabel,
                        isActive = state.timeLapseFrames > 0,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = {
                            if (state.isRecording) {
                                viewModel.onShutterTapped()
                            } else {
                                onOpenSettingsTab("TIME-LAPSE")
                            }
                        }
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
                        icon = Icons.Default.Shield,
                        label = if (stabIsActive) "STAB $stabEngineShort" else "TRIPOD OFF",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = onOpenStabilizationSheet
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
                        icon = Icons.Default.SwapHoriz,
                        label = if (state.panoramaDirection >= 0) "DIR L→R" else "DIR R←L",
                        isActive = true,
                        accentColor = CinemaTextLight,
                        onClick = { viewModel.onTogglePanoramaDirection() }
                    )
                }
                item {
                    ContextChip(
                        icon = Icons.Default.Lock,
                        label = if (state.aeAfLocked || state.isPanoramaCapturing) "EXP LOCKED" else "LOCK EXP",
                        isActive = state.aeAfLocked || state.isPanoramaCapturing,
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
                        icon = Icons.Default.Shield,
                        label = "STAB SWEEP-SAFE",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        onClick = onOpenStabilizationSheet
                    )
                }
                item {
                    ContextChip(
                        label = if (state.isPanoramaCapturing && state.panoramaCapturedFrames > 0) {
                            "DONE (${state.panoramaCapturedFrames})"
                        } else {
                            "${state.panoramaCapturedFrames} SLICES"
                        },
                        isActive = state.panoramaCapturedFrames > 0,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = {
                            if (state.isPanoramaCapturing && state.panoramaCapturedFrames > 0) {
                                viewModel.finishPanoramaCapture()
                            } else {
                                onOpenSettingsTab("PANORAMA")
                            }
                        }
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
                        icon = Icons.Default.Shield,
                        label = "STAB SWEEP-SAFE",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        onClick = onOpenStabilizationSheet
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
                            } else {
                                onOpenSettingsTab("SPATIAL")
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
                    val normTrigger = CommixModeCatalog.normalizeAutomationTrigger(state.automationTriggerMode)
                    ContextChip(
                        icon = Icons.Default.Sensors,
                        label = "TRIG: ${CommixModeCatalog.formatAutomationTriggerShort(normTrigger)}",
                        isActive = normTrigger != "instant",
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
                        icon = Icons.Default.Shield,
                        label = if (stabIsActive) "STAB $stabEngineShort·$stabWhenShort" else "STAB OFF",
                        isActive = stabIsActive,
                        accentColor = CinemaGreen,
                        monospace = true,
                        onClick = onOpenStabilizationSheet
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
