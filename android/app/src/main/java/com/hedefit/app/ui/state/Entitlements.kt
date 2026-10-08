package com.hedefit.app.ui.state

import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.data.model.ExerciseCatalogData
import java.time.LocalDate

/**
 * Hesap katmanları: Misafir → (hesap kaydı) Free → (satın alma) Plus / Premium.
 * Sunucudaki plan_tier değerleri: free | plus | pro (pro = Premium).
 * Sunucu kotaları lib/usage-limits.ts içindedir; ikisini birlikte güncelle.
 */
enum class Tier(private val labelTr: String, private val labelEn: String) {
    Guest("Misafir", "Guest"), Free("Free", "Free"), Plus("Plus", "Plus"), Premium("Premium", "Premium");
    val label: String get() = com.hedefit.app.ui.i18n.tr(labelTr, labelEn)
}

/** Kısıtlanabilen her özellik; kilit ekranı metni buna göre seçilir. */
enum class LockedFeature(private val titleTr: String, private val bodyTr: String, val emoji: String, private val titleEn: String, private val bodyEn: String) {
    MealLogs("Bugünkü öğün hakkın doldu", "Günlük öğün kaydı sınırına ulaştın. Sınırsız takip için devam et.", "🍽️", "Daily meal limit reached", "You reached your daily meal logging limit. Continue for unlimited tracking."),
    PhotoMeal("Fotoğraftan kalori", "Fotoğraftan kalori hesaplama hakkın doldu.", "📸", "Photo calorie scan", "Your photo calorie scans for today are used up."),
    Exercise("Bu hareket kilitli", "Orta ve ileri seviye hareketler daha geniş erişimle açılır.", "🔒", "This exercise is locked", "Intermediate and advanced exercises unlock with wider access."),
    CustomProgram("Kendi programını oluştur", "Kendi program sınırına ulaştın.", "🗂️", "Create your own program", "You reached your custom program limit."),
    Route("Rotanı kaydet", "GPS rotalarını kaydedip geçmişini görmek için erişimini genişlet.", "🗺️", "Save your route", "Upgrade access to save GPS routes and see your history."),
    MealPlanner("Haftalık öğün planlayıcı", "Haftalık öğün planı oluşturmak için erişimini genişlet.", "📅", "Weekly meal planner", "Upgrade access to build a weekly meal plan."),
    RegionalPlan("Bölgesel program", "Bölgeye özel program üretmek için erişimini genişlet.", "🎯", "Regional program", "Upgrade access to generate body-part programs."),
    History("Geçmiş ilerleme", "Daha eski kayıtları görmek için erişimini genişlet.", "📈", "Past progress", "Upgrade access to see older records."),
    CardioPrograms("Hazır kardiyo programları", "12-3-30, aralıklı koşu ve tepe tırmanışı gibi programlar Plus ve Premium'da.", "🏃", "Ready cardio programs", "Programs like 12-3-30, interval runs and hill climbs are in Plus and Premium."),
    CardioGame("Kardiyo oyun modu", "Sanal rota, rekorunla yarış ve hedef görevleri Plus ve Premium'da.", "🎮", "Cardio game mode", "Virtual route, racing your record and target quests are in Plus and Premium."),
    WellnessContent("Pilates ve mobilite içeriği", "Başlangıç dışındaki Pilates, mobilite, düşük etkili ve toparlanma içerikleri Plus ve Premium'da.", "🧘", "Pilates & mobility content", "Pilates, mobility, low-impact and recovery content beyond the starter set is in Plus and Premium."),
    Barre("Barre", "Barre antrenmanları Plus ve Premium'da.", "🩰", "Barre", "Barre workouts are in Plus and Premium."),
    AdaptiveAction("Gelişmiş adaptasyon", "Hareket değiştirme, mobilite ekleme ve toparlanmaya geçme Plus ve Premium'da.", "🔄", "Advanced adaptation", "Swapping exercises, adding mobility and switching to recovery are in Plus and Premium."),
    PilatesSwitch("Pilates oturumuna geç", "Bugünkü planı Pilates oturumuna çevirme Premium'da.", "✨", "Switch to a Pilates session", "Turning today's plan into a Pilates session is in Premium."),
    CycleAdaptation("Döngüye göre uyarlama", "Döngü bilgini antrenman uyarlamasına katmak Plus ve Premium'da. Döngü takibi ücretsizdir.", "🌙", "Cycle-aware adaptation", "Using your cycle information in workout adaptation is in Plus and Premium. Cycle tracking is free."),
    AiAdaptiveCoach("Koçtan kişisel açıklama", "Uyarlamanı koçun sana özel açıklaması Premium'da.", "🤖", "Personal coach explanation", "Your coach's personalised explanation of the adaptation is in Premium."),
    ;
    val title: String get() = com.hedefit.app.ui.i18n.tr(titleTr, titleEn)
    val body: String get() = com.hedefit.app.ui.i18n.tr(bodyTr, bodyEn)
}

data class TierLimits(
    /** Günlük öğün kaydı (her besin satırı bir kayıt). */
    val dailyMealLogs: Int,
    /** Günlük fotoğraftan kalori analizi (sunucu kotasıyla aynı tutulur). */
    val dailyPhotoMeals: Int,
    /** Açık egzersiz seviyeleri (levelKey). */
    val exerciseLevels: Set<String>,
    /** Kullanıcının kendi oluşturduğu/kopyaladığı program sayısı. */
    val customPrograms: Int,
    val routeSaving: Boolean,
    val mealPlanner: Boolean,
    val regionalPlans: Boolean,
    /** İlerleme ve beslenme geçmişinde görülebilen gün sayısı. */
    val historyDays: Int,
    /** FitKoç günlük soru hakkı (sunucu kotasıyla aynı). */
    val dailyCoachQuestions: Int,
    /** Reklamlar: alt banner, seyrek sekme geçişi reklamı, izleyerek soru hakkı. */
    val bannerAds: Boolean,
    val interstitialAds: Boolean,
    val rewardedAds: Boolean,
    /** Kardiyo: hazır programlar ve oyun modu (temel kardiyo herkese açık). */
    val cardioPrograms: Boolean,
    val cardioGame: Boolean,
    /** Adaptive Fitness (sunucudaki lib/entitlements.ts ile aynı matris). Döngü TAKİBİ her katmanda ücretsizdir. */
    val wellnessStarterOnly: Boolean,
    val barre: Boolean,
    val checkinHistoryDays: Int,
    val checkinTrends: Boolean,
    val adaptiveActions: Set<String>,
    val cycleAdaptation: Boolean,
    val aiAdaptiveCoach: Boolean,
    val coachCycleAware: Boolean,
    /** basic | training_load | full */
    val nutritionPersonalization: String,
)

private const val UNLIMITED = Int.MAX_VALUE

/** Tüm sınırlar tek tabloda: değiştirmek için yalnız burayı düzenle. */
val TIER_LIMITS: Map<Tier, TierLimits> = mapOf(
    Tier.Guest to TierLimits(
        dailyMealLogs = 5,
        dailyPhotoMeals = 1,
        exerciseLevels = setOf("beginner"),
        customPrograms = 0,
        routeSaving = false,
        mealPlanner = false,
        regionalPlans = false,
        historyDays = 7,
        dailyCoachQuestions = 3,
        bannerAds = true,
        interstitialAds = true,
        cardioPrograms = false,
        cardioGame = false,
        rewardedAds = true,
        wellnessStarterOnly = true, barre = false, checkinHistoryDays = 7, checkinTrends = false,
        adaptiveActions = setOf("shorten", "reduce_intensity"), cycleAdaptation = false, aiAdaptiveCoach = false, coachCycleAware = false, nutritionPersonalization = "basic",
    ),
    Tier.Free to TierLimits(
        dailyMealLogs = 15,
        dailyPhotoMeals = 1,
        exerciseLevels = setOf("beginner", "intermediate"),
        customPrograms = 2,
        routeSaving = true,
        mealPlanner = false,
        regionalPlans = false,
        historyDays = 30,
        dailyCoachQuestions = 5,
        bannerAds = true,
        interstitialAds = true,
        cardioPrograms = false,
        cardioGame = false,
        rewardedAds = true,
        wellnessStarterOnly = true, barre = false, checkinHistoryDays = 14, checkinTrends = false,
        adaptiveActions = setOf("shorten", "reduce_intensity"), cycleAdaptation = false, aiAdaptiveCoach = false, coachCycleAware = false, nutritionPersonalization = "basic",
    ),
    Tier.Plus to TierLimits(
        dailyMealLogs = UNLIMITED,
        dailyPhotoMeals = 3,
        exerciseLevels = setOf("beginner", "intermediate", "advanced"),
        customPrograms = 5,
        routeSaving = true,
        mealPlanner = true,
        regionalPlans = false,
        historyDays = 90,
        dailyCoachQuestions = 20,
        bannerAds = false,
        interstitialAds = false,
        cardioPrograms = true,
        cardioGame = true,
        rewardedAds = false,
        wellnessStarterOnly = false, barre = true, checkinHistoryDays = 90, checkinTrends = false,
        adaptiveActions = setOf("shorten", "reduce_intensity", "replace_exercises", "add_mobility", "switch_recovery"), cycleAdaptation = true, aiAdaptiveCoach = false, coachCycleAware = false, nutritionPersonalization = "training_load",
    ),
    Tier.Premium to TierLimits(
        dailyMealLogs = UNLIMITED,
        dailyPhotoMeals = 8,
        exerciseLevels = setOf("beginner", "intermediate", "advanced"),
        customPrograms = UNLIMITED,
        routeSaving = true,
        mealPlanner = true,
        regionalPlans = true,
        historyDays = UNLIMITED,
        dailyCoachQuestions = 40,
        bannerAds = false,
        interstitialAds = false,
        cardioPrograms = true,
        cardioGame = true,
        rewardedAds = false,
        wellnessStarterOnly = false, barre = true, checkinHistoryDays = UNLIMITED, checkinTrends = true,
        adaptiveActions = setOf("shorten", "reduce_intensity", "replace_exercises", "add_mobility", "switch_recovery", "switch_pilates"), cycleAdaptation = true, aiAdaptiveCoach = true, coachCycleAware = true, nutritionPersonalization = "full",
    ),
)

fun MainUiState.tier(): Tier = when {
    isGuest -> Tier.Guest
    dashboard?.profile?.planTier == "pro" || (dashboard?.profile?.isPremium == true && dashboard.profile.planTier != "plus") -> Tier.Premium
    dashboard?.profile?.planTier == "plus" -> Tier.Plus
    else -> Tier.Free
}

fun MainUiState.limits(): TierLimits = TIER_LIMITS.getValue(tier())

fun DashboardData.todayMealLogCount(today: LocalDate = LocalDate.now()): Int =
    nutritionLogs.count { it.date.startsWith(today.toString()) }

/** Seviyesi bilinmeyen hareketler kilitlenmez; programdaki hareketler her zaman açıktır. */
fun TierLimits.canUseExercise(item: ExerciseCatalogData): Boolean =
    item.levelKey.isBlank() || item.levelKey.lowercase() in exerciseLevels

/** Adaptasyon eylemi bu katmanda açık mı? (eylem adları sunucudaki AdaptiveAction ile aynıdır.) */
fun TierLimits.canUseAdaptiveAction(action: String): Boolean = action in adaptiveActions

private val STARTER_SUBCATEGORIES = setOf("beginner", "short", "recovery", "morning", "evening")

/**
 * Modalite (Pilates, mobilite, barre, düşük etkili, toparlanma) hareketi bu katmanda açık mı? Modalitesiz klasik
 * hareketler bu kurala tabi değildir; mevcut seviye kuralı (`canUseExercise`) aynen geçerlidir.
 */
fun TierLimits.canUseModalityExercise(modalities: List<String>, subcategories: List<String>): Boolean {
    if (modalities.isEmpty()) return true
    val onlyBarre = modalities.all { it == "barre" }
    if (!wellnessStarterOnly) return barre || !onlyBarre
    if (onlyBarre) return false
    return subcategories.any { it in STARTER_SUBCATEGORIES }
}
