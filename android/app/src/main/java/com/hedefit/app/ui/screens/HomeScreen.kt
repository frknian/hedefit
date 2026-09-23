package com.hedefit.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.R
import com.hedefit.app.ui.components.CardDivider
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.MacroBar
import com.hedefit.app.ui.components.MetricCard
import com.hedefit.app.ui.components.PrimaryButton
import com.hedefit.app.ui.components.ProgressRing
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.components.AdBanner
import com.hedefit.app.ui.components.SectionTitle
import com.hedefit.app.ui.components.Sparkline
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.ui.components.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEvents
import com.hedefit.app.ui.settings.MeasurementUnits
import com.hedefit.app.ui.settings.estimatedGoalWeeks
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.data.model.NutritionGoalData
import com.hedefit.app.data.model.NutritionLogData
import com.hedefit.app.data.model.WorkoutExerciseData
import com.hedefit.app.data.model.WorkoutProgramData
import com.hedefit.app.route.RouteTrackingStore
import com.hedefit.app.route.formatDuration
import com.hedefit.app.route.formatPace
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.time.LocalDate
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import com.hedefit.app.steps.StepSource
import kotlin.math.abs

@Composable
fun HomeScreen(
    padding: PaddingValues,
    expanded: Boolean,
    data: DashboardData?,
    avatarPreview: ByteArray?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenRoute: () -> Unit,
    onOpenGoal: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenProgram: (String?) -> Unit,
    onProgramHomeVisibilityChange: (WorkoutProgramData, Boolean) -> Unit,
    unitSystem: String,
    stepGoal: Int,
    onStepGoalChange: (Int) -> Unit,
    waterGoalMl: Int,
    onWaterGoalChange: (Int) -> Unit,
    onAddWater: (Int) -> Unit,
    language: String,
    stepSource: StepSource = StepSource.UNAVAILABLE,
    showAds: Boolean = false,
    onOpenCoach: () -> Unit = {},
    onSaveSleep: (Int, String) -> Unit = { _, _ -> },
    onOpenGame: () -> Unit = {},
) {
    val en = language == "en"
    var metricDialog by remember { mutableStateOf<String?>(null) }
    ScreenContainer(padding) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HomeHeader(
                    name = data?.profile?.displayName ?: if (en) "Athlete" else "Sporcu",
                    avatarUrl = data?.profile?.avatarUrl,
                    avatarPreview = avatarPreview,
                    streak = data?.streakDays ?: 0,
                    onOpenProfile = onOpenProfile,
                    onOpenNotifications = onOpenNotifications,
                    onOpenGame = onOpenGame,
                    en = en,
                )
            }
            if (data == null) {
                item { HomeDataState(loading, error, onRetry) }
                return@LazyColumn
            }
            item { TodayCard(data, en, onOpenProgram) }
            item {
                val done = listOf(
                    data.nutritionLogs.sumOf { it.calories } >= data.nutritionGoal.calories * .9,
                    data.steps >= stepGoal,
                    data.waterMl >= waterGoalMl,
                    data.sleepMinutes >= 420,
                ).count { it }
                HfSectionHeader(if (en) "Daily balance" else "Günlük denge", if (en) "$done of 4 goals met" else "4 hedefin $done'i tamam")
            }
            item {
                DailyBalanceCard(
                    data = data,
                    stepGoal = stepGoal,
                    waterGoalMl = waterGoalMl,
                    en = en,
                    onSteps = { metricDialog = "steps" },
                    onCalories = { metricDialog = "calories" },
                    onWater = { metricDialog = "water" },
                    onSleep = { metricDialog = "sleep" },
                )
            }
            item { HfSectionHeader(if (en) "Quick actions" else "Hızlı işlemler") }
            item {
                QuickActionsGrid(
                    en = en,
                    sleepMinutes = data.sleepMinutes,
                    onOpenNutrition = onOpenNutrition,
                    onOpenRoute = onOpenRoute,
                    onSleep = { metricDialog = "sleep" },
                    onOpenCoach = onOpenCoach,
                )
            }
            item { HfSectionHeader(if (en) "Goal journey" else "Hedef yolculuğu") }
            item { GoalProjectionCard(data, onOpenGoal, en, unitSystem) }
            if (showAds) item { AdBanner(Modifier.fillMaxWidth()) }
            item { HomePrograms(data, en, onOpenProgram, onProgramHomeVisibilityChange) }
        }
    }
    when (metricDialog) {
        "steps" -> StepDetailDialog(data?.steps ?: 0, stepGoal, data?.stepHistory.orEmpty(), en, { metricDialog = null }, { onStepGoalChange(it); metricDialog = null })
        "calories" -> CalorieDetailDialog(data, en, stepSource) { metricDialog = null }
        "water" -> WaterAddDialog(data?.waterMl ?: 0, waterGoalMl, en, unitSystem, { metricDialog = null }, onWaterGoalChange) { onAddWater(it); metricDialog = null }
        "sleep" -> SleepDetailDialog(data?.sleepMinutes ?: 0, en, { metricDialog = null }) { mins, q -> onSaveSleep(mins, q); metricDialog = null }
    }
}

@Composable
private fun HomeHeader(
    name: String,
    avatarUrl: String?,
    avatarPreview: ByteArray?,
    streak: Int,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenGame: () -> Unit,
    en: Boolean,
) {
    val now = LocalDate.now()
    val locale = java.util.Locale(if (en) "en" else "tr")
    val date = now.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM EEEE", locale))
    HfScreenHeader(
        title = if (en) "Hi, $name" else "Merhaba, $name",
        subtitle = (if (en) "Today • " else "Bugün • ") + date,
    ) {
        Box {
            HfCircleButton(Icons.Default.EmojiEvents, if (en) "Rewards, $streak day streak" else "Ödüller, $streak günlük seri", onOpenGame)
            if (streak > 0) Text(
                "$streak",
                color = HedefitColors.OnLime,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopEnd).background(HedefitColors.Lime, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        HfCircleButton(Icons.Default.NotificationsNone, if (en) "Notifications" else "Bildirimler", onOpenNotifications)
        HomeAvatar(avatarUrl, avatarPreview, name, Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = if (en) "Profile and settings" else "Profil ve ayarlar", onClick = onOpenProfile))
    }
}

@Composable
private fun TodayCard(
    data: DashboardData,
    en: Boolean,
    onOpenProgram: (String?) -> Unit,
) {
    val program = data.workoutPrograms.firstOrNull { it.isActive } ?: data.workoutPrograms.firstOrNull()
    val exerciseCount = data.workouts.size
    val today = LocalDate.now()
    val workoutDoneToday = data.sessions.any { session ->
        runCatching { java.time.Instant.parse(session.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today }.getOrDefault(false)
    }
    val areas = data.workouts.map { it.area }.filter { it.isNotBlank() }.distinct().take(3)
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (en) "TODAY'S WORKOUT" else "BUGÜNÜN ANTRENMANI", color = HedefitColors.Lime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (workoutDoneToday) HfPill(if (en) "Completed" else "Tamamlandı")
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    program?.name ?: if (en) "Create your workout plan" else "Antrenman planını oluştur",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (exerciseCount > 0) (if (en) "$exerciseCount exercises • ~${exerciseCount * 7} min" else "$exerciseCount hareket • ~${exerciseCount * 7} dk")
                    else if (en) "Choose an AI or custom routine to start today" else "Bugüne başlamak için akıllı veya özel bir rutin seç",
                    color = HedefitColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (areas.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { areas.forEach { HfTag(it) } }
            HfPrimaryButton(
                text = if (exerciseCount > 0) (if (en) "Start workout" else "Antrenmanı başlat") else if (en) "Create a plan" else "Plan oluştur",
                onClick = { onOpenProgram(program?.id) },
                icon = if (exerciseCount > 0) Icons.Default.PlayArrow else Icons.Default.Add,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DailyBalanceCard(
    data: DashboardData,
    stepGoal: Int,
    waterGoalMl: Int,
    en: Boolean,
    onSteps: () -> Unit,
    onCalories: () -> Unit,
    onWater: () -> Unit,
    onSleep: () -> Unit,
) {
    val consumed = data.nutritionLogs.sumOf { it.calories }
    val target = data.nutritionGoal.calories.coerceAtLeast(1)
    fun fmt(n: Int) = "%,d".format(n).replace(',', '.')
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            BalanceRing(if (en) "Calories" else "Kalori", fmt(consumed), "/ ${fmt(target)}", consumed / target.toFloat(), HedefitColors.Lime, onCalories, Modifier.weight(1f))
            BalanceRing(if (en) "Steps" else "Adım", fmt(data.steps), "/ ${fmt(stepGoal)}", data.steps / stepGoal.coerceAtLeast(1).toFloat(), HedefitColors.Water, onSteps, Modifier.weight(1f))
            BalanceRing(if (en) "Water" else "Su", "%.1f".format(data.waterMl / 1000f), "/ %.1f L".format(waterGoalMl / 1000f), data.waterMl / waterGoalMl.coerceAtLeast(1).toFloat(), HedefitColors.Water, onWater, Modifier.weight(1f))
            val sleep = if (data.sleepMinutes > 0) "${data.sleepMinutes / 60}s ${data.sleepMinutes % 60}dk" else "—"
            BalanceRing(if (en) "Sleep" else "Uyku", sleep, if (en) "/ 8h" else "/ 8s", data.sleepMinutes / 480f, HedefitColors.Sleep, onSleep, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BalanceRing(label: String, value: String, target: String, progress: Float, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = label, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(progress.coerceIn(0f, 1f), Modifier.size(70.dp), 7.dp, color = color) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(target, fontSize = 9.sp, color = HedefitColors.TextMuted, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickActionsGrid(en: Boolean, sleepMinutes: Int, onOpenNutrition: () -> Unit, onOpenRoute: () -> Unit, onSleep: () -> Unit, onOpenCoach: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            HfActionTile(Icons.Default.Restaurant, HedefitColors.Lime, if (en) "Log meal" else "Öğün ekle", if (en) "Text, photo or search" else "Yazı, foto veya arama", onOpenNutrition, Modifier.weight(1f).fillMaxHeight())
            HfActionTile(Icons.Default.Route, HedefitColors.Water, if (en) "Hedefit Route" else "Hedefit Rota", if (en) "GPS run or walk" else "GPS ile koşu, yürüyüş", onOpenRoute, Modifier.weight(1f).fillMaxHeight())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            val sleepSub = if (sleepMinutes > 0) (if (en) "Last night: ${sleepMinutes / 60}h ${sleepMinutes % 60}m" else "Dün gece: ${sleepMinutes / 60}s ${sleepMinutes % 60}dk") else if (en) "Not logged yet" else "Henüz girilmedi"
            HfActionTile(Icons.Default.Bedtime, HedefitColors.Sleep, if (en) "Log sleep" else "Uyku gir", sleepSub, onSleep, Modifier.weight(1f).fillMaxHeight())
            HfActionTile(Icons.Default.AutoAwesome, HedefitColors.Warning, if (en) "Ask Fit Coach" else "FitKoç'a sor", if (en) "Training and nutrition" else "Antrenman ve beslenme", onOpenCoach, Modifier.weight(1f).fillMaxHeight())
        }
    }
}


@Composable
private fun HomeAvatar(url: String?, previewBytes: ByteArray?, name: String, modifier: Modifier) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val preview = remember(previewBytes) { previewBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    LaunchedEffect(url) { bitmap = url?.let { address -> withContext(Dispatchers.IO) { runCatching { URL(address).openStream().use(BitmapFactory::decodeStream) }.getOrNull() } } }
    Box(modifier.clip(CircleShape).background(HedefitColors.Lime).padding(2.dp).clip(CircleShape).background(HedefitColors.SurfaceHigh), contentAlignment = Alignment.Center) {
        (preview ?: bitmap)?.let { Image(it.asImageBitmap(), contentDescription = "Profil fotoğrafı", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Text(name.take(2).uppercase(), color = HedefitColors.Lime, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GoalProjectionCard(data: DashboardData, onOpen: () -> Unit, en: Boolean, unitSystem: String) {
    val current = data.measurements.lastOrNull()?.weightKg ?: data.profile.weightKg
    val target = data.profile.targetWeightKg
    val weeks = estimatedGoalWeeks(current, target)
    val initial = data.measurements.firstOrNull()?.weightKg ?: current
    val totalChange = if (initial != null && target != null) abs(target - initial) else 0.0
    val completedChange = if (initial != null && current != null) abs(current - initial) else 0.0
    val progress = if (totalChange > .05) (completedChange / totalChange).toFloat().coerceIn(0f, 1f) else 0f
    val remaining = if (current != null && target != null) abs(target - current) else null
    HedefitCard(Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(if (en) "NOW" else "ŞİMDİ", color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(current?.let { MeasurementUnits.formatWeight(it, unitSystem, 1) } ?: "—", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
                HfPill(if (weeks != null) (if (en) "$weeks weeks left" else "$weeks hafta kaldı") else if (en) "Set a goal" else "Hedef belirle", modifier = Modifier.padding(bottom = 4.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(if (en) "TARGET" else "HEDEF", color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(target?.let { MeasurementUnits.formatWeight(it, unitSystem, 1) } ?: "—", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = HedefitColors.TextSecondary)
                }
            }
            HfProgressBar(progress, height = 8.dp)
            Text(
                remaining?.let { if (en) "${MeasurementUnits.formatWeight(it, unitSystem, 1)} to go • tap for weekly pace" else "${MeasurementUnits.formatWeight(it, unitSystem, 1)} kaldı • haftalık tempo için dokun" }
                    ?: if (en) "Set a target weight to see your pace." else "Temponu görmek için hedef kilonu belirle.",
                color = HedefitColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HomePrograms(
    data: DashboardData,
    en: Boolean,
    onOpenProgram: (String?) -> Unit,
    onVisibilityChange: (WorkoutProgramData, Boolean) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val programs = data.workoutPrograms.filter(WorkoutProgramData::showOnHome)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        HfSectionHeader(if (en) "Programs" else "Programlar", if (en) "Manage" else "Düzenle") { showPicker = true }
        if (programs.isEmpty()) {
            TextButton(onClick = { showPicker = true }) { Icon(Icons.Default.Add, null, tint = HedefitColors.Lime); Spacer(Modifier.width(6.dp)); Text(if (en) "Add a program" else "Program Ekle", color = HedefitColors.Lime) }
        } else LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(end = 18.dp)) {
            items(programs, key = { it.id }) { program ->
                HedefitCard(Modifier.width(218.dp).height(108.dp), onClick = { onOpenProgram(program.id) }, contentPadding = PaddingValues(14.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).background(HedefitColors.Lime.copy(alpha = .15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FitnessCenter, null, tint = HedefitColors.Lime, modifier = Modifier.size(17.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (en) "PROGRAM" else "PROGRAM", color = HedefitColors.Lime, style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text(program.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(program.focusArea.takeIf { it.isNotBlank() }, if (en) "${program.exercises.size} exercises" else "${program.exercises.size} hareket").joinToString(" • "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = HedefitColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showPicker) AlertDialog(
        onDismissRequest = { showPicker = false },
        title = { Text(if (en) "Add programs" else "Program Ekle") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (data.workoutPrograms.isEmpty()) Text(if (en) "Create a program from the Workout tab first." else "Önce Antrenman sekmesinden bir program oluştur.", color = HedefitColors.TextSecondary)
            data.workoutPrograms.forEach { program ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(program.name); Text(if (en) "${program.exercises.size} exercises" else "${program.exercises.size} hareket", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                    androidx.compose.material3.Checkbox(checked = program.showOnHome, onCheckedChange = { onVisibilityChange(program, it) })
                }
            }
        } },
        confirmButton = { TextButton(onClick = { showPicker = false }) { Text(if (en) "Done" else "Tamam") } },
    )
}

@Composable
private fun CompactDailySummary(data: DashboardData, en: Boolean, unitSystem: String, stepSource: StepSource, onSteps: () -> Unit, onCalories: () -> Unit, onWater: () -> Unit) {
    val sourceLabel = when (stepSource) {
        StepSource.HEALTH_CONNECT -> if (en) "Health Connect" else "Health Connect"
        StepSource.DEVICE_STEP_COUNTER, StepSource.DEVICE_STEP_DETECTOR -> if (en) "This device" else "Bu cihaz"
        StepSource.UNAVAILABLE -> null
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactMetricCard(Icons.Default.DirectionsWalk, "%,d".format(data.steps).replace(',', '.'), sourceLabel, HedefitColors.Lime, onSteps, Modifier.weight(1f))
        CalorieBalanceCompactCard(data, en, stepSource, onCalories, Modifier.weight(1f))
        CompactMetricCard(Icons.Default.LocalDrink, MeasurementUnits.formatWater(data.waterMl, unitSystem), null, HedefitColors.Water, onWater, Modifier.weight(1f))
    }
}

@Composable
private fun CalorieBalanceCompactCard(data: DashboardData, en: Boolean, stepSource: StepSource, onClick: () -> Unit, modifier: Modifier) {
    val consumed = data.nutritionLogs.sumOf { it.calories }
    val today = LocalDate.now()
    val manualBurned = data.sessions.filter { session ->
        session.manualActivityKey != null && runCatching { java.time.Instant.parse(session.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today }.getOrDefault(false)
    }.sumOf { it.calories }
    // Health Connect aktifken onun birleştirilmiş kalorisi kanonik kaynaktır;
    // aynı seansı manuel kayıtla ikinci kez eklemeyiz.
    val burned = data.activeCalories + if (stepSource == StepSource.HEALTH_CONNECT) 0 else manualBurned
    HedefitCard(modifier.height(96.dp), onClick = onClick, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 9.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.LocalFireDepartment, null, tint = HedefitColors.Warning, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(3.dp))
            Text(if (en) "IN $consumed" else "ALINAN $consumed", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            Text(if (en) "OUT $burned" else "YAKILAN $burned", maxLines = 1, color = HedefitColors.Warning, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CompactMetricCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String?, label: String?, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    HedefitCard(modifier.height(96.dp), onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(if (value == null) 42.dp else 20.dp))
            if (value != null) Spacer(Modifier.height(4.dp))
            value?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) }
            label?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun DailyMotivationCard(en: Boolean) {
    val messages = if (en) listOf(
        "Consistency beats intensity you cannot repeat.",
        "One controlled rep is progress you can build on.",
        "Train for the person you want to be tomorrow.",
        "A short workout still keeps the promise you made to yourself.",
        "Good form today creates strength for the long run.",
        "You do not need perfect conditions; you need the next step.",
        "Recovery is part of training, not time away from it.",
    ) else listOf(
        "Tekrarlayabildiğin düzen, sürdüremediğin yoğunluktan güçlüdür.",
        "Kontrollü yapılan tek bir tekrar bile üzerine koyabileceğin ilerlemedir.",
        "Yarın olmak istediğin kişi için bugün hareket et.",
        "Kısa bir antrenman da kendine verdiğin sözü tutar.",
        "Bugünkü iyi form, uzun vadeli gücün temelidir.",
        "Mükemmel koşullara değil, sıradaki adıma ihtiyacın var.",
        "Toparlanma antrenmanın dışında değil, onun bir parçasıdır.",
    )
    val message = messages[(LocalDate.now().dayOfYear - 1) % messages.size]
    HedefitCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).background(HedefitColors.Lime.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = HedefitColors.Lime) }
            Column(Modifier.weight(1f)) {
                Text(if (en) "Today" else "Bugünün sözü", color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge)
                Text(message, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun StepDetailDialog(steps: Int, goal: Int, history: List<com.hedefit.app.data.model.DailyStepData>, en: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var target by remember(goal) { mutableStateOf(goal.toString()) }
    var historyDays by remember { mutableStateOf(7) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (en) "Step details" else "Adım detayları") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (en) "$steps steps today" else "Bugün $steps adım", color = HedefitColors.Lime, style = MaterialTheme.typography.headlineSmall)
        Text(if (en) "You completed %${(steps * 100 / goal.coerceAtLeast(1)).coerceAtMost(999)} of your target." else "Hedefinin %${(steps * 100 / goal.coerceAtLeast(1)).coerceAtMost(999)} kadarını tamamladın.", color = HedefitColors.TextSecondary)
        OutlinedTextField(target, { target = it.filter(Char::isDigit).take(5) }, label = { Text(if (en) "Daily step target" else "Günlük adım hedefi") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(6000, 8000, 10000, 12000).forEach { value -> TextButton(onClick = { target = value.toString() }) { Text("${value / 1000}K") } } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { historyDays = 7 }) { Text(if (en) "7 days" else "7 gün") }; TextButton(onClick = { historyDays = 30 }) { Text(if (en) "30 days" else "30 gün") } }
        history.takeLast(historyDays).joinToString(" · ") { "${it.localDate.dayOfMonth}: ${it.steps / 1000}K" }.takeIf { it.isNotBlank() }?.let { Text(it, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Close" else "Kapat") } }, confirmButton = { Button(onClick = { onSave(target.toIntOrNull()?.coerceIn(1000, 50000) ?: goal) }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "Save target" else "Hedefi kaydet") } })
}

@Composable
private fun CalorieDetailDialog(data: DashboardData?, en: Boolean, stepSource: StepSource, onDismiss: () -> Unit) {
    val consumed = data?.nutritionLogs.orEmpty().sumOf { it.calories }
    val base = data?.nutritionGoal?.calories ?: 0
    val manualBurned = data?.sessions.orEmpty().filter { session -> session.manualActivityKey != null && runCatching { java.time.Instant.parse(session.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }.getOrDefault(false) }.sumOf { it.calories }
    val burned = (data?.activeCalories ?: 0) + if (stepSource == StepSource.HEALTH_CONNECT) 0 else manualBurned
    val target = base + burned.coerceIn(0, 600)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (en) "Calorie balance" else "Kalori dengesi") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (en) "Your target is $target kcal" else "$target kcal almalısın", color = HedefitColors.Lime, style = MaterialTheme.typography.headlineSmall)
        HedefitCard { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(if (en) "Consumed: $consumed kcal" else "Alınan: $consumed kcal"); Text(if (en) "Active burn: $burned kcal" else "Aktivitede harcanan: $burned kcal"); Text(if (en) "Remaining: ${(target - consumed).coerceAtLeast(0)} kcal" else "Kalan: ${(target - consumed).coerceAtLeast(0)} kcal", color = HedefitColors.Lime) } }
        Text(if (en) "Health Connect and manually logged sports update this value." else "Health Connect ve elle eklediğin sporlar bu değeri günceller.", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = onDismiss) { Text(if (en) "Done" else "Tamam") } })
}

@Composable
private fun WaterAddDialog(currentMl: Int, goalMl: Int, en: Boolean, unitSystem: String, onDismiss: () -> Unit, onGoalChange: (Int) -> Unit, onAdd: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (en) "Add water" else "Su ekle") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (en) "${MeasurementUnits.formatWater(currentMl, unitSystem)} today" else "Bugün ${MeasurementUnits.formatWater(currentMl, unitSystem)} içtin", color = HedefitColors.Water, style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (en) "Daily goal" else "Günlük hedef", color = HedefitColors.TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onGoalChange((goalMl - 250).coerceAtLeast(500)) }) { Icon(Icons.Default.Remove, if (en) "Decrease goal" else "Hedefi azalt") }
                Text(MeasurementUnits.formatWater(goalMl, unitSystem), color = HedefitColors.Water, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onGoalChange((goalMl + 250).coerceAtMost(10_000)) }) { Icon(Icons.Default.Add, if (en) "Increase goal" else "Hedefi artır") }
            }
        }
        Box(Modifier.fillMaxWidth().height(7.dp).background(HedefitColors.Divider, CircleShape)) { Box(Modifier.fillMaxWidth((currentMl / goalMl.toFloat()).coerceIn(0f, 1f)).height(7.dp).background(HedefitColors.Water, CircleShape)) }
        listOf(200, 300, 500).forEach { amount -> Button(onClick = { onAdd(amount) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.SurfaceHigh, contentColor = HedefitColors.Water)) { Text("+${if (MeasurementUnits.isImperial(unitSystem)) MeasurementUnits.formatWater(amount, unitSystem) else "$amount ml"}") } }
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Close" else "Kapat") } }, confirmButton = {})
}

@Composable
private fun SleepDetailDialog(
    currentMinutes: Int,
    en: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit,
) {
    var hours by remember(currentMinutes) { mutableIntStateOf(if (currentMinutes > 0) currentMinutes / 60 else 7) }
    var minutes by remember(currentMinutes) { mutableIntStateOf(if (currentMinutes > 0) currentMinutes % 60 else 30) }
    var quality by remember { mutableStateOf("iyi") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Sleep Tracking" else "Uyku Takibi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = if (currentMinutes > 0) {
                        if (en) "${currentMinutes / 60}h ${currentMinutes % 60}m recorded today"
                        else "Bugün ${currentMinutes / 60} sa ${currentMinutes % 60} dk kayıtlı"
                    } else {
                        if (en) "Log last night's sleep" else "Dün gecenin uykusunu kaydet"
                    },
                    color = HedefitColors.Sleep,
                    style = MaterialTheme.typography.headlineSmall,
                )

                Text(
                    if (en) "Quick presets:" else "Hızlı seçim:",
                    color = HedefitColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        (6 to 0) to "6s",
                        (7 to 0) to "7s",
                        (7 to 30) to "7.5s",
                        (8 to 0) to "8s",
                        (8 to 30) to "8.5s",
                    ).forEach { (pair, label) ->
                        val (h, m) = pair
                        val isSelected = hours == h && minutes == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) HedefitColors.Sleep.copy(alpha = 0.25f) else HedefitColors.SurfaceHigh)
                                .border(1.dp, if (isSelected) HedefitColors.Sleep else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    hours = h
                                    minutes = m
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) HedefitColors.Sleep else HedefitColors.TextPrimary,
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (en) "Hours" else "Saat", style = MaterialTheme.typography.labelSmall, color = HedefitColors.TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (hours > 0) hours-- }) {
                                Icon(Icons.Default.Remove, null, tint = HedefitColors.TextPrimary)
                            }
                            Text("$hours", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { if (hours < 18) hours++ }) {
                                Icon(Icons.Default.Add, null, tint = HedefitColors.TextPrimary)
                            }
                        }
                    }

                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (en) "Minutes" else "Dakika", style = MaterialTheme.typography.labelSmall, color = HedefitColors.TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { minutes = (minutes - 15 + 60) % 60 }) {
                                Icon(Icons.Default.Remove, null, tint = HedefitColors.TextPrimary)
                            }
                            Text("%02d".format(minutes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { minutes = (minutes + 15) % 60 }) {
                                Icon(Icons.Default.Add, null, tint = HedefitColors.TextPrimary)
                            }
                        }
                    }
                }

                Text(if (en) "Sleep Quality" else "Uyku Kalitesi", style = MaterialTheme.typography.labelSmall, color = HedefitColors.TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "kotu" to (if (en) "Poor" else "Kötü"),
                        "orta" to (if (en) "Average" else "Orta"),
                        "iyi" to (if (en) "Good" else "İyi"),
                    ).forEach { (qKey, qLabel) ->
                        val isSel = quality == qKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) HedefitColors.Sleep.copy(alpha = 0.25f) else HedefitColors.SurfaceHigh)
                                .border(1.dp, if (isSel) HedefitColors.Sleep else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { quality = qKey }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = qLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) HedefitColors.Sleep else HedefitColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "İptal") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val totalMinutes = hours * 60 + minutes
                    onSave(totalMinutes, quality)
                },
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Sleep, contentColor = Color.Black),
            ) {
                Text(if (en) "Save" else "Kaydet", fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun CompactRecovery(data: DashboardData, en: Boolean) {
    val latestFatigue = data.sessions.firstOrNull()?.fatigue
    val ready = data.sleepMinutes >= 360 && (latestFatigue == null || latestFatigue <= 3)
    Row(Modifier.fillMaxWidth().background(HedefitColors.SurfaceHigh, RoundedCornerShape(14.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Bedtime, null, tint = if (ready) HedefitColors.Lime else HedefitColors.Warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (ready) (if (en) "Recovered • Ready today" else "Toparlanman iyi • Bugün hazırsın") else (if (en) "Low recovery • Keep it light" else "Toparlanma düşük • Yükü kontrollü tut"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.ChevronRight, null, tint = HedefitColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun WorkoutHero(workout: WorkoutExerciseData?, onStartWorkout: () -> Unit) {
    HedefitCard(contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            Image(
                painterResource(R.drawable.exercise_curl),
                contentDescription = "Üst vücut antrenmanı",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, HedefitColors.Background.copy(alpha = .45f), HedefitColors.Background.copy(alpha = .96f))),
                ),
            )
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FitnessCenter, null, tint = HedefitColors.Lime, modifier = Modifier.size(22.dp))
                    Text("${workout?.sets ?: 3} set • ${workout?.reps ?: "8–12"} tekrar", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(5.dp))
                Text(workout?.name ?: "Programını oluştur", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(HedefitColors.Divider, CircleShape)) {
                    Box(Modifier.fillMaxWidth(.70f).height(6.dp).background(HedefitColors.Lime, CircleShape))
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(if (workout == null) "Antrenman Sekmesine Git" else "Başla", onStartWorkout, icon = Icons.Default.PlayArrow)
            }
        }
    }
}

@Composable
private fun NutritionSummary(logs: List<NutritionLogData>, goal: NutritionGoalData) {
    val calories = logs.sumOf { it.calories }
    val protein = logs.sumOf { it.protein }
    val carbs = logs.sumOf { it.carbs }
    val fat = logs.sumOf { it.fat }
    HedefitCard(Modifier.fillMaxWidth()) {
        BoxWithConstraints {
            val compact = maxWidth < 310.dp
            val ring: @Composable () -> Unit = {
                ProgressRing(calories / goal.calories.toFloat(), Modifier.size(124.dp), strokeWidth = 10.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = HedefitColors.Lime, modifier = Modifier.size(18.dp))
                        Text("$calories", style = MaterialTheme.typography.headlineSmall)
                        Text("kcal", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val macros: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    MacroBar("Protein", "${protein.toInt()} / ${goal.protein} g", (protein / goal.protein).toFloat())
                    MacroBar("Karbonhidrat", "${carbs.toInt()} / ${goal.carbs} g", (carbs / goal.carbs).toFloat())
                    MacroBar("Yağ", "${fat.toInt()} / ${goal.fat} g", (fat / goal.fat).toFloat())
                }
            }
            if (compact) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    ring()
                    macros()
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ring()
                Box(Modifier.weight(1f)) { macros() }
            }
        }
    }
}

@Composable
private fun ActivityMetrics(data: DashboardData) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(Icons.Default.DirectionsWalk, "%,d".format(data.steps).replace(',', '.'), "adım", Modifier.weight(1f)) {
                Sparkline(listOf(1f, 3f, 2f, 5f, 4f, 8f, 7f), Modifier.fillMaxWidth().height(24.dp))
            }
            MetricCard(Icons.Default.LocalFireDepartment, "${data.streakDays} gün", "günlük seri", Modifier.weight(1f), HedefitColors.Warning) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { index -> Box(Modifier.size(8.dp).background(if (index < data.streakDays.coerceAtMost(7)) HedefitColors.Lime else HedefitColors.Divider, CircleShape)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(Icons.Default.LocalDrink, "%.1f L".format(data.waterMl / 1000.0), "su", Modifier.weight(1f), HedefitColors.Water)
            MetricCard(Icons.Default.Bedtime, "${data.sleepMinutes / 60} sa ${data.sleepMinutes % 60} dk", "uyku", Modifier.weight(1f), HedefitColors.Sleep)
        }
    }
}

@Composable
private fun RecoveryCard(data: DashboardData) {
    val latestFatigue = data.sessions.firstOrNull()?.fatigue
    val ready = data.sleepMinutes >= 360 && (latestFatigue == null || latestFatigue <= 3)
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Toparlanma")
            Text(if (ready) "Bugünkü antrenman için hazırsın" else "Bugün yükü kontrollü tut", style = MaterialTheme.typography.titleMedium)
            Text(
                "${data.sleepMinutes / 60} saat ${data.sleepMinutes % 60} dakika uyku ve son antrenmandaki ${latestFatigue ?: "—"}/5 yorgunluk puanın birlikte değerlendirildi.",
                color = HedefitColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(HedefitColors.Divider)) {
                Box(Modifier.fillMaxWidth(if (ready) .78f else .42f).height(6.dp).background(if (ready) HedefitColors.Lime else HedefitColors.Warning, CircleShape))
            }
        }
    }
}

@Composable
private fun HomeDataState(loading: Boolean, error: String?, onRetry: () -> Unit) {
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
            if (loading) CircularProgressIndicator(color = HedefitColors.Lime)
            Text(if (loading) "Verilerin yükleniyor…" else error ?: "Henüz veri bulunamadı.", color = HedefitColors.TextSecondary)
            if (!loading) PrimaryButton("Tekrar Dene", onRetry, Modifier.fillMaxWidth(.7f))
        }
    }
}
