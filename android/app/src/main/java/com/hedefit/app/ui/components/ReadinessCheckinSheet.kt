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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.DailyReadinessInput
import com.hedefit.app.data.model.ReadinessAdaptationData
import com.hedefit.app.ui.theme.HedefitColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessCheckinSheet(
    onDismiss: () -> Unit,
    onSubmit: (DailyReadinessInput) -> Unit,
    adaptationResult: ReadinessAdaptationData? = null,
    isBusy: Boolean = false,
    onApplyAdaptation: (ReadinessAdaptationData) -> Unit,
    onKeepOriginal: () -> Unit,
    locale: String = "tr",
) {
    var energy by remember { mutableFloatStateOf(8f) }
    var sleepQuality by remember { mutableFloatStateOf(8f) }
    var fatigue by remember { mutableFloatStateOf(3f) }
    var hasSoreness by remember { mutableStateOf(false) }
    val selectedSorenessAreas = remember { mutableStateListOf<String>() }
    var discomfortLevel by remember { mutableFloatStateOf(1f) }

    val muscleOptions = listOf(
        "quadriceps" to "Ön Bacak",
        "hamstrings" to "Arka Bacak",
        "chest" to "Göğüs",
        "back" to "Sırt",
        "shoulders" to "Omuz",
        "biceps" to "Pazu",
        "triceps" to "Arka Kol",
        "core" to "Karın",
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
                        text = if (locale == "en") "Readiness & Recovery" else "Hazırlık ve Toparlanma",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = HedefitColors.TextPrimary,
                    )
                    Text(
                        text = if (locale == "en") "10-second check to adapt today's workout" else "Antrenmanı bugünkü durumuna uyarlamak için 10 saniyelik kontrol",
                        style = MaterialTheme.typography.bodySmall,
                        color = HedefitColors.TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = HedefitColors.TextSecondary)
                }
            }

            HorizontalDivider(color = HedefitColors.Divider, thickness = 0.5.dp)

            if (adaptationResult == null) {
                // Form: Energy
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (locale == "en") "Energy Level" else "Enerji Seviyesi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = HedefitColors.TextPrimary,
                        )
                        Text(
                            text = "${energy.toInt()} / 10",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = HedefitColors.Lime,
                        )
                    }
                    Slider(
                        value = energy,
                        onValueChange = { energy = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = HedefitColors.Lime,
                            activeTrackColor = HedefitColors.Lime,
                            inactiveTrackColor = HedefitColors.SurfaceHigh,
                        ),
                    )
                }

                // Form: Sleep Quality
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (locale == "en") "Sleep Quality" else "Uyku Kalitesi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = HedefitColors.TextPrimary,
                        )
                        Text(
                            text = "${sleepQuality.toInt()} / 10",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = HedefitColors.Lime,
                        )
                    }
                    Slider(
                        value = sleepQuality,
                        onValueChange = { sleepQuality = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = HedefitColors.Lime,
                            activeTrackColor = HedefitColors.Lime,
                            inactiveTrackColor = HedefitColors.SurfaceHigh,
                        ),
                    )
                }

                // Form: Fatigue
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (locale == "en") "Fatigue Level" else "Yorgunluk Seviyesi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = HedefitColors.TextPrimary,
                        )
                        Text(
                            text = "${fatigue.toInt()} / 10",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (fatigue >= 7f) Color(0xFFFF6B6B) else HedefitColors.TextSecondary,
                        )
                    }
                    Slider(
                        value = fatigue,
                        onValueChange = { fatigue = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = if (fatigue >= 7f) Color(0xFFFF6B6B) else HedefitColors.Lime,
                            activeTrackColor = if (fatigue >= 7f) Color(0xFFFF6B6B) else HedefitColors.Lime,
                            inactiveTrackColor = HedefitColors.SurfaceHigh,
                        ),
                    )
                }

                // Form: Muscle Soreness
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (locale == "en") "Muscle Soreness (DOMS)" else "Kas Ağrısı / Hamlık Var mı?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = HedefitColors.TextPrimary,
                        )
                        Switch(
                            checked = hasSoreness,
                            onCheckedChange = {
                                hasSoreness = it
                                if (!it) selectedSorenessAreas.clear()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HedefitColors.OnLime,
                                checkedTrackColor = HedefitColors.Lime,
                            ),
                        )
                    }

                    if (hasSoreness) {
                        Text(
                            text = if (locale == "en") "Select sore muscles:" else "Ağrılı bölgeleri seç:",
                            style = MaterialTheme.typography.labelSmall,
                            color = HedefitColors.TextSecondary,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(muscleOptions) { (key, label) ->
                                val isSelected = selectedSorenessAreas.contains(key)
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
                                            if (isSelected) selectedSorenessAreas.remove(key)
                                            else selectedSorenessAreas.add(key)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    }
                }

                // Form: Joint Discomfort Level
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (locale == "en") "Joint / Body Discomfort" else "Eklem / Bölgesel Rahatsızlık",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = HedefitColors.TextPrimary,
                        )
                        Text(
                            text = "${discomfortLevel.toInt()} / 10",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (discomfortLevel >= 7f) Color(0xFFFF6B6B) else HedefitColors.TextSecondary,
                        )
                    }
                    Slider(
                        value = discomfortLevel,
                        onValueChange = { discomfortLevel = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = if (discomfortLevel >= 7f) Color(0xFFFF6B6B) else HedefitColors.Lime,
                            activeTrackColor = if (discomfortLevel >= 7f) Color(0xFFFF6B6B) else HedefitColors.Lime,
                            inactiveTrackColor = HedefitColors.SurfaceHigh,
                        ),
                    )
                }

                Spacer(Modifier.height(8.dp))

                PrimaryButton(
                    text = if (isBusy) "Değerlendiriliyor..." else if (locale == "en") "Evaluate Readiness" else "Durumu Değerlendir",
                    onClick = {
                        onSubmit(
                            DailyReadinessInput(
                                energy = energy.toInt(),
                                sleepQuality = sleepQuality.toInt(),
                                fatigue = fatigue.toInt(),
                                hasSoreness = hasSoreness,
                                sorenessAreas = selectedSorenessAreas.toList(),
                                discomfortLevel = discomfortLevel.toInt(),
                            )
                        )
                    },
                    enabled = !isBusy,
                )
            } else {
                // Result View: Adaptation Proposal
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = HedefitColors.SurfaceHigh),
                    border = BorderStroke(1.dp, HedefitColors.Lime.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = HedefitColors.Lime)
                            Text(
                                text = if (adaptationResult.recommendedIntensity == "active_recovery") {
                                    if (locale == "en") "Active Recovery Mode" else "Aktif Toparlanma Modu"
                                } else if (adaptationResult.needsAdaptation) {
                                    if (locale == "en") "Adapted Workout (-${adaptationResult.volumeReductionPercent}%)" else "Hafifletilmiş Hacim (-%${adaptationResult.volumeReductionPercent})"
                                } else {
                                    if (locale == "en") "100% Ready for Workout!" else "Antrenmana Tam Hazırsın!"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = HedefitColors.Lime,
                            )
                        }

                        Text(
                            text = if (locale == "en") adaptationResult.explanationEn.ifBlank { adaptationResult.explanationTr } else adaptationResult.explanationTr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HedefitColors.TextPrimary,
                            lineHeight = 20.sp,
                        )

                        if (adaptationResult.delodedMuscles.isNotEmpty()) {
                            Text(
                                text = (if (locale == "en") "Deloaded muscles: " else "Hafifletilen kaslar: ") + adaptationResult.delodedMuscles.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = HedefitColors.TextSecondary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                PrimaryButton(
                    text = if (locale == "en") "Apply Adapted Workout" else "Düzenlenmiş Programı Uygula",
                    onClick = { onApplyAdaptation(adaptationResult) },
                )

                SecondaryButton(
                    text = if (locale == "en") "Continue with Original Plan" else "Orijinal Programla Devam Et",
                    onClick = onKeepOriginal,
                )
            }
        }
    }
}
