package com.txor.commix.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun StatusPill(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            visible = true
            delay(2800)
            visible = false
            delay(300)
            onDismiss()
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible && !message.isNullOrBlank(),
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CinemaGlass)
                .border(1.0.dp, AppleYellow.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AppleYellow,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = message ?: "",
                    color = CinemaTextLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.25.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(CinemaSubtleBg)
                        .clickable {
                            visible = false
                            onDismiss()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = CinemaTextDim,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
