package com.txor.commix.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PanoramaHorizontal
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.PanoramaGuidanceSeverity
import com.txor.commix.ui.theme.AppleYellow
import com.txor.commix.ui.theme.CinemaBlack
import com.txor.commix.ui.theme.CinemaCyan
import com.txor.commix.ui.theme.CinemaGlass
import com.txor.commix.ui.theme.CinemaGlassBorder
import com.txor.commix.ui.theme.CinemaGreen
import com.txor.commix.ui.theme.CinemaTextLight
import com.txor.commix.ui.theme.RecordingRed
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Flagship-style Panorama & 360° Photo Sphere Shooting HUD:
 * - Live Panoramic Filmstrip Ribbon painting real captured slice thumbnails across the viewfinder
 * - Spring-animated leading-edge arrow that rides vertically with pitch drift so users keep it on the line
 * - Tap-to-flip sweep direction (Left->Right / Right->Left) before capture
 * - Real-time speed, tilt, vertical-drift, and direction coaching pill
 */
@Composable
fun PanoramaGuidanceOverlay(
    sweepAngle: Float,
    progress: Float,
    rollDeg: Float,
    pitchDeg: Float,
    is360Sphere: Boolean,
    isCapturing: Boolean = false,
    capturedFrames: Int = 0,
    direction: Int = 1,
    verticalDriftDeg: Float = 0f,
    panSpeedDegPerSec: Float = 0f,
    guidanceHint: String = "",
    guidanceSeverity: String = PanoramaGuidanceSeverity.OK.name,
    previewStrip: List<Bitmap> = emptyList(),
    onToggle360: () -> Unit,
    onToggleDirection: () -> Unit = {},
    onFinishStitch: () -> Unit = {},
    onCancelCapture: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLeftToRight = direction >= 0
    val accent = if (is360Sphere) CinemaCyan else AppleYellow
    val targetDeg = if (is360Sphere) 360 else 180

    val severityEnum = remember(guidanceSeverity) {
        try {
            PanoramaGuidanceSeverity.valueOf(guidanceSeverity)
        } catch (_: Exception) {
            PanoramaGuidanceSeverity.OK
        }
    }

    val isIdleLevel = abs(rollDeg) < 6.5f && abs(pitchDeg) < 18.0f
    val targetGuideColor = when {
        isCapturing && (severityEnum == PanoramaGuidanceSeverity.WARN_SPEED || severityEnum == PanoramaGuidanceSeverity.WARN_DIRECTION) -> RecordingRed
        isCapturing && (severityEnum == PanoramaGuidanceSeverity.WARN_DRIFT || severityEnum == PanoramaGuidanceSeverity.WARN_TILT) -> AppleYellow
        isCapturing -> CinemaGreen
        isIdleLevel -> accent
        else -> AppleYellow
    }
    val guideColor by animateColorAsState(
        targetValue = targetGuideColor,
        animationSpec = tween(180),
        label = "panoGuideColor"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "panoProgress"
    )

    // During capture, track vertical drift relative to start pitch; when idle, gently indicate roll/pitch balance
    val rawDriftDeg = if (isCapturing) verticalDriftDeg else 0f
    val animatedDriftDeg by animateFloatAsState(
        targetValue = rawDriftDeg.coerceIn(-12f, 12f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "panoDrift"
    )

    val animatedRollDeg by animateFloatAsState(
        targetValue = rollDeg.coerceIn(-18f, 18f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "panoRoll"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pano_arrow_pulse")
    val idleArrowNudge by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleArrowNudge"
    )

    // Convert live strip bitmaps to ImageBitmaps safely (skip recycled bitmaps)
    val stripImages = remember(previewStrip) {
        previewStrip.mapNotNull { bmp ->
            if (!bmp.isRecycled) bmp.asImageBitmap() else null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Top Status & Action Bar (offset cleanly below CLEAN VIEW & QUEUE pills)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 46.dp)
        ) {
            // Mode & live sweep counter badge
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CinemaGlass)
                    .border(0.8.dp, guideColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (is360Sphere) Icons.Default.Public else Icons.Default.PanoramaHorizontal,
                        contentDescription = null,
                        tint = guideColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isCapturing) {
                            "${if (is360Sphere) "360°" else "PANO"} · ${sweepAngle.roundToInt()}° / ${targetDeg}° · $capturedFrames"
                        } else if (is360Sphere) {
                            "360° PHOTO SPHERE"
                        } else {
                            "PANORAMA"
                        },
                        color = CinemaTextLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.35.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isCapturing) {
                // Direction flip pill (L->R / R<-L)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CinemaGlass)
                        .border(0.8.dp, CinemaGlassBorder, RoundedCornerShape(12.dp))
                        .testTag("pano_direction_pill")
                        .clickable { onToggleDirection() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Flip Panorama Direction",
                            tint = CinemaTextLight,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isLeftToRight) "L → R" else "R ← L",
                            color = CinemaTextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // 360° Photo Sphere toggle pill
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (is360Sphere) CinemaCyan else CinemaGlass)
                        .border(0.8.dp, accent, RoundedCornerShape(12.dp))
                        .testTag("pano_360_toggle")
                        .clickable { onToggle360() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (is360Sphere) "360° ON" else "360°",
                        color = if (is360Sphere) CinemaBlack else accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(CinemaGlass)
                            .border(0.8.dp, RecordingRed.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .testTag("pano_cancel_button")
                            .clickable { onCancelCapture() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Panorama",
                            tint = RecordingRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (capturedFrames >= 1) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CinemaGreen)
                                .testTag("pano_finish_stitch")
                                .clickable { onFinishStitch() }
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Finish and Stitch",
                                    tint = CinemaBlack,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "DONE",
                                    color = CinemaBlack,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Center Flagship Panoramic Filmstrip Ribbon + Live Preview + Vertical-Drift Arrow
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x9908080A))
                    .border(
                        width = 1.dp,
                        color = if (isCapturing) guideColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .testTag("pano_direction_flip")
                    .clickable(enabled = !isCapturing) { onToggleDirection() }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val centerY = h / 2f
                    val edgePad = 14.dp.toPx()
                    val usableW = (w - edgePad * 2f).coerceAtLeast(1f)

                    // Subtle degree ticks along top/bottom of ribbon (every 30°)
                    val tickSegments = if (is360Sphere) 12 else 6
                    for (t in 1 until tickSegments) {
                        val tx = edgePad + usableW * (t.toFloat() / tickSegments.toFloat())
                        drawLine(
                            color = Color.White.copy(alpha = 0.16f),
                            start = Offset(tx, 3.dp.toPx()),
                            end = Offset(tx, 9.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.16f),
                            start = Offset(tx, h - 9.dp.toPx()),
                            end = Offset(tx, h - 3.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Live captured panorama filmstrip painted progressively inside the ribbon
                    val filledW = if (isCapturing) {
                        (usableW * animatedProgress).coerceAtLeast(if (stripImages.isNotEmpty()) 26.dp.toPx() else 0f)
                    } else 0f

                    if (isCapturing && stripImages.isNotEmpty() && filledW > 2f) {
                        val sliceCount = stripImages.size
                        val stripStartX = if (isLeftToRight) edgePad else (w - edgePad - filledW)
                        val slotW = (filledW / sliceCount.toFloat()).coerceAtLeast(1f)

                        for (idx in 0 until sliceCount) {
                            val img = stripImages[idx]
                            val drawIdx = if (isLeftToRight) idx else (sliceCount - 1 - idx)
                            val dstLeft = (stripStartX + drawIdx * slotW).roundToInt()
                            val dstW = (slotW + 1.5f).roundToInt().coerceAtLeast(1)
                            // Center-crop a vertical strip from each slice so the live ribbon stays crisp
                            val srcSliceW = (img.width * 0.45f).roundToInt().coerceIn(1, img.width)
                            val srcLeft = ((img.width - srcSliceW) / 2).coerceAtLeast(0)
                            drawImage(
                                image = img,
                                srcOffset = IntOffset(srcLeft, 0),
                                srcSize = IntSize(srcSliceW, img.height),
                                dstOffset = IntOffset(dstLeft, 2),
                                dstSize = IntSize(dstW, (h - 4).roundToInt().coerceAtLeast(1))
                            )
                        }

                        // Subtle dark tint over strip so horizon guide line & arrow remain razor legible
                        drawRect(
                            color = Color.Black.copy(alpha = 0.24f),
                            topLeft = Offset(stripStartX, 0f),
                            size = Size(filledW, h)
                        )
                    } else if (isCapturing && filledW > 2f) {
                        val stripStartX = if (isLeftToRight) edgePad else (w - edgePad - filledW)
                        drawRect(
                            color = guideColor.copy(alpha = 0.18f),
                            topLeft = Offset(stripStartX, 0f),
                            size = Size(filledW, h)
                        )
                    }

                    // Horizontal center horizon guide rail
                    drawLine(
                        color = guideColor.copy(alpha = if (isCapturing) 0.92f else 0.72f),
                        start = Offset(edgePad, centerY),
                        end = Offset(w - edgePad, centerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Leading-edge vertical cursor bar + animated vertical-drift arrow
                    val leadX = if (isCapturing) {
                        if (isLeftToRight) {
                            (edgePad + filledW).coerceIn(edgePad + 16.dp.toPx(), w - edgePad - 12.dp.toPx())
                        } else {
                            (w - edgePad - filledW).coerceIn(edgePad + 12.dp.toPx(), w - edgePad - 16.dp.toPx())
                        }
                    } else {
                        val base = if (isLeftToRight) edgePad + 24.dp.toPx() else w - edgePad - 24.dp.toPx()
                        base + (if (isLeftToRight) idleArrowNudge else -idleArrowNudge).dp.toPx()
                    }

                    // Vertical cursor line at the leading capture frontier
                    if (isCapturing) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.9f),
                            start = Offset(leadX, 6.dp.toPx()),
                            end = Offset(leadX, h - 6.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Arrow vertical offset reflects pitch drift relative to starting horizon
                    // Aiming too high (+drift) pushes arrow ABOVE the line -> user tilts down to bring arrow onto line
                    val maxOffsetPx = (h * 0.34f)
                    val driftOffsetY = (-(animatedDriftDeg / 10f) * maxOffsetPx).coerceIn(-maxOffsetPx, maxOffsetPx)
                    val arrowCenterY = centerY + driftOffsetY

                    // Draw crisp white/colored directional arrow riding on the leading edge
                    val arrowLen = 20.dp.toPx()
                    val headSize = 7.dp.toPx()
                    val dirSign = if (isLeftToRight) 1f else -1f
                    val arrowColor = if (abs(animatedDriftDeg) <= 4.5f && isCapturing) Color.White else guideColor

                    rotate(degrees = animatedRollDeg * 0.5f, pivot = Offset(leadX, arrowCenterY)) {
                        // Subtle dark halo behind arrow for contrast against bright scenes
                        val haloPath = Path().apply {
                            moveTo(leadX - dirSign * (arrowLen * 0.45f), arrowCenterY)
                            lineTo(leadX + dirSign * (arrowLen * 0.55f), arrowCenterY)
                            moveTo(leadX + dirSign * (arrowLen * 0.55f - headSize), arrowCenterY - headSize)
                            lineTo(leadX + dirSign * (arrowLen * 0.55f), arrowCenterY)
                            lineTo(leadX + dirSign * (arrowLen * 0.55f - headSize), arrowCenterY + headSize)
                        }
                        drawPath(
                            path = haloPath,
                            color = Color.Black.copy(alpha = 0.65f),
                            style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = haloPath,
                            color = arrowColor,
                            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Priority Real-Time Coaching Pill (never fights with bottom Multi-Lens bar)
            val coachingText = when {
                isCapturing && guidanceHint.isNotBlank() -> guidanceHint
                isCapturing -> "Move camera steadily ${if (isLeftToRight) "right" else "left"}"
                !isIdleLevel -> "Level camera (${String.format(Locale.US, "%+.0f°", rollDeg)})"
                else -> "Move continuously ${if (isLeftToRight) "right" else "left"} · Tap arrow to flip"
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(CinemaGlass)
                    .border(
                        width = 0.85.dp,
                        color = if (severityEnum == PanoramaGuidanceSeverity.OK && (!isCapturing && isIdleLevel || isCapturing)) {
                            guideColor.copy(alpha = 0.6f)
                        } else {
                            guideColor
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isCapturing && severityEnum == PanoramaGuidanceSeverity.WARN_SPEED -> Icons.Default.Speed
                            isLeftToRight -> Icons.AutoMirrored.Filled.ArrowForward
                            else -> Icons.AutoMirrored.Filled.ArrowBack
                        },
                        contentDescription = null,
                        tint = guideColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = coachingText,
                        color = if (isCapturing && severityEnum != PanoramaGuidanceSeverity.OK) guideColor else CinemaTextLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Live pan-speed rhythm bar while shooting so user feels ideal sweep tempo
                if (isCapturing) {
                    Spacer(modifier = Modifier.height(5.dp))
                    val speedNorm = (panSpeedDegPerSec / 40f).coerceIn(0.05f, 1f)
                    val speedColor = when {
                        panSpeedDegPerSec > 34f -> RecordingRed
                        panSpeedDegPerSec > 26f -> AppleYellow
                        else -> CinemaGreen
                    }
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(CinemaGlassBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(speedNorm)
                                .background(speedColor)
                        )
                    }
                }
            }
        }
    }
}
