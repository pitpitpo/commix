package com.txor.commix.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.txor.commix.ui.theme.AppleYellow
import kotlin.math.roundToInt

@Composable
fun FocusRing(
    x: Float,
    y: Float,
    evStops: Float,
    onEvChanged: (Float) -> Unit,
    animationsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(if (animationsEnabled) 1.35f else 1.0f) }

    LaunchedEffect(x, y, animationsEnabled) {
        if (animationsEnabled) {
            scale.snapTo(1.35f)
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        } else {
            scale.snapTo(1.0f)
        }
    }

    val density = LocalDensity.current
    val halfSizePx = with(density) { 46.dp.roundToPx() }

    val currentOnEvChanged by rememberUpdatedState(onEvChanged)
    var accumulatedEv by remember(x, y) { mutableFloatStateOf(evStops) }

    Box(
        modifier = modifier
            .offset { IntOffset((x - halfSizePx).roundToInt(), (y - halfSizePx).roundToInt()) }
            .size(92.dp)
            .pointerInput(x, y) {
                detectVerticalDragGestures { _, dragAmount ->
                    accumulatedEv = (accumulatedEv - (dragAmount / 50.0f)).coerceIn(-2.0f, 2.0f)
                    currentOnEvChanged(accumulatedEv)
                }
            }
    ) {
        Canvas(modifier = Modifier.size(92.dp)) {
            val radius = 26.dp.toPx() * scale.value
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokePx = 1.6.dp.toPx()

            drawCircle(
                color = AppleYellow,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx)
            )

            val tickLen = 5.5.dp.toPx()
            drawLine(AppleYellow, Offset(center.x - radius, center.y), Offset(center.x - radius + tickLen, center.y), strokePx)
            drawLine(AppleYellow, Offset(center.x + radius, center.y), Offset(center.x + radius - tickLen, center.y), strokePx)
            drawLine(AppleYellow, Offset(center.x, center.y - radius), Offset(center.x, center.y - radius + tickLen), strokePx)
            drawLine(AppleYellow, Offset(center.x, center.y + radius), Offset(center.x, center.y + radius - tickLen), strokePx)

            val sunX = center.x + radius + 12.dp.toPx()
            val sunY = center.y - (evStops / 2.0f).coerceIn(-1f, 1f) * radius

            drawLine(
                color = AppleYellow.copy(alpha = 0.45f),
                start = Offset(sunX, center.y - radius),
                end = Offset(sunX, center.y + radius),
                strokeWidth = 1.2.dp.toPx()
            )

            drawCircle(
                color = AppleYellow,
                radius = 3.8.dp.toPx(),
                center = Offset(sunX, sunY)
            )
        }
    }
}
