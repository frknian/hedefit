package com.hedefit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.CoachActionData
import com.hedefit.app.data.model.WorkoutCoachContext
import com.hedefit.app.ui.state.ChatMessageState
import com.hedefit.app.ui.theme.HedefitColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitCoachContextSheet(
    onDismiss: () -> Unit,
    workoutContext: WorkoutCoachContext? = null,
    messages: List<ChatMessageState>,
    isBusy: Boolean,
    onSendMessage: (String, WorkoutCoachContext?) -> Unit,
    onExecuteAction: (CoachActionData) -> Unit,
    locale: String = "tr",
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val quickQuestions = remember(workoutContext) {
        if (workoutContext?.exerciseName != null) {
            listOf(
                com.hedefit.app.ui.i18n.tr("Bu harekette zorlanıyorum, ne yapabilirim?", "I'm struggling with this exercise, what can I do?"),
                com.hedefit.app.ui.i18n.tr("Bu hareket nereyi çalıştırır?", "What does this exercise work?"),
                com.hedefit.app.ui.i18n.tr("Doğru form ve nefes nasıl olmalı?", "What's the right form and breathing?"),
                com.hedefit.app.ui.i18n.tr("Daha kolay bir alternatifi var mı?", "Is there an easier alternative?"),
                com.hedefit.app.ui.i18n.tr("Dinlenme süresi kaç saniye olmalı?", "How many seconds should I rest?"),
            )
        } else {
            listOf(
                com.hedefit.app.ui.i18n.tr("Bugünkü antrenmanı 20 dakikaya sığdırabilir miyiz?", "Can we fit today's workout into 20 minutes?"),
                com.hedefit.app.ui.i18n.tr("Bugün çok yorgunum, antrenmanı nasıl hafifletebilirim?", "I'm very tired today, how can I lighten the workout?"),
                "Toparlanma durumumu kontrol et",
                com.hedefit.app.ui.i18n.tr("Kaslarımda hafif hamlık var, devam etmeli miyim?", "My muscles are a bit sore, should I continue?"),
            )
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HedefitColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HedefitColors.TextSecondary.copy(alpha = 0.4f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
                .heightIn(min = 400.dp, max = 640.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FitCoachRobotAvatar(Modifier.size(36.dp))
                    Column {
                        Text(
                            text = if (locale == "en") "Fit Coach AI" else "Fit Koç AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = HedefitColors.TextPrimary,
                        )
                        Text(
                            text = if (workoutContext?.exerciseName != null) {
                                "${workoutContext.exerciseName} ${workoutContext.reps?.let { "($it)" } ?: ""}"
                            } else {
                                if (locale == "en") "Context-aware workout assistant" else "Antrenman koçun yanında"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = HedefitColors.Lime,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = HedefitColors.TextSecondary)
                }
            }

            HorizontalDivider(color = HedefitColors.Divider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Quick Question Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                items(quickQuestions) { question ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HedefitColors.SurfaceHigh)
                            .clickable(enabled = !isBusy) {
                                onSendMessage(question, workoutContext)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = question,
                            style = MaterialTheme.typography.labelSmall,
                            color = HedefitColors.TextPrimary,
                        )
                    }
                }
            }

            // Message Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (locale == "en") "Ask Fit Coach anything about your workout, form, or energy!" else "Fit Koç'a hareketin formu, zorluğu veya antrenman süresiyle ilgili aklına geleni sorabilirsin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HedefitColors.TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }

                items(messages) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.user) Alignment.End else Alignment.Start,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (msg.user) 14.dp else 4.dp,
                                        bottomEnd = if (msg.user) 4.dp else 14.dp,
                                    )
                                )
                                .background(if (msg.user) HedefitColors.Lime else HedefitColors.SurfaceHigh)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (msg.user) HedefitColors.OnLime else HedefitColors.TextPrimary,
                                lineHeight = 20.sp,
                            )
                        }

                        // Structured Actions proposed by Coach
                        if (!msg.user && msg.actions.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                msg.actions.forEach { action ->
                                    val actionLabel = when (action.type) {
                                        "replace_exercise" -> "Bu hareketle değiştir: ${action.replacementName ?: "Alternatif"}"
                                        "reduce_intensity" -> "Yoğunluğu %${action.percent ?: 25} hafiflet"
                                        "shorten_workout" -> "Antrenmanı ${action.targetMinutes ?: 20} dakikaya uyarla"
                                        "start_recovery_check" -> "Hazırlık ve toparlanma kontrolü yap"
                                        "modify_sets" -> "Set sayısını ${action.sets ?: 3} yap"
                                        "modify_rest_time" -> "Dinlenmeyi ${action.restSeconds ?: 60}s yap"
                                        else -> "Önerilen eylemi uygula"
                                    }

                                    Button(
                                        onClick = { onExecuteAction(action) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = HedefitColors.Lime.copy(alpha = 0.2f),
                                            contentColor = HedefitColors.Lime,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp),
                                    ) {
                                        Icon(Icons.Default.FlashOn, contentDescription = null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(actionLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isBusy) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(4.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = HedefitColors.Lime,
                            )
                            Text(
                                text = if (locale == "en") "Fit Coach is typing..." else "Fit Koç düşünüyor...",
                                style = MaterialTheme.typography.bodySmall,
                                color = HedefitColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (locale == "en") "Ask a question..." else "Fit Koç'a bir şey sor...",
                            style = MaterialTheme.typography.bodySmall,
                            color = HedefitColors.TextSecondary,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HedefitColors.Lime,
                        unfocusedBorderColor = HedefitColors.Divider,
                        focusedContainerColor = HedefitColors.SurfaceHigh,
                        unfocusedContainerColor = HedefitColors.SurfaceHigh,
                    ),
                    maxLines = 3,
                )

                IconButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotEmpty() && !isBusy) {
                            inputText = ""
                            onSendMessage(text, workoutContext)
                        }
                    },
                    enabled = inputText.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank() && !isBusy) HedefitColors.Lime else HedefitColors.SurfaceHigh),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = com.hedefit.app.ui.i18n.tr("Gönder", "Send"),
                        tint = if (inputText.isNotBlank() && !isBusy) HedefitColors.OnLime else HedefitColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
