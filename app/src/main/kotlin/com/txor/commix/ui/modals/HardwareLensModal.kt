package com.txor.commix.ui.modals

import android.hardware.camera2.CameraCharacteristics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.CameraProbeResult
import com.txor.commix.LensProbeService
import com.txor.commix.ui.components.CommixBadgePill
import com.txor.commix.ui.components.CommixModalBottomSheet
import com.txor.commix.ui.components.CommixSheetHeader
import com.txor.commix.ui.theme.*
import java.util.Locale

/**
 * Universal Multi-Lens Hardware Discovery & Direct / Physical-via-Logical Switcher Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareLensModal(
    activeCameraId: String,
    cameras: List<CameraProbeResult>,
    onSelectCamera: (String) -> Unit,
    onProbeAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var manualIdText by remember(activeCameraId) { mutableStateOf(activeCameraId) }
    var isProbing by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredCameras = remember(cameras, selectedFilter) {
        when (selectedFilter) {
            "REAR" -> cameras.filter { !it.isFront }
            "FRONT" -> cameras.filter { it.isFront }
            "PHYSICAL" -> cameras.filter {
                it.parentLogicalId != null ||
                    it.discoverySource.equals("PHYSICAL", ignoreCase = true) ||
                    it.discoverySource.equals("AUX_SCAN", ignoreCase = true)
            }
            else -> cameras
        }
    }

    CommixModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .navigationBarsPadding()
        ) {
            CommixSheetHeader(
                title = "Universal Lens Engine",
                subtitle = "Logical, physical & OEM auxiliary sensors",
                icon = Icons.Default.Hexagon,
                iconTint = AppleYellow,
                badgeText = "ID $activeCameraId",
                onDismiss = onDismiss
            )

            // Deep Multi-Range Sensor Scan Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleYellow.copy(alpha = 0.16f))
                    .border(0.8.dp, AppleYellow.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .testTag("probe_all_lenses_button")
                    .clickable {
                        isProbing = true
                        onProbeAll()
                        isProbing = false
                    }
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(
                            color = AppleYellow,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Deep Sensor Scan",
                            tint = AppleYellow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProbing) "Scanning 0..255 + Physical & Aux..." else "Deep Scan Sensors (0..255 + Physical & Aux)",
                        color = AppleYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                val filters = listOf(
                    "ALL" to "ALL (${cameras.size})",
                    "REAR" to "REAR (${cameras.count { !it.isFront }})",
                    "FRONT" to "FRONT (${cameras.count { it.isFront }})",
                    "PHYSICAL" to "AUX/PHYS (${cameras.count { it.parentLogicalId != null || it.discoverySource.equals("PHYSICAL", ignoreCase = true) || it.discoverySource.equals("AUX_SCAN", ignoreCase = true) }})"
                )
                filters.forEach { (key, label) ->
                    val active = selectedFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) AppleYellow else CinemaSurface)
                            .border(
                                0.8.dp,
                                if (active) AppleYellow else CinemaGlassBorder,
                                RoundedCornerShape(9.dp)
                            )
                            .testTag("lens_filter_$key")
                            .clickable { selectedFilter = key }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (active) CinemaBlack else CinemaTextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // List of Discovered Camera Sensors
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 320.dp)
            ) {
                items(filteredCameras, key = { it.cameraId }) { cam ->
                    val isSelected = cam.cameraId == activeCameraId
                    val facingStr = if (cam.isFront) "Front" else "Rear"
                    val eqFl = cam.focalLengths.firstOrNull() ?: 26f
                    val rawFlStr = if (cam.rawFocalLengthMm > 0f) {
                        String.format(Locale.US, "%.2fmm (%.0fmm eq)", cam.rawFocalLengthMm, eqFl)
                    } else {
                        "${eqFl.toInt()}mm eq"
                    }
                    val apertureStr = cam.apertures.firstOrNull()?.let {
                        String.format(Locale.US, "f/%.1f", it)
                    } ?: ""
                    val badge = LensProbeService.formatLensBadge(cam)
                    val lensName = when {
                        cam.isFront && cam.isUltraWide -> "Front Ultra-Wide"
                        cam.isFront -> "Front Camera"
                        cam.isMonochrome -> "Monochrome Sensor"
                        cam.isPeriscope -> "Periscope Telephoto"
                        cam.isMacro -> "Macro Lens"
                        cam.isUltraWide -> "Ultra-Wide Lens"
                        cam.isTelephoto -> "Telephoto Lens"
                        else -> "Main Wide Sensor"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) AppleYellow.copy(alpha = 0.16f) else CinemaSurface)
                            .border(
                                width = if (isSelected) 1.4.dp else 0.8.dp,
                                color = if (isSelected) AppleYellow else CinemaGlassBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("lens_card_${cam.cameraId}")
                            .clickable { onSelectCamera(cam.cameraId) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Optical Zoom / Role Badge
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AppleYellow else CinemaSurfaceElevated)
                                        .padding(horizontal = 7.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = badge,
                                        color = if (isSelected) CinemaBlack else AppleYellow,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$lensName  [ID ${cam.cameraId}]",
                                        color = if (isSelected) AppleYellow else CinemaTextLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val metaParts = buildList {
                                        add(facingStr)
                                        add(rawFlStr)
                                        if (apertureStr.isNotEmpty()) add(apertureStr)
                                        add(String.format(Locale.US, "%.1fMP", cam.sensorMp))
                                        if (cam.maxFps > 30.0) add("${cam.maxFps.toInt()}fps")
                                    }.joinToString(" · ")
                                    Text(
                                        text = metaParts,
                                        color = CinemaTextDim,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                CommixBadgePill(
                                    text = if (isSelected) "ACTIVE" else "USE",
                                    accentColor = if (isSelected) AppleYellow else CinemaTextLight,
                                    filled = isSelected
                                )
                            }

                            // Hardware Capability Tags Row
                            val hwLevelLabel = when (cam.hardwareLevel) {
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL 3"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXT"
                                else -> "LEGACY"
                            }
                            val caps = buildList<Pair<String, Color>> {
                                if (cam.hasRaw) add("RAW14" to AppleYellow)
                                if (cam.hasOis) add("OIS" to CinemaCyan)
                                if (cam.supports10Bit) add("10-BIT" to CinemaCyan)
                                if (cam.isUltraHighRes) add("HI-RES" to CinemaAmber)
                                if (cam.isPeriscope) add("PERISCOPE" to AppleYellow)
                                if (cam.isMonochrome) add("MONO" to CinemaTextLight)
                                if (cam.parentLogicalId != null) add("VIA LOGICAL ${cam.parentLogicalId}" to CinemaAmber)
                                if (cam.discoverySource.equals("AUX_SCAN", ignoreCase = true)) add("OEM AUX" to CinemaAmber)
                                add(hwLevelLabel to CinemaTextDim)
                            }
                            if (caps.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    caps.forEach { (tag, tint) ->
                                        CommixBadgePill(
                                            text = tag,
                                            accentColor = tint,
                                            monospace = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Force Camera ID input + Direct Switch button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CinemaSurface)
                    .border(0.8.dp, CinemaGlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Force ID:",
                        color = CinemaTextLight,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CinemaSurfaceElevated)
                            .border(0.6.dp, CinemaGlassBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        BasicTextField(
                            value = manualIdText,
                            onValueChange = { manualIdText = it.trim().take(4) },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = CinemaTextLight,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(AppleYellow)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(AppleYellow)
                            .testTag("direct_switch_lens_button")
                            .clickable {
                                if (manualIdText.isNotEmpty()) {
                                    onSelectCamera(manualIdText)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Direct Switch",
                            color = CinemaBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
