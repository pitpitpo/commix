package com.txor.commix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.txor.commix.ui.theme.*

/**
 * Single source of truth for Commix camera modes, settings tabs, and canonical automation triggers.
 * Keeps ModeCarousel, MainSettingsModal, ModeContextControlBar, and AutomationModal 100% aligned.
 */
object CommixModeCatalog {
    val ALL_CAMERA_MODES: List<String> = listOf(
        "NIGHT",
        "LONG EXP",
        "SLOW-MO",
        "VIDEO",
        "CINEMATIC",
        "PHOTO",
        "PRO",
        "TIME-LAPSE",
        "PANORAMA",
        "SPATIAL",
        "AUTOMATION"
    )

    val ALL_SETTINGS_TABS: List<String> = listOf(
        "PHOTO",
        "VIDEO",
        "PRO",
        "CINEMATIC",
        "NIGHT",
        "LONG EXP",
        "SLOW-MO",
        "TIME-LAPSE",
        "PANORAMA",
        "SPATIAL",
        "AUTOMATION",
        "FLAGSHIPS",
        "QUEUE",
        "GENERAL"
    )

    val AUTOMATION_TRIGGER_OPTIONS: List<Pair<String, String>> = listOf(
        "timer_3s" to "3s Timer",
        "timer_5s" to "5s Timer",
        "audio_clap" to "Audio Clap",
        "gyro_shake" to "Gyro Shake",
        "instant" to "Instant"
    )

    fun normalizeAutomationTrigger(raw: String): String = when (raw.trim().lowercase()) {
        "timer", "timer_3s", "3s" -> "timer_3s"
        "timer_5s", "5s" -> "timer_5s"
        "audio_clap", "clap", "audio" -> "audio_clap"
        "gyro_shake", "shake", "motion" -> "gyro_shake"
        "instant", "immediate", "now" -> "instant"
        else -> "timer_3s"
    }

    fun formatAutomationTriggerShort(raw: String): String = when (normalizeAutomationTrigger(raw)) {
        "timer_3s" -> "3S TIMER"
        "timer_5s" -> "5S TIMER"
        "audio_clap" -> "CLAP"
        "gyro_shake" -> "SHAKE"
        "instant" -> "INSTANT"
        else -> "3S TIMER"
    }

    fun resolveSettingsTabForMode(mode: String): String =
        if (mode in ALL_SETTINGS_TABS) mode else "GENERAL"
}

/**
 * Unified Cinema ModalBottomSheet wrapper shared across all 8 modals in the app.
 * Guarantees consistent surface elevation, corner geometry, scrim, and drag handle everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommixModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = CinemaSheetBg,
        contentColor = CinemaTextLight,
        scrimColor = CinemaScrim,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = CinemaGlassBorder,
                width = 38.dp,
                height = 4.dp
            )
        },
        content = content
    )
}

/**
 * Unified Cinema Bottom Sheet Header:
 * optional leading icon badge + bold title + optional accent pill + optional subtitle + consistent Done pill.
 */
@Composable
fun CommixSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = AppleYellow,
    badgeText: String? = null,
    badgeColor: Color = AppleYellow,
    dismissLabel: String = "Done"
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.16f))
                    .border(0.8.dp, iconTint.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = title,
                    color = CinemaTextLight,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!badgeText.isNullOrBlank()) {
                    CommixBadgePill(
                        text = badgeText,
                        accentColor = badgeColor,
                        monospace = true
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = CinemaTextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(AppleYellow.copy(alpha = 0.16f))
                .border(0.8.dp, AppleYellow.copy(alpha = 0.48f), RoundedCornerShape(17.dp))
                .testTag("sheet_done_button")
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = dismissLabel,
                color = AppleYellow,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Unified elevated Cinema card container for grouping controls inside modals.
 */
@Composable
fun CommixSectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = AppleYellow,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CinemaSurface)
            .border(0.8.dp, CinemaGlassBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 0.3.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        content()
    }
}

/**
 * Unified full-width Cinema primary call-to-action button (48dp touch height).
 */
@Composable
fun CommixPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = AppleYellow,
    contentColor: Color = CinemaBlack,
    testTagName: String? = null
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Unified compact tag/status badge pill.
 */
@Composable
fun CommixBadgePill(
    text: String,
    accentColor: Color = AppleYellow,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    monospace: Boolean = false
) {
    val bg = if (filled) accentColor else accentColor.copy(alpha = 0.16f)
    val fg = if (filled) CinemaBlack else accentColor
    val border = accentColor.copy(alpha = if (filled) 1f else 0.45f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.6.dp, border, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun commixSliderColors(accentColor: Color = AppleYellow): SliderColors = SliderDefaults.colors(
    thumbColor = CinemaTextLight,
    activeTrackColor = accentColor,
    inactiveTrackColor = CinemaGlassBorder,
    activeTickColor = CinemaBlack,
    inactiveTickColor = CinemaTextDim
)

@Composable
fun commixSwitchColors(accentColor: Color = AppleYellow): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = CinemaBlack,
    checkedTrackColor = accentColor,
    uncheckedThumbColor = CinemaTextLight,
    uncheckedTrackColor = CinemaSurfaceElevated,
    uncheckedBorderColor = CinemaGlassBorder
)
