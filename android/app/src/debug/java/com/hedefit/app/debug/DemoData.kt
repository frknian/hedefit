package com.hedefit.app.debug

import com.hedefit.app.data.model.BodyMeasurementData
import com.hedefit.app.data.model.ChallengeDayLog
import com.hedefit.app.data.model.ChallengeHubData
import com.hedefit.app.data.model.ChallengePlanData
import com.hedefit.app.data.model.ChallengeRulesData
import com.hedefit.app.data.model.ChallengeTaskData
import com.hedefit.app.data.model.ChallengeTemplateData
import com.hedefit.app.data.model.CheckinData
import com.hedefit.app.data.model.DailyStepData
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.data.model.FriendRequestData
import com.hedefit.app.data.model.FriendUserData
import com.hedefit.app.data.model.FriendsSummaryData
import com.hedefit.app.data.model.L10nText
import com.hedefit.app.data.model.LeaderboardEntryData
import com.hedefit.app.data.model.NutritionGoalData
import com.hedefit.app.data.model.NutritionLogData
import com.hedefit.app.data.model.ProfileData
import com.hedefit.app.data.model.UserChallengeData
import com.hedefit.app.data.model.WorkoutExerciseData
import com.hedefit.app.data.model.WorkoutProgramData
import com.hedefit.app.data.model.WorkoutSessionData
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * YALNIZ DEBUG. Tanıtım ve QA görüntüleri için gerçekçi ama KURGUSAL demo verisi (gerçek kullanıcı verisi yok).
 * Katalog şablonları sunucudaki lib/challenges/catalog.ts ile aynı metinleri kullanır; katılımcı sayısı
 * bilerek 0'dır (sahte sosyal kanıt gösterilmez).
 */
object DemoData {
    private val today: LocalDate get() = LocalDate.now()
    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toString()

    fun profile(en: Boolean) = ProfileData(
        id = "demo", displayName = if (en) "Alex" else "Deniz", weightKg = 74.0, heightCm = 172.0,
        goal = "Yağ yakmak | hedef:68.0 | hafta:16", isPremium = true, age = 29, gender = if (en) "" else "Kadın",
        environment = "Salon", equipment = "Tam salon", historyAnswers = List(22) { "" }, targetWeightKg = 68.0, targetWeeks = 16, username = "deniz.fit", planTier = "pro",
    )

    private val workouts = listOf(
        WorkoutExerciseData("bench-press", "Barbell Bench Press", "Göğüs", 4, "8–10", 90, 50.0),
        WorkoutExerciseData("lat-pulldown", "Lat Pulldown", "Sırt", 4, "10–12", 75, 45.0),
        WorkoutExerciseData("dumbbell-shoulder-press", "Dumbbell Shoulder Press", "Omuz", 3, "10–12", 75, 14.0),
        WorkoutExerciseData("seated-cable-row", "Seated Cable Row", "Sırt", 3, "10–12", 75, 40.0),
        WorkoutExerciseData("bicep-curl", "Dumbbell Bicep Curl", "Kol", 3, "12", 60, 10.0),
        WorkoutExerciseData("plank", "Plank", "Karın", 3, "45 sn", 45),
    )

    fun dashboard(en: Boolean): DashboardData {
        val sessions = (1..26).map { index ->
            val day = today.minusDays((index * 1.6).toLong())
            WorkoutSessionData("s$index", at(day, 18, 30), 52 * 60, 410, 6, 6, 2)
        }
        return DashboardData(
            profile = profile(en),
            workouts = workouts,
            sessions = sessions,
            nutritionLogs = listOf(
                NutritionLogData("n1", at(today, 8, 15), "Kahvaltı", if (en) "Oatmeal with berries" else "Meyveli yulaf", 420, 18.0, 62.0, 11.0, 250.0, fiber = 9.0, sodiumMg = 140.0),
                NutritionLogData("n2", at(today, 13, 0), "Öğle yemeği", if (en) "Grilled chicken & rice" else "Izgara tavuk ve pilav", 640, 48.0, 70.0, 16.0, 380.0, fiber = 4.0, sodiumMg = 720.0),
                NutritionLogData("n3", at(today, 16, 30), "Atıştırmalık", if (en) "Greek yogurt" else "Süzme yoğurt", 180, 17.0, 9.0, 8.0, 200.0, fiber = 0.0, sodiumMg = 90.0),
            ),
            nutritionGoal = NutritionGoalData(2050, 130, 230, 62),
            steps = 7_840,
            waterMl = 1_750,
            sleepMinutes = 445,
            streakDays = 9,
            measurements = (0..10).map { week -> BodyMeasurementData(today.minusWeeks((10 - week).toLong()).toString(), 78.2 - week * 0.42, 86.0 - week * 0.35, 101.0 - week * 0.2, 98.0, 33.0, 57.0) },
            loadedDate = today,
            activeCalories = 486,
            workoutPrograms = listOf(WorkoutProgramData("p1", if (en) "Upper / Lower Strength" else "Üst / Alt Güç Programı", "ai", "Tüm Vücut", workouts, isActive = true, showOnHome = true)),
            stepHistory = (0..29).map { DailyStepData(today.minusDays(it.toLong()), 6_500 + (it * 1_337) % 5_200) },
            gamificationTotalXp = 5_160,
            gamificationWeeklyXp = 420,
            unlockedAchievements = mapOf(
                "first_activity" to today.minusDays(60), "workouts_3" to today.minusDays(55), "workouts_10" to today.minusDays(40), "workouts_25" to today.minusDays(6),
                "streak_3" to today.minusDays(20), "streak_7" to today.minusDays(12), "first_challenge" to today.minusDays(18), "steps_100k" to today.minusDays(25),
            ),
        )
    }

    fun checkin() = CheckinData(today.toString(), 7, 8, 7.5, 2, 0, 30)

    private fun plan(key: String, category: String, difficulty: String, equipment: String, tr: String, en: String, descTr: String, descEn: String, days: List<ChallengeTaskData>, source: String = "catalog") =
        ChallengePlanData(key, source, category, difficulty, equipment, L10nText(tr, en), L10nText(descTr, descEn), days)

    private fun ramp(days: Int, from: Int, to: Int, session: String) = List(days) { index -> ChallengeTaskData("session", session, from + (to - from) * index / (days - 1)) }

    private val core14 = plan("core_14", "workout", "beginner", "none", "14 Gün Core Challenge", "14-Day Core Challenge",
        "Her gün kısa, kontrollü core seansları. Duruşun ve gövde gücün iki haftada fark edilir.", "Short, controlled core sessions every day. Feel your posture and trunk strength change in two weeks.", ramp(14, 8, 15, "core_focus"))
    private val pilates21 = plan("pilates_21", "pilates", "intermediate", "mat", "21 Gün Pilates", "21-Day Pilates",
        "Üç haftalık düzenli Pilates: daha güçlü core, daha esnek kalça ve daha dik duruş.", "Three weeks of regular Pilates: a stronger core, looser hips and a taller posture.", ramp(21, 12, 22, "pilates_today"))
    private val steps7 = plan("steps_7", "steps", "beginner", "none", "7 Gün Adım Challenge", "7-Day Step Challenge",
        "Bir hafta boyunca her gün 8.000 adım. Arkadaşınla yarışmak için harika.", "8,000 steps every day for a week. Great to race a friend.", List(7) { ChallengeTaskData("steps", target = 8_000) })
    private val home21 = plan("home_21", "workout", "intermediate", "none", "21 Gün Evde Güç", "21-Day Home Strength",
        "Ekipmansız, kademeli artan tüm vücut antrenmanları. Her dördüncü gün aktif toparlanma.", "Progressive full-body workouts with no equipment. Active recovery every fourth day.", ramp(21, 15, 25, "home_strength"))
    private val flex10 = plan("flex_10", "flexibility", "beginner", "none", "10 Gün Esneklik", "10-Day Flexibility",
        "Günde birkaç dakika kontrollü esneme. Masa başı tutukluğuna iyi gelir.", "A few minutes of controlled stretching a day. Great for desk-bound stiffness.", ramp(10, 8, 12, "flexibility"))
    private val water7 = plan("water_7", "nutrition", "beginner", "none", "7 Gün Su Challenge", "7-Day Hydration Challenge",
        "Her gün en az 2 litre su. Küçük alışkanlık, büyük fark.", "At least 2 litres of water every day. Small habit, big difference.", List(7) { ChallengeTaskData("water", target = 2_000) })
    private val coachBand = plan("coach:core:band:beginner:14:15", "coach", "beginner", "band", "14 Gün Band & Core", "14-Day Band & Core",
        "Fit Koç bu planı hedefin, seviyen ve ekipmanına göre hazırladı; günde 12–15 dakika. Her gün check-in'ine göre uyarlanır.",
        "Fit Coach built this plan around your goal, level and equipment; 12–15 minutes a day. It adapts to your daily check-in.",
        List(14) { index -> if (index % 4 == 3) ChallengeTaskData("session", "posture_mobility", 10) else ChallengeTaskData("session", if (index % 3 == 1) "core_focus" else "band_strength", 12 + minOf(3, index / 3)) }, source = "coach")

    private fun template(plan: ChallengePlanData, minutes: Pair<Int, Int>?, fit: String = "fit", recommended: Boolean = false, popular: Boolean = false) =
        ChallengeTemplateData(plan, minutes, rewardFor(plan.days.size), participants = 0, popular = popular, fit = fit, recommended = recommended)

    private fun rewardFor(days: Int) = days * 25 + (if (days >= 3) 30 else 0) + (days / 7) * 100 + 250

    fun hub(): ChallengeHubData {
        val dates = (5 downTo 1).map { today.minusDays(it.toLong()).toString() }
        val coreActive = UserChallengeData(
            "uc1", "core_14", core14, "active", null, today.minusDays(5).toString(), null,
            dates.mapIndexed { index, date -> ChallengeDayLog(index, date, if (index == 2) "adapted" else "completed", 9 + index) },
        )
        val stepsActive = UserChallengeData("uc2", "steps_7", steps7, "active", "social1", today.minusDays(3).toString(), null,
            (3 downTo 1).mapIndexed { index, offset -> ChallengeDayLog(index, today.minusDays(offset.toLong()).toString(), "completed", null) })
        val done = UserChallengeData("uc3", "flex_10", flex10, "completed", null, today.minusDays(30).toString(), at(today.minusDays(19), 20),
            (0 until 10).map { ChallengeDayLog(it, today.minusDays((29 - it).toLong()).toString(), if (it == 6) "recovery" else "completed", 10) })
        return ChallengeHubData(
            ChallengeRulesData(),
            listOf(
                template(core14, 8 to 15, recommended = true, popular = true),
                template(pilates21, 12 to 22, fit = "stretch", recommended = true, popular = true),
                template(steps7, null, recommended = true, popular = true),
                template(home21, 10 to 25, fit = "stretch", recommended = true, popular = true),
                template(flex10, 8 to 12),
                template(water7, null),
            ),
            listOf(coreActive, stepsActive, done),
        )
    }

    fun coachPlan() = coachBand

    fun friends(en: Boolean): FriendsSummaryData {
        fun user(id: String, name: String, username: String) = FriendUserData(id, username, name, null)
        val people = listOf(user("f1", "Mert", "mert.runs"), user("f2", if (en) "Sophie" else "Ece", "ece.pilates"), user("f3", "Can", "can.lifts"))
        return FriendsSummaryData(
            friends = people.map { FriendRequestData("fr-${it.id}", "accepted", today.minusDays(20).toString(), false, it) },
            incomingRequests = listOf(FriendRequestData("in1", "pending", today.toString(), true, user("f4", if (en) "Liam" else "Selin", "selin.moves"))),
            outgoingRequests = emptyList(),
        )
    }

    fun leaderboard(en: Boolean) = listOf(
        LeaderboardEntryData(1, 640, false, FriendUserData("f1", "mert.runs", "Mert", null)),
        LeaderboardEntryData(2, 420, true, FriendUserData("demo", "deniz.fit", if (en) "Alex" else "Deniz", null)),
        LeaderboardEntryData(3, 385, false, FriendUserData("f2", "ece.pilates", if (en) "Sophie" else "Ece", null)),
        LeaderboardEntryData(4, 210, false, FriendUserData("f3", "can.lifts", "Can", null)),
    )

    fun friendChallenges(en: Boolean) = listOf(
        com.hedefit.app.data.model.ChallengeData("social1", if (en) "7-Day Step Challenge" else "7 Gün Adım Challenge", "steps", 56_000.0, at(today.minusDays(3), 9), at(today.plusDays(4), 9), "demo", true, "joined", 2, "compete", "steps_7"),
        com.hedefit.app.data.model.ChallengeData("social2", if (en) "21-Day Pilates" else "21 Gün Pilates", "challenge_days", 21.0, at(today, 9), at(today.plusDays(21), 9), "f2", false, "invited", 1, "together", "pilates_21"),
    )

    fun stepRace(en: Boolean) = listOf(
        com.hedefit.app.data.model.ChallengeProgressEntryData(1, 42_840.0, false, FriendUserData("f1", "mert.runs", "Mert", null)),
        com.hedefit.app.data.model.ChallengeProgressEntryData(2, 39_210.0, true, FriendUserData("demo", "deniz.fit", if (en) "Alex" else "Deniz", null)),
    )
}
