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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.txor.commix.ui.theme.AppleYellow
import com.txor.commix.ui.theme.CinemaCardDark
import com.txor.commix.ui.theme.CinemaTextLight
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
    val clapperAngle = remember { Animatable(-25f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "CINEMA PRODUCTION SLATE",
                color = AppleYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Clapper Stick
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .rotate(clapperAngle.value)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .border(2.dp, Color.Black)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Slate Board Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaCardDark)
                    .border(2.dp, Color(0x33FFFFFF))
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                    Spacer(modifier = Modifier.width(8.dp))
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
                    Spacer(modifier = Modifier.width(8.dp))
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

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = specLabel,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Clapper Action Button (Claps stick, emits sync tone, auto-increments Take)
            Button(
                onClick = {
                    coroutineScope.launch {
                        onPlayTone()
                        clapperAngle.animateTo(0f, tween(80))
                        clapperAngle.animateTo(-25f, tween(150))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppleYellow),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("clap_slate_button")
            ) {
                Text(
                    text = "CLAP SLATE & SYNC AUDIO (1kHz)",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x1AFFFFFF))
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(text = title, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text(text = value, fontSize = 19.sp, color = CinemaTextLight, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x26FFFFFF))
                    .clickable(onClick = onDecrement)
            ) {
                Text("−", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x26FFD60A))
                    .clickable(onClick = onIncrement)
            ) {
                Text("+", color = AppleYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
