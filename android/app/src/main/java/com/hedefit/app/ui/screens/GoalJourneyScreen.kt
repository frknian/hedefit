package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.hedefit.app.ui.components.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.components.Sparkline
import com.hedefit.app.ui.settings.MeasurementUnits
import com.hedefit.app.ui.settings.estimatedGoalWeeks
import com.hedefit.app.ui.theme.HedefitColors
import kotlin.math.abs

@Composable
fun GoalJourneyScreen(data: DashboardData, onBack: () -> Unit, onSetCurrentWeight: (Double) -> Unit, onSetGoalWeight: (Double) -> Unit, language: String = "tr", unitSystem: String = "metric", stepGoal: Int = 10_000, waterGoalMl: Int = 2_500) {
    val en = language == "en"
    val locale = java.util.Locale.forLanguageTag(if (en) "en" else "tr")
    var showCurrentEditor by remember { mutableStateOf(false) }
    var showGoalEditor by remember { mutableStateOf(false) }
    val weights = data.measurements.mapNotNull { m -> m.weightKg?.let { m.date to it } }
    val current = weights.lastOrNull()?.second ?: data.profile.weightKg
    val start = weights.firstOrNull()?.second ?: current
    val target = data.profile.targetWeightKg
    val weeks = estimatedGoalWeeks(current, target)
    val losing = current != null && target != null && target < current
    val gaining = current != null && target != null && target > current
    val heightMeters = data.profile.heightCm?.div(100.0)
    val bmi = if (current != null && heightMeters != null && heightMeters > 0) current / (heightMeters * heightMeters) else null
    val targetBmi = if (target != null && heightMeters != null && heightMeters > 0) target / (heightMeters * heightMeters) else null
    val remaining = if (current != null && target != null) abs(target - current) else null
    val weeklyRate = if (remaining != null && weeks != null && weeks > 0) remaining / weeks else null
    val totalChange = if (start != null && target != null) abs(target - start) else 0.0
    val progress = if (totalChange > .05 && start != null && current != null) (abs(current - start) / totalChange).toFloat().coerceIn(0f, 1f) else 0f
    val finishDate = weeks?.let { java.time.LocalDate.now().plusWeeks(it.toLong()) }
    val dateFormat = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    fun kg(value: Double, digits: Int = 1) = MeasurementUnits.formatWeight(value, unitSystem, digits)

    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { HfScreenHeader(if (en) "My goal journey" else "Hedef yolculuğum", onBack = onBack, backLabel = if (en) "Back" else "Geri") }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            when { losing -> if (en) "WEIGHT LOSS" else "KİLO VERME"; gaining -> if (en) "WEIGHT GAIN" else "KİLO ALMA"; target != null -> if (en) "MAINTAIN" else "KİLOYU KORU"; else -> if (en) "SET YOUR GOAL" else "HEDEFİNİ BELİRLE" },
                            color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            JourneyWeight(if (en) "Now" else "Şimdi", current?.let { kg(it) } ?: "—", Modifier.weight(1f)) { showCurrentEditor = true }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = HedefitColors.Lime)
                            JourneyWeight(if (en) "Target" else "Hedef", target?.let { kg(it) } ?: (if (en) "Set" else "Belirle"), Modifier.weight(1f), end = true) { showGoalEditor = true }
                        }
                        HfProgressBar(progress, height = 10.dp)
                        Text(
                            if (target == null) (if (en) "Tap Target to set your goal weight" else "Hedef kilonu belirlemek için Hedef'e dokun")
                            else if (en) "${(progress * 100).toInt()}% done • ${remaining?.let { kg(it) } ?: "—"} to go" else "%${(progress * 100).toInt()} tamamlandı • ${remaining?.let { kg(it) } ?: "—"} kaldı",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HfStatTile(if (en) "Weekly pace" else "Haftalık tempo", weeklyRate?.let { kg(it, 2) } ?: "—", Modifier.weight(1f), valueColor = HedefitColors.Lime)
                        HfStatTile(if (en) "Duration" else "Süre", weeks?.let { if (en) "$it weeks" else "$it hafta" } ?: "—", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HfStatTile(if (en) "Est. finish" else "Tahmini bitiş", finishDate?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", locale)) ?: "—", Modifier.weight(1f))
                        HfStatTile(if (en) "BMI" else "VKİ", bmi?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f), sub = targetBmi?.let { if (en) "Target %.1f".format(it) else "Hedef %.1f".format(it) })
                    }
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (en) "Weight trend" else "Kilo gidişatı", style = MaterialTheme.typography.titleLarge)
                        val points = weights.takeLast(12).map { it.second.toFloat() }
                        val projection = if (current != null && target != null) List(9) { i -> (current + (target - current) * i / 8.0).toFloat() } else emptyList()
                        if (points.size >= 2) {
                            Text(if (en) "Your measurements" else "Ölçümlerin", color = HedefitColors.Lime, fontWeight = FontWeight.Bold)
                            Sparkline(points, Modifier.fillMaxWidth().height(110.dp), showGrid = true)
                        }
                        if (projection.isNotEmpty()) {
                            Text(if (en) "Planned path" else "Planlanan yol", color = HedefitColors.Water, fontWeight = FontWeight.Bold)
                            Sparkline(projection, Modifier.fillMaxWidth().height(90.dp), showGrid = true)
                        }
                        Text(if (en) "${data.measurements.size} measurements saved" else "${data.measurements.size} ölçüm kayıtlı", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (current != null && target != null && remaining != null && remaining >= .1 && weeks != null) item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (en) "Milestones" else "Ara hedefler", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 6.dp))
                        listOf(.25, .5, .75, 1.0).forEachIndexed { index, share ->
                            val milestoneWeight = (start ?: current) + (target - (start ?: current)) * share
                            val reached = if (losing) current <= milestoneWeight + .05 else current >= milestoneWeight - .05
                            val weeksFromNow = if (reached) 0 else kotlin.math.ceil(abs(milestoneWeight - current) / (weeklyRate ?: 0.5)).toLong()
                            if (index > 0) HfDivider()
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).background(if (reached) HedefitColors.Lime else HedefitColors.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
                                    if (reached) Icon(Icons.Default.Check, null, tint = HedefitColors.OnLime, modifier = Modifier.size(16.dp))
                                    else Text("${(share * 100).toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(kg(milestoneWeight), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                Text(
                                    if (reached) (if (en) "Reached" else "Ulaşıldı") else java.time.LocalDate.now().plusWeeks(weeksFromNow).format(dateFormat),
                                    color = if (reached) HedefitColors.Lime else HedefitColors.TextPrimary, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (en) "How the process works" else "Süreç nasıl ilerleyecek", style = MaterialTheme.typography.titleLarge)
                        journeyPhases(en, losing, gaining, weeks).forEachIndexed { index, (title, body) ->
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(28.dp).background(HedefitColors.Lime.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("${index + 1}", color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(title, fontWeight = FontWeight.ExtraBold)
                                    Text(body, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (en) "Daily targets for this goal" else "Bu hedef için günlük hedeflerin", style = MaterialTheme.typography.titleLarge)
                        GoalDetailRow(if (en) "Calories" else "Kalori", "${data.nutritionGoal.calories} kcal")
                        GoalDetailRow("Protein", "${data.nutritionGoal.protein} g")
                        GoalDetailRow(if (en) "Carbs / Fat" else "Karb / Yağ", "${data.nutritionGoal.carbs} g / ${data.nutritionGoal.fat} g")
                        GoalDetailRow(if (en) "Steps" else "Adım", "%,d".format(stepGoal).replace(',', '.'))
                        GoalDetailRow(if (en) "Water" else "Su", MeasurementUnits.formatWater(waterGoalMl, unitSystem))
                        GoalDetailRow(if (en) "Goal type" else "Hedef türü", data.profile.goal.ifBlank { "—" })
                    }
                }
            }
        }
    }
    if (showCurrentEditor) WeightEditDialog(
        currentValue = current,
        editingTarget = false,
        en = en,
        unitSystem = unitSystem,
        onDismiss = { showCurrentEditor = false },
        onSave = { onSetCurrentWeight(it); showCurrentEditor = false },
    )
    if (showGoalEditor) GoalWeightDialog(
        currentTarget = target,
        en = en,
        unitSystem = unitSystem,
        onDismiss = { showGoalEditor = false },
        onSave = { onSetGoalWeight(it); showGoalEditor = false },
    )
}

private fun journeyPhases(en: Boolean, losing: Boolean, gaining: Boolean, weeks: Int?): List<Pair<String, String>> {
    val total = weeks ?: 12
    return if (en) listOf(
        "Weeks 1–2 • Adaptation" to if (losing) "The scale can drop fast at first, mostly water. Build the routine: log meals, hit protein, walk daily." else "Your body adapts to the new intake and training. Focus on consistency over perfection.",
        "Weeks 3–${(total / 2).coerceAtLeast(4)} • Momentum" to if (gaining) "Aim for the weekly pace with a small calorie surplus and progressive training. Protein at every meal." else "The weekly pace settles. Keep the calorie target, reach your step goal and complete planned workouts.",
        "Every 4 weeks • Check-in" to "Weigh yourself under the same conditions and compare with the planned path. Targets are recalculated from your new weight.",
        "Plateaus are normal" to "1–2 flat weeks happen. Keep going; if it lasts 3 weeks, adjust calories by ~100–150 kcal or add activity.",
        "Final stretch • Maintain" to "Near the goal the pace slows. After reaching it, move to maintenance calories to keep the result.",
    ) else listOf(
        "1–2. hafta • Uyum" to if (losing) "Başta tartı hızlı düşebilir, bu çoğunlukla sudur. Rutini oturt: öğünlerini kaydet, proteini tamamla, her gün yürü." else "Vücudun yeni beslenme ve antrenmana uyum sağlar. Mükemmellik değil süreklilik hedefle.",
        "3–${(total / 2).coerceAtLeast(4)}. hafta • İvme" to if (gaining) "Küçük bir kalori fazlası ve artan antrenman yüküyle haftalık tempoyu yakala. Her öğünde protein olsun." else "Haftalık tempo oturur. Kalori hedefini koru, adım hedefine ulaş ve planlı antrenmanlarını tamamla.",
        "Her 4 haftada • Kontrol" to "Aynı koşullarda tartıl ve planlanan yolla karşılaştır. Hedeflerin yeni kilona göre yeniden hesaplanır.",
        "Duraklama normaldir" to "1–2 hafta sabit kalmak olağandır. Devam et; 3 haftayı geçerse kaloriyi ~100–150 kcal ayarla ya da aktiviteyi artır.",
        "Son dönem • Koruma" to "Hedefe yaklaştıkça tempo yavaşlar. Ulaştıktan sonra sonucu korumak için koruma kalorisine geçersin.",
    )
}

@Composable
private fun JourneyWeight(label: String, value: String, modifier: Modifier, end: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = if (end) Alignment.End else Alignment.Start,
    ) {
        Text(label.uppercase(), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun WeightEditDialog(currentValue: Double?, editingTarget: Boolean, en: Boolean, unitSystem: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember(currentValue, unitSystem) { mutableStateOf(currentValue?.let { "%.1f".format(MeasurementUnits.weightValue(it, unitSystem)).replace(',', '.') }.orEmpty()) }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    val unit = MeasurementUnits.weightUnit(unitSystem)
    val title = if (editingTarget) (if (en) "Target weight" else "Hedef kilo") else if (en) "Current weight" else "Mevcut kilo"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { input -> value = input.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    label = { Text("$title ($unit)") },
                    suffix = { Text(unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = {
            Button(
                enabled = parsed?.let { MeasurementUnits.weightToKg(it, unitSystem) in 30.0..300.0 } == true,
                onClick = { onSave(MeasurementUnits.weightToKg(parsed!!, unitSystem)) },
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) { Text(if (en) "Save" else "Kaydet") }
        },
    )
}

@Composable
private fun GoalDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun GoalWeightDialog(currentTarget: Double?, en: Boolean, unitSystem: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    WeightEditDialog(currentTarget, true, en, unitSystem, onDismiss, onSave)
}
