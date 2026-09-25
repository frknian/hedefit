package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.LabeledValue
import com.hedefit.app.ui.components.MetricCard
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.components.SectionTitle
import com.hedefit.app.ui.components.Sparkline
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.ui.components.*
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import com.hedefit.app.ui.settings.MeasurementUnits
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.data.model.BodyMeasurementData
import com.hedefit.app.data.model.WorkoutSessionData
import com.hedefit.app.data.model.RouteActivityData
import com.hedefit.app.data.model.WorkoutExercisePerformanceData
import com.hedefit.app.data.model.manualActivityTypes
import com.hedefit.app.route.formatDuration
import com.hedefit.app.gym.compactKg
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun ProgressScreen(
    padding: PaddingValues,
    expanded: Boolean,
    data: DashboardData?,
    language: String = "tr",
    unitSystem: String = "metric",
    weeklyWorkoutGoal: Int = 3,
    onWeeklyWorkoutGoalChange: (Int) -> Unit = {},
    measurementSaving: Boolean = false,
    onSaveMeasurement: (BodyMeasurementData) -> Unit = {},
    onDeleteRoute: (RouteActivityData) -> Unit = {},
    nutritionHistory: List<com.hedefit.app.data.model.NutritionLogData> = emptyList(),
    onLoadNutritionHistory: () -> Unit = {},
) {
    val en = language == "en"
    LaunchedEffect(Unit) { onLoadNutritionHistory() }
    var range by remember { mutableStateOf("30G") }
    var showGoalEditor by remember { mutableStateOf(false) }
    var showMeasurementEditor by remember { mutableStateOf(false) }
    var showWeeklyReview by remember { mutableStateOf(false) }
    val filteredData = filterProgressData(data, range)
    ScreenContainer(padding) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            item {
                HfScreenHeader(if (en) "Progress" else "İlerleme") {
                    HfCircleButton(Icons.Default.MonitorWeight, if (en) "Add measurement" else "Ölçüm ekle", { showMeasurementEditor = true })
                }
            }
            item { TimeRangeSelector(range, en) { range = it } }
            item { ProgressHero(filteredData, data?.sessions.orEmpty(), weeklyWorkoutGoal, en) { showGoalEditor = true } }
            item { ActivityHeatmap(data, range, en) }
            item { WeeklyMinutesChart(data, en) }
            item { WeeklyCalorieBalance(data, nutritionHistory, en) }
            item { WeightChart(range, filteredData, en, unitSystem) }
            item { ExercisePerformanceHistory(filteredData?.exercisePerformance.orEmpty(), en) }
            item { WorkoutHistory(filteredData, en) }
            item { RouteHistory(filteredData?.routeActivities.orEmpty(), en, unitSystem, onDeleteRoute) }
            item { BodyMeasurements(filteredData, en, unitSystem) { showMeasurementEditor = true } }
            item { WeeklyReviewCard(filteredData, en) { showWeeklyReview = true } }
        }
    }
    if (showGoalEditor) WeeklyGoalDialog(weeklyWorkoutGoal, en, { showGoalEditor = false }) { onWeeklyWorkoutGoalChange(it); showGoalEditor = false }
    if (showMeasurementEditor) BodyMeasurementDialog(
        latest = data?.measurements?.lastOrNull(),
        profileWeightKg = data?.profile?.weightKg,
        en = en,
        unitSystem = unitSystem,
        saving = measurementSaving,
        onDismiss = { if (!measurementSaving) showMeasurementEditor = false },
        onSave = { onSaveMeasurement(it); showMeasurementEditor = false },
    )
    if (showWeeklyReview) WeeklyReviewDialog(filteredData, en) { showWeeklyReview = false }
}

private fun filterProgressData(data: DashboardData?, range: String): DashboardData? {
    data ?: return null
    val days = when (range) { "7G" -> 7L; "30G" -> 30L; "90G" -> 90L; "1Y" -> 365L; else -> null }
    val cutoff = days?.let { LocalDate.now().minusDays(it - 1) } ?: return data
    return data.copy(
        measurements = data.measurements.filter { runCatching { LocalDate.parse(it.date.take(10)) >= cutoff }.getOrDefault(false) },
        sessions = data.sessions.filter { sessionDate(it) >= cutoff },
        exercisePerformance = data.exercisePerformance.filter { performanceDate(it) >= cutoff },
        routeActivities = data.routeActivities.filter { routeDate(it) >= cutoff },
    )
}

private val progressRanges = listOf("7G", "30G", "90G", "1Y", "Tümü")

private fun rangeLabel(range: String, en: Boolean) = when (range) {
    "7G" -> if (en) "Last 7 days" else "Son 7 gün"
    "30G" -> if (en) "Last 30 days" else "Son 30 gün"
    "90G" -> if (en) "Last 90 days" else "Son 90 gün"
    "1Y" -> if (en) "Last year" else "Son 1 yıl"
    else -> if (en) "All time" else "Tüm zamanlar"
}

@Composable
private fun TimeRangeSelector(selected: String, en: Boolean, onSelect: (String) -> Unit) {
    val labels = if (en) listOf("7D", "30D", "90D", "1Y", "All") else listOf("7G", "30G", "90G", "1Y", "Tümü")
    HfSegmented(labels, progressRanges.indexOf(selected).coerceAtLeast(0), { onSelect(progressRanges[it]) })
}

@Composable
private fun WeightChart(range: String, data: DashboardData?, en: Boolean, unitSystem: String) {
    val weights = data?.measurements?.mapNotNull { it.weightKg?.toFloat() }.orEmpty()
    val current = weights.lastOrNull() ?: data?.profile?.weightKg?.toFloat()
    val change = if (weights.size >= 2) current!! - weights.first() else null
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(if (en) "BODY WEIGHT" else "VÜCUT AĞIRLIĞI", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(current?.let { "%.1f".format(MeasurementUnits.weightValue(it.toDouble(), unitSystem)) } ?: "—", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text(MeasurementUnits.weightUnit(unitSystem), modifier = Modifier.padding(bottom = 5.dp), color = HedefitColors.TextMuted)
                    }
                }
                if (change != null) HfPill("%+.1f %s".format(MeasurementUnits.weightValue(change.toDouble(), unitSystem), MeasurementUnits.weightUnit(unitSystem)), if (change <= 0) HedefitColors.Lime else HedefitColors.Warning)
            }
            Sparkline(
                if (weights.size >= 2) weights.map { MeasurementUnits.weightValue(it.toDouble(), unitSystem).toFloat() } else listOf(0f, 0f),
                Modifier.fillMaxWidth().height(if (range == "7G") 80.dp else 110.dp),
                showGrid = true,
            )
            val dates = data?.measurements.orEmpty().map { it.date.take(10) }
            if (dates.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(dates.first(), dates[dates.lastIndex / 2], dates.last()).forEach { Text(formatDate(it, en), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ProgressHero(data: DashboardData?, allSessions: List<WorkoutSessionData>, weeklyGoal: Int, en: Boolean, onEditGoal: () -> Unit) {
    val sessions = data?.sessions.orEmpty()
    val totalMinutes = sessions.sumOf { it.durationSeconds } / 60
    val totalCalories = sessions.sumOf { it.calories }
    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeklyDone = allSessions.map(::sessionDate).filter { it >= weekStart }.distinct().size
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${sessions.size}", fontSize = 44.sp, fontWeight = FontWeight.Black, color = HedefitColors.Lime)
                    Text(if (en) "workouts" else "antrenman", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                ProgressRing((weeklyDone / weeklyGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f), Modifier.size(86.dp).clickable(onClick = onEditGoal), 9.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$weeklyDone/$weeklyGoal", fontWeight = FontWeight.ExtraBold)
                        Text(if (en) "this week" else "bu hafta", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroMetric(if (en) "Time" else "Süre", "${totalMinutes / 60}s ${totalMinutes % 60}dk", HedefitColors.Water, Modifier.weight(1f))
                HeroMetric("kcal", "$totalCalories", HedefitColors.Warning, Modifier.weight(1f))
                HeroMetric(if (en) "Streak" else "Seri", "${data?.streakDays ?: 0} ${if (en) "d" else "gün"}", HedefitColors.Coral, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(color.copy(alpha = .12f), RoundedCornerShape(14.dp)).padding(vertical = 10.dp, horizontal = 10.dp)) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActivityHeatmap(data: DashboardData?, range: String, en: Boolean) {
    val today = LocalDate.now()
    val activityDates = data?.sessions.orEmpty().map(::sessionDate) + data?.routeActivities.orEmpty().map(::routeDate)
    val days = when (range) {
        "7G" -> 7L
        "30G" -> 30L
        "90G" -> 90L
        "1Y" -> 365L
        else -> activityDates.filter { it != LocalDate.MIN }.minOrNull()?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) + 1 }?.coerceAtLeast(7L) ?: 30L
    }
    val cutoff = today.minusDays(days - 1)
    val start = cutoff.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = (java.time.temporal.ChronoUnit.DAYS.between(start, today) / 7 + 1).toInt()
    val counts = activityDates.filter { it >= cutoff }.groupingBy { it }.eachCount()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HfSectionHeader(if (en) "Activity • ${rangeLabel(range, true).lowercase()}" else "Aktivite • ${rangeLabel(range, false).lowercase()}")
        HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (range == "7G") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0L..6L).map(cutoff::plusDays).forEach { date ->
                        val count = counts[date] ?: 0
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(10.dp)).background(if (count >= 2) HedefitColors.Lime else if (count == 1) HedefitColors.Lime.copy(alpha = .55f) else HedefitColors.SurfaceSoft),
                                contentAlignment = Alignment.Center,
                            ) { Text("${date.dayOfMonth}", fontWeight = FontWeight.ExtraBold, color = if (count > 0) HedefitColors.OnLime else HedefitColors.TextPrimary) }
                            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.forLanguageTag(if (en) "en" else "tr")).take(3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (range == "30G") Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            Text(day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.forLanguageTag(if (en) "en" else "tr")).take(3), modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    (0 until weeks).forEach { w ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            (0 until 7).forEach { d ->
                                val date = start.plusDays(w * 7L + d)
                                val count = counts[date] ?: 0
                                val inRange = !date.isAfter(today) && !date.isBefore(cutoff)
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(
                                        when {
                                            !inRange -> Color.Transparent
                                            count >= 2 -> HedefitColors.Lime
                                            count == 1 -> HedefitColors.Lime.copy(alpha = .55f)
                                            else -> HedefitColors.SurfaceSoft
                                        },
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (inRange) Text("${date.dayOfMonth}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (count > 0) HedefitColors.OnLime else HedefitColors.TextSecondary)
                                }
                            }
                        }
                    }
                } else if (range == "1Y" || range == "Tümü") Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0 until weeks).forEach { w ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            (0 until 7).forEach { d ->
                                val date = start.plusDays(w * 7L + d)
                                val count = counts[date] ?: 0
                                Box(
                                    Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(
                                        when {
                                            date.isAfter(today) || date.isBefore(cutoff) -> Color.Transparent
                                            count >= 2 -> HedefitColors.Lime
                                            count == 1 -> HedefitColors.Lime.copy(alpha = .55f)
                                            else -> HedefitColors.SurfaceSoft
                                        },
                                    ),
                                )
                            }
                        }
                    }
                } else Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0 until weeks).forEach { w ->
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            (0 until 7).forEach { d ->
                                val date = start.plusDays(w * 7L + d)
                                val count = counts[date] ?: 0
                                Box(
                                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(3.dp)).background(
                                        when {
                                            date.isAfter(today) || date.isBefore(cutoff) -> Color.Transparent
                                            count >= 2 -> HedefitColors.Lime
                                            count == 1 -> HedefitColors.Lime.copy(alpha = .55f)
                                            else -> HedefitColors.SurfaceSoft
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                Text(if (en) "${counts.size} of $days days active" else "Aktif gün: ${counts.size} / $days", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WeeklyMinutesChart(data: DashboardData?, en: Boolean) {
    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = (7 downTo 0).map { weekStart.minusWeeks(it.toLong()) }
    val minutes = weeks.map { start ->
        val end = start.plusDays(7)
        data?.sessions.orEmpty().filter { sessionDate(it).let { day -> day >= start && day < end } }.sumOf { it.durationSeconds } / 60 +
            data?.routeActivities.orEmpty().filter { routeDate(it).let { day -> day >= start && day < end } }.sumOf { it.durationSeconds } / 60
    }
    val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HfSectionHeader(if (en) "Weekly training time" else "Haftalık antrenman süresi")
        HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                weeks.forEachIndexed { index, start ->
                    val value = minutes[index]
                    val current = index == weeks.lastIndex
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (value > 0) Text("$value", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (current) HedefitColors.Lime else HedefitColors.TextPrimary)
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((value / max.toFloat()).coerceAtLeast(.03f) * .72f)
                                .clip(RoundedCornerShape(6.dp)).background(if (current) HedefitColors.Lime else HedefitColors.Lime.copy(alpha = .35f)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${start.dayOfMonth}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Estimated daily burn: Mifflin-St Jeor BMR × 1.2 (daily life) + logged workouts/routes + steps. */
private fun estimatedBurn(data: DashboardData, date: LocalDate): Int {
    val profile = data.profile
    val weight = data.measurements.lastOrNull()?.weightKg ?: profile.weightKg
    val bmr = if (weight != null && profile.heightCm != null && profile.age != null) {
        10 * weight + 6.25 * profile.heightCm - 5 * profile.age + if (profile.gender.contains("kad", ignoreCase = true) || profile.gender.contains("female", ignoreCase = true)) -161 else 5
    } else null
    val base = bmr?.let { (it * 1.2).toInt() } ?: data.nutritionGoal.calories
    val workouts = data.sessions.filter { sessionDate(it) == date }.sumOf { it.calories }
    val routes = data.routeActivities.filter { routeDate(it) == date }.sumOf { it.calories }
    val steps = data.stepHistory.firstOrNull { it.localDate == date }?.steps ?: 0
    return base + workouts + routes + (steps * 0.04).toInt()
}

@Composable
private fun WeeklyCalorieBalance(data: DashboardData?, logs: List<com.hedefit.app.data.model.NutritionLogData>, en: Boolean) {
    data ?: return
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val intakeByDay = logs.groupBy { runCatching { LocalDate.parse(it.date.take(10)) }.getOrDefault(LocalDate.MIN) }.mapValues { (_, dayLogs) -> dayLogs.sumOf { it.calories } }
    data class Week(val start: LocalDate, val intake: Int, val burned: Int, val loggedDays: Int)
    val weeks = (7 downTo 0).map { weekStart.minusWeeks(it.toLong()) }.map { start ->
        val loggedDays = (0L..6L).map(start::plusDays).filter { !it.isAfter(today) && (intakeByDay[it] ?: 0) > 0 }
        Week(start, loggedDays.sumOf { intakeByDay[it] ?: 0 }, loggedDays.sumOf { estimatedBurn(data, it) }, loggedDays.size)
    }
    val max = weeks.maxOf { maxOf(it.intake, it.burned) }.coerceAtLeast(1)
    val current = weeks.last()
    val balance = current.intake - current.burned
    fun compact(value: Int) = if (kotlin.math.abs(value) >= 1000) "%+.1fk".format(value / 1000.0).replace('.', ',') else "%+d".format(value)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HfSectionHeader(if (en) "Weekly calorie balance" else "Haftalık kalori dengesi")
        HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (current.loggedDays == 0) "—" else if (balance <= 0) (if (en) "Deficit ${"%,d".format(-balance)} kcal" else "Açık ${"%,d".format(-balance).replace(',', '.')} kcal") else if (en) "Surplus ${"%,d".format(balance)} kcal" else "Fazla ${"%,d".format(balance).replace(',', '.')} kcal",
                            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (balance <= 0) HedefitColors.Lime else HedefitColors.Warning,
                        )
                        Text(if (en) "This week • ${current.loggedDays} logged days" else "Bu hafta • ${current.loggedDays} kayıtlı gün", fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(10.dp).background(HedefitColors.Lime, CircleShape)); Text(if (en) "Eaten" else "Alınan", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(10.dp).background(HedefitColors.Coral, CircleShape)); Text(if (en) "Burned (est.)" else "Yakılan (tahmini)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                }
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    weeks.forEach { week ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            if (week.loggedDays > 0) Text(compact(week.intake - week.burned), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (week.intake <= week.burned) HedefitColors.Lime else HedefitColors.Warning, maxLines = 1)
                            Row(Modifier.fillMaxWidth().fillMaxHeight(.78f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                                Box(Modifier.weight(1f).fillMaxHeight((week.intake / max.toFloat()).coerceAtLeast(.02f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(HedefitColors.Lime))
                                Box(Modifier.weight(1f).fillMaxHeight((week.burned / max.toFloat()).coerceAtLeast(.02f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(HedefitColors.Coral))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${week.start.dayOfMonth}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyGoalDialog(current: Int, en: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember(current) { mutableStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Weekly workout target" else "Haftalık antrenman hedefi") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (en) "${value.toInt()} completed workouts per week" else "Haftada ${value.toInt()} tamamlanmış antrenman", style = MaterialTheme.typography.titleLarge, color = HedefitColors.Lime)
            Text(if (en) "Choose how many separate workouts you realistically want to complete in one week." else "Bir hafta içinde gerçekçi olarak tamamlamak istediğin ayrı antrenman sayısını seç.", color = HedefitColors.TextSecondary)
            Slider(value = value, onValueChange = { value = it }, valueRange = 1f..7f, steps = 5)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = { Button(onClick = { onSave(value.toInt()) }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "Save" else "Kaydet") } },
    )
}

@Composable
private fun WorkoutHistory(data: DashboardData?, en: Boolean) {
    val sessions = data?.sessions.orEmpty().take(5)
    var selectedSession by remember { mutableStateOf<WorkoutSessionData?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HfSectionHeader(if (en) "Recent workouts" else "Son antrenmanlar")
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sessions.isEmpty()) Text(if (en) "Completed workouts will appear here." else "Tamamladığın antrenmanlar burada tarihleriyle görünecek.", color = HedefitColors.TextSecondary)
            sessions.forEach { session ->
                val manualActivity = com.hedefit.app.data.model.activityTypeFor(session.manualActivityKey)
                val sessionPerformance = data?.exercisePerformance.orEmpty().filter { it.sessionId == session.id }
                val sessionSets = sessionPerformance.flatMap { it.sets }
                val sessionVolume = sessionSets.sumOf { set -> com.hedefit.app.gym.setVolume(set.weightKg, set.reps) }
                Row(Modifier.fillMaxWidth().clickable(enabled = manualActivity == null) { selectedSession = session }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(manualActivity?.let { "${it.emoji} ${if (en) it.titleEn else it.titleTr}" } ?: formatDate(session.completedAt.take(10), en), style = MaterialTheme.typography.titleMedium)
                        Text(if (manualActivity != null) formatDate(session.completedAt.take(10), en) else if (sessionSets.isNotEmpty()) "${sessionSets.size} set • ${sessionVolume.compactKg()}" else if (en) "${session.completedExercises}/${session.totalExercises} movements completed" else "${session.completedExercises}/${session.totalExercises} hareket tamamlandı", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${session.durationSeconds / 60} ${if (en) "min" else "dk"} • ${session.calories} kcal", color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    }
    selectedSession?.let { session ->
        val performances = data?.exercisePerformance.orEmpty().filter { it.sessionId == session.id }
        AlertDialog(
            onDismissRequest = { selectedSession = null },
            title = { Text(if (en) "Workout details" else "Antrenman Detayı") },
            text = { LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (performances.isEmpty()) item { Text(if (en) "Detailed set data is not available for this older workout." else "Bu eski antrenman için ayrıntılı set verisi bulunmuyor.", color = HedefitColors.TextSecondary) }
                items(performances, key = { it.exerciseId ?: it.exerciseName }) { performance ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(performance.exerciseName, style = MaterialTheme.typography.titleMedium)
                        performance.sets.forEach { set -> Text("Set ${set.setNumber}  •  ${set.weightKg?.let { "${it.toInt()} kg" } ?: if (en) "Bodyweight" else "Vücut ağırlığı"} × ${set.reps ?: "—"}", color = HedefitColors.TextSecondary) }
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { selectedSession = null }) { Text(if (en) "Close" else "Kapat") } },
        )
    }
}

@Composable
private fun ExercisePerformanceHistory(performances: List<WorkoutExercisePerformanceData>, en: Boolean) {
    val latestByExercise = performances.sortedByDescending { it.completedAt }.distinctBy { it.exerciseId ?: it.exerciseName }.take(6)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HfSectionHeader(if (en) "Personal bests" else "Kişisel rekorlar")
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (latestByExercise.isEmpty()) Text(if (en) "Complete a movement from your plan or the exercise atlas to see its performance here." else "Programdan veya Hareket Atlası'ndan bir hareketi tamamla; set performansın burada görünür.", color = HedefitColors.TextSecondary)
            latestByExercise.forEach { performance ->
                val best = performance.sets.maxWithOrNull(compareBy<com.hedefit.app.data.model.WorkoutSetPerformanceData> { it.weightKg ?: 0.0 }.thenBy { it.reps ?: 0 })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(performance.exerciseName, style = MaterialTheme.typography.titleMedium)
                        Text(formatDate(performance.completedAt.take(10), en), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        best?.let { "${it.weightKg?.let { weight -> "${weight.toInt()} kg" } ?: if (en) "Bodyweight" else "Vücut ağırlığı"} × ${it.reps ?: "—"}" } ?: "—",
                        color = HedefitColors.Lime,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun RouteHistory(routes: List<RouteActivityData>, en: Boolean, unitSystem: String, onDeleteRoute: (RouteActivityData) -> Unit) {
    var selectedRoute by remember { mutableStateOf<RouteActivityData?>(null) }
    var deleteCandidate by remember { mutableStateOf<RouteActivityData?>(null) }
    val totalDistance = routes.sumOf { it.distanceMeters }
    val totalDuration = routes.sumOf { it.durationSeconds }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HfSectionHeader(if (en) "Hedefit Route history" else "Hedefit Rota geçmişi")
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (routes.isEmpty()) {
                Text(if (en) "Your completed GPS routes will appear here." else "Tamamladığın GPS rotaları burada görünecek.", color = HedefitColors.TextSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(Icons.Default.Route, MeasurementUnits.formatDistance(totalDistance, unitSystem), if (en) "Total distance" else "Toplam mesafe", Modifier.weight(1f))
                    MetricCard(Icons.Default.Timer, formatDuration(totalDuration), if (en) "Route time" else "Rota süresi", Modifier.weight(1f))
                }
                routes.take(5).forEach { route ->
                    val pace = if (route.distanceMeters >= 50) route.durationSeconds / (route.distanceMeters / 1000.0) else null
                    Row(Modifier.fillMaxWidth().clickable { selectedRoute = route }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Route, null, tint = HedefitColors.Lime, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(route.title.ifBlank { routeActivityLabel(route.activityType, en) }, style = MaterialTheme.typography.titleMedium)
                            Text(formatDate(route.startedAt.take(10), en), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(MeasurementUnits.formatDistance(route.distanceMeters, unitSystem), color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge)
                            Text("${formatDuration(route.durationSeconds)} • ${MeasurementUnits.formatPace(pace?.toInt(), unitSystem)}", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    }
    selectedRoute?.let { route -> AlertDialog(
        onDismissRequest = { selectedRoute = null },
        title = { Text(route.title.ifBlank { routeActivityLabel(route.activityType, en) }) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RoutePolylinePreview(route, Modifier.fillMaxWidth().height(210.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue(if (en) "Distance" else "Mesafe", MeasurementUnits.formatDistance(route.distanceMeters, unitSystem), accent = HedefitColors.Lime)
                LabeledValue(if (en) "Time" else "Süre", formatDuration(route.movingDurationSeconds))
                LabeledValue(if (en) "Avg pace" else "Ort. Tempo", MeasurementUnits.formatPace(route.averagePaceSecondsPerKm, unitSystem))
            }
            Text("${if (en) "Elapsed" else "Toplam süre"}: ${formatDuration(route.durationSeconds)}  •  ${route.calories} kcal", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        } },
        dismissButton = { TextButton(onClick = { selectedRoute = null; deleteCandidate = route }) { Text(if (en) "Delete record" else "Kaydı sil", color = HedefitColors.Coral) } },
        confirmButton = { TextButton(onClick = { selectedRoute = null }) { Text(if (en) "Done" else "Tamam") } },
    ) }
    deleteCandidate?.let { route -> AlertDialog(
        onDismissRequest = { deleteCandidate = null },
        title = { Text(if (en) "Delete this route?" else "Bu rota silinsin mi?") },
        text = { Text(if (en) "The GPS track, distance and activity record will be permanently deleted." else "GPS izi, mesafe ve aktivite kaydı kalıcı olarak silinecek.") },
        dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = { Button(onClick = { onDeleteRoute(route); deleteCandidate = null; selectedRoute = null }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Coral, contentColor = androidx.compose.ui.graphics.Color.White)) { Text(if (en) "Delete" else "Sil") } },
    ) }
}

@Composable
private fun RoutePolylinePreview(route: RouteActivityData, modifier: Modifier) {
    val points = route.routePoints
    Box(modifier.background(HedefitColors.SurfaceHigh, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        if (points.size < 2) Text(com.hedefit.app.ui.i18n.tr("Rota önizlemesi bulunmuyor", "No route preview"), color = HedefitColors.TextSecondary)
        else Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
            val minLng = points.minOf { it.longitude }; val maxLng = points.maxOf { it.longitude }
            val latRange = (maxLat - minLat).coerceAtLeast(.000001); val lngRange = (maxLng - minLng).coerceAtLeast(.000001)
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = ((point.longitude - minLng) / lngRange * size.width).toFloat()
                val y = (size.height - (point.latitude - minLat) / latRange * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, HedefitColors.Lime, style = Stroke(width = 8f, cap = StrokeCap.Round))
            val first = points.first(); val last = points.last()
            fun offset(point: com.hedefit.app.data.model.ActivityRoutePointData) = Offset(((point.longitude - minLng) / lngRange * size.width).toFloat(), (size.height - (point.latitude - minLat) / latRange * size.height).toFloat())
            drawCircle(androidx.compose.ui.graphics.Color.White, 9f, offset(first)); drawCircle(HedefitColors.Lime, 11f, offset(last))
        }
    }
}

private fun routeActivityLabel(type: String, en: Boolean) = when (type.lowercase()) {
    "run", "running", "koşu" -> if (en) "Run" else "Koşu"
    "ride", "cycling", "bisiklet" -> if (en) "Ride" else "Bisiklet"
    else -> if (en) "Walk" else "Yürüyüş"
}

@Composable
private fun BodyMeasurements(data: DashboardData?, en: Boolean, unitSystem: String, onClick: () -> Unit) {
    val latest = data?.measurements?.lastOrNull()
    val first = data?.measurements?.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HfSectionHeader(if (en) "Body measurements" else "Vücut ölçüleri", if (en) "Add / Edit" else "Ekle / Düzenle", onClick)
    HedefitCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue(if (en) "Waist" else "Bel", latest?.waistCm?.let { MeasurementUnits.formatLength(it, unitSystem) } ?: "—", accent = HedefitColors.TextPrimary)
                LabeledValue(if (en) "Chest" else "Göğüs", latest?.chestCm?.let { MeasurementUnits.formatLength(it, unitSystem) } ?: "—")
                LabeledValue(if (en) "Arm" else "Kol", latest?.armCm?.let { MeasurementUnits.formatLength(it, unitSystem) } ?: "—")
                LabeledValue(if (en) "Leg" else "Bacak", latest?.thighCm?.let { MeasurementUnits.formatLength(it, unitSystem) } ?: "—")
            }
            if (first != null && latest != null && first != latest) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(measurementDelta(first.waistCm, latest.waistCm, unitSystem), measurementDelta(first.chestCm, latest.chestCm, unitSystem), measurementDelta(first.armCm, latest.armCm, unitSystem), measurementDelta(first.thighCm, latest.thighCm, unitSystem)).forEach { delta -> Text(delta ?: "—", color = if (delta != null) HedefitColors.Lime else HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    }
}

@Composable
private fun BodyMeasurementDialog(
    latest: BodyMeasurementData?,
    profileWeightKg: Double?,
    en: Boolean,
    unitSystem: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (BodyMeasurementData) -> Unit,
) {
    fun initial(value: Double?) = value?.let { "%.1f".format(MeasurementUnits.heightValue(it, unitSystem)) }.orEmpty()
    var weight by remember(latest, unitSystem) { mutableStateOf(latest?.weightKg?.let { "%.1f".format(MeasurementUnits.weightValue(it, unitSystem)) } ?: profileWeightKg?.let { "%.1f".format(MeasurementUnits.weightValue(it, unitSystem)) }.orEmpty()) }
    var waist by remember(latest, unitSystem) { mutableStateOf(initial(latest?.waistCm)) }
    var hips by remember(latest, unitSystem) { mutableStateOf(initial(latest?.hipsCm)) }
    var chest by remember(latest, unitSystem) { mutableStateOf(initial(latest?.chestCm)) }
    var arm by remember(latest, unitSystem) { mutableStateOf(initial(latest?.armCm)) }
    var thigh by remember(latest, unitSystem) { mutableStateOf(initial(latest?.thighCm)) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    fun parsed(raw: String) = raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    fun lengthCm(raw: String) = parsed(raw)?.let { MeasurementUnits.heightToCm(it, unitSystem) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Update body measurements" else "Vücut ölçülerini güncelle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (en) "Today's record is updated. Leave a field blank if you do not want to track it."
                    else "Bugünün kaydı güncellenir. Takip etmek istemediğin alanı boş bırakabilirsin.",
                    color = HedefitColors.TextSecondary,
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it; validationError = null },
                    label = { Text(if (en) "Weight (${MeasurementUnits.weightUnit(unitSystem)})" else "Kilo (${MeasurementUnits.weightUnit(unitSystem)})") },
                    singleLine = true,
                    keyboardOptions = keyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                MeasurementInputRow(
                    firstValue = waist, onFirstChange = { waist = it; validationError = null }, firstLabel = if (en) "Waist" else "Bel",
                    secondValue = hips, onSecondChange = { hips = it; validationError = null }, secondLabel = if (en) "Hips" else "Kalça",
                    unit = MeasurementUnits.heightUnit(unitSystem), keyboard = keyboard,
                )
                MeasurementInputRow(
                    firstValue = chest, onFirstChange = { chest = it; validationError = null }, firstLabel = if (en) "Chest" else "Göğüs",
                    secondValue = arm, onSecondChange = { arm = it; validationError = null }, secondLabel = if (en) "Arm" else "Kol",
                    unit = MeasurementUnits.heightUnit(unitSystem), keyboard = keyboard,
                )
                OutlinedTextField(
                    value = thigh,
                    onValueChange = { thigh = it; validationError = null },
                    label = { Text("${if (en) "Thigh" else "Bacak"} (${MeasurementUnits.heightUnit(unitSystem)})") },
                    singleLine = true,
                    keyboardOptions = keyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    val measurement = BodyMeasurementData(
                        date = LocalDate.now().toString(),
                        weightKg = parsed(weight)?.let { MeasurementUnits.weightToKg(it, unitSystem) },
                        waistCm = lengthCm(waist),
                        hipsCm = lengthCm(hips),
                        chestCm = lengthCm(chest),
                        armCm = lengthCm(arm),
                        thighCm = lengthCm(thigh),
                    )
                    val invalidWeight = weight.isNotBlank() && measurement.weightKg?.let { it in 20.0..400.0 } != true
                    val rawLengths = listOf(waist, hips, chest, arm, thigh)
                    val lengths = listOf(measurement.waistCm, measurement.hipsCm, measurement.chestCm, measurement.armCm, measurement.thighCm)
                    val invalidLength = rawLengths.zip(lengths).any { (raw, value) ->
                        raw.isNotBlank() && value?.let { it in 10.0..300.0 } != true
                    }
                    when {
                        invalidWeight -> validationError = if (en) "Weight must be between 20 and 400 kg." else "Kilo 20 ile 400 kg arasında olmalı."
                        invalidLength -> validationError = if (en) "Measurements must be between 10 and 300 cm." else "Çevre ölçüleri 10 ile 300 cm arasında olmalı."
                        listOf(measurement.weightKg, measurement.waistCm, measurement.hipsCm, measurement.chestCm, measurement.armCm, measurement.thighCm).all { it == null } -> validationError = if (en) "Enter at least one valid value." else "En az bir geçerli değer gir."
                        else -> onSave(measurement)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) { Text(if (saving) (if (en) "Saving…" else "Kaydediliyor…") else if (en) "Save" else "Kaydet") }
        },
    )
}

@Composable
private fun MeasurementInputRow(
    firstValue: String,
    onFirstChange: (String) -> Unit,
    firstLabel: String,
    secondValue: String,
    onSecondChange: (String) -> Unit,
    secondLabel: String,
    unit: String,
    keyboard: KeyboardOptions,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(firstValue, onFirstChange, Modifier.weight(1f), label = { Text("$firstLabel ($unit)") }, singleLine = true, keyboardOptions = keyboard)
        OutlinedTextField(secondValue, onSecondChange, Modifier.weight(1f), label = { Text("$secondLabel ($unit)") }, singleLine = true, keyboardOptions = keyboard)
    }
}

private fun measurementDelta(first: Double?, latest: Double?, unitSystem: String) = if (first != null && latest != null) MeasurementUnits.formatLength(latest - first, unitSystem, signed = true).replace('.', ',') else null

private fun sessionDate(session: WorkoutSessionData): LocalDate = runCatching { Instant.parse(session.completedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.recoverCatching { LocalDate.parse(session.completedAt.take(10)) }.getOrDefault(LocalDate.MIN)
private fun performanceDate(performance: WorkoutExercisePerformanceData): LocalDate = runCatching { Instant.parse(performance.completedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.recoverCatching { LocalDate.parse(performance.completedAt.take(10)) }.getOrDefault(LocalDate.MIN)
private fun routeDate(route: RouteActivityData): LocalDate = runCatching { Instant.parse(route.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.recoverCatching { LocalDate.parse(route.startedAt.take(10)) }.getOrDefault(LocalDate.MIN)

private fun formatDate(raw: String, en: Boolean): String = runCatching {
    LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern(if (en) "d MMM" else "d MMM", if (en) Locale.ENGLISH else Locale("tr", "TR")))
}.getOrDefault(raw.take(10))

@Composable
private fun WeeklyReviewCard(data: DashboardData?, en: Boolean, onClick: () -> Unit) {
    HedefitCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(HedefitColors.Sleep.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, null, tint = HedefitColors.Sleep)
            }
            Column(Modifier.weight(1f)) {
                Text(if (en) "Weekly Review" else "Haftalık Değerlendirme", style = MaterialTheme.typography.titleMedium)
                Text(if (data?.sessions.isNullOrEmpty()) (if (en) "Your personal review appears after your first workout." else "İlk antrenmanını tamamladıktan sonra kişisel değerlendirmen burada oluşacak.") else if (en) "Your new week is ready to review from ${data?.sessions?.size ?: 0} workouts." else "${data?.sessions?.size ?: 0} antrenman kaydın üzerinden yeni haftan değerlendirilmeye hazır.", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = HedefitColors.TextSecondary)
        }
    }
}

@Composable
private fun WeeklyReviewDialog(data: DashboardData?, en: Boolean, onDismiss: () -> Unit) {
    val sessions = data?.sessions.orEmpty()
    val minutes = sessions.sumOf { it.durationSeconds } / 60
    val completed = sessions.sumOf { it.completedExercises }
    val planned = sessions.sumOf { it.totalExercises }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Weekly Review" else "Haftalık Değerlendirme") },
        text = {
            if (sessions.isEmpty()) Text(if (en) "Complete your first workout to unlock a personal weekly review." else "Kişisel haftalık değerlendirmeni görmek için ilk antrenmanını tamamla.")
            else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (en) "${sessions.size} workouts • $minutes minutes" else "${sessions.size} antrenman • $minutes dakika", style = MaterialTheme.typography.titleLarge, color = HedefitColors.Lime)
                Text(if (en) "$completed of $planned planned movements were completed." else "Planlanan $planned hareketin $completed tanesi tamamlandı.", color = HedefitColors.TextSecondary)
                Text(if (en) "Use your fatigue and pain feedback to keep next week progressive but sustainable." else "Gelecek haftayı ilerleyici ama sürdürülebilir tutmak için yorgunluk ve ağrı geri bildirimlerini kullan.", color = HedefitColors.TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (en) "Close" else "Kapat") } },
    )
}
