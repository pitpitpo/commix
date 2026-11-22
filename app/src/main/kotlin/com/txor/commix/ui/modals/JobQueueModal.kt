package com.txor.commix.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.*
import com.txor.commix.ui.components.*
import com.txor.commix.ui.theme.*

/**
 * Background processing job queue modal using unified Cinema design-system primitives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobQueueModal(
    onDismiss: () -> Unit
) {
    val queue = ProcessingJobQueue.instance
    val jobs by queue.jobs.collectAsState()
    val isPaused by queue.isPaused.collectAsState()

    val activeCount = queue.activeJobCount
    val pendingCount = queue.pendingJobCount
    val completedCount = queue.completedJobCount

    CommixModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            CommixSheetHeader(
                title = "Processing Job Queue",
                subtitle = "$activeCount Active · $pendingCount Pending · $completedCount Done",
                icon = Icons.Default.Layers,
                onDismiss = onDismiss
            )

            // Action Buttons Row: Pause/Resume + Clear Done
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isPaused) CinemaGreen.copy(alpha = 0.2f) else CinemaSurface)
                        .border(
                            0.8.dp,
                            if (isPaused) CinemaGreen.copy(alpha = 0.6f) else CinemaGlassBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            if (isPaused) queue.resumeAll() else queue.pauseAll()
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (isPaused) CinemaGreen else CinemaTextLight,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPaused) "Resume Queue" else "Pause Queue",
                            color = if (isPaused) CinemaGreen else CinemaTextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CinemaSurface)
                        .border(0.8.dp, CinemaGlassBorder, RoundedCornerShape(10.dp))
                        .clickable { queue.clearCompleted() }
                        .padding(vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = CinemaTextDim,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clear Done",
                            color = CinemaTextDim,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pipeline Diagnostic Job Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppleYellow.copy(alpha = 0.16f))
                    .border(0.8.dp, AppleYellow.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable {
                        queue.enqueueJob(
                            title = "Pipeline Check: Virtual Gimbal 60fps Stack",
                            type = ProcessingJobType.VIDEO_STABILIZATION,
                            priority = JobPriority.HIGH,
                            executionBlock = { updateProgress ->
                                updateProgress(0.25f, "Parsing Gyroflow IMU log")
                                kotlinx.coroutines.delay(200)
                                updateProgress(0.60f, "Running Kalman attitude filter")
                                kotlinx.coroutines.delay(200)
                                updateProgress(0.90f, "Applying quaternion smoothing")
                                kotlinx.coroutines.delay(200)
                                updateProgress(1.0f, "Completed")
                            }
                        )
                    }
                    .padding(vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = AppleYellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Run Pipeline Diagnostic Job",
                        color = AppleYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (jobs.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = CinemaTextDim.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Jobs in Queue",
                            color = CinemaTextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Background video stabilization, exposure stacking,\nand media exports will be processed here.",
                            color = CinemaTextDim,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp)
                ) {
                    items(jobs, key = { it.id }) { job ->
                        JobItemCard(job = job, onCancel = { queue.cancelJob(job.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun JobItemCard(
    job: ProcessingJob,
    onCancel: () -> Unit
) {
    val statusColor = when (job.status) {
        JobStatus.RUNNING -> AppleYellow
        JobStatus.PENDING -> CinemaBlue
        JobStatus.PAUSED -> CinemaAmber
        JobStatus.COMPLETED -> CinemaGreen
        JobStatus.FAILED -> RecordingRed
        JobStatus.CANCELLED -> CinemaGrey
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CinemaSurface)
            .border(0.8.dp, CinemaGlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CommixBadgePill(
                        text = job.type.shortCode,
                        accentColor = statusColor,
                        monospace = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = job.title,
                        color = CinemaTextLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                CommixBadgePill(
                    text = job.status.displayLabel,
                    accentColor = statusColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = job.currentStage,
                color = CinemaTextDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (job.status.isActive) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { job.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AppleYellow,
                    trackColor = CinemaGlassBorder
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancel",
                        color = RecordingRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onCancel() }
                    )
                }
            }
        }
    }
}
