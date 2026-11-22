package com.txor.commix.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Interactive Pro Focus Loupe & Manual Diopter Micro-Stepper.
 * - Magnifies the live viewfinder (2x / 3x / 4x) and restores prior zoom on close.
 * - Displays live focus peaking score and real optical distance in meters / cm / diopters.
 * - Provides direct +/- fine diopter stepping, Infinity lock, and Auto-Focus reset.
 */
@Composable
fun FocusLoupe(
    currentFocusDiopters: Float,
    focusPeakScore: Float = 0f,
    currentZoomRatio: Double = 1.0,
    onStepFocus: (Float) -> Unit = {},
    onSetFocus: (Float) -> Unit = {},
    onMagnificationChanged: (Double) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(40f) }
    val initialZoom = remember { currentZoomRatio.coerceAtLeast(1.0) }
    var magnification by remember { mutableFloatStateOf(2.0f) }

    LaunchedEffect(magnification) {
        onMagnificationChanged(magnification.toDouble())
    }

    DisposableEffect(Unit) {
        onDispose {
            onMagnificationChanged(initialZoom)
        }
    }

    val focusText = remember(currentFocusDiopters) {
        if (currentFocusDiopters < 0f) {
            "AF · AUTO"
        } else if (currentFocusDiopters <= 0.05f) {
            "∞ INFINITY"
        } else {
            val meters = 1.0f / currentFocusDiopters
            if (meters < 1.0f) {
                String.format(Locale.US, "%d cm · %.1fD", (meters * 100).roundToInt(), currentFocusDiopters)
            } else {
                String.format(Locale.US, "%.2f m · %.1fD", meters, currentFocusDiopters)
            }
        }
    }

    val peakPct = focusPeakScore.toInt().coerceIn(0, 100)
    val peakColor = when {
        peakPct >= 35 -> CinemaGreen
        peakPct >= 18 -> AppleYellow
        else -> CinemaTextDim
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(184.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CinemaGlass)
            .border(1.2.dp, AppleYellow.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(-300f, 300f)
                    offsetY = (offsetY + dragAmount.y).coerceIn(-50f, 700f)
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CinemaSurface.copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = AppleYellow,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${magnification.toInt()}× LOUPE",
                        color = AppleYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(CinemaSubtleBg)
                        .testTag("close_focus_loupe_button")
                        .clickable { onClose() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Loupe",
                        tint = CinemaTextLight,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // 2. Concentric Reticle & Live Focus Peak Window
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    drawCircle(
                        CinemaGlassBorder,
                        radius = 30.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(1.dp.toPx())
                    )
                    drawCircle(
                        peakColor.copy(alpha = 0.65f),
                        radius = 16.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(1.2.dp.toPx())
                    )

                    val cross = 9.dp.toPx()
                    drawLine(peakColor, Offset(cx - cross, cy), Offset(cx + cross, cy), strokeWidth = 1.4.dp.toPx())
                    drawLine(peakColor, Offset(cx, cy - cross), Offset(cx, cy + cross), strokeWidth = 1.4.dp.toPx())
                }

                // Live Peak Focus Indicator (Top-Left)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 7.dp, top = 5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CinemaPillBg)
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = "PK $peakPct%",
                        color = peakColor,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Focus Distance Readout (Bottom-Center)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(CinemaPillBg)
                        .border(0.5.dp, CinemaGlassBorder, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = focusText,
                        color = CinemaGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // 3. Interactive Manual Focus Fine-Steppers (AF / ∞ / - / +)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CinemaSurface.copy(alpha = 0.72f))
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                LoupeMiniButton(
                    label = "AF",
                    active = currentFocusDiopters < 0f,
                    onClick = { onSetFocus(-1.0f) },
                    modifier = Modifier.weight(1f).testTag("loupe_af_button")
                )
                LoupeMiniButton(
                    label = "∞",
                    active = currentFocusDiopters in 0f..0.05f,
                    onClick = { onSetFocus(0.0f) },
                    modifier = Modifier.weight(1f).testTag("loupe_inf_button")
                )
                LoupeMiniButton(
                    label = "−",
                    active = false,
                    onClick = { onStepFocus(-0.25f) },
                    modifier = Modifier.weight(1f).testTag("loupe_step_far_button")
                )
                LoupeMiniButton(
                    label = "+",
                    active = false,
                    onClick = { onStepFocus(0.25f) },
                    modifier = Modifier.weight(1f).testTag("loupe_step_near_button")
                )
            }

            // 4. Live Optical/Digital Magnification Selector Pills (2x, 3x, 4x)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CinemaBlack.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                listOf(2.0f, 3.0f, 4.0f).forEach { mag ->
                    val isSelected = (magnification == mag)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) AppleYellow else CinemaSubtleBg)
                            .clickable { magnification = mag }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "${mag.toInt()}×",
                            color = if (isSelected) CinemaBlack else CinemaTextLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoupeMiniButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) CinemaGreen else CinemaSubtleBg)
            .border(0.6.dp, if (active) CinemaGreen else CinemaGlassBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (active) CinemaBlack else CinemaTextLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}
