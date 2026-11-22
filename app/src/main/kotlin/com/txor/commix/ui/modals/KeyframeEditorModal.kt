package com.txor.commix.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.txor.commix.ui.components.CommixBadgePill
import com.txor.commix.ui.components.CommixModalBottomSheet
import com.txor.commix.ui.components.CommixPrimaryButton
import com.txor.commix.ui.components.CommixSheetHeader
import com.txor.commix.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyframeEditorModal(
    activePresetId: String,
    onSelectPreset: (AutomationPresetConfig) -> Unit,
    onArmShot: () -> Unit,
    onDismiss: () -> Unit
) {
    val presets = AutomationEngine.BUILT_IN_PRESETS
    var selectedPreset by remember(activePresetId) {
        mutableStateOf(presets.find { it.id == activePresetId } ?: presets.first())
    }

    CommixModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .navigationBarsPadding()
        ) {
            CommixSheetHeader(
                title = "Keyframe Presets",
                subtitle = "C++20 cubic keyframe motion curves",
                icon = Icons.Default.Timeline,
                iconTint = AppleYellow,
                badgeText = "${presets.size} CURVES",
                onDismiss = onDismiss
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(presets, key = { it.id }) { preset ->
                    val isSelected = (preset.id == selectedPreset.id)
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
                            .testTag("keyframe_preset_${preset.id}")
                            .clickable {
                                selectedPreset = preset
                                onSelectPreset(preset)
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = preset.icon, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = preset.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = if (isSelected) AppleYellow else CinemaTextLight,
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
                                        fontSize = 11.sp,
                                        color = CinemaTextDim,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected keyframe preset",
                                    tint = AppleYellow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            CommixPrimaryButton(
                text = "ARM ${selectedPreset.title.uppercase()}",
                icon = Icons.Default.PlayArrow,
                testTagName = "arm_automation_button",
                onClick = {
                    AutomationEngine.programNativeBridge(selectedPreset)
                    onSelectPreset(selectedPreset)
                    onArmShot()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
