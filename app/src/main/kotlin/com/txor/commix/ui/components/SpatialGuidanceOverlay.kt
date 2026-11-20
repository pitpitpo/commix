package com.txor.commix.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.SpatialCaptureEngine
import com.txor.commix.SpatialFov
import com.txor.commix.SpatialTargetNode
import com.txor.commix.ui.theme.AppleYellow
import com.txor.commix.ui.theme.CinemaCyan
import com.txor.commix.ui.theme.CinemaGreen
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D Spherical AR Guidance HUD for SPATIAL Mode (180° VR Hemisphere & 360° Full Sphere).
 *
 * - Projects live 3D spherical target nodes onto the viewfinder with roll compensation.
 * - Draws a directional guidance arrow toward the nearest uncaptured node when off-center.
 * - Renders a compact live equirectangular coverage mini-map showing captured vs remaining tiles.
 * - Positioned cleanly below top pills (46dp) and above bottom lens pill (62dp) with zero overlap.
 */
@Composable
fun SpatialGuidanceOverlay(
    fovDeg: Int,
    isCapturing: Boolean,
    nodes: List<SpatialTargetNode>,
    capturedCount: Int,
    totalNodes: Int,
    relYawDeg: Float,
    relPitchDeg: Float,
    rollDeg: Float,
    autoCapture: Boolean,
    onSelectFov: (Int) -> Unit,
    onFinishStitch: () -> Unit,
    onCancelCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fov = remember(fovDeg) { SpatialFov.fromDegrees(fovDeg) }
    val activeNodes = remember(nodes, fov) {
        if (nodes.isNotEmpty()) nodes else SpatialCaptureEngine.generateTargetGrid(fov)
    }
    val nearest = remember(relYawDeg, relPitchDeg, activeNodes) {
        SpatialCaptureEngine.findNearestUncapturedNode(relYawDeg, relPitchDeg, activeNodes)
    }
    val isLockedOnTarget = isCapturing && nearest != null &&
        nearest.second <= SpatialCaptureEngine.ALIGNMENT_LOCK_TOLERANCE_DEG

    val infiniteTransition = rememberInfiniteTransition(label = "spatial_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val accent = if (fov == SpatialFov.SPATIAL_360) CinemaCyan else AppleYellow

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Live 3D Projected AR Target Lattice & Center Reticle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // Subtle spherical horizon reference arc
            val ringRadius = 22.dp.toPx()
            val reticleColor = when {
                isLockedOnTarget -> CinemaGreen
                isCapturing -> accent
                else -> Color(0xCCFFFFFF)
            }

            // Project & draw every spherical target node when capturing (or preview grid when idle)
            val nearestId = nearest?.first?.id ?: -1
            for (node in activeNodes) {
                val proj = SpatialCaptureEngine.projectPointToViewfinder(
                    targetYawDeg = node.yawDeg,
                    targetPitchDeg = node.pitchDeg,
                    currentYawDeg = if (isCapturing) relYawDeg else 0f,
                    currentPitchDeg = if (isCapturing) relPitchDeg else 0f,
                    rollDeg = rollDeg
                )
                if (!proj.inFront) continue
                val px = proj.normX * w
                val py = proj.normY * h
                if (px < -30f || px > w + 30f || py < -30f || py > h + 30f) continue

                when {
                    node.captured -> {
                        drawCircle(
                            color = CinemaGreen.copy(alpha = 0.30f),
                            radius = 10.dp.toPx(),
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = CinemaGreen,
                            radius = 4.5.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                    node.id == nearestId && isCapturing -> {
                        // Pulsing active target sphere
                        drawCircle(
                            color = accent.copy(alpha = 0.22f * pulse),
                            radius = 18.dp.toPx() * (0.92f + 0.15f * pulse),
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = accent,
                            radius = 12.dp.toPx(),
                            center = Offset(px, py),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = accent,
                            radius = 4.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                    else -> {
                        drawCircle(
                            color = Color.White.copy(alpha = if (isCapturing) 0.55f else 0.35f),
                            radius = 7.dp.toPx(),
                            center = Offset(px, py),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = if (isCapturing) 0.70f else 0.45f),
                            radius = 2.2.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // Directional Guidance Arrow from center toward nearest uncaptured target
            if (isCapturing && nearest != null && nearest.second > SpatialCaptureEngine.ALIGNMENT_LOCK_TOLERANCE_DEG) {
                val targetNode = nearest.first
                val dyaw = SpatialCaptureEngine.normalizeYaw(targetNode.yawDeg - relYawDeg)
                val dpitch = targetNode.pitchDeg - relPitchDeg
                val angle = atan2(-dpitch.toDouble(), dyaw.toDouble()).toFloat()
                val arrowDist = ringRadius + 18.dp.toPx()
                val tipX = cx + cos(angle) * (arrowDist + 9.dp.toPx())
                val tipY = cy + sin(angle) * (arrowDist + 9.dp.toPx())
                val baseX = cx + cos(angle) * arrowDist
                val baseY = cy + sin(angle) * arrowDist
                val perpX = -sin(angle) * 6.dp.toPx()
                val perpY = cos(angle) * 6.dp.toPx()

                val arrowPath = Path().apply {
                    moveTo(tipX, tipY)
                    lineTo(baseX + perpX, baseY + perpY)
                    lineTo(baseX - perpX, baseY - perpY)
                    close()
                }
                drawPath(arrowPath, color = accent.copy(alpha = pulse))
            }

            // Center Alignment Reticle
            drawCircle(
                color = reticleColor,
                radius = ringRadius,
                center = Offset(cx, cy),
                style = Stroke(width = if (isLockedOnTarget) 3.dp.toPx() else 2.dp.toPx())
            )
            // Reticle crosshair ticks
            val tickInner = ringRadius + 3.dp.toPx()
            val tickOuter = ringRadius + 9.dp.toPx()
            drawLine(reticleColor, Offset(cx - tickOuter, cy), Offset(cx - tickInner, cy), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(reticleColor, Offset(cx + tickInner, cy), Offset(cx + tickOuter, cy), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(reticleColor, Offset(cx, cy - tickOuter), Offset(cx, cy - tickInner), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(reticleColor, Offset(cx, cy + tickInner), Offset(cx, cy + tickOuter), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(
                color = reticleColor,
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )
        }

        // 2. Top Spatial Bar (46dp from top — clears CLEAN VIEW & QUEUE pills with zero collision)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 46.dp, start = 12.dp, end = 12.dp)
        ) {
            // Left Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xBB000000))
                    .border(0.8.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = "Spatial Mode",
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isCapturing) {
                        "SPATIAL ${fov.label} · $capturedCount/$totalNodes"
                    } else {
                        "SPATIAL ${fov.label} ${if (fov == SpatialFov.SPATIAL_360) "SPHERE" else "DOME"}"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isCapturing) {
                // 180° | 360° Segmented Switcher
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC000000))
                        .border(0.8.dp, Color(0x38FFFFFF), RoundedCornerShape(12.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(180 to "180°", 360 to "360°").forEach { (deg, label) ->
                        val selected = (fovDeg == deg)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) accent else Color.Transparent)
                                .testTag("spatial_fov_$deg")
                                .clickable { onSelectFov(deg) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            } else {
                // Active Capture Controls: Cancel + Finish & Stitch
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC000000))
                            .border(0.8.dp, Color(0x66FF453A), RoundedCornerShape(10.dp))
                            .clickable { onCancelCapture() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Spatial",
                            tint = Color(0xFFFF6961),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (capturedCount >= 1) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CinemaGreen)
                                .testTag("spatial_finish_stitch")
                                .clickable { onFinishStitch() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Finish and Stitch",
                                    tint = Color.Black,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "STITCH",
                                    color = Color.Black,
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

        // 3. Bottom Guidance Bar + Compact Equirectangular Coverage Radar (above floating lens pill at 60dp)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp, start = 12.dp, end = 12.dp)
        ) {
            // Live Coaching Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .border(0.8.dp, if (isLockedOnTarget) CinemaGreen else Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                val hint = if (!isCapturing) {
                    if (fov == SpatialFov.SPATIAL_360) {
                        "Tap shutter to start 360° full sphere capture"
                    } else {
                        "Tap shutter to start 180° VR dome capture"
                    }
                } else {
                    val base = SpatialCaptureEngine.coachingHint(relYawDeg, relPitchDeg, nearest)
                    if (!autoCapture && isLockedOnTarget) "Aligned — tap shutter to capture" else base
                }
                Text(
                    text = hint,
                    color = if (isLockedOnTarget) CinemaGreen else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Equirectangular Spherical Coverage Mini-Map (88x46dp)
            Canvas(
                modifier = Modifier
                    .width(88.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC0A0A0D))
                    .border(0.8.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            ) {
                val mw = size.width
                val mh = size.height
                // Equator & meridian gridlines
                drawLine(Color(0x33FFFFFF), Offset(0f, mh / 2f), Offset(mw, mh / 2f), strokeWidth = 1f)
                drawLine(Color(0x33FFFFFF), Offset(mw / 2f, 0f), Offset(mw / 2f, mh), strokeWidth = 1f)

                val yawSpan = fov.degrees.toFloat()
                for (n in activeNodes) {
                    val nx = ((n.yawDeg / yawSpan) + 0.5f).coerceIn(0.06f, 0.94f) * mw
                    val ny = ((90f - n.pitchDeg) / 180f).coerceIn(0.10f, 0.90f) * mh
                    drawCircle(
                        color = if (n.captured) CinemaGreen else Color.White.copy(alpha = 0.45f),
                        radius = if (n.captured) 3.dp.toPx() else 2.dp.toPx(),
                        center = Offset(nx, ny)
                    )
                }

                // Live camera look crosshair on radar
                if (isCapturing) {
                    val lx = ((SpatialCaptureEngine.normalizeYaw(relYawDeg) / yawSpan) + 0.5f).coerceIn(0.05f, 0.95f) * mw
                    val ly = ((90f - relPitchDeg) / 180f).coerceIn(0.08f, 0.92f) * mh
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(lx - 5.dp.toPx(), ly - 4.dp.toPx()),
                        size = Size(10.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            }
        }
    }
}
