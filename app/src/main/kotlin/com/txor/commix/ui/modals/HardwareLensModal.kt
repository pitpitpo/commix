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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.txor.commix.ui.theme.AppleYellow
import com.txor.commix.ui.theme.CinemaAmber
import com.txor.commix.ui.theme.CinemaCardDark
import com.txor.commix.ui.theme.CinemaCyan
import com.txor.commix.ui.theme.CinemaGlass
import com.txor.commix.ui.theme.CinemaGlassBorder
import com.txor.commix.ui.theme.CinemaTextLight
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
    var manualIdText by remember { mutableStateOf(activeCameraId) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CinemaCardDark,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .navigationBarsPadding()
        ) {
            // Header Row: Icon + Title + Active ID Pill + Done button
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
                        imageVector = Icons.Default.Hexagon,
                        contentDescription = "Lens Engine",
                        tint = AppleYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UNIVERSAL LENS ENGINE",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x33FFD60A))
                            .border(0.6.dp, AppleYellow.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ID $activeCameraId",
                            color = AppleYellow,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Text(
                    text = "Done",
                    color = AppleYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Deep Multi-Range Sensor Scan Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x26FFD60A))
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

            // Filter Chips Row + Sensor Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) AppleYellow else CinemaGlass)
                                .border(
                                    0.6.dp,
                                    if (active) AppleYellow else CinemaGlassBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedFilter = key }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (active) Color.Black else CinemaTextLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List of Discovered Camera Sensors
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 300.dp)
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0x28FFD60A) else Color(0x14FFFFFF))
                            .border(
                                width = if (isSelected) 1.4.dp else 0.7.dp,
                                color = if (isSelected) AppleYellow else CinemaGlassBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectCamera(cam.cameraId) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                                        .background(if (isSelected) AppleYellow else Color(0x26FFFFFF))
                                        .padding(horizontal = 7.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = badge,
                                        color = if (isSelected) Color.Black else AppleYellow,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$lensName  [ID ${cam.cameraId}]",
                                        color = if (isSelected) AppleYellow else Color.White,
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
                                        color = CinemaTextLight,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AppleYellow)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = Color.Black,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x2EFFFFFF))
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "USE",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
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
                                if (cam.isMonochrome) add("MONO" to Color.White)
                                if (cam.parentLogicalId != null) add("VIA LOGICAL ${cam.parentLogicalId}" to CinemaAmber)
                                if (cam.discoverySource.equals("AUX_SCAN", ignoreCase = true)) add("OEM AUX" to CinemaAmber)
                                add(hwLevelLabel to CinemaTextLight)
                            }
                            if (caps.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    caps.forEach { (tag, tint) ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(tint.copy(alpha = 0.14f))
                                                .border(0.5.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                color = tint,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x14FFFFFF))
                    .border(0.6.dp, CinemaGlassBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Force ID:",
                        color = CinemaTextLight,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(58.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x22FFFFFF))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        BasicTextField(
                            value = manualIdText,
                            onValueChange = { manualIdText = it.trim().take(4) },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(AppleYellow)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(AppleYellow)
                            .clickable {
                                if (manualIdText.isNotEmpty()) {
                                    onSelectCamera(manualIdText)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Direct Switch",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
