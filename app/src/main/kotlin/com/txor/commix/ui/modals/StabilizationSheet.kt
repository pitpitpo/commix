package com.txor.commix.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.StabilizationPolicyEngine
import com.txor.commix.model.CameraUiState
import com.txor.commix.ui.CommixViewModel
import com.txor.commix.ui.components.*
import com.txor.commix.ui.theme.*

/**
 * Unified Hardware + Software Stabilization Control Sheet.
 * Displays real probed per-lens capabilities (VCM OIS, HAL EIS, Android 13+ Preview Stab, 6-axis Gyro),
 * lets the cinematographer choose the stabilization engine AND exact application timing
 * (Always / Capture Only / Preview Only / Post Only), and toggles Photo Auto-Level + Gyroflow .gcsv export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StabilizationSheet(
    state: CameraUiState,
    viewModel: CommixViewModel,
    onDismiss: () -> Unit
) {
    val activeCam = state.availableCameras.find { it.cameraId == state.activeCameraId }
    val hasOis = activeCam?.hasOis == true
    val hasEis = activeCam?.hasEis == true
    val hasPreviewStab = activeCam?.hasPreviewStabilization == true
    val hasGyro = state.hasGyroSensor
    val engineMode = StabilizationPolicyEngine.normalizeEngineMode(state.stabilizationEngineMode)
    val applyWhen = StabilizationPolicyEngine.normalizeApplyWhen(state.stabilizationApplyWhen)
    val isSweepMode = state.activeMode == "PANORAMA" || state.activeMode == "SPATIAL"

    CommixModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            CommixSheetHeader(
                title = "Stabilization Engine",
                subtitle = "Hardware OIS, HAL EIS, Preview Stab & IMU software pipeline",
                icon = Icons.Default.Shield,
                badgeText = state.stabilizationActiveLabel.ifBlank { engineMode.uppercase() },
                onDismiss = onDismiss
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Live Pipeline & Probed Hardware Capabilities
                CommixSectionCard(title = "Active Lens Hardware Telemetry") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CinemaSurfaceElevated)
                            .border(0.8.dp, AppleYellow.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "ACTIVE PIPELINE",
                                color = CinemaTextDim,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp
                            )
                            Text(
                                text = state.stabilizationActiveLabel.ifBlank { "HYBRID OIS+EIS" },
                                color = AppleYellow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        CommixBadgePill(
                            text = "CAM ${state.activeCameraId}",
                            color = CinemaCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    CapabilityRow(
                        label = "Optical VCM Stabilization (OIS)",
                        supported = hasOis,
                        yesText = "HARDWARE [VCM]",
                        noText = "SW FALLBACK"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CapabilityRow(
                        label = "Electronic Video Stab (HAL EIS)",
                        supported = hasEis,
                        yesText = "HARDWARE [HAL]",
                        noText = "GYRO POST"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CapabilityRow(
                        label = "Preview Stabilization (API 33+)",
                        supported = hasPreviewStab,
                        yesText = "SUPPORTED",
                        noText = "STANDARD EIS"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CapabilityRow(
                        label = "6-Axis Gyroscope IMU Telemetry",
                        supported = hasGyro,
                        yesText = "LIVE 200Hz+",
                        noText = "UNAVAILABLE"
                    )

                    if (isSweepMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CinemaCyan.copy(alpha = 0.14f))
                                .border(0.7.dp, CinemaCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = CinemaCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${state.activeMode} sweep mode automatically suppresses live EIS FOV crop to preserve exact spherical stitching geometry while retaining optical OIS.",
                                color = CinemaTextLight,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // 2. Stabilization Engine Selector (5 modes)
                CommixSectionCard(title = "Stabilization Engine") {
                    val engineOptions = listOf(
                        Triple(
                            StabilizationPolicyEngine.ENGINE_OFF,
                            "Off (Native Uncropped Sensor)",
                            "Disable OIS & EIS for tripod, gimbal, or pure full-FOV capture."
                        ),
                        Triple(
                            StabilizationPolicyEngine.ENGINE_HARDWARE_OIS,
                            "Hardware OIS (Zero Crop)",
                            if (hasOis) "Uses optical lens-shift VCM actuators with zero resolution loss."
                            else "Lens lacks VCM OIS — gracefully falls back to software EIS / Gyroflow."
                        ),
                        Triple(
                            StabilizationPolicyEngine.ENGINE_SOFTWARE_EIS,
                            "Software / HAL EIS",
                            if (hasEis) "Electronic crop-and-warp video stabilization via HAL + preview stab."
                            else "Lens lacks HAL EIS — records Gyroflow IMU telemetry for post warp."
                        ),
                        Triple(
                            StabilizationPolicyEngine.ENGINE_HYBRID,
                            "Hybrid (Hardware OIS + Software EIS)",
                            "Combines optical OIS micro-jitter absorption with smooth electronic stabilization."
                        ),
                        Triple(
                            StabilizationPolicyEngine.ENGINE_GYRO_POST,
                            "Gyroflow Post-Production (Zero Live Crop)",
                            "Captures full uncropped sensor frame + synchronized .gcsv gyro log for Gyroflow."
                        )
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        engineOptions.forEach { (key, title, desc) ->
                            val isSelected = (engineMode == key)
                            SelectableOptionTile(
                                title = title,
                                subtitle = desc,
                                isSelected = isSelected,
                                accent = if (key == StabilizationPolicyEngine.ENGINE_GYRO_POST) CinemaCyan else AppleYellow,
                                testTagName = "stab_engine_$key",
                                onClick = { viewModel.onSetStabilizationEngineMode(key) }
                            )
                        }
                    }
                }

                // 3. When To Apply Selector (4 timings)
                CommixSectionCard(title = "When To Apply Stabilization") {
                    val timingOptions = listOf(
                        Triple(
                            StabilizationPolicyEngine.APPLY_ALWAYS,
                            "Always (Preview + Capture)",
                            "Active continuously in both the live viewfinder and recorded output."
                        ),
                        Triple(
                            StabilizationPolicyEngine.APPLY_RECORDING_ONLY,
                            "Capture / Record Only",
                            "Viewfinder stays wide & uncropped while framing; stabilization engages only during recording or shutter capture."
                        ),
                        Triple(
                            StabilizationPolicyEngine.APPLY_PREVIEW_ONLY,
                            "Preview Only (Clean Uncropped Capture)",
                            "Stabilizes your handheld framing preview, while saving an uncropped clean file + Gyroflow .gcsv."
                        ),
                        Triple(
                            StabilizationPolicyEngine.APPLY_POST_EXPORT,
                            "Post-Processing Only (Zero Live Crop)",
                            "Disables live EIS crop completely; exports synchronized .gcsv gyro data & applies still Auto-Level post-capture."
                        )
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        timingOptions.forEach { (key, title, desc) ->
                            val isSelected = (applyWhen == key)
                            SelectableOptionTile(
                                title = title,
                                subtitle = desc,
                                isSelected = isSelected,
                                accent = CinemaGreen,
                                testTagName = "stab_when_$key",
                                onClick = { viewModel.onSetStabilizationApplyWhen(key) }
                            )
                        }
                    }
                }

                // 4. Software Horizon Auto-Level & Gyroflow Sidecar Controls
                CommixSectionCard(title = "Software Still & Post-Export Pipeline") {
                    StabToggleRow(
                        icon = Icons.Default.Straighten,
                        title = "Photo Auto-Level Horizon Straightening (±20°)",
                        subtitle = "Uses live IMU roll angle at shutter release to auto-level still photos with exact inscribed aspect-preserving crop (zero black corners).",
                        checked = state.photoAutoLevelEnabled,
                        testTagName = "stab_photo_autolevel_toggle",
                        onCheckedChange = { viewModel.onTogglePhotoAutoLevel() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    StabToggleRow(
                        icon = Icons.Default.TrackChanges,
                        title = "Gyroflow v1.3 .gcsv Sidecar Export",
                        subtitle = "Saves high-rate synchronized gyroscope & accelerometer logs alongside video recordings with camera name & readout calibration.",
                        checked = state.gyroflowExportEnabled,
                        testTagName = "stab_gyroflow_export_toggle",
                        onCheckedChange = { viewModel.onToggleGyroflowExport() }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    label: String,
    supported: Boolean,
    yesText: String,
    noText: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = CinemaTextDim,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = if (supported) yesText else noText,
            color = if (supported) CinemaGreen else CinemaAmber,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun SelectableOptionTile(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accent: Color,
    testTagName: String,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) accent.copy(alpha = 0.14f) else CinemaSurfaceElevated
    val borderColor = if (isSelected) accent else CinemaBorder

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (isSelected) 1.2.dp else 0.7.dp, borderColor, RoundedCornerShape(10.dp))
            .testTag(testTagName)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) accent else CinemaGrey,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) accent else CinemaTextLight,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = CinemaTextDim,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun StabToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTagName: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) AppleYellow else CinemaGrey,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = CinemaTextLight,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = CinemaTextDim,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = commixSwitchColors(),
            modifier = Modifier.testTag(testTagName)
        )
    }
}
