package com.hedefit.app.ui.screens

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hedefit.app.R
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.OutlineAction
import com.hedefit.app.ui.components.PrimaryButton
import com.hedefit.app.ui.components.ProgressRing
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.components.SectionTitle
import com.hedefit.app.ui.components.ExerciseMedia
import com.hedefit.app.ui.components.ExerciseMotionPlayer
import com.hedefit.app.ui.components.workoutExerciseImagePaths
import com.hedefit.app.ui.model.ExerciseUi
import com.hedefit.app.ui.model.WorkoutDayUi
import com.hedefit.app.ui.model.weeklyWorkouts
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.ui.components.*
import com.hedefit.app.data.model.ProfileData
import com.hedefit.app.data.model.WorkoutExercisePerformanceData
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Place
import com.hedefit.app.ui.state.prescribedStartingReps
import com.hedefit.app.ui.state.canFinishWorkout
import com.hedefit.app.ui.state.savedWorkoutSet
import com.hedefit.app.ui.state.toSavedWorkoutSet
import com.hedefit.app.ui.state.validateWorkoutReps
import com.hedefit.app.ui.state.validateWorkoutRest
import com.hedefit.app.ui.state.validateWorkoutSets
import com.hedefit.app.gym.ActiveWorkoutSnapshot
import com.hedefit.app.gym.ActiveWorkoutStore
import com.hedefit.app.gym.adjustedRestDeadline
import com.hedefit.app.gym.remainingRestSeconds
import com.hedefit.app.notifications.AndroidRestCompletionNotifier
import com.hedefit.app.data.model.WorkoutExerciseData
import com.hedefit.app.data.model.WorkoutSetInput
import com.hedefit.app.data.model.WorkoutFeedbackData
import com.hedefit.app.data.model.PreviousSetData
import com.hedefit.app.data.model.WorkoutProgramData
import com.hedefit.app.data.model.ExerciseCatalogData
import com.hedefit.app.data.model.CustomProgramDraft
import com.hedefit.app.data.model.WorkoutProgramDayData
import com.hedefit.app.data.model.CoachActionData
import com.hedefit.app.data.model.DailyReadinessInput
import com.hedefit.app.data.model.ReadinessAdaptationData
import com.hedefit.app.data.model.ExerciseReplacementCandidate
import com.hedefit.app.data.model.WorkoutAdaptationResultData
import com.hedefit.app.data.model.WorkoutCoachContext
import com.hedefit.app.ui.components.ReadinessCheckinSheet
import com.hedefit.app.ui.components.ExerciseReplacementSheet
import com.hedefit.app.ui.components.WorkoutAdaptationSheet
import com.hedefit.app.ui.components.SecondaryButton
import com.hedefit.app.ui.state.ChatMessageState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import java.util.Locale

@Composable
fun WorkoutPlanScreen(
    padding: PaddingValues,
    expanded: Boolean,
    workouts: List<WorkoutExerciseData>,
    programs: List<WorkoutProgramData>,
    regionalExercises: List<ExerciseCatalogData>,
    regionalLoading: Boolean,
    loading: Boolean,
    generating: Boolean,
    onGeneratePlan: () -> Unit,
    onStartWorkout: () -> Unit,
    hasActiveWorkout: Boolean,
    onResumeWorkout: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenActivityLog: () -> Unit,
    onOpenRoute: () -> Unit,
    onGenerateRegional: (String, String, Pair<String, String>?) -> Unit,
    onLoadRegional: (String) -> Unit,
    onGenerateQuickWorkout: (List<Pair<String, String>>, Int, String, String, String) -> Unit,
    onCreateOwnPlan: (CustomProgramDraft) -> Unit,
    onAddPushPullTemplate: (String) -> Unit,
    onSelectProgram: (WorkoutProgramData) -> Unit,
    onRemoveProgram: (WorkoutProgramData) -> Unit,
    onCopyProgram: (WorkoutProgramData) -> Unit,
    onUpdateExercise: (WorkoutExerciseData) -> Unit,
    onReplaceExercise: (String, WorkoutExerciseData) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onLoadReplacementOptions: (WorkoutExerciseData) -> Unit,
    language: String = "tr",
    onAdaptReadiness: (DailyReadinessInput, (ReadinessAdaptationData) -> Unit) -> Unit = { _, _ -> },
    onApplyReadinessAdaptation: (ReadinessAdaptationData) -> Unit = {},
    readinessAdaptation: ReadinessAdaptationData? = null,
    readinessBusy: Boolean = false,
    onRequestReplacementCandidate: (currentExerciseId: String, reason: String, discomfortArea: String?, (ExerciseReplacementCandidate?) -> Unit) -> Unit = { _, _, _, _ -> },
    onApplyReplacementCandidate: (ExerciseReplacementCandidate) -> Unit = {},
    replacementCandidate: ExerciseReplacementCandidate? = null,
    replacementBusy: Boolean = false,
    onRequestPlanAdaptation: (trigger: String, targetMinutes: Int?, (WorkoutAdaptationResultData) -> Unit) -> Unit = { _, _, _ -> },
    onApplyPlanAdaptation: (WorkoutAdaptationResultData) -> Unit = {},
    planAdaptationResult: WorkoutAdaptationResultData? = null,
    planAdaptationBusy: Boolean = false,
    chatMessages: List<ChatMessageState> = emptyList(),
    chatBusy: Boolean = false,
    onSendChatMessage: (String, WorkoutCoachContext?) -> Unit = { _, _ -> },
    onExecuteCoachAction: (CoachActionData) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenQuestionnaire: () -> Unit = {},
    profile: ProfileData? = null,
    performances: List<WorkoutExercisePerformanceData> = emptyList(),
    catalog: List<ExerciseCatalogData> = emptyList(),
) {
    val en = language == "en"
    var showRegional by remember { mutableStateOf(false) }
    var selectedRegional by remember { mutableStateOf<Pair<String, String>?>(null) }
    var removingProgram by remember { mutableStateOf<WorkoutProgramData?>(null) }
    var editingExercise by remember { mutableStateOf<WorkoutExerciseData?>(null) }
    var previewExercise by remember { mutableStateOf<WorkoutExerciseData?>(null) }
    var replacingExercise by remember { mutableStateOf<WorkoutExerciseData?>(null) }
    var pendingScrollProgramId by remember { mutableStateOf<String?>(null) }
    var showReadinessCheckin by remember { mutableStateOf(false) }
    var showTimeAdaptation by remember { mutableStateOf(false) }
    var replacingWithEngineExercise by remember { mutableStateOf<WorkoutExerciseData?>(null) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val activeProgram = programs.firstOrNull { it.isActive }
    val programDetailIndex = if (hasActiveWorkout) 2 else 1
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllExercises by remember { mutableStateOf(false) }
    LaunchedEffect(activeProgram?.id, pendingScrollProgramId) {
        val requested = pendingScrollProgramId ?: return@LaunchedEffect
        if (activeProgram?.id == requested) {
            listState.animateScrollToItem(programDetailIndex)
            pendingScrollProgramId = null
        }
    }
    if (showRegional) {
        BackHandler {
            if (selectedRegional != null) selectedRegional = null else showRegional = false
        }
        RegionalProgramBrowser(
            padding = padding,
            en = en,
            selectedRegion = selectedRegional,
            exercises = regionalExercises,
            loading = regionalLoading,
            onBack = { if (selectedRegional != null) selectedRegional = null else showRegional = false },
            onSelectRegion = { region -> selectedRegional = region; onLoadRegional(region.first) },
            onCreateProgram = { muscle, label, connected -> onGenerateRegional(muscle, label, connected); showRegional = false; selectedRegional = null; page = null },
        )
        return
    }
    page?.let { current ->
        val back = { page = if (current in listOf("quick", "ai", "ready", "custom")) "hub" else null }
        BackHandler(onBack = back)
        when (current) {
            "hub" -> ProgramCreateHub(padding, en, onBack = back, onOpen = { page = it }, onOpenRegional = { showRegional = true })
            "quick" -> QuickWorkoutPage(padding, en, generating, onBack = back) { regions, duration, fatigue, environment, equipment -> onGenerateQuickWorkout(regions, duration, fatigue, environment, equipment); page = null }
            "ai" -> AiProgramPage(padding, en, profile, generating, onBack = back, onEditPreferences = onOpenQuestionnaire) { onGeneratePlan(); page = null }
            "ready" -> ReadyProgramsPage(padding, en, generating, onBack = back) { key -> onAddPushPullTemplate(key); page = null }
            "custom" -> CustomProgramPage(padding, en, onBack = back) { draft -> onCreateOwnPlan(draft); page = null }
            "muscles" -> ScreenContainer(padding) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { HfScreenHeader(if (en) "Muscle Atlas" else "Kas Atlası", if (en) "Your muscle development map" else "Kas gelişim haritan", onBack = back) }
                    item { TrainingAnalysisSection(performances, catalog, language) }
                }
            }
        }
        return
    }
    ScreenContainer(padding) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HfScreenHeader(
                    if (en) "My Workout" else "Antrenmanım",
                    activeProgram?.let { programDisplayName(it, en) } ?: if (en) "No active program" else "Aktif program yok",
                ) { HfCircleButton(Icons.Default.CalendarMonth, if (en) "Workout calendar" else "Antrenman takvimi", onOpenCalendar) }
            }
            if (hasActiveWorkout) item {
                HfNavRow(Icons.Default.PlayArrow, HedefitColors.Lime, if (en) "Workout in progress" else "Antrenman devam ediyor", if (en) "Tap to resume without losing your sets" else "Setlerini kaybetmeden devam et", onResumeWorkout)
            }
            item(key = "active-program-detail") {
                if (loading && workouts.isEmpty()) {
                    HedefitCard(Modifier.fillMaxWidth()) { Text(if (en) "Loading your program…" else "Programın yükleniyor…", color = HedefitColors.TextSecondary) }
                } else if (workouts.isEmpty()) {
                    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HfIconBadge(Icons.Default.FitnessCenter, HedefitColors.Lime, 44.dp, 22.dp, 14.dp)
                            Text(if (en) "No program yet" else "Henüz programın yok", style = MaterialTheme.typography.titleLarge)
                            Text(if (en) "Create one with Hedefit AI, pick a ready template or build your own." else "Hedefit AI ile oluştur, hazır bir şablon seç ya da kendin yap.", color = HedefitColors.TextSecondary)
                            HfPrimaryButton(if (en) "Create a program" else "Program oluştur", { page = "hub" }, Modifier.fillMaxWidth(), Icons.Default.Add)
                        }
                    }
                } else {
                    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (en) "ACTIVE PROGRAM" else "AKTİF PROGRAM", color = HedefitColors.Lime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                                    Text(activeProgram?.let { programDisplayName(it, en) } ?: workouts.first().area, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(if (en) "${workouts.size} exercises • ~${workouts.size * 7} min" else "${workouts.size} hareket • ~${workouts.size * 7} dk", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (activeProgram?.source == "regional") TextButton(onClick = { showRegional = true }) { Text(if (en) "Change area" else "Bölgeyi değiştir", color = HedefitColors.Lime) }
                            }
                            HfDivider()
                            val visible = if (showAllExercises) workouts else workouts.take(4)
                            visible.forEach { exercise ->
                                EditableProgramRow(
                                    exercise = exercise,
                                    en = en,
                                    onPreview = { previewExercise = exercise },
                                    onEdit = { editingExercise = exercise },
                                    onSetCountChange = { count -> onUpdateExercise(exercise.copy(sets = count)) },
                                    onReplace = { replacingWithEngineExercise = exercise },
                                )
                            }
                            if (workouts.size > 4) TextButton(onClick = { showAllExercises = !showAllExercises }) {
                                Text(if (showAllExercises) (if (en) "Show less" else "Daha az göster") else if (en) "Show ${workouts.size - 4} more" else "+${workouts.size - 4} hareket daha göster", color = HedefitColors.TextSecondary, fontWeight = FontWeight.Bold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                HfPrimaryButton(if (en) "Start workout" else "Antrenmanı başlat", onStartWorkout, Modifier.weight(1f), Icons.Default.PlayArrow)
                                IconButton(onClick = { showReadinessCheckin = true }, modifier = Modifier.size(52.dp).background(HedefitColors.SurfaceHigh, RoundedCornerShape(16.dp))) {
                                    Icon(Icons.Default.Bolt, if (en) "Readiness check" else "Hazırlık kontrolü", tint = HedefitColors.Warning)
                                }
                                IconButton(onClick = { showTimeAdaptation = true }, modifier = Modifier.size(52.dp).background(HedefitColors.SurfaceHigh, RoundedCornerShape(16.dp))) {
                                    Icon(Icons.Default.Timer, if (en) "Adapt plan" else "Planı uyarla", tint = HedefitColors.TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
            item { HfSectionHeader(if (en) "Tools" else "Araçlar") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        HfActionTile(Icons.Default.MenuBook, HedefitColors.Lime, if (en) "Movement Atlas" else "Hareket Atlası", if (en) "Technique and exercises" else "Teknik ve hareketler", onOpenLibrary, Modifier.weight(1f).fillMaxHeight())
                        HfActionTile(Icons.Default.AutoAwesome, HedefitColors.Warning, if (en) "New program" else "Program oluştur", if (en) "AI, templates or custom" else "AI, şablon veya kendin", { page = "hub" }, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        HfActionTile(Icons.Default.AccessibilityNew, HedefitColors.Coral, if (en) "Muscle Atlas" else "Kas Atlası", if (en) "Muscles you trained" else "Çalışan kasların", { page = "muscles" }, Modifier.weight(1f).fillMaxHeight())
                        HfActionTile(Icons.Default.Route, HedefitColors.Sleep, if (en) "Hedefit Route" else "Hedefit Rota", if (en) "GPS activity" else "GPS aktivitesi", onOpenRoute, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        HfActionTile(Icons.Default.Add, HedefitColors.Water, if (en) "Log activity" else "Antrenman ekle", if (en) "Sport, distance, pace" else "Spor, mesafe, tempo", onOpenActivityLog, Modifier.weight(1f).fillMaxHeight())
                        HfActionTile(Icons.Default.CameraAlt, HedefitColors.TextSecondary, if (en) "Scan equipment" else "Ekipman tara", if (en) "Recognise with camera" else "Kamerayla tanı", onOpenScanner, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            if (programs.isNotEmpty()) {
                item { HfSectionHeader(if (en) "My programs" else "Programlarım", if (en) "${programs.size} programs" else "${programs.size} program") }
                items(programs, key = { it.id }) { program ->
                    val (icon, tint) = programStyle(program.source)
                    HfNavRow(
                        icon, tint, programDisplayName(program, en),
                        "${programSourceLabel(program.source, en)} • " + if (en) "${program.exercises.size} exercises" else "${program.exercises.size} hareket",
                        onClick = {
                            pendingScrollProgramId = program.id
                            onSelectProgram(program)
                            scrollScope.launch { delay(180); listState.animateScrollToItem(programDetailIndex) }
                        },
                        chevron = false,
                    ) {
                        if (program.isActive) HfPill(if (en) "ACTIVE" else "AKTİF")
                        IconButton(onClick = { onCopyProgram(program) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ContentCopy, if (en) "Copy program" else "Programı kopyala", tint = HedefitColors.TextMuted, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { removingProgram = program }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.DeleteOutline, if (en) "Remove program" else "Programı kaldır", tint = HedefitColors.TextMuted, modifier = Modifier.size(19.dp)) }
                    }
                }
            }
            item { HfNavRow(Icons.Default.Add, HedefitColors.TextSecondary, if (en) "Create a new program" else "Yeni program oluştur", if (en) "With AI, from a template or your own" else "AI ile, hazır şablondan veya kendin", { page = "hub" }) }
        }
    }
    removingProgram?.let { program ->
        AlertDialog(
            onDismissRequest = { removingProgram = null },
            title = { Text(if (en) "Remove program?" else "Program kaldırılsın mı?") },
            text = { Text(if (en) "${programDisplayName(program, en)} will be removed. Completed workout records stay safe." else "${programDisplayName(program, en)} kaldırılacak. Tamamlanmış antrenman kayıtların korunur.") },
            dismissButton = { TextButton(onClick = { removingProgram = null }) { Text(if (en) "Cancel" else "Vazgeç") } },
            confirmButton = { Button(onClick = { onRemoveProgram(program); removingProgram = null }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Coral, contentColor = Color.White)) { Text(if (en) "Remove" else "Kaldır") } },
        )
    }
    editingExercise?.let { exercise ->
        val index = workouts.indexOfFirst { it.id == exercise.id }
        WorkoutExerciseEditorDialog(
            exercise, en, index > 0, index in 0 until workouts.lastIndex,
            onDismiss = { editingExercise = null },
            onMove = { onMoveExercise(exercise.id, it); editingExercise = null },
            onRemove = { onRemoveExercise(exercise.id); editingExercise = null },
            onReplace = { replacingWithEngineExercise = exercise; editingExercise = null },
        ) { onUpdateExercise(it); editingExercise = null }
    }
    replacingExercise?.let { exercise ->
        ExerciseReplacementDialog(
            exercise = exercise,
            candidates = regionalExercises.filter { it.id != exercise.id }.take(24),
            loading = regionalLoading,
            en = en,
            onDismiss = { replacingExercise = null },
            onReplace = { selected ->
                onReplaceExercise(exercise.id, exercise.copy(id = selected.id, name = selected.name, area = selected.primaryMuscles.firstOrNull() ?: exercise.area))
                replacingExercise = null
            },
        )
    }
    previewExercise?.let { exercise ->
        AlertDialog(
            onDismissRequest = { previewExercise = null },
            title = { Text(exercise.name) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExerciseMotionPlayer(
                    exercise.id,
                    exercise.name,
                    exercise.name,
                    Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(18.dp)),
                )
                Text(if (en) "Watch the full movement path before starting. Keep the motion controlled and stop if you feel sharp pain." else "Başlamadan önce hareket yolunu izle. Hareketi kontrollü uygula; keskin ağrı hissedersen dur.", color = HedefitColors.TextSecondary)
            } },
            confirmButton = { TextButton(onClick = { previewExercise = null }) { Text(if (en) "Close" else "Kapat") } },
        )
    }

    if (showReadinessCheckin) {
        ReadinessCheckinSheet(
            onDismiss = { showReadinessCheckin = false },
            onSubmit = { input ->
                onAdaptReadiness(input) { _ -> }
            },
            adaptationResult = readinessAdaptation,
            isBusy = readinessBusy,
            onApplyAdaptation = { res ->
                onApplyReadinessAdaptation(res)
                showReadinessCheckin = false
            },
            onKeepOriginal = { showReadinessCheckin = false },
            locale = language,
        )
    }

    replacingWithEngineExercise?.let { ex ->
        ExerciseReplacementSheet(
            exercise = ex,
            candidate = replacementCandidate,
            isBusy = replacementBusy,
            onDismiss = { replacingWithEngineExercise = null },
            onRequestReplacement = { reason, discomfortArea ->
                onRequestReplacementCandidate(ex.id, reason, discomfortArea) { _ -> }
            },
            onApplyReplacement = { rep ->
                onApplyReplacementCandidate(rep)
                replacingWithEngineExercise = null
            },
            locale = language,
        )
    }

    if (showTimeAdaptation) {
        WorkoutAdaptationSheet(
            onDismiss = { showTimeAdaptation = false },
            // Yalnız isteği başlatır; sonucu doğrudan uygulamaz. `onApplyAdaptation`
            // (aşağıda) "Uygula" butonuna bağlı — kullanıcı öneriyi görmeden
            // otomatik uygulanıp temizlenirse (eski davranış) sayfa hiçbir şey
            // olmamış gibi seçenek listesine geri dönüyordu.
            onSelectTrigger = { trigger, mins -> onRequestPlanAdaptation(trigger, mins) {} },
            adaptationResult = planAdaptationResult,
            isBusy = planAdaptationBusy,
            onApplyAdaptation = onApplyPlanAdaptation,
            locale = language,
        )
    }
}

private fun programDisplayName(program: WorkoutProgramData, en: Boolean) =
    if (program.source == "assessment") (if (en) "Fit Coach Program" else "Fit Koç Programı") else program.name

@Composable
private fun EditableProgramRow(
    exercise: WorkoutExerciseData,
    en: Boolean,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onSetCountChange: (Int) -> Unit,
    onReplace: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExerciseMedia(workoutExerciseImagePaths(exercise.id, exercise.name), exercise.name, Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onPreview))
        Column(Modifier.weight(1f)) {
            Text(exercise.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable(onClick = onPreview))
            Text("${exercise.reps} ${if (en) "reps" else "tekrar"} • ${exercise.restSeconds} ${if (en) "sec rest" else "sn dinlenme"}", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = if (en) "Replace" else "Değiştir",
                    color = HedefitColors.Lime,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onReplace),
                )
            }
        }
        Row(Modifier.background(HedefitColors.SurfaceHigh, RoundedCornerShape(14.dp)), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = exercise.sets > 1, onClick = { onSetCountChange((exercise.sets - 1).coerceAtLeast(1)) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Remove, if (en) "Decrease sets" else "Set azalt", modifier = Modifier.size(18.dp))
            }
            Text("${exercise.sets}", color = HedefitColors.Lime, fontWeight = FontWeight.Black)
            IconButton(enabled = exercise.sets < 10, onClick = { onSetCountChange((exercise.sets + 1).coerceAtMost(10)) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Add, if (en) "Increase sets" else "Set artır", modifier = Modifier.size(18.dp))
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, if (en) "Edit" else "Düzenle", tint = HedefitColors.TextSecondary) }
    }
}

@Composable
private fun WorkoutExerciseEditorDialog(exercise: WorkoutExerciseData, en: Boolean, canMoveUp: Boolean, canMoveDown: Boolean, onDismiss: () -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit, onReplace: () -> Unit, onSave: (WorkoutExerciseData) -> Unit) {
    var sets by remember(exercise) { mutableStateOf(exercise.sets.toString()) }
    var reps by remember(exercise) { mutableStateOf(exercise.reps) }
    var rest by remember(exercise) { mutableStateOf(exercise.restSeconds.toString()) }
    var targetWeight by remember(exercise) { mutableStateOf(exercise.targetWeightKg?.let { "%.1f".format(it) }.orEmpty()) }
    var submitted by remember(exercise) { mutableStateOf(false) }
    val setsError = if (submitted) validateWorkoutSets(sets) else null
    val repsError = if (submitted) validateWorkoutReps(reps) else null
    val restError = if (submitted) validateWorkoutRest(rest) else null
    AlertDialog(onDismissRequest = onDismiss, title = { Text(exercise.name) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(sets, { sets = it.filter(Char::isDigit).take(2) }, label = { Text(if (en) "Sets" else "Set") }, singleLine = true, isError = setsError != null, supportingText = setsError?.let { ({ Text(if (en) "Sets must be between 1 and 10." else it) }) })
        OutlinedTextField(reps, { reps = it.take(12) }, label = { Text(if (en) "Reps" else "Tekrar") }, singleLine = true, isError = repsError != null, supportingText = repsError?.let { ({ Text(if (en) "Enter a repetition target." else it) }) })
        OutlinedTextField(rest, { rest = it.filter(Char::isDigit).take(3) }, label = { Text(if (en) "Rest (seconds)" else "Dinlenme (saniye)") }, singleLine = true, isError = restError != null, supportingText = restError?.let { ({ Text(if (en) "Rest must be between 15 and 300 seconds." else it) }) })
        OutlinedTextField(targetWeight, { targetWeight = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(7) }, label = { Text(if (en) "Target weight (kg)" else "Hedef ağırlık (kg)") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(enabled = canMoveUp, onClick = { onMove(-1) }) { Icon(Icons.Default.KeyboardArrowUp, if (en) "Move up" else "Yukarı taşı", tint = HedefitColors.Lime) }
            IconButton(enabled = canMoveDown, onClick = { onMove(1) }) { Icon(Icons.Default.KeyboardArrowDown, if (en) "Move down" else "Aşağı taşı", tint = HedefitColors.Lime) }
            IconButton(onClick = onRemove) { Icon(Icons.Default.DeleteOutline, if (en) "Remove" else "Programdan çıkar", tint = HedefitColors.Coral) }
        }
        TextButton(onClick = onReplace) { Text(if (en) "Replace movement" else "Hareketi değiştir", color = HedefitColors.Lime) }
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } }, confirmButton = { Button(onClick = {
        submitted = true
        if (validateWorkoutSets(sets) == null && validateWorkoutReps(reps) == null && validateWorkoutRest(rest) == null) {
            onSave(exercise.copy(sets = requireNotNull(sets.toIntOrNull()), reps = reps.trim(), restSeconds = requireNotNull(rest.toIntOrNull()), targetWeightKg = targetWeight.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..1_000.0 }))
        }
    }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "Save" else "Kaydet") } })
}

@Composable
private fun ExerciseReplacementDialog(exercise: WorkoutExerciseData, candidates: List<ExerciseCatalogData>, loading: Boolean, en: Boolean, onDismiss: () -> Unit, onReplace: (ExerciseCatalogData) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Same-muscle alternatives" else "Aynı bölge için alternatif hareketler") },
        text = {
            if (loading) Text(if (en) "Loading matching movements…" else "Uygun hareketler yükleniyor…")
            else if (candidates.isEmpty()) Text(if (en) "No same-muscle alternative was found." else "Bu bölge için alternatif hareket bulunamadı.")
            else LazyColumn(Modifier.height(330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(candidates, key = { it.id }) { candidate ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onReplace(candidate) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ExerciseMedia(candidate.imageUrls.firstOrNull(), candidate.name, Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(candidate.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(candidate.equipment, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (en) "Close" else "Kapat") } },
    )
}

@Composable
private fun RegionalProgramBrowser(
    padding: PaddingValues,
    en: Boolean,
    selectedRegion: Pair<String, String>?,
    exercises: List<ExerciseCatalogData>,
    loading: Boolean,
    onBack: () -> Unit,
    onSelectRegion: (Pair<String, String>) -> Unit,
    onCreateProgram: (String, String, Pair<String, String>?) -> Unit,
) {
    val regions = regionalMuscleRegions(en)
    var selectedExercise by remember { mutableStateOf<ExerciseCatalogData?>(null) }
    var includeConnected by remember(selectedRegion) { mutableStateOf(false) }
    val connectedKey = selectedRegion?.let { CONNECTED_REGIONS[it.first] }
    val connectedLabel = connectedKey?.let { key -> regions.firstOrNull { it.key == key }?.label }
    ScreenContainer(padding) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HfScreenHeader(
                    selectedRegion?.second ?: if (en) "Body-part programs" else "Bölgesel programlar",
                    if (selectedRegion == null) (if (en) "Focus on a muscle group" else "Bir kas grubuna odaklan") else if (loading) (if (en) "Loading movements…" else "Hareketler yükleniyor…") else if (en) "${exercises.size} movements" else "${exercises.size} hareket",
                    onBack = onBack,
                )
            }
            if (selectedRegion == null) {
                items(regions.chunked(3)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { region ->
                            HedefitCard(Modifier.weight(1f).heightIn(min = 64.dp), onClick = { onSelectRegion(region.key to region.label) }, contentPadding = PaddingValues(12.dp)) {
                                Text(region.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                if (loading) item { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(), color = HedefitColors.Lime) }
                if (!loading && exercises.isEmpty()) item { Text(if (en) "No movement was found for this region." else "Bu bölge için hareket bulunamadı.", color = HedefitColors.TextSecondary) }
                if (exercises.isNotEmpty()) item {
                    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Column {
                            exercises.forEachIndexed { index, exercise ->
                                if (index > 0) HfDivider()
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selectedExercise = exercise }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ExerciseMedia(exercise.imageUrls.firstOrNull(), exercise.name, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                                    Column(Modifier.weight(1f)) {
                                        Text(exercise.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(listOf(exercise.primaryMuscles.joinToString(), exercise.equipment).filter(String::isNotBlank).joinToString(" • "), color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                if (!loading && exercises.isNotEmpty() && connectedLabel != null) item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HedefitColors.Surface)
                            .clickable { includeConnected = !includeConnected }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HfIconBadge(Icons.Default.Link, HedefitColors.Sleep, 34.dp, 17.dp, 11.dp)
                        Column(Modifier.weight(1f)) {
                            Text(if (en) "Also add connected area: $connectedLabel" else "Bağlı bölgeyi de ekle: $connectedLabel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (en) "Creates a separate program for it" else "Onun için ayrı bir program oluşturulur", color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        androidx.compose.material3.Switch(checked = includeConnected, onCheckedChange = { includeConnected = it }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = HedefitColors.OnLime, checkedTrackColor = HedefitColors.Lime))
                    }
                }
                if (!loading && exercises.isNotEmpty()) item {
                    HfPrimaryButton(
                        if (includeConnected && connectedLabel != null) (if (en) "Create 2 programs" else "2 program oluştur") else if (en) "Create ${selectedRegion.second} program" else "${selectedRegion.second} programı oluştur",
                        { onCreateProgram(selectedRegion.first, selectedRegion.second, if (includeConnected) connectedKey?.let { it to connectedLabel!! } else null) },
                        Modifier.fillMaxWidth(),
                        Icons.Default.Add,
                    )
                }
            }
        }
    }
    selectedExercise?.let { exercise ->
        AlertDialog(
            onDismissRequest = { selectedExercise = null },
            title = { Text(exercise.name) },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ExerciseMotionPlayer(exercise.imageUrls, exercise.name, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp))) }
                item { Text(if (en) "How to perform" else "Nasıl yapılır?", style = MaterialTheme.typography.titleMedium) }
                items(exercise.instructions.take(3).size) { index -> Text("${index + 1}. ${exercise.instructions[index]}") }
            } },
            confirmButton = { TextButton(onClick = { selectedExercise = null }) { Text(if (en) "Close" else "Kapat") } },
        )
    }
}

private data class MuscleRegion(val key: String, val label: String, val emoji: String)

/**
 * Bir bölgeyi antrenmanda sık birlikte çalışılan "sinerjist" bölgesine eşler
 * (ör. Göğüs günü genelde Arka Kol da çalışır). Karşılıklı olması şart değil —
 * Karın ve Boyun gibi tek başına çalışılan bölgelerin bağlantısı yok.
 */
private val CONNECTED_REGIONS: Map<String, String> = mapOf(
    "chest" to "triceps", "triceps" to "chest",
    "back" to "biceps", "lats" to "biceps", "biceps" to "back",
    "shoulders" to "triceps",
    "legs" to "glutes", "glutes" to "legs",
    "calves" to "legs", "abductors" to "glutes",
    "forearms" to "biceps",
    "traps" to "neck", "neck" to "traps",
)

/** The 17 distinct muscles stored in exercises.json, plus the useful aggregate back entry. */
private fun regionalMuscleRegions(en: Boolean) = if (en) listOf(
    MuscleRegion("chest", "Chest", "🫁"), MuscleRegion("back", "Back", "🧍"), MuscleRegion("lats", "Lats", "🤸"), MuscleRegion("traps", "Traps", "🙆"), MuscleRegion("neck", "Neck", "🙋"), MuscleRegion("shoulders", "Shoulders", "🤷"), MuscleRegion("biceps", "Front arm", "💪"), MuscleRegion("triceps", "Back arm", "🦾"), MuscleRegion("forearms", "Wrist", "✋"), MuscleRegion("abdominals", "Abs", "🧘"), MuscleRegion("legs", "Leg", "🦵"), MuscleRegion("glutes", "Hip", "🧍‍♀️"), MuscleRegion("calves", "Calf", "🦿"), MuscleRegion("abductors", "Outer hip", "🕺"),
) else listOf(
    MuscleRegion("chest", "Göğüs", "🫁"), MuscleRegion("back", "Sırt", "🧍"), MuscleRegion("lats", "Kanat", "🤸"), MuscleRegion("traps", "Trapez", "🙆"), MuscleRegion("neck", "Boyun", "🙋"), MuscleRegion("shoulders", "Omuz", "🤷"), MuscleRegion("biceps", "Ön kol", "💪"), MuscleRegion("triceps", "Arka kol", "🦾"), MuscleRegion("forearms", "Bilek", "✋"), MuscleRegion("abdominals", "Karın", "🧘"), MuscleRegion("legs", "Bacak", "🦵"), MuscleRegion("glutes", "Kalça", "🧍‍♀️"), MuscleRegion("calves", "Baldır", "🦿"), MuscleRegion("abductors", "Dış kalça", "🕺"),
)

@Composable
private fun WeekStrip() {
    val days = listOf("PZT" to "12", "SAL" to "13", "ÇAR" to "14", "PER" to "15", "CUM" to "16", "CMT" to "17", "PZR" to "18")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days) { (day, date) ->
            val selected = day == "PZT"
            Column(
                Modifier.width(52.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (selected) HedefitColors.Lime else HedefitColors.Surface)
                    .clickable { }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(day, color = if (selected) HedefitColors.OnLime else HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(date, color = if (selected) HedefitColors.OnLime else HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WorkoutDayCard(workout: WorkoutDayUi, onStartWorkout: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    HedefitCard(modifier.fillMaxWidth(), onClick = onStartWorkout) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(workout.day, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(workout.title, style = MaterialTheme.typography.headlineSmall)
                }
                Text("%${(workout.progress * 100).toInt()}", color = HedefitColors.Lime, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { workout.progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = HedefitColors.Lime,
                trackColor = HedefitColors.Divider,
            )
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    workout.exercises.forEach { ExerciseRow(it) }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(workout.exercises) { exercise -> ExercisePreview(exercise) }
                }
            }
        }
    }
}

@Composable
private fun ExercisePreview(exercise: ExerciseUi) {
    Column(Modifier.width(116.dp)) {
        Image(
            painterResource(exercise.image),
            contentDescription = exercise.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(11.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(exercise.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        Text(exercise.prescription, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseUi) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(
            painterResource(exercise.image), exercise.name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(exercise.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            Text(exercise.prescription, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ActiveWorkoutScreen(
    onBack: () -> Unit,
    exercises: List<WorkoutExerciseData>,
    previousPerformance: Map<String, List<PreviousSetData>>,
    saving: Boolean,
    language: String = "tr",
    onFinish: (durationSeconds: Int, calories: Int, sets: List<WorkoutSetInput>, feedback: WorkoutFeedbackData) -> Unit,
    onRequestReplacementCandidate: (currentExerciseId: String, reason: String, discomfortArea: String?, (ExerciseReplacementCandidate?) -> Unit) -> Unit = { _, _, _, _ -> },
    onApplyReplacementCandidate: (ExerciseReplacementCandidate) -> Unit = {},
    replacementCandidate: ExerciseReplacementCandidate? = null,
    replacementBusy: Boolean = false,
    chatMessages: List<ChatMessageState> = emptyList(),
    chatBusy: Boolean = false,
    onSendChatMessage: (String, WorkoutCoachContext?) -> Unit = { _, _ -> },
    onExecuteCoachAction: (CoachActionData) -> Unit = {},
) {
    val en = language == "en"
    val context = LocalContext.current
    val sessionStore = remember { ActiveWorkoutStore(context.applicationContext) }
    val restNotifier = remember { AndroidRestCompletionNotifier(context.applicationContext) }
    val restored = remember { sessionStore.read() }
    var sessionExercises by remember { mutableStateOf(restored?.exercises ?: exercises) }
    var showReplacementSheet by remember { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var prepared by rememberSaveable { mutableStateOf(restored != null) }
    var weight by rememberSaveable { mutableIntStateOf(restored?.weight ?: 0) }
    var weightTouched by rememberSaveable { mutableStateOf(false) }
    var reps by rememberSaveable { mutableIntStateOf(restored?.reps ?: prescribedStartingReps(sessionExercises.firstOrNull()?.reps)) }
    var rpe by rememberSaveable { mutableIntStateOf(restored?.rpe ?: 7) }
    var setType by rememberSaveable { mutableStateOf(restored?.setType ?: "normal") }
    var note by rememberSaveable { mutableStateOf(restored?.note.orEmpty()) }
    var exerciseIndex by rememberSaveable { mutableIntStateOf(restored?.exerciseIndex ?: 0) }
    var currentSet by rememberSaveable { mutableIntStateOf(restored?.currentSet ?: 1) }
    var restDeadlineEpochMs by rememberSaveable { mutableLongStateOf(restored?.restDeadlineEpochMs ?: 0L) }
    var restSeconds by rememberSaveable { mutableIntStateOf(restored?.restPausedSeconds?.takeIf { it > 0 } ?: remainingRestSeconds(restDeadlineEpochMs, System.currentTimeMillis())) }
    var restTimerPaused by rememberSaveable { mutableStateOf((restored?.restPausedSeconds ?: 0) > 0) }
    var timerSeconds by rememberSaveable { mutableIntStateOf(0) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }
    val startedAt by rememberSaveable { mutableLongStateOf(restored?.startedAt ?: System.currentTimeMillis()) }
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(((System.currentTimeMillis() - startedAt) / 1_000L).toInt().coerceAtLeast(0)) }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    var showNoSetsWarning by rememberSaveable { mutableStateOf(false) }
    var showFinishConfirmation by rememberSaveable { mutableStateOf(false) }
    var personalRecord by rememberSaveable { mutableStateOf(false) }
    var completedSetStates by rememberSaveable { mutableStateOf<List<String>>(restored?.completedSets.orEmpty().map(WorkoutSetInput::toSavedWorkoutSet)) }
    val exercise = sessionExercises.getOrNull(exerciseIndex)
    val totalSets = exercise?.sets?.coerceAtLeast(1) ?: 1

    BackHandler { onBack() }

    LaunchedEffect(exercise?.id, previousPerformance) {
        if (!weightTouched) weight = exercise?.let { previousPerformance[it.id]?.firstOrNull()?.weightKg?.toInt() } ?: 0
    }

    DisposableEffect(context) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val speech = remember { TextToSpeech(context) {} }
    DisposableEffect(speech) {
        speech.language = if (en) Locale.US else Locale("tr", "TR")
        onDispose { speech.shutdown() }
    }

    LaunchedEffect(restDeadlineEpochMs, restTimerPaused, paused) {
        while (restDeadlineEpochMs > 0L && !restTimerPaused && !paused) {
            val remaining = remainingRestSeconds(restDeadlineEpochMs, System.currentTimeMillis())
            restSeconds = remaining
            if (remaining == 0) {
                restDeadlineEpochMs = 0L
                restNotifier.cancel()
                vibrate(context)
                speech.speak(if (en) "Rest complete. You are ready for the next set." else "Dinlenme tamamlandı. Sıradaki sete hazırsın.", TextToSpeech.QUEUE_FLUSH, null, "rest-finished")
                break
            }
            delay(500)
        }
    }

    LaunchedEffect(exerciseIndex, currentSet, weight, reps, rpe, setType, note, completedSetStates, restDeadlineEpochMs, restSeconds, restTimerPaused) {
        if (sessionExercises.isNotEmpty()) sessionStore.write(ActiveWorkoutSnapshot(
            startedAt, sessionExercises, exerciseIndex, currentSet, weight, reps, rpe, setType, note,
            completedSetStates.mapNotNull(::savedWorkoutSet), restDeadlineEpochMs, if (restTimerPaused) restSeconds else 0,
        ))
    }

    LaunchedEffect(timerSeconds, timerRunning, paused) {
        if (timerRunning && timerSeconds > 0 && !paused) {
            delay(1_000)
            timerSeconds--
            if (timerSeconds == 0) {
                timerRunning = false
                vibrate(context)
                speech.speak(if (en) "Timer complete" else "Süre tamamlandı", TextToSpeech.QUEUE_FLUSH, null, "workout-timer-finished")
            }
        }
    }

    LaunchedEffect(startedAt, paused) {
        while (!paused) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000L).toInt().coerceIn(0, 24 * 60 * 60)
            delay(1_000L)
        }
    }

    fun recordSetAndContinue() {
        val current = exercise ?: return
        val existing = completedSetStates.mapNotNull(::savedWorkoutSet).any { it.exerciseId == current.id && it.setNumber == currentSet }
        if (existing) return
        val old = previousPerformance[current.id]?.getOrNull(currentSet - 1)
        personalRecord = old != null && ((old.weightKg != null && weight > old.weightKg) || (weight.toDouble() >= (old.weightKg ?: weight.toDouble()) && reps > (old.reps ?: reps)))
        val completed = WorkoutSetInput(current.id, current.name, exerciseIndex + 1, currentSet, weight.toDouble(), reps, null, rpe, setType, note)
        completedSetStates = completedSetStates + completed.toSavedWorkoutSet()
        vibrate(context)
        note = ""
        setType = "normal"
        if (currentSet < totalSets) {
            currentSet++
            restSeconds = smartRestSeconds(current, rpe)
            restTimerPaused = false
            restDeadlineEpochMs = System.currentTimeMillis() + restSeconds * 1_000L
            restNotifier.schedule(restDeadlineEpochMs)
        } else if (exerciseIndex < sessionExercises.lastIndex) {
            exerciseIndex++
            currentSet = 1
            val nextPrevious = previousPerformance[sessionExercises[exerciseIndex].id]?.firstOrNull()
            weightTouched = false
            weight = nextPrevious?.weightKg?.toInt() ?: 0
            reps = nextPrevious?.reps ?: prescribedStartingReps(sessionExercises[exerciseIndex].reps)
            restSeconds = 0
        } else showFeedback = true
    }

    fun skipExercise() {
        if (exerciseIndex < sessionExercises.lastIndex) {
            exerciseIndex++
            currentSet = 1
            weightTouched = false
            weight = previousPerformance[sessionExercises[exerciseIndex].id]?.firstOrNull()?.weightKg?.toInt() ?: 0
            reps = prescribedStartingReps(sessionExercises[exerciseIndex].reps)
            restSeconds = 0
            restDeadlineEpochMs = 0L
            restNotifier.cancel()
        } else if (!canFinishWorkout(completedSetStates.size)) {
            showNoSetsWarning = true
        } else {
            showFeedback = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(HedefitColors.Background)) {
        val isWide = maxWidth >= 760.dp
        val safePadding = WindowInsets.safeDrawing.asPaddingValues()
        Column(
            Modifier.fillMaxSize().padding(safePadding).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActiveTopBar(onBack, { showFinishConfirmation = true }, paused, en, elapsedSeconds) { paused = !paused }
            if (paused) HedefitCard(Modifier.fillMaxWidth().widthIn(max = 1040.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Pause, null, tint = HedefitColors.Warning)
                    Spacer(Modifier.width(10.dp))
                    Text(if (en) "Workout paused" else "Antrenman duraklatıldı", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { paused = false }) { Text(if (en) "Resume" else "Devam et", color = HedefitColors.Lime) }
                }
            }
            if (isWide) {
                Row(
                    Modifier.fillMaxWidth().widthIn(max = 1040.dp).weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActiveWorkoutHero(currentSet, exercise, Modifier.weight(1.1f).fillMaxHeight(.9f))
                    ActiveControls(weight, reps, rpe, setType, note, restSeconds, restTimerPaused, timerSeconds, timerRunning, currentSet, totalSets, exerciseIndex, sessionExercises.size, previousPerformance[exercise?.id].orEmpty(), Modifier.weight(.9f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        onWeight = { weightTouched = true; weight = (weight + it).coerceAtLeast(0) },
                        onReps = { reps = (reps + it).coerceAtLeast(1) },
                        onRpe = { rpe = it }, onSetType = { setType = it }, onNote = { note = it },
                        onRest = { restSeconds = 0; restDeadlineEpochMs = 0L; restTimerPaused = false; restNotifier.cancel() },
                        onAddRest = { restSeconds += 30; restDeadlineEpochMs = if (restTimerPaused) 0L else adjustedRestDeadline(restDeadlineEpochMs, System.currentTimeMillis(), 30); if (!restTimerPaused) restNotifier.schedule(restDeadlineEpochMs) },
                        onToggleRest = { restTimerPaused = !restTimerPaused; if (restTimerPaused) { restDeadlineEpochMs = 0L; restNotifier.cancel() } else { restDeadlineEpochMs = System.currentTimeMillis() + restSeconds * 1_000L; restNotifier.schedule(restDeadlineEpochMs) } }, onComplete = ::recordSetAndContinue,
                        onTimer = { seconds -> timerSeconds = seconds; timerRunning = seconds > 0 }, onToggleTimer = { timerRunning = !timerRunning },
                        onSkipExercise = ::skipExercise,
                        onOpenReplacement = { showReplacementSheet = true },
                        saving = saving || paused, personalRecord = personalRecord,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { ActiveWorkoutHero(currentSet, exercise, Modifier.fillMaxWidth()) }
                    item {
                        ActiveControls(weight, reps, rpe, setType, note, restSeconds, restTimerPaused, timerSeconds, timerRunning, currentSet, totalSets, exerciseIndex, sessionExercises.size, previousPerformance[exercise?.id].orEmpty(), Modifier.fillMaxWidth(),
                            onWeight = { weightTouched = true; weight = (weight + it).coerceAtLeast(0) },
                            onReps = { reps = (reps + it).coerceAtLeast(1) },
                            onRpe = { rpe = it }, onSetType = { setType = it }, onNote = { note = it },
                            onRest = { restSeconds = 0; restDeadlineEpochMs = 0L; restTimerPaused = false; restNotifier.cancel() },
                            onAddRest = { restSeconds += 30; restDeadlineEpochMs = if (restTimerPaused) 0L else adjustedRestDeadline(restDeadlineEpochMs, System.currentTimeMillis(), 30); if (!restTimerPaused) restNotifier.schedule(restDeadlineEpochMs) },
                            onToggleRest = { restTimerPaused = !restTimerPaused; if (restTimerPaused) { restDeadlineEpochMs = 0L; restNotifier.cancel() } else { restDeadlineEpochMs = System.currentTimeMillis() + restSeconds * 1_000L; restNotifier.schedule(restDeadlineEpochMs) } }, onComplete = ::recordSetAndContinue,
                            onTimer = { seconds -> timerSeconds = seconds; timerRunning = seconds > 0 }, onToggleTimer = { timerRunning = !timerRunning },
                            onSkipExercise = ::skipExercise,
                            onOpenReplacement = { showReplacementSheet = true },
                            saving = saving || paused, personalRecord = personalRecord,
                        )
                    }
                }
            }
        }
    }

    if (showReplacementSheet && exercise != null) {
        ExerciseReplacementSheet(
            exercise = exercise,
            candidate = replacementCandidate,
            isBusy = replacementBusy,
            onDismiss = { showReplacementSheet = false },
            onRequestReplacement = { reason, discomfortArea ->
                onRequestReplacementCandidate(exercise.id, reason, discomfortArea) { _ -> }
            },
            onApplyReplacement = { rep ->
                val updated = sessionExercises.toMutableList()
                val newExercise = exercise.copy(
                    id = rep.replacementExerciseId,
                    name = rep.replacementExerciseName,
                    sets = rep.sets,
                    reps = rep.reps,
                    restSeconds = rep.restSeconds,
                )
                updated[exerciseIndex] = newExercise
                sessionExercises = updated
                weight = 0
                reps = prescribedStartingReps(rep.reps)
                showReplacementSheet = false
            },
            locale = language,
        )
    }

    if (showFeedback) WorkoutFeedbackDialog(saving, onDismiss = { showFeedback = false }) { feedback ->
        val duration = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        sessionStore.clear()
        restNotifier.cancel()
        onFinish(duration, (duration / 60 * 7).coerceAtLeast(60), completedSetStates.mapNotNull(::savedWorkoutSet), feedback)
    }
    if (showNoSetsWarning) AlertDialog(
        onDismissRequest = { showNoSetsWarning = false },
        title = { Text(if (en) "No completed sets" else "Tamamlanan set yok") },
        text = { Text(if (en) "Complete at least one set before finishing the workout." else "Antrenmanı bitirmeden önce en az bir set tamamla.") },
        confirmButton = { TextButton(onClick = { showNoSetsWarning = false }) { Text(if (en) "Continue workout" else "Antrenmana devam et", color = HedefitColors.Lime) } },
    )
    if (!prepared) WarmupDialog(en) { prepared = true }
    if (showFinishConfirmation) AlertDialog(
        onDismissRequest = { showFinishConfirmation = false },
        title = { Text(if (en) "Finish workout?" else "Antrenmanı Bitir?") },
        text = { Text("${completedSetStates.size} ${if (en) "sets completed" else "set tamamlandı"}\n${formatWorkoutClock(elapsedSeconds)}") },
        dismissButton = { TextButton(onClick = { showFinishConfirmation = false }) { Text(if (en) "Keep training" else "Devam Et") } },
        confirmButton = { TextButton(onClick = {
            showFinishConfirmation = false
            if (canFinishWorkout(completedSetStates.size)) showFeedback = true else showNoSetsWarning = true
        }) { Text(if (en) "Finish workout" else "Antrenmanı Bitir", color = HedefitColors.Coral) } },
    )
}

@Composable
private fun ActiveTopBar(onBack: () -> Unit, onFinish: () -> Unit, paused: Boolean, en: Boolean, elapsedSeconds: Int, onPause: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (en) "Minimize" else "Küçült") }
        Column(Modifier.weight(1f)) { Text(if (en) "Active Workout" else "Aktif Antrenman", style = MaterialTheme.typography.titleLarge); Text(formatWorkoutClock(elapsedSeconds), color = HedefitColors.Lime, style = MaterialTheme.typography.labelMedium) }
        IconButton(onClick = onPause) { Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (paused) (if (en) "Resume" else "Devam et") else (if (en) "Pause" else "Duraklat"), tint = HedefitColors.Lime) }
        TextButton(onClick = onFinish) { Text(if (en) "Finish" else "Bitir", color = HedefitColors.Coral) }
    }
}

private fun formatWorkoutClock(seconds: Int) = "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)

@Composable
private fun ActiveWorkoutHero(currentSet: Int, exercise: WorkoutExerciseData?, modifier: Modifier) {
    HedefitCard(modifier, contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            if (exercise != null) ExerciseMotionPlayer(
                exercise.id,
                exercise.name,
                exercise.name,
                Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(HedefitColors.Background.copy(alpha = .18f), Color.Transparent, HedefitColors.Background.copy(alpha = .94f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(exercise?.name ?: "Program yüklenemedi", style = MaterialTheme.typography.headlineLarge)
                Text("$currentSet / ${exercise?.sets ?: 4} set", color = HedefitColors.Lime, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun ActiveControls(
    weight: Int,
    reps: Int,
    rpe: Int,
    setType: String,
    note: String,
    restSeconds: Int,
    restTimerPaused: Boolean,
    timerSeconds: Int,
    timerRunning: Boolean,
    currentSet: Int,
    totalSets: Int,
    exerciseIndex: Int,
    exerciseCount: Int,
    previous: List<PreviousSetData>,
    modifier: Modifier,
    onWeight: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onRpe: (Int) -> Unit,
    onSetType: (String) -> Unit,
    onNote: (String) -> Unit,
    onRest: () -> Unit,
    onAddRest: () -> Unit,
    onToggleRest: () -> Unit,
    onTimer: (Int) -> Unit,
    onToggleTimer: () -> Unit,
    onComplete: () -> Unit,
    onSkipExercise: () -> Unit,
    onOpenReplacement: () -> Unit = {},
    saving: Boolean,
    personalRecord: Boolean,
) {
    var showPlateCalculator by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hareket ${exerciseIndex + 1}/$exerciseCount • Set $currentSet/$totalSets", color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge)
        if (previous.isNotEmpty()) {
            val old = previous.getOrNull(currentSet - 1) ?: previous.last()
            HedefitCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Whatshot, null, tint = HedefitColors.Warning)
                    Spacer(Modifier.width(9.dp))
                    Text("Önceki: ${old.weightKg?.let { "${it.toInt()} kg" } ?: "vücut ağırlığı"} × ${old.reps ?: "—"} • RPE ${old.rpe ?: "—"}", color = HedefitColors.TextSecondary)
                }
            }
            progressiveOverloadTip(old, currentSet)?.let { tip ->
                Text(tip, color = HedefitColors.Lime, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (personalRecord) HedefitCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Whatshot, null, tint = HedefitColors.Lime)
                Spacer(Modifier.width(9.dp))
                Text("Yeni kişisel rekor! Son set önceki performansını geçti.", color = HedefitColors.Lime, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CounterCard("$weight kg", "Ağırlık", Modifier.weight(1f), { onWeight(-5) }, { onWeight(5) })
            CounterCard("$reps tekrar", "Tekrar", Modifier.weight(1f), { onReps(-1) }, { onReps(1) })
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("warmup" to "Isınma", "normal" to "Normal", "superset" to "Süper", "dropset" to "Drop", "failure" to "Tükeniş")) { (value, label) ->
                FilterChip(selected = setType == value, onClick = { onSetType(value) }, label = { Text(label, style = MaterialTheme.typography.labelMedium) })
            }
        }
        HedefitCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text("Efor (RPE)", Modifier.weight(1f)); Text("$rpe/10", color = HedefitColors.Lime, fontWeight = FontWeight.Bold) }
                Slider(value = rpe.toFloat(), onValueChange = { onRpe(it.toInt()) }, valueRange = 1f..10f, steps = 8)
            }
        }
        if (restSeconds > 0) HedefitCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ProgressRing(restSeconds / 180f, Modifier.size(110.dp), 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Dinlenme", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text("%02d:%02d".format(restSeconds / 60, restSeconds % 60), style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Akıllı süre: hareketin zorluğu ve RPE'ye göre ayarlandı.", color = HedefitColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onAddRest) { Text("+30 sn") }
                        TextButton(onClick = onToggleRest) { Text(if (restTimerPaused) "Devam" else "Duraklat") }
                    }
                    OutlineAction("Sonraki Set / Atla", onRest)
                }
            }
        }
        HedefitCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kronometre", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("%02d:%02d".format(timerSeconds / 60, timerSeconds % 60), color = HedefitColors.Lime, style = MaterialTheme.typography.headlineSmall)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(listOf(30, 60, 90, 120)) { seconds ->
                        FilterChip(selected = timerSeconds == seconds, onClick = { onTimer(seconds) }, label = { Text("${seconds / 60}:${"%02d".format(seconds % 60)}") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineAction(if (timerRunning) "Duraklat" else "Başlat", onToggleTimer, modifier = Modifier.weight(1f))
                    OutlineAction("Sıfırla", { onTimer(0) }, modifier = Modifier.weight(1f))
                }
            }
        }
        OutlinedTextField(note, onNote, label = { Text("Set notu (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (weight >= 15) OutlineAction("Plaka Hesaplayıcı", onClick = { showPlateCalculator = true })
        PrimaryButton(if (saving) "Kaydediliyor…" else if (currentSet >= totalSets && exerciseIndex >= exerciseCount - 1) "Antrenmanı Değerlendir" else "Seti Tamamla", if (saving || restSeconds > 0) ({}) else onComplete, icon = Icons.Default.Check)
        HedefitCard(Modifier.fillMaxWidth(), onClick = onSkipExercise) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SkipNext, null, tint = HedefitColors.Lime)
                Spacer(Modifier.width(10.dp))
                Text("Hareketi atla", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
        }
        SecondaryButton(
            text = "Hareketi Değiştir",
            onClick = onOpenReplacement,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (showPlateCalculator) PlateCalculatorDialog(weight) { showPlateCalculator = false }
}

@Composable
private fun WarmupDialog(en: Boolean, onReady: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (en) "Warm-up" else "Isınma ve hazırlık") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(if (en) "• 3–5 min light cardio\n• Dynamic joint mobility\n• 1–2 light sets for the first move" else "• 3–5 dk hafif kardiyo\n• Eklemlere dinamik mobilite\n• İlk harekette 1–2 hafif ısınma seti", color = HedefitColors.TextSecondary)
            Text(if (en) "Choose Warm-up as the set type to log preparation sets." else "Set türünden ‘Isınma’yı seçerek hazırlık setlerini ayrıca kaydedebilirsin.", color = HedefitColors.Lime)
        } },
        confirmButton = { Button(onClick = onReady, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "I'm ready" else "Hazırım, başla") } },
    )
}

@Composable
private fun PlateCalculatorDialog(targetWeight: Int, onDismiss: () -> Unit) {
    var barWeight by remember { mutableIntStateOf(20) }
    val perSide = ((targetWeight - barWeight).coerceAtLeast(0) / 2.0)
    val plates = remember(perSide) {
        var remaining = perSide
        buildList {
            listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25).forEach { plate ->
                val count = (remaining / plate).toInt()
                repeat(count) { add(plate) }
                remaining -= count * plate
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plaka Hesaplayıcı") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hedef: $targetWeight kg", color = HedefitColors.Lime, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 20).forEach { value -> FilterChip(barWeight == value, { barWeight = value }, label = { Text("$value kg bar") }) } }
            Text(if (plates.isEmpty()) "Yalnızca barı kullan." else "Her tarafa: ${plates.joinToString(" + ") { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }} kg", color = HedefitColors.TextSecondary)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam", color = HedefitColors.Lime) } },
    )
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
}

@Composable
private fun WorkoutFeedbackDialog(saving: Boolean, onDismiss: () -> Unit, onConfirm: (WorkoutFeedbackData) -> Unit) {
    var difficulty by remember { mutableStateOf("Uygun") }
    var fatigue by remember { mutableIntStateOf(3) }
    var pain by remember { mutableStateOf("Yok") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Antrenman nasıldı?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fit Koç sonraki planını bu geri bildirime göre düzenler. Bitirdikten sonra 3–5 dk hafif soğuma ve esneme yap.", color = HedefitColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("Kolay", "Uygun", "Zor").forEach { value -> FilterChip(difficulty == value, { difficulty = value }, label = { Text(value) }) } }
            Text("Yorgunluk: $fatigue/5")
            Slider(fatigue.toFloat(), { fatigue = it.toInt() }, valueRange = 1f..5f, steps = 3)
            Text("Ağrı / hassasiyet")
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Yok", "Bel", "Diz", "Omuz").forEach { value -> FilterChip(pain == value, { pain = value }, label = { Text(value) }) } }
            OutlinedTextField(note, { note = it }, label = { Text("Koça not") }, modifier = Modifier.fillMaxWidth())
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Geri dön") } },
        confirmButton = { Button(enabled = !saving, onClick = { onConfirm(WorkoutFeedbackData(difficulty, fatigue, listOf(pain), note)) }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (saving) "Kaydediliyor…" else "Bitir ve uyarla") } },
    )
}

private fun List<WorkoutExerciseData>.toWorkoutDays(): List<WorkoutDayUi> {
    if (isEmpty()) return emptyList()
    val dayNames = listOf("Pazartesi" to "Üst Vücut", "Çarşamba" to "Alt Vücut", "Cuma" to "Tüm Vücut")
    val chunkSize = ceil(size / 3.0).toInt().coerceAtLeast(1)
    return chunked(chunkSize).take(3).mapIndexed { index, chunk ->
        val (day, fallbackTitle) = dayNames[index]
        WorkoutDayUi(day, chunk.firstOrNull()?.area ?: fallbackTitle, 0f, chunk.map {
            ExerciseUi(it.name, "${it.sets} × ${it.reps}", imageForExercise(it.name))
        })
    }
}

private fun imageForExercise(name: String): Int {
    val folded = name.lowercase()
    return when {
        "squat" in folded -> R.drawable.exercise_squat
        "deadlift" in folded -> R.drawable.exercise_deadlift
        "lat" in folded || "pulldown" in folded || "row" in folded -> R.drawable.exercise_lat_pulldown
        "leg press" in folded -> R.drawable.exercise_leg_press
        "curl" in folded -> R.drawable.exercise_curl
        else -> R.drawable.exercise_bench_press
    }
}

private fun smartRestSeconds(exercise: WorkoutExerciseData, rpe: Int): Int {
    val name = exercise.name.lowercase()
    val compound = listOf("squat", "bench", "deadlift", "row", "press", "pull-up", "barbell").any { it in name }
    val base = if (compound) 120 else 75
    return (base + if (rpe >= 9) 30 else if (rpe <= 5) -15 else 0).coerceIn(60, 150)
}

private fun progressiveOverloadTip(previous: PreviousSetData, currentSet: Int): String? {
    val weight = previous.weightKg ?: return null
    val reps = previous.reps ?: return null
    return when {
        reps >= 12 && (previous.rpe ?: 8) <= 8 -> "Yük önerisi: $weight kg yerine ${if (weight >= 50) weight + 2.5 else weight + 1.25} kg ile 8–10 dene."
        reps < 12 -> "Yük önerisi: $weight kg ile ${reps + 1} tekrar hedefle."
        else -> "Yük önerisi: $weight kg ile formu koruyarak tekrar et."
    }
}

@Composable
private fun CounterCard(value: String, label: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    HedefitCard(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RoundControl(Icons.Default.Remove, "Azalt", onMinus)
                Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                RoundControl(Icons.Default.Add, "Artır", onPlus)
            }
        }
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(HedefitColors.SurfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = HedefitColors.Lime, modifier = Modifier.size(20.dp)) }
}

private fun programStyle(source: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> = when (source) {
    "regional" -> Icons.Default.AccessibilityNew to HedefitColors.Coral
    "assessment" -> Icons.Default.AutoAwesome to HedefitColors.Lime
    "push_pull_template" -> Icons.Default.GridView to HedefitColors.Water
    else -> Icons.Default.Edit to HedefitColors.Warning
}

private fun programSourceLabel(source: String, en: Boolean) = when (source) {
    "regional" -> if (en) "Body part" else "Bölgesel"
    "assessment" -> "Hedefit AI"
    "push_pull_template" -> if (en) "Template" else "Hazır program"
    else -> if (en) "Custom" else "Kendi programın"
}

@Composable
private fun ModuleCard(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, description: String, bullets: List<String>, tag: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    HedefitCard(Modifier.fillMaxWidth(), onClick = if (enabled) onClick else null, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HfIconBadge(icon, tint, 44.dp, 22.dp, 14.dp)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(description, color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.KeyboardArrowRight, null, tint = HedefitColors.TextMuted)
            }
            if (tag != null) HfPill(tag, tint)
            bullets.forEach { bullet ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, tint = tint, modifier = Modifier.size(15.dp))
                    Text(bullet, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ProgramCreateHub(padding: PaddingValues, en: Boolean, onBack: () -> Unit, onOpen: (String) -> Unit, onOpenRegional: () -> Unit) {
    ScreenContainer(padding) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { HfScreenHeader(if (en) "Create a program" else "Program oluştur", if (en) "Choose the way that suits you" else "Sana uygun yolu seç", onBack = onBack) }
            item { ModuleCard(Icons.Default.Bolt, HedefitColors.Coral, if (en) "Quick workout" else "Hızlı antrenman", if (en) "Right now, based on your time and energy" else "Şu an, süren ve yorgunluğuna göre", listOf(if (en) "Set duration, fatigue and target area" else "Süre, yorgunluk ve bölge seç", if (en) "Program is ready in seconds" else "Program saniyeler içinde hazır"), if (en) "FASTEST" else "EN HIZLI") { onOpen("quick") } }
            item { ModuleCard(Icons.Default.AutoAwesome, HedefitColors.Lime, if (en) "Create with Hedefit AI" else "Hedefit AI ile oluştur", if (en) "A personal plan from your profile" else "Profiline göre kişisel program", listOf(if (en) "Uses your goal, level and equipment" else "Hedef, seviye ve ekipmanını dikkate alır", if (en) "Adapts as you progress" else "İlerlemene göre uyarlanır"), if (en) "RECOMMENDED" else "ÖNERİLEN") { onOpen("ai") } }
            item { ModuleCard(Icons.Default.GridView, HedefitColors.Water, if (en) "Ready programs" else "Hazır programlar", if (en) "Pick a proven template" else "Kanıtlanmış bir şablon seç", listOf(if (en) "Push / Pull / Legs and Full Body" else "İtiş / Çekiş / Bacak ve Tüm Vücut", if (en) "Every movement stays editable" else "Tüm hareketler sonradan düzenlenebilir")) { onOpen("ready") } }
            item { ModuleCard(Icons.Default.Edit, HedefitColors.Warning, if (en) "Build a program" else "Program yap", if (en) "Name and order your own sessions" else "Antrenmanlarını kendin adlandır ve sırala", listOf(if (en) "Pick exercises from the Movement Atlas" else "Hareket Atlası'ndan hareket seç", if (en) "Set sets, reps and rest yourself" else "Set, tekrar ve dinlenmeyi sen belirle")) { onOpen("custom") } }
            item { HfDivider() }
            item { HfNavRow(Icons.Default.AccessibilityNew, HedefitColors.Coral, if (en) "Body-part programs" else "Bölgesel programlar", if (en) "Focus on a single muscle group" else "Tek bir kas grubuna odaklan", onOpenRegional) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickWorkoutPage(padding: PaddingValues, en: Boolean, busy: Boolean, onBack: () -> Unit, onCreate: (List<Pair<String, String>>, Int, String, String, String) -> Unit) {
    var duration by rememberSaveable { mutableIntStateOf(30) }
    var fatigue by rememberSaveable { mutableStateOf("normal") }
    var environment by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf("") }
    val allRegions = regionalMuscleRegions(en)
    var selectedRegions by rememberSaveable { mutableStateOf(setOf("chest")) }
    val durations = listOf(15, 20, 30, 45, 60)
    val fatigueOptions = listOf("dinc" to (if (en) "Fresh" else "Dinç"), "normal" to (if (en) "Normal" else "Normal"), "yorgun" to (if (en) "Tired" else "Yorgun"))
    val environmentOptions = listOf("" to (if (en) "Any" else "Fark etmez"), "gym" to (if (en) "Gym" else "Spor salonu"), "home" to (if (en) "Home" else "Ev"))
    val equipmentOptions = listOf(
        "" to (if (en) "Any" else "Tümü"),
        "bodyweight" to (if (en) "Bodyweight" else "Vücut ağırlığı"),
        "dumbbell" to (if (en) "Dumbbell" else "Dambıl"),
        "barbell" to (if (en) "Barbell" else "Halter"),
        "kettlebell" to "Kettlebell",
        "cable" to (if (en) "Cable" else "Kablo"),
        "band" to (if (en) "Band" else "Direnç bandı"),
        "machine" to (if (en) "Machine" else "Makine"),
        "pull_up_bar" to (if (en) "Pull-up bar" else "Barfiks barı"),
    )
    ScreenContainer(padding) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { HfScreenHeader(if (en) "Quick workout" else "Hızlı antrenman", if (en) "Tell us what you've got right now" else "Şu an elindekini söyle", onBack = onBack) }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (en) "How much time do you have?" else "Ne kadar süren var?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        HfChipRow { durations.forEach { value -> HfChip(if (en) "$value min" else "$value dk", duration == value, { duration = value }) } }
                    }
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (en) "How's your energy?" else "Yorgunluk durumun?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        HfSegmented(fatigueOptions.map { it.second }, fatigueOptions.indexOfFirst { it.first == fatigue }.coerceAtLeast(0), { fatigue = fatigueOptions[it].first })
                        Text(
                            when (fatigue) {
                                "yorgun" -> if (en) "Fewer sets, lighter session." else "Daha az set, hafif bir seans."
                                "dinc" -> if (en) "More sets, higher intensity." else "Daha çok set, yüksek yoğunluk."
                                else -> if (en) "Balanced volume and intensity." else "Dengeli hacim ve yoğunluk."
                            },
                            color = HedefitColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (en) "Where are you training?" else "Nerede antrenman yapıyorsun?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        HfChipRow { environmentOptions.forEach { (key, label) -> HfChip(label, environment == key, onClick = { environment = key }) } }
                    }
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (en) "What equipment do you have?" else "Hangi ekipman elinde var?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            equipmentOptions.forEach { (key, label) -> HfChip(label, equipment == key, onClick = { equipment = key }) }
                        }
                    }
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (en) "Which areas? (up to 3)" else "Hangi bölgeler? (en fazla 3)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            allRegions.forEach { region ->
                                HfChip(region.label, region.key in selectedRegions, onClick = {
                                    selectedRegions = when {
                                        region.key in selectedRegions -> selectedRegions - region.key
                                        selectedRegions.size >= 3 -> selectedRegions
                                        else -> selectedRegions + region.key
                                    }
                                })
                            }
                        }
                    }
                }
            }
            item {
                HfPrimaryButton(
                    if (busy) (if (en) "Preparing…" else "Hazırlanıyor…") else if (en) "Create my workout" else "Antrenmanımı oluştur",
                    {
                        val chosen = selectedRegions.mapNotNull { key -> allRegions.firstOrNull { it.key == key }?.let { key to it.label } }
                        if (chosen.isNotEmpty()) onCreate(chosen, duration, fatigue, environment, equipment)
                    },
                    Modifier.fillMaxWidth(),
                    Icons.Default.Bolt,
                    enabled = !busy && selectedRegions.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun AiProgramPage(padding: PaddingValues, en: Boolean, profile: ProfileData?, generating: Boolean, onBack: () -> Unit, onEditPreferences: () -> Unit, onGenerate: () -> Unit) {
    ScreenContainer(padding) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { HfScreenHeader("Hedefit AI", if (en) "Personal program from your profile" else "Profiline göre kişisel program", onBack = onBack) }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                    Column {
                        Text(if (en) "WHAT THE AI USES" else "AI'IN KULLANACAĞI BİLGİLER", color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 4.dp))
                        HfListRow(Icons.Default.Flag, HedefitColors.Lime, if (en) "Goal" else "Hedef", profile?.goal?.substringBefore(" | ")?.ifBlank { null } ?: "—", null)
                        HfDivider()
                        HfListRow(Icons.Default.Place, HedefitColors.Water, if (en) "Where you train" else "Antrenman ortamı", profile?.environment?.ifBlank { null } ?: "—", null)
                        HfDivider()
                        HfListRow(Icons.Default.FitnessCenter, HedefitColors.Warning, if (en) "Equipment" else "Ekipman", profile?.equipment?.ifBlank { null } ?: if (en) "Bodyweight" else "Vücut ağırlığı", null)
                        HfDivider()
                        HfListRow(Icons.Default.MonitorWeight, HedefitColors.Sleep, if (en) "Body" else "Vücut", listOfNotNull(profile?.age?.let { if (en) "$it y" else "$it yaş" }, profile?.heightCm?.let { "${it.toInt()} cm" }, profile?.weightKg?.let { "%.1f kg".format(it) }).joinToString(" • ").ifBlank { "—" }, null)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().background(HedefitColors.Lime.copy(alpha = .08f), RoundedCornerShape(18.dp)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HfIconBadge(Icons.Default.AutoAwesome, HedefitColors.Lime, 34.dp, 17.dp, 11.dp)
                    Text(if (en) "Your injury notes and past workouts are considered too. You can change every movement after the program is created." else "Sakatlık notların ve geçmiş antrenmanların da hesaba katılır. Program oluştuktan sonra her hareketi değiştirebilirsin.", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { HfPrimaryButton(if (generating) (if (en) "Creating…" else "Oluşturuluyor…") else if (en) "Create my program" else "Programımı oluştur", onGenerate, Modifier.fillMaxWidth(), Icons.Default.AutoAwesome, enabled = !generating) }
            item { HfPrimaryButton(if (en) "Update my preferences" else "Tercihlerimi güncelle", onEditPreferences, Modifier.fillMaxWidth(), Icons.Default.Edit, secondary = true) }
        }
    }
}

private fun readyTemplates(en: Boolean) = listOf(
    Triple("push_a", if (en) "Push A • Chest focus" else "İtiş A • Göğüs odaklı", "push"),
    Triple("push_b", if (en) "Push B • Shoulder focus" else "İtiş B • Omuz odaklı", "push"),
    Triple("pull_a", if (en) "Pull A • Back width" else "Çekiş A • Sırt genişliği", "pull"),
    Triple("pull_b", if (en) "Pull B • Back thickness" else "Çekiş B • Sırt kalınlığı", "pull"),
    Triple("leg_a", if (en) "Legs A • Quad focus" else "Bacak A • Ön bacak odaklı", "legs"),
    Triple("leg_b", if (en) "Legs B • Hamstring & glute focus" else "Bacak B • Arka bacak & kalça odaklı", "legs"),
    Triple("full_a", if (en) "Full Body A • Strength basics" else "Tüm Vücut A • Temel kuvvet", "full"),
    Triple("full_b", if (en) "Full Body B • Deadlift focus" else "Tüm Vücut B • Yerden kaldırış odaklı", "full"),
)

@Composable
private fun ReadyProgramsPage(padding: PaddingValues, en: Boolean, busy: Boolean, onBack: () -> Unit, onAdd: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val filters = listOf("all" to (if (en) "All" else "Tümü"), "push" to (if (en) "Push" else "İtiş"), "pull" to (if (en) "Pull" else "Çekiş"), "legs" to (if (en) "Legs" else "Bacak"), "full" to (if (en) "Full body" else "Tüm vücut"))
    val templates = readyTemplates(en).filter { filter == "all" || it.third == filter }
    ScreenContainer(padding) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { HfScreenHeader(if (en) "Ready programs" else "Hazır programlar", if (en) "${readyTemplates(en).size} templates" else "${readyTemplates(en).size} şablon", onBack = onBack) }
            item { HfChipRow { filters.forEach { (key, label) -> HfChip(label, filter == key, { filter = key }) } } }
            item { Text(if (en) "Only the program you choose is added. You can edit every movement afterwards." else "Yalnızca seçtiğin program eklenir. Sonrasında tüm hareketleri düzenleyebilirsin.", color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
            items(templates, key = { it.first }) { (key, title, group) ->
                val tint = when (group) { "push" -> HedefitColors.Lime; "pull" -> HedefitColors.Water; "legs" -> HedefitColors.Warning; else -> HedefitColors.Sleep }
                HfNavRow(Icons.Default.FitnessCenter, tint, title, filters.first { it.first == group }.second, onClick = if (busy) null else ({ onAdd(key) }), chevron = false) {
                    Box(Modifier.size(36.dp).background(HedefitColors.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, if (en) "Add $title" else "$title ekle", tint = HedefitColors.TextPrimary, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CustomProgramPage(padding: PaddingValues, en: Boolean, onBack: () -> Unit, onCreate: (CustomProgramDraft) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val titles = remember { mutableStateListOf("Push", "Pull", "Legs") }
    val valid = name.trim().length >= 2 && titles.isNotEmpty() && titles.all(String::isNotBlank)
    ScreenContainer(padding) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { HfScreenHeader(if (en) "Build a program" else "Program yap", if (en) "Name and order your sessions" else "Antrenmanlarını adlandır ve sırala", onBack = onBack) }
            item {
                HedefitCard(Modifier.fillMaxWidth()) {
                    OutlinedTextField(name, { name = it.take(80) }, modifier = Modifier.fillMaxWidth(), label = { Text(if (en) "Program name" else "Program adı") }, placeholder = { Text("Push Pull Legs") }, singleLine = true)
                }
            }
            item { HfSectionHeader(if (en) "Sessions" else "Antrenmanlar", if (en) "${titles.size} / 7" else "${titles.size} / 7") }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
                    Column {
                        titles.forEachIndexed { index, title ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(28.dp).background(HedefitColors.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Text("${index + 1}", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold) }
                                OutlinedTextField(title, { value -> titles[index] = value.take(40) }, modifier = Modifier.weight(1f).padding(start = 6.dp), placeholder = { Text(if (en) "Workout name" else "Antrenman adı") }, singleLine = true)
                                IconButton(enabled = index > 0, onClick = { val value = titles.removeAt(index); titles.add(index - 1, value) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.KeyboardArrowUp, if (en) "Move up" else "Yukarı taşı") }
                                IconButton(enabled = index < titles.lastIndex, onClick = { val value = titles.removeAt(index); titles.add(index + 1, value) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.KeyboardArrowDown, if (en) "Move down" else "Aşağı taşı") }
                                IconButton(enabled = titles.size > 1, onClick = { titles.removeAt(index) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.DeleteOutline, if (en) "Remove" else "Kaldır", tint = HedefitColors.Coral) }
                            }
                        }
                        if (titles.size < 7) TextButton(onClick = { titles.add("") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null, tint = HedefitColors.Lime); Spacer(Modifier.width(6.dp)); Text(if (en) "Add session" else "Antrenman ekle", color = HedefitColors.Lime, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item { Text(if (en) "After saving, choose exercises from the Movement Atlas; sets, reps, target weight and rest can be edited in the program." else "Kaydettikten sonra Hareket Atlası'ndan hareket seç; set, tekrar, hedef ağırlık ve dinlenmeyi programdan düzenle.", color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
            item { HfPrimaryButton(if (en) "Save program" else "Programı kaydet", { onCreate(CustomProgramDraft(name.trim(), titles.mapIndexed { index, title -> WorkoutProgramDayData(index + 1, title.trim()) })) }, Modifier.fillMaxWidth(), Icons.Default.Check, enabled = valid) }
        }
    }
}
