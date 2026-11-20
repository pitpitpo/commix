package com.txor.commix.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.txor.commix.ui.theme.*

/**
 * High-performance hardware-accelerated scopes, rule-of-thirds grid,
 * pitch/roll horizon leveler, and chroma polar vectorscope.
 */
@Composable
fun ScopesOverlay(
    showWaveform: Boolean,
    showHistogram: Boolean,
    showHorizon: Boolean,
    showGrid: Boolean,
    gridType: String = "3x3",
    showVectorscope: Boolean = false,
    showRgbParade: Boolean = false,
    rollDeg: Float = 0f,
    pitchDeg: Float = 0f,
    waveformCols: FloatArray = FloatArray(0),
    lumaHistogram: IntArray = IntArray(0),
    vectorscopeSamples: FloatArray = FloatArray(0),
    vectorscopeCount: Int = 0,
    paradeR: FloatArray = FloatArray(0),
    paradeG: FloatArray = FloatArray(0),
    paradeB: FloatArray = FloatArray(0),
    modifier: Modifier = Modifier
) {
    val wavePath = remember { Path() }
    val histPath = remember { Path() }
    val paradePathR = remember { Path() }
    val paradePathG = remember { Path() }
    val paradePathB = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Composition Guides (3x3, 4x4, Golden Ratio, Diagonal, Crosshair)
        if (showGrid && gridType != "off") {
            val gridColor = Color(0x40FFFFFF)
            when (gridType.lowercase()) {
                "4x4" -> {
                    for (i in 1..3) {
                        val vx = w * i / 4f
                        val hy = h * i / 4f
                        drawLine(gridColor, Offset(vx, 0f), Offset(vx, h), strokeWidth = 1f)
                        drawLine(gridColor, Offset(0f, hy), Offset(w, hy), strokeWidth = 1f)
                    }
                }
                "golden" -> {
                    val phi = 0.381966f
                    val invPhi = 1f - phi
                    drawLine(gridColor, Offset(w * phi, 0f), Offset(w * phi, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(w * invPhi, 0f), Offset(w * invPhi, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, h * phi), Offset(w, h * phi), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, h * invPhi), Offset(w, h * invPhi), strokeWidth = 1f)
                }
                "diagonal" -> {
                    drawLine(gridColor, Offset(0f, 0f), Offset(w, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(w, 0f), Offset(0f, h), strokeWidth = 1f)
                    drawLine(Color(0x22FFFFFF), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1f)
                    drawLine(Color(0x22FFFFFF), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)
                }
                "crosshair" -> {
                    val cx = w / 2f
                    val cy = h / 2f
                    drawLine(gridColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f)
                    drawRect(
                        color = Color(0x28FFFFFF),
                        topLeft = Offset(w * 0.1f, h * 0.1f),
                        size = Size(w * 0.8f, h * 0.8f),
                        style = Stroke(width = 1f)
                    )
                }
                else -> { // "3x3"
                    drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1f)
                }
            }
        }

        // 2. Center Reticle & Horizon Leveler
        val cx = w / 2f
        val cy = h / 2f

        val isLevel = kotlin.math.abs(rollDeg) <= 0.5f && kotlin.math.abs(pitchDeg) <= 0.5f
        val reticleColor = if (showHorizon && isLevel) CinemaGreen else Color(0x80FFFFFF)

        val ringRadius = 15.dp.toPx()
        val wingRadius = 26.dp.toPx()
        drawCircle(
            color = reticleColor,
            radius = ringRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawCircle(
            color = reticleColor,
            radius = 2.dp.toPx(),
            center = Offset(cx, cy)
        )

        rotate(degrees = if (showHorizon) rollDeg else 0f, pivot = Offset(cx, cy)) {
            drawLine(
                color = reticleColor,
                start = Offset(cx - wingRadius, cy),
                end = Offset(cx - ringRadius, cy),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = reticleColor,
                start = Offset(cx + ringRadius, cy),
                end = Offset(cx + wingRadius, cy),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        if (showHorizon) {
            val clampedPitch = pitchDeg.coerceIn(-30f, 30f)
            val pitchOffset = clampedPitch * 1.4.dp.toPx()
            val ladderY = cy - pitchOffset
            val stepPx = 7.dp.toPx()

            drawLine(Color(0x66FFFFFF), Offset(cx - 10.dp.toPx(), ladderY - stepPx), Offset(cx + 10.dp.toPx(), ladderY - stepPx), strokeWidth = 1.dp.toPx())
            drawLine(Color(0x99FFFFFF), Offset(cx - 14.dp.toPx(), ladderY), Offset(cx + 14.dp.toPx(), ladderY), strokeWidth = 1.2.dp.toPx())
            drawLine(Color(0x66FFFFFF), Offset(cx - 10.dp.toPx(), ladderY + stepPx), Offset(cx + 10.dp.toPx(), ladderY + stepPx), strokeWidth = 1.dp.toPx())
        }

        val edgePad = 12.dp.toPx()
        val topSafeY = 54.dp.toPx()
        val bottomSafePad = 62.dp.toPx()

        // 3. Broadcast Waveform Scope (Bottom left mini overlay, cleanly above lens pill)
        if (showWaveform) {
            val scopeW = minOf(108.dp.toPx(), w * 0.34f)
            val scopeH = 56.dp.toPx()
            val scopeX = edgePad
            val scopeY = (h - scopeH - bottomSafePad).coerceAtLeast(topSafeY)

            drawRect(
                color = Color(0x99000000),
                topLeft = Offset(scopeX, scopeY),
                size = Size(scopeW, scopeH)
            )
            drawLine(Color(0x55FFFFFF), Offset(scopeX, scopeY), Offset(scopeX + scopeW, scopeY), strokeWidth = 1f)
            drawLine(Color(0x33FFFFFF), Offset(scopeX, scopeY + scopeH * 0.5f), Offset(scopeX + scopeW, scopeY + scopeH * 0.5f), strokeWidth = 1f)
            drawLine(Color(0x55FFFFFF), Offset(scopeX, scopeY + scopeH), Offset(scopeX + scopeW, scopeY + scopeH), strokeWidth = 1f)

            wavePath.reset()
            val colCount = if (waveformCols.isNotEmpty()) waveformCols.size else 64
            val startIre = if (waveformCols.isNotEmpty()) waveformCols[0].coerceIn(0f, 100f) else 50f
            wavePath.moveTo(scopeX, scopeY + scopeH * (1f - startIre / 100f))
            for (i in 0 until colCount) {
                val ire = if (waveformCols.isNotEmpty()) waveformCols[i].coerceIn(0f, 100f) else (50f + 15f * kotlin.math.sin(i.toDouble() * 0.3).toFloat())
                val px = scopeX + (i.toFloat() / (colCount - 1).coerceAtLeast(1)) * scopeW
                val py = scopeY + scopeH * (1f - ire / 100f)
                wavePath.lineTo(px, py)
            }
            drawPath(wavePath, color = CinemaGreen, style = Stroke(width = 1.5f))
        }

        // 4. Luma Histogram Scope (Top right corner, below QUEUE pill)
        if (showHistogram) {
            val histW = minOf(96.dp.toPx(), w * 0.32f)
            val histH = 48.dp.toPx()
            val histX = w - histW - edgePad
            val histY = topSafeY

            drawRect(
                color = Color(0x99000000),
                topLeft = Offset(histX, histY),
                size = Size(histW, histH)
            )

            histPath.reset()
            histPath.moveTo(histX, histY + histH)
            var maxCount = 1
            if (lumaHistogram.isNotEmpty()) {
                for (b in lumaHistogram) {
                    if (b > maxCount) maxCount = b
                }
            }
            val steps = 32
            val binStep = (if (lumaHistogram.isNotEmpty()) lumaHistogram.size else 256) / steps
            for (i in 0 until steps) {
                var binSum = 0
                if (lumaHistogram.isNotEmpty()) {
                    for (k in 0 until binStep) {
                        val idx = i * binStep + k
                        if (idx < lumaHistogram.size) binSum += lumaHistogram[idx]
                    }
                }
                val avgBin = binSum.toFloat() / binStep.coerceAtLeast(1)
                val normalizedHeight = if (lumaHistogram.isNotEmpty()) {
                    (avgBin / maxCount.toFloat()).coerceIn(0.02f, 1f)
                } else {
                    val diff = (i - 12) / 6.0
                    (0.8f * kotlin.math.exp(-diff * diff).toFloat())
                }
                val hx = histX + (i.toFloat() / (steps - 1)) * histW
                val hy = histY + histH - (histH * normalizedHeight)
                histPath.lineTo(hx, hy)
            }
            histPath.lineTo(histX + histW, histY + histH)
            histPath.close()
            drawPath(histPath, color = Color(0x66FFCC00))
            drawPath(histPath, color = AppleYellow, style = Stroke(width = 1.5f))
        }

        // 5. Broadcast RGB Parade Scope (Bottom right mini overlay, cleanly above lens pill)
        if (showRgbParade) {
            val paradeW = minOf(112.dp.toPx(), w * 0.36f)
            val paradeH = 56.dp.toPx()
            val paradeX = w - paradeW - edgePad
            val paradeY = (h - paradeH - bottomSafePad).coerceAtLeast(topSafeY)
            val segW = paradeW / 3f

            drawRect(
                color = Color(0xAA000000),
                topLeft = Offset(paradeX, paradeY),
                size = Size(paradeW, paradeH)
            )
            // Channel dividers & 50% IRE reference
            drawLine(Color(0x33FFFFFF), Offset(paradeX + segW, paradeY), Offset(paradeX + segW, paradeY + paradeH), 1f)
            drawLine(Color(0x33FFFFFF), Offset(paradeX + segW * 2f, paradeY), Offset(paradeX + segW * 2f, paradeY + paradeH), 1f)
            drawLine(Color(0x28FFFFFF), Offset(paradeX, paradeY + paradeH * 0.5f), Offset(paradeX + paradeW, paradeY + paradeH * 0.5f), 1f)

            fun drawChannelParade(samples: FloatArray, offsetX: Float, path: Path, color: Color) {
                val n = if (samples.isNotEmpty()) samples.size else 64
                path.reset()
                for (i in 0 until n) {
                    val ire = if (samples.isNotEmpty()) samples[i].coerceIn(0f, 100f) else 0f
                    val px = offsetX + (i.toFloat() / (n - 1).coerceAtLeast(1)) * (segW - 2f) + 1f
                    val py = paradeY + paradeH * (1f - ire / 100f)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color = color, style = Stroke(width = 1.4f))
            }

            drawChannelParade(paradeR, paradeX, paradePathR, Color(0xFFFF5252))
            drawChannelParade(paradeG, paradeX + segW, paradePathG, Color(0xFF69F0AE))
            drawChannelParade(paradeB, paradeX + segW * 2f, paradePathB, Color(0xFF448AFF))
        }

        // 6. Broadcast Chroma Vectorscope (Top-left polar reticle, below CLEAN VIEW pill)
        if (showVectorscope) {
            val vScopeSize = minOf(80.dp.toPx(), w * 0.28f)
            val vScopeX = edgePad
            val vScopeY = topSafeY
            val vScopeCenterX = vScopeX + vScopeSize / 2f
            val vScopeCenterY = vScopeY + vScopeSize / 2f
            val maxRadius = (vScopeSize / 2f) - 4.dp.toPx()

            drawCircle(
                color = Color(0xCC05080A),
                radius = vScopeSize / 2f,
                center = Offset(vScopeCenterX, vScopeCenterY)
            )
            drawCircle(
                color = Color(0x44FFFFFF),
                radius = maxRadius,
                center = Offset(vScopeCenterX, vScopeCenterY),
                style = Stroke(width = 1f)
            )
            val r75 = maxRadius * 0.75f
            drawCircle(
                color = Color(0x3300E676),
                radius = r75,
                center = Offset(vScopeCenterX, vScopeCenterY),
                style = Stroke(width = 1f)
            )
            drawLine(Color(0x33FFFFFF), Offset(vScopeCenterX - maxRadius, vScopeCenterY), Offset(vScopeCenterX + maxRadius, vScopeCenterY), 1f)
            drawLine(Color(0x33FFFFFF), Offset(vScopeCenterX, vScopeCenterY - maxRadius), Offset(vScopeCenterX, vScopeCenterY + maxRadius), 1f)
            drawCircle(Color(0x66FFFFFF), radius = 2f, center = Offset(vScopeCenterX, vScopeCenterY))

            val targetBoxes = listOf(
                Pair(-0.29f, 0.61f) to Color(0xFFFF5252),
                Pair(0.35f, 0.61f) to Color(0xFFE040FB),
                Pair(0.64f, -0.15f) to Color(0xFF448AFF),
                Pair(0.29f, -0.61f) to Color(0xFF18FFFF),
                Pair(-0.35f, -0.61f) to Color(0xFF69F0AE),
                Pair(-0.64f, 0.15f) to Color(0xFFFFD740)
            )
            for ((coord, color) in targetBoxes) {
                val tx = vScopeCenterX + coord.first * maxRadius
                val ty = vScopeCenterY - coord.second * maxRadius
                drawRect(
                    color = color.copy(alpha = 0.8f),
                    topLeft = Offset(tx - 3f, ty - 3f),
                    size = Size(6f, 6f),
                    style = Stroke(width = 1f)
                )
            }

            val count = vectorscopeCount.coerceIn(0, vectorscopeSamples.size / 2)
            for (i in 0 until count) {
                val u = vectorscopeSamples[i * 2]
                val v = vectorscopeSamples[i * 2 + 1]
                val px = vScopeCenterX + u * maxRadius
                val py = vScopeCenterY - v * maxRadius
                drawCircle(
                    color = CinemaGreen.copy(alpha = 0.7f),
                    radius = 1.5f,
                    center = Offset(px, py)
                )
            }
        }
    }
}
