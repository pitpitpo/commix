package com.txor.commix.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.automation.AutomationEngine
import com.txor.commix.automation.AutomationPresetConfig
import com.txor.commix.ui.components.*
import com.txor.commix.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationModal(
    activePresetId: String = "dolly_zoom",
    activeTriggerMode: String = "timer_3s",
    maxZoom: Double = 30.0,
    onSelectPreset: (String) -> Unit = {},
    onSelectTrigger: (String) -> Unit = {},
    onStartPreset: (presetId: String, triggerMode: String) -> Unit = { _, _ -> },
    onStartCustom: (durationSec: Double, startZoom: Double, endZoom: Double, startEv: Float, endEv: Float, startFocus: Float, endFocus: Float, triggerMode: String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onStartAutomation: ((durationSec: Double, startZoom: Double, endZoom: Double, startEv: Float, endEv: Float, startFocus: Float, endFocus: Float) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Presets, 1: Custom
    var selectedPresetId by remember(activePresetId) { mutableStateOf(activePresetId) }
    var triggerMode by remember(activeTriggerMode) {
        mutableStateOf(CommixModeCatalog.normalizeAutomationTrigger(activeTriggerMode))
    }

    val safeMaxZoom = maxZoom.toFloat().coerceAtLeast(10f)

    // Custom controls state
    var durationSec by remember { mutableFloatStateOf(10.0f) }
    var startZoom by remember { mutableFloatStateOf(1.0f) }
    var endZoom by remember { mutableFloatStateOf(3.5f.coerceAtMost(safeMaxZoom)) }
    var enableZoomRamp by remember { mutableStateOf(true) }

    var startEv by remember { mutableFloatStateOf(0.0f) }
    var endEv by remember { mutableFloatStateOf(0.0f) }
    var enableEvRamp by remember { mutableStateOf(false) }

    var startFocus by remember { mutableFloatStateOf(1.0f) }
    var endFocus by remember { mutableFloatStateOf(5.0f) }
    var enableFocusRamp by remember { mutableStateOf(false) }

    CommixModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            CommixSheetHeader(
                title = "Motion Automation",
                subtitle = "Keyframe presets & custom motion ramps",
                icon = Icons.Default.ElectricBolt,
                iconTint = AppleYellow,
                onDismiss = onDismiss
            )

            // Segmented Tab Selector (Presets vs Custom)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CinemaSurface)
                    .border(0.8.dp, CinemaGlassBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                listOf(0 to "BUILT-IN PRESETS", 1 to "CUSTOM MOTION").forEach { (idx, label) ->
                    val isSelected = selectedTab == idx
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) AppleYellow else CinemaSurface)
                            .testTag("automation_tab_$idx")
                            .clickable { selectedTab = idx }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) CinemaBlack else CinemaTextDim,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body Area
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(AutomationEngine.BUILT_IN_PRESETS, key = { it.id }) { preset ->
                            val isSelected = (preset.id == selectedPresetId)
                            PresetCard(
                                preset = preset,
                                isSelected = isSelected,
                                onSelect = {
                                    selectedPresetId = preset.id
                                    onSelectPreset(preset.id)
                                }
                            )
                        }
                    }
                } else {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Duration Card
                        CommixSectionCard(title = "Sequence Duration: ${durationSec.toInt()}s") {
                            Slider(
                                value = durationSec,
                                onValueChange = { durationSec = it },
                                valueRange = 3f..30f,
                                steps = 26,
                                colors = commixSliderColors()
                            )
                        }

                        // Zoom Ramp Card
                        CommixSectionCard(title = "Zoom Ramping Track") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (enableZoomRamp) String.format(Locale.US, "%.1f× → %.1f×", startZoom, endZoom) else "Disabled",
                                    color = if (enableZoomRamp) AppleYellow else CinemaTextDim,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = enableZoomRamp,
                                    onCheckedChange = { enableZoomRamp = it },
                                    colors = commixSwitchColors()
                                )
                            }
                            if (enableZoomRamp) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Start Zoom: ${String.format(Locale.US, "%.1f×", startZoom)}", color = CinemaTextLight, fontSize = 10.5.sp)
                                Slider(
                                    value = startZoom.coerceIn(1.0f, safeMaxZoom),
                                    onValueChange = { startZoom = it },
                                    valueRange = 1.0f..safeMaxZoom,
                                    colors = commixSliderColors()
                                )
                                Text(text = "End Zoom: ${String.format(Locale.US, "%.1f×", endZoom)}", color = CinemaTextLight, fontSize = 10.5.sp)
                                Slider(
                                    value = endZoom.coerceIn(1.0f, safeMaxZoom),
                                    onValueChange = { endZoom = it },
                                    valueRange = 1.0f..safeMaxZoom,
                                    colors = commixSliderColors()
                                )
                            }
                        }

                        // EV Ramp Card
                        CommixSectionCard(title = "Exposure Compensation Track") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (enableEvRamp) String.format(Locale.US, "%+.1f EV → %+.1f EV", startEv, endEv) else "Disabled",
                                    color = if (enableEvRamp) AppleYellow else CinemaTextDim,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = enableEvRamp,
                                    onCheckedChange = { enableEvRamp = it },
                                    colors = commixSwitchColors()
                                )
                            }
                            if (enableEvRamp) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Start EV: ${String.format(Locale.US, "%+.1f", startEv)}", color = CinemaTextLight, fontSize = 10.5.sp)
                                Slider(
                                    value = startEv.coerceIn(-3.0f, 3.0f),
                                    onValueChange = { startEv = it },
                                    valueRange = -3.0f..3.0f,
                                    colors = commixSliderColors()
                                )
                                Text(text = "End EV: ${String.format(Locale.US, "%+.1f", endEv)}", color = CinemaTextLight, fontSize = 10.5.sp)
                                Slider(
                                    value = endEv.coerceIn(-3.0f, 3.0f),
                                    onValueChange = { endEv = it },
                                    valueRange = -3.0f..3.0f,
                                    colors = commixSliderColors()
                                )
                            }
                        }

                        // Focus Ramp Card
                        CommixSectionCard(title = "Focus Pull Track") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (enableFocusRamp) String.format(Locale.US, "%.1f D → %.1f D", startFocus, endFocus) else "Disabled",
                                    color = if (enableFocusRamp) AppleYellow else CinemaTextDim,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = enableFocusRamp,
                                    onCheckedChange = { enableFocusRamp = it },
                                    colors = commixSwitchColors()
                                )
                            }
                            if (enableFocusRamp) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Near Focus (Start): ${String.format(Locale.US, "%.1f D", startFocus)}", color = CinemaTextLight, fontSize = 10.5.sp)
                                Slider(
                                    value = startFocus.coerceIn(0.0f, 10.0f),
                                    onValueChange = { startFocus = it },
                                    valueRange = 0.0f..10.0f,
                                    colors = commixSliderColors()
                                )
                                Text(text = "Far Focus (End): ${String.format(Locale.US, "%.1f D", endFocus)}", color = CinemaTextLight, fontSize = 10.5.sp)
                                Slider(
                                    value = endFocus.coerceIn(0.0f, 10.0f),
                                    onValueChange = { endFocus = it },
                                    valueRange = 0.0f..10.0f,
                                    colors = commixSliderColors()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Unified Canonical Trigger Selector (all 5 triggers from CommixModeCatalog)
            CommixSectionCard(title = "Trigger Mode") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    CommixModeCatalog.AUTOMATION_TRIGGER_OPTIONS.forEach { (key, label) ->
                        val isSel = (key == triggerMode)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSel) AppleYellow else CinemaSurfaceElevated)
                                .border(
                                    width = 0.8.dp,
                                    color = if (isSel) AppleYellow else CinemaGlassBorder,
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .testTag("automation_trigger_$key")
                                .clickable {
                                    triggerMode = key
                                    onSelectTrigger(key)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) CinemaBlack else CinemaTextLight,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button
            val activePreset = AutomationEngine.getPresetById(selectedPresetId)
            val buttonText = if (selectedTab == 0) {
                "EXECUTE ${activePreset.title.uppercase()} (${activePreset.durationSeconds.toInt()}s)"
            } else {
                "START CUSTOM MOTION (${durationSec.toInt()}s)"
            }

            CommixPrimaryButton(
                text = buttonText,
                icon = Icons.Default.PlayArrow,
                testTagName = "execute_automation_button",
                onClick = {
                    if (selectedTab == 0) {
                        onStartPreset(selectedPresetId, triggerMode)
                    } else {
                        if (onStartAutomation != null) {
                            onStartAutomation(
                                durationSec.toDouble(),
                                if (enableZoomRamp) startZoom.toDouble() else 1.0,
                                if (enableZoomRamp) endZoom.toDouble() else 1.0,
                                if (enableEvRamp) startEv else 0f,
                                if (enableEvRamp) endEv else 0f,
                                if (enableFocusRamp) startFocus else -1f,
                                if (enableFocusRamp) endFocus else -1f
                            )
                        }
                        onStartCustom(
                            durationSec.toDouble(),
                            if (enableZoomRamp) startZoom.toDouble() else 1.0,
                            if (enableZoomRamp) endZoom.toDouble() else 1.0,
                            if (enableEvRamp) startEv else 0f,
                            if (enableEvRamp) endEv else 0f,
                            if (enableFocusRamp) startFocus else -1f,
                            if (enableFocusRamp) endFocus else -1f,
                            triggerMode
                        )
                    }
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PresetCard(
    preset: AutomationPresetConfig,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) CinemaSurfaceElevated else CinemaSurface)
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = if (isSelected) AppleYellow else CinemaGlassBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("automation_preset_${preset.id}")
            .clickable { onSelect() }
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = preset.icon, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = preset.title,
                        color = if (isSelected) AppleYellow else CinemaTextLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    CommixBadgePill(
                        text = "${preset.durationSeconds.toInt()}s",
                        accentColor = AppleYellow,
                        monospace = true
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = preset.description,
                    color = CinemaTextDim,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected preset",
                    tint = AppleYellow,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 6.dp)
                )
            }
        }
    }
}
