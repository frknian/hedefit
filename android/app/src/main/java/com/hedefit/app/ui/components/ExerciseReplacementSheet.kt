package com.hedefit.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.ExerciseReplacementCandidate
import com.hedefit.app.data.model.WorkoutExerciseData
import com.hedefit.app.ui.theme.HedefitColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseReplacementSheet(
    exercise: WorkoutExerciseData,
    candidate: ExerciseReplacementCandidate? = null,
    isBusy: Boolean = false,
    onDismiss: () -> Unit,
    onRequestReplacement: (reason: String, discomfortArea: String?) -> Unit,
    onApplyReplacement: (ExerciseReplacementCandidate) -> Unit,
    locale: String = "tr",
) {
    var selectedReason by remember { mutableStateOf<String?>("too_hard") }
    var selectedPainArea by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(exercise.id) {
        if (candidate == null) {
            onRequestReplacement("too_hard", null)
        }
    }

    val reasons = listOf(
        "too_hard" to if (locale == "en") "Too Hard" else "Çok Zor",
        "cant_do" to if (locale == "en") "Can't Do Form" else "Yapamıyorum",
        "too_easy" to if (locale == "en") "Too Easy" else "Çok Kolay",
        "no_equipment" to if (locale == "en") "No Equipment" else "Ekipmanım Yok",
        "pain_discomfort" to if (locale == "en") "Joint Pain / Discomfort" else "Ağrı / Rahatsızlık",
        "dont_understand" to if (locale == "en") "Don't Understand" else "Hareketi Anlamadım",
        "disliked" to if (locale == "en") "Don't Like This" else "Sevmiyorum",
    )

    val painAreas = listOf(
        "knee" to if (locale == "en") "Knee" else "Diz",
        "shoulder" to if (locale == "en") "Shoulder" else "Omuz",
        "lower_back" to if (locale == "en") "Lower Back" else "Bel",
        "wrist" to if (locale == "en") "Wrist" else "Bilek",
        "hip" to if (locale == "en") "Hip" else "Kalça",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HedefitColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HedefitColors.TextSecondary.copy(alpha = 0.4f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = if (locale == "en") "Replace Exercise" else "Hareketi Değiştir",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = HedefitColors.TextPrimary,
                    )
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HedefitColors.Lime,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = HedefitColors.TextSecondary)
                }
            }

            HorizontalDivider(color = HedefitColors.Divider, thickness = 0.5.dp)

            // Step 1: Why do you want to replace it?
            Text(
                text = if (locale == "en") "Why do you want to change it?" else "Bu hareketi neden değiştirmek istiyorsun?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = HedefitColors.TextPrimary,
            )

            // Reason chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                reasons.forEach { (code, label) ->
                    val isSelected = selectedReason == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) HedefitColors.Lime.copy(alpha = 0.2f) else HedefitColors.SurfaceHigh)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) HedefitColors.Lime else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                selectedReason = code
                                onRequestReplacement(code, selectedPainArea)
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) HedefitColors.Lime else HedefitColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            // If pain/discomfort selected, show pain area selector
            if (selectedReason == "pain_discomfort") {
                Text(
                    text = if (locale == "en") "Where do you feel discomfort?" else "Hangi bölgede rahatsızlık hissediyorsun?",
                    style = MaterialTheme.typography.labelMedium,
                    color = HedefitColors.TextSecondary,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(painAreas) { (code, label) ->
                        val isSelected = selectedPainArea == code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFFFF6B6B).copy(alpha = 0.2f) else HedefitColors.SurfaceHigh)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFFFF6B6B) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    selectedPainArea = code
                                    onRequestReplacement("pain_discomfort", code)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color(0xFFFF6B6B) else HedefitColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Step 2: Proposed Replacement Card
            if (isBusy) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = HedefitColors.Lime, modifier = Modifier.size(32.dp))
                }
            } else if (candidate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = HedefitColors.SurfaceHigh),
                    border = BorderStroke(1.dp, HedefitColors.Lime.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Badge
                        val badgeText = when (candidate.progressionType) {
                            "regression" -> if (locale == "en") "Controlled Regression" else "Kontrollü Regresyon (Daha Kolay)"
                            "progression" -> if (locale == "en") "Higher Progression" else "İleri Varyasyon (Daha Zor)"
                            "equipment_swap" -> if (locale == "en") "Equipment Match" else "Ekipmanına Uyumlu"
                            "safety_swap" -> if (locale == "en") "Joint Friendly" else "Eklem Dostu Güvenli Alternatif"
                            else -> if (locale == "en") "Targeted Alternative" else "Hedef Kas Alternatifi"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.ChangeCircle, contentDescription = null, tint = HedefitColors.Lime, modifier = Modifier.size(20.dp))
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = HedefitColors.Lime,
                            )
                        }

                        // Comparison: Old -> New
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (locale == "en") "Current" else "Mevcut",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HedefitColors.TextSecondary,
                                )
                                Text(
                                    text = candidate.originalExerciseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HedefitColors.TextPrimary.copy(alpha = 0.6f),
                                )
                            }

                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = HedefitColors.Lime,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )

                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (locale == "en") "Proposed" else "Önerilen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HedefitColors.Lime,
                                )
                                Text(
                                    text = candidate.replacementExerciseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = HedefitColors.TextPrimary,
                                )
                            }
                        }

                        // Set / Rep / Rest Details
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HedefitColors.Surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Set", style = MaterialTheme.typography.labelSmall, color = HedefitColors.TextSecondary)
                                Text("${candidate.sets}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (locale == "en") "Reps" else "Tekrar", style = MaterialTheme.typography.labelSmall, color = HedefitColors.TextSecondary)
                                Text(candidate.reps, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (locale == "en") "Rest" else "Dinlenme", style = MaterialTheme.typography.labelSmall, color = HedefitColors.TextSecondary)
                                Text("${candidate.restSeconds} sn", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Explanation
                        Text(
                            text = candidate.explanationTr,
                            style = MaterialTheme.typography.bodySmall,
                            color = HedefitColors.TextSecondary,
                            lineHeight = 18.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                PrimaryButton(
                    text = if (locale == "en") "Replace with This Exercise" else "Bu Hareketle Değiştir",
                    onClick = { onApplyReplacement(candidate) },
                )
            }
        }
    }
}
