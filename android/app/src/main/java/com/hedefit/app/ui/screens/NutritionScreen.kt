package com.hedefit.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EggAlt
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import com.hedefit.app.ui.components.HfPill
import com.hedefit.app.ui.components.HfDivider
import com.hedefit.app.ui.components.HfIconBadge
import com.hedefit.app.ui.components.HfPrimaryButton
import androidx.compose.material.icons.filled.WaterDrop
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.MacroBar
import com.hedefit.app.ui.components.OutlineAction
import com.hedefit.app.ui.components.PrimaryButton
import com.hedefit.app.ui.components.ProgressRing
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.components.SectionTitle
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.ui.validation.validateFoodGrams
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.data.model.NutritionGoalData
import com.hedefit.app.data.model.NutritionLogData
import com.hedefit.app.data.model.FoodSearchData
import com.hedefit.app.data.model.FavoriteMealData
import com.hedefit.app.data.model.ProfileData
import com.hedefit.app.data.model.MealPlanItemData
import com.hedefit.app.data.model.NutritionEstimateData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    padding: PaddingValues,
    expanded: Boolean,
    data: DashboardData?,
    busy: Boolean,
    foodSearchBusy: Boolean,
    foodResults: List<FoodSearchData>,
    foodSearchQuery: String?,
    onAddWithAi: (food: String, grams: Double, meal: String) -> Unit,
    onSearchFoods: (String) -> Unit,
    onAddCatalogFood: (FoodSearchData, Double, String) -> Unit,
    onAddFavorite: (NutritionLogData) -> Unit,
    onRemoveNutritionLog: (NutritionLogData) -> Unit,
    onUpdateNutritionLog: (NutritionLogData, Double, String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onRepeatFavorite: (FavoriteMealData) -> Unit,
    onAddWater: (Int) -> Unit,
    waterGoalMl: Int,
    onWaterGoalChange: (Int) -> Unit,
    language: String = "tr",
    openMealComposer: Boolean = false,
    onMealComposerOpened: () -> Unit = {},
    selectedDate: LocalDate = LocalDate.now(),
    selectedLogs: List<NutritionLogData> = data?.nutritionLogs.orEmpty(),
    historyLogs: List<NutritionLogData> = emptyList(),
    dateLoading: Boolean = false,
    onSelectDate: (LocalDate) -> Unit = {},
    onLoadMonth: (YearMonth) -> Unit = {},
    onAddMealPlanItem: (FoodSearchData, Double, LocalDate, String) -> Unit = { _, _, _, _ -> },
    onToggleMealPlanItem: (MealPlanItemData, Boolean) -> Unit = { _, _ -> },
    onRemoveMealPlanItem: (MealPlanItemData) -> Unit = {},
    photoBusy: Boolean = false,
    photoResults: List<NutritionEstimateData> = emptyList(),
    onAnalyzePhoto: (ByteArray) -> Unit = {},
    onClearPhotoResults: () -> Unit = {},
    onSavePhotoResults: (List<NutritionEstimateData>, String) -> Unit = { _, _ -> },
    onAskCoach: (String) -> Unit = {},
    reviewFromText: Boolean = false,
    reviewMeal: String? = null,
) {
    val en = language == "en"
    var showFoodSearch by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showPhotoSource by remember { mutableStateOf(false) }
    var quickAddMeal by remember { mutableStateOf<String?>(null) }
    var showPlanner by rememberSaveable { mutableStateOf(false) }
    var showTrainingMeals by rememberSaveable { mutableStateOf(false) }
    var showMicros by rememberSaveable { mutableStateOf(false) }
    var tipDismissed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraPhotoFile by remember { mutableStateOf<File?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { prepareMealPhoto(context, uri)?.let(onAnalyzePhoto) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraPhotoUri
        if (saved && uri != null) scope.launch { prepareMealPhoto(context, uri)?.let(onAnalyzePhoto); cameraPhotoFile?.delete(); cameraPhotoFile = null; cameraPhotoUri = null }
    }
    LaunchedEffect(openMealComposer) {
        if (openMealComposer) {
            quickAddMeal = smartMealForCurrentTime()
            onMealComposerOpened()
        }
    }
    val today = LocalDate.now()
    val logs = selectedLogs
    val canLog = selectedDate == today
    val history = remember(historyLogs, logs) { (historyLogs + logs).distinctBy { it.id } }
    val dailyCalories = remember(history) { history.groupBy { it.date.take(10) }.mapValues { (_, dayLogs) -> dayLogs.sumOf { it.calories } } }
    val favorites = data?.favoriteMeals.orEmpty()
    LaunchedEffect(selectedDate) { onLoadMonth(YearMonth.from(selectedDate)) }

    ScreenContainer(padding) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = if (canLog) 100.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { NutritionHeader(selectedDate, en) { showCalendar = true } }
            item { WeekStrip(selectedDate, dailyCalories, dateLoading, en, onSelectDate) }
            if (!canLog) item { PastDayBanner(selectedDate, en) { onSelectDate(today) } }
            item {
                DailySummaryCard(
                    logs = logs,
                    goal = data?.nutritionGoal ?: NutritionGoalData(),
                    trainingDay = isTrainingDay(data, selectedDate),
                    activeCalories = if (canLog) data?.activeCalories ?: 0 else 0,
                    loggedActivityCalories = if (canLog) data?.sessions.orEmpty().filter { s -> s.manualActivityKey != null && runCatching { java.time.Instant.parse(s.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == selectedDate }.getOrDefault(false) }.sumOf { it.calories } else 0,
                    waterMl = if (canLog) data?.waterMl else null,
                    waterGoalMl = waterGoalMl,
                    onAddWater = onAddWater,
                    onWaterGoalChange = onWaterGoalChange,
                    en = en,
                )
            }
            if (canLog && isTrainingDay(data, selectedDate)) item {
                ExpandableRow(
                    icon = Icons.Default.FitnessCenter,
                    tint = HedefitColors.Warning,
                    title = if (en) "Training day meals" else "Spor günü öğünleri",
                    subtitle = if (en) "Before and after training" else "Antrenman öncesi ve sonrası",
                    expanded = showTrainingMeals,
                    onToggle = { showTrainingMeals = !showTrainingMeals },
                ) { TrainingDayMealsCard(
                    en,
                    enabled = !busy,
                    workoutTime = data?.schedule?.firstOrNull { it.date.take(10) == selectedDate.toString() }?.time,
                    goalDirection = data?.let { d -> val now = d.measurements.lastOrNull()?.weightKg ?: d.profile.weightKg; val target = d.profile.targetWeightKg; if (now == null || target == null || kotlin.math.abs(target - now) < 0.5) 0 else if (target < now) -1 else 1 } ?: 0,
                    onAdd = onAddWithAi,
                    onAddWater = onAddWater,
                    onAskCoach = {
                    onAskCoach(if (en) "Today is a training day. Based on my goal and what I've eaten so far, suggest my pre- and post-workout meals with portions." else "Bugün spor günüm. Hedefime ve bugün yediklerime göre antrenman öncesi ve sonrası öğünlerimi porsiyonlarıyla önerir misin?")
                }) }
            }
            item {
                val weekStart = today.with(DayOfWeek.MONDAY)
                val plannedDays = data?.mealPlanItems.orEmpty().mapNotNull { runCatching { LocalDate.parse(it.plannedDate.take(10)) }.getOrNull() }.filter { it >= weekStart && it < weekStart.plusDays(7) }.distinct().size
                ExpandableRow(
                    icon = Icons.Default.CalendarMonth,
                    tint = HedefitColors.Sleep,
                    title = if (en) "This week's meal plan" else "Bu haftaki öğün planı",
                    subtitle = if (plannedDays == 0) (if (en) "Nothing planned yet" else "Henüz plan yok") else if (en) "$plannedDays days planned" else "$plannedDays gün planlandı",
                    expanded = showPlanner,
                    onToggle = { showPlanner = !showPlanner },
                ) { WeeklyMealPlanner(data?.mealPlanItems.orEmpty(), foodResults, foodSearchBusy, foodSearchQuery, busy, en, onSearchFoods, onAddMealPlanItem, onToggleMealPlanItem, onRemoveMealPlanItem) }
            }
            item {
                ExpandableRow(
                    icon = Icons.Default.Eco,
                    tint = HedefitColors.Lime,
                    title = if (en) "Fibre and micronutrients" else "Lif ve mikro besinler",
                    subtitle = if (en) "Fibre ${logs.sumOf { it.fiber }.toInt()} g • Sodium ${logs.sumOf { it.sodiumMg }.toInt()} mg" else "Lif ${logs.sumOf { it.fiber }.toInt()} g • Sodyum ${logs.sumOf { it.sodiumMg }.toInt()} mg",
                    expanded = showMicros,
                    onToggle = { showMicros = !showMicros },
                ) { MicroNutrientCard(logs, data?.profile, en) }
            }
            item {
                val mealCount = logs.map { it.meal }.distinct().size
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(if (en) "Meals" else "Öğünler", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(if (en) "$mealCount meals • ${logs.sumOf { it.calories }} kcal" else "$mealCount öğün • ${logs.sumOf { it.calories }} kcal", color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
            items(MEAL_TYPES) { type ->
                val entries = logs.filter { if (type == "Atıştırmalık") it.meal == type || it.meal !in MEAL_TYPES else it.meal == type }
                MealSection(type, entries, canLog, busy, en, onAdd = { quickAddMeal = type }, onFavorite = onAddFavorite, onRemove = onRemoveNutritionLog, onUpdate = onUpdateNutritionLog)
            }
            if (canLog && favorites.isNotEmpty()) item { FavoriteChips(favorites, busy, en, onRepeatFavorite, onRemoveFavorite) }
            if (canLog && !tipDismissed) item { NutritionTip(en) { tipDismissed = true } }
        }
        if (canLog) {
            FloatingActionButton(
                onClick = { quickAddMeal = smartMealForCurrentTime() },
                containerColor = HedefitColors.Lime,
                contentColor = HedefitColors.OnLime,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 20.dp).size(60.dp),
            ) { Icon(Icons.Default.Add, if (en) "Add food" else "Yiyecek ekle", Modifier.size(28.dp)) }
        }
    }

    quickAddMeal?.let { meal ->
        ModalBottomSheet(
            onDismissRequest = { quickAddMeal = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = HedefitColors.Surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (en) "What did you eat?" else "Ne yedin?", style = MaterialTheme.typography.headlineSmall)
                MealEntryCard(
                    en = en,
                    busy = busy,
                    enabled = canLog,
                    initialMeal = meal,
                    results = foodResults,
                    searching = foodSearchBusy,
                    searchedQuery = foodSearchQuery,
                    onSearch = onSearchFoods,
                    onOpenCatalog = { quickAddMeal = null; showFoodSearch = true },
                    onOpenPhoto = { quickAddMeal = null; showPhotoSource = true },
                    onAddWithAi = { food, grams, type -> onAddWithAi(food, grams, type); quickAddMeal = null },
                    onAddCatalog = { food, grams, type -> onAddCatalogFood(food, grams, type); quickAddMeal = null },
                    recentLogs = history,
                    favorites = favorites,
                )
            }
        }
    }
    if (showFoodSearch) FoodSearchDialog(foodResults, foodSearchBusy, foodSearchQuery, busy, en, onDismiss = { showFoodSearch = false }, onSearch = onSearchFoods, onAdd = { item, amount, type -> onAddCatalogFood(item, amount, type); showFoodSearch = false })
    if (showCalendar) NutritionCalendarDialog(selectedDate, history, en, dateLoading, onMonthShown = onLoadMonth, onDismiss = { showCalendar = false }, onSelect = { onSelectDate(it); showCalendar = false })
    if (showPhotoSource) AlertDialog(
        onDismissRequest = { showPhotoSource = false },
        title = { Text(if (en) "Analyze meal photo" else "Öğün fotoğrafını analiz et") },
        text = { Text(if (en) "Include the whole plate and, if possible, a fork or card for scale. Portions remain estimates." else "Tabağın tamamını ve mümkünse ölçek için çatal veya kartı kadraja al. Porsiyonlar yine tahminidir.") },
        confirmButton = { TextButton(onClick = {
            showPhotoSource = false
            val directory = File(context.cacheDir, "meal-photos").apply { mkdirs() }
            val file = File.createTempFile("meal-", ".jpg", directory)
            cameraPhotoFile = file
            val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            cameraPhotoUri = photoUri
            cameraLauncher.launch(photoUri)
        }) { Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(5.dp)); Text(if (en) "Camera" else "Kamera") } },
        dismissButton = { TextButton(onClick = { showPhotoSource = false; galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(5.dp)); Text(if (en) "Gallery" else "Galeri") } },
    )
    if (photoBusy) AlertDialog(onDismissRequest = {}, title = { Text(if (en) "Analyzing meal…" else "Öğün analiz ediliyor…") }, text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(color = HedefitColors.Lime); Text(if (en) "Foods, portions and nutrients are being estimated." else "Besinler, porsiyonlar ve değerler tahmin ediliyor.") } }, confirmButton = {})
    if (photoResults.isNotEmpty()) PhotoNutritionReviewDialog(photoResults, busy, en, onClearPhotoResults, onSavePhotoResults, fromText = reviewFromText, initialMeal = reviewMeal)
}

private val MEAL_TYPES = listOf("Kahvaltı", "Öğle yemeği", "Akşam yemeği", "Atıştırmalık")

@Composable
private fun NutritionHeader(date: LocalDate, en: Boolean, onOpenCalendar: () -> Unit) {
    val locale = Locale(if (en) "en" else "tr")
    val formatted = date.format(DateTimeFormatter.ofPattern("d MMMM EEEE", locale))
    val prefix = when (date) {
        LocalDate.now() -> if (en) "Today" else "Bugün"
        LocalDate.now().minusDays(1) -> if (en) "Yesterday" else "Dün"
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (en) "Nutrition" else "Beslenme", style = MaterialTheme.typography.headlineMedium)
            Text(if (prefix != null) "$prefix • $formatted" else formatted, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onOpenCalendar, modifier = Modifier.background(HedefitColors.SurfaceHigh, CircleShape)) {
            Icon(Icons.Default.CalendarMonth, if (en) "Open calorie calendar" else "Kalori takvimini aç", tint = HedefitColors.TextPrimary)
        }
    }
}

@Composable
private fun WeekStrip(selectedDate: LocalDate, dailyCalories: Map<String, Int>, loading: Boolean, en: Boolean, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val weekStart = selectedDate.with(DayOfWeek.MONDAY)
    val locale = Locale(if (en) "en" else "tr")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(selectedDate.minusWeeks(1)) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, if (en) "Previous week" else "Önceki hafta", tint = HedefitColors.TextSecondary)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0L..6L).forEach { offset ->
                    val day = weekStart.plusDays(offset)
                    val selected = day == selectedDate
                    val future = day > today
                    val kcal = dailyCalories[day.toString()]
                    Column(
                        Modifier.weight(1f)
                            .background(if (selected) HedefitColors.Lime else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable(enabled = !future) { onSelect(day) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        val color = when {
                            selected -> HedefitColors.OnLime
                            future -> HedefitColors.TextMuted.copy(alpha = .5f)
                            day == today -> HedefitColors.Lime
                            else -> HedefitColors.TextSecondary
                        }
                        Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale).take(3), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(day.dayOfMonth.toString(), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold)
                        Box(Modifier.size(5.dp).background(if (kcal != null && kcal > 0) (if (selected) HedefitColors.OnLime else HedefitColors.Lime) else Color.Transparent, CircleShape))
                    }
                }
            }
            IconButton(enabled = weekStart.plusWeeks(1) <= today, onClick = { onSelect(minOf(selectedDate.plusWeeks(1), today)) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, if (en) "Next week" else "Sonraki hafta", tint = HedefitColors.TextSecondary)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = HedefitColors.Lime, trackColor = HedefitColors.Divider)
    }
}

@Composable
private fun PastDayBanner(date: LocalDate, en: Boolean, onBackToToday: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(HedefitColors.Water.copy(alpha = .12f), RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (en) "Viewing ${date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))} • read-only" else "${date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("tr")))} kaydı • sadece görüntüleme",
            color = HedefitColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onBackToToday) { Text(if (en) "Back to today" else "Bugüne dön", color = HedefitColors.Water, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun DailySummaryCard(
    logs: List<NutritionLogData>,
    goal: NutritionGoalData,
    trainingDay: Boolean,
    activeCalories: Int,
    waterMl: Int?,
    /** Bugün kaydedilen kardiyo ve manuel aktivitelerin kalorisi; spor günü bonusunun üstüne eklenir. */
    loggedActivityCalories: Int = 0,
    waterGoalMl: Int,
    onAddWater: (Int) -> Unit,
    onWaterGoalChange: (Int) -> Unit,
    en: Boolean,
) {
    val consumed = logs.sumOf { it.calories }
    val protein = logs.sumOf { it.protein }
    val carbs = logs.sumOf { it.carbs }
    val fat = logs.sumOf { it.fat }
    val activityBonus = activeCalories.coerceIn(0, 600)
    val activityExtra = loggedActivityCalories.coerceIn(0, 600)
    val bonus = (maxOf(activityBonus, if (trainingDay) TRAINING_DAY_KCAL else 0) + activityExtra).coerceAtMost(800)
    val target = (goal.calories + bonus).coerceAtLeast(1)
    val over = consumed > target
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ProgressRing(consumed / target.toFloat(), Modifier.size(104.dp), 10.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$consumed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text("/ $target", color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (en) "DAILY CALORIES" else "GÜNLÜK KALORİ", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    val accent = if (over) HedefitColors.Coral else HedefitColors.Lime
                    Text(
                        if (!over) (if (en) "${target - consumed} kcal left" else "${target - consumed} kcal kaldı") else (if (en) "${consumed - target} kcal over" else "${consumed - target} kcal aştın"),
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.background(accent.copy(alpha = .15f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    if (trainingDay) HfPill(if (en) "Training day +${bonus - activityExtra} kcal" else "Spor günü +${bonus - activityExtra} kcal", HedefitColors.Warning)
                    if (activityExtra > 0) HfPill(if (en) "Cardio & activity +$activityExtra kcal" else "Kardiyo/aktivite +$activityExtra kcal", HedefitColors.Coral)
                    else if (bonus > 0) HfPill(if (en) "Activity +$bonus kcal" else "Aktivite +$bonus kcal", HedefitColors.Water)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(HedefitColors.Divider))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroColumn("Protein", protein, goal.protein + if (trainingDay) 20 else 0, HedefitColors.Lime, Modifier.weight(1f))
                MacroColumn(if (en) "Carbs" else "Karb.", carbs, goal.carbs + if (trainingDay) 40 else 0, HedefitColors.Water, Modifier.weight(1f))
                MacroColumn(if (en) "Fat" else "Yağ", fat, goal.fat, HedefitColors.Warning, Modifier.weight(1f))
            }
            if (waterMl != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(HedefitColors.Divider))
                WaterRow(waterMl, waterGoalMl, onAddWater, onWaterGoalChange, en)
            }
        }
    }
}

@Composable
private fun MacroColumn(label: String, value: Double, goal: Int, color: Color, modifier: Modifier) {
    val progress = (value / goal.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Text("${value.toInt()} / ${goal}g", style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth().height(6.dp).background(HedefitColors.SurfaceSoft, CircleShape)) {
            Box(Modifier.fillMaxWidth(progress).height(6.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun WaterRow(currentMl: Int, goalMl: Int, onAdd: (Int) -> Unit, onGoalChange: (Int) -> Unit, en: Boolean) {
    var showGoal by remember { mutableStateOf(false) }
    val glasses = (goalMl / 250).coerceIn(1, 12)
    val filled = (currentMl / 250).coerceIn(0, glasses)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.WaterDrop, null, tint = HedefitColors.Water, modifier = Modifier.size(18.dp))
        Row(Modifier.weight(1f).clickable { showGoal = true }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(glasses) { index -> Box(Modifier.weight(1f).height(16.dp).background(if (index < filled) HedefitColors.Water else HedefitColors.SurfaceSoft, RoundedCornerShape(4.dp))) }
        }
        Text("${currentMl}/${goalMl} ml", color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = { onAdd(250) }, modifier = Modifier.size(36.dp).background(HedefitColors.Water.copy(alpha = .16f), CircleShape)) {
            Icon(Icons.Default.Add, if (en) "Add 250 ml water" else "250 ml su ekle", tint = HedefitColors.Water, modifier = Modifier.size(18.dp))
        }
    }
    if (showGoal) AlertDialog(
        onDismissRequest = { showGoal = false },
        title = { Text(if (en) "Water" else "Su") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (en) "Quick add" else "Hızlı ekle", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(200, 330, 500).forEach { amount -> FilterChip(false, { onAdd(amount) }, label = { Text("+$amount ml") }) }
                }
                Text(if (en) "Daily goal: $goalMl ml" else "Günlük hedef: $goalMl ml", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(false, { onGoalChange((goalMl - 250).coerceAtLeast(500)) }, label = { Text("−250") })
                    FilterChip(false, { onGoalChange((goalMl + 250).coerceAtMost(10_000)) }, label = { Text("+250") })
                }
            }
        },
        confirmButton = { TextButton(onClick = { showGoal = false }) { Text(if (en) "Done" else "Tamam") } },
    )
}

@Composable
private fun ExpandableRow(icon: ImageVector, tint: Color, title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HedefitCard(Modifier.fillMaxWidth(), onClick = onToggle, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(tint.copy(alpha = .15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) com.hedefit.app.ui.i18n.tr("Daralt", "Collapse") else com.hedefit.app.ui.i18n.tr("Genişlet", "Expand"), tint = HedefitColors.TextMuted)
            }
        }
        if (expanded) content()
    }
}

@Composable
private fun MealSection(
    type: String,
    entries: List<NutritionLogData>,
    canLog: Boolean,
    busy: Boolean,
    en: Boolean,
    onAdd: () -> Unit,
    onFavorite: (NutritionLogData) -> Unit,
    onRemove: (NutritionLogData) -> Unit,
    onUpdate: (NutritionLogData, Double, String) -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<NutritionLogData?>(null) }
    var editing by remember { mutableStateOf<NutritionLogData?>(null) }
    val (icon, tint) = mealStyle(type)
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 8.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(tint.copy(alpha = .15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(mealLabel(type, en), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (entries.isEmpty()) (if (en) "No food yet" else "Henüz eklenmedi") else if (en) "${entries.size} foods" else "${entries.size} besin", color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text("${entries.sumOf { it.calories }} kcal", color = if (entries.isEmpty()) HedefitColors.TextMuted else HedefitColors.TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                if (canLog) {
                    IconButton(enabled = !busy, onClick = onAdd) {
                        Box(Modifier.size(32.dp).background(HedefitColors.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, if (en) "Add to ${mealLabel(type, true)}" else "${mealLabel(type, false)} öğününe ekle", tint = HedefitColors.TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                } else Spacer(Modifier.width(10.dp))
            }
            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().padding(end = 8.dp).height(1.dp).background(HedefitColors.Divider))
                entries.forEach { log ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(log.name, fontWeight = FontWeight.SemiBold)
                            Text("${log.grams?.cleanNumber() ?: "—"} g • P ${log.protein.toInt()} • K ${log.carbs.toInt()} • Y ${log.fat.toInt()}", color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${log.calories} kcal", color = HedefitColors.TextSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        LogMenu(log, canLog, busy, en, onEdit = { editing = log }, onFavorite = { onFavorite(log) }, onRemove = { pendingRemoval = log })
                    }
                }
                if (entries.size > 1) {
                    TextButton(enabled = !busy, onClick = { onFavorite(entries.asSavedMeal(type)) }) {
                        Icon(Icons.Default.Favorite, null, tint = HedefitColors.Coral, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (en) "Save as a meal" else "Öğün olarak kaydet", color = HedefitColors.Coral, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (canLog) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp)
                        .border(1.dp, HedefitColors.Divider, RoundedCornerShape(14.dp))
                        .clickable(enabled = !busy, onClick = onAdd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, null, tint = HedefitColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (en) "Tap to add" else "Eklemek için dokun", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    pendingRemoval?.let { log ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRemoval = null },
            title = { Text(if (en) "Remove food?" else "Besin kaldırılsın mı?") },
            text = { Text(if (en) "${log.name} will be removed from ${mealLabel(log.meal, true).lowercase()}." else "${log.name}, ${mealLabel(log.meal, false).lowercase()} öğününden kaldırılacak.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { pendingRemoval = null; onRemove(log) }) { Text(if (en) "Remove" else "Kaldır", color = HedefitColors.Coral, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingRemoval = null }) { Text(if (en) "Cancel" else "Vazgeç") } },
        )
    }
    editing?.let { log -> EditMealLogDialog(log, busy, en, onDismiss = { editing = null }, onSave = { grams, meal -> editing = null; onUpdate(log, grams, meal) }) }
}

@Composable
private fun LogMenu(log: NutritionLogData, canEdit: Boolean, busy: Boolean, en: Boolean, onEdit: () -> Unit, onFavorite: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, if (en) "Options for ${log.name}" else "${log.name} seçenekleri", tint = HedefitColors.TextMuted, modifier = Modifier.size(20.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canEdit) DropdownMenuItem(enabled = !busy, text = { Text(if (en) "Edit or move" else "Düzenle / taşı") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text(if (en) "Add to favourites" else "Favorilere ekle") }, leadingIcon = { Icon(Icons.Default.Favorite, null, tint = HedefitColors.Coral) }, onClick = { open = false; onFavorite() })
            if (canEdit) DropdownMenuItem(enabled = !busy, text = { Text(if (en) "Remove" else "Kaldır", color = HedefitColors.Coral) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = HedefitColors.Coral) }, onClick = { open = false; onRemove() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteChips(favorites: List<FavoriteMealData>, busy: Boolean, en: Boolean, onRepeat: (FavoriteMealData) -> Unit, onRemove: (String) -> Unit) {
    var pendingRemoval by remember { mutableStateOf<FavoriteMealData?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (en) "Favourites" else "Sık kullanılanlar", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favorites, key = { it.id }) { favorite ->
                Row(
                    Modifier.background(HedefitColors.Surface, CircleShape).border(.6.dp, HedefitColors.Divider, CircleShape)
                        .combinedClickable(onClick = {}, onLongClick = { pendingRemoval = favorite })
                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(favorite.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text("${favorite.calories} kcal", color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(enabled = !busy, onClick = { onRepeat(favorite) }) {
                        Box(Modifier.size(28.dp).background(HedefitColors.Lime.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, if (en) "Add ${favorite.name}" else "${favorite.name} ekle", tint = HedefitColors.Lime, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
    pendingRemoval?.let { favorite ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(if (en) "Remove favourite?" else "Favoriden kaldırılsın mı?") },
            text = { Text(favorite.name) },
            confirmButton = { TextButton(onClick = { pendingRemoval = null; onRemove(favorite.id) }) { Text(if (en) "Remove" else "Kaldır", color = HedefitColors.Coral) } },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text(if (en) "Cancel" else "Vazgeç") } },
        )
    }
}

@Composable
private fun NutritionTip(en: Boolean, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(HedefitColors.Lime.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = HedefitColors.Lime, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (en) "To reach your protein target, choose yoghurt, eggs, or lean meat in your next meal." else "Protein hedefin için sonraki öğününde yoğurt, yumurta veya yağsız et tercih edebilirsin.",
            color = HedefitColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, if (en) "Dismiss tip" else "Öneriyi kapat", tint = HedefitColors.TextMuted, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun MealEntryCard(
    en: Boolean,
    busy: Boolean,
    enabled: Boolean,
    initialMeal: String,
    results: List<FoodSearchData>,
    searching: Boolean,
    searchedQuery: String?,
    onSearch: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenPhoto: () -> Unit,
    onAddWithAi: (String, Double, String) -> Unit,
    onAddCatalog: (FoodSearchData, Double, String) -> Unit,
    recentLogs: List<NutritionLogData>,
    favorites: List<FavoriteMealData>,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("100") }
    var unit by remember { mutableStateOf("g") }
    var selectedFood by remember { mutableStateOf<FoodSearchData?>(null) }
    var meal by remember(initialMeal) { mutableStateOf(initialMeal) }
    var showRecipe by remember { mutableStateOf(false) }
    val numericAmount = amount.replace(',', '.').toDoubleOrNull()
    val unitGrams = if (unit == "adet") selectedFood?.servingGrams?.coerceAtLeast(1.0) ?: 100.0 else 1.0
    val grams = numericAmount?.times(unitGrams)
    val cleanName = name.trim()
    val suggestions = if (cleanName.length >= 2 && searchedQuery == cleanName) results.take(8) else emptyList()

    LaunchedEffect(cleanName, searching, searchedQuery) {
        if (cleanName.length >= 2 && !searching && cleanName != searchedQuery) {
            delay(300)
            onSearch(cleanName)
        }
    }
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(HedefitColors.Lime.copy(alpha = .14f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Restaurant, null, tint = HedefitColors.Lime)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (en) "Smart meal entry" else "Akıllı öğün ekle", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(enabled = enabled, onClick = onOpenCatalog, modifier = Modifier.background(HedefitColors.SurfaceHigh, CircleShape)) {
                    Icon(Icons.Default.MenuBook, if (en) "Food catalogue" else "Besin kataloğu", tint = HedefitColors.Lime)
                }
                Spacer(Modifier.width(6.dp))
                IconButton(enabled = enabled && !busy, onClick = onOpenPhoto, modifier = Modifier.background(HedefitColors.Lime.copy(alpha = .16f), CircleShape)) {
                    Icon(Icons.Default.AddAPhoto, if (en) "Analyze meal photo" else "Fotoğrafla öğün analiz et", tint = HedefitColors.Lime)
                }
                Spacer(Modifier.width(6.dp))
                IconButton(enabled = enabled, onClick = { showRecipe = true }, modifier = Modifier.background(HedefitColors.SurfaceHigh, CircleShape)) {
                    Icon(Icons.Default.SoupKitchen, if (en) "Add recipe" else "Tarif ekle", tint = HedefitColors.Warning)
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(300)
                    if (selectedFood?.name != name) selectedFood = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (en) "Food or whole meal: omelette, 2 slices bread…" else "Besin ya da öğün: omlet, 2 dilim ekmek…") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = HedefitColors.TextSecondary) },
                trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = HedefitColors.Lime) },
                maxLines = 3,
                colors = nutritionFieldColors(),
            )
            val wholeMeal = selectedFood == null && com.hedefit.app.ui.state.looksLikeWholeMeal(cleanName)
            if (suggestions.isNotEmpty() && !wholeMeal) {
                Column(Modifier.fillMaxWidth().background(HedefitColors.SurfaceHigh, RoundedCornerShape(14.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    suggestions.forEach { food ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selectedFood = food
                                name = food.name
                                unit = "g"
                                amount = food.servingGrams.coerceAtLeast(1.0).cleanNumber()
                            }.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val portion = food.servingGrams.coerceAtLeast(1.0)
                            val portionKcal = (food.calories * portion / 100.0).toInt()
                            Column(Modifier.weight(1f)) {
                                Text(food.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${portion.cleanNumber()} g • $portionKcal kcal • P ${"%.0f".format(food.protein * portion / 100)} K ${"%.0f".format(food.carbs * portion / 100)} Y ${"%.0f".format(food.fat * portion / 100)}",
                                    color = HedefitColors.TextPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            if (selectedFood?.id == food.id) Icon(Icons.Default.CheckCircle, null, tint = HedefitColors.Lime, modifier = Modifier.size(20.dp))
                            else if (food.verified) Icon(Icons.Default.Verified, null, tint = HedefitColors.Lime, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            val recent = recentLogs.sortedByDescending { it.date }.distinctBy { it.name.lowercase(Locale("tr")) }.take(3)
            if (cleanName.length < 2 && (recent.isNotEmpty() || favorites.isNotEmpty())) {
                Column(Modifier.fillMaxWidth().background(HedefitColors.SurfaceHigh, RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (recent.isNotEmpty()) {
                        Text(if (en) "Recent" else "Son eklenenler", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(recent) { log ->
                                FilterChip(selected = false, onClick = {
                                    name = log.name; amount = (log.grams ?: 100.0).cleanNumber(); unit = "g"
                                }, label = { Text(log.name) })
                            }
                        }
                    }
                    if (favorites.isNotEmpty()) {
                        Text(if (en) "Favourites" else "Favoriler", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(favorites.take(4)) { favorite ->
                                FilterChip(selected = false, onClick = {
                                    name = favorite.name; amount = favorite.grams.cleanNumber(); unit = "g"
                                }, label = { Text("♥ ${favorite.name}") })
                            }
                        }
                    }
                }
            }
            if (!wholeMeal) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("g", "adet")) { value ->
                    FilterChip(
                        selected = unit == value,
                        onClick = { unit = value; amount = if (value == "adet") "1" else "100" },
                        label = { Text(if (en && value == "adet") "piece" else value) },
                    )
                }
            }
            if (!wholeMeal) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { amount = ((numericAmount ?: 1.0) - amountStep(unit)).coerceAtLeast(amountStep(unit)).cleanNumber() }, modifier = Modifier.background(HedefitColors.SurfaceHigh, CircleShape)) { Icon(Icons.Default.Remove, null) }
                OutlinedTextField(
                    amount,
                    { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) },
                    Modifier.weight(1f),
                    label = { Text(if (unit == "adet") (if (en) "Count" else "Adet") else if (en) "Grams" else "Gramaj") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = nutritionFieldColors(),
                )
                IconButton(onClick = { amount = ((numericAmount ?: 0.0) + amountStep(unit)).cleanNumber() }, modifier = Modifier.background(HedefitColors.Lime, CircleShape)) { Icon(Icons.Default.Add, null, tint = HedefitColors.OnLime) }
            }
            if (!wholeMeal) grams?.takeIf { it > 0 }?.let { totalGrams ->
                val ratio = totalGrams / 100.0
                Column(Modifier.fillMaxWidth().background(HedefitColors.Lime.copy(alpha = .08f), RoundedCornerShape(13.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when (unit) {
                            "adet" -> if (en) "${amount} piece = ${totalGrams.cleanNumber()} g" else "${amount} adet = ${totalGrams.cleanNumber()} g"
                            else -> "${totalGrams.cleanNumber()} g"
                        },
                        color = HedefitColors.Lime,
                        fontWeight = FontWeight.Bold,
                    )
                    selectedFood?.let { food ->
                        Text(food.name, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "${(food.calories * ratio).toInt()} kcal • ${if (en) "P" else "Protein"} ${"%.1f".format(food.protein * ratio)} g • ${if (en) "C" else "Karb"} ${"%.1f".format(food.carbs * ratio)} g • ${if (en) "F" else "Yağ"} ${"%.1f".format(food.fat * ratio)} g",
                            color = HedefitColors.Lime,
                            fontWeight = FontWeight.Bold,
                        )
                    } ?: Text(
                        if (suggestions.isNotEmpty()) (if (en) "Pick the exact item above" else "Yukarıdan tam olarak ne yediğini seç")
                        else if (en) "\"$cleanName\" will be estimated by AI" else "\"$cleanName\" yapay zekâ ile hesaplanacak",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("Kahvaltı", "Öğle yemeği", "Akşam yemeği", "Atıştırmalık")) { type -> FilterChip(meal == type, { meal = type }, label = { Text(mealLabel(type, en)) }) }
            }
            Button(
                enabled = enabled && !busy && cleanName.length >= 2 && (wholeMeal || grams != null && grams > 0),
                onClick = {
                    if (wholeMeal) {
                        onAddWithAi(cleanName, 100.0, meal)
                        name = ""
                        return@Button
                    }
                    grams?.let { total ->
                        // Search suggestions are never silently accepted: the user must tap one.
                        // Otherwise the full phrase goes to the server, where exact catalogue
                        // matches are used and compound dishes are analysed by AI.
                        selectedFood?.takeIf { it.name == cleanName }?.let { onAddCatalog(it, total, meal) }
                            ?: onAddWithAi(cleanName, total, meal)
                        name = ""
                        selectedFood = null
                        unit = "g"
                        amount = "100"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) {
                Text(when {
                    !enabled -> if (en) "Past day" else "Geçmiş gün"
                    busy -> if (en) "Adding…" else "Ekleniyor…"
                    wholeMeal -> if (en) "Split into foods and review" else "Besinlere ayır ve kontrol et"
                    selectedFood == null && cleanName.length >= 2 -> if (en) "Add \"$cleanName\" to ${mealLabel(meal, true)}" else "\"$cleanName\" → ${mealLabel(meal, false)}"
                    en -> "Add to ${mealLabel(meal, true)}"
                    else -> "${mealLabel(meal, false)} öğününe ekle"
                }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
    if (showRecipe) RecipeComposerDialog(en, busy, meal, onDismiss = { showRecipe = false }) { recipe, grams, selectedMeal ->
        showRecipe = false
        meal = selectedMeal
        onAddWithAi(recipe, grams, selectedMeal)
    }
}

private fun smartMealForCurrentTime(): String = when (LocalTime.now().hour) {
    in 4..10 -> "Kahvaltı"
    in 11..14 -> "Öğle yemeği"
    in 15..17 -> "Atıştırmalık"
    else -> "Akşam yemeği"
}

private fun smartFoodUnit(name: String): String {
    val clean = name.lowercase(Locale("tr"))
    return when {
        listOf("su", "süt", "ayran", "kefir", "meyve suyu", "kahve", "çay", "çorba").any(clean::contains) -> "ml"
        listOf("yumurta", "elma", "muz", "portakal", "mandalina", "simit", "dilim", "bar").any(clean::contains) -> "adet"
        listOf("pilav", "makarna", "salata", "yemek", "tabak", "kase", "menü").any(clean::contains) -> "porsiyon"
        else -> "g"
    }
}

private data class ParsedFoodEntry(val name: String, val amount: Double? = null, val unit: String? = null)

/** Understands short Turkish entries such as "2 yumurta" and "250 ml ayran" before catalogue search. */
private fun parseNaturalFoodEntry(value: String): ParsedFoodEntry {
    val match = Regex("^\\s*(\\d+(?:[.,]\\d+)?)\\s*(g|gr|gram|ml|adet|tane|porsiyon|portion)?\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(value.trim())
        ?: return ParsedFoodEntry(value)
    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return ParsedFoodEntry(value)
    val food = match.groupValues[3].trim()
    if (food.length < 2) return ParsedFoodEntry(value)
    val explicitUnit = when (match.groupValues[2].lowercase(Locale("tr"))) {
        "g", "gr", "gram" -> "g"
        "ml" -> "ml"
        "adet", "tane" -> "adet"
        "porsiyon", "portion" -> "porsiyon"
        else -> smartFoodUnit(food)
    }
    return ParsedFoodEntry(food, amount, explicitUnit)
}

private fun defaultAmountForUnit(unit: String) = if (unit == "adet" || unit == "porsiyon") "1" else "100"
private fun amountStep(unit: String) = if (unit == "adet" || unit == "porsiyon") 1.0 else 10.0
private fun Double.cleanNumber(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(Locale.US, this)

@Composable
private fun RecipeComposerDialog(en: Boolean, busy: Boolean, meal: String, onDismiss: () -> Unit, onAnalyze: (String, Double, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("1") }
    var eatenServings by remember { mutableStateOf("1") }
    var selectedMeal by remember(meal) { mutableStateOf(meal) }
    val totalGrams = grams.replace(',', '.').toDoubleOrNull()
    val totalServings = servings.toIntOrNull()
    val eaten = eatenServings.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (en) "Add recipe" else "Tarif ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (en) "Write the ingredients and amounts. Hedefit estimates the recipe's calories, macros, and micronutrients for the total portion." else "Malzemeleri ve miktarlarını yaz. Hedefit, tarifin toplam porsiyonu için kalori, makro ve mikro besin tahmini çıkarır.", color = HedefitColors.TextSecondary)
                OutlinedTextField(title, { title = it.take(80) }, label = { Text(if (en) "Recipe name" else "Tarif adı") }, placeholder = { Text(if (en) "e.g. chicken pasta" else "örn. tavuklu makarna") }, singleLine = true)
                OutlinedTextField(ingredients, { ingredients = it.take(850) }, label = { Text(if (en) "Ingredients and amounts" else "Malzemeler ve miktarları") }, placeholder = { Text(if (en) "150 g chicken, 80 g pasta, 1 tsp olive oil" else "150 g tavuk, 80 g makarna, 1 çay kaşığı zeytinyağı") }, minLines = 4, maxLines = 6)
                OutlinedTextField(grams, { grams = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) }, label = { Text(if (en) "Whole recipe weight (g)" else "Tarifin toplam ağırlığı (g)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(servings, { servings = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text(if (en) "Recipe servings" else "Tarif porsiyonu") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(eatenServings, { eatenServings = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(4) }, Modifier.weight(1f), label = { Text(if (en) "You ate" else "Yediğin porsiyon") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Text(if (en) "Save to meal" else "Kaydedilecek öğün", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Kahvaltı", "Öğle yemeği", "Akşam yemeği", "Atıştırmalık")) { type ->
                        FilterChip(selected = selectedMeal == type, onClick = { selectedMeal = type }, label = { Text(mealLabel(type, en)) })
                    }
                }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = {
            Button(
                enabled = !busy && title.trim().length >= 2 && ingredients.trim().length >= 4 && totalGrams != null && totalGrams in 1.0..5000.0 && totalServings != null && totalServings in 1..30 && eaten != null && eaten > 0 && eaten <= totalServings,
                onClick = { onAnalyze("${title.trim()}: ${ingredients.trim()}. Toplam tarif: ${totalGrams!!.cleanNumber()} g, $totalServings porsiyon. Kaydedilen miktar: ${eaten!!.cleanNumber()} porsiyon.", totalGrams * eaten / totalServings!!, selectedMeal) },
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) { Text(if (busy) (if (en) "Saving…" else "Kaydediliyor…") else if (en) "Calculate and save" else "Hesapla ve öğüne kaydet") }
        },
    )
}

/** A saved combination is kept as one reusable, fully calculated meal to make repeat logging one touch. */
private fun List<NutritionLogData>.asSavedMeal(meal: String): NutritionLogData = NutritionLogData(
    id = "saved-$meal",
    date = LocalDate.now().toString(),
    meal = meal,
    name = "$meal paketi",
    calories = sumOf { it.calories },
    protein = sumOf { it.protein },
    carbs = sumOf { it.carbs },
    fat = sumOf { it.fat },
    grams = sumOf { it.grams ?: 0.0 }.takeIf { it > 0 },
    fiber = sumOf { it.fiber },
    sugar = sumOf { it.sugar },
    sodiumMg = sumOf { it.sodiumMg },
    potassiumMg = sumOf { it.potassiumMg },
    calciumMg = sumOf { it.calciumMg },
    ironMg = sumOf { it.ironMg },
    vitaminCMg = sumOf { it.vitaminCMg },
)

@Composable
private fun EditMealLogDialog(log: NutritionLogData, busy: Boolean, en: Boolean, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var amount by remember(log.id) { mutableStateOf((log.grams ?: 100.0).cleanNumber()) }
    var meal by remember(log.id) { mutableStateOf(log.meal) }
    val grams = amount.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (en) "Edit food" else "Besini düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(log.name, fontWeight = FontWeight.Bold)
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) }, label = { Text(if (en) "Amount (g)" else "Miktar (g)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = nutritionFieldColors())
                Text(if (en) "Move to meal" else "Öğün değiştir", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Kahvaltı", "Öğle yemeği", "Akşam yemeği", "Atıştırmalık")) { type ->
                        FilterChip(selected = meal == type, onClick = { meal = type }, label = { Text(mealLabel(type, en)) })
                    }
                }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = { Button(enabled = !busy && grams != null && grams in 1.0..5000.0, onClick = { onSave(grams!!, meal) }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(if (en) "Save" else "Kaydet") } },
    )
}

private fun mealStyle(meal: String): Pair<ImageVector, Color> = when (meal) {
    "Kahvaltı" -> Icons.Default.EggAlt to HedefitColors.Warning
    "Öğle yemeği" -> Icons.Default.RamenDining to HedefitColors.Lime
    "Akşam yemeği" -> Icons.Default.LocalDining to HedefitColors.Coral
    else -> Icons.Default.Restaurant to HedefitColors.Sleep
}

private fun mealLabel(meal: String, en: Boolean) = if (!en) meal else when (meal) {
    "Kahvaltı" -> "Breakfast"
    "Öğle yemeği" -> "Lunch"
    "Akşam yemeği" -> "Dinner"
    else -> "Snack"
}

@Composable
private fun nutritionFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HedefitColors.Lime,
    unfocusedBorderColor = HedefitColors.Divider,
    focusedContainerColor = HedefitColors.Surface,
    unfocusedContainerColor = HedefitColors.Surface,
)

@Composable
private fun NutritionCalendarDialog(
    selectedDate: LocalDate,
    history: List<NutritionLogData>,
    en: Boolean,
    loading: Boolean,
    onMonthShown: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    LaunchedEffect(month) { onMonthShown(month) }
    val calories = history.groupBy { runCatching { LocalDate.parse(it.date.take(10)) }.getOrNull() }
        .filterKeys { it != null }.mapKeys { it.key!! }.mapValues { (_, logs) -> logs.sumOf { it.calories } }
    val firstOffset = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val cells = List(firstOffset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Calorie calendar" else "Kalori takvimi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (en) "Previous month" else "Önceki ay") }
                    Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale(if (en) "en" else "tr"))), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    IconButton(enabled = month < YearMonth.now(), onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, if (en) "Next month" else "Sonraki ay") }
                }
                Row(Modifier.fillMaxWidth()) {
                    (if (en) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("P", "S", "Ç", "P", "C", "C", "P")).forEach { day -> Text(day, Modifier.weight(1f), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelSmall) }
                }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) Box(Modifier.weight(1f).height(48.dp))
                            else {
                                val total = calories[date]
                                val active = date == selectedDate
                                Column(
                                    Modifier.weight(1f).height(48.dp).padding(2.dp)
                                        .background(if (active) HedefitColors.Lime else HedefitColors.SurfaceHigh, RoundedCornerShape(10.dp))
                                        .clickable(enabled = date <= LocalDate.now()) { onSelect(date) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(date.dayOfMonth.toString(), color = if (active) HedefitColors.OnLime else HedefitColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
                                    if (total != null) Text(if (total >= 1_000) "${total / 1000}k" else total.toString(), color = if (active) HedefitColors.OnLime else HedefitColors.Lime, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        repeat(7 - week.size) { Box(Modifier.weight(1f).height(48.dp)) }
                    }
                }
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = HedefitColors.Lime)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (en) "Close" else "Kapat") } },
    )
}

@Composable
private fun MicroNutrientCard(logs: List<NutritionLogData>, profile: ProfileData?, en: Boolean) {
    val fiber = logs.sumOf { it.fiber }
    val sugar = logs.sumOf { it.sugar }
    val sodium = logs.sumOf { it.sodiumMg }
    val potassium = logs.sumOf { it.potassiumMg }
    val calcium = logs.sumOf { it.calciumMg }
    val iron = logs.sumOf { it.ironMg }
    val vitaminC = logs.sumOf { it.vitaminCMg }
    val age = profile?.age ?: 30
    val normalizedGender = profile?.gender.orEmpty().lowercase(Locale("tr"))
    val female = listOf("kadın", "kadin", "female", "woman").any(normalizedGender::contains)
    val male = listOf("erkek", "male", "man").any(normalizedGender::contains)
    val fiberTarget = when { age > 50 && !male -> 21; age > 50 -> 30; male -> 38; else -> 25 }
    val potassiumTarget = if (male) 3400 else 2600
    val calciumTarget = if (age >= 71 || (female && age >= 51)) 1200 else 1000
    val ironTarget = if (female && age in 19..50) 18 else 8
    val vitaminCTarget = if (male) 90 else 75
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(if (en) "Fibre and micronutrients" else "Lif ve mikro besinler")
            MacroBar(if (en) "Fibre" else "Lif", "${fiber.toInt()} / $fiberTarget g", (fiber / fiberTarget).toFloat())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MicroValue(if (en) "Total sugar (not a target)" else "Toplam şeker (hedef değil)", "${sugar.toInt()} g", Modifier.weight(1f))
                MicroValue(if (en) "Sodium limit" else "Sodyum sınırı", "${sodium.toInt()} / ≤2300 mg", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MicroValue(if (en) "Potassium" else "Potasyum", "${potassium.toInt()} / $potassiumTarget mg", Modifier.weight(1f))
                MicroValue(if (en) "Calcium" else "Kalsiyum", "${calcium.toInt()} / $calciumTarget mg", Modifier.weight(1f))
                MicroValue(if (en) "Iron" else "Demir", "%.1f / %d mg".format(iron, ironTarget), Modifier.weight(1f))
            }
            MicroValue(if (en) "Vitamin C" else "C Vitamini", "${vitaminC.toInt()} / $vitaminCTarget mg", Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun MicroValue(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(HedefitColors.SurfaceHigh, RoundedCornerShape(11.dp)).padding(9.dp)) { Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall); Text(value, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun FoodSearchDialog(results: List<FoodSearchData>, searching: Boolean, searchedQuery: String?, adding: Boolean, en: Boolean, onDismiss: () -> Unit, onSearch: (String) -> Unit, onAdd: (FoodSearchData, Double, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FoodSearchData?>(null) }
    var grams by remember { mutableStateOf("100") }
    var addAttempted by remember { mutableStateOf(false) }
    var meal by remember { mutableStateOf("Atıştırmalık") }
    LaunchedEffect(query, searching, searchedQuery) {
        val clean = query.trim()
        if (clean.length >= 2 && !searching && clean != searchedQuery) {
            delay(350)
            onSearch(clean)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Verified food catalogue" else "Doğrulanmış besin kataloğu") },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                query,
                { query = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (en) "Food or brand" else "Besin veya marka") },
                trailingIcon = { IconButton(onClick = { onSearch(query.trim()) }) { Icon(Icons.Default.Search, if (en) "Search" else "Ara") } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.trim().length >= 2) onSearch(query.trim()) }),
            )
            if (searching) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = HedefitColors.Lime)
            if (!searching && searchedQuery != null && results.isEmpty()) {
                Text(
                    if (en) "No verified result for “$searchedQuery”. Try another name or brand."
                    else "“$searchedQuery” için doğrulanmış sonuç bulunamadı. Farklı bir ad veya marka dene.",
                    color = HedefitColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            selected?.let { food ->
                HedefitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row { Text(food.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); if (food.verified) Icon(Icons.Default.Verified, com.hedefit.app.ui.i18n.tr("Doğrulanmış", "Verified"), tint = HedefitColors.Lime) }
                        Text("${food.calories} kcal • P ${food.protein.toInt()} • K ${food.carbs.toInt()} • Y ${food.fat.toInt()} • Lif ${food.fiber.toInt()}", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        val gramsError = if (addAttempted) validateFoodGrams(grams) else null
                        OutlinedTextField(
                            grams,
                            { grams = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(if (en) "Grams" else "Gram") },
                            singleLine = true,
                            isError = gramsError != null,
                            supportingText = gramsError?.let { error -> ({ Text(if (en) "Enter a value between 0 and 5000 grams." else error) }) },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Kahvaltı", "Öğle yemeği", "Akşam yemeği", "Atıştırmalık").forEach { type -> FilterChip(meal == type, { meal = type }, label = { Text(mealLabel(type, en), style = MaterialTheme.typography.labelMedium) }) } }
                        Button(enabled = !adding, onClick = {
                            addAttempted = true
                            if (validateFoodGrams(grams) == null) onAdd(food, requireNotNull(grams.toDoubleOrNull()), meal)
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "Add to meal" else "Öğüne ekle") }
                    }
                }
            } ?: LazyColumn(Modifier.weight(1f, fill = false)) {
                items(results.size) { index ->
                    val food = results[index]
                    Row(Modifier.fillMaxWidth().clickable { selected = food; grams = food.servingGrams.toInt().toString() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(food.name, fontWeight = FontWeight.SemiBold); if (food.verified) { Spacer(Modifier.width(5.dp)); Icon(Icons.Default.Verified, null, tint = HedefitColors.Lime, modifier = Modifier.size(16.dp)) } }; Text("${food.source} • ${food.calories} kcal/100g", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = HedefitColors.TextSecondary)
                    }
                }
            }
        } },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Close" else "Kapat") } },
    )
}

private fun portionStepHalf(unit: String?) = unit in setOf("porsiyon", "tabak", "kase", "bardak", "portion")
private fun formatPortion(q: Double) = if (q % 1.0 == 0.0) q.toInt().toString() else "%.1f".format(q).replace('.', ',')
private fun portionUnitLabel(unit: String?, en: Boolean): String = when (unit) {
    null, "", "tane", "adet" -> if (en) "pc" else "adet"
    "dilim" -> if (en) "slice" else "dilim"
    "porsiyon", "portion" -> if (en) "serving" else "porsiyon"
    "tabak" -> if (en) "plate" else "tabak"
    "kase" -> if (en) "bowl" else "kase"
    "bardak" -> if (en) "glass" else "bardak"
    else -> unit
}

private data class EditablePhotoFood(val original: NutritionEstimateData, val name: String, val gramsText: String, val included: Boolean = true)

@Composable
private fun PhotoNutritionReviewDialog(
    detected: List<NutritionEstimateData>, busy: Boolean, en: Boolean,
    onDismiss: () -> Unit, onSave: (List<NutritionEstimateData>, String) -> Unit,
    fromText: Boolean = false, initialMeal: String? = null,
) {
    var foods by remember(detected) { mutableStateOf(detected.map { EditablePhotoFood(it, it.name, it.grams.toInt().toString()) }) }
    var meal by remember { mutableStateOf(initialMeal ?: smartMealForCurrentTime()) }
    fun scaled(editable: EditablePhotoFood): NutritionEstimateData? {
        val grams = editable.gramsText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..5000.0 } ?: return null
        val ratio = grams / editable.original.grams.coerceAtLeast(1.0)
        return editable.original.copy(name = editable.name.trim().take(100), grams = grams, calories = (editable.original.calories * ratio).toInt(), protein = editable.original.protein * ratio, carbs = editable.original.carbs * ratio, fat = editable.original.fat * ratio, fiber = editable.original.fiber * ratio, sugar = editable.original.sugar * ratio, sodiumMg = editable.original.sodiumMg * ratio, potassiumMg = editable.original.potassiumMg * ratio, calciumMg = editable.original.calciumMg * ratio, ironMg = editable.original.ironMg * ratio, vitaminCMg = editable.original.vitaminCMg * ratio)
    }
    val ready = foods.filter { it.included }.mapNotNull(::scaled).filter { it.name.isNotBlank() }
    val calories = ready.sumOf { it.calories }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (fromText) (if (en) "Check your meal" else "Öğününü kontrol et") else if (en) "Check photo analysis" else "Fotoğraf analizini kontrol et") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!fromText) Text(if (en) "Portions are visual estimates. Edit names and grams before saving." else "Porsiyonlar görsel tahmindir. Kaydetmeden önce adları ve gramları düzenle.", color = HedefitColors.Warning, style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(foods.size) { index ->
                    val food = foods[index]; val value = scaled(food)
                    val portion = food.original.portionQuantity
                    if (fromText && portion != null) {
                        val perUnit = food.original.grams / portion
                        val quantity = (food.gramsText.replace(',', '.').toDoubleOrNull() ?: food.original.grams) / perUnit
                        val step = if (portionStepHalf(food.original.portionUnit)) .5 else 1.0
                        fun setQuantity(q: Double) { foods = foods.toMutableList().also { it[index] = food.copy(gramsText = (perUnit * q).toInt().toString()) } }
                        Row(
                            Modifier.fillMaxWidth().background(HedefitColors.SurfaceHigh, RoundedCornerShape(14.dp)).padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(food.included, onCheckedChange = { checked -> foods = foods.toMutableList().also { it[index] = food.copy(included = checked) } })
                            Column(Modifier.weight(1f)) {
                                Text(food.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                value?.let { Text("${it.grams.toInt()} g • ${it.calories} kcal", color = HedefitColors.Lime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                            }
                            IconButton(onClick = { setQuantity((quantity - step).coerceAtLeast(step)) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, if (en) "Less" else "Azalt") }
                            Text("${formatPortion(quantity)} ${portionUnitLabel(food.original.portionUnit, en)}", fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { setQuantity(quantity + step) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, if (en) "More" else "Artır") }
                        }
                        return@items
                    }
                    Column(Modifier.fillMaxWidth().background(HedefitColors.SurfaceHigh, RoundedCornerShape(14.dp)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(food.included, onCheckedChange = { checked -> foods = foods.toMutableList().also { it[index] = food.copy(included = checked) } })
                            OutlinedTextField(food.name, { name -> foods = foods.toMutableList().also { it[index] = food.copy(name = name.take(100)) } }, Modifier.weight(1f), label = { Text(if (en) "Food" else "Besin") }, singleLine = true)
                        }
                        OutlinedTextField(food.gramsText, { grams -> foods = foods.toMutableList().also { it[index] = food.copy(gramsText = grams.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7)) } }, Modifier.fillMaxWidth(), label = { Text(if (fromText) (if (en) "Amount (g)" else "Miktar (g)") else if (en) "Estimated amount (g)" else "Tahmini miktar (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        value?.let { Text("${it.calories} kcal • P ${it.protein.toInt()} g • K ${it.carbs.toInt()} g • Y ${it.fat.toInt()} g • Lif ${it.fiber.toInt()} g", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall); Text("Na ${it.sodiumMg.toInt()} mg • K ${it.potassiumMg.toInt()} mg • Ca ${it.calciumMg.toInt()} mg • Fe ${"%.1f".format(it.ironMg)} mg • C ${it.vitaminCMg.toInt()} mg", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) { items(listOf("Kahvaltı", "Öğle yemeği", "Akşam yemeği", "Atıştırmalık")) { type -> FilterChip(meal == type, { meal = type }, label = { Text(mealLabel(type, en)) }) } }
            Text(if (en) "Total: $calories kcal • ${ready.size} foods" else "Toplam: $calories kcal • ${ready.size} besin", color = HedefitColors.Lime, fontWeight = FontWeight.Bold)
        } },
        confirmButton = { Button(enabled = !busy && ready.isNotEmpty(), onClick = { onSave(ready, meal) }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "Confirm and add" else "Onayla ve ekle") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
    )
}

private suspend fun prepareMealPhoto(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@runCatching null
        encodeMealPhoto(bitmap).also { bitmap.recycle() }
    }.getOrNull()
}

private fun encodeMealPhoto(bitmap: Bitmap): ByteArray? = runCatching {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    val resized = if (maxSide > 1600) {
        val scale = 1600f / maxSide
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else bitmap
    ByteArrayOutputStream().use { output -> resized.compress(Bitmap.CompressFormat.JPEG, 82, output); output.toByteArray() }
        .also { if (resized !== bitmap) resized.recycle() }
}.getOrNull()?.takeIf { it.size <= 5 * 1024 * 1024 }

// ~40 g carbs + 20 g protein extra around a session; the larger of this and
// measured activity burn is added so the two are never double counted.
private const val TRAINING_DAY_KCAL = 250

private fun isTrainingDay(data: DashboardData?, date: LocalDate): Boolean {
    if (data == null) return false
    val zone = java.time.ZoneId.systemDefault()
    val scheduled = data.schedule.any { it.date.take(10) == date.toString() && it.status in setOf("planned", "completed") }
    val trained = data.sessions.any { session ->
        runCatching { java.time.Instant.parse(session.completedAt).atZone(zone).toLocalDate() }.getOrElse { runCatching { LocalDate.parse(session.completedAt.take(10)) }.getOrNull() } == date
    }
    return scheduled || trained
}

private data class TrainingFood(
    val catalogName: String,
    val tr: String,
    val en: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val grams: Double,
    val meal: String,
    val countLabelTr: String? = null,
    val countLabelEn: String? = null,
    val unit: String = "g",
)

// Per-100 g values mirror lib/default-food-catalog.ts so the preview matches what gets logged.
private val PRE_WORKOUT_FOODS = listOf(
    TrainingFood("Yulaf ezmesi", "Yulaf ezmesi", "Oats", 379.0, 13.0, 68.0, 6.5, 60.0, "Kahvaltı"),
    TrainingFood("Muz", "Muz", "Banana", 89.0, 1.1, 23.0, 0.3, 120.0, "Atıştırmalık", "1 adet", "1 piece"),
    TrainingFood("Tam buğday ekmeği", "Tam buğday ekmeği", "Whole wheat bread", 247.0, 13.0, 41.0, 3.4, 60.0, "Atıştırmalık", "2 dilim", "2 slices"),
    TrainingFood("Süt, yarım yağlı", "Süt", "Milk", 50.0, 3.4, 4.8, 1.8, 200.0, "Atıştırmalık", unit = "ml"),
)

private val POST_WORKOUT_FOODS = listOf(
    TrainingFood("Tavuk göğsü, pişmiş", "Tavuk göğsü", "Chicken breast", 165.0, 31.0, 0.0, 3.6, 150.0, "Akşam yemeği"),
    TrainingFood("Pirinç pilavı, pişmiş", "Pirinç pilavı", "Rice", 130.0, 2.7, 28.0, 0.3, 150.0, "Akşam yemeği"),
    TrainingFood("Somon, pişmiş", "Somon", "Salmon", 206.0, 22.0, 0.0, 12.0, 150.0, "Akşam yemeği"),
    TrainingFood("Tatlı patates, pişmiş", "Tatlı patates", "Sweet potato", 90.0, 2.0, 21.0, 0.2, 200.0, "Akşam yemeği"),
    TrainingFood("Ton Balıklı Sandviç", "Ton balıklı sandviç", "Tuna sandwich", 235.0, 15.0, 25.0, 8.0, 180.0, "Öğle yemeği", "1 adet", "1 piece"),
    TrainingFood("Yumurta, bütün", "Yumurta", "Eggs", 143.0, 13.0, 0.7, 9.5, 100.0, "Atıştırmalık", "2 adet", "2 eggs"),
    TrainingFood("Süzme yoğurt", "Süzme yoğurt", "Greek yogurt", 97.0, 9.0, 3.9, 5.0, 200.0, "Atıştırmalık"),
)

private fun TrainingFood.scaledGrams(factor: Double) = if (countLabelTr != null) grams else (kotlin.math.round(grams * factor / 10.0) * 10.0).coerceAtLeast(10.0)

@Composable
private fun TrainingDayMealsCard(
    en: Boolean,
    enabled: Boolean,
    workoutTime: String?,
    goalDirection: Int,
    onAdd: (String, Double, String) -> Unit,
    onAddWater: (Int) -> Unit,
    onAskCoach: () -> Unit,
) {
    val factor = when { goalDirection < 0 -> 0.8; goalDirection > 0 -> 1.2; else -> 1.0 }
    val start = workoutTime?.let { runCatching { java.time.LocalTime.parse(it.take(5)) }.getOrNull() }
    fun at(minutes: Long) = start?.plusMinutes(minutes)?.toString()
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when {
                    goalDirection < 0 -> if (en) "Portions are trimmed for fat loss (×0.8)." else "Porsiyonlar yağ kaybı hedefine göre ayarlandı (×0.8)."
                    goalDirection > 0 -> if (en) "Portions are increased for muscle/weight gain (×1.2)." else "Porsiyonlar kas/kilo alma hedefine göre artırıldı (×1.2)."
                    else -> if (en) "Portions are set for maintenance." else "Porsiyonlar kilo koruma hedefine göre ayarlandı."
                },
                fontWeight = FontWeight.SemiBold,
            )
            TrainingSection(
                title = if (en) "Before training" else "Antrenman öncesi",
                timing = if (start != null) (if (en) "${at(-90)}–${at(-60)} • 60–90 min before" else "${at(-90)}–${at(-60)} • 60–90 dk önce") else if (en) "60–90 min before" else "Antrenmandan 60–90 dk önce",
                why = if (en) "Easily digested carbs fuel the session; keep fat and fibre low." else "Kolay sindirilen karbonhidrat enerji verir; yağ ve lifi düşük tut.",
                foods = PRE_WORKOUT_FOODS, factor = factor, en = en, enabled = enabled, onAdd = onAdd,
            )
            HfDivider()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (en) "During training" else "Antrenman sırasında", color = HedefitColors.Water, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                Text(if (en) "Sip water every 15–20 min, about 500 ml per hour." else "15–20 dakikada bir yudumla, saatte yaklaşık 500 ml su iç.")
                HfPrimaryButton(if (en) "+500 ml water" else "+500 ml su", { onAddWater(500) }, Modifier.fillMaxWidth(), secondary = true, enabled = enabled)
            }
            HfDivider()
            TrainingSection(
                title = if (en) "After training" else "Antrenman sonrası",
                timing = if (start != null) (if (en) "Until ${at(180)} • within 2 h" else "En geç ${at(180)} • 2 saat içinde") else if (en) "Within 2 hours" else "Antrenmandan sonraki 2 saat içinde",
                why = if (en) "Protein (25–40 g) repairs muscle; carbs refill glycogen." else "Protein (25–40 g) kası onarır, karbonhidrat glikojen depolarını doldurur.",
                foods = POST_WORKOUT_FOODS, factor = factor, en = en, enabled = enabled, onAdd = onAdd,
            )
            HfPrimaryButton(if (en) "Ask Fit Coach for a menu" else "FitKoç'tan menü iste", onAskCoach, Modifier.fillMaxWidth(), Icons.Default.AutoAwesome, secondary = true)
        }
    }
}

@Composable
private fun TrainingSection(title: String, timing: String, why: String, foods: List<TrainingFood>, factor: Double, en: Boolean, enabled: Boolean, onAdd: (String, Double, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = HedefitColors.Lime, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
        Text(timing, fontWeight = FontWeight.Bold)
        Text(why)
        foods.forEach { food ->
            val grams = food.scaledGrams(factor)
            val r = grams / 100.0
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${if (en) food.en else food.tr} • ${(if (en) food.countLabelEn else food.countLabelTr) ?: "${grams.toInt()} ${food.unit}"}", fontWeight = FontWeight.SemiBold)
                    Text("${(food.kcal * r).toInt()} kcal • P ${"%.0f".format(food.protein * r)} • K ${"%.0f".format(food.carbs * r)} • Y ${"%.0f".format(food.fat * r)}", style = MaterialTheme.typography.labelMedium, color = HedefitColors.Lime)
                }
                IconButton(enabled = enabled, onClick = { onAdd(food.catalogName, grams, food.meal) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, if (en) "Add ${food.en}" else "${food.tr} ekle", tint = HedefitColors.Lime, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
