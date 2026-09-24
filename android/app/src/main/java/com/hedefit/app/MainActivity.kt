package com.hedefit.app

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hedefit.app.ui.components.HedefitAppFrame
import com.hedefit.app.ui.components.PersonalDetailsOnboardingDialog
import com.hedefit.app.ui.model.AppDestination
import com.hedefit.app.ui.screens.ActiveWorkoutScreen
import com.hedefit.app.ui.screens.AuthGateScreen
import com.hedefit.app.ui.screens.CoachScreen
import com.hedefit.app.ui.screens.HomeScreen
import com.hedefit.app.ui.screens.NutritionScreen
import com.hedefit.app.ui.screens.ProgressScreen
import com.hedefit.app.ui.screens.WorkoutPlanScreen
import com.hedefit.app.ui.screens.ProfileSettingsScreen
import com.hedefit.app.ui.screens.ProfileQuestionnaireScreen
import com.hedefit.app.ui.screens.NotificationCalendarScreen
import com.hedefit.app.ui.screens.FrozenAccountScreen
import com.hedefit.app.ui.screens.WorkoutCalendarScreen
import com.hedefit.app.ui.screens.ExerciseLibraryScreen
import com.hedefit.app.ui.screens.RouteScreen
import com.hedefit.app.ui.screens.GoalJourneyScreen
import com.hedefit.app.ui.screens.GameScreen
import com.hedefit.app.ui.screens.ManualActivityScreen
import com.hedefit.app.ui.screens.WelcomeGuideDialog
import com.hedefit.app.ui.screens.EquipmentScannerScreen
import com.hedefit.app.ui.screens.WorkoutSummaryScreen
import com.hedefit.app.shortcuts.HedefitShortcuts
import com.hedefit.app.ui.theme.HedefitTheme
import com.hedefit.app.ui.state.MainViewModel
import com.hedefit.app.data.auth.AuthState
import com.hedefit.app.data.auth.GoogleSignInManager
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.hedefit.app.ui.settings.AppPreferencesStore
import com.hedefit.app.notifications.NotificationScheduler
import com.hedefit.app.notifications.StepCounterNotification
import com.hedefit.app.widgets.HedefitWidgetData
import com.hedefit.app.route.RouteTrackingStore
import com.hedefit.app.health.HealthConnectManager
import com.hedefit.app.steps.StepSource
import com.hedefit.app.ads.AdMobManager
import com.hedefit.app.gym.ActiveWorkoutStore
import com.hedefit.app.gym.WorkoutSummary
import com.hedefit.app.gym.detectPersonalRecord

private enum class UtilityPage { Main, Profile, Questionnaire, Notifications, Calendar, ExerciseLibrary, EquipmentScanner, Route, GoalJourney, ManualActivity, Wearables }

/** Programdaki Türkçe bölge adını atlasın birincil kas filtresine çevirir. */
private fun replacementMuscle(area: String): String {
    val value = area.lowercase(java.util.Locale("tr", "TR"))
    return when {
        "göğ" in value || "chest" in value -> "chest"
        "arka kol" in value || "triceps" in value -> "triceps"
        "ön kol" in value || "bilek" in value || "forearm" in value -> "forearms"
        "biceps" in value || "pazu" in value || "kol" in value -> "biceps"
        "kanat" in value || "lat" in value -> "lats"
        "trapez" in value || "trap" in value -> "traps"
        "boyun" in value || "neck" in value -> "neck"
        "omuz" in value || "shoulder" in value -> "shoulders"
        "karın" in value || "core" in value || "ab" in value -> "abdominals"
        "dış kalça" in value || "abductor" in value -> "abductors"
        "iç bacak" in value || "adductor" in value -> "adductors"
        "kalça" in value || "glute" in value -> "glutes"
        "baldır" in value || "calf" in value -> "calves"
        "arka bacak" in value || "hamstring" in value -> "hamstrings"
        "ön bacak" in value || "quad" in value -> "quadriceps"
        "bel" in value || "lower back" in value -> "lower back"
        "sırt" in value || "back" in value -> "middle back"
        else -> area
    }
}

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        mainViewModel.syncHealthIfConnected()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferencesStore = remember { AppPreferencesStore(this@MainActivity) }
            val notificationScheduler = remember { NotificationScheduler(this@MainActivity) }
            val stepCounterNotification = remember { StepCounterNotification }
            val healthConnectManager = remember { HealthConnectManager(this@MainActivity) }
            val adMobManager = remember { AdMobManager(this@MainActivity) }
            val activeWorkoutStore = remember { ActiveWorkoutStore(this@MainActivity) }
            var preferences by remember { mutableStateOf(preferencesStore.read()) }
            var adsAllowed by remember { mutableStateOf(false) }

            fun updatePreferences(next: com.hedefit.app.ui.settings.AppPreferences) {
                preferences = next
                preferencesStore.write(next)
                notificationScheduler.apply(next)
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                updatePreferences(preferences.copy(notificationsEnabled = granted))
                if (!granted) Toast.makeText(
                    this@MainActivity,
                    if (preferences.language == "en") "Notification permission was not granted. Reminders remain off." else "Bildirim izni verilmedi. Hatırlatmalar kapalı kaldı.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            val healthPermissionLauncher = rememberLauncherForActivityResult(healthConnectManager.permissionContract()) { granted ->
                mainViewModel.loadWearables()
                if (granted.contains(healthConnectManager.stepPermission)) mainViewModel.syncHealthConnect()
                else Toast.makeText(this@MainActivity, "Health Connect adım izni verilmedi.", Toast.LENGTH_LONG).show()
                mainViewModel.checkHealthConnect()
            }
            val activityRecognitionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                mainViewModel.onActivityRecognitionPermissionChanged()
            }
            HedefitTheme(darkTheme = preferences.darkTheme, accentHue = preferences.accentHue) {
                val uiState by mainViewModel.state.collectAsState()
                var selected by rememberSaveable { mutableStateOf(when { intent?.getBooleanExtra("open_workout", false) == true -> AppDestination.Workout; intent?.getBooleanExtra("open_nutrition", false) == true -> AppDestination.Nutrition; else -> AppDestination.Home }) }
                val tabHistory = remember { mutableStateListOf<AppDestination>() }
                var lastTab by remember { mutableStateOf(selected) }
                var poppingTab by remember { mutableStateOf(false) }
                LaunchedEffect(selected) {
                    if (selected != lastTab) {
                        if (!poppingTab) { tabHistory.remove(lastTab); tabHistory.add(lastTab) }
                        tabHistory.remove(selected)
                        poppingTab = false
                        lastTab = selected
                    }
                }
                var activeWorkout by rememberSaveable { mutableStateOf(activeWorkoutStore.hasRecoverable()) }
                var activeWorkoutExercises by remember { mutableStateOf(activeWorkoutStore.read()?.exercises) }
                var workoutSummary by remember { mutableStateOf<WorkoutSummary?>(null) }
                var utilityPage by rememberSaveable { mutableStateOf(when { intent?.getBooleanExtra("open_route", false) == true -> UtilityPage.Route; intent?.getBooleanExtra("open_activity", false) == true -> UtilityPage.ManualActivity; else -> UtilityPage.Main }) }
                var googleCredentialBusy by remember { mutableStateOf(false) }
                var showWelcomeGuide by remember { mutableStateOf(!preferences.welcomeGuideSeen) }
                var openMealComposer by remember { mutableStateOf(false) }
                var healthAutoSynced by rememberSaveable { mutableStateOf(false) }
                var activityRecognitionAsked by remember { mutableStateOf(this@MainActivity.getSharedPreferences("hedefit-step-permission", android.content.Context.MODE_PRIVATE).getBoolean("activity_recognition_asked", false)) }
                val scope = rememberCoroutineScope()
                val googleSignIn = remember { GoogleSignInManager(this@MainActivity) }
                val lifecycleOwner = LocalLifecycleOwner.current

                LaunchedEffect(uiState.dashboard?.profile?.id) {
                    val profile = uiState.dashboard?.profile ?: return@LaunchedEffect
                    // İlk program yalnız hızlı başlangıç cevaplarıyla değil, 15 soruluk
                    // profil değerlendirmesiyle hazırlanır. Böylece Atlas seçimleri
                    // ekipman, sakatlık, süre ve hedefin tamamına dayanır.
                    if (profile.historyAnswers.count { it.isNotBlank() } < 15) utilityPage = UtilityPage.Questionnaire
                }
                LaunchedEffect(Unit) { mainViewModel.checkHealthConnect() }
                LaunchedEffect(lifecycleOwner, uiState.dashboard?.profile?.id) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (isActive) {
                            mainViewModel.refreshSteps()
                            delay(60_000L)
                        }
                    }
                }
                LaunchedEffect(uiState.healthConnected, uiState.stepSource) {
                    if (!uiState.healthConnected && uiState.stepSource == StepSource.UNAVAILABLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !activityRecognitionAsked) {
                        activityRecognitionAsked = true
                        this@MainActivity.getSharedPreferences("hedefit-step-permission", android.content.Context.MODE_PRIVATE).edit().putBoolean("activity_recognition_asked", true).apply()
                        activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }
                LaunchedEffect(uiState.healthConnected, uiState.dashboard?.profile?.id) {
                    if (uiState.healthConnected && uiState.dashboard != null && !healthAutoSynced) {
                        healthAutoSynced = true
                        mainViewModel.syncHealthConnect()
                    }
                }
                LaunchedEffect(uiState.dashboard?.steps, uiState.dashboard?.activeCalories, preferences.stepCounterNotificationEnabled, preferences.stepGoal) {
                    if (preferences.stepCounterNotificationEnabled) stepCounterNotification.show(this@MainActivity, uiState.dashboard?.steps ?: 0, preferences.stepGoal, uiState.dashboard?.activeCalories ?: 0)
                    else stepCounterNotification.cancel(this@MainActivity)
                }
                LaunchedEffect(uiState.dashboard, preferences.stepGoal) {
                    HedefitWidgetData.write(this@MainActivity, uiState.dashboard, RouteTrackingStore(this@MainActivity).readSummary(), preferences.stepGoal)
                }
                LaunchedEffect(uiState.dashboard?.profile?.id, preferences.stepGoal, preferences.waterGoalMl, preferences.weeklyWorkoutGoal) {
                    if (uiState.dashboard != null) mainViewModel.syncGamificationPreferences(
                        preferences.stepGoal,
                        preferences.waterGoalMl,
                        preferences.weeklyWorkoutGoal,
                        java.time.ZoneId.systemDefault().id,
                    )
                }
                LaunchedEffect(activeWorkout) { HedefitWidgetData.writeWorkoutState(this@MainActivity, activeWorkout) }
                LaunchedEffect(preferences.stepCounterNotificationEnabled) {
                    if (preferences.stepCounterNotificationEnabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                LaunchedEffect(Unit) { adMobManager.requestConsent { allowed -> adsAllowed = allowed } }

                // Sistem Toast'ı ekranın altında, tema dışı bir baloncukla çıkar.
                // Bunun yerine üstten kayan, temayla tutarlı bir bildirim damlası
                // gösteriliyor (bkz. TopNotificationBanner) — mesaj tüketildiğinde
                // (kısa bir gecikmeyle, kaybolma animasyonunun görünmesi için) state
                // temizlenir.
                var bannerMessage by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(uiState.transientMessage) {
                    uiState.transientMessage?.let {
                        bannerMessage = it
                        mainViewModel.consumeTransientMessage()
                    }
                }

                if (uiState.auth !is AuthState.SignedIn) {
                    AuthGateScreen(
                        authState = uiState.auth,
                        busy = uiState.authBusy || googleCredentialBusy,
                        googleBusy = googleCredentialBusy,
                        message = uiState.authMessage,
                        onSignIn = mainViewModel::signIn,
                        onSignUp = mainViewModel::signUp,
                        onCheckUsername = mainViewModel::checkUsername,
                        onClearMessage = mainViewModel::clearAuthMessage,
                        onGoogleSignIn = { legalAcceptance ->
                            if (!googleCredentialBusy && !uiState.authBusy) scope.launch {
                                googleCredentialBusy = true
                                runCatching { googleSignIn.requestCredential() }
                                    .onSuccess { credential ->
                                        mainViewModel.signInWithGoogle(credential.idToken, credential.rawNonce, legalAcceptance)
                                    }
                                    .onFailure { error ->
                                        mainViewModel.reportAuthError(error.message ?: "Google ile giriş yapılamadı.")
                                    }
                                googleCredentialBusy = false
                            }
                        },
                    )
                } else if (uiState.accountFrozen) {
                    FrozenAccountScreen(
                        busy = uiState.accountBusy,
                        language = preferences.language,
                        onReactivate = mainViewModel::reactivateAccount,
                        onSignOut = {
                            scope.launch { googleSignIn.clearCredentialState() }
                            mainViewModel.signOut()
                        },
                    )
                } else if (activeWorkout) {
                    ActiveWorkoutScreen(
                        onBack = { activeWorkout = false },
                        exercises = activeWorkoutExercises ?: uiState.dashboard?.workouts.orEmpty(),
                        previousPerformance = uiState.previousPerformance,
                        saving = uiState.workoutSaving,
                        language = preferences.language,
                        onFinish = { seconds, calories, sets, feedback ->
                            val history = uiState.dashboard?.exercisePerformance.orEmpty()
                            val prs = sets.groupBy { it.exerciseId }.mapNotNull { (_, exerciseSets) -> detectPersonalRecord(exerciseSets.first().exerciseName, exerciseSets, history) }
                            val areas = (activeWorkoutExercises ?: uiState.dashboard?.workouts.orEmpty()).associate { it.id to it.area }
                            workoutSummary = WorkoutSummary(uiState.dashboard?.workoutPrograms?.firstOrNull { it.isActive }?.name ?: "Antrenman", seconds, calories, sets, prs, areas)
                            mainViewModel.completeDetailedWorkout(seconds, calories, sets, feedback, activeWorkoutExercises)
                            activeWorkout = false
                            activeWorkoutExercises = null
                            adMobManager.showAtNaturalTransition(uiState.dashboard?.profile?.isPremium != true)
                        },
                        onRequestReplacementCandidate = { id, reason, area, cb ->
                            mainViewModel.requestExerciseReplacement(id, reason, activeWorkoutExercises ?: uiState.dashboard?.workouts.orEmpty(), discomfortArea = area, locale = preferences.language, onComplete = cb)
                        },
                        onApplyReplacementCandidate = mainViewModel::applyExerciseReplacement,
                        replacementCandidate = uiState.replacementCandidate,
                        replacementBusy = uiState.replacementBusy,
                        chatMessages = uiState.chatMessages,
                        chatBusy = uiState.chatBusy,
                        onSendChatMessage = { msg, ctx -> mainViewModel.sendChat(msg, preferences.language, ctx) },
                        onExecuteCoachAction = mainViewModel::executeCoachAction,
                    )
                } else if (workoutSummary != null) {
                    WorkoutSummaryScreen(requireNotNull(workoutSummary), onDone = {
                        workoutSummary = null
                        com.hedefit.app.growth.AppGrowth.maybeRequestReview(this@MainActivity, uiState.dashboard?.sessions?.size ?: 0)
                    }, language = preferences.language)
                } else if (utilityPage != UtilityPage.Main && uiState.dashboard != null) {
                    BackHandler { utilityPage = if (utilityPage == UtilityPage.Notifications) UtilityPage.Profile else UtilityPage.Main }
                    val dashboard = requireNotNull(uiState.dashboard)
                    val signedIn = uiState.auth as AuthState.SignedIn
                    when (utilityPage) {
                        UtilityPage.Profile -> ProfileSettingsScreen(
                            profile = dashboard.profile,
                            email = signedIn.session.user.email,
                            preferences = preferences,
                            saving = uiState.profileSaving,
                            avatarUploading = uiState.avatarUploading,
                            avatarPreview = uiState.avatarPreview,
                            accountBusy = uiState.accountBusy,
                            healthConnected = uiState.healthConnected,
                            healthBusy = uiState.healthBusy,
                            onBack = { utilityPage = UtilityPage.Main },
                            onPreferencesChange = ::updatePreferences,
                            onOpenQuestionnaire = { utilityPage = UtilityPage.Questionnaire },
                            onOpenNotifications = { utilityPage = UtilityPage.Notifications },
                            onOpenWearables = { utilityPage = UtilityPage.Wearables },
                            onAddShortcut = { type ->
                                val accepted = if (type.startsWith("widget_")) HedefitShortcuts.requestWidget(this@MainActivity, type) else HedefitShortcuts.request(this@MainActivity, type)
                                if (!accepted) Toast.makeText(this@MainActivity, "Bu başlatıcı ana ekrana eklemeyi desteklemiyor.", Toast.LENGTH_LONG).show()
                            },
                            onConnectHealth = {
                                if (uiState.healthConnected) mainViewModel.syncHealthConnect()
                                else healthPermissionLauncher.launch(healthConnectManager.permissions)
                            },
                            onSave = { mainViewModel.saveProfile(it) },
                            onUploadAvatar = mainViewModel::uploadAvatar,
                            onResetProgress = mainViewModel::resetProgress,
                            onFreeze = mainViewModel::freezeAccount,
                            onDelete = mainViewModel::deleteAccount,
                            onSignOut = {
                                scope.launch { googleSignIn.clearCredentialState() }
                                utilityPage = UtilityPage.Main
                                mainViewModel.signOut()
                            },
                            onReplayGuide = { utilityPage = UtilityPage.Main; showWelcomeGuide = true },
                            onRateApp = { com.hedefit.app.growth.AppGrowth.openStoreListing(this@MainActivity) },
                            onShareApp = { message -> com.hedefit.app.growth.AppGrowth.shareApp(this@MainActivity, message, preferences.language == "en") },
                            defaultShareMessage = com.hedefit.app.growth.AppGrowth.defaultShareMessage(this@MainActivity, preferences.language == "en"),
                        )
                        UtilityPage.Questionnaire -> ProfileQuestionnaireScreen(
                            profile = dashboard.profile,
                            saving = uiState.profileSaving,
                            quickStart = false,
                            onClose = { utilityPage = UtilityPage.Main },
                            onSave = { update -> mainViewModel.saveProfile(update, regeneratePlan = true) { utilityPage = UtilityPage.Main } },
                        )
                        UtilityPage.Notifications -> NotificationCalendarScreen(
                            preferences = preferences,
                            onBack = { utilityPage = UtilityPage.Profile },
                            onChange = ::updatePreferences,
                            onNotificationsEnabledChange = { enabled ->
                                val permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                when {
                                    !enabled || permissionGranted -> updatePreferences(preferences.copy(notificationsEnabled = enabled))
                                    else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )
                        UtilityPage.Calendar -> WorkoutCalendarScreen(
                            schedule = dashboard.schedule,
                            programs = dashboard.workoutPrograms,
                            onBack = { utilityPage = UtilityPage.Main },
                            onSchedule = mainViewModel::scheduleWorkout,
                            onAutoDistribute = { month, dayTimes, chosenPrograms -> mainViewModel.autoDistributeProgram(month, dayTimes, chosenPrograms, preferences.language) },
                            language = preferences.language,
                            completedDates = dashboard.sessions.mapNotNull { session -> runCatching { java.time.Instant.parse(session.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull() }.toSet(),
                        )
                        UtilityPage.ExerciseLibrary -> ExerciseLibraryScreen(
                            items = uiState.exerciseLibrary,
                            loading = uiState.exerciseLibraryBusy,
                            language = preferences.language,
                            onBack = { utilityPage = UtilityPage.Main },
                            onSearch = { search, muscle, equipment, level, environment, muscleRole, force, mechanic, category -> mainViewModel.loadExerciseLibrary(search, muscle, equipment, level, environment, muscleRole, force, mechanic, category, preferences.language) },
                            onUse = mainViewModel::useExerciseFromLibrary,
                            onStart = { item ->
                                val exercise = com.hedefit.app.data.model.WorkoutExerciseData(item.id, item.name, item.primaryMuscles.firstOrNull() ?: "Tüm Vücut", 3, "8–12", 75)
                                activeWorkoutExercises = listOf(exercise)
                                mainViewModel.loadPreviousPerformance(listOf(exercise))
                                activeWorkout = true
                            },
                            onCreateProgram = { name, exercises -> mainViewModel.createProgramFromExercises(name, exercises, preferences.language); utilityPage = UtilityPage.Main },
                        )
                        UtilityPage.EquipmentScanner -> EquipmentScannerScreen(
                            onBack = { utilityPage = UtilityPage.Main },
                            onAddExercise = { item -> mainViewModel.useExerciseFromLibrary(item); utilityPage = UtilityPage.Main },
                            exerciseCatalog = dashboard.exerciseCatalog,
                            recognitionService = com.hedefit.app.equipment.EquipmentRecognitionService(mainViewModel::recognizeEquipment),
                            language = preferences.language,
                        )
                        UtilityPage.Route -> RouteScreen(
                            onBack = { utilityPage = UtilityPage.Main },
                            onCompleted = { snapshot, activityType, title ->
                                mainViewModel.saveRoute(snapshot, activityType, title)
                                adMobManager.showAtNaturalTransition(uiState.dashboard?.profile?.isPremium != true)
                            },
                            routes = dashboard.routeActivities,
                            onDeleteRoute = mainViewModel::deleteRoute,
                            language = preferences.language,
                            unitSystem = preferences.unitSystem,
                        )
                        UtilityPage.GoalJourney -> GoalJourneyScreen(
                            dashboard,
                            onBack = { utilityPage = UtilityPage.Main },
                            stepGoal = preferences.stepGoal,
                            waterGoalMl = preferences.waterGoalMl,
                            onSetCurrentWeight = { currentWeight ->
                                val latest = dashboard.measurements.lastOrNull()
                                mainViewModel.saveBodyMeasurement(
                                    com.hedefit.app.data.model.BodyMeasurementData(
                                        date = java.time.LocalDate.now().toString(),
                                        weightKg = currentWeight,
                                        waistCm = latest?.waistCm,
                                        hipsCm = latest?.hipsCm,
                                        chestCm = latest?.chestCm,
                                        armCm = latest?.armCm,
                                        thighCm = latest?.thighCm,
                                    ),
                                )
                            },
                            onSetGoalWeight = { targetWeight ->
                                val profile = dashboard.profile
                                mainViewModel.saveProfile(
                                    com.hedefit.app.data.model.ProfileUpdateData(
                                        displayName = profile.displayName,
                                        age = profile.age,
                                        gender = profile.gender,
                                        heightCm = profile.heightCm,
                                        weightKg = profile.weightKg,
                                        goalType = profile.goal.substringBefore(" | "),
                                        targetWeightKg = targetWeight,
                                        targetWeeks = profile.targetWeeks,
                                        environment = profile.environment,
                                        equipment = profile.equipment,
                                        historyAnswers = profile.historyAnswers,
                                    ),
                                )
                            },
                            language = preferences.language,
                            unitSystem = preferences.unitSystem,
                        )
                        UtilityPage.Wearables -> com.hedefit.app.ui.screens.WearablesScreen(
                            snapshot = uiState.wearables,
                            busy = uiState.wearablesBusy,
                            error = uiState.wearablesError,
                            steps = dashboard.steps,
                            sleepMinutes = dashboard.sleepMinutes,
                            language = preferences.language,
                            healthSdkStatus = healthConnectManager.sdkStatus(),
                            onBack = { utilityPage = UtilityPage.Main },
                            onRefresh = mainViewModel::loadWearables,
                            onGrantPermissions = { healthPermissionLauncher.launch(healthConnectManager.allPermissions) },
                        )
                        UtilityPage.ManualActivity -> ManualActivityScreen(
                            language = preferences.language,
                            weightKg = dashboard.profile.weightKg,
                            saving = uiState.workoutSaving,
                            onBack = { utilityPage = UtilityPage.Main },
                            onSave = { input -> mainViewModel.recordManualActivity(input, preferences.language) { utilityPage = UtilityPage.Main } },
                        )
                        UtilityPage.Main -> Unit
                    }
                } else {
                    val coachDisplayName = preferences.coachName.ifBlank { if (preferences.language == "en") "Fit Coach" else "Fit Koç" }
                    BackHandler(enabled = tabHistory.isNotEmpty() || selected != AppDestination.Home) {
                        poppingTab = true
                        selected = tabHistory.removeLastOrNull() ?: AppDestination.Home
                    }
                    HedefitAppFrame(selected = selected, onSelect = { selected = it }, language = preferences.language, coachName = coachDisplayName) { padding, expanded ->
                        com.hedefit.app.ui.components.HedefitTabPager(selected, { selected = it }) { destination -> when (destination) {
                            AppDestination.Home -> HomeScreen(padding, expanded, uiState.dashboard, uiState.avatarPreview, uiState.dataLoading, uiState.dataError, onRetry = mainViewModel::refreshAll, onSignOut = {
                                scope.launch { googleSignIn.clearCredentialState() }
                                mainViewModel.signOut()
                            }, onOpenProfile = { utilityPage = UtilityPage.Profile }, onOpenCalendar = { utilityPage = UtilityPage.Calendar }, onOpenRoute = { utilityPage = UtilityPage.Route },
                                onOpenGoal = { utilityPage = UtilityPage.GoalJourney },
                                onOpenNutrition = { selected = AppDestination.Nutrition },
                                onOpenProgram = { programId ->
                                    programId?.let { id -> uiState.dashboard?.workoutPrograms?.firstOrNull { it.id == id }?.let(mainViewModel::activateProgram) }
                                    selected = AppDestination.Workout
                                },
                                onProgramHomeVisibilityChange = mainViewModel::setProgramHomeVisibility,
                                unitSystem = preferences.unitSystem,
                                stepGoal = preferences.stepGoal,
                                onStepGoalChange = { updatePreferences(preferences.copy(stepGoal = it)) },
                                waterGoalMl = preferences.waterGoalMl,
                                onWaterGoalChange = { updatePreferences(preferences.copy(waterGoalMl = it)) },
                                onAddWater = mainViewModel::addWater,
                                language = preferences.language,
                                stepSource = uiState.stepSource,
                                showAds = adsAllowed && uiState.dashboard?.profile?.isPremium != true,
                                onOpenCoach = { selected = AppDestination.Coach },
                                onOpenLibrary = { mainViewModel.loadExerciseLibrary(locale = preferences.language); utilityPage = UtilityPage.ExerciseLibrary },
                                onSaveSleep = mainViewModel::saveSleep,
                                onOpenGame = { selected = AppDestination.Game },
                                onOpenProgress = { selected = AppDestination.Progress },
                                onOpenActivityLog = { utilityPage = UtilityPage.ManualActivity },
                                onOpenWearables = { utilityPage = UtilityPage.Wearables },
                                quickActions = preferences.homeQuickActions,
                                onQuickActionsChange = { updatePreferences(preferences.copy(homeQuickActions = it)) },
                            )
                            AppDestination.Workout -> WorkoutPlanScreen(padding, expanded, uiState.dashboard?.workouts.orEmpty(), uiState.dashboard?.workoutPrograms.orEmpty(), uiState.exerciseLibrary, uiState.exerciseLibraryBusy, uiState.dataLoading, uiState.planGenerating, mainViewModel::generatePlan, onStartWorkout = {
                                if (!uiState.dashboard?.workouts.isNullOrEmpty()) { activeWorkoutStore.clear(); activeWorkoutExercises = null; mainViewModel.loadPreviousPerformance(); activeWorkout = true }
                            }, hasActiveWorkout = activeWorkoutStore.hasRecoverable(), onResumeWorkout = { activeWorkoutExercises = activeWorkoutStore.read()?.exercises; mainViewModel.loadPreviousPerformance(activeWorkoutExercises); activeWorkout = true }, onOpenScanner = { utilityPage = UtilityPage.EquipmentScanner }, onOpenLibrary = { mainViewModel.loadExerciseLibrary(locale = preferences.language); utilityPage = UtilityPage.ExerciseLibrary },
                                onOpenActivityLog = { utilityPage = UtilityPage.ManualActivity },
                                onOpenRoute = { utilityPage = UtilityPage.Route },
                                onGenerateRegional = { muscle, label, connected -> mainViewModel.generateRegionalPlan(muscle, label, connected, preferences.language) },
                                onLoadRegional = { muscle -> mainViewModel.loadExerciseLibrary(muscle = muscle, muscleRole = "primary", category = "strength", locale = preferences.language) },
                                onGenerateQuickWorkout = { regions, duration, fatigue, environment, owned, level -> mainViewModel.generateQuickWorkout(regions, duration, fatigue, environment, owned, level, preferences.language) },
                                onCreateOwnPlan = { draft -> mainViewModel.createCustomProgram(draft, preferences.language) { mainViewModel.loadExerciseLibrary(locale = preferences.language); utilityPage = UtilityPage.ExerciseLibrary } },
                                onAddPushPullTemplate = { key -> mainViewModel.addPushPullTemplate(key, preferences.language) },
                                onSelectProgram = mainViewModel::activateProgram,
                                onRemoveProgram = mainViewModel::deleteProgram,
                                onCopyProgram = mainViewModel::copyProgram,
                                onUpdateExercise = mainViewModel::updateWorkoutExercise,
                                onReplaceExercise = mainViewModel::replaceWorkoutExercise,
                                onRemoveExercise = mainViewModel::removeWorkoutExercise,
                                onMoveExercise = mainViewModel::moveWorkoutExercise,
                                onLoadReplacementOptions = { exercise -> mainViewModel.loadExerciseLibrary(muscle = replacementMuscle(exercise.area), muscleRole = "primary", category = "strength", locale = preferences.language) },
                                language = preferences.language,
                                onAdaptReadiness = { input, cb -> mainViewModel.submitReadinessCheckin(input, onComplete = cb) },
                                onApplyReadinessAdaptation = mainViewModel::applyReadinessAdaptation,
                                readinessAdaptation = uiState.readinessAdaptation,
                                readinessBusy = uiState.readinessCheckinBusy,
                                onRequestReplacementCandidate = { id, reason, area, cb ->
                                    mainViewModel.requestExerciseReplacement(id, reason, discomfortArea = area, locale = preferences.language, onComplete = cb)
                                },
                                onApplyReplacementCandidate = mainViewModel::applyExerciseReplacement,
                                replacementCandidate = uiState.replacementCandidate,
                                replacementBusy = uiState.replacementBusy,
                                onRequestPlanAdaptation = { trigger, mins, cb ->
                                    mainViewModel.requestPlanAdaptation(trigger, mins, locale = preferences.language, onComplete = cb)
                                },
                                onApplyPlanAdaptation = mainViewModel::applyPlanAdaptation,
                                planAdaptationResult = uiState.planAdaptationResult,
                                planAdaptationBusy = uiState.planAdaptationBusy,
                                chatMessages = uiState.chatMessages,
                                chatBusy = uiState.chatBusy,
                                onSendChatMessage = { msg, ctx -> mainViewModel.sendChat(msg, preferences.language, ctx) },
                                onExecuteCoachAction = mainViewModel::executeCoachAction,
                                onOpenCalendar = { utilityPage = UtilityPage.Calendar },
                                onOpenQuestionnaire = { utilityPage = UtilityPage.Questionnaire },
                                profile = uiState.dashboard?.profile,
                                performances = uiState.dashboard?.exercisePerformance.orEmpty(),
                                catalog = uiState.dashboard?.exerciseCatalog.orEmpty(),
                            )
                            AppDestination.Nutrition -> NutritionScreen(
                                padding, expanded, uiState.dashboard, uiState.nutritionBusy, uiState.foodSearchBusy, uiState.foodSearchResults, uiState.foodSearchQuery,
                                mainViewModel::addNutritionWithAi, { query -> mainViewModel.searchFoods(query, preferences.language) }, mainViewModel::addCatalogFood,
                                mainViewModel::addFavorite, mainViewModel::removeNutritionLog, mainViewModel::updateNutritionLog, mainViewModel::removeFavorite, mainViewModel::repeatFavorite, mainViewModel::addWater,
                                waterGoalMl = preferences.waterGoalMl,
                                onWaterGoalChange = { updatePreferences(preferences.copy(waterGoalMl = it)) },
                                onAskCoach = { prompt -> selected = AppDestination.Coach; mainViewModel.sendChat(prompt, preferences.language) },
                                language = preferences.language,
                                selectedDate = uiState.nutritionViewingDate,
                                selectedLogs = uiState.nutritionViewingLogs,
                                historyLogs = uiState.nutritionHistory,
                                dateLoading = uiState.nutritionDateLoading,
                                onSelectDate = mainViewModel::loadNutritionDate,
                                onLoadMonth = mainViewModel::loadNutritionHistory,
                                onAddMealPlanItem = mainViewModel::addMealPlanItem,
                                onToggleMealPlanItem = mainViewModel::toggleMealPlanItem,
                                onRemoveMealPlanItem = mainViewModel::removeMealPlanItem,
                                photoBusy = uiState.photoNutritionBusy,
                                photoResults = uiState.photoNutritionResults,
                                onAnalyzePhoto = mainViewModel::analyzeNutritionPhoto,
                                onClearPhotoResults = mainViewModel::clearPhotoNutritionResults,
                                onSavePhotoResults = mainViewModel::savePhotoNutrition,
                                reviewFromText = uiState.mealReviewSource == "text",
                                reviewMeal = uiState.mealReviewMeal,
                                openMealComposer = openMealComposer,
                                onMealComposerOpened = { openMealComposer = false },
                            )
                            AppDestination.Game -> GameScreen(
                                padding = padding,
                                data = uiState.dashboard,
                                stepGoal = preferences.stepGoal,
                                waterGoalMl = preferences.waterGoalMl,
                                weeklyActivityGoal = preferences.weeklyWorkoutGoal,
                                language = preferences.language,
                                onBack = { selected = AppDestination.Home },
                            )
                            AppDestination.Progress -> ProgressScreen(
                                padding, expanded, uiState.dashboard, preferences.language,
                                unitSystem = preferences.unitSystem,
                                weeklyWorkoutGoal = preferences.weeklyWorkoutGoal,
                                onWeeklyWorkoutGoalChange = { updatePreferences(preferences.copy(weeklyWorkoutGoal = it)) },
                                measurementSaving = uiState.measurementSaving,
                                onSaveMeasurement = mainViewModel::saveBodyMeasurement,
                                onDeleteRoute = mainViewModel::deleteRoute,
                                nutritionHistory = (uiState.nutritionHistory + uiState.dashboard?.nutritionLogs.orEmpty()).distinctBy { it.id },
                                onLoadNutritionHistory = mainViewModel::loadProgressNutrition,
                            )
                            AppDestination.Coach -> CoachScreen(
                                padding, expanded, uiState.chatMessages, uiState.chatBusy,
                                { message -> mainViewModel.sendChat(message, preferences.language) },
                                uiState.dashboard,
                                onOpenPlan = { selected = AppDestination.Workout },
                                language = preferences.language,
                                coachName = coachDisplayName,
                                onCoachNameChange = { updatePreferences(preferences.copy(coachName = it)) },
                                onClearChat = mainViewModel::clearChat,
                                onExecuteAction = { action ->
                                    // Bu 6 eylem tipi bir EKRAN GEÇİŞİ ister; bu durum (selected/
                                    // utilityPage) yalnız burada, Compose ağacında tutuluyor —
                                    // ViewModel'in erişimi yok. Diğer eylemler (hareket değiştirme,
                                    // set/tekrar/dinlenme güncelleme vb.) veri mutasyonudur ve
                                    // olduğu gibi ViewModel'e devredilir.
                                    when (action.type) {
                                        "openWorkout" -> {
                                            selected = AppDestination.Workout
                                            mainViewModel.showTransientMessage(if (preferences.language == "en") "Today's workout is open." else "Bugünkü antrenman açıldı.")
                                        }
                                        "createWorkout" -> {
                                            selected = AppDestination.Workout
                                            val region = action.region
                                            mainViewModel.showTransientMessage(
                                                if (preferences.language == "en") "Opening the workout screen${if (region != null) " for $region" else ""}."
                                                else "Antrenman ekranı${if (region != null) " ($region)" else ""} açılıyor.",
                                            )
                                        }
                                        "startOutdoor" -> {
                                            utilityPage = UtilityPage.Route
                                        }
                                        "suggestMeal" -> {
                                            selected = AppDestination.Nutrition
                                            openMealComposer = true
                                        }
                                        "remind" -> {
                                            utilityPage = UtilityPage.Notifications
                                        }
                                        "changeGoal" -> {
                                            utilityPage = UtilityPage.GoalJourney
                                        }
                                        else -> mainViewModel.executeCoachAction(action)
                                    }
                                },
                                usageUsed = uiState.chatUsageUsed,
                                usageLimit = uiState.chatUsageLimit,
                            )
                        } }
                    }
                }
                if (showWelcomeGuide && uiState.auth is AuthState.SignedIn && uiState.dashboard != null) WelcomeGuideDialog(
                    language = preferences.language,
                    coachName = preferences.coachName.ifBlank { if (preferences.language == "en") "Fit Coach" else "FitKoç" },
                    onAction = { action ->
                        if (action == com.hedefit.app.ui.screens.GuideAction.HealthConnect) {
                            if (uiState.healthConnected) mainViewModel.syncHealthConnect()
                            else healthPermissionLauncher.launch(healthConnectManager.permissions)
                        } else {
                            showWelcomeGuide = false
                            updatePreferences(preferences.copy(welcomeGuideSeen = true))
                            utilityPage = UtilityPage.Main
                            when (action) {
                                com.hedefit.app.ui.screens.GuideAction.Workout -> selected = AppDestination.Workout
                                com.hedefit.app.ui.screens.GuideAction.Nutrition -> { selected = AppDestination.Nutrition; openMealComposer = true }
                                com.hedefit.app.ui.screens.GuideAction.Reminders -> utilityPage = UtilityPage.Notifications
                                com.hedefit.app.ui.screens.GuideAction.Coach -> selected = AppDestination.Coach
                                com.hedefit.app.ui.screens.GuideAction.HealthConnect -> Unit
                            }
                        }
                    },
                    onDismiss = {
                        showWelcomeGuide = false
                        updatePreferences(preferences.copy(welcomeGuideSeen = true))
                    },
                )
                val userProfile = uiState.dashboard?.profile
                if (uiState.auth is AuthState.SignedIn && userProfile != null && userProfile.username.isNullOrBlank() && userProfile.heightCm != null && userProfile.weightKg != null && userProfile.age != null) {
                    com.hedefit.app.ui.screens.UsernameSetupDialog(onCheck = mainViewModel::checkUsername, onSave = mainViewModel::saveUsername)
                }
                if (uiState.auth is AuthState.SignedIn && userProfile != null && (userProfile.heightCm == null || userProfile.weightKg == null || userProfile.age == null)) {
                    PersonalDetailsOnboardingDialog(
                        initialAge = userProfile.age,
                        initialGender = userProfile.gender,
                        initialHeightCm = userProfile.heightCm,
                        initialWeightKg = userProfile.weightKg,
                        isSaving = uiState.profileSaving,
                        onSave = { age, gender, heightCm, weightKg ->
                            mainViewModel.saveProfile(
                                com.hedefit.app.data.model.ProfileUpdateData(
                                    displayName = userProfile.displayName,
                                    age = age,
                                    gender = gender,
                                    heightCm = heightCm,
                                    weightKg = weightKg,
                                    goalType = userProfile.goal.substringBefore(" | "),
                                    targetWeightKg = userProfile.targetWeightKg,
                                    targetWeeks = userProfile.targetWeeks,
                                    environment = userProfile.environment,
                                    equipment = userProfile.equipment,
                                    historyAnswers = userProfile.historyAnswers,
                                )
                            )
                        },
                        language = preferences.language,
                    )
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    com.hedefit.app.ui.components.TopNotificationBanner(bannerMessage) { bannerMessage = null }
                }
            }
        }
    }

}
