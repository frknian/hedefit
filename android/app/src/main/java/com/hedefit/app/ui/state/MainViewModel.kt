package com.hedefit.app.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hedefit.app.data.auth.AuthRepository
import com.hedefit.app.data.auth.AuthSession
import com.hedefit.app.data.auth.AuthState
import com.hedefit.app.data.auth.RegistrationLegalAcceptance
import com.hedefit.app.data.auth.SecureSessionStore
import com.hedefit.app.data.auth.SignUpResult
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.data.model.BodyMeasurementData
import com.hedefit.app.data.model.ProfileUpdateData
import com.hedefit.app.data.model.WorkoutSetInput
import com.hedefit.app.data.model.WorkoutFeedbackData
import com.hedefit.app.data.model.FoodSearchData
import com.hedefit.app.data.model.FavoriteMealData
import com.hedefit.app.data.model.ExerciseCatalogData
import com.hedefit.app.data.model.PreviousSetData
import com.hedefit.app.data.model.WorkoutProgramData
import com.hedefit.app.data.model.CustomProgramDraft
import com.hedefit.app.data.model.RouteActivityData
import com.hedefit.app.data.model.WorkoutExercisePerformanceData
import com.hedefit.app.data.model.WorkoutSetPerformanceData
import com.hedefit.app.data.model.ManualActivityInput
import com.hedefit.app.data.model.estimateManualActivityEnergy
import com.hedefit.app.data.model.manualActivityTypes
import com.hedefit.app.data.offline.OfflineQueueStore
import com.hedefit.app.data.offline.OfflineSyncScheduler
import com.hedefit.app.data.offline.workoutOfflinePayload
import com.hedefit.app.health.HealthConnectManager
import com.hedefit.app.steps.StepRepository
import com.hedefit.app.steps.StepSource
import com.hedefit.app.route.RouteSnapshot
import com.hedefit.app.data.network.HedefitApiClient
import com.hedefit.app.data.network.JsonHttpClient
import com.hedefit.app.data.network.SupabaseRestClient
import com.hedefit.app.data.repository.HedefitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Job
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.HttpURLConnection
import com.hedefit.app.data.model.CoachActionData
import com.hedefit.app.data.model.DailyReadinessInput
import com.hedefit.app.data.model.ReadinessAdaptationData
import com.hedefit.app.data.model.ExerciseReplacementCandidate
import com.hedefit.app.data.model.WorkoutAdaptationResultData
import com.hedefit.app.data.model.WorkoutCoachContext
import com.hedefit.app.data.model.WorkoutExerciseData

data class ChatMessageState(
    val text: String,
    val user: Boolean,
    val pending: Boolean = false,
    val actions: List<CoachActionData> = emptyList(),
)

data class MainUiState(
    val auth: AuthState = AuthState.Loading,
    val dashboard: DashboardData? = null,
    val dataLoading: Boolean = false,
    val dataError: String? = null,
    val authBusy: Boolean = false,
    val authMessage: String? = null,
    val workoutSaving: Boolean = false,
    val planGenerating: Boolean = false,
    val nutritionBusy: Boolean = false,
    val nutritionDateLoading: Boolean = false,
    val nutritionViewingDate: LocalDate = LocalDate.now(),
    val nutritionViewingLogs: List<com.hedefit.app.data.model.NutritionLogData> = emptyList(),
    val nutritionHistory: List<com.hedefit.app.data.model.NutritionLogData> = emptyList(),
    val nutritionLoadedMonths: Set<YearMonth> = emptySet(),
    val chatBusy: Boolean = false,
    val chatMessages: List<ChatMessageState> = emptyList(),
    val chatUsageUsed: Int? = null,
    val chatUsageLimit: Int? = null,
    val transientMessage: String? = null,
    val profileSaving: Boolean = false,
    val avatarUploading: Boolean = false,
    val avatarPreview: ByteArray? = null,
    val measurementSaving: Boolean = false,
    val accountBusy: Boolean = false,
    val accountFrozen: Boolean = false,
    val healthConnected: Boolean = false,
    val healthBusy: Boolean = false,
    val wearables: com.hedefit.app.health.WearableSnapshot? = null,
    val wearablesBusy: Boolean = false,
    val wearablesError: String? = null,
    val stepSource: StepSource = StepSource.UNAVAILABLE,
    val foodSearchBusy: Boolean = false,
    val foodSearchResults: List<FoodSearchData> = emptyList(),
    val foodSearchQuery: String? = null,
    val photoNutritionBusy: Boolean = false,
    val photoNutritionResults: List<com.hedefit.app.data.model.NutritionEstimateData> = emptyList(),
    /** "photo" or "text": where the items in [photoNutritionResults] came from. */
    val mealReviewSource: String = "photo",
    val mealReviewMeal: String? = null,
    val exerciseLibraryBusy: Boolean = false,
    val exerciseLibrary: List<ExerciseCatalogData> = emptyList(),
    val offlinePendingCount: Int = 0,
    val previousPerformance: Map<String, List<PreviousSetData>> = emptyMap(),
    val readinessCheckinBusy: Boolean = false,
    val readinessAdaptation: ReadinessAdaptationData? = null,
    val replacementBusy: Boolean = false,
    val replacementCandidate: ExerciseReplacementCandidate? = null,
    val planAdaptationBusy: Boolean = false,
    val planAdaptationResult: WorkoutAdaptationResultData? = null,
    val activeCoachContext: WorkoutCoachContext? = null,
    /** Misafir hesabı kalıcı hale getirme teklifinin gösterileceği bağlam (null = gizli). */
    val saveAccountPrompt: SaveAccountTrigger? = null,
    val saveAccountBusy: Boolean = false,
    val saveAccountMessage: String? = null,
    /** Kilide takılan özellik: misafirde kayıt teklifi, ücretside premium teklifi gösterilir. */
    val lockedFeature: LockedFeature? = null,
    /** Hareket adlarının TR/EN karşılıkları (katalogdan, bir kez yüklenir). */
    val exerciseNames: com.hedefit.app.ui.i18n.ExerciseNameIndex = com.hedefit.app.ui.i18n.ExerciseNameIndex.Empty,
) {
    val isGuest: Boolean get() = (auth as? AuthState.SignedIn)?.session?.user?.isAnonymous == true
}

/** Misafire kayıt teklifinin çıktığı an; metin buna göre kişiselleşir. */
enum class SaveAccountTrigger { WorkoutCompleted, CoachLimit, Sync, Manual, Limit }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val http = JsonHttpClient()
    private val authRepository = AuthRepository(SecureSessionStore(application), http)
    private val repository = HedefitRepository(
        authRepository,
        SupabaseRestClient(authRepository, http),
        HedefitApiClient(authRepository, http),
    )
    private val healthConnect = HealthConnectManager(application)
    private val stepRepository = StepRepository(application, healthConnect)
    private val offlineQueue = OfflineQueueStore(application)
    private val waterUpdateMutex = Mutex()

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    override fun onCleared() {
        stepRepository.stop()
        super.onCleared()
    }

    init {
        viewModelScope.launch {
            stepRepository.todaySteps.collect { reading ->
                _state.update { current -> current.copy(
                    stepSource = reading.source,
                    dashboard = current.dashboard?.copy(
                        steps = reading.count,
                        activeCalories = if (reading.source == StepSource.HEALTH_CONNECT) current.dashboard.activeCalories else reading.count / 25,
                    ),
                ) }
            }
        }
        viewModelScope.launch {
            val auth = authRepository.bootstrap()
            val cachedAvatar = if (auth is AuthState.SignedIn) loadCachedAvatar(auth.session.user.id) else null
            _state.update { it.copy(auth = auth, avatarPreview = cachedAvatar) }
            if (auth is AuthState.SignedIn) checkAccountThenLoad()
        }
    }

    fun signIn(email: String, password: String) = authAction {
        val session = authRepository.signIn(email, password)
        onSignedIn(session)
    }

    fun signUp(email: String, password: String, username: String, legalAcceptance: RegistrationLegalAcceptance) = authAction {
        when (val result = authRepository.signUp(email, password, username, legalAcceptance)) {
            is SignUpResult.SignedIn -> onSignedIn(result.session)
            SignUpResult.VerificationRequired -> _state.update {
                it.copy(authBusy = false, authMessage = "Doğrulama bağlantısı e-posta adresine gönderildi. Doğruladıktan sonra giriş yapabilirsin.")
            }
        }
    }

    fun checkUsername(username: String, onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(runCatching { authRepository.checkUsername(username) }.getOrDefault("error")) }
    }

    fun saveUsername(username: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.saveUsername(username) }
                .onSuccess { saved ->
                    _state.update { current -> current.copy(dashboard = current.dashboard?.let { it.copy(profile = it.profile.copy(username = saved)) }) }
                    onDone(null)
                }
                .onFailure { error -> onDone(friendlyError(error)) }
        }
    }

    fun signInWithGoogle(idToken: String, nonce: String, legalAcceptance: RegistrationLegalAcceptance? = null) = authAction {
        onSignedIn(authRepository.signInWithGoogle(idToken, nonce, legalAcceptance))
    }

    /** Üye olmadan dene: anonim oturum açar ve normal akışa geçer. */
    fun startAsGuest(legalAcceptance: RegistrationLegalAcceptance) = authAction {
        onSignedIn(authRepository.signInAsGuest(legalAcceptance))
    }

    fun showSaveAccountPrompt(trigger: SaveAccountTrigger) {
        if (!_state.value.isGuest) return
        _state.update { it.copy(saveAccountPrompt = trigger, saveAccountMessage = null) }
    }

    fun dismissSaveAccountPrompt() {
        _state.update { it.copy(saveAccountPrompt = null, saveAccountMessage = null, saveAccountBusy = false, lockedFeature = null) }
    }

    fun dismissLockedFeature() {
        _state.update { it.copy(lockedFeature = null) }
    }

    /**
     * Katman kontrolü. İzin yoksa uygun teklifi açar ve false döner:
     * misafire hesap kaydetme, ücretsiz kullanıcıya premium.
     */
    fun requireEntitlement(feature: LockedFeature, allowed: (TierLimits) -> Boolean): Boolean {
        val current = _state.value
        if (allowed(current.limits())) return true
        _state.update {
            if (it.isGuest) it.copy(saveAccountPrompt = SaveAccountTrigger.Limit, lockedFeature = feature, saveAccountMessage = null)
            else it.copy(lockedFeature = feature)
        }
        return false
    }

    private fun canAddMeals(count: Int = 1): Boolean = requireEntitlement(LockedFeature.MealLogs) { limits ->
        (_state.value.dashboard?.todayMealLogCount() ?: 0) + count <= limits.dailyMealLogs
    }

    private fun canCreateCustomProgram(): Boolean = requireEntitlement(LockedFeature.CustomProgram) { limits ->
        (_state.value.dashboard?.workoutPrograms?.count { it.source == "custom" } ?: 0) < limits.customPrograms
    }

    fun linkGuestEmail(email: String, password: String, username: String) {
        if (_state.value.saveAccountBusy) return
        viewModelScope.launch {
            _state.update { it.copy(saveAccountBusy = true, saveAccountMessage = null) }
            runCatching { authRepository.linkEmail(email, password, username) }
                .onSuccess { _state.update { it.copy(saveAccountBusy = false, saveAccountMessage = com.hedefit.app.ui.i18n.tr("Doğrulama bağlantısını $email adresine gönderdik. Onayladığında hesabın kalıcı olur; verilerin korunur.", "We sent a verification link to $email. Once you confirm, your account is permanent and your data is kept.")) } }
                .onFailure { error -> _state.update { it.copy(saveAccountBusy = false, saveAccountMessage = friendlyError(error)) } }
        }
    }

    fun linkGuestGoogle(idToken: String, nonce: String) {
        if (_state.value.saveAccountBusy) return
        viewModelScope.launch {
            _state.update { it.copy(saveAccountBusy = true, saveAccountMessage = null) }
            runCatching { authRepository.linkGoogle(idToken, nonce) }
                .onSuccess { session -> _state.update { it.copy(auth = AuthState.SignedIn(session), saveAccountBusy = false, saveAccountPrompt = null, transientMessage = com.hedefit.app.ui.i18n.tr("Hesabın kaydedildi. Tüm ilerlemen güvende.", "Account saved. All your progress is safe.")) } }
                .onFailure { error -> _state.update { it.copy(saveAccountBusy = false, saveAccountMessage = friendlyError(error)) } }
        }
    }

    /** E-posta doğrulamasından sonra uygulamaya dönüldüğünde misafir bayrağını tazeler. */
    fun refreshGuestStatus() {
        if (!_state.value.isGuest) return
        viewModelScope.launch {
            runCatching { authRepository.refreshSession() }.onSuccess { session ->
                if (!session.user.isAnonymous) _state.update { it.copy(auth = AuthState.SignedIn(session), saveAccountPrompt = null, transientMessage = com.hedefit.app.ui.i18n.tr("Hesabın doğrulandı ve kaydedildi.", "Your account is verified and saved.")) }
                else _state.update { it.copy(auth = AuthState.SignedIn(session)) }
            }
        }
    }

    fun reportAuthError(message: String) {
        _state.update { it.copy(authBusy = false, authMessage = message) }
    }

    fun clearAuthMessage() {
        _state.update { it.copy(authMessage = null) }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true) }
            authRepository.signOut()
            _state.value = MainUiState(auth = AuthState.SignedOut)
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.update { it.copy(dataLoading = true, dataError = null) }
            runCatching { repository.loadDashboard() }
                .onSuccess { dashboard ->
                    _state.update { current ->
                        current.copy(
                            dashboard = dashboard,
                            nutritionViewingDate = LocalDate.now(),
                            nutritionViewingLogs = dashboard.nutritionLogs,
                            dataLoading = false,
                            dataError = null,
                            chatMessages = current.chatMessages.ifEmpty {
                                listOf(ChatMessageState(com.hedefit.app.ui.i18n.tr("Merhaba ${com.hedefit.app.ui.i18n.localizedDisplayName(dashboard.profile.displayName)}! Antrenman, beslenme veya ilerlemen hakkında bana bir şey sorabilirsin.", "Hi ${com.hedefit.app.ui.i18n.localizedDisplayName(dashboard.profile.displayName)}! Ask me anything about training, nutrition or your progress."), false))
                            },
                            chatUsageLimit = current.chatUsageLimit ?: current.copy(dashboard = dashboard).limits().dailyCoachQuestions,
                        )
                    }
                    // The server snapshot is historical data only. Immediately
                    // replace today's visible total with the central live source.
                    val reading = stepRepository.refresh()
                    _state.update { current -> current.copy(
                        stepSource = reading.source,
                        dashboard = current.dashboard?.copy(
                            steps = reading.count,
                            activeCalories = if (reading.source == StepSource.HEALTH_CONNECT) current.dashboard.activeCalories else reading.count / 25,
                        ),
                    ) }
                    if (_state.value.avatarPreview == null) {
                        dashboard.profile.avatarUrl?.let { url ->
                            viewModelScope.launch {
                                val restored = downloadAvatar(url) ?: return@launch
                                cacheAvatar(dashboard.profile.id, restored)
                                _state.update { current ->
                                    if (current.avatarPreview == null && (current.auth as? AuthState.SignedIn)?.session?.user?.id == dashboard.profile.id) current.copy(avatarPreview = restored)
                                    else current
                                }
                            }
                        }
                    }
                }
                .onFailure { error -> _state.update { it.copy(dataLoading = false, dataError = friendlyError(error)) } }
        }
    }

    fun refreshSteps() {
        if (_state.value.dashboard == null) return
        viewModelScope.launch { stepRepository.refresh() }
    }

    fun syncGamificationPreferences(stepGoal: Int, waterGoalMl: Int, weeklyActivityGoal: Int, timezone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.syncGamificationPreferences(stepGoal, waterGoalMl, weeklyActivityGoal, timezone)
        }
    }

    private var nutritionDateJob: Job? = null
    private val nutritionMonthsInFlight = mutableSetOf<YearMonth>()

    fun loadNutritionDate(date: LocalDate) {
        if (!requireEntitlement(LockedFeature.History) { java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now()) < it.historyDays }) return
        val today = LocalDate.now()
        if (date > today) return
        nutritionDateJob?.cancel()
        val current = _state.value
        // Days of already-loaded months are served from memory, so switching back and forth is instant.
        val known = when {
            date == today -> current.dashboard?.nutritionLogs
            YearMonth.from(date) in current.nutritionLoadedMonths -> current.nutritionHistory.filter { it.date.take(10) == date.toString() }
            else -> null
        }
        _state.update { it.copy(nutritionViewingDate = date, nutritionViewingLogs = known.orEmpty(), nutritionDateLoading = known == null) }
        loadNutritionHistory(YearMonth.from(date))
        if (known != null) return
        nutritionDateJob = viewModelScope.launch {
            val result = runCatching { repository.loadNutritionLogs(date) }
            _state.update { state ->
                if (state.nutritionViewingDate != date) return@update state
                result.fold(
                    onSuccess = { logs -> state.copy(nutritionDateLoading = false, nutritionViewingLogs = logs, nutritionHistory = (logs + state.nutritionHistory).distinctBy { it.id }) },
                    onFailure = { error -> state.copy(nutritionDateLoading = false, transientMessage = friendlyError(error)) },
                )
            }
        }
    }

    fun loadNutritionHistory(month: YearMonth = YearMonth.now()) {
        val today = LocalDate.now()
        if (month > YearMonth.from(today) || month in _state.value.nutritionLoadedMonths || !nutritionMonthsInFlight.add(month)) return
        viewModelScope.launch {
            runCatching { repository.loadNutritionHistory(month.atDay(1), minOf(month.atEndOfMonth(), today)) }
                .onSuccess { logs ->
                    _state.update { state ->
                        val viewing = state.nutritionViewingDate
                        val refreshViewing = viewing != today && YearMonth.from(viewing) == month && !state.nutritionDateLoading
                        state.copy(
                            nutritionHistory = (logs + state.nutritionHistory).distinctBy { it.id },
                            nutritionLoadedMonths = state.nutritionLoadedMonths + month,
                            nutritionViewingLogs = if (refreshViewing) logs.filter { it.date.take(10) == viewing.toString() } else state.nutritionViewingLogs,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
            nutritionMonthsInFlight.remove(month)
        }
    }

    fun completeDetailedWorkout(durationSeconds: Int, calories: Int, sets: List<WorkoutSetInput>, feedback: WorkoutFeedbackData, workoutExercises: List<com.hedefit.app.data.model.WorkoutExerciseData>? = null) {
        val exercises = workoutExercises ?: _state.value.dashboard?.workouts.orEmpty()
        if (exercises.isEmpty() || sets.isEmpty() || _state.value.workoutSaving) return
        viewModelScope.launch {
            _state.update { it.copy(workoutSaving = true) }
            runCatching { repository.recordWorkout(exercises, sets, durationSeconds, calories, feedback) }
            .onSuccess { session ->
                    val performances = sets.groupBy { it.exerciseId }.map { (exerciseId, exerciseSets) ->
                        WorkoutExercisePerformanceData(
                            sessionId = session.id,
                            exerciseId = exerciseId,
                            exerciseName = exerciseSets.first().exerciseName,
                            completedAt = session.completedAt,
                            sets = exerciseSets.sortedBy { it.setNumber }.map { set -> WorkoutSetPerformanceData(set.setNumber, set.weightKg, set.reps, set.durationSeconds, set.rpe) },
                        )
                    }
                    _state.update { current -> current.copy(
                        workoutSaving = false,
                        dashboard = current.dashboard?.copy(
                            sessions = listOf(session) + current.dashboard.sessions,
                            exercisePerformance = performances + current.dashboard.exercisePerformance,
                        ),
                        transientMessage = "Antrenman ve tüm setlerin ilerlemene kaydedildi.",
                    ) }
                    if (feedback.difficulty == "Zor" || feedback.painAreas.any { it != "Yok" } || feedback.fatigue >= 4) adaptPlan(feedback)
                }
                .onFailure { error ->
                    val networkLike = error.message.orEmpty().contains("network", true) || error.message.orEmpty().contains("host", true) || error is java.io.IOException
                    if (networkLike) {
                        offlineQueue.enqueue("workout", workoutOfflinePayload(exercises, sets, durationSeconds, calories, feedback))
                        OfflineSyncScheduler.enqueue(getApplication())
                        _state.update { it.copy(workoutSaving = false, offlinePendingCount = offlineQueue.count(), transientMessage = "Antrenman cihazda saklandı; bağlantı gelince otomatik eşitlenecek.") }
                    } else _state.update { it.copy(workoutSaving = false, transientMessage = friendlyError(error)) }
                }
        }
    }

    fun recordManualActivity(input: ManualActivityInput, language: String, onSaved: () -> Unit) {
        if (_state.value.workoutSaving) return
        val dashboard = _state.value.dashboard ?: return
        val activity = manualActivityTypes.firstOrNull { it.key == input.activityKey } ?: return
        val estimate = estimateManualActivityEnergy(activity, input, dashboard.profile.weightKg)
        val calories = estimate.activeCalories
        viewModelScope.launch {
            _state.update { it.copy(workoutSaving = true) }
            runCatching { repository.recordManualActivity(input, calories) }
                .onSuccess { session ->
                    _state.update { current -> current.copy(
                        workoutSaving = false,
                        dashboard = current.dashboard?.copy(sessions = listOf(session) + current.dashboard.sessions),
                        transientMessage = if (language == "en") "${activity.titleEn} saved: $calories kcal burned." else "${activity.titleTr} kaydedildi: $calories kcal yakıldı.",
                    ) }
                    onSaved()
                }
                .onFailure { error -> _state.update { it.copy(workoutSaving = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun recordCardioSession(machineKey: String, durationSeconds: Int, calories: Int, summary: String, onSaved: () -> Unit) {
        if (_state.value.workoutSaving) return
        if (durationSeconds < 60) { _state.update { it.copy(transientMessage = com.hedefit.app.ui.i18n.tr("Kaydetmek için en az 1 dakika kardiyo yapmalısın.", "Do at least 1 minute of cardio to save.")) }; return }
        viewModelScope.launch {
            _state.update { it.copy(workoutSaving = true) }
            runCatching { repository.recordCardioSession(machineKey, durationSeconds, calories, summary) }
                .onSuccess { session ->
                    _state.update { current -> current.copy(
                        workoutSaving = false,
                        dashboard = current.dashboard?.copy(sessions = listOf(session) + current.dashboard.sessions),
                        transientMessage = com.hedefit.app.ui.i18n.tr("Kardiyo kaydedildi: ${session.calories} kcal günlük hesabına eklendi.", "Cardio saved: ${session.calories} kcal added to your day."),
                    ) }
                    onSaved()
                }
                .onFailure { error -> _state.update { it.copy(workoutSaving = false, transientMessage = friendlyError(error)) } }
        }
    }

    private fun adaptPlan(feedback: WorkoutFeedbackData) {
        val profile = _state.value.dashboard?.profile ?: return
        viewModelScope.launch {
            runCatching { repository.generatePlan(profile, feedback) }.onSuccess { workouts ->
                _state.update { current -> current.copy(dashboard = current.dashboard?.copy(workouts = workouts), transientMessage = "Fit Koç geri bildirimine göre sonraki planı uyarladı.") }
            }
        }
    }

    fun loadProgressNutrition() {
        viewModelScope.launch {
            val to = LocalDate.now()
            val from = to.with(java.time.DayOfWeek.MONDAY).minusWeeks(7)
            runCatching { repository.loadNutritionHistory(from, to) }
                .onSuccess { logs -> _state.update { it.copy(nutritionHistory = (logs + it.nutritionHistory).distinctBy { log -> log.id }) } }
        }
    }

    fun loadWearables() {
        if (_state.value.wearablesBusy) return
        viewModelScope.launch {
            _state.update { it.copy(wearablesBusy = true, wearablesError = null) }
            runCatching { healthConnect.readWearableSources() }
                .onSuccess { snapshot -> _state.update { it.copy(wearablesBusy = false, wearables = snapshot) } }
                .onFailure { error -> _state.update { it.copy(wearablesBusy = false, wearablesError = friendlyError(error)) } }
        }
    }

    fun syncHealthConnect() = syncHealth(showMessage = true, checkPermissionFirst = false)

    fun syncHealthIfConnected() = syncHealth(showMessage = false, checkPermissionFirst = true)

    private fun syncHealth(showMessage: Boolean, checkPermissionFirst: Boolean) {
        if (_state.value.healthBusy) return
        viewModelScope.launch {
            if (checkPermissionFirst) {
                val connected = runCatching { healthConnect.hasStepPermission() }.getOrDefault(false)
                _state.update { it.copy(healthConnected = connected) }
            }
            val stepReading = stepRepository.refresh()
            if (stepReading.source != StepSource.HEALTH_CONNECT) {
                _state.update { current -> current.copy(
                    healthConnected = false,
                    healthBusy = false,
                    stepSource = stepReading.source,
                    dashboard = current.dashboard?.copy(steps = stepReading.count, activeCalories = stepReading.count / 25),
                    transientMessage = if (showMessage && stepReading.source == StepSource.UNAVAILABLE) "Bu cihaz otomatik adım takibini desteklemiyor. Health Connect bağlayarak adımlarını takip edebilirsin." else current.transientMessage,
                ) }
                return@launch
            }
            _state.update { current -> current.copy(
                healthConnected = true,
                healthBusy = false,
                stepSource = stepReading.source,
                dashboard = current.dashboard?.copy(steps = stepReading.count),
            ) }
            if (!runCatching { healthConnect.hasPermissions() }.getOrDefault(false)) {
                if (showMessage) _state.update { it.copy(transientMessage = "Samsung Health adımları güncellendi. Uyku, kalori ve kilo için ek Health Connect izinleri gerekir.") }
                return@launch
            }
            _state.update { it.copy(healthBusy = true) }
            val snapshot = runCatching { healthConnect.readToday() }.getOrElse { error ->
                _state.update { it.copy(healthBusy = false, transientMessage = if (showMessage) friendlyError(error) else it.transientMessage) }
                return@launch
            }
            // Health Connect is the source of truth on this device. Show its current,
            // deduplicated daily total immediately; a slow or unavailable server must
            // not leave the dashboard displaying yesterday's cached database value.
            _state.update { current -> current.copy(
                healthConnected = true,
                dashboard = current.dashboard?.copy(steps = snapshot.steps, sleepMinutes = snapshot.sleepMinutes.takeIf { it > 0 } ?: current.dashboard.sleepMinutes, activeCalories = snapshot.activeCalories),
            ) }
            runCatching { repository.syncHealth(snapshot) }.onSuccess {
                _state.update { current -> current.copy(
                    healthBusy = false,
                    transientMessage = if (showMessage) "Health Connect verileri güncellendi." else current.transientMessage,
                ) }
            }.onFailure {
                _state.update { current -> current.copy(
                    healthBusy = false,
                    transientMessage = if (showMessage) "Health Connect verileri cihazdan güncellendi; bulut eşitlemesi daha sonra yeniden denenecek." else current.transientMessage,
                ) }
            }
        }
    }

    fun checkHealthConnect() {
        viewModelScope.launch {
            val connected = runCatching { healthConnect.hasStepPermission() }.getOrDefault(false)
            _state.update { it.copy(healthConnected = connected) }
            stepRepository.refresh()
        }
    }

    fun onActivityRecognitionPermissionChanged() {
        viewModelScope.launch { stepRepository.refresh() }
    }

    fun searchFoods(query: String, locale: String = "tr") {
        if (query.trim().length < 2 || _state.value.foodSearchBusy) return
        viewModelScope.launch {
            _state.update { it.copy(foodSearchBusy = true, foodSearchQuery = null, foodSearchResults = emptyList()) }
            runCatching { repository.searchFoods(query, locale) }
                .onSuccess { results -> _state.update { it.copy(foodSearchBusy = false, foodSearchResults = results, foodSearchQuery = query.trim()) } }
                .onFailure { error -> _state.update { it.copy(foodSearchBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun analyzeNutritionPhoto(jpegBytes: ByteArray) {
        if (!canAddMeals()) return
        if (_state.value.photoNutritionBusy) return
        viewModelScope.launch {
            _state.update { it.copy(photoNutritionBusy = true, photoNutritionResults = emptyList()) }
            runCatching { repository.analyzeNutritionPhoto(jpegBytes) }
                .onSuccess { results -> _state.update { it.copy(photoNutritionBusy = false, photoNutritionResults = results) } }
                .onFailure { error ->
                    val message = friendlyError(error)
                    _state.update { it.copy(photoNutritionBusy = false, transientMessage = message) }
                    // Sunucu günlük fotoğraf kotasını aştıysa uygun teklifi göster.
                    if (message.contains("limit", true) || message.contains("hak", true)) requireEntitlement(LockedFeature.PhotoMeal) { false }
                }
        }
    }

    fun clearPhotoNutritionResults() = _state.update { it.copy(photoNutritionResults = emptyList(), mealReviewSource = "photo", mealReviewMeal = null) }

    suspend fun recognizeEquipment(jpegBytes: ByteArray) = repository.recognizeEquipment(jpegBytes)

    fun savePhotoNutrition(items: List<com.hedefit.app.data.model.NutritionEstimateData>, meal: String) {
        if (!canAddMeals(items.size.coerceAtLeast(1))) return
        if (_state.value.nutritionBusy || items.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(nutritionBusy = true) }
            val source = _state.value.mealReviewSource
            runCatching { repository.savePhotoNutrition(items, meal, if (source == "text") "natural_language" else "photo") }
                .onSuccess { logs -> _state.update { current -> current.copy(
                    nutritionBusy = false, photoNutritionResults = emptyList(), mealReviewSource = "photo", mealReviewMeal = null,
                    dashboard = current.dashboard?.copy(nutritionLogs = logs + current.dashboard.nutritionLogs),
                    nutritionViewingLogs = if (current.nutritionViewingDate == LocalDate.now()) logs + current.nutritionViewingLogs else current.nutritionViewingLogs,
                    nutritionHistory = logs + current.nutritionHistory,
                    transientMessage = if (source == "text") "${logs.size} besin öğüne eklendi." else "Fotoğraftaki ${logs.size} besin öğüne eklendi.",
                ) } }
                .onFailure { error -> _state.update { it.copy(nutritionBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun addCatalogFood(food: FoodSearchData, grams: Double, meal: String) {
        if (!canAddMeals()) return
        if (_state.value.nutritionBusy) return
        viewModelScope.launch {
            _state.update { it.copy(nutritionBusy = true) }
            runCatching { repository.addCatalogFood(food, grams, meal) }
                .onSuccess { log -> _state.update { current -> current.copy(nutritionBusy = false, dashboard = current.dashboard?.copy(nutritionLogs = listOf(log) + current.dashboard.nutritionLogs), nutritionViewingLogs = if (current.nutritionViewingDate == LocalDate.now()) listOf(log) + current.nutritionViewingLogs else current.nutritionViewingLogs, nutritionHistory = listOf(log) + current.nutritionHistory, transientMessage = "${log.name} eklendi.") } }
                .onFailure { error ->
                    val payload = repository.catalogFoodPayload(food, grams, meal)
                    val networkLike = error.message.orEmpty().contains("network", true) || error.message.orEmpty().contains("host", true) || error is java.io.IOException
                    if (networkLike) {
                        val pendingId = offlineQueue.enqueue("nutrition", payload); OfflineSyncScheduler.enqueue(getApplication())
                        val ratio = grams / 100.0
                        val local = com.hedefit.app.data.model.NutritionLogData("offline-$pendingId", java.time.LocalDate.now().toString(), meal, food.name, (food.calories * ratio).toInt(), food.protein * ratio, food.carbs * ratio, food.fat * ratio, grams, food.fiber * ratio, food.sugar * ratio, food.sodiumMg * ratio, food.potassiumMg * ratio, food.calciumMg * ratio, food.ironMg * ratio, food.vitaminCMg * ratio)
                        _state.update { current -> current.copy(nutritionBusy = false, offlinePendingCount = offlineQueue.count(), dashboard = current.dashboard?.copy(nutritionLogs = listOf(local) + current.dashboard.nutritionLogs), nutritionViewingLogs = if (current.nutritionViewingDate == LocalDate.now()) listOf(local) + current.nutritionViewingLogs else current.nutritionViewingLogs, nutritionHistory = listOf(local) + current.nutritionHistory, transientMessage = "Öğün çevrimdışı kaydedildi; bağlantı gelince eşitlenecek.") }
                    } else _state.update { it.copy(nutritionBusy = false, transientMessage = friendlyError(error)) }
                }
        }
    }

    fun addFavorite(log: com.hedefit.app.data.model.NutritionLogData) = viewModelScope.launch {
        runCatching { repository.addFavorite(log) }.onSuccess { favorite -> _state.update { current -> current.copy(dashboard = current.dashboard?.copy(favoriteMeals = listOf(favorite) + current.dashboard.favoriteMeals.filterNot { it.id == favorite.id }), transientMessage = "Öğün favorilere eklendi.") } }
            .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
    }

    fun removeNutritionLog(log: com.hedefit.app.data.model.NutritionLogData) {
        if (_state.value.nutritionBusy) return
        viewModelScope.launch {
            _state.update { it.copy(nutritionBusy = true) }
            runCatching {
                if (log.id.startsWith("offline-")) offlineQueue.remove(log.id.removePrefix("offline-"))
                else repository.removeNutritionLog(log.id)
            }.onSuccess {
                _state.update { current ->
                    current.copy(
                        nutritionBusy = false,
                        offlinePendingCount = offlineQueue.count(),
                        dashboard = current.dashboard?.copy(nutritionLogs = current.dashboard.nutritionLogs.filterNot { it.id == log.id }),
                        nutritionViewingLogs = current.nutritionViewingLogs.filterNot { it.id == log.id },
                        nutritionHistory = current.nutritionHistory.filterNot { it.id == log.id },
                        transientMessage = "${log.name} kaldırıldı.",
                    )
                }
            }.onFailure { error -> _state.update { it.copy(nutritionBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun updateNutritionLog(log: com.hedefit.app.data.model.NutritionLogData, grams: Double, meal: String) {
        if (_state.value.nutritionBusy || log.id.startsWith("offline-")) return
        viewModelScope.launch {
            _state.update { it.copy(nutritionBusy = true) }
            runCatching { repository.updateNutritionLog(log, grams, meal) }.onSuccess { updated ->
                fun replace(items: List<com.hedefit.app.data.model.NutritionLogData>) = items.map { if (it.id == updated.id) updated else it }
                _state.update { current -> current.copy(
                    nutritionBusy = false,
                    dashboard = current.dashboard?.copy(nutritionLogs = replace(current.dashboard.nutritionLogs)),
                    nutritionViewingLogs = replace(current.nutritionViewingLogs),
                    nutritionHistory = replace(current.nutritionHistory),
                    transientMessage = if (updated.meal == log.meal) "${log.name} güncellendi." else "${log.name}, ${updated.meal} öğününe taşındı.",
                ) }
            }.onFailure { error -> _state.update { it.copy(nutritionBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun removeFavorite(id: String) = viewModelScope.launch {
        runCatching { repository.removeFavorite(id) }.onSuccess { _state.update { current -> current.copy(dashboard = current.dashboard?.copy(favoriteMeals = current.dashboard.favoriteMeals.filterNot { it.id == id })) } }
    }

    fun repeatFavorite(favorite: FavoriteMealData) = viewModelScope.launch {
        if (!canAddMeals()) return@launch
        _state.update { it.copy(nutritionBusy = true) }
        runCatching { repository.repeatFavorite(favorite) }.onSuccess { log -> _state.update { current -> current.copy(nutritionBusy = false, dashboard = current.dashboard?.copy(nutritionLogs = listOf(log) + current.dashboard.nutritionLogs), nutritionViewingLogs = if (current.nutritionViewingDate == LocalDate.now()) listOf(log) + current.nutritionViewingLogs else current.nutritionViewingLogs, nutritionHistory = listOf(log) + current.nutritionHistory, transientMessage = "Favori öğün tekrar eklendi.") } }
            .onFailure { error -> _state.update { it.copy(nutritionBusy = false, transientMessage = friendlyError(error)) } }
    }

    fun addMealPlanItem(food: com.hedefit.app.data.model.FoodSearchData, grams: Double, date: LocalDate, mealType: String) = viewModelScope.launch {
        if (!requireEntitlement(LockedFeature.MealPlanner) { it.mealPlanner }) return@launch
        if (_state.value.nutritionBusy) return@launch
        _state.update { it.copy(nutritionBusy = true) }
        runCatching { repository.addMealPlanItem(food, grams, date, mealType) }
            .onSuccess { item -> _state.update { current -> current.copy(
                nutritionBusy = false,
                dashboard = current.dashboard?.copy(mealPlanItems = (current.dashboard.mealPlanItems + item).sortedBy { it.plannedDate }),
                transientMessage = "Öğün haftalık plana eklendi.",
            ) } }
            .onFailure { error -> _state.update { it.copy(nutritionBusy = false, transientMessage = friendlyError(error)) } }
    }

    fun toggleMealPlanItem(item: com.hedefit.app.data.model.MealPlanItemData, completed: Boolean) = viewModelScope.launch {
        if (_state.value.nutritionBusy) return@launch
        _state.update { current -> current.copy(
            nutritionBusy = true,
            dashboard = current.dashboard?.copy(mealPlanItems = current.dashboard.mealPlanItems.map { if (it.id == item.id) it.copy(completed = completed) else it }),
        ) }
        runCatching { repository.setMealPlanCompleted(item, completed) }
            .onSuccess { saved -> _state.update { current -> current.copy(
                nutritionBusy = false,
                dashboard = current.dashboard?.copy(mealPlanItems = current.dashboard.mealPlanItems.map { if (it.id == saved.id) saved else it }),
            ) } }
            .onFailure { error -> _state.update { current -> current.copy(
                nutritionBusy = false,
                dashboard = current.dashboard?.copy(mealPlanItems = current.dashboard.mealPlanItems.map { if (it.id == item.id) item else it }),
                transientMessage = friendlyError(error),
            ) } }
    }

    fun removeMealPlanItem(item: com.hedefit.app.data.model.MealPlanItemData) = viewModelScope.launch {
        if (_state.value.nutritionBusy) return@launch
        _state.update { it.copy(nutritionBusy = true) }
        runCatching { repository.removeMealPlanItem(item.id) }
            .onSuccess { _state.update { current -> current.copy(
                nutritionBusy = false,
                dashboard = current.dashboard?.copy(mealPlanItems = current.dashboard.mealPlanItems.filterNot { it.id == item.id }),
            ) } }
            .onFailure { error -> _state.update { it.copy(nutritionBusy = false, transientMessage = friendlyError(error)) } }
    }

    fun addWater(amountMl: Int) {
        if (amountMl == 0) return
        viewModelScope.launch {
            waterUpdateMutex.withLock {
                val dashboard = _state.value.dashboard ?: return@withLock
                val previous = dashboard.waterMl
                val next = (previous + amountMl).coerceIn(0, 20_000)
                if (next == previous) return@withLock
                _state.update { current -> current.copy(dashboard = current.dashboard?.copy(waterMl = next)) }
                runCatching { repository.setWater(next) }.onFailure { error ->
                    _state.update { current ->
                        val visibleWater = current.dashboard?.waterMl
                        current.copy(
                            dashboard = if (visibleWater == next) current.dashboard?.copy(waterMl = previous) else current.dashboard,
                            transientMessage = friendlyError(error),
                        )
                    }
                }
            }
        }
    }

    fun saveSleep(minutes: Int, quality: String = "iyi", bedTime: String? = null, wakeTime: String? = null) {
        val safeMinutes = minutes.coerceIn(0, 1_440)
        _state.update { current -> current.copy(dashboard = current.dashboard?.copy(sleepMinutes = safeMinutes)) }
        viewModelScope.launch {
            runCatching {
                repository.saveSleepLog(safeMinutes, quality, bedTime = bedTime, wakeTime = wakeTime)
            }.onSuccess {
                _state.update { it.copy(transientMessage = "Uyku süresi kaydedildi.") }
            }.onFailure { error ->
                _state.update { it.copy(transientMessage = friendlyError(error)) }
            }
        }
    }

    fun saveRoute(snapshot: RouteSnapshot, activityType: String, title: String) = viewModelScope.launch {
        if (!requireEntitlement(LockedFeature.Route) { it.routeSaving }) return@launch
        val route = RouteActivityData(
            id = snapshot.id,
            activityType = activityType,
            title = title,
            startedAt = Instant.ofEpochMilli(snapshot.startedAt).toString(),
            endedAt = Instant.ofEpochMilli(snapshot.stoppedAt).toString(),
            durationSeconds = snapshot.elapsedDurationSeconds,
            movingDurationSeconds = snapshot.durationSeconds,
            distanceMeters = snapshot.distanceMeters,
            averagePaceSecondsPerKm = snapshot.paceSecondsPerKm,
            averageSpeedKmh = snapshot.averageSpeedKmh,
            calories = ((snapshot.distanceMeters / 1_000.0) * if (activityType == "Bisiklet") 28 else if (activityType == "Kayak") 35 else if (activityType.contains("Koş")) 62 else 45).toInt(),
            routePoints = snapshot.points.map { com.hedefit.app.data.model.ActivityRoutePointData(it.latitude, it.longitude, it.recordedAt, it.accuracyMeters, it.altitude) },
        )
        fun showInHistory(message: String) = _state.update { current -> current.copy(
            dashboard = current.dashboard?.copy(routeActivities = listOf(route) + current.dashboard.routeActivities.filterNot { it.id == route.id }),
            transientMessage = message,
        ) }
        showInHistory("Rota Yapılanlar'a ekleniyor…")
        runCatching { repository.saveRoute(snapshot, activityType, title) }
            .onSuccess {
                showInHistory("Hedefit Rota kaydedildi; Yapılanlar'da görüntüleyebilirsin.")
            }
            .onFailure { error ->
                offlineQueue.enqueue("route", repository.routePayload(snapshot, activityType, title))
                OfflineSyncScheduler.enqueue(getApplication())
                showInHistory("Rota cihazda saklandı ve Yapılanlar'a eklendi; bağlantı gelince otomatik eşitlenecek. (${friendlyError(error)})")
                _state.update { it.copy(offlinePendingCount = offlineQueue.count()) }
            }
    }

    fun deleteRoute(route: RouteActivityData) = viewModelScope.launch {
        runCatching { repository.deleteRoute(route.id) }
            .onSuccess {
                _state.update { current -> current.copy(
                    dashboard = current.dashboard?.copy(routeActivities = current.dashboard.routeActivities.filterNot { it.id == route.id }),
                    transientMessage = "Rota kaydı silindi.",
                ) }
            }
            .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
    }

    fun uploadAvatar(bytes: ByteArray, mimeType: String) = viewModelScope.launch {
        if (_state.value.avatarUploading) return@launch
        val previousAvatar = _state.value.avatarPreview
        _state.update { it.copy(avatarUploading = true, avatarPreview = bytes) }
        try {
            val (path, url) = repository.uploadAvatar(bytes, mimeType)
            val userId = (_state.value.auth as? AuthState.SignedIn)?.session?.user?.id
            if (userId != null) cacheAvatar(userId, bytes)
            _state.update { current -> current.copy(avatarUploading = false, avatarPreview = bytes, dashboard = current.dashboard?.let { data -> data.copy(profile = data.profile.copy(avatarPath = path, avatarUrl = url)) }, transientMessage = "Profil fotoğrafın güncellendi.") }
        } catch (error: Throwable) {
            _state.update { it.copy(avatarUploading = false, avatarPreview = previousAvatar, transientMessage = friendlyError(error)) }
        }
    }

    fun scheduleWorkout(date: java.time.LocalDate, time: String, originalDate: String? = null, programId: String? = null, programName: String? = null) = viewModelScope.launch {
        runCatching { repository.scheduleWorkout(date, time, originalDate = originalDate, programId = programId, programName = programName) }
            .onSuccess { entry -> _state.update { current -> current.copy(dashboard = current.dashboard?.copy(schedule = current.dashboard.schedule.filterNot { it.date == entry.date } + entry), transientMessage = "Antrenman takvime kaydedildi.") } }
            .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
    }

    /**
     * Set yapılmadan bitirilen antrenman. Ertele: bugün "deferred", takvimde bugünden sonraki
     * ilk boş gün aynı saatte "planned" olur. Pas geç: bugün "rest" (kaçırıldı sayılmaz).
     */
    fun skipTodayWorkout(postpone: Boolean, programId: String?, programName: String?, locale: String = "tr") = viewModelScope.launch {
        val en = locale == "en"
        val today = java.time.LocalDate.now()
        val schedule = _state.value.dashboard?.schedule.orEmpty()
        val time = schedule.firstOrNull { it.date == today.toString() }?.time?.takeIf { it.isNotBlank() } ?: "19:00"
        runCatching {
            val entries = mutableListOf(repository.scheduleWorkout(today, time, status = if (postpone) "deferred" else "rest", programId = programId, programName = programName))
            if (postpone) {
                val taken = schedule.map { it.date }.toSet()
                val next = generateSequence(today.plusDays(1)) { it.plusDays(1) }.take(60).first { it.toString() !in taken }
                entries += repository.scheduleWorkout(next, time, originalDate = today.toString(), programId = programId, programName = programName)
            }
            entries
        }.onSuccess { entries -> _state.update { current -> current.copy(
                dashboard = current.dashboard?.let { data -> data.copy(schedule = data.schedule.filterNot { s -> entries.any { it.date == s.date } } + entries) },
                transientMessage = if (postpone) {
                    val d = java.time.LocalDate.parse(entries.last().date)
                    val label = d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM EEEE", if (en) java.util.Locale.US else java.util.Locale("tr", "TR")))
                    if (en) "Workout moved to $label." else "Antrenman $label gününe eklendi."
                } else if (en) "Skipped today's workout." else "Bugünkü antrenman pas geçildi.",
            ) } }
            .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
    }

    fun autoDistributeProgram(month: java.time.YearMonth, dayTimes: Map<java.time.DayOfWeek, String>, programs: List<Pair<String, String>>, locale: String = "tr") = viewModelScope.launch {
        val en = locale == "en"
        if (programs.isEmpty() || dayTimes.isEmpty()) return@launch
        val today = java.time.LocalDate.now()
        val start = if (month.atDay(1).isBefore(today)) today else month.atDay(1)
        val dates = generateSequence(start) { it.plusDays(1) }
            .takeWhile { java.time.YearMonth.from(it) == month }
            .filter { it.dayOfWeek in dayTimes }
            .toList()
        if (dates.isEmpty()) {
            _state.update { it.copy(transientMessage = if (en) "No matching days left this month." else "Bu ay için uygun gün kalmadı.") }
            return@launch
        }
        runCatching {
            dates.mapIndexed { index, date ->
                val (programId, programName) = programs[index % programs.size]
                repository.scheduleWorkout(date, dayTimes.getValue(date.dayOfWeek), programId = programId, programName = programName)
            }
        }.onSuccess { entries -> _state.update { current -> current.copy(
                dashboard = current.dashboard?.let { data -> data.copy(schedule = data.schedule.filterNot { s -> entries.any { it.date == s.date } } + entries) },
                transientMessage = if (en) "Scheduled on ${entries.size} days." else "${entries.size} güne dağıtıldı.",
            ) } }
            .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
    }

    private var exerciseNamesLoading = false

    /** Katalogu TR ve EN çekip kimlik → (TR, EN) ad eşlemesini kurar; başarılı olunca tekrar çekmez. */
    fun ensureExerciseNames() {
        if (exerciseNamesLoading || _state.value.exerciseNames.byId.isNotEmpty() || _state.value.dashboard == null) return
        exerciseNamesLoading = true
        viewModelScope.launch {
            runCatching {
                val trItems = repository.loadExerciseCatalog(locale = "tr")
                val enById = repository.loadExerciseCatalog(locale = "en").associate { it.id to it.name }
                val byId = trItems.mapNotNull { item -> enById[item.id]?.let { en -> item.id to (item.name to en) } }.toMap()
                val byName = byId.values.flatMap { pair -> listOf(pair.first.lowercase() to pair, pair.second.lowercase() to pair) }.toMap()
                com.hedefit.app.ui.i18n.ExerciseNameIndex(byId, byName)
            }.onSuccess { index -> _state.update { it.copy(exerciseNames = index) } }
            exerciseNamesLoading = false
        }
    }

    fun loadExerciseLibrary(search: String = "", muscle: String = "", equipment: String = "", level: String = "", environment: String = "", muscleRole: String = "", force: String = "", mechanic: String = "", category: String = "", locale: String = "tr") {
        viewModelScope.launch {
            _state.update { it.copy(exerciseLibraryBusy = true) }
            runCatching { repository.loadExerciseCatalog(search, muscle, equipment, level, environment, muscleRole, force, mechanic, category, locale) }
                .onSuccess { items -> _state.update { it.copy(exerciseLibraryBusy = false, exerciseLibrary = items) } }
                .onFailure { error -> _state.update { it.copy(exerciseLibraryBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun loadPreviousPerformance(requestedExercises: List<com.hedefit.app.data.model.WorkoutExerciseData>? = null) {
        val exercises = requestedExercises ?: _state.value.dashboard?.workouts.orEmpty()
        viewModelScope.launch { runCatching { repository.loadPreviousPerformance(exercises) }.onSuccess { previous -> _state.update { it.copy(previousPerformance = previous) } } }
    }

    fun useExerciseFromLibrary(item: ExerciseCatalogData) {
        if (!requireEntitlement(LockedFeature.Exercise) { it.canUseExercise(item) }) return
        val dashboard = _state.value.dashboard ?: return
        val current = dashboard.workouts.toMutableList()
        val replacement = com.hedefit.app.data.model.WorkoutExerciseData(item.id, item.name, item.primaryMuscles.firstOrNull() ?: "Tüm Vücut", 3, "8–12", 75)
        if (current.none { it.id == replacement.id || it.name.equals(replacement.name, ignoreCase = true) }) current += replacement
        _state.update { it.copy(dashboard = dashboard.copy(workouts = current)) }
        viewModelScope.launch {
            val active = dashboard.workoutPrograms.firstOrNull { it.isActive }
            runCatching {
                if (active != null) repository.saveProgram(active.name, active.source, active.focusArea, current, active.id, active.showOnHome, active.trainingDays)
                else repository.saveProgram("Kendi Programım", "custom", replacement.area, current)
            }.onSuccess { program -> _state.update { state -> state.copy(dashboard = state.dashboard?.let { data -> data.copy(workouts = current, workoutPrograms = withActiveProgram(data.workoutPrograms, program)) }, transientMessage = "Hareket programa eklendi.") } }
                .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
        }
    }

    fun createProgramFromExercises(name: String, exercises: List<ExerciseCatalogData>, locale: String = "tr") {
        if (!canCreateCustomProgram()) return
        if (_state.value.planGenerating || exercises.isEmpty()) return
        val en = locale == "en"
        viewModelScope.launch {
            _state.update { it.copy(planGenerating = true) }
            val plan = exercises.map { item -> com.hedefit.app.data.model.WorkoutExerciseData(item.id, item.name, item.primaryMuscles.firstOrNull() ?: "Tüm Vücut", 3, "8–12", 75) }
            val focusArea = exercises.flatMap { it.primaryMuscles }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""
            runCatching { repository.saveProgram(name.ifBlank { if (en) "My Program" else "Programım" }, "custom", focusArea, plan) }
                .onSuccess { program -> _state.update { current -> current.copy(
                    planGenerating = false,
                    dashboard = current.dashboard?.let { data -> data.copy(workouts = program.exercises, workoutPrograms = withActiveProgram(data.workoutPrograms, program)) },
                    transientMessage = if (en) "${program.name} was created and activated." else "${program.name} oluşturuldu ve aktif edildi.",
                ) } }
                .onFailure { error -> _state.update { it.copy(planGenerating = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun updateWorkoutExercise(updated: com.hedefit.app.data.model.WorkoutExerciseData) = mutateWorkoutPlan("Hareket güncellendi.") { current ->
        current.map { if (it.id == updated.id) updated else it }
    }

    fun replaceWorkoutExercise(previousId: String, replacement: com.hedefit.app.data.model.WorkoutExerciseData) = mutateWorkoutPlan("Hareket değiştirildi.") { current ->
        current.map { if (it.id == previousId) replacement else it }
    }

    fun removeWorkoutExercise(id: String) = mutateWorkoutPlan("Hareket programdan çıkarıldı.") { current -> current.filterNot { it.id == id } }

    fun moveWorkoutExercise(id: String, offset: Int) = mutateWorkoutPlan("Program sırası güncellendi.") { current ->
        val from = current.indexOfFirst { it.id == id }
        val to = (from + offset).coerceIn(0, current.lastIndex)
        if (from < 0 || from == to) current else current.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun mutateWorkoutPlan(message: String, transform: (List<com.hedefit.app.data.model.WorkoutExerciseData>) -> List<com.hedefit.app.data.model.WorkoutExerciseData>) {
        val dashboard = _state.value.dashboard ?: return
        val next = transform(dashboard.workouts)
        _state.update { it.copy(dashboard = dashboard.copy(workouts = next)) }
        viewModelScope.launch {
            val active = dashboard.workoutPrograms.firstOrNull { it.isActive }
            runCatching { if (active != null) repository.saveProgram(active.name, active.source, active.focusArea, next, active.id, active.showOnHome, active.trainingDays) else { repository.saveWorkoutPlan(next); null } }
                .onSuccess { saved -> _state.update { state -> state.copy(dashboard = state.dashboard?.let { data -> data.copy(workoutPrograms = saved?.let { withActiveProgram(data.workoutPrograms, it) } ?: data.workoutPrograms) }, transientMessage = message) } }
                .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
        }
    }

    fun createCustomProgram(draft: CustomProgramDraft, locale: String = "tr", onComplete: () -> Unit = {}) {
        if (!canCreateCustomProgram()) return
        viewModelScope.launch {
            runCatching { repository.saveProgram(draft.name.ifBlank { if (locale == "en") "My Program" else "Programım" }, "custom", "", emptyList(), trainingDays = draft.trainingDays) }
                .onSuccess { program -> _state.update { state -> state.copy(dashboard = state.dashboard?.let { data -> data.copy(workouts = emptyList(), workoutPrograms = withActiveProgram(data.workoutPrograms, program)) }, transientMessage = if (locale == "en") "Custom program created." else "Kendi programın oluşturuldu.") }; onComplete() }
                .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
        }
    }

    fun copyProgram(program: WorkoutProgramData) {
        if (!canCreateCustomProgram()) return
        viewModelScope.launch {
            runCatching { repository.saveProgram("${program.name} Kopyası", "custom", program.focusArea, program.exercises, trainingDays = program.trainingDays) }
                .onSuccess { copy -> _state.update { state -> state.copy(dashboard = state.dashboard?.let { data -> data.copy(workouts = copy.exercises, workoutPrograms = withActiveProgram(data.workoutPrograms, copy)) }, transientMessage = "Program kopyalandı ve aktif edildi.") } }
                .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
        }
    }

    fun addPushPullTemplate(key: String, locale: String = "tr") {
        if (_state.value.planGenerating) return
        val en = locale == "en"
        viewModelScope.launch {
            _state.update { it.copy(planGenerating = true) }
            // Şablonlardaki hareket adları RepDB kataloğundan, o anki `locale` ile
            // canlı çekilir — sabit Türkçe/İngilizce metin kopyalamak (eski hâl)
            // yanlış/eksik çeviri riski taşır; katalog `lib/exercise-translations.ts`
            // ile tek, tutarlı çeviri kaynağıdır.
            val nameLookup = runCatching { repository.loadExerciseCatalog(locale = locale) }
                .getOrDefault(emptyList())
                .associate { it.id to it.name }
            val definitions = pushPullTemplate(key, en, nameLookup) ?: run { _state.update { it.copy(planGenerating = false) }; return@launch }
            // Push / Pull şablonları kullanıcının kütüphanesine eklenen programlardır.
            // "custom" kaynak türü mevcut canlı şemada desteklenir; ayrı bir kaynak
            // etiketi kullanmak eski istemcilerdeki check constraint'i ihlal eder.
            runCatching { repository.saveProgram(definitions.first, "custom", definitions.first, definitions.second) }
                .onSuccess { program -> _state.update { current -> current.copy(
                    planGenerating = false,
                    dashboard = current.dashboard?.let { data -> data.copy(workouts = program.exercises, workoutPrograms = withActiveProgram(data.workoutPrograms, program)) },
                    transientMessage = if (en) "${program.name} was added and activated." else "${program.name} eklendi ve aktif edildi.",
                ) } }
                .onFailure { error -> _state.update { it.copy(planGenerating = false, transientMessage = friendlyError(error)) } }
        }
    }

    // Hareket id'leri RepDB kataloğuna (bkz. fit-ai/scripts/import-repdb.mjs) karşılık
    // gelir: görsellerin doğru yüklenmesi (ExerciseMedia/workoutExerciseImagePaths)
    // ve "Değiştir" ile katalogdan alternatif bulunabilmesi buna bağlıdır.
    /** Önizleme: şablonun hareketleri; adlar yüklüyse katalogdan uygulama dilinde gelir. */
    fun previewReadyProgram(key: String, en: Boolean): Pair<String, List<com.hedefit.app.data.model.WorkoutExerciseData>>? =
        pushPullTemplate(key, en, _state.value.exerciseNames.byId.mapValues { (_, pair) -> if (en) pair.second else pair.first })

    private fun pushPullTemplate(key: String, en: Boolean, nameLookup: Map<String, String>): Pair<String, List<com.hedefit.app.data.model.WorkoutExerciseData>>? {
        // İsimler her zaman katalogdan (bkz. addPushPullTemplate) gelir — burada
        // yalnızca id id'ye eşlik eden fallback (katalog yüklenemezse) ve
        // set/tekrar/dinlenme reçetesi tanımlanır.
        fun exercise(id: String, fallback: String, area: String, sets: Int, reps: String, rest: Int) =
            com.hedefit.app.data.model.WorkoutExerciseData(id, nameLookup[id] ?: fallback, area, sets, reps, rest)
        val pushA = listOf(
            exercise("bench-press", "Barbell Bench Press", if (en) "Chest" else "Göğüs", 4, "6–8", 120),
            exercise("incline-db-press", "Incline Dumbbell Press", if (en) "Upper chest" else "Üst göğüs", 3, "8–12", 90),
            exercise("dumbbell-shoulder-press", "Dumbbell Shoulder Press", if (en) "Shoulders" else "Omuz", 3, "8–12", 90),
            exercise("seated-dumbbell-lateral-raise", "Dumbbell Lateral Raise", if (en) "Shoulders" else "Omuz", 3, "12–15", 60),
            exercise("tricep-pushdown", "Cable Triceps Pushdown", if (en) "Triceps" else "Arka kol", 3, "10–15", 60),
        )
        val pushB = listOf(
            exercise("seated-barbell-overhead-press", "Barbell Overhead Press", if (en) "Shoulders" else "Omuz", 4, "6–8", 120),
            exercise("db-bench-press", "Dumbbell Bench Press", if (en) "Chest" else "Göğüs", 3, "8–12", 90),
            exercise("cable-fly", "Cable Fly", if (en) "Chest" else "Göğüs", 3, "12–15", 60),
            exercise("seated-dumbbell-lateral-raise", "Dumbbell Lateral Raise", if (en) "Shoulders" else "Omuz", 4, "12–20", 60),
            exercise("overhead-tricep-extension", "Overhead Triceps Extension", if (en) "Triceps" else "Arka kol", 3, "10–15", 60),
        )
        val pullA = listOf(
            exercise("pull-up", "Pull-up", if (en) "Back" else "Sırt", 4, "6–10", 120),
            exercise("v-bar-lat-pulldown", "Lat Pulldown", if (en) "Back" else "Sırt", 3, "8–12", 90),
            exercise("seated-cable-row", "Seated Cable Row", if (en) "Back" else "Sırt", 3, "8–12", 90),
            exercise("face-pull", "Face Pull", if (en) "Rear delts" else "Arka omuz", 3, "12–15", 60),
            exercise("seated-dumbbell-curl", "Seated Dumbbell Curl", if (en) "Biceps" else "Ön kol", 3, "10–15", 60),
        )
        val pullB = listOf(
            exercise("barbell-row", "Barbell Row", if (en) "Back" else "Sırt", 4, "6–8", 120),
            exercise("close-grip-lat-pulldown", "Close-Grip Lat Pulldown", if (en) "Back" else "Sırt", 3, "8–12", 90),
            exercise("chest-supported-db-row", "Chest-Supported Dumbbell Row", if (en) "Back" else "Sırt", 3, "8–12", 90),
            exercise("rear-delt-fly", "Rear Delt Fly", if (en) "Rear delts" else "Arka omuz", 3, "12–15", 60),
            exercise("hammer-curl", "Hammer Curl", if (en) "Biceps" else "Ön kol", 3, "10–15", 60),
        )
        val legA = listOf(
            exercise("squat", "Barbell Back Squat", if (en) "Quads" else "Ön bacak", 4, "6–8", 150),
            exercise("bulgarian-split-squat", "Bulgarian Split Squat", if (en) "Quads & glutes" else "Ön bacak & kalça", 3, "8–12", 90),
            exercise("close-stance-leg-press", "Leg Press", if (en) "Quads" else "Ön bacak", 3, "10–15", 90),
            exercise("leg-extension", "Leg Extension", if (en) "Quads" else "Ön bacak", 3, "12–15", 60),
            exercise("standing-calf-raise", "Standing Calf Raise", if (en) "Calves" else "Baldır", 4, "12–15", 45),
        )
        val legB = listOf(
            exercise("romanian-deadlift", "Romanian Deadlift", if (en) "Hamstrings & glutes" else "Arka bacak & kalça", 4, "8–10", 120),
            exercise("hip-thrust", "Barbell Hip Thrust", if (en) "Glutes" else "Kalça", 4, "8–12", 90),
            exercise("seated-leg-curl", "Seated Leg Curl", if (en) "Hamstrings" else "Arka bacak", 3, "10–15", 60),
            exercise("goblet-squat", "Goblet Squat", if (en) "Quads & glutes" else "Ön bacak & kalça", 3, "10–12", 90),
            exercise("seated-calf-raise", "Seated Calf Raise", if (en) "Calves" else "Baldır", 4, "12–15", 45),
        )
        val fullA = listOf(
            exercise("squat", "Barbell Back Squat", if (en) "Legs" else "Bacak", 3, "8–10", 120),
            exercise("bench-press", "Barbell Bench Press", if (en) "Chest" else "Göğüs", 3, "8–10", 120),
            exercise("barbell-row", "Barbell Row", if (en) "Back" else "Sırt", 3, "8–10", 90),
            exercise("dumbbell-shoulder-press", "Dumbbell Shoulder Press", if (en) "Shoulders" else "Omuz", 3, "10–12", 90),
            exercise("plank", "Plank", if (en) "Core" else "Karın", 3, "30–45 sn", 45),
        )
        val fullB = listOf(
            exercise("deadlift", "Barbell Deadlift", if (en) "Full body" else "Tüm vücut", 3, "6–8", 150),
            exercise("db-bench-press", "Dumbbell Bench Press", if (en) "Chest" else "Göğüs", 3, "10–12", 90),
            exercise("pull-up", "Pull-up", if (en) "Back" else "Sırt", 3, "6–10", 120),
            exercise("goblet-squat", "Goblet Squat", if (en) "Quads & glutes" else "Ön bacak & kalça", 3, "10–12", 90),
            exercise("hanging-leg-raise", "Hanging Leg Raise", if (en) "Core" else "Karın", 3, "10–15", 60),
        )
        return when (key) {
            "push_a" -> readyProgramTitle(key, en) to pushA
            "push_b" -> readyProgramTitle(key, en) to pushB
            "pull_a" -> readyProgramTitle(key, en) to pullA
            "pull_b" -> readyProgramTitle(key, en) to pullB
            "leg_a" -> readyProgramTitle(key, en) to legA
            "leg_b" -> readyProgramTitle(key, en) to legB
            "full_a" -> readyProgramTitle(key, en) to fullA
            "full_b" -> readyProgramTitle(key, en) to fullB
            else -> null
        }
    }

    fun activateProgram(program: WorkoutProgramData) {
        viewModelScope.launch { runCatching { repository.activateProgram(program) }
            .onSuccess { active -> _state.update { state -> state.copy(dashboard = state.dashboard?.let { data -> data.copy(workouts = active.exercises, workoutPrograms = withActiveProgram(data.workoutPrograms, active)) }, transientMessage = "${active.name} aktif program oldu.") } }
            .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } } }
    }

    fun setProgramHomeVisibility(program: WorkoutProgramData, showOnHome: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setProgramHomeVisibility(program, showOnHome) }
                .onSuccess { updated -> _state.update { state -> state.copy(dashboard = state.dashboard?.let { data ->
                    data.copy(workoutPrograms = data.workoutPrograms.map { if (it.id == updated.id) updated else it })
                }) } }
                .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } }
        }
    }

    fun deleteProgram(program: WorkoutProgramData) {
        viewModelScope.launch { runCatching { repository.deleteProgram(program) }
            .onSuccess { active -> _state.update { state -> state.copy(dashboard = state.dashboard?.let { data ->
                val remaining = data.workoutPrograms.filterNot { it.id == program.id }
                data.copy(
                    workouts = if (program.isActive) active?.exercises.orEmpty() else data.workouts,
                    workoutPrograms = active?.let { withActiveProgram(remaining, it) } ?: remaining,
                )
            }, transientMessage = "Program kaldırıldı.") } }
            .onFailure { error -> _state.update { it.copy(transientMessage = friendlyError(error)) } } }
    }

    private fun withActiveProgram(current: List<WorkoutProgramData>, active: WorkoutProgramData) = listOf(active.copy(isActive = true)) + current.filterNot { it.id == active.id }.map { it.copy(isActive = false) }

    private suspend fun buildRegionalProgram(muscle: String, label: String, locale: String): com.hedefit.app.data.model.WorkoutProgramData {
        val plan = repository.loadExerciseCatalog(muscle = muscle, muscleRole = "primary", category = "strength", locale = locale)
            .sortedWith(compareBy<ExerciseCatalogData> { if (it.mechanic == "compound") 0 else 1 }.thenBy { it.name })
            .take(5).mapIndexed { index, item ->
            com.hedefit.app.data.model.WorkoutExerciseData(item.id, item.name, label, if (index < 2) 4 else 3, "8–12", 75)
        }
        require(plan.isNotEmpty()) { if (locale == "en") "No exercises found for this area." else "Bu bölge için hareket bulunamadı." }
        return repository.saveProgram(if (locale == "en") "$label Program" else "$label Programı", "regional", label, plan)
    }

    /**
     * `connected`, bir sinerjist bölgeyi (ör. Göğüs seçilince Arka Kol) AYRI bir
     * program olarak ekler — tek programın içine karıştırmak yerine kullanıcı
     * ikisini birbirinden bağımsız açıp kapatabilsin, farklı günlerde
     * çalışabilsin diye. Bağlı bölge hareketi bulunamazsa (nadiren) sessizce
     * atlanır; ana bölge programı yine de oluşturulur.
     */
    fun generateRegionalPlan(muscle: String, label: String, connected: Pair<String, String>? = null, locale: String = "tr") {
        if (!requireEntitlement(LockedFeature.RegionalPlan) { it.regionalPlans }) return
        if (_state.value.planGenerating) return
        viewModelScope.launch {
            _state.update { it.copy(planGenerating = true) }
            runCatching { buildRegionalProgram(muscle, label, locale) to connected?.let { (cm, cl) -> runCatching { buildRegionalProgram(cm, cl, locale) }.getOrNull() } }
                .onSuccess { (primary, secondary) -> _state.update { current -> current.copy(
                    planGenerating = false,
                    dashboard = current.dashboard?.let { data ->
                        val withPrimary = withActiveProgram(data.workoutPrograms, primary)
                        val withBoth = secondary?.let { withActiveProgram(withPrimary, it) } ?: withPrimary
                        data.copy(workouts = (secondary ?: primary).exercises, workoutPrograms = withBoth)
                    },
                    transientMessage = when {
                        secondary != null -> if (locale == "en") "${primary.name} and ${secondary.name} are ready." else "${primary.name} ve ${secondary.name} hazır."
                        connected != null -> if (locale == "en") "${primary.name} is ready; the connected area had no matching exercises." else "${primary.name} hazır; bağlı bölge için uygun hareket bulunamadı."
                        else -> if (locale == "en") "$label plan is ready." else "$label odaklı programın hazır."
                    },
                ) } }
                .onFailure { error -> _state.update { it.copy(planGenerating = false, transientMessage = friendlyError(error)) } }
        }
    }

    /**
     * Kullanıcının o an eldeki süresi, yorgunluğu ve çalışmak istediği bölge(ler)ine
     * göre tek seferlik, kaydetmeye değer bir program üretir — 15 sorulu teste
     * girmeden "5 dakikam var, yorgunum, bacak çalışayım" gibi anlık ihtiyacı
     * karşılar. Birden fazla bölge seçilirse hareket sayısı bölgeler arasında
     * mümkün olduğunca eşit paylaştırılır.
     */
    /**
     * Builds a one-off session from the user's time, energy, place, owned equipment and level.
     * Equipment is strict: at home only moves doable with exactly the owned items (plus
     * no-equipment moves) are used; nothing is silently swapped for other gear.
     */
    fun generateQuickWorkout(regions: List<Pair<String, String>>, durationMinutes: Int, fatigue: String, environment: String, owned: List<String>, level: String, locale: String = "tr") {
        if (_state.value.planGenerating || regions.isEmpty()) return
        val en = locale == "en"
        viewModelScope.launch {
            _state.update { it.copy(planGenerating = true) }
            runCatching {
                val totalExercises = when {
                    durationMinutes <= 20 -> 3
                    durationMinutes <= 30 -> 4
                    durationMinutes <= 45 -> 6
                    else -> 8
                }
                val (sets, reps, rest) = when (fatigue) {
                    "yorgun" -> Triple(2, "12–15", 45)
                    "dinc" -> Triple(4, "6–10", 90)
                    else -> Triple(3, "8–12", 60)
                }
                val fullBody = regions.any { it.first == FULL_BODY }
                val targets = if (fullBody) FULL_BODY_REGIONS.map { it to regionLabel(it, en) } else regions
                val ownedFilter = if (environment == "gym") listOf("gym") else owned.ifEmpty { listOf("none") }
                val maxLevel = LEVEL_ORDER.indexOf(level).coerceAtLeast(0)
                val chosen = mutableListOf<com.hedefit.app.data.model.WorkoutExerciseData>()
                val usedFamilies = mutableSetOf<String>()
                val perRegion = targets.associate { (muscle, _) ->
                    muscle to repository.loadExerciseCatalog(muscle = muscle, muscleRole = "primary", category = "strength", locale = locale, owned = ownedFilter)
                        .filter { LEVEL_ORDER.indexOf(it.levelKey).let { lvl -> lvl < 0 || lvl <= maxLevel } }
                        .sortedWith(compareBy<ExerciseCatalogData>(
                            // At home, use the equipment the person chose before falling back to bodyweight.
                            { item -> if (environment != "gym" && owned.isNotEmpty() && item.requiredEquipment.none { option -> option.isNotEmpty() && option.all(owned::contains) }) 1 else 0 },
                            { if (it.mechanic == "compound") 0 else 1 },
                            { if (FOUNDATION_LIFT.containsMatchIn(it.id)) 0 else 1 },
                            // Prefer the user's own level, then easier, over harder variations.
                            { kotlin.math.abs(LEVEL_ORDER.indexOf(it.levelKey).coerceAtLeast(0) - maxLevel) },
                            { it.id.hashCode() },
                        ))
                }
                // Round-robin across regions so every selected area gets work before any gets a second move.
                var round = 0
                while (chosen.size < totalExercises && round < 6) {
                    targets.forEach { (muscle, label) ->
                        if (chosen.size >= totalExercises) return@forEach
                        val pool = perRegion[muscle].orEmpty().filter { item -> chosen.none { it.id == item.id } }
                        val pick = pool.firstOrNull { movementFamily(it.id) !in usedFamilies } ?: pool.firstOrNull()
                        if (pick != null) {
                            usedFamilies += movementFamily(pick.id)
                            chosen += com.hedefit.app.data.model.WorkoutExerciseData(pick.id, pick.name, label, sets, reps, rest)
                        }
                    }
                    round++
                }
                require(chosen.isNotEmpty()) { if (en) "No exercises match this equipment and level for the selected areas." else "Seçilen bölgeler için bu ekipman ve seviyeye uygun hareket bulunamadı." }
                val regionNames = if (fullBody) (if (en) "Full body" else "Tüm vücut") else regions.joinToString(" & ") { it.second }
                val name = if (en) "Quick Workout: $regionNames" else "Hızlı Antrenman: $regionNames"
                repository.saveProgram(name, "custom", regionNames, chosen)
            }.onSuccess { program -> _state.update { current -> current.copy(
                planGenerating = false,
                dashboard = current.dashboard?.let { data -> data.copy(workouts = program.exercises, workoutPrograms = withActiveProgram(data.workoutPrograms, program)) },
                transientMessage = if (en) "${program.name} is ready." else "${program.name} hazır.",
            ) } }
                .onFailure { error -> _state.update { it.copy(planGenerating = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun generatePlan() {
        val profile = _state.value.dashboard?.profile ?: return
        if (_state.value.planGenerating) return
        viewModelScope.launch {
            _state.update { it.copy(planGenerating = true, transientMessage = null) }
            val existingId = _state.value.dashboard?.workoutPrograms?.firstOrNull { it.source == "assessment" }?.id
            runCatching { repository.generatePlan(profile).let { workouts -> repository.saveProgram("Kişisel Atlas Programım", "assessment", profile.goal, workouts, existingId ?: java.util.UUID.randomUUID().toString(), showOnHome = true) } }
                .onSuccess { program ->
                    _state.update { current ->
                        current.copy(
                            planGenerating = false,
                            dashboard = current.dashboard?.let { data -> data.copy(workouts = program.exercises, workoutPrograms = withActiveProgram(data.workoutPrograms, program)) },
                            transientMessage = "Kişisel antrenman programın hazır.",
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(planGenerating = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun addNutritionWithAi(food: String, grams: Double, meal: String) {
        if (!canAddMeals()) return
        if (_state.value.nutritionBusy) return
        if (looksLikeWholeMeal(food)) {
            viewModelScope.launch {
                _state.update { it.copy(nutritionBusy = true, transientMessage = null) }
                runCatching { repository.parseMealText(food) }
                    .onSuccess { items -> _state.update { it.copy(nutritionBusy = false, photoNutritionResults = items, mealReviewSource = "text", mealReviewMeal = meal) } }
                    .onFailure { error -> _state.update { it.copy(nutritionBusy = false, transientMessage = friendlyError(error)) } }
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(nutritionBusy = true, transientMessage = null) }
            runCatching {
                val estimate = repository.estimateNutrition(food, grams)
                repository.addNutrition(estimate, meal)
            }.onSuccess { log ->
                _state.update { current ->
                    current.copy(
                        nutritionBusy = false,
                        dashboard = current.dashboard?.copy(nutritionLogs = listOf(log) + current.dashboard.nutritionLogs),
                        nutritionViewingLogs = if (current.nutritionViewingDate == LocalDate.now()) listOf(log) + current.nutritionViewingLogs else current.nutritionViewingLogs,
                        nutritionHistory = listOf(log) + current.nutritionHistory,
                        transientMessage = "${log.name} öğün günlüğüne eklendi.",
                    )
                }
            }.onFailure { error -> _state.update { it.copy(nutritionBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun sendChat(text: String, locale: String = "tr", workoutContext: WorkoutCoachContext? = null) {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.chatBusy) return
        val userMessage = ChatMessageState(clean, true)
        _state.update { it.copy(chatBusy = true, chatMessages = it.chatMessages + userMessage) }
        viewModelScope.launch {
            val history = _state.value.chatMessages.map { it.text to it.user }
            runCatching {
                repository.sendChat(history, _state.value.dashboard, locale, workoutContext ?: _state.value.activeCoachContext)
            }
                .onSuccess { reply ->
                    _state.update {
                        it.copy(
                            chatBusy = false,
                            chatMessages = it.chatMessages + ChatMessageState(reply.text, false, actions = reply.actions),
                            chatUsageUsed = reply.used ?: it.chatUsageUsed,
                            chatUsageLimit = reply.limit ?: it.chatUsageLimit,
                        )
                    }
                    val now = _state.value
                    if (now.isGuest && now.chatUsageLimit != null && (now.chatUsageUsed ?: 0) >= now.chatUsageLimit) showSaveAccountPrompt(SaveAccountTrigger.CoachLimit)
                }
                .onFailure { error ->
                    val message = friendlyError(error)
                    _state.update { it.copy(chatBusy = false, chatMessages = it.chatMessages + ChatMessageState("Fit Koç şu anda yanıtı tamamlayamadı: $message", false), transientMessage = message) }
                    if (_state.value.isGuest && (message.contains("limit", true) || message.contains("hak", true))) showSaveAccountPrompt(SaveAccountTrigger.CoachLimit)
                }
        }
    }

    fun setActiveCoachContext(context: WorkoutCoachContext?) {
        _state.update { it.copy(activeCoachContext = context) }
    }

    fun submitReadinessCheckin(
        input: DailyReadinessInput,
        exercises: List<WorkoutExerciseData>? = null,
        locale: String = "tr",
        onComplete: (ReadinessAdaptationData) -> Unit = {},
    ) {
        val currentExercises = exercises ?: _state.value.dashboard?.workouts.orEmpty()
        if (currentExercises.isEmpty()) return
        _state.update { it.copy(readinessCheckinBusy = true) }
        viewModelScope.launch {
            runCatching {
                repository.adaptWorkoutForReadiness(input, currentExercises, _state.value.dashboard?.profile, locale)
            }.onSuccess { adaptation ->
                _state.update { it.copy(readinessCheckinBusy = false, readinessAdaptation = adaptation) }
                onComplete(adaptation)
            }.onFailure { error ->
                _state.update { it.copy(readinessCheckinBusy = false, transientMessage = friendlyError(error)) }
            }
        }
    }

    fun applyReadinessAdaptation(adaptation: ReadinessAdaptationData) {
        val adapted = adaptation.adaptedExercises
        if (adapted.isNotEmpty()) {
            _state.update { current ->
                current.copy(
                    dashboard = current.dashboard?.copy(workouts = adapted),
                    readinessAdaptation = null,
                    transientMessage = "Antrenman günlük toparlanma durumuna göre uyarlandı.",
                )
            }
        } else {
            _state.update { it.copy(readinessAdaptation = null) }
        }
    }

    fun dismissReadinessAdaptation() {
        _state.update { it.copy(readinessAdaptation = null) }
    }

    fun requestExerciseReplacement(
        currentExerciseId: String,
        reason: String,
        exercises: List<WorkoutExerciseData>? = null,
        discomfortArea: String? = null,
        locale: String = "tr",
        onComplete: (ExerciseReplacementCandidate?) -> Unit = {},
    ) {
        val currentExercises = exercises ?: _state.value.dashboard?.workouts.orEmpty()
        _state.update { it.copy(replacementBusy = true) }
        viewModelScope.launch {
            runCatching {
                repository.replaceWorkoutExercise(currentExerciseId, reason, currentExercises, _state.value.dashboard?.profile, discomfortArea, locale)
            }.onSuccess { candidate ->
                _state.update { it.copy(replacementBusy = false, replacementCandidate = candidate) }
                onComplete(candidate)
            }.onFailure { error ->
                _state.update { it.copy(replacementBusy = false, transientMessage = friendlyError(error)) }
            }
        }
    }

    fun applyExerciseReplacement(replacement: ExerciseReplacementCandidate) {
        val msg = "'${replacement.originalExerciseName}' hareketi '${replacement.replacementExerciseName}' ile değiştirildi."
        mutateWorkoutPlan(msg) { current ->
            current.map { item ->
                if (item.id == replacement.originalExerciseId) {
                    item.copy(
                        id = replacement.replacementExerciseId,
                        name = replacement.replacementExerciseName,
                        sets = replacement.sets,
                        reps = replacement.reps,
                        restSeconds = replacement.restSeconds,
                    )
                } else item
            }
        }
        _state.update { it.copy(replacementCandidate = null) }
    }

    fun dismissExerciseReplacement() {
        _state.update { it.copy(replacementCandidate = null) }
    }

    fun requestPlanAdaptation(
        trigger: String,
        targetMinutes: Int?,
        exercises: List<WorkoutExerciseData>? = null,
        locale: String = "tr",
        onComplete: (WorkoutAdaptationResultData) -> Unit = {},
    ) {
        val currentExercises = exercises ?: _state.value.dashboard?.workouts.orEmpty()
        _state.update { it.copy(planAdaptationBusy = true) }
        viewModelScope.launch {
            runCatching {
                repository.adaptWorkoutPlan(trigger, targetMinutes, currentExercises, _state.value.dashboard?.profile, locale)
            }.onSuccess { result ->
                _state.update { it.copy(planAdaptationBusy = false, planAdaptationResult = result) }
                onComplete(result)
            }.onFailure { error ->
                _state.update { it.copy(planAdaptationBusy = false, transientMessage = friendlyError(error)) }
            }
        }
    }

    fun applyPlanAdaptation(result: WorkoutAdaptationResultData) {
        val adapted = result.adaptedExercises
        if (adapted.isNotEmpty()) {
            _state.update { current ->
                current.copy(
                    dashboard = current.dashboard?.copy(workouts = adapted),
                    planAdaptationResult = null,
                    transientMessage = result.explanationTr.ifBlank { "Antrenman başarıyla uyarlandı." },
                )
            }
        } else {
            _state.update { it.copy(planAdaptationResult = null) }
        }
    }

    fun dismissPlanAdaptation() {
        _state.update { it.copy(planAdaptationResult = null) }
    }

    fun executeCoachAction(action: CoachActionData, onActionHandled: (String) -> Unit = {}) {
        when (action.type) {
            "replace_exercise" -> {
                val origId = action.exerciseId.orEmpty().trim()
                val repId = action.replacementId.orEmpty().trim()
                val repName = (action.replacementName ?: repId).trim()
                if (repId.isNotBlank()) {
                    mutateWorkoutPlan("Hareket '$repName' ile güncellendi.") { current ->
                        var replaced = false
                        val updated = current.map { item ->
                            val matches = !replaced && (
                                (origId.isNotBlank() && (item.id.equals(origId, ignoreCase = true) || item.name.contains(origId, ignoreCase = true))) ||
                                (origId.isBlank() && action.reason != null && item.name.contains(action.reason, ignoreCase = true))
                            )
                            if (matches) {
                                replaced = true
                                item.copy(
                                    id = repId,
                                    name = repName,
                                    sets = action.sets ?: item.sets,
                                    reps = action.reps ?: item.reps,
                                    restSeconds = action.restSeconds ?: item.restSeconds,
                                )
                            } else item
                        }
                        if (!replaced && updated.isNotEmpty()) {
                            updated.toMutableList().apply {
                                val first = this[0]
                                this[0] = first.copy(
                                    id = repId,
                                    name = repName,
                                    sets = action.sets ?: first.sets,
                                    reps = action.reps ?: first.reps,
                                    restSeconds = action.restSeconds ?: first.restSeconds,
                                )
                            }
                        } else updated
                    }
                    onActionHandled("Hareket değiştirildi: $repName")
                }
            }
            "reduce_intensity" -> {
                val percent = action.percent ?: 25
                val current = _state.value.dashboard?.workouts.orEmpty()
                val next = current.map { item ->
                    item.copy(
                        sets = (item.sets * (100 - percent) / 100).coerceAtLeast(1),
                        restSeconds = item.restSeconds + 20,
                    )
                }
                _state.update { state ->
                    state.copy(
                        dashboard = state.dashboard?.copy(workouts = next),
                        transientMessage = "Yoğunluk %$percent düşürüldü ve dinlenme uzatıldı.",
                    )
                }
                onActionHandled("Yoğunluk düşürüldü")
            }
            "shorten_workout" -> {
                val targetMinutes = action.targetMinutes ?: 20
                requestPlanAdaptation("time_shortage", targetMinutes) { res ->
                    applyPlanAdaptation(res)
                    onActionHandled("Antrenman $targetMinutes dakikaya uyarlandı")
                }
            }
            "start_recovery_check" -> {
                _state.update { it.copy(transientMessage = "Hazırlık ve toparlanma kontrolü için Antrenman sekmesini aç.") }
                onActionHandled("start_recovery_check")
            }
            "modify_sets" -> {
                val exId = action.exerciseId ?: ""
                val sets = action.sets ?: 3
                val current = _state.value.dashboard?.workouts.orEmpty()
                val next = current.map { if (it.id == exId) it.copy(sets = sets) else it }
                _state.update { it.copy(dashboard = it.dashboard?.copy(workouts = next), transientMessage = "Set sayısı $sets olarak güncellendi.") }
                onActionHandled("Set sayısı güncellendi")
            }
            "modify_reps" -> {
                val exId = action.exerciseId ?: ""
                val reps = action.reps ?: "10"
                val current = _state.value.dashboard?.workouts.orEmpty()
                val next = current.map { if (it.id == exId) it.copy(reps = reps) else it }
                _state.update { it.copy(dashboard = it.dashboard?.copy(workouts = next), transientMessage = "Tekrar sayısı $reps olarak güncellendi.") }
                onActionHandled("Tekrar sayısı güncellendi")
            }
            "modify_rest_time" -> {
                val exId = action.exerciseId ?: ""
                val rest = action.restSeconds ?: 60
                val current = _state.value.dashboard?.workouts.orEmpty()
                val next = current.map { if (it.id == exId) it.copy(restSeconds = rest) else it }
                _state.update { it.copy(dashboard = it.dashboard?.copy(workouts = next), transientMessage = "Dinlenme süresi ${rest}s olarak güncellendi.") }
                onActionHandled("Dinlenme süresi güncellendi")
            }
            // openWorkout/createWorkout/startOutdoor/suggestMeal/remind/changeGoal
            // ekran GEÇİŞİ gerektirir; bu ViewModel'in erişemediği navigasyon
            // durumu (MainActivity'deki `selected`/`utilityPage`) MainActivity'de
            // ele alınır (bkz. MainActivity onExecuteAction sarmalayıcısı).
            // Buraya düşerlerse (ör. eski istemci sürümü) en azından bir
            // geri bildirim göster; sessizce hiçbir şey olmasın.
            else -> {
                _state.update { it.copy(transientMessage = "Bu öneriyi uygulamak için ilgili sekmeyi aç.") }
                onActionHandled(action.type)
            }
        }
    }

    /** Ekrana taşınan koç eylemleri (openWorkout, createWorkout, ...) için kısa geri bildirim. */
    fun showTransientMessage(text: String) {
        _state.update { it.copy(transientMessage = text) }
    }

    fun clearChat() {
        if (_state.value.chatBusy) return
        _state.update { it.copy(chatMessages = emptyList(), transientMessage = "Sohbet temizlendi.") }
    }

    fun saveProfile(update: ProfileUpdateData, regeneratePlan: Boolean = false, onComplete: () -> Unit = {}) {
        if (_state.value.profileSaving) return
        viewModelScope.launch {
            _state.update { it.copy(profileSaving = true) }
            val profile = runCatching { repository.saveProfile(update) }.getOrElse { error ->
                _state.update { it.copy(profileSaving = false, transientMessage = friendlyError(error)) }
                return@launch
            }
            _state.update { current -> current.copy(dashboard = current.dashboard?.copy(profile = profile)) }
            if (!regeneratePlan) {
                _state.update { it.copy(profileSaving = false, transientMessage = "Profilin güncellendi.") }
                onComplete()
                return@launch
            }
            val existingAssessment = _state.value.dashboard?.workoutPrograms?.firstOrNull { it.source == "assessment" }
            runCatching {
                val workouts = repository.generatePlan(profile)
                repository.saveProgram("Kişisel Atlas Programım", "assessment", profile.goal, workouts, existingAssessment?.id ?: java.util.UUID.randomUUID().toString(), showOnHome = true)
            }.onSuccess { program ->
                _state.update { current ->
                    current.copy(
                        profileSaving = false,
                        dashboard = current.dashboard?.let { data -> data.copy(profile = profile, workouts = program.exercises, workoutPrograms = withActiveProgram(data.workoutPrograms, program)) },
                        transientMessage = "OpenAI 15 yanıtını değerlendirdi; Hareket Atlası programın ana ekranda hazır.",
                    )
                }
                onComplete()
            }.onFailure { error ->
                _state.update { it.copy(profileSaving = false, transientMessage = "Profilin kaydedildi. Program şu anda yenilenemedi: ${friendlyError(error)}") }
                onComplete()
            }
        }
    }

    fun saveBodyMeasurement(measurement: BodyMeasurementData) {
        if (_state.value.measurementSaving) return
        viewModelScope.launch {
            _state.update { it.copy(measurementSaving = true, transientMessage = null) }
            runCatching { repository.saveBodyMeasurement(measurement) }
                .onSuccess { saved ->
                    _state.update { current ->
                        val existing = current.dashboard?.measurements.orEmpty().filterNot { it.date.take(10) == saved.date.take(10) }
                        current.copy(
                            measurementSaving = false,
                            dashboard = current.dashboard?.copy(measurements = (existing + saved).sortedBy { it.date }),
                            transientMessage = "Vücut ölçülerin kaydedildi.",
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(measurementSaving = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun resetProgress() {
        if (_state.value.accountBusy) return
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true) }
            runCatching { repository.resetProgress() }
                .onSuccess {
                    _state.update { it.copy(accountBusy = false, transientMessage = "İlerleme verilerin sıfırlandı.") }
                    refreshAll()
                }
                .onFailure { error -> _state.update { it.copy(accountBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun freezeAccount() {
        if (_state.value.accountBusy) return
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true) }
            runCatching { repository.freezeAccount() }
                .onSuccess { _state.update { it.copy(accountBusy = false, accountFrozen = true, dashboard = null) } }
                .onFailure { error -> _state.update { it.copy(accountBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun reactivateAccount() {
        if (_state.value.accountBusy) return
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true) }
            runCatching { repository.reactivateAccount() }
                .onSuccess {
                    _state.update { it.copy(accountBusy = false, accountFrozen = false, transientMessage = "Hesabın yeniden etkinleştirildi.") }
                    refreshAll()
                }
                .onFailure { error -> _state.update { it.copy(accountBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun deleteAccount(email: String) {
        if (_state.value.accountBusy) return
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true) }
            runCatching { repository.deleteAccount(email) }
                .onSuccess {
                    authRepository.signOut()
                    _state.value = MainUiState(auth = AuthState.SignedOut, transientMessage = "Hesabın kalıcı olarak silindi.")
                }
                .onFailure { error -> _state.update { it.copy(accountBusy = false, transientMessage = friendlyError(error)) } }
        }
    }

    fun consumeTransientMessage() {
        _state.update { it.copy(transientMessage = null) }
    }

    private fun authAction(block: suspend () -> Unit) {
        if (_state.value.authBusy) return
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true, authMessage = null) }
            runCatching { block() }
                .onFailure { error -> _state.update { it.copy(authBusy = false, authMessage = friendlyError(error)) } }
        }
    }

    private suspend fun onSignedIn(session: AuthSession) {
        val cachedAvatar = loadCachedAvatar(session.user.id)
        _state.update { it.copy(auth = AuthState.SignedIn(session), authBusy = false, authMessage = null, avatarPreview = cachedAvatar) }
        checkAccountThenLoad()
    }

    private fun avatarCacheFile(userId: String): File {
        val safeId = userId.replace(Regex("[^a-zA-Z0-9_-]"), "")
        return File(getApplication<Application>().filesDir, "profile-avatar-$safeId.jpg")
    }

    private suspend fun cacheAvatar(userId: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        avatarCacheFile(userId).writeBytes(bytes)
    }

    private suspend fun loadCachedAvatar(userId: String): ByteArray? = withContext(Dispatchers.IO) {
        avatarCacheFile(userId).takeIf { it.isFile && it.length() in 1..(5L * 1024 * 1024) }?.readBytes()
    }

    private suspend fun downloadAvatar(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            connection.getInputStream().use { input ->
                input.readAtMost(5 * 1024 * 1024)
            }
        }.getOrNull()
    }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun checkAccountThenLoad() {
        viewModelScope.launch {
            runCatching { repository.accountStatus() }
                .onSuccess { status ->
                    if (status == "frozen") _state.update { it.copy(accountFrozen = true, dataLoading = false) }
                    else refreshAll()
                }
                .onFailure { refreshAll() }
        }
    }

    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Invalid login credentials", true) -> "E-posta veya şifre hatalı."
            message.contains("Email not confirmed", true) -> "E-posta adresini doğrulaman gerekiyor."
            message.contains("Email address", true) && message.contains("invalid", true) -> "Bu e-posta adresi kabul edilmedi. Başka bir e-posta adresi dene."
            message.contains("already registered", true) || message.contains("already exists", true) -> "Bu e-posta adresiyle zaten bir hesap var. Giriş yapmayı dene."
            message.contains("rate limit", true) || message.contains("too many", true) -> "Çok fazla deneme yapıldı. Biraz bekleyip yeniden dene."
            message.contains("Database error saving new user", true) || message.contains("profiles_username", true) -> "Bu kullanıcı adı alınmış ya da kullanılamaz. Başka bir kullanıcı adı dene."
            message.contains("network", true) || message.contains("Unable to resolve host", true) -> "İnternet bağlantısı kurulamadı."
            message.isNotBlank() -> message.take(220)
            else -> "Beklenmeyen bir hata oluştu."
        }
    }
}

/** A sentence listing several foods ("omlet, 3 dilim ekmek ve domates") rather than one food name. */
internal fun looksLikeWholeMeal(text: String): Boolean =
    Regex("[,;+\\n]|\\s(ve|ile)\\s|\\s\\d", RegexOption.IGNORE_CASE).containsMatchIn(text.trim())

internal const val FULL_BODY = "full_body"
private val FULL_BODY_REGIONS = listOf("legs", "chest", "back", "shoulders", "glutes", "abdominals")
private val LEVEL_ORDER = listOf("beginner", "intermediate", "advanced")
private val FOUNDATION_LIFT = Regex("(^|-)(squat|leg-press|deadlift|romanian|lunge|split-squat|hip-thrust|bench-press|push-up|pull-up|chin-up|lat-pulldown|row|shoulder-press|ohp|overhead-press|dips?)(-|$)")

private fun regionLabel(key: String, en: Boolean) = when (key) {
    "legs" -> if (en) "Legs" else "Bacak"
    "chest" -> if (en) "Chest" else "Göğüs"
    "back" -> if (en) "Back" else "Sırt"
    "shoulders" -> if (en) "Shoulders" else "Omuz"
    "glutes" -> if (en) "Glutes" else "Kalça"
    else -> if (en) "Core" else "Karın"
}

/** Groups variations of one movement ("push-up", "knee-push-ups", "wide-grip-push-ups") so a session isn't three push-ups. */
private fun movementFamily(id: String): String {
    val key = id.lowercase().removeSuffix("s")
    return listOf(
        "push-up", "pull-up", "chin-up", "squat", "lunge", "row", "deadlift", "rdl",
        "bench-press", "floor-press", "leg-press", "shoulder-press", "overhead-press", "push-press", "press",
        "curl", "lateral-raise", "front-raise", "calf-raise", "raise", "fly", "dip", "plank", "crunch", "bridge", "thrust", "extension", "pulldown", "kickback",
    )
        .firstOrNull { key.contains(it) } ?: key
}


/** Hazır program adları: kodlu "A/B" yerine içeriği anlatan adlar. */
fun readyProgramTitle(key: String, en: Boolean): String = when (key) {
    "push_a" -> if (en) "Chest & Triceps Power Day" else "Göğüs & Arka Kol Güç Günü"
    "push_b" -> if (en) "Shoulder & Chest Sculpt" else "Omuz & Göğüs Şekillendirme"
    "pull_a" -> if (en) "Wide Back & Biceps" else "Geniş Sırt & Ön Kol"
    "pull_b" -> if (en) "Thick Back & Rear Delts" else "Kalın Sırt & Arka Omuz"
    "leg_a" -> if (en) "Quad Power Day" else "Ön Bacak Güç Günü"
    "leg_b" -> if (en) "Glutes & Hamstrings" else "Kalça & Arka Bacak"
    "full_a" -> if (en) "Full Body Strength Basics" else "Tüm Vücut Temel Kuvvet"
    "full_b" -> if (en) "Full Body Deadlift Day" else "Tüm Vücut Deadlift Günü"
    else -> key
}
