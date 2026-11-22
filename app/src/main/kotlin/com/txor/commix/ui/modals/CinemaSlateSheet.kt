package com.txor.commix.ui.modals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txor.commix.ui.components.*
import com.txor.commix.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaSlateSheet(
    roll: String,
    scene: String,
    take: String,
    specLabel: String = "COMMIX PRO CINEMA",
    onUpdateSlate: (roll: String, scene: String, take: String) -> Unit = { _, _, _ -> },
    onPlayTone: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clapperAngle = remember { Animatable(-18f) }

    CommixModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            CommixSheetHeader(
                title = "Cinema Production Slate",
                subtitle = "1kHz audio sync clapper & take counter",
                icon = Icons.Default.MovieFilter,
                badgeText = "R$roll · S$scene · T$take",
                onDismiss = onDismiss
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Clapper Stick
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .rotate(clapperAngle.value)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CinemaTextLight)
                    .border(2.dp, CinemaBlack, RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Slate Board Body
            CommixSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SlateStepperBox(
                        title = "ROLL",
                        value = roll,
                        onDecrement = {
                            val num = roll.filter { it.isDigit() }.toIntOrNull() ?: 1
                            val prefix = roll.filter { !it.isDigit() }.ifEmpty { "A" }
                            val next = (num - 1).coerceAtLeast(1)
                            onUpdateSlate("$prefix${next.toString().padStart(3, '0')}", scene, take)
                        },
                        onIncrement = {
                            val num = roll.filter { it.isDigit() }.toIntOrNull() ?: 1
                            val prefix = roll.filter { !it.isDigit() }.ifEmpty { "A" }
                            val next = num + 1
                            onUpdateSlate("$prefix${next.toString().padStart(3, '0')}", scene, take)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SlateStepperBox(
                        title = "SCENE",
                        value = scene,
                        onDecrement = {
                            val next = ((scene.toIntOrNull() ?: 1) - 1).coerceAtLeast(1)
                            onUpdateSlate(roll, next.toString(), "1")
                        },
                        onIncrement = {
                            val next = (scene.toIntOrNull() ?: 1) + 1
                            onUpdateSlate(roll, next.toString(), "1")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SlateStepperBox(
                        title = "TAKE",
                        value = take,
                        onDecrement = {
                            val next = ((take.toIntOrNull() ?: 1) - 1).coerceAtLeast(1)
                            onUpdateSlate(roll, scene, next.toString())
                        },
                        onIncrement = {
                            val next = (take.toIntOrNull() ?: 1) + 1
                            onUpdateSlate(roll, scene, next.toString())
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = specLabel,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = CinemaTextDim
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            CommixPrimaryButton(
                text = "CLAP SLATE & SYNC AUDIO (1kHz)",
                icon = Icons.Default.VolumeUp,
                onClick = {
                    coroutineScope.launch {
                        onPlayTone()
                        clapperAngle.animateTo(0f, tween(80))
                        clapperAngle.animateTo(-18f, tween(150))
                    }
                },
                testTagName = "clap_slate_button"
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SlateStepperBox(
    title: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CinemaSurfaceElevated)
            .border(0.6.dp, CinemaGlassBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            color = CinemaTextDim,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            fontSize = 19.sp,
            color = CinemaTextLight,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(CinemaSubtleBg)
                    .clickable(onClick = onDecrement)
            ) {
                Text("−", color = CinemaTextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(AppleYellow.copy(alpha = 0.2f))
                    .clickable(onClick = onIncrement)
            ) {
                Text("+", color = AppleYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
