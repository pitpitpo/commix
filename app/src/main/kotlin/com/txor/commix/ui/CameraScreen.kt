package com.txor.commix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import com.txor.commix.LensProbeService
import com.txor.commix.PhotoFormatEngine
import com.txor.commix.ProcessingJobQueue
import com.txor.commix.ui.components.*
import com.txor.commix.ui.modals.CinemaSlateSheet
import com.txor.commix.ui.modals.HardwareLensModal
import com.txor.commix.ui.modals.JobQueueModal
import com.txor.commix.ui.modals.KeyframeEditorModal
import com.txor.commix.ui.modals.MegapixelLimitsModal
import com.txor.commix.ui.modals.ProVideoFormatModal
import com.txor.commix.ui.modals.MainSettingsModal
import com.txor.commix.ui.modals.AutomationModal
import com.txor.commix.ui.modals.StabilizationSheet
import com.txor.commix.PipelineThroughputEngine
import com.txor.commix.StabilizationPolicyEngine
import com.txor.commix.ui.theme.*

/**
 * 1:1 Apple Cinema / Commix Pro Camera Screen in Jetpack Compose
 * matching screen_installed.png, screen_main_settings.png, and screen_lens_opened.png.
 */
@Composable
fun CameraScreen(
    viewModel: CommixViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val panoramaPreviewStrip by viewModel.panoramaPreviewStrip.collectAsState()
    val queue = ProcessingJobQueue.instance
    val queueJobs by queue.jobs.collectAsState()

    val context = LocalContext.current
    var showFormatModal by remember { mutableStateOf(false) }
    var showLensModal by remember { mutableStateOf(false) }
    var showMpModal by remember { mutableStateOf(false) }
    var showJobQueueModal by remember { mutableStateOf(false) }
    var showSlateSheet by remember { mutableStateOf(false) }
    var showKeyframeModal by remember { mutableStateOf(false) }
    var showFocusLoupe by remember { mutableStateOf(false) }
    var showMainSettings by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableStateOf<String?>(null) }
    var showAutomationModal by remember { mutableStateOf(false) }
    var showStabilizationSheet by remember { mutableStateOf(false) }
    val isCleanActive = state.cleanViewfinderMode
    var activeProTab by remember { mutableStateOf(ProControlTab.ISO) }

    BackHandler(enabled = state.isPanoramaCapturing || showFocusLoupe || state.showProControls || state.cleanViewfinderMode) {
        when {
            state.isPanoramaCapturing -> viewModel.cancelPanoramaCapture()
            showFocusLoupe -> showFocusLoupe = false
            state.showProControls -> viewModel.toggleProControls()
            state.cleanViewfinderMode -> viewModel.onToggleCleanViewfinder()
        }
    }

    // Tap to focus reticle state
    var tapFocusPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var isFocusLocked by remember { mutableStateOf(false) }
    var vfWidth by remember { mutableFloatStateOf(1f) }
    var vfHeight by remember { mutableFloatStateOf(1f) }

    val currentMaxZoom by rememberUpdatedState(state.maxZoomRatio)
    var gestureAccumulatedZoom by remember { mutableDoubleStateOf(state.zoomRatio) }
    LaunchedEffect(state.zoomRatio) {
        gestureAccumulatedZoom = state.zoomRatio
    }

    LaunchedEffect(tapFocusPoint, isFocusLocked) {
        if (tapFocusPoint != null && !isFocusLocked) {
            kotlinx.coroutines.delay(3500)
            tapFocusPoint = null
            viewModel.onCancelFocus()
        }
    }

    LaunchedEffect(state.activeCameraId, state.activeMode) {
        tapFocusPoint = null
        isFocusLocked = false
        viewModel.onCancelFocus()
    }

    val modes = CommixModeCatalog.ALL_CAMERA_MODES
    val isVideoMode = state.activeMode in listOf("VIDEO", "CINEMATIC", "SLOW-MO", "TIME-LAPSE", "AUTOMATION")

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CinemaBlack)
        ) {
        // -------------------------------------------------------------
        // 1. TOP BAR CONTROLS (Height ~50dp)
        // -------------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .zIndex(20f)
        ) {
            // Left Group: Flash Toggle, Queue, Self-Timer / Action Shield & Thermal Warning
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Flash Circle Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (state.flashMode != "off") AppleYellow.copy(alpha = 0.20f) else CinemaGlass)
                        .testTag("flash_button")
                        .clickable { viewModel.onCycleFlash() }
                ) {
                    Icon(
                        imageVector = if (state.flashMode == "off") Icons.Default.FlashOff else Icons.Default.FlashOn,
                        contentDescription = "Flash",
                        tint = if (state.flashMode != "off") AppleYellow else CinemaTextLight,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Job Queue Icon Button
                val activeJobs = queue.activeJobCount
                val hasQueueWork = queue.hasWorkInProgress

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (hasQueueWork) AppleYellow.copy(alpha = 0.20f) else CinemaGlass)
                        .testTag("job_queue_button")
                        .clickable { showJobQueueModal = true }
                ) {
                    if (activeJobs > 0) {
                        CircularProgressIndicator(
                            color = AppleYellow,
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Job Queue",
                            tint = if (hasQueueWork) AppleYellow else CinemaTextLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Self-Timer Badge (in Photo / Pro / Night / Long Exposure modes)
                if (!isVideoMode && state.activeMode != "PANORAMA" && state.activeMode != "SPATIAL") {
                    val timerActive = state.selfTimerSec > 0
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(30.dp)
                            .defaultMinSize(minWidth = 32.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (timerActive) AppleYellow.copy(alpha = 0.20f) else CinemaGlass)
                            .border(
                                0.8.dp,
                                if (timerActive) AppleYellow else CinemaGlassBorder,
                                RoundedCornerShape(15.dp)
                            )
                            .testTag("self_timer_button")
                            .clickable {
                                CommixHaptics.click(context, state.hapticsEnabled)
                                viewModel.onCycleSelfTimer()
                            }
                            .padding(horizontal = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Self Timer",
                                tint = if (timerActive) AppleYellow else CinemaTextLight,
                                modifier = Modifier.size(13.dp)
                            )
                            if (timerActive) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${state.selfTimerSec}s",
                                    color = AppleYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Universal Hardware + Software Stabilization Shield Badge (all modes — opens StabilizationSheet)
                run {
                    val engineNorm = StabilizationPolicyEngine.normalizeEngineMode(state.stabilizationEngineMode)
                    val isStabActive = engineNorm != StabilizationPolicyEngine.ENGINE_OFF
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isStabActive) AppleYellow else CinemaGlass)
                            .border(
                                0.8.dp,
                                if (isStabActive) AppleYellow else CinemaGlassBorder,
                                CircleShape
                            )
                            .testTag("stabilization_shield_button")
                            .clickable {
                                CommixHaptics.click(context, state.hapticsEnabled)
                                showStabilizationSheet = true
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Stabilization Settings",
                            tint = if (isStabActive) CinemaBlack else CinemaTextLight,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Compact Thermal Warning Badge (only when thermal throttle is active)
                val isWarm = state.thermalStatus.lowercase() in listOf("moderate", "severe", "critical", "emergency")
                if (isWarm) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(RecordingRed.copy(alpha = 0.28f))
                            .border(0.7.dp, RecordingRed, RoundedCornerShape(13.dp))
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "HOT",
                            color = RecordingRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Center Group: Apple Expandable Chevron for Pro Controls
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(38.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (state.showProControls) Color(0x33FFFFFF) else Color(0x22000000))
                    .clickable { viewModel.toggleProControls() }
            ) {
                Icon(
                    imageVector = if (state.showProControls) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand Pro Controls",
                    tint = if (state.showProControls) AppleYellow else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Right Group: Video Format / RAW & MP Badges / Settings Sliders
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isVideoMode) {
                    // Video Resolution & FPS Pill (e.g. 64MP · 2 or 1080p · 24)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x33000000))
                            .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        CommixHaptics.click(context, state.hapticsEnabled)
                                        showFormatModal = true
                                    },
                                    onLongPress = {
                                        CommixHaptics.heavy(context, state.hapticsEnabled)
                                        viewModel.onCycleResolution()
                                    }
                                )
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        val (pw, ph) = PipelineThroughputEngine.parseResolution(state.videoResolution)
                        val pmp = (pw.toLong() * ph.toLong()) / 1_000_000.0
                        val resPillLabel = when {
                            pmp >= 10.0 -> String.format(java.util.Locale.US, "%.0fMP", pmp)
                            state.videoResolution.contains("3840") || state.videoResolution.contains("4K") -> "4K"
                            state.videoResolution.contains("1920") || state.videoResolution.contains("1080") -> "1080p"
                            state.videoResolution.contains("1280") || state.videoResolution.contains("720") -> "720p"
                            pmp >= 1.0 -> String.format(java.util.Locale.US, "%.1fMP", pmp)
                            else -> state.videoResolution
                        }
                        val fpsPillLabel = if (state.videoFpsFloat % 1.0 == 0.0) {
                            "${state.videoFpsFloat.toInt()}"
                        } else {
                            String.format(java.util.Locale.US, "%.1f", state.videoFpsFloat)
                        }
                        Text(
                            text = "$resPillLabel · $fpsPillLabel",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    // Format & Lossless Toggle Badge (JPEG, HEIC, PNG, WEBP, UNCOMP/BMP, TIFF, RAW)
                    val isLosslessActive = PhotoFormatEngine.isLossless(state.photoFormat, state.photoQuality)
                    val formatBadgeLabel = when (state.photoFormat.lowercase()) {
                        PhotoFormatEngine.FORMAT_RAW_DNG, "dng", "raw" -> if (state.isTrueRawAvailable) "RAW14" else "DNG"
                        PhotoFormatEngine.FORMAT_LOSSLESS, "bmp" -> "UNCOMP"
                        PhotoFormatEngine.FORMAT_TIFF, "tif" -> "TIFF"
                        PhotoFormatEngine.FORMAT_HEIC, "heif" -> "HEIC"
                        PhotoFormatEngine.FORMAT_PNG -> "PNG"
                        PhotoFormatEngine.FORMAT_WEBP -> if (state.photoQuality >= 100) "WEBP-L" else "WEBP"
                        else -> if (state.photoQuality >= 100) "JPG-MAX" else "JPEG"
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isLosslessActive) AppleYellow else Color(0x33000000))
                            .border(
                                0.8.dp,
                                if (isLosslessActive) AppleYellow else Color(0x33FFFFFF),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                CommixHaptics.click(context, state.hapticsEnabled)
                                viewModel.onCyclePhotoFormat()
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = formatBadgeLabel,
                            color = if (isLosslessActive) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Megapixel Pill Badge (compact integer when >= 10MP)
                    val mpLabel = if (state.targetPhotoMp >= 10.0) {
                        String.format(java.util.Locale.US, "%.0fMP", state.targetPhotoMp)
                    } else {
                        String.format(java.util.Locale.US, "%.1fMP", state.targetPhotoMp)
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x33000000))
                            .border(1.2.dp, AppleYellow, RoundedCornerShape(14.dp))
                            .clickable {
                                CommixHaptics.click(context, state.hapticsEnabled)
                                showMpModal = true
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = mpLabel,
                            color = AppleYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Settings 3-Sliders Circle Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33000000))
                        .testTag("pro_controls_button")
                        .clickable {
                            CommixHaptics.click(context, state.hapticsEnabled)
                            viewModel.toggleProControls()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Pro Controls",
                        tint = if (state.showProControls) AppleYellow else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Overall Settings Gear Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CinemaGlass)
                        .testTag("settings_button")
                        .clickable {
                            CommixHaptics.click(context, state.hapticsEnabled)
                            settingsInitialTab = null
                            showMainSettings = true
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Camera Settings",
                        tint = CinemaTextLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 2. LIVE VIEWFINDER VIEWPORT (Expanded with exact mode aspect ratio & 24dp rounded corners)
        // -------------------------------------------------------------
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val isVideoOrCinema = state.activeMode in listOf("VIDEO", "SLOW-MO", "TIME-LAPSE", "CINEMATIC", "AUTOMATION")
            val targetRatio = if (state.previewAspectRatio > 0f) state.previewAspectRatio else {
                if (isVideoOrCinema) 9f / 16f else 3f / 4f
            }

            val is43 = kotlin.math.abs(targetRatio - 0.75f) < 0.05f
            val vfCornerRadius = if (is43) 24.dp else 0.dp

            val containerRatio = maxWidth / maxHeight
            val vfModifier = if (containerRatio > targetRatio) {
                Modifier.fillMaxHeight().aspectRatio(targetRatio, matchHeightConstraintsFirst = true)
            } else {
                Modifier.fillMaxWidth().aspectRatio(targetRatio, matchHeightConstraintsFirst = false)
            }

            Box(
                modifier = vfModifier
                    .clip(RoundedCornerShape(vfCornerRadius))
                    .clipToBounds()
                    .background(Color.Black)
                    .onGloballyPositioned { coords ->
                        vfWidth = coords.size.width.toFloat().coerceAtLeast(1f)
                        vfHeight = coords.size.height.toFloat().coerceAtLeast(1f)
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val target = (gestureAccumulatedZoom * zoom).coerceIn(1.0, currentMaxZoom)
                            gestureAccumulatedZoom = target
                            viewModel.onZoomChanged(target)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                showFocusLoupe = !showFocusLoupe
                            },
                            onTap = { offset ->
                                tapFocusPoint = Pair(offset.x, offset.y)
                                isFocusLocked = false
                                val normX = (offset.x / vfWidth).coerceIn(0f, 1f)
                                val normY = (offset.y / vfHeight).coerceIn(0f, 1f)
                                viewModel.onTapFocus(normX, normY)
                            },
                            onLongPress = { offset ->
                                tapFocusPoint = Pair(offset.x, offset.y)
                                isFocusLocked = true
                                val normX = (offset.x / vfWidth).coerceIn(0f, 1f)
                                val normY = (offset.y / vfHeight).coerceIn(0f, 1f)
                                viewModel.onTapFocus(normX, normY)
                            }
                        )
                    }
            ) {
                val previewW = state.previewWidth
                val previewH = state.previewHeight
                ViewfinderSurface(
                    onSurfaceReady = { surface, surfaceTexture ->
                        viewModel.onSurfaceAvailable(surface, surfaceTexture)
                    },
                    onSurfaceDestroyed = {
                        viewModel.onSurfaceDestroyed()
                    },
                    previewWidth = previewW,
                    previewHeight = previewH,
                    sensorOrientation = state.sensorOrientation,
                    anamorphicDeSqueeze = state.anamorphicRatio,
                    targetAspectRatio = targetRatio,
                    modifier = Modifier.graphicsLayer {
                        val hwMax = state.hardwareMaxDigitalZoom.coerceAtLeast(1.0)
                        val digScale = if (state.zoomRatio > hwMax) {
                            (state.zoomRatio / hwMax).toFloat().coerceIn(1f, 30f)
                        } else 1f
                        scaleX = digScale
                        scaleY = digScale
                        clip = true
                    }
                )

                // Cinema Anamorphic Letterbox Matte Overlay
                if (state.activeMode == "CINEMATIC" || (state.cinemaMatteRatio.isNotEmpty() && state.cinemaMatteRatio != "off")) {
                    val matte = if (state.activeMode == "CINEMATIC") state.cinemaMatteRatio.ifEmpty { "2.39:1" } else state.cinemaMatteRatio
                    CinemaMatteOverlay(ratio = matte)
                }

                // Flagship Live Filmstrip Panorama & 360° Photo Sphere Overlay
                if (state.activeMode == "PANORAMA") {
                    PanoramaGuidanceOverlay(
                        sweepAngle = state.panoramaSweepAngle,
                        progress = state.panoramaProgress,
                        rollDeg = state.rollDeg,
                        pitchDeg = state.pitchDeg,
                        is360Sphere = state.is360SphereMode,
                        isCapturing = state.isPanoramaCapturing,
                        capturedFrames = state.panoramaCapturedFrames,
                        direction = state.panoramaDirection,
                        verticalDriftDeg = state.panoramaVerticalDriftDeg,
                        panSpeedDegPerSec = state.panoramaSpeedDegPerSec,
                        guidanceHint = state.panoramaGuidanceHint,
                        guidanceSeverity = state.panoramaGuidanceSeverity,
                        previewStrip = panoramaPreviewStrip,
                        onToggle360 = { viewModel.onToggle360SphereMode() },
                        onToggleDirection = { viewModel.onTogglePanoramaDirection() },
                        onFinishStitch = { viewModel.finishPanoramaCapture() },
                        onCancelCapture = { viewModel.cancelPanoramaCapture() }
                    )
                }

                // Spatial 180° VR Hemisphere & 360° Full Sphere AR Guidance Overlay
                if (state.activeMode == "SPATIAL") {
                    SpatialGuidanceOverlay(
                        fovDeg = state.spatialFovDeg,
                        isCapturing = state.isSpatialCapturing,
                        nodes = state.spatialNodes,
                        capturedCount = state.spatialCapturedCount,
                        totalNodes = state.spatialTotalNodes,
                        relYawDeg = state.spatialRelYawDeg,
                        relPitchDeg = state.spatialRelPitchDeg,
                        rollDeg = state.rollDeg,
                        autoCapture = state.spatialAutoCapture,
                        onSelectFov = { viewModel.onSetSpatialFov(it) },
                        onFinishStitch = { viewModel.finishSpatialCapture() },
                        onCancelCapture = { viewModel.cancelSpatialCapture() }
                    )
                }

                // Blind Viewfinder / Cinema Headless Mode (active during recording)
                if (state.viewfinderDisabled && state.isRecording) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable { viewModel.onToggleViewfinderDisabled() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isRecording) {
                            // Cinema Headless OLED Monitor matching Flutter 1:1
                            val m = (state.recordingSeconds / 60).toString().padStart(2, '0')
                            val s = (state.recordingSeconds % 60).toString().padStart(2, '0')
                            val timeStr = "$m:$s"
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(CinemaAmber.copy(alpha = 0.20f))
                                        .border(1.2.dp, CinemaAmber, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "CINEMA BLIND VF · 100% ISP DEDICATED",
                                            color = CinemaAmber,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.6.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = timeStr,
                                    color = CinemaTextLight,
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                val dispRes = when {
                                    state.videoResolution.contains("3840") -> "4K"
                                    state.videoResolution.contains("1920") -> "1080p"
                                    state.videoResolution.contains("1280") -> "720p"
                                    else -> state.videoResolution
                                }
                                Text(
                                    text = "$dispRes · ${String.format(java.util.Locale.US, "%.1f", state.videoFpsFloat)} FPS · ${state.videoBitrateBps / 1_000_000} Mbps",
                                    color = AppleYellow,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                // Real-time Audio VU meter
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CinemaSurface)
                                        .border(0.8.dp, CinemaGlassBorder, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text("AUDIO", color = CinemaTextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(CinemaSurfaceElevated)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(state.audioNorm.coerceIn(0.01f, 1f))
                                                .fillMaxHeight()
                                                .background(if (state.audioNorm > 0.85f) RecordingRed else CinemaGreen)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "${state.audioDb.toInt()} dB",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    "Viewfinder preview detached to guarantee zero dropped frames.\n100% throughput dedicated to hardware encoder.",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = AppleYellow, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("VIEWFINDER DETACHED (BLIND MODE)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Tap anywhere to restore live preview", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Automation Mode HUD & Overlays
                if (state.activeMode == "AUTOMATION") {
                    // 1. Countdown Overlay (3s / 5s timer)
                    if (state.automationCountdownSec > 0) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x88000000))
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xCC000000))
                                        .border(3.dp, AppleYellow, CircleShape)
                                ) {
                                    Text(
                                        text = "${state.automationCountdownSec}",
                                        color = AppleYellow,
                                        fontSize = 54.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "STARTING AUTOMATION IN...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    // 2. Live Automation Motion HUD during sequence execution
                    if ((state.isAutomationRunning || state.isRecording) && state.automationCountdownSec == 0) {
                        val preset = com.txor.commix.automation.AutomationEngine.getPresetById(state.automationPresetId)
                        val title = if (state.automationPresetId == "custom_motion") "CUSTOM MOTION" else preset.title
                        val elapsed = state.automationElapsedSec.toInt()
                        val total = state.automationDurationSec.toInt()
                        val elapsedMin = elapsed / 60
                        val elapsedSec = elapsed % 60
                        val totalMin = total / 60
                        val totalSec = total % 60
                        val timeStr = String.format(Locale.US, "%02d:%02d / %02d:%02d", elapsedMin, elapsedSec, totalMin, totalSec)
                        val pct = (state.automationProgress * 100).toInt()

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = if (state.isRecording) 74.dp else 46.dp, start = 12.dp, end = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xEE121214))
                                .border(1.5.dp, AppleYellow, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.ElectricBolt,
                                            contentDescription = null,
                                            tint = AppleYellow,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = title.uppercase(),
                                            color = AppleYellow,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = timeStr,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AppleYellow)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$pct%",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                // Progress Bar
                                LinearProgressIndicator(
                                    progress = { state.automationProgress },
                                    color = AppleYellow,
                                    trackColor = Color(0x33E5A93C),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )

                                // Live Parameter Badges
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val zText = String.format(Locale.US, "%.1f×", state.zoomRatio)
                                    val evText = String.format(Locale.US, "%+.1f", state.evStops)
                                    val focText = if (state.focusDiopters >= 0f) String.format(Locale.US, "%.2f D", state.focusDiopters) else "AF"
                                    val wbText = if (state.wbKelvin > 0) "${state.wbKelvin}K" else "AWB"

                                    AutomationParamBadge(label = "ZOOM", value = zText, modifier = Modifier.weight(1f))
                                    AutomationParamBadge(label = "EV", value = evText, modifier = Modifier.weight(1f))
                                    AutomationParamBadge(label = "FOCUS", value = focText, modifier = Modifier.weight(1f))
                                    AutomationParamBadge(label = "WB", value = wbText, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // 3. Idle Quick Action Pill (lifted cleanly above Multi-Lens selector)
                    if (!state.isAutomationRunning && !state.isRecording) {
                        val preset = com.txor.commix.automation.AutomationEngine.getPresetById(state.automationPresetId)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 60.dp, start = 12.dp, end = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xCC000000))
                                .border(1.dp, AppleYellow, RoundedCornerShape(14.dp))
                                .clickable { showAutomationModal = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = AppleYellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AUTOMATION: ${preset.title.uppercase()} (${preset.durationSeconds.toInt()}s)",
                                    color = AppleYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Ultra Zoom AI Engine Active Badge (offset cleanly below top pills / telemetry strip)
                val showProTelemetryStrip = !isCleanActive && (state.activeMode == "PRO" || state.showScopes || state.aeAfLocked || state.focusStackEnabled || state.multiExposureMode != "off")
                if (state.zoomRatio >= 3.0 && state.activeMode != "PANORAMA" && state.activeMode != "SPATIAL") {
                    val zoomTopPad = when {
                        state.isRecording && showProTelemetryStrip -> 96.dp
                        state.isRecording -> 72.dp
                        showProTelemetryStrip -> 74.dp
                        else -> 44.dp
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = zoomTopPad, start = 12.dp, end = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x665856D6))
                            .border(0.8.dp, Color(0xFF5856D6), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFBF5AF2),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "ULTRA ZOOM AI · %.1f×", state.zoomRatio),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Scopes, Multi-Grid Guides, False Color, Zebras, Safe-Area, RGB Parade, Center Reticle, and Vectorscope
                ScopesOverlay(
                    showWaveform = state.showWaveform,
                    showHistogram = state.showHistogram,
                    showRgbParade = state.showRgbParade || state.showScopes,
                    showHorizon = state.showHorizonLeveler,
                    showGrid = state.showGrid,
                    gridType = state.gridType,
                    showVectorscope = state.showVectorscope || state.showScopes,
                    showZebras = state.showZebras,
                    zebraThresholdIre = state.zebraThresholdIre,
                    showFalseColor = state.showFalseColor,
                    showSafeAreaGuides = state.showSafeAreaGuides,
                    lumaGrid = state.lumaGrid,
                    lumaGridW = state.lumaGridW,
                    lumaGridH = state.lumaGridH,
                    rollDeg = state.rollDeg,
                    pitchDeg = state.pitchDeg,
                    waveformCols = state.waveformCols,
                    paradeR = state.paradeR,
                    paradeG = state.paradeG,
                    paradeB = state.paradeB,
                    lumaHistogram = state.lumaHistogram,
                    vectorscopeSamples = state.vectorscopeSamples,
                    vectorscopeCount = state.vectorscopeCount
                )

                // Live Pro Exposure / Focus Peak / Hyperfocal / AE-AF Lock Telemetry Strip
                if (showProTelemetryStrip && state.activeMode != "PANORAMA" && state.activeMode != "SPATIAL") {
                    val liveIsoDisp = if (state.iso > 0) state.iso else state.liveIso
                    val liveShutterDispNs = if (state.shutterNs > 0L) state.shutterNs else state.liveShutterNs
                    val shutterStr = if (liveShutterDispNs >= 1_000_000_000L) {
                        String.format(Locale.US, "%.1fs", liveShutterDispNs / 1e9)
                    } else {
                        "1/${(1_000_000_000L / liveShutterDispNs.coerceAtLeast(1L))}"
                    }
                    val hfStr = if (state.hyperfocalMeters > 0.05f) String.format(Locale.US, "HF %.1fm", state.hyperfocalMeters) else ""
                    val peakPct = state.focusPeakScore.toInt().coerceIn(0, 100)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (state.isRecording) 64.dp else 44.dp, start = 10.dp, end = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xAA000000))
                            .border(0.6.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("ISO $liveIsoDisp", color = CinemaTextLight, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                        Text(shutterStr, color = CinemaTextLight, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                        Text("IRE ${state.spotIre.toInt()}", color = AppleYellow, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                        Text("PK $peakPct%", color = if (peakPct > 25) CinemaGreen else CinemaTextDim, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                        if (hfStr.isNotEmpty()) {
                            Text(hfStr, color = CinemaCyan, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (state.aeAfLocked) AppleYellow else Color(0x26FFFFFF))
                                .clickable {
                                    CommixHaptics.click(context, state.hapticsEnabled)
                                    viewModel.onToggleAeAfLock()
                                }
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (state.aeAfLocked) "AE/AF LOCK" else "AE/AF",
                                color = if (state.aeAfLocked) Color.Black else Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Long Exposure style, frame count, and e-ND controls live in ModeContextControlBar below viewfinder

                // Automation Armed Listening Banner (Audio Clap / Gyro Shake)
                state.automationListeningLabel?.let { listenText ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xDD000000))
                            .border(1.5.dp, AppleYellow, RoundedCornerShape(18.dp))
                            .clickable { viewModel.stopAutomation() }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = listenText,
                                color = AppleYellow,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "TAP TO CANCEL",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Self-Timer Countdown Overlay
                if (state.selfTimerCountdown > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xB3000000))
                            .border(2.dp, AppleYellow, CircleShape)
                            .clickable { viewModel.onCancelSelfTimer() }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${state.selfTimerCountdown}",
                                color = AppleYellow,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "CANCEL",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Samsung One UI Composition & Horizon Suggestion (hidden in PANORAMA/SPATIAL to avoid center reticle clash)
                if (!isCleanActive && !state.isRecording && state.activeMode != "PANORAMA" && state.activeMode != "SPATIAL") {
                    ShotSuggestionWidget(
                        shouldShowShootNow = state.shouldShowShootNow,
                        bestShotScore = state.bestShotScore,
                        rollDeg = state.rollDeg,
                        pitchDeg = state.pitchDeg
                    )
                }

                // Interactive Focus Loupe (live zoom magnification + manual diopter stepper)
                if (showFocusLoupe) {
                    FocusLoupe(
                        currentFocusDiopters = state.focusDiopters,
                        focusPeakScore = state.focusPeakScore,
                        currentZoomRatio = state.zoomRatio,
                        onStepFocus = { viewModel.onStepFocusDiopters(it) },
                        onSetFocus = { viewModel.onFocusChanged(it) },
                        onMagnificationChanged = { viewModel.onZoomChanged(it) },
                        onClose = { showFocusLoupe = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 12.dp)
                    )
                }

                // Tap Focus Reticle
                tapFocusPoint?.let { (fx, fy) ->
                    FocusRing(
                        x = fx,
                        y = fy,
                        evStops = state.evStops,
                        onEvChanged = { viewModel.onEvChanged(it) },
                        animationsEnabled = state.animationsEnabled
                    )
                }

                // Recording HUD with timecode and audio VU meter
                if (state.isRecording) {
                    RecordingHud(
                        isRecording = state.isRecording,
                        seconds = state.recordingSeconds,
                        audioDb = state.audioDb,
                        resolution = state.videoResolution,
                        fps = state.videoFpsFloat,
                        bitrateBps = state.videoBitrateBps,
                        remainingMinutes = state.estimatedTimeRemainingMinutes,
                        showStorageRemaining = state.showStorageRemaining,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    )
                }


                // Clean View Exit Pill (Top-Left corner of viewfinder)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 14.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CinemaGlass)
                        .border(0.7.dp, CinemaGlassBorder, RoundedCornerShape(12.dp))
                        .testTag("clean_view_pill")
                        .clickable {
                            CommixHaptics.click(context, state.hapticsEnabled)
                            viewModel.onToggleCleanViewfinder()
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Clean View",
                            tint = AppleYellow,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isCleanActive) "CLEAN VIEW (TAP TO EXIT)" else "CLEAN VIEW",
                            color = CinemaTextLight,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                // Job Queue HUD Pill (top-right of viewfinder overlay when work in progress)
                if (!isCleanActive && queue.hasWorkInProgress) {
                    val qActive = queue.activeJobCount
                    val qUnfinished = qActive + queue.pendingJobCount
                    val qPct = (queue.overallProgress * 100).toInt()

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 14.dp, top = 14.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xCC000000))
                            .border(0.8.dp, AppleYellow, RoundedCornerShape(12.dp))
                            .clickable { showJobQueueModal = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (qActive > 0) {
                                CircularProgressIndicator(
                                    color = AppleYellow,
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = AppleYellow,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "QUEUE: $qUnfinished ($qPct%)",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // "SHOOT NOW!" Best Shot AI Cue
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.shouldShowShootNow,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 80.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppleYellow)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "SHOOT NOW! (PEAK STILLNESS)",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // ---------------------------------------------------------
                // Floating Universal Multi-Lens Selector Pill
                // Dynamically renders every discovered physical/logical sensor on any phone
                // Hidden during active Panorama/Spatial sweeps to keep viewfinder focused
                // ---------------------------------------------------------
                if (!isCleanActive && !state.isPanoramaCapturing && !state.isSpatialCapturing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp, start = 8.dp, end = 8.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(CinemaGlass)
                            .border(0.7.dp, CinemaGlassBorder, RoundedCornerShape(22.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            val activeCam = state.availableCameras.find { it.cameraId == state.activeCameraId }
                            val isFrontActive = activeCam?.isFront == true
                            val zoom = state.zoomRatio

                            data class DynamicLensPill(
                                val key: String,
                                val label: String,
                                val isSelected: Boolean,
                                val onClick: () -> Unit
                            )

                            val pills = mutableListOf<DynamicLensPill>()

                            if (isFrontActive) {
                                val frontCams = state.availableCameras
                                    .filter { it.isFront && !it.isDepth }
                                    .sortedBy { it.opticalZoomFactor }
                                if (frontCams.size <= 1) {
                                    val is1x = zoom < 1.35
                                    val is1_4x = zoom >= 1.35
                                    pills.add(DynamicLensPill("1x", "1×", is1x) { viewModel.onZoomChanged(1.0) })
                                    if (state.hardwareMaxDigitalZoom >= 1.5) {
                                        pills.add(DynamicLensPill("1.4x", "1.4×", is1_4x) { viewModel.onZoomChanged(1.4) })
                                    }
                                } else {
                                    frontCams.forEach { fCam ->
                                        val isSel = (fCam.cameraId == state.activeCameraId)
                                        val label = if (fCam.isUltraWide) "0.7×" else "1×"
                                        pills.add(DynamicLensPill(fCam.cameraId, label, isSel) {
                                            viewModel.onSelectLensPill(fCam.cameraId, 1.0)
                                        })
                                    }
                                }
                            } else {
                                val rearCams = state.availableCameras.filter { !it.isFront && !it.isDepth }
                                val mainCam = rearCams.firstOrNull { !it.isMacro && !it.isUltraWide && !it.isTelephoto && !it.isMonochrome }
                                    ?: rearCams.firstOrNull()
                                val hasPhysicalTele = rearCams.any { it.isTelephoto || it.isPeriscope }

                                // Sort optical rear lenses by opticalZoomFactor (e.g., 0.5x, 0.6x, 1x, 2x, 3x, 5x, 10x)
                                val opticalCams = rearCams
                                    .filter { !it.isMacro && !it.isMonochrome }
                                    .sortedBy { it.opticalZoomFactor }
                                val seenOpticalLabels = mutableSetOf<String>()

                                opticalCams.forEach { cam ->
                                    val isMain = cam.cameraId == mainCam?.cameraId
                                    val label = if (isMain) "1×" else LensProbeService.formatLensBadge(cam)
                                    if (seenOpticalLabels.add(label) || cam.cameraId == state.activeCameraId) {
                                        val isSel = if (isMain) {
                                            (state.activeCameraId == cam.cameraId) && (hasPhysicalTele || zoom < 1.6)
                                        } else {
                                            (state.activeCameraId == cam.cameraId)
                                        }
                                        pills.add(DynamicLensPill(cam.cameraId, label, isSel) {
                                            viewModel.onSelectLensPill(cam.cameraId, 1.0)
                                        })
                                    }
                                    // Offer lossless-crop 2x step right after main 1x when no dedicated tele sensor exists
                                    if (isMain && !hasPhysicalTele && state.hardwareMaxDigitalZoom >= 2.0) {
                                        val is2xSel = (state.activeCameraId == cam.cameraId) && (zoom >= 1.6)
                                        pills.add(DynamicLensPill("main_2x", "2×", is2xSel) {
                                            viewModel.onSelectLensPill(cam.cameraId, 2.0)
                                        })
                                    }
                                }

                                // Dedicated Macro physical sensors
                                rearCams.filter { it.isMacro && it.cameraId != mainCam?.cameraId }.forEach { macroCam ->
                                    val isSel = (state.activeCameraId == macroCam.cameraId)
                                    pills.add(DynamicLensPill(macroCam.cameraId, "MACRO", isSel) {
                                        viewModel.onSelectLensPill(macroCam.cameraId, 1.0)
                                    })
                                }

                                // Dedicated Monochrome physical sensors
                                rearCams.filter { it.isMonochrome }.forEach { monoCam ->
                                    val isSel = (state.activeCameraId == monoCam.cameraId)
                                    pills.add(DynamicLensPill(monoCam.cameraId, "MONO", isSel) {
                                        viewModel.onSelectLensPill(monoCam.cameraId, 1.0)
                                    })
                                }
                            }

                            pills.forEach { pill ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .height(34.dp)
                                        .defaultMinSize(minWidth = 34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(if (pill.isSelected) Color(0x88000000) else Color.Transparent)
                                        .then(
                                            if (pill.isSelected) Modifier.border(1.2.dp, AppleYellow.copy(alpha = 0.85f), RoundedCornerShape(17.dp))
                                            else Modifier
                                        )
                                        .clickable {
                                            CommixHaptics.click(context, state.hapticsEnabled)
                                            pill.onClick()
                                        }
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = pill.label,
                                        color = if (pill.isSelected) AppleYellow else Color.White,
                                        fontWeight = if (pill.isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = if (pill.label.length >= 4) 10.5.sp else 12.5.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Universal Sensor Hub & Prober button [:: LENS]
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp, end = 2.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppleYellow.copy(alpha = 0.16f))
                                    .border(0.8.dp, AppleYellow.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                    .testTag("lens_modal_button")
                                    .clickable {
                                        CommixHaptics.click(context, state.hapticsEnabled)
                                        showLensModal = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Hexagon,
                                        contentDescription = "All Hardware Lenses",
                                        tint = AppleYellow,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LENS",
                                        color = AppleYellow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. BOTTOM CONTROLS CONTAINER (Pure Black background)
        // -------------------------------------------------------------
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(CinemaBlack)
                .padding(bottom = 36.dp + navBottom, top = 4.dp)
                .zIndex(20f)
        ) {
            // Pro Controls Expandable Drawer
            AnimatedVisibility(visible = state.showProControls) {
                ProSettingsCards(
                    activeTab = activeProTab,
                    onTabSelected = { activeProTab = it },
                    currentIso = state.iso,
                    onIsoSelected = { viewModel.onIsoChanged(it) },
                    currentShutterNs = state.shutterNs,
                    onShutterSelected = { viewModel.onShutterChanged(it) },
                    currentShutterAngle = state.shutterAngleDeg,
                    shutterAngleLocked = state.shutterAngleLocked,
                    onShutterAngleSelected = { viewModel.onShutterAngleChanged(it) },
                    onToggleShutter180Lock = { viewModel.onToggleShutter180Lock() },
                    videoFps = state.videoFps,
                    currentEv = state.evStops,
                    onEvSelected = { viewModel.onEvChanged(it) },
                    currentWb = state.wbKelvin,
                    onWbSelected = { viewModel.onWbChanged(it) },
                    wbTint = state.wbTint,
                    onWbTintChanged = { viewModel.onWbTintChanged(it) },
                    currentFocus = state.focusDiopters,
                    onFocusSelected = { viewModel.onFocusChanged(it) },
                    currentZoom = state.zoomRatio,
                    maxZoom = state.maxZoomRatio,
                    onZoomSelected = { viewModel.onZoomChanged(it) },
                    spaceZoomLock = state.spaceZoomLockEngaged,
                    onToggleSpaceZoomLock = { viewModel.onToggleSpaceZoomLock() },
                    currentCurve = state.toneCurve,
                    onCurveSelected = { viewModel.onToneCurveChanged(it) },
                    currentGrain = state.filmGrain,
                    onGrainSelected = { viewModel.onFilmGrainChanged(it) },
                    grainIntensity = state.grainIntensity,
                    onGrainIntensityChanged = { viewModel.onGrainIntensityChanged(it) },
                    currentLut = state.lutLook,
                    onLutSelected = { viewModel.onLutChanged(it) },
                    lutBlend = state.lutBlend,
                    onLutBlendChanged = { viewModel.onLutBlendChanged(it) },
                    currentNd = state.ndFilter,
                    onNdSelected = { viewModel.onNdFilterChanged(it) },
                    currentAnamorphic = state.anamorphicRatio,
                    onAnamorphicSelected = { viewModel.onAnamorphicChanged(it) },
                    aebEnabled = state.aebEnabled,
                    aebShots = state.aebShots,
                    aebSpreadEv = state.aebSpreadEv,
                    aebHdrFusion = state.aebHdrFusion,
                    onAebSelected = { en, shots, sp -> viewModel.onAebChanged(en, shots, sp) },
                    onToggleAebHdrFusion = { viewModel.onToggleAebHdrFusion() },
                    currentAudioGain = state.audioGainDb,
                    onAudioGainChanged = { viewModel.onAudioGainChanged(it) },
                    audioLowCut = state.audioLowCut,
                    onToggleAudioLowCut = { viewModel.onToggleAudioLowCut() },
                    isProcessZero = state.isProcessZeroActive,
                    onToggleProcessZero = { viewModel.onToggleProcessZero() },
                    processZeroIntensity = state.processZeroIntensity,
                    onProcessZeroIntensityChanged = { viewModel.onProcessZeroIntensityChanged(it) },
                    activeCamera = state.availableCameras.find { it.cameraId == state.activeCameraId },
                    showHistogram = state.showHistogram,
                    onToggleHistogram = { viewModel.toggleHistogram() },
                    showWaveform = state.showWaveform,
                    onToggleWaveform = { viewModel.toggleWaveform() },
                    showRgbParade = state.showRgbParade,
                    onToggleRgbParade = { viewModel.toggleRgbParade() },
                    showVectorscope = state.showVectorscope,
                    onToggleVectorscope = { viewModel.toggleVectorscope() },
                    showHorizon = state.showHorizonLeveler,
                    onToggleHorizon = { viewModel.toggleHorizon() },
                    showGrid = state.showGrid,
                    onToggleGrid = { viewModel.toggleGrid() },
                    showZebras = state.showZebras,
                    zebraThresholdIre = state.zebraThresholdIre,
                    onToggleZebras = { viewModel.onToggleZebras() },
                    onCycleZebraThreshold = { viewModel.onCycleZebraThreshold() },
                    showFalseColor = state.showFalseColor,
                    onToggleFalseColor = { viewModel.onToggleFalseColor() },
                    showSafeAreaGuides = state.showSafeAreaGuides,
                    onToggleSafeAreaGuides = { viewModel.onToggleSafeAreaGuides() },
                    focusPointA = state.focusPointA,
                    focusPointB = state.focusPointB,
                    focusPullDurationSec = state.focusPullDurationSec,
                    isFocusPullRunning = state.isFocusPullRunning,
                    onSetFocusPointA = { viewModel.onSetFocusPointA() },
                    onSetFocusPointB = { viewModel.onSetFocusPointB() },
                    onClearFocusPullPoints = { viewModel.onClearFocusPullPoints() },
                    onSetFocusPullDuration = { viewModel.onSetFocusPullDuration(it) },
                    onTriggerFocusPull = { viewModel.onTriggerFocusPull(it) },
                    savedCustomSlots = state.savedCustomSlots,
                    activeCustomSlot = state.activeCustomSlot,
                    onSaveCustomPreset = { viewModel.onSaveCustomPreset(it) },
                    onLoadCustomPreset = { viewModel.onLoadCustomPreset(it) },
                    aeAfLocked = state.aeAfLocked,
                    onToggleAeAfLock = { viewModel.onToggleAeAfLock() },
                    lensCorrectionEnabled = state.lensCorrectionEnabled,
                    onToggleLensCorrection = { viewModel.onToggleLensCorrection() },
                    focusStackEnabled = state.focusStackEnabled,
                    onToggleFocusStack = { viewModel.onToggleFocusStack() },
                    multiExposureMode = state.multiExposureMode,
                    onMultiExposureModeSelected = { viewModel.onSetMultiExposureMode(it) },
                    longExposureStyle = state.longExposureStyle,
                    onLongExposureStyleSelected = { viewModel.onSetLongExposureStyle(it) },
                    onOpenSlate = { showSlateSheet = true },
                    onOpenKeyframePresets = { showKeyframeModal = true }
                )
            }


            // Tactile Rotary Dial for EV (active when EV tab is selected)
            if (!isCleanActive && state.showProControls && activeProTab == ProControlTab.EV) {
                val activeCam = state.availableCameras.find { it.cameraId == state.activeCameraId }
                val evStep = activeCam?.evStep ?: 0.1f
                val minEv = if (activeCam?.evRange != null) activeCam.evRange.lower * evStep else -3.0f
                val maxEv = if (activeCam?.evRange != null) activeCam.evRange.upper * evStep else 3.0f

                RotaryDial(
                    value = state.evStops,
                    minValue = minEv,
                    maxValue = maxEv,
                    step = evStep,
                    label = "EV COMPENSATION",
                    valueFormatter = { String.format(java.util.Locale.US, "%+.1f EV", it) },
                    onValueChanged = { viewModel.onEvChanged(it) },
                    hapticsEnabled = state.hapticsEnabled
                )
            }

            // Mode-Specific Dedicated Pro Control Strip (all 11 modes)
            if (!isCleanActive && !state.isRecording) {
                ModeContextControlBar(
                    state = state,
                    viewModel = viewModel,
                    onOpenProTab = { tab ->
                        activeProTab = tab
                        if (!state.showProControls) viewModel.toggleProControls()
                    },
                    onOpenFormatModal = { showFormatModal = true },
                    onOpenSlateSheet = { showSlateSheet = true },
                    onOpenAutomationModal = { showAutomationModal = true },
                    onOpenKeyframeModal = { showKeyframeModal = true },
                    onOpenSettingsTab = { tab ->
                        settingsInitialTab = tab
                        showMainSettings = true
                    },
                    onOpenStabilizationSheet = { showStabilizationSheet = true },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Horizontal Pill Mode Carousel (all 11 modes)
            ModeCarousel(
                modes = modes,
                activeMode = state.activeMode,
                onModeSelected = { viewModel.onModeSelected(it) },
                hapticsEnabled = state.hapticsEnabled
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Shutter Row: Gallery Rounded Square, Dual-Ring Shutter, Camera Switcher
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                // Left: Gallery Thumbnail Button (50x50, 12dp radius + green media indicator dot)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CinemaSurface)
                        .border(0.8.dp, CinemaGlassBorder, RoundedCornerShape(12.dp))
                        .testTag("gallery_button")
                        .clickable {
                            CommixHaptics.click(context, state.hapticsEnabled)
                            viewModel.openGallery(context)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = "Gallery",
                        tint = CinemaTextLight,
                        modifier = Modifier.size(23.dp)
                    )
                    if (state.lastSavedUriString != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CinemaGreen)
                        )
                    }
                }

                // Center: Dual-Ring Shutter Button with smooth animated morphing & capture progress
                val isPanoCapturing = state.activeMode == "PANORAMA" && state.isPanoramaCapturing
                val isSpatialMode = state.activeMode == "SPATIAL"
                val isSpatialCapturing = isSpatialMode && state.isSpatialCapturing
                val isNightOrLongExp = state.activeMode == "NIGHT" || state.activeMode == "LONG EXP"
                val isActiveStopSquare = state.isRecording || isPanoCapturing || (isSpatialCapturing && state.spatialAutoCapture)
                val outerRingColor = when {
                    isSpatialMode -> CinemaCyan
                    isPanoCapturing -> AppleYellow
                    isVideoMode -> RecordingRed
                    isNightOrLongExp -> CinemaAmber
                    else -> Color.White
                }
                val innerCircleColor = when {
                    isSpatialMode -> CinemaCyan
                    isPanoCapturing -> AppleYellow
                    isVideoMode -> RecordingRed
                    isNightOrLongExp -> CinemaAmber
                    else -> Color.White
                }
                val innerSize by animateDpAsState(
                    targetValue = if (isActiveStopSquare) 28.dp else if (state.isCapturing) 54.dp else 62.dp,
                    label = "shutter_inner_size"
                )
                val innerRadius by animateDpAsState(
                    targetValue = if (isActiveStopSquare) 7.dp else 31.dp,
                    label = "shutter_inner_radius"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(78.dp)
                        .border(3.5.dp, outerRingColor, CircleShape)
                        .testTag("shutter_button")
                        .clickable {
                            CommixHaptics.heavy(context, state.hapticsEnabled)
                            viewModel.onShutterTapped()
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(innerSize)
                            .clip(RoundedCornerShape(innerRadius))
                            .background(innerCircleColor)
                    )
                    if (state.isCapturing && !isActiveStopSquare) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Right: Camera Switcher Button (50x50, circle)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0x1EFFFFFF))
                        .border(0.8.dp, CinemaGlassBorder, CircleShape)
                        .testTag("switch_camera_button")
                        .clickable {
                            CommixHaptics.click(context, state.hapticsEnabled)
                            viewModel.onSwitchCameraTapped()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 4. BOTTOM SHEET MODALS
        // -------------------------------------------------------------
        if (showLensModal) {
            HardwareLensModal(
                activeCameraId = state.activeCameraId,
                cameras = state.availableCameras,
                onSelectCamera = { id ->
                    viewModel.onSelectCamera(id)
                    showLensModal = false
                },
                onProbeAll = { viewModel.onProbeAllCameras() },
                onDismiss = { showLensModal = false }
            )
        }

        if (showMpModal) {
            MegapixelLimitsModal(
                currentMp = state.targetPhotoMp,
                maxMp = state.maxLensMp,
                isHiRes = state.isHiResActive,
                onMpSelected = { mp -> viewModel.onUpdatePhotoMp(mp) },
                onDismiss = { showMpModal = false }
            )
        }

        if (showJobQueueModal) {
            JobQueueModal(
                onDismiss = { showJobQueueModal = false }
            )
        }

        if (showFormatModal) {
            ProVideoFormatModal(
                currentResolution = state.videoResolution,
                currentFpsFloat = state.videoFpsFloat,
                currentBitrateBps = state.videoBitrateBps,
                isViewfinderDisabled = state.viewfinderDisabled,
                availableResolutions = viewModel.getSupportedVideoSizes(),
                availableFpsList = viewModel.getSupportedFpsList(),
                hapticsEnabled = state.hapticsEnabled,
                onResolutionSelected = { res ->
                    val newMax = PipelineThroughputEngine.getMaxFpsForResolution(
                        resolution = res,
                        isVfDisabled = state.viewfinderDisabled
                    )
                    val clampedFps = if (state.videoFpsFloat > newMax) newMax else state.videoFpsFloat
                    viewModel.onUpdateVideoFormat(res, clampedFps, state.videoBitrateBps)
                },
                onFpsSelected = { fps ->
                    viewModel.onUpdateVideoFormat(state.videoResolution, fps, state.videoBitrateBps)
                },
                onBitrateSelected = { br ->
                    viewModel.onUpdateVideoFormat(state.videoResolution, state.videoFpsFloat, br)
                },
                onToggleDisableVf = { disabled ->
                    viewModel.onSetViewfinderDisabled(disabled)
                    val newMax = PipelineThroughputEngine.getMaxFpsForResolution(
                        resolution = state.videoResolution,
                        isVfDisabled = disabled
                    )
                    if (state.videoFpsFloat > newMax) {
                        viewModel.onUpdateVideoFormat(state.videoResolution, newMax, state.videoBitrateBps)
                    }
                },
                onDismiss = { showFormatModal = false }
            )
        }

        if (showSlateSheet) {
            val slateSpec = "${state.videoResolution} • ${state.videoFpsFloat.toInt()} FPS • ${state.videoCodec.uppercase()}"
            CinemaSlateSheet(
                roll = state.slateRoll,
                scene = state.slateScene,
                take = state.slateTake,
                specLabel = slateSpec,
                onUpdateSlate = { r, s, t -> viewModel.onUpdateSlate(r, s, t) },
                onPlayTone = { viewModel.onClapSlate() },
                onDismiss = { showSlateSheet = false }
            )
        }

        if (showKeyframeModal) {
            KeyframeEditorModal(
                activePresetId = state.automationPresetId,
                onSelectPreset = { preset ->
                    viewModel.onSelectAutomationPreset(preset.id)
                    viewModel.showStatus("Selected: ${preset.title}")
                },
                onArmShot = {
                    viewModel.onModeSelected("AUTOMATION")
                    viewModel.showStatus("Automation Armed — Tap Shutter to Begin")
                },
                onDismiss = { showKeyframeModal = false }
            )
        }

        if (showMainSettings) {
            MainSettingsModal(
                state = state,
                viewModel = viewModel,
                initialTab = settingsInitialTab,
                onDismiss = {
                    showMainSettings = false
                    settingsInitialTab = null
                }
            )
        }

        if (showAutomationModal) {
            AutomationModal(
                activePresetId = state.automationPresetId,
                activeTriggerMode = state.automationTriggerMode,
                maxZoom = state.maxZoomRatio,
                onSelectPreset = { presetId ->
                    viewModel.onSelectAutomationPreset(presetId)
                },
                onSelectTrigger = { trigger ->
                    viewModel.onSetAutomationTrigger(trigger)
                },
                onStartPreset = { presetId, trigger ->
                    viewModel.onSelectAutomationPreset(presetId)
                    viewModel.onSetAutomationTrigger(trigger)
                    viewModel.startPresetAutomation(presetId, trigger)
                    showAutomationModal = false
                },
                onStartCustom = { dur, sz, ez, sev, eev, sfoc, efoc, trigger ->
                    viewModel.onSetAutomationTrigger(trigger)
                    viewModel.startCustomAutomation(dur, sz, ez, sev, eev, sfoc, efoc, trigger)
                    showAutomationModal = false
                },
                onDismiss = { showAutomationModal = false }
            )
        }
        if (showStabilizationSheet) {
            StabilizationSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { showStabilizationSheet = false }
            )
        }
    }

    // Astrophotography Dark-Adaptation Red Vision HUD Tint (touch-passthrough)
    if (state.activeMode == "NIGHT" && state.nightRedUiEnabled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RecordingRed.copy(alpha = 0.16f))
        )
    }

    // Floating status toast pill
    StatusPill(
        message = state.statusMessage,
        onDismiss = { viewModel.clearStatusMessage() },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 54.dp)
    )
}
}

@Composable
private fun AutomationParamBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x55000000))
            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(1.dp))
            Text(text = value, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}
