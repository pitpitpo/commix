package com.txor.commix.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.PhotoFormatEngine
import com.txor.commix.StabilizationPolicyEngine
import com.txor.commix.model.CameraUiState
import com.txor.commix.ui.CommixViewModel
import com.txor.commix.ui.components.*
import com.txor.commix.ui.theme.*
import java.util.Locale

/**
 * Complete Main Camera Settings Modal covering all 11 camera modes and subsystems
 * with unified Cinema design-system sheet, header, card, slider, and switch tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsModal(
    state: CameraUiState,
    viewModel: CommixViewModel,
    initialTab: String? = null,
    onDismiss: () -> Unit
) {
    val tabs = CommixModeCatalog.ALL_SETTINGS_TABS
    val defaultTab = remember(initialTab, state.activeMode) {
        when {
            initialTab != null && initialTab in tabs -> initialTab
            else -> CommixModeCatalog.resolveSettingsTabForMode(state.activeMode)
        }
    }
    var selectedTab by remember(defaultTab) { mutableStateOf(defaultTab) }

    CommixModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            // Unified Sheet Header
            CommixSheetHeader(
                title = "Camera Settings",
                subtitle = "All 11 camera modes & Aether engine",
                icon = Icons.Default.Settings,
                badgeText = state.activeMode,
                onDismiss = onDismiss
            )

            // Mode Tabs Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                items(tabs) { tab ->
                    val isSelected = (tab == selectedTab)
                    val isCurrent = (tab == state.activeMode)
                    val tabAccent = if (tab == "SPATIAL") CinemaCyan else AppleYellow
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) tabAccent.copy(alpha = 0.18f) else CinemaSurface)
                            .border(
                                width = if (isSelected) 1.1.dp else 0.7.dp,
                                color = if (isSelected) tabAccent else CinemaGlassBorder,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCurrent && !isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(RoundedCornerShape(2.5.dp))
                                        .background(tabAccent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = tab,
                                color = if (isSelected) tabAccent else if (isCurrent) CinemaTextLight else CinemaTextDim,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Tab Content
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    "PHOTO" -> {
                        SettingsCard(title = "Photo Format & Compression") {
                            val isLossless = PhotoFormatEngine.isLossless(state.photoFormat, state.photoQuality)
                            val isZeroComp = PhotoFormatEngine.isZeroCompression(state.photoFormat)
                            val formatSubtitle = when {
                                isZeroComp -> "${state.photoFormat.uppercase()} · Zero Compression (Archival Exact)"
                                isLossless -> "${state.photoFormat.uppercase()} · 100% (Lossless Compressed)"
                                else -> "${state.photoFormat.uppercase()} · ${state.photoQuality}% Quality"
                            }
                            SettingsSegmentedRow(
                                title = "Standard Formats",
                                subtitle = formatSubtitle,
                                options = listOf(
                                    "jpeg" to "JPEG",
                                    "heic" to "HEIC",
                                    "webp" to "WEBP",
                                    "png" to "PNG"
                                ),
                                selected = state.photoFormat,
                                onSelected = { viewModel.onFormatChanged(it, state.photoQuality) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Uncompressed & Lossless (Zero Compression)",
                                subtitle = "Bit-exact pixel streams without compression algorithms",
                                options = listOf(
                                    "lossless" to "BMP (Raw)",
                                    "tiff" to "TIFF 6.0",
                                    "raw_dng" to "RAW DNG"
                                ),
                                selected = state.photoFormat,
                                onSelected = { viewModel.onFormatChanged(it, 100) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSliderRow(
                                title = "Compression Quality",
                                value = (if (isLossless) 100 else state.photoQuality).toFloat(),
                                range = 50f..100f,
                                label = if (isLossless) "100% (Lossless)" else "${state.photoQuality}%",
                                onValueChange = { viewModel.onFormatChanged(state.photoFormat, it.toInt()) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Enhancement Model",
                                subtitle = "Model: ${state.photoEnhanceAlgo.replace("_", " ").uppercase()}",
                                options = listOf(
                                    "aether_master" to "Aether",
                                    "clarity" to "Clarity",
                                    "detail" to "Detail",
                                    "unsharp" to "Sharp",
                                    "super_res" to "SuperRes"
                                ),
                                selected = state.photoEnhanceAlgo,
                                onSelected = { viewModel.onSetEnhanceAlgo(it) }
                            )
                        }

                        SettingsCard(title = "Computational Imaging & Optics") {
                            SettingsSegmentedRow(
                                title = "Self-Timer Shutter",
                                subtitle = if (state.selfTimerSec == 0) "Instant" else "${state.selfTimerSec}s countdown",
                                options = listOf("0" to "Off", "3" to "3s", "5" to "5s", "10" to "10s"),
                                selected = state.selfTimerSec.toString(),
                                onSelected = { viewModel.onSetSelfTimer(it.toIntOrNull() ?: 0) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Continuous High-Speed Burst",
                                subtitle = if (state.burstShotCount <= 1) "Single shutter release" else "${state.burstShotCount}-shot continuous burst capture",
                                options = listOf("1" to "Off", "3" to "3×", "5" to "5×", "10" to "10×"),
                                selected = state.burstShotCount.toString(),
                                onSelected = { viewModel.onSetBurstShotCount(it.toIntOrNull() ?: 1) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Photo Auto-Level Horizon Straightening (±20°)",
                                subtitle = "Software IMU roll straightening with inscribed aspect-preserving crop",
                                checked = state.photoAutoLevelEnabled,
                                onCheckedChange = { viewModel.onTogglePhotoAutoLevel() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Optical Lens Distortion & CA Correction",
                                subtitle = "Native bilinear radial distortion & chromatic aberration rectification",
                                checked = state.lensCorrectionEnabled,
                                onCheckedChange = { viewModel.onToggleLensCorrection() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Computational Focus Stacking",
                                subtitle = "Capture ${state.focusStackSlices} focus slices and fuse sharpest Laplacian detail",
                                checked = state.focusStackEnabled,
                                onCheckedChange = { viewModel.onToggleFocusStack() }
                            )
                            if (state.focusStackEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                SettingsSegmentedRow(
                                    title = "Focus Stack Depth Slices",
                                    subtitle = "${state.focusStackSlices} planes",
                                    options = listOf("3" to "3 Slices", "5" to "5 Slices", "7" to "7 Slices"),
                                    selected = state.focusStackSlices.toString(),
                                    onSelected = { viewModel.onSetFocusStackSlices(it.toIntOrNull() ?: 5) }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Multi-Exposure Blend",
                                subtitle = state.multiExposureMode.uppercase(),
                                options = listOf("off" to "Off", "additive" to "Add", "average" to "Avg", "lighten" to "Light", "darken" to "Dark"),
                                selected = state.multiExposureMode,
                                onSelected = { viewModel.onSetMultiExposureMode(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Long Exposure Style",
                                subtitle = state.longExposureStyle.replace("_", " ").uppercase(),
                                options = listOf("silky_water" to "Silky", "light_trails" to "Trails", "star_trails" to "Stars", "moving_crowd" to "Crowd"),
                                selected = state.longExposureStyle,
                                onSelected = { viewModel.onSetLongExposureStyle(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Long Exposure Burst Frames",
                                subtitle = "${state.longExposureFrames} frames",
                                options = listOf("4" to "4f", "8" to "8f", "12" to "12f", "16" to "16f"),
                                selected = state.longExposureFrames.toString(),
                                onSelected = { viewModel.onSetLongExposureFrames(it.toIntOrNull() ?: 8) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Process Zero (Zero Smoothing)",
                                subtitle = "Bypass ISP noise reduction & sharpening halos",
                                checked = state.isProcessZeroActive,
                                onCheckedChange = { viewModel.onToggleProcessZero() }
                            )
                            if (state.isProcessZeroActive) {
                                Spacer(modifier = Modifier.height(10.dp))
                                SettingsSliderRow(
                                    title = "Process Zero Intensity",
                                    value = state.processZeroIntensity,
                                    range = 0.0f..2.0f,
                                    label = String.format(Locale.US, "%.1f×", state.processZeroIntensity),
                                    onValueChange = { viewModel.onProcessZeroIntensityChanged(it) }
                                )
                            }
                        }
                    }

                    "VIDEO" -> {
                        SettingsCard(title = "Video Encoding & Bitrate") {
                            SettingsSegmentedRow(
                                title = "Video Codec",
                                subtitle = "Hardware AVC / HEVC / All-I Intra",
                                options = listOf("hevc" to "H.265 / HEVC", "h264" to "H.264", "all_i" to "All-I Intra"),
                                selected = state.videoCodec,
                                onSelected = { viewModel.onSetVideoCodec(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CinemaSurfaceElevated)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                TelemetryKeyValueRow(
                                    label = "Format Spec",
                                    value = "${state.videoResolution} · ${state.videoFpsFloat.toInt()} FPS · ${state.videoBitrateBps / 1_000_000} Mbps",
                                    valueColor = Color.White,
                                    fontSizeSp = 11f
                                )
                            }
                        }

                        SettingsCard(title = "Hardware + Software Stabilization") {
                            SettingsSegmentedRow(
                                title = "Stabilization Engine",
                                subtitle = "Active: ${state.stabilizationActiveLabel.ifBlank { state.stabilizationEngineMode.uppercase() }}",
                                options = StabilizationPolicyEngine.ALL_ENGINE_MODES,
                                selected = StabilizationPolicyEngine.normalizeEngineMode(state.stabilizationEngineMode),
                                onSelected = { viewModel.onSetStabilizationEngineMode(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "When To Apply Stabilization",
                                subtitle = "Choose whether stabilization runs in preview, capture only, or post-export",
                                options = StabilizationPolicyEngine.ALL_APPLY_TIMINGS,
                                selected = StabilizationPolicyEngine.normalizeApplyWhen(state.stabilizationApplyWhen),
                                onSelected = { viewModel.onSetStabilizationApplyWhen(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Gyroflow v1.3 .gcsv Sidecar Export",
                                subtitle = "Save synchronized high-rate IMU gyro logs alongside every video recording",
                                checked = state.gyroflowExportEnabled,
                                onCheckedChange = { viewModel.onToggleGyroflowExport() }
                            )
                        }

                        SettingsCard(title = "Audio Pipeline & Viewfinder") {
                            SettingsToggleRow(
                                title = "Broadcast Quality Audio (384kbps AAC LC)",
                                subtitle = "Studio broadcast high-bitrate audio stream",
                                checked = state.uncompressedAudio,
                                onCheckedChange = { viewModel.onToggleUncompressedAudio() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Audio Low Cut (80Hz High-Pass)",
                                subtitle = "Eliminate wind rumble & mechanical handling noise",
                                checked = state.audioLowCut,
                                onCheckedChange = { viewModel.onToggleAudioLowCut() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Disable Viewfinder Preview (Blind Mode)",
                                subtitle = "OLED blackout to unlock 100% cadence and 0 dropped frames",
                                checked = state.viewfinderDisabled,
                                onCheckedChange = { viewModel.onToggleViewfinderDisabled() }
                            )
                        }
                    }

                    "PRO" -> {
                        SettingsCard(title = "Pro Cinema Scopes & Assists") {
                            SettingsToggleRow(
                                title = "Luma Histogram (256-bin)",
                                subtitle = "Real-time luminance distribution & exposure zones",
                                checked = state.showHistogram,
                                onCheckedChange = { viewModel.toggleHistogram() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Luma Waveform Monitor",
                                subtitle = "Horizontal IRE exposure profile across the frame",
                                checked = state.showWaveform,
                                onCheckedChange = { viewModel.toggleWaveform() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "RGB Parade Scope (Broadcast)",
                                subtitle = "Separate R, G, B channel waveform calibration for white balance",
                                checked = state.showRgbParade,
                                onCheckedChange = { viewModel.toggleRgbParade() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Chroma Vectorscope (Polar)",
                                subtitle = "Real-time hue and chroma saturation polar graticule",
                                checked = state.showVectorscope,
                                onCheckedChange = { viewModel.toggleVectorscope() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Zebra Highlight Clipping Overlay",
                                subtitle = "Live diagonal stripes over sensor regions exceeding IRE threshold",
                                checked = state.showZebras,
                                onCheckedChange = { viewModel.onToggleZebras() }
                            )
                            if (state.showZebras) {
                                Spacer(modifier = Modifier.height(10.dp))
                                SettingsSegmentedRow(
                                    title = "Zebra IRE Threshold",
                                    subtitle = "Highlight warning trigger level (${state.zebraThresholdIre}% IRE)",
                                    options = listOf("70" to "70%", "85" to "85%", "95" to "95%", "100" to "100%"),
                                    selected = state.zebraThresholdIre.toString(),
                                    onSelected = { viewModel.onSetZebraThreshold(it.toIntOrNull() ?: 95) }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "False Color IRE Exposure Heatmap",
                                subtitle = "Cinema ARRI/Blackmagic IRE color bands for exact skin & clip metering",
                                checked = state.showFalseColor,
                                onCheckedChange = { viewModel.onToggleFalseColor() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Broadcast Safe-Area Guides (90% / 80% / 9:16)",
                                subtitle = "Action safe, title safe, and vertical social crop framing markers",
                                checked = state.showSafeAreaGuides,
                                onCheckedChange = { viewModel.onToggleSafeAreaGuides() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Default Shutter Angle",
                                subtitle = "Motion blur cadence preset",
                                options = listOf("180" to "180°", "144" to "144°", "172.8" to "172.8°", "360" to "360°"),
                                selected = state.defaultShutterAngle,
                                onSelected = { viewModel.onSetDefaultShutterAngle(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "180° Cinema Shutter Lock",
                                subtitle = "Auto-link exposure duration strictly to video framerate",
                                checked = state.shutterAngleLocked,
                                onCheckedChange = { viewModel.onToggleShutter180Lock() }
                            )
                        }
                    }

                    "CINEMATIC" -> {
                        SettingsCard(title = "Cinematic Aspect Ratio & Optics") {
                            SettingsSegmentedRow(
                                title = "Framing Matte",
                                subtitle = "Cinema letterbox overlay",
                                options = listOf("2.39:1" to "2.39:1", "2.00:1" to "2.00:1", "1.85:1" to "1.85:1", "1.66:1" to "1.66:1", "16:9" to "16:9"),
                                selected = state.cinemaMatteRatio,
                                onSelected = { viewModel.onSetCinemaMatteRatio(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Anamorphic De-Squeeze",
                                subtitle = "Optical lens horizontal expansion",
                                options = listOf("1.0" to "Off", "1.33" to "1.33×", "1.55" to "1.55×", "1.75" to "1.75×", "2.0" to "2.0×"),
                                selected = state.anamorphicRatio.toString(),
                                onSelected = { viewModel.onAnamorphicChanged(it.toFloatOrNull() ?: 1.0f) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "360° World Horizon Lock",
                                subtitle = "GoPro-grade IMU quaternion roll inversion",
                                checked = state.is360SphereMode,
                                onCheckedChange = { viewModel.onToggle360SphereMode() }
                            )
                        }
                    }

                    "NIGHT" -> {
                        SettingsCard(title = "Computational Night & Astrophotography") {
                            SettingsSegmentedRow(
                                title = "Multi-Frame Burst Stacking",
                                subtitle = "Align & merge RAW exposures",
                                options = listOf("4" to "4 Frames", "8" to "8 Frames", "12" to "12 Frames", "16" to "16 Frames"),
                                selected = state.nightFrameCount.toString(),
                                onSelected = { viewModel.onSetNightFrameCount(it.toIntOrNull() ?: 8) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Astro Alignment & Star Tracking",
                                subtitle = "IMU gyroscopic star field motion compensation",
                                checked = state.nightAstroAlignment,
                                onCheckedChange = { viewModel.onToggleNightAstro() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Night Tone Curve",
                                subtitle = "Black point calibration",
                                options = listOf("neutral" to "Neutral", "cold" to "Cold", "warm" to "Warm", "gold" to "Gold"),
                                selected = state.nightTone,
                                onSelected = { viewModel.onSetNightTone(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Astrophotography Red Vision UI",
                                subtitle = "Subtle dark-adaptation red screen tint to preserve night vision",
                                checked = state.nightRedUiEnabled,
                                onCheckedChange = { viewModel.onToggleNightRedUi() }
                            )
                        }
                    }

                    "LONG EXP" -> {
                        SettingsCard(title = "Computational Long Exposure") {
                            SettingsSegmentedRow(
                                title = "Long Exposure Style",
                                subtitle = state.longExposureStyle.replace("_", " ").uppercase(),
                                options = listOf(
                                    "silky_water" to "Silky Water",
                                    "light_trails" to "Light Trails",
                                    "star_trails" to "Star Trails",
                                    "moving_crowd" to "Moving Crowd"
                                ),
                                selected = state.longExposureStyle,
                                onSelected = { viewModel.onSetLongExposureStyle(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Frame Stack Depth",
                                subtitle = "${state.longExposureFrames} aligned frames averaged/peak-blended",
                                options = listOf("4" to "4f", "8" to "8f", "12" to "12f", "16" to "16f"),
                                selected = state.longExposureFrames.toString(),
                                onSelected = { viewModel.onSetLongExposureFrames(it.toIntOrNull() ?: 8) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Electronic ND Filter (e-ND)",
                                subtitle = if (state.ndFilter == "off") "Disabled" else state.ndFilter.uppercase(),
                                options = listOf("off" to "Off", "nd4" to "ND4", "nd8" to "ND8", "nd16" to "ND16", "nd64" to "ND64"),
                                selected = state.ndFilter,
                                onSelected = { viewModel.onNdFilterChanged(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Tripod AE/AF Lock",
                                subtitle = "Lock exposure & focus plane across the entire stack",
                                checked = state.aeAfLocked,
                                onCheckedChange = { viewModel.onToggleAeAfLock() }
                            )
                        }
                    }

                    "SLOW-MO" -> {
                        SettingsCard(title = "High Speed Slow-Motion") {
                            SettingsSegmentedRow(
                                title = "Sensor Readout FPS",
                                subtitle = "Hardware sensor high-speed rate",
                                options = listOf("60" to "60 FPS", "120" to "120 FPS", "240" to "240 FPS", "480" to "480 FPS"),
                                selected = state.slowMoFps.toString(),
                                onSelected = { viewModel.onSetSlowMoFps(it.toIntOrNull() ?: 120) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val dilation = (state.slowMoFps.toDouble() / state.slowMoPlaybackFps.coerceAtLeast(1)).coerceAtLeast(1.0)
                            SettingsSegmentedRow(
                                title = "Output Playback Frame Rate",
                                subtitle = String.format(Locale.US, "%dp timeline · 1/%.1f× motion dilation", state.slowMoPlaybackFps, dilation),
                                options = listOf("24" to "24p Cine", "30" to "30p Std", "60" to "60p Smooth"),
                                selected = state.slowMoPlaybackFps.toString(),
                                onSelected = { viewModel.onSetSlowMoPlaybackFps(it.toIntOrNull() ?: 30) }
                            )
                        }
                    }

                    "TIME-LAPSE" -> {
                        SettingsCard(title = "Time-Lapse & Motion Interval") {
                            SettingsSegmentedRow(
                                title = "Capture Interval",
                                subtitle = "Duration between successive frames",
                                options = listOf("1" to "1 sec", "2" to "2 sec", "5" to "5 sec", "10" to "10 sec", "30" to "30 sec"),
                                selected = state.timeLapseIntervalSec.toString(),
                                onSelected = { viewModel.onSetTimeLapseInterval(it.toIntOrNull() ?: 2) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Intervalometer Shot Limit",
                                subtitle = if (state.timeLapseShotLimit <= 0) "Unlimited until manual stop" else "Auto-stop at ${state.timeLapseShotLimit} frames (${state.timeLapseShotLimit / 30}s @ 30fps)",
                                options = listOf("0" to "∞", "60" to "60 (2s)", "150" to "150 (5s)", "300" to "300 (10s)", "600" to "600 (20s)"),
                                selected = state.timeLapseShotLimit.toString(),
                                onSelected = { viewModel.onSetTimeLapseShotLimit(it.toIntOrNull() ?: 0) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "De-Flicker AE/AF Lock",
                                subtitle = "Lock ISO, shutter & focus to eliminate exposure flicker",
                                checked = state.aeAfLocked,
                                onCheckedChange = { viewModel.onToggleAeAfLock() }
                            )
                        }
                    }

                    "PANORAMA" -> {
                        SettingsCard(title = "Panoramic & 360° Sweep") {
                            SettingsSegmentedRow(
                                title = "Panorama Projection Mode",
                                subtitle = if (state.is360SphereMode) "Full 360° cylindrical sweep" else "Wide horizontal sweep (180°)",
                                options = listOf("wide" to "Wide Sweep", "sphere" to "360° Sphere"),
                                selected = if (state.is360SphereMode) "sphere" else "wide",
                                onSelected = { choice ->
                                    val want360 = (choice == "sphere")
                                    if (state.is360SphereMode != want360) viewModel.onToggle360SphereMode()
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Horizon Leveling Guide",
                                subtitle = "Display live roll & pitch balance indicators during sweep",
                                checked = state.showHorizonLeveler,
                                onCheckedChange = { viewModel.toggleHorizon() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Lock Exposure Across Slices",
                                subtitle = "Prevent brightness seams between adjacent stitched tiles",
                                checked = state.aeAfLocked,
                                onCheckedChange = { viewModel.onToggleAeAfLock() }
                            )
                        }
                    }

                    "FLAGSHIPS" -> {
                        SettingsCard(title = "Cinema Look & Color Science") {
                            SettingsSegmentedRow(
                                title = "Color Science Profile",
                                subtitle = "ASC CDL Grading Matrix",
                                options = listOf("none" to "Standard", "leica" to "Leica", "hasselblad" to "Hasselblad", "zeiss" to "Zeiss", "proraw" to "Apple"),
                                selected = state.lutLook,
                                onSelected = { viewModel.onLutChanged(it) }
                            )
                        }
                    }

                    "AUTOMATION" -> {
                        SettingsCard(title = "Motion & Parameter Automation") {
                            SettingsSegmentedRow(
                                title = "Trigger Mode",
                                subtitle = "Automation start event",
                                options = CommixModeCatalog.AUTOMATION_TRIGGER_OPTIONS,
                                selected = CommixModeCatalog.normalizeAutomationTrigger(state.automationTriggerMode),
                                onSelected = { viewModel.onSetAutomationTrigger(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsSegmentedRow(
                                title = "Motion Preset",
                                subtitle = "Cubic ease-in-out curve",
                                options = listOf(
                                    "dolly_zoom" to "Dolly",
                                    "sunrise_ramp" to "Sunrise",
                                    "golden_hour" to "Golden",
                                    "reveal_shot" to "Reveal",
                                    "dramatic_rack" to "Rack"
                                ),
                                selected = state.automationPresetId,
                                onSelected = { viewModel.onSelectAutomationPreset(it) }
                            )
                        }
                    }

                    "QUEUE" -> {
                        SettingsCard(title = "Processing Job Queue Engine") {
                            SettingsSegmentedRow(
                                title = "Worker Threads",
                                subtitle = "Concurrent background synthesis threads",
                                options = listOf("1" to "1 Core", "2" to "2 Cores", "4" to "4 Cores"),
                                selected = state.queueWorkerThreads.toString(),
                                onSelected = { viewModel.onSetQueueWorkerThreads(it.toIntOrNull() ?: 2) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Auto-Queue Multi-Exposure Stacking",
                                subtitle = "Defer heavy computation to background queue",
                                checked = state.queueAutoQueueStacking,
                                onCheckedChange = { viewModel.onToggleQueueAutoStacking() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val queue = com.txor.commix.ProcessingJobQueue.instance
                            val activeCount = queue.activeJobCount
                            val pendingCount = queue.pendingJobCount
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CinemaSurfaceElevated)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                TelemetryKeyValueRow(
                                    label = "Queue Load",
                                    value = "$activeCount Active · $pendingCount Pending (${(queue.overallProgress * 100).toInt()}%)",
                                    valueColor = if (activeCount > 0) AppleYellow else Color.White,
                                    fontSizeSp = 11f
                                )
                            }
                        }
                    }

                    "GENERAL" -> {
                        // 1. Viewfinder & Display
                        SettingsCard(title = "Viewfinder & Display") {
                            SettingsToggleRow(
                                title = "Disable Viewfinder Preview (Blackout)",
                                subtitle = "OLED battery-saving blackout (tap to wake)",
                                checked = state.viewfinderDisabled,
                                onCheckedChange = { viewModel.onToggleViewfinderDisabled() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Clean Viewfinder Mode",
                                subtitle = "Hide all non-essential HUD overlays & scopes",
                                checked = state.cleanViewfinderMode,
                                onCheckedChange = { viewModel.onToggleCleanViewfinder() }
                            )
                        }

                        // 2. Composition & Guides
                        SettingsCard(title = "Composition & Guides") {
                            SettingsSegmentedRow(
                                title = "Grid & Alignment",
                                subtitle = state.gridType.uppercase(),
                                options = listOf(
                                    "off" to "Off",
                                    "3x3" to "3×3",
                                    "4x4" to "4×4",
                                    "golden" to "Phi",
                                    "diagonal" to "Diag",
                                    "crosshair" to "Cross"
                                ),
                                selected = state.gridType,
                                onSelected = { viewModel.onSelectGridType(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Dual-Axis Horizon Leveler",
                                subtitle = "Roll & pitch visual level guides",
                                checked = state.showHorizonLeveler,
                                onCheckedChange = { viewModel.toggleHorizon() }
                            )
                        }

                        // 3. Haptics & Telemetry (1:1 with Flutter)
                        SettingsCard(title = "Haptics & Telemetry") {
                            SettingsToggleRow(
                                title = "Tactile Haptic Feedback",
                                subtitle = "Haptics for shutter, stops, and focus lock",
                                checked = state.hapticsEnabled,
                                onCheckedChange = { viewModel.onToggleHaptics() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Focus Ring Animations",
                                subtitle = "Visual reticle pulse and lock animation",
                                checked = state.animationsEnabled,
                                onCheckedChange = { viewModel.onToggleAnimations() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Storage & Battery Telemetry",
                                subtitle = "Live free disk and battery gauge in HUD",
                                checked = state.showStorageRemaining,
                                onCheckedChange = { viewModel.onToggleStorageRemaining() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Adaptive Performance Mode",
                                subtitle = "Auto-tune refresh rates on battery/thermal load",
                                checked = state.adaptivePerformanceEnabled,
                                onCheckedChange = { viewModel.onToggleAdaptivePerformance() }
                            )
                        }

                        // 4. EXIF & Privacy Engine (1:1 with Flutter)
                        SettingsCard(title = "EXIF & Privacy Engine") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.onSetExifPreset(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceElevated),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Allow All", color = CinemaTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                                Button(
                                    onClick = { viewModel.onSetExifPreset(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleYellow.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Privacy Mode", color = AppleYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }

                            SettingsToggleRow(
                                title = "GPS Geotagging & Location",
                                subtitle = "Embed location coordinates in photos",
                                checked = state.exifLocation,
                                onCheckedChange = { viewModel.onToggleExifLocation() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Camera & Device Hardware",
                                subtitle = "Embed device make & model",
                                checked = state.exifCameraInfo,
                                onCheckedChange = { viewModel.onToggleExifCameraInfo() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Exposure & Shooting Data",
                                subtitle = "Embed ISO, shutter speed & aperture",
                                checked = state.exifExposureSettings,
                                onCheckedChange = { viewModel.onToggleExifExposure() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Lens & Optical Profile",
                                subtitle = "Embed focal length & lens model",
                                checked = state.exifLensInfo,
                                onCheckedChange = { viewModel.onToggleExifLensInfo() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Timestamp & Capture Time",
                                subtitle = "Embed date & exact capture timestamp",
                                checked = state.exifDateTime,
                                onCheckedChange = { viewModel.onToggleExifDateTime() }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsToggleRow(
                                title = "Image Description & Copyright",
                                subtitle = "Embed technical tags & copyright",
                                checked = state.exifImageDescription,
                                onCheckedChange = { viewModel.onToggleExifImageDesc() }
                            )
                        }

                        // 5. Flagship & Sensor Telemetry Card (1:1 with Flutter)
                        val activeCam = state.availableCameras.find { it.cameraId == state.activeCameraId }
                        SettingsCard(title = "Flagship & Sensor Telemetry") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CinemaSurfaceElevated)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TelemetryKeyValueRow(
                                    label = "SENSOR: CAM ${state.activeCameraId} (${if (activeCam?.isFront == true) "FRONT" else "REAR"})",
                                    value = "MAX ${String.format(Locale.US, "%.1f", state.maxLensMp)} MP",
                                    labelColor = AppleYellow,
                                    labelBold = true,
                                    valueColor = Color.White,
                                    fontSizeSp = 11f
                                )
                                TelemetryKeyValueRow(
                                    label = "Optical Stabilization (OIS)",
                                    value = if (activeCam?.hasOis == true) "Hardware VCM [HW]" else "SW Fallback",
                                    valueColor = if (activeCam?.hasOis == true) CinemaGreen else CinemaAmber
                                )
                                TelemetryKeyValueRow(
                                    label = "Electronic Stabilization (EIS)",
                                    value = if (activeCam?.hasEis == true) "HAL Stream [HW]" else "Gyroflow [SW]",
                                    valueColor = if (activeCam?.hasEis == true) CinemaGreen else CinemaAmber
                                )
                                TelemetryKeyValueRow(
                                    label = "Preview Stabilization (API 33+)",
                                    value = if (activeCam?.hasPreviewStabilization == true) "Supported [HW]" else "Standard EIS",
                                    valueColor = if (activeCam?.hasPreviewStabilization == true) CinemaGreen else CinemaTextDim
                                )
                                TelemetryKeyValueRow(
                                    label = "Active Stabilization Policy",
                                    value = state.stabilizationActiveLabel.ifBlank { state.stabilizationEngineMode.uppercase() },
                                    valueColor = AppleYellow
                                )
                                TelemetryKeyValueRow(
                                    label = "Dynamic Range (HDR)",
                                    value = if (activeCam?.hasRaw == true) "Staggered [HW]" else "Mertens HDR+ [SW]",
                                    valueColor = if (activeCam?.hasRaw == true) CinemaGreen else CinemaAmber
                                )
                                TelemetryKeyValueRow(
                                    label = "RAW Bayer Stream",
                                    value = if (activeCam?.hasRaw == true) "Bayer CFA [HW]" else "Lossless RGB [SW]",
                                    valueColor = if (activeCam?.hasRaw == true) CinemaGreen else CinemaAmber
                                )
                            }
                        }
                    }

                    "SPATIAL" -> {
                        SettingsCard(title = "Spatial 180° & 360° Spherical Capture") {
                            SettingsSegmentedRow(
                                title = "Spatial Field of View",
                                subtitle = if (state.spatialFovDeg >= 270) {
                                    "Full 360°×180° equirectangular sphere (22 AR target tiles, 2:1 GPano XMP)"
                                } else {
                                    "Front 180°×180° VR hemisphere dome (13 AR target tiles, VR180 GPano XMP)"
                                },
                                options = listOf(
                                    "180" to "180° VR Dome",
                                    "360" to "360° Full Sphere"
                                ),
                                selected = if (state.spatialFovDeg >= 270) "360" else "180",
                                onSelected = { viewModel.onSetSpatialFov(it.toIntOrNull() ?: 180) }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            SettingsToggleRow(
                                title = "Auto-Capture on Target Lock",
                                subtitle = "Automatically trigger tile capture when reticle aligns within ±8.5°",
                                checked = state.spatialAutoCapture,
                                onCheckedChange = { viewModel.onToggleSpatialAutoCapture() }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CinemaSurfaceElevated)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TelemetryKeyValueRow(
                                    label = "Projection Format",
                                    value = if (state.spatialFovDeg >= 270) "Equirectangular 2:1" else "Cropped Equirect 1:1",
                                    valueColor = AppleYellow
                                )
                                TelemetryKeyValueRow(
                                    label = "Metadata Standard",
                                    value = "Google GPano XMP",
                                    valueColor = CinemaGreen
                                )
                                TelemetryKeyValueRow(
                                    label = "Spherical Lattice",
                                    value = "${state.spatialTotalNodes} 3D Nodes",
                                    valueColor = Color.White
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    CommixSectionCard(title = title, content = content)
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = CinemaTextLight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = CinemaTextDim, fontSize = 10.5.sp, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = commixSwitchColors()
        )
    }
}

@Composable
private fun TelemetryKeyValueRow(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color = Color.Gray,
    labelBold: Boolean = false,
    fontSizeSp: Float = 10f
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = fontSizeSp.sp,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsSegmentedRow(
    title: String,
    subtitle: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    val useScrollableSegments = options.size >= 5 || options.any { it.second.length >= 10 }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Stack title cleanly above subtitle so long titles & descriptions never collide or wrap mid-word
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 17.sp
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = AppleYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (useScrollableSegments) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaSurfaceElevated)
                    .horizontalScroll(rememberScrollState())
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (key, label) ->
                    val isSel = (key == selected)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) AppleYellow else Color.Transparent)
                            .clickable { onSelected(key) }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaSurfaceElevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                options.forEach { (key, label) ->
                    val isSel = (key == selected)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) AppleYellow else Color.Transparent)
                            .clickable { onSelected(key) }
                            .padding(horizontal = 4.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = label,
                color = AppleYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = commixSliderColors()
        )
    }
}
