package com.txor.commix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.ui.theme.*
import java.util.Locale

enum class ProControlTab {
    ZOOM, ISO, SHUTTER, ANGLE, EV, WB, FOCUS, CURVE, GRAIN, LUT, PROCESS_ZERO, AUDIO, ND, ANAMORPHIC, AEB, COMP, SCOPES, PRESETS
}

@Composable
fun ProSettingsCards(
    activeTab: ProControlTab,
    onTabSelected: (ProControlTab) -> Unit,
    currentIso: Int,
    onIsoSelected: (Int) -> Unit,
    currentShutterNs: Long,
    onShutterSelected: (Long) -> Unit,
    currentShutterAngle: Double = 180.0,
    shutterAngleLocked: Boolean = false,
    onShutterAngleSelected: (Double) -> Unit = {},
    onToggleShutter180Lock: () -> Unit = {},
    videoFps: Int = 30,
    currentEv: Float,
    onEvSelected: (Float) -> Unit,
    currentWb: Int,
    onWbSelected: (Int) -> Unit,
    wbTint: Float = 0f,
    onWbTintChanged: (Float) -> Unit = {},
    currentFocus: Float,
    onFocusSelected: (Float) -> Unit,
    currentZoom: Double = 1.0,
    maxZoom: Double = 10.0,
    onZoomSelected: (Double) -> Unit = {},
    spaceZoomLock: Boolean = false,
    onToggleSpaceZoomLock: () -> Unit = {},
    currentCurve: String,
    onCurveSelected: (String) -> Unit,
    currentGrain: String,
    onGrainSelected: (String) -> Unit,
    grainIntensity: Float = 0.5f,
    onGrainIntensityChanged: (Float) -> Unit = {},
    currentLut: String = "none",
    onLutSelected: (String) -> Unit = {},
    lutBlend: Float = 1.0f,
    onLutBlendChanged: (Float) -> Unit = {},
    currentNd: String = "off",
    onNdSelected: (String) -> Unit = {},
    currentAnamorphic: Float = 1.0f,
    onAnamorphicSelected: (Float) -> Unit = {},
    aebEnabled: Boolean = false,
    aebShots: Int = 3,
    aebSpreadEv: Float = 1.0f,
    aebHdrFusion: Boolean = true,
    onAebSelected: (Boolean, Int, Float) -> Unit = { _, _, _ -> },
    onToggleAebHdrFusion: () -> Unit = {},
    currentAudioGain: Float = 0.0f,
    onAudioGainChanged: (Float) -> Unit = {},
    audioLowCut: Boolean = false,
    onToggleAudioLowCut: () -> Unit = {},
    isProcessZero: Boolean = false,
    onToggleProcessZero: () -> Unit = {},
    processZeroIntensity: Float = 1.0f,
    onProcessZeroIntensityChanged: (Float) -> Unit = {},
    activeCamera: com.txor.commix.CameraProbeResult? = null,
    showHistogram: Boolean = false,
    onToggleHistogram: () -> Unit = {},
    showWaveform: Boolean = false,
    onToggleWaveform: () -> Unit = {},
    showRgbParade: Boolean = false,
    onToggleRgbParade: () -> Unit = {},
    showVectorscope: Boolean = false,
    onToggleVectorscope: () -> Unit = {},
    showHorizon: Boolean = false,
    onToggleHorizon: () -> Unit = {},
    showGrid: Boolean = false,
    onToggleGrid: () -> Unit = {},
    showZebras: Boolean = false,
    zebraThresholdIre: Int = 95,
    onToggleZebras: () -> Unit = {},
    onCycleZebraThreshold: () -> Unit = {},
    showFalseColor: Boolean = false,
    onToggleFalseColor: () -> Unit = {},
    showSafeAreaGuides: Boolean = false,
    onToggleSafeAreaGuides: () -> Unit = {},
    focusPointA: Float? = null,
    focusPointB: Float? = null,
    focusPullDurationSec: Float = 2.0f,
    isFocusPullRunning: Boolean = false,
    onSetFocusPointA: () -> Unit = {},
    onSetFocusPointB: () -> Unit = {},
    onClearFocusPullPoints: () -> Unit = {},
    onSetFocusPullDuration: (Float) -> Unit = {},
    onTriggerFocusPull: (Boolean) -> Unit = {},
    savedCustomSlots: Set<Int> = emptySet(),
    activeCustomSlot: Int = 0,
    onSaveCustomPreset: (Int) -> Unit = {},
    onLoadCustomPreset: (Int) -> Unit = {},
    aeAfLocked: Boolean = false,
    onToggleAeAfLock: () -> Unit = {},
    lensCorrectionEnabled: Boolean = false,
    onToggleLensCorrection: () -> Unit = {},
    focusStackEnabled: Boolean = false,
    onToggleFocusStack: () -> Unit = {},
    multiExposureMode: String = "off",
    onMultiExposureModeSelected: (String) -> Unit = {},
    longExposureStyle: String = "silky_water",
    onLongExposureStyleSelected: (String) -> Unit = {},
    onOpenSlate: () -> Unit = {},
    onOpenKeyframePresets: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(CinemaCardDark.copy(alpha = 0.95f))
            .padding(vertical = 10.dp)
    ) {
        // Horizontal tab selectors
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            items(ProControlTab.values()) { tab ->
                val isSelected = (tab == activeTab)
                val tabLabel = when (tab) {
                    ProControlTab.PROCESS_ZERO -> "P0"
                    ProControlTab.SHUTTER -> "S"
                    ProControlTab.FOCUS -> "F"
                    ProControlTab.PRESETS -> "C1-3"
                    else -> tab.name
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AppleYellow else Color(0x22FFFFFF))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tabLabel,
                        color = if (isSelected) Color.Black else CinemaTextLight,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Active control values row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (activeTab) {
                ProControlTab.ZOOM -> {
                    val safeMaxZoom = maxZoom.toFloat().coerceAtLeast(1.0f)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val zoomSteps = listOf(
                            1.0 to "24mm (1.0×)",
                            1.5 to "35mm (1.5×)",
                            2.0 to "48mm (2.0×)",
                            3.0 to "72mm (3.0×)",
                            5.0 to "120mm (5.0×)",
                            10.0 to "240mm (10×)"
                        ).filter { it.first <= maxZoom || it.first == 1.0 }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(zoomSteps) { (z, label) ->
                                val isSel = kotlin.math.abs(currentZoom - z) < 0.15
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) AppleYellow else CinemaSurfaceElevated)
                                        .clickable { onZoomSelected(z) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) CinemaBlack else CinemaTextLight,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (currentZoom >= 10.0) {
                                item {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (spaceZoomLock) AppleYellow else AppleYellow.copy(alpha = 0.20f))
                                            .border(0.8.dp, AppleYellow, RoundedCornerShape(8.dp))
                                            .clickable { onToggleSpaceZoomLock() }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = if (spaceZoomLock) CinemaBlack else AppleYellow,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "ZOOM LOCK",
                                                color = if (spaceZoomLock) CinemaBlack else AppleYellow,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Continuous Zoom Slider (clamped safely to hardware range)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f×", currentZoom),
                                color = AppleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.width(42.dp)
                            )
                            Slider(
                                value = currentZoom.toFloat().coerceIn(1.0f, safeMaxZoom),
                                onValueChange = { onZoomSelected(it.toDouble()) },
                                valueRange = 1.0f..safeMaxZoom,
                                colors = commixSliderColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                ProControlTab.ISO -> {
                    val isoRange = activeCamera?.isoRange
                    val minIso = (isoRange?.lower ?: 50).toFloat()
                    val hwMaxIso = (isoRange?.upper ?: 3200)
                    val maxExtendedIso = maxOf(hwMaxIso, 25600).toFloat()
                    val isVirtualExtended = currentIso > hwMaxIso
                    val baseIsos = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600)
                    val validIsos = listOf(0) + baseIsos.filter { it >= minIso.toInt() && it <= maxExtendedIso.toInt() }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (currentIso == 0) "ISO: AUTO" else "ISO: $currentIso",
                                    color = if (isVirtualExtended) CinemaAmber else AppleYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isVirtualExtended) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(CinemaAmber.copy(alpha = 0.22f))
                                            .border(0.6.dp, CinemaAmber, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "AETHER EXT",
                                            color = CinemaAmber,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 8.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "HW ${minIso.toInt()}–$hwMaxIso · EXT ${maxExtendedIso.toInt()}",
                                color = CinemaTextLight,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        PillRow(items = validIsos, selected = currentIso, formatter = { if (it == 0) "AUTO" else "$it" }, onSelected = onIsoSelected)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SLIDER",
                                color = CinemaTextLight,
                                fontSize = 10.sp,
                                modifier = Modifier.width(42.dp)
                            )
                            var sliderIso by remember(currentIso) { mutableFloatStateOf(if (currentIso == 0) minIso else currentIso.toFloat()) }
                            Slider(
                                value = sliderIso.coerceIn(minIso, maxExtendedIso),
                                onValueChange = { sliderIso = it },
                                onValueChangeFinished = { onIsoSelected(sliderIso.toInt()) },
                                valueRange = minIso..maxExtendedIso,
                                colors = commixSliderColors(if (isVirtualExtended) CinemaAmber else AppleYellow),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                ProControlTab.SHUTTER -> {
                    val expRange = activeCamera?.expRange
                    val minExpNs = expRange?.lower ?: 125_000L // 1/8000s
                    val hwMaxExpNs = expRange?.upper ?: 1_000_000_000L // 1s
                    val maxExtendedExpNs = maxOf(hwMaxExpNs, 16_000_000_000L) // Up to 16s via Aether virtual stacking
                    val isVirtualShutter = currentShutterNs > hwMaxExpNs
                    val baseShutters = listOf(
                        125_000L, 250_000L, 500_000L, 1_000_000L, 2_000_000L,
                        4_000_000L, 8_000_000L, 16_666_666L, 33_333_333L, 50_000_000L,
                        100_000_000L, 250_000_000L, 500_000_000L, 1_000_000_000L,
                        2_000_000_000L, 4_000_000_000L, 8_000_000_000L, 16_000_000_000L
                    )
                    val validShutters = listOf(0L) + baseShutters.filter { it >= minExpNs && it <= maxExtendedExpNs }

                    fun formatShutterLabel(ns: Long): String {
                        if (ns <= 0L) return "AUTO"
                        if (ns >= 1_000_000_000L) {
                            val sec = ns / 1_000_000_000.0
                            return if (sec % 1.0 < 0.05) "${sec.toInt()}s" else String.format(Locale.US, "%.1fs", sec)
                        }
                        val denom = (1_000_000_000.0 / ns).toInt().coerceAtLeast(1)
                        return "1/${denom}s"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (shutterAngleLocked) AppleYellow else Color(0x22FFFFFF))
                                        .clickable { onToggleShutter180Lock() }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (shutterAngleLocked) Color.Black else Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "180° LOCK",
                                            color = if (shutterAngleLocked) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                if (isVirtualShutter) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(CinemaAmber.copy(alpha = 0.22f))
                                            .border(0.6.dp, CinemaAmber, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "AETHER STACK",
                                            color = CinemaAmber,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 8.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            val liveAngle = if (currentShutterNs > 0 && videoFps > 0) {
                                (360.0 * (currentShutterNs / 1_000_000_000.0) * videoFps).coerceIn(0.0, 360.0)
                            } else 180.0
                            Text(
                                text = if (currentShutterNs == 0L) "SHUTTER: AUTO" else "S: ${formatShutterLabel(currentShutterNs)} (${String.format(Locale.US, "%.0f°", liveAngle)})",
                                color = if (isVirtualShutter) CinemaAmber else AppleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        PillRow(items = validShutters, selected = currentShutterNs, formatter = ::formatShutterLabel, onSelected = onShutterSelected)

                        // Logarithmic continuous shutter slider (extends to 16s via Aether virtual burst synthesis)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SLIDER",
                                color = CinemaTextLight,
                                fontSize = 10.sp,
                                modifier = Modifier.width(42.dp)
                            )
                            val minLog = kotlin.math.ln(minExpNs.toDouble())
                            val maxLog = kotlin.math.ln(maxExtendedExpNs.toDouble())
                            val curLog = if (currentShutterNs > 0L) kotlin.math.ln(currentShutterNs.toDouble().coerceIn(minExpNs.toDouble(), maxExtendedExpNs.toDouble())) else kotlin.math.ln(16666666.0)
                            var sliderShutterLog by remember(currentShutterNs) { mutableFloatStateOf(curLog.toFloat()) }
                            Slider(
                                value = sliderShutterLog.coerceIn(minLog.toFloat(), maxLog.toFloat()),
                                onValueChange = { sliderShutterLog = it },
                                onValueChangeFinished = {
                                    val ns = kotlin.math.exp(sliderShutterLog.toDouble()).toLong()
                                    onShutterSelected(ns)
                                },
                                valueRange = minLog.toFloat()..maxLog.toFloat(),
                                colors = commixSliderColors(if (isVirtualShutter) CinemaAmber else AppleYellow),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                ProControlTab.ANGLE -> {
                    val angles = listOf(45.0, 90.0, 144.0, 172.8, 180.0, 270.0, 360.0)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(angles) { a ->
                                val isSel = kotlin.math.abs(currentShutterAngle - a) < 0.5
                                val den = (360.0 * kotlin.math.max(1, videoFps) / a).toInt()
                                val label = when (a) {
                                    144.0 -> "144° (60Hz)"
                                    172.8 -> "172.8° (50Hz)"
                                    else -> "${a.toInt()}° (1/$den)"
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) AppleYellow else Color(0x1AFFFFFF))
                                        .clickable { onShutterAngleSelected(a) }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.Black else CinemaTextLight,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val curDen = (360.0 * kotlin.math.max(1, videoFps) / currentShutterAngle).toInt()
                            Text(
                                text = String.format(Locale.US, "%.1f° (1/%d)", currentShutterAngle, curDen),
                                color = AppleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.width(90.dp)
                            )
                            Slider(
                                value = currentShutterAngle.toFloat().coerceIn(10f, 360f),
                                onValueChange = { onShutterAngleSelected(it.toDouble()) },
                                valueRange = 10f..360f,
                                colors = commixSliderColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                ProControlTab.EV -> {
                    val evRange = activeCamera?.evRange
                    val evStep = activeCamera?.evStep ?: 0.1f
                    val minEv = (evRange?.lower ?: -30) * evStep
                    val maxEv = (evRange?.upper ?: 30) * evStep
                    val validEvs = listOf(-3.0f, -2.0f, -1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f).filter { it in minEv..maxEv }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = String.format(Locale.US, "EXPOSURE: %+.1f EV", currentEv),
                                color = AppleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f to %+.1f", minEv, maxEv),
                                color = CinemaTextLight,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        PillRow(items = validEvs, selected = currentEv, formatter = { String.format(Locale.US, "%+.1f", it) }, onSelected = onEvSelected)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SLIDER",
                                color = CinemaTextLight,
                                fontSize = 10.sp,
                                modifier = Modifier.width(42.dp)
                            )
                            var sliderEv by remember(currentEv) { mutableFloatStateOf(currentEv) }
                            Slider(
                                value = sliderEv.coerceIn(minEv, maxEv),
                                onValueChange = { sliderEv = it },
                                onValueChangeFinished = { onEvSelected(sliderEv) },
                                valueRange = minEv..maxEv,
                                colors = commixSliderColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                ProControlTab.WB -> {
                    val wbs = listOf(0, 2800, 3200, 4000, 5000, 5600, 6500, 7500)
                    val wbLabels = mapOf(
                        0 to "AUTO",
                        2800 to "2800K",
                        3200 to "3200K Tungsten",
                        4000 to "4000K Fluor",
                        5000 to "5000K Day",
                        5600 to "5600K Sun",
                        6500 to "6500K Cloud",
                        7500 to "7500K Shade"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (currentWb == 0) "WHITE BALANCE: AUTO" else "WHITE BALANCE: ${currentWb}K",
                                color = AppleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "2000K – 10000K",
                                color = CinemaTextLight,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        PillRow(items = wbs, selected = currentWb, formatter = { wbLabels[it] ?: "${it}K" }, onSelected = onWbSelected)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SLIDER",
                                color = CinemaTextLight,
                                fontSize = 10.sp,
                                modifier = Modifier.width(42.dp)
                            )
                            var sliderWb by remember(currentWb) { mutableFloatStateOf(if (currentWb == 0) 5600f else currentWb.toFloat()) }
                            Slider(
                                value = sliderWb.coerceIn(2000f, 10000f),
                                onValueChange = { sliderWb = it },
                                onValueChangeFinished = { onWbSelected(sliderWb.toInt()) },
                                valueRange = 2000f..10000f,
                                colors = commixSliderColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (currentWb > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = String.format(Locale.US, "TINT %+.0f", wbTint),
                                    color = if (kotlin.math.abs(wbTint) > 0.5f) AppleYellow else CinemaTextLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(62.dp)
                                )
                                Slider(
                                    value = wbTint.coerceIn(-50f, 50f),
                                    onValueChange = { onWbTintChanged(it) },
                                    valueRange = -50f..50f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFF64A6),
                                        activeTrackColor = Color(0xFF4CD964),
                                        inactiveTrackColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                ProControlTab.FOCUS -> {
                    val maxD = (activeCamera?.focusMaxDiopters ?: 10.0f).coerceAtLeast(1.0f)
                    val validFocuses = listOf(-1.0f, 0.0f, (maxD * 0.1f), (maxD * 0.2f), (maxD * 0.5f), (maxD * 0.8f), maxD)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val focusDesc = if (currentFocus < 0f) "AUTO AF"
                            else if (currentFocus <= 0.05f) "∞ INFINITY"
                            else {
                                val m = 1.0f / currentFocus
                                if (m < 1.0f) String.format(Locale.US, "%.0f cm (%.1f D)", m * 100f, currentFocus)
                                else String.format(Locale.US, "%.2f m (%.1f D)", m, currentFocus)
                            }
                            Text(
                                text = "FOCUS: $focusDesc",
                                color = AppleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "0.0 – ${String.format(Locale.US, "%.1f D", maxD)}",
                                color = CinemaTextLight,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        PillRow(
                            items = validFocuses,
                            selected = currentFocus,
                            formatter = {
                                if (it < 0f) "AF"
                                else if (it <= 0.05f) "∞ INF"
                                else {
                                    val m = 1.0f / it
                                    if (m < 1.0f) String.format(Locale.US, "%.0fcm", m * 100f)
                                    else String.format(Locale.US, "%.1fm", m)
                                }
                            },
                            onSelected = onFocusSelected
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "MANUAL",
                                color = CinemaTextLight,
                                fontSize = 10.sp,
                                modifier = Modifier.width(42.dp)
                            )
                            var sliderFocus by remember(currentFocus) { mutableFloatStateOf(if (currentFocus < 0f) 0f else currentFocus) }
                            Slider(
                                value = sliderFocus.coerceIn(0f, maxD),
                                onValueChange = { sliderFocus = it },
                                onValueChangeFinished = { onFocusSelected(sliderFocus) },
                                valueRange = 0f..maxD,
                                colors = commixSliderColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Cinema Motorized Focus Pull A <-> B Bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            ScopeTogglePill(
                                name = if (focusPointA != null) String.format(Locale.US, "A: %.1fD", focusPointA) else "SET A",
                                active = focusPointA != null,
                                onToggle = onSetFocusPointA
                            )
                            ScopeTogglePill(
                                name = if (focusPointB != null) String.format(Locale.US, "B: %.1fD", focusPointB) else "SET B",
                                active = focusPointB != null,
                                onToggle = onSetFocusPointB
                            )
                            val readyPull = focusPointA != null && focusPointB != null
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isFocusPullRunning) CinemaCyan else if (readyPull) AppleYellow else Color(0x1AFFFFFF))
                                    .clickable { onTriggerFocusPull(true) }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isFocusPullRunning) "PULLING…" else "PULL A→B",
                                    color = if (readyPull || isFocusPullRunning) Color.Black else CinemaTextDim,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            if (readyPull) {
                                ScopeTogglePill(
                                    name = "PULL B→A",
                                    active = false,
                                    onToggle = { onTriggerFocusPull(false) }
                                )
                            }
                            val durations = listOf(1.0f, 2.0f, 3.5f, 5.0f)
                            val nextDur = durations[(durations.indexOfFirst { kotlin.math.abs(it - focusPullDurationSec) < 0.2f } + 1).coerceAtLeast(0) % durations.size]
                            ScopeTogglePill(
                                name = String.format(Locale.US, "%.1fs", focusPullDurationSec),
                                active = true,
                                onToggle = { onSetFocusPullDuration(nextDur) }
                            )
                            if (focusPointA != null || focusPointB != null) {
                                ScopeTogglePill(
                                    name = "CLEAR",
                                    active = false,
                                    onToggle = onClearFocusPullPoints
                                )
                            }
                        }
                    }
                }

                ProControlTab.CURVE -> {
                    val curves = listOf("neutral", "film_print", "cine_d", "linear", "arri_logc", "rec709", "slog3")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "TONE CURVE: ${currentCurve.replace("_", " ").uppercase()}",
                                color = AppleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        PillRow(items = curves, selected = currentCurve, formatter = { it.replace("_", " ").uppercase() }, onSelected = onCurveSelected)
                    }
                }

                ProControlTab.GRAIN -> {
                    val grains = listOf("off", "35mm", "16mm", "8mm")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "FILM GRAIN: ${currentGrain.uppercase()}",
                                color = AppleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            if (currentGrain != "off") {
                                Text(
                                    text = "${(grainIntensity * 100).toInt()}%",
                                    color = CinemaTextLight,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        PillRow(items = grains, selected = currentGrain, formatter = { it.uppercase() }, onSelected = onGrainSelected)
                        if (currentGrain != "off") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "AMOUNT",
                                    color = CinemaTextLight,
                                    fontSize = 10.sp,
                                    modifier = Modifier.width(52.dp)
                                )
                                Slider(
                                    value = grainIntensity.coerceIn(0f, 1f),
                                    onValueChange = onGrainIntensityChanged,
                                    valueRange = 0f..1f,
                                    colors = commixSliderColors(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                ProControlTab.LUT -> {
                    val luts = listOf("none", "rec709", "slog3", "apple_log", "kodak_2383", "fuji_eterna", "bleach_bypass", "monochrome", "leica", "hasselblad", "zeiss", "proraw")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillRow(items = luts, selected = currentLut, formatter = { it.replace("_", " ").uppercase() }, onSelected = onLutSelected)
                        if (currentLut != "none") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "MIX ${(lutBlend * 100).toInt()}%",
                                    color = AppleYellow,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(60.dp)
                                )
                                Slider(
                                    value = lutBlend.coerceIn(0f, 1f),
                                    onValueChange = onLutBlendChanged,
                                    valueRange = 0f..1f,
                                    colors = commixSliderColors(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                ProControlTab.PROCESS_ZERO -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PROCESS ZERO (ANTI-COMPUTATIONAL)",
                                    color = AppleYellow,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Zero smoothing • Pure demosaic • Emulsion grain",
                                    color = CinemaTextLight,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isProcessZero) AppleYellow else Color(0x22FFFFFF))
                                    .clickable { onToggleProcessZero() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isProcessZero) "ACTIVE" else "ENABLE",
                                    color = if (isProcessZero) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (isProcessZero) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = String.format(Locale.US, "INTENSITY: %.0f%%", processZeroIntensity * 100f),
                                    color = AppleYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(105.dp)
                                )
                                Slider(
                                    value = processZeroIntensity.coerceIn(0.1f, 1.0f),
                                    onValueChange = onProcessZeroIntensityChanged,
                                    valueRange = 0.1f..1.0f,
                                    colors = commixSliderColors(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                ProControlTab.AUDIO -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppleYellow)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Studio Mic 48kHz",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (audioLowCut) AppleYellow else Color(0x22FFFFFF))
                                    .clickable { onToggleAudioLowCut() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Low Cut 80Hz",
                                    color = if (audioLowCut) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable { onAudioGainChanged(0.0f) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "0 dB Reset",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = String.format(Locale.US, "GAIN: %+.0fdB", currentAudioGain),
                                color = AppleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.width(85.dp)
                            )
                            Slider(
                                value = currentAudioGain.coerceIn(-12f, 18f),
                                onValueChange = onAudioGainChanged,
                                valueRange = -12f..18f,
                                colors = commixSliderColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                ProControlTab.ND -> {
                    val nds = listOf("off", "nd2", "nd4", "nd8", "nd16", "nd64", "nd1000")
                    val ndLabels = mapOf(
                        "off" to "Clear",
                        "nd2" to "ND2 (-1 EV)",
                        "nd4" to "ND4 (-2 EV)",
                        "nd8" to "ND8 (-3 EV)",
                        "nd16" to "ND16 (-4 EV)",
                        "nd64" to "ND64 (-6 EV)",
                        "nd1000" to "ND1000 (-10 EV)"
                    )
                    PillRow(items = nds, selected = currentNd, formatter = { ndLabels[it] ?: it.uppercase() }, onSelected = onNdSelected)
                }

                ProControlTab.ANAMORPHIC -> {
                    val anamorphicFactors = listOf(1.0f, 1.25f, 1.33f, 1.5f, 1.8f, 2.0f)
                    PillRow(
                        items = anamorphicFactors,
                        selected = currentAnamorphic,
                        formatter = { if (it == 1.0f) "1.0× (OFF)" else "${it}×" },
                        onSelected = onAnamorphicSelected
                    )
                }

                ProControlTab.AEB -> {
                    val aebOpts = listOf(
                        Triple(false, 3, 1.0f) to "OFF",
                        Triple(true, 3, 1.0f) to "3-Shot (±1 EV)",
                        Triple(true, 3, 2.0f) to "3-Shot (±2 EV)",
                        Triple(true, 5, 2.0f) to "5-Shot (±2 EV)"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(aebOpts) { (opt, label) ->
                                val isSel = (!opt.first && !aebEnabled) || (aebEnabled && aebShots == opt.second && kotlin.math.abs(aebSpreadEv - opt.third) < 0.1f)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) AppleYellow else Color(0x1AFFFFFF))
                                        .clickable { onAebSelected(opt.first, opt.second, opt.third) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.Black else CinemaTextLight,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (aebHdrFusion) "C++20 MERTENS HDR+ EXPOSURE FUSION" else "SAVE SEPARATE EXPOSURE BRACKETS",
                                color = if (aebHdrFusion) AppleYellow else CinemaTextLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (aebHdrFusion) AppleYellow else Color(0x22FFFFFF))
                                    .clickable { onToggleAebHdrFusion() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (aebHdrFusion) "HDR+ FUSE ON" else "BRACKETS ONLY",
                                    color = if (aebHdrFusion) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                ProControlTab.COMP -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                ScopeTogglePill(
                                    name = if (focusStackEnabled) "FOCUS STACK ON" else "FOCUS STACK",
                                    active = focusStackEnabled,
                                    onToggle = onToggleFocusStack
                                )
                            }
                            item {
                                ScopeTogglePill(
                                    name = if (lensCorrectionEnabled) "LENS CORR ON" else "LENS CORR",
                                    active = lensCorrectionEnabled,
                                    onToggle = onToggleLensCorrection
                                )
                            }
                            items(listOf("off" to "ME: OFF", "additive" to "ME: ADD", "average" to "ME: AVG", "lighten" to "ME: LIGHTEN", "darken" to "ME: DARKEN")) { (modeKey, label) ->
                                val isSel = multiExposureMode == modeKey
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) AppleYellow else Color(0x1AFFFFFF))
                                        .clickable { onMultiExposureModeSelected(modeKey) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.Black else CinemaTextLight,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        val leStyles = listOf(
                            "silky_water" to "LE: SILKY",
                            "light_trails" to "LE: TRAILS",
                            "star_trails" to "LE: STARS",
                            "moving_crowd" to "LE: CROWD"
                        )
                        PillRow(
                            items = leStyles.map { it.first },
                            selected = longExposureStyle,
                            formatter = { key -> leStyles.firstOrNull { it.first == key }?.second ?: key.uppercase() },
                            onSelected = onLongExposureStyleSelected
                        )
                    }
                }

                ProControlTab.SCOPES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item { ScopeTogglePill(name = if (showZebras) "ZEBRAS ${zebraThresholdIre}%" else "ZEBRAS", active = showZebras, onToggle = onToggleZebras) }
                            if (showZebras) {
                                item { ScopeTogglePill(name = "IRE ${zebraThresholdIre}% ↻", active = true, onToggle = onCycleZebraThreshold) }
                            }
                            item { ScopeTogglePill(name = "FALSE COLOR", active = showFalseColor, onToggle = onToggleFalseColor) }
                            item { ScopeTogglePill(name = "SAFE AREA", active = showSafeAreaGuides, onToggle = onToggleSafeAreaGuides) }
                            item { ScopeTogglePill(name = "HISTOGRAM", active = showHistogram, onToggle = onToggleHistogram) }
                            item { ScopeTogglePill(name = "WAVEFORM", active = showWaveform, onToggle = onToggleWaveform) }
                            item { ScopeTogglePill(name = "RGB PARADE", active = showRgbParade, onToggle = onToggleRgbParade) }
                            item { ScopeTogglePill(name = "V-SCOPE", active = showVectorscope, onToggle = onToggleVectorscope) }
                            item { ScopeTogglePill(name = "HORIZON", active = showHorizon, onToggle = onToggleHorizon) }
                            item { ScopeTogglePill(name = "GRID", active = showGrid, onToggle = onToggleGrid) }
                            item { ScopeTogglePill(name = if (aeAfLocked) "AE/AF LOCKED" else "AE/AF LOCK", active = aeAfLocked, onToggle = onToggleAeAfLock) }
                            item { ScopeTogglePill(name = "SLATE", active = false, onToggle = onOpenSlate) }
                            item { ScopeTogglePill(name = "AUTOMATION", active = false, onToggle = onOpenKeyframePresets) }
                        }
                    }
                }

                ProControlTab.PRESETS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "CUSTOM CAMERA RIG MEMORY (C1 – C3)",
                            color = AppleYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(1, 2, 3).forEach { slot ->
                                val isSaved = slot in savedCustomSlots
                                val isActive = activeCustomSlot == slot
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isActive) AppleYellow.copy(alpha = 0.16f) else CinemaSurfaceElevated)
                                        .border(
                                            width = if (isActive) 1.dp else 0.7.dp,
                                            color = if (isActive) AppleYellow else CinemaGlassBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = if (isSaved) "C$slot · READY" else "C$slot · EMPTY",
                                        color = if (isActive) AppleYellow else if (isSaved) CinemaGreen else CinemaTextDim,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(if (isSaved) AppleYellow else Color(0x1AFFFFFF))
                                                .clickable { onLoadCustomPreset(slot) }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "LOAD",
                                                color = if (isSaved) Color.Black else CinemaTextDim,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            )
                                        }
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(CinemaCyan.copy(alpha = 0.22f))
                                                .clickable { onSaveCustomPreset(slot) }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "SAVE",
                                                color = CinemaCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeTogglePill(name: String, active: Boolean, onToggle: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AppleYellow.copy(alpha = 0.25f) else Color(0x1AFFFFFF))
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = name,
            color = if (active) AppleYellow else CinemaTextLight,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun <T> PillRow(
    items: List<T>,
    selected: T,
    formatter: (T) -> String,
    onSelected: (T) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item ->
            val isSelected = (item == selected)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) AppleYellow else Color(0x1AFFFFFF))
                    .clickable { onSelected(item) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = formatter(item),
                    color = if (isSelected) Color.Black else CinemaTextLight,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
