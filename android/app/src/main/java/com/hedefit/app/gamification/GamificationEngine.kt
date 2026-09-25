package com.hedefit.app.gamification

import com.hedefit.app.data.model.DailyStepData
import com.hedefit.app.data.model.NutritionGoalData
import com.hedefit.app.data.model.NutritionLogData
import com.hedefit.app.data.model.RouteActivityData
import com.hedefit.app.data.model.WorkoutScheduleData
import com.hedefit.app.data.model.WorkoutSessionData
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.roundToInt

const val ACHIEVEMENT_XP = 100

enum class XpSource { WORKOUT_COMPLETED, STEP_GOAL_COMPLETED, ROUTE_DISTANCE, NUTRITION_TARGET_COMPLETED, SLEEP_GOAL_COMPLETED, WEEKLY_GOAL_COMPLETED, WEEKLY_CHALLENGE_COMPLETED, ACHIEVEMENT_UNLOCKED }
data class XpEvent(val source: XpSource, val sourceId: String, val amount: Int, val occurredOn: LocalDate)
data class LevelProgress(val level: Int, val currentXp: Int, val nextLevelXp: Int, val progress: Float)
data class DailyQuest(val id: String, val title: String, val xp: Int, val completed: Boolean)
enum class AchievementRarity { COMMON, RARE, EPIC }
data class Achievement(val id: String, val title: String, val description: String, val icon: String, val progress: Double, val target: Double, val unlockedAt: LocalDate?, val rarity: AchievementRarity, val rewardXp: Int = ACHIEVEMENT_XP)
data class HabitDay(val date: LocalDate, val active: Boolean, val today: Boolean)
data class WeeklyChallenge(val type: String, val title: String, val progress: Double, val target: Double, val rewardXp: Int, val completed: Boolean)
data class AiReward(val extraQuestions: Int, val nextThreshold: Int?)
data class GamificationSnapshot(val streakDays: Int, val totalXp: Int, val level: LevelProgress, val habitStrength: Int, val habitWeek: List<HabitDay>, val dailyQuests: List<DailyQuest>, val challenge: WeeklyChallenge, val achievements: List<Achievement>, val weeklyXp: Int, val dailyXpCap: Int, val aiReward: AiReward, val motivation: String)

data class GamificationInput(
    val sessions: List<WorkoutSessionData>, val routes: List<RouteActivityData>, val stepHistory: List<DailyStepData>, val todaySteps: Int,
    val todayWaterMl: Int = 0, val sleepMinutes: Int = 0, val nutritionLogs: List<NutritionLogData> = emptyList(), val nutritionGoal: NutritionGoalData = NutritionGoalData(),
    val schedule: List<WorkoutScheduleData>, val dailyStepGoal: Int, val dailyWaterGoalMl: Int = 2_000, val weeklyActivityGoal: Int,
    val authoritativeTotalXp: Int? = null, val authoritativeWeeklyXp: Int? = null, val unlockedAchievements: Map<String, LocalDate> = emptyMap(), val today: LocalDate = LocalDate.now(), val zoneId: ZoneId = ZoneId.systemDefault(),
)

/** Pure domain service: task rotation and rewards are deterministic for a given day. */
object GamificationEngine {
    const val DAILY_XP_CAP = 100
    private const val XP_PER_WORKOUT = 40
    private const val XP_STEP_GOAL = 40
    private const val XP_PER_ROUTE_KM = 10
    private const val XP_WEEKLY_GOAL = 100
    private const val XP_WEEKLY_CHALLENGE = 250
    private const val CHALLENGE_DISTANCE_KM = 15.0

    fun snapshot(input: GamificationInput): GamificationSnapshot {
        val sessionDates = input.sessions.mapNotNull { instantDate(it.completedAt, input.zoneId) }
        val routeDates = input.routes.mapNotNull { instantDate(it.startedAt, input.zoneId) }
        val activeDates = (sessionDates + routeDates).toSet()
        val quests = dailyQuests(input, sessionDates, routeDates)
        check(quests.sumOf(DailyQuest::xp) == DAILY_XP_CAP) { "Daily task rewards must total $DAILY_XP_CAP XP." }
        val achievements = achievements(input, sessionDates, routeDates, activeDates)
        val events = xpEvents(input, achievements).distinctBy { it.source to it.sourceId }
        val totalXp = input.authoritativeTotalXp ?: events.sumOf(XpEvent::amount)
        val weekStart = input.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val thisWeekXp = input.authoritativeWeeklyXp ?: events.filter { !it.occurredOn.isBefore(weekStart) && !it.occurredOn.isAfter(input.today) }.sumOf(XpEvent::amount)
        val distance = input.routes.filter { route -> instantDate(route.startedAt, input.zoneId)?.let { !it.isBefore(weekStart) && !it.isAfter(input.today) } == true }.sumOf { it.distanceMeters } / 1_000.0
        val streak = goalAwareStreak(input.today, activeDates, input.weeklyActivityGoal)
        return GamificationSnapshot(streak, totalXp, levelFor(totalXp), habitStrength(input, activeDates), (0L..6L).map { offset -> weekStart.plusDays(offset).let { HabitDay(it, it in activeDates, it == input.today) } }, quests, WeeklyChallenge("DISTANCE", com.hedefit.app.ui.i18n.tr("15 km Koş/Yürü", "Run/Walk 15 km"), distance, CHALLENGE_DISTANCE_KM, XP_WEEKLY_CHALLENGE, distance >= CHALLENGE_DISTANCE_KM), achievements, thisWeekXp, DAILY_XP_CAP, aiRewardFor(totalXp), motivation(quests, streak, achievements, input.today))
    }

    fun xpEvents(input: GamificationInput, achievements: List<Achievement> = emptyList()): List<XpEvent> = buildList {
        input.sessions.forEach { session -> instantDate(session.completedAt, input.zoneId)?.let { add(XpEvent(XpSource.WORKOUT_COMPLETED, session.id, XP_PER_WORKOUT, it)) } }
        input.routes.forEach { route ->
            val km = floor(route.distanceMeters.coerceAtLeast(0.0) / 1_000.0).toInt()
            if (km > 0) instantDate(route.startedAt, input.zoneId)?.let { add(XpEvent(XpSource.ROUTE_DISTANCE, route.id, km * XP_PER_ROUTE_KM, it)) }
        }
        input.stepHistory.filter { it.steps >= input.dailyStepGoal }.forEach { add(XpEvent(XpSource.STEP_GOAL_COMPLETED, it.localDate.toString(), XP_STEP_GOAL, it.localDate)) }
        val sessionDates = input.sessions.mapNotNull { instantDate(it.completedAt, input.zoneId) }
        sessionDates.groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.forEach { (week, dates) -> if (dates.distinct().size >= input.weeklyActivityGoal) add(XpEvent(XpSource.WEEKLY_GOAL_COMPLETED, week.toString(), XP_WEEKLY_GOAL, week.plusDays(6))) }
        input.routes.groupBy { instantDate(it.startedAt, input.zoneId)?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.forEach { (week, routes) -> if (week != null && routes.sumOf { it.distanceMeters } >= CHALLENGE_DISTANCE_KM * 1_000) add(XpEvent(XpSource.WEEKLY_CHALLENGE_COMPLETED, "distance:$week", XP_WEEKLY_CHALLENGE, week.plusDays(6))) }
        achievements.filter { it.id in input.unlockedAchievements }.forEach { achievement -> add(XpEvent(XpSource.ACHIEVEMENT_UNLOCKED, achievement.id, achievement.rewardXp, requireNotNull(achievement.unlockedAt))) }
    }

    fun aiRewardFor(totalXp: Int): AiReward {
        val xp = totalXp.coerceAtLeast(0)
        val questions = when { xp < 300 -> 0; xp < 500 -> 1; else -> 2 + ((xp - 500) / 250) }.coerceAtMost(5)
        val next = when { questions >= 5 -> null; xp < 300 -> 300; xp < 500 -> 500; else -> 500 + ((questions - 1) * 250) + 250 }
        return AiReward(questions, next)
    }

    fun levelFor(totalXp: Int): LevelProgress {
        var level = 1; var remaining = totalXp.coerceAtLeast(0); var required = xpForLevel(level)
        while (remaining >= required) { remaining -= required; level += 1; required = xpForLevel(level) }
        return LevelProgress(level, remaining, required, remaining.toFloat() / required)
    }

    fun goalAwareStreak(today: LocalDate, activeDates: Set<LocalDate>, weeklyGoal: Int): Int {
        if (activeDates.isEmpty()) return 0
        val goal = weeklyGoal.coerceIn(1, 7); var week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); var earliest = today; var first = true
        while (true) {
            val effectiveEnd = if (first) today else week.plusDays(6); val count = activeDates.count { !it.isBefore(week) && !it.isAfter(effectiveEnd) }; val remainingDays = if (first) 7 - today.dayOfWeek.value else 0
            if (count < goal && (!first || count + remainingDays < goal)) break
            if (count == 0 && first) break
            earliest = week; week = week.minusWeeks(1); first = false
        }
        return if (earliest == today && today !in activeDates) 0 else (today.toEpochDay() - earliest.toEpochDay() + 1).toInt().coerceAtLeast(1)
    }

    private fun dailyQuests(input: GamificationInput, sessionDates: List<LocalDate>, routeDates: List<LocalDate>): List<DailyQuest> {
        val workout = input.today in sessionDates; val route = input.today in routeDates; val sleep = input.sleepMinutes >= 420; val meals = input.nutritionLogs.size >= 3
        val protein = input.nutritionLogs.sumOf { it.protein } >= input.nutritionGoal.protein * .85; val calories = input.nutritionLogs.sumOf { it.calories } >= input.nutritionGoal.calories * .75; val steps = input.todaySteps >= input.dailyStepGoal
        return when (input.today.dayOfYear % 4) {
            0 -> listOf(DailyQuest("steps", com.hedefit.app.ui.i18n.tr("${formatInt(input.dailyStepGoal)} adıma ulaş", "Reach ${formatInt(input.dailyStepGoal)} steps"), 40, steps), DailyQuest("sleep", com.hedefit.app.ui.i18n.tr("En az 7 saat uyu", "Sleep at least 7 hours"), 25, sleep), DailyQuest("meals", com.hedefit.app.ui.i18n.tr("3 öğününü kaydet", "Log 3 meals"), 15, meals), DailyQuest("pushups", com.hedefit.app.ui.i18n.tr("5 şınavlık antrenmanını tamamla", "Finish your 5-push-up workout"), 20, workout))
            1 -> listOf(DailyQuest("steps", com.hedefit.app.ui.i18n.tr("${formatInt(input.dailyStepGoal)} adıma ulaş", "Reach ${formatInt(input.dailyStepGoal)} steps"), 35, steps), DailyQuest("protein", com.hedefit.app.ui.i18n.tr("Protein hedefinin %85'ine ulaş", "Hit 85% of your protein goal"), 25, protein), DailyQuest("workout", com.hedefit.app.ui.i18n.tr("Antrenmanını tamamla", "Complete your workout"), 40, workout))
            2 -> listOf(DailyQuest("steps", com.hedefit.app.ui.i18n.tr("${formatInt(input.dailyStepGoal)} adıma ulaş", "Reach ${formatInt(input.dailyStepGoal)} steps"), 35, steps), DailyQuest("nutrition", com.hedefit.app.ui.i18n.tr("Günlük enerji hedefinin %75'ini kaydet", "Log 75% of your daily energy goal"), 25, calories), DailyQuest("sleep", com.hedefit.app.ui.i18n.tr("En az 7 saat uyu", "Sleep at least 7 hours"), 30, sleep), DailyQuest("route", com.hedefit.app.ui.i18n.tr("15 dk yürüyüş/koşu yap", "Walk/run for 15 min"), 10, route))
            else -> listOf(DailyQuest("sleep", com.hedefit.app.ui.i18n.tr("En az 7 saat uyu", "Sleep at least 7 hours"), 30, sleep), DailyQuest("meals", com.hedefit.app.ui.i18n.tr("3 öğününü kaydet", "Log 3 meals"), 20, meals), DailyQuest("route", com.hedefit.app.ui.i18n.tr("15 dk yürüyüş/koşu yap", "Walk/run for 15 min"), 20, route), DailyQuest("workout", com.hedefit.app.ui.i18n.tr("Antrenmanını tamamla", "Complete your workout"), 30, workout))
        }
    }

    private fun habitStrength(input: GamificationInput, activeDates: Set<LocalDate>): Int {
        val start = input.today.minusDays(29); val recent = activeDates.count { !it.isBefore(start) && !it.isAfter(input.today) }; val expected = (input.weeklyActivityGoal.coerceIn(1, 7) * (30.0 / 7.0)).coerceAtLeast(1.0)
        return (recent / expected * 100).roundToInt().coerceIn(0, 100)
    }

    private fun achievements(input: GamificationInput, sessionDates: List<LocalDate>, routeDates: List<LocalDate>, activeDates: Set<LocalDate>): List<Achievement> {
        val workouts = input.sessions.size.toDouble(); val steps = input.stepHistory.sumOf { it.steps }.toDouble(); val km = input.routes.sumOf { it.distanceMeters } / 1_000.0
        val times = input.sessions.map { it.completedAt } + input.routes.map { it.startedAt }; val early = times.count { localHour(it, input.zoneId)?.let { hour -> hour < 8 } == true }.toDouble(); val night = times.count { localHour(it, input.zoneId)?.let { hour -> hour >= 20 } == true }.toDouble(); val streak = goalAwareStreak(input.today, activeDates, input.weeklyActivityGoal).toDouble()
        fun item(id: String, title: String, description: String, progress: Double, target: Double, rarity: AchievementRarity): Achievement { val unlockedAt = input.unlockedAchievements[id] ?: if (progress >= target) input.today else null; return Achievement(id, title, description, id, if (unlockedAt != null) target else progress.coerceAtMost(target), target, unlockedAt, rarity) }
        return listOf(
            item("first_activity", com.hedefit.app.ui.i18n.tr("İlk Adım", "First Step"), com.hedefit.app.ui.i18n.tr("İlk aktiviteni tamamla", "Complete your first activity"), workouts + input.routes.size, 1.0, AchievementRarity.COMMON), item("workouts_3", com.hedefit.app.ui.i18n.tr("Ritmi Bul", "Find the Rhythm"), com.hedefit.app.ui.i18n.tr("3 antrenman tamamla", "Complete 3 workouts"), workouts, 3.0, AchievementRarity.COMMON), item("workouts_10", com.hedefit.app.ui.i18n.tr("Kararlı", "Committed"), com.hedefit.app.ui.i18n.tr("10 antrenman tamamla", "Complete 10 workouts"), workouts, 10.0, AchievementRarity.COMMON), item("workouts_25", com.hedefit.app.ui.i18n.tr("Güçleniyor", "Getting Stronger"), com.hedefit.app.ui.i18n.tr("25 antrenman tamamla", "Complete 25 workouts"), workouts, 25.0, AchievementRarity.RARE), item("workouts_50", com.hedefit.app.ui.i18n.tr("Demir İrade", "Iron Will"), com.hedefit.app.ui.i18n.tr("50 antrenman tamamla", "Complete 50 workouts"), workouts, 50.0, AchievementRarity.EPIC), item("workouts_100", com.hedefit.app.ui.i18n.tr("Yüzlük Kulüp", "Century Club"), com.hedefit.app.ui.i18n.tr("100 antrenman tamamla", "Complete 100 workouts"), workouts, 100.0, AchievementRarity.EPIC),
            item("steps_10k", com.hedefit.app.ui.i18n.tr("Hareket Et", "Get Moving"), com.hedefit.app.ui.i18n.tr("Toplam 10.000 adıma ulaş", "Reach 10,000 total steps"), steps, 10_000.0, AchievementRarity.COMMON), item("steps_50k", com.hedefit.app.ui.i18n.tr("50K Adım", "50K Steps"), com.hedefit.app.ui.i18n.tr("Toplam 50.000 adıma ulaş", "Reach 50,000 total steps"), steps, 50_000.0, AchievementRarity.COMMON), item("steps_100k", "100K Club", com.hedefit.app.ui.i18n.tr("Toplam 100.000 adıma ulaş", "Reach 100,000 total steps"), steps, 100_000.0, AchievementRarity.RARE), item("steps_250k", com.hedefit.app.ui.i18n.tr("Çeyrek Milyon", "Quarter Million"), com.hedefit.app.ui.i18n.tr("Toplam 250.000 adıma ulaş", "Reach 250,000 total steps"), steps, 250_000.0, AchievementRarity.RARE), item("steps_500k", com.hedefit.app.ui.i18n.tr("Yarım Milyon", "Half Million"), com.hedefit.app.ui.i18n.tr("Toplam 500.000 adıma ulaş", "Reach 500,000 total steps"), steps, 500_000.0, AchievementRarity.EPIC),
            item("route_5", com.hedefit.app.ui.i18n.tr("Yola Çık", "Hit the Road"), com.hedefit.app.ui.i18n.tr("Toplam 5 km yürü/koş", "Walk/run 5 km in total"), km, 5.0, AchievementRarity.COMMON), item("route_half", "21.1", com.hedefit.app.ui.i18n.tr("Toplam 21,1 km yürü/koş", "Walk/run 21.1 km in total"), km, 21.1, AchievementRarity.RARE), item("marathon_distance", "42.2", com.hedefit.app.ui.i18n.tr("Toplam 42,2 km yürü/koş", "Walk/run 42.2 km in total"), km, 42.2, AchievementRarity.EPIC), item("route_100", com.hedefit.app.ui.i18n.tr("Yolcu", "Traveler"), com.hedefit.app.ui.i18n.tr("Toplam 100 km yürü/koş", "Walk/run 100 km in total"), km, 100.0, AchievementRarity.EPIC), item("route_250", com.hedefit.app.ui.i18n.tr("Ufuk Çizgisi", "Horizon"), com.hedefit.app.ui.i18n.tr("Toplam 250 km yürü/koş", "Walk/run 250 km in total"), km, 250.0, AchievementRarity.EPIC),
            item("early_once", com.hedefit.app.ui.i18n.tr("Erken Kuş", "Early Bird"), com.hedefit.app.ui.i18n.tr("08:00 öncesi bir aktivite", "One activity before 08:00"), early, 1.0, AchievementRarity.COMMON), item("early_5", com.hedefit.app.ui.i18n.tr("Gün Doğumu", "Sunrise"), com.hedefit.app.ui.i18n.tr("08:00 öncesi 5 aktivite", "5 activities before 08:00"), early, 5.0, AchievementRarity.RARE), item("early_bird", com.hedefit.app.ui.i18n.tr("Sabah Disiplini", "Morning Discipline"), com.hedefit.app.ui.i18n.tr("08:00 öncesi 10 aktivite", "10 activities before 08:00"), early, 10.0, AchievementRarity.EPIC), item("night_once", com.hedefit.app.ui.i18n.tr("Gece Modu", "Night Mode"), com.hedefit.app.ui.i18n.tr("20:00 sonrası bir aktivite", "One activity after 20:00"), night, 1.0, AchievementRarity.COMMON), item("night_5", com.hedefit.app.ui.i18n.tr("Gece Sporcusu", "Night Athlete"), com.hedefit.app.ui.i18n.tr("20:00 sonrası 5 aktivite", "5 activities after 20:00"), night, 5.0, AchievementRarity.RARE), item("streak_3", com.hedefit.app.ui.i18n.tr("3 Günlük Seri", "3-Day Streak"), com.hedefit.app.ui.i18n.tr("3 gün ritmini koru", "Keep your rhythm for 3 days"), streak, 3.0, AchievementRarity.COMMON), item("streak_7", com.hedefit.app.ui.i18n.tr("Haftalık Seri", "Weekly Streak"), com.hedefit.app.ui.i18n.tr("7 gün ritmini koru", "Keep your rhythm for 7 days"), streak, 7.0, AchievementRarity.RARE), item("streak_21", com.hedefit.app.ui.i18n.tr("Alışkanlık", "Habit"), com.hedefit.app.ui.i18n.tr("21 gün ritmini koru", "Keep your rhythm for 21 days"), streak, 21.0, AchievementRarity.EPIC),
        )
    }

    private fun motivation(quests: List<DailyQuest>, streak: Int, achievements: List<Achievement>, today: LocalDate) = when { achievements.any { it.unlockedAt == today } -> com.hedefit.app.ui.i18n.tr("Yeni başarımın +$ACHIEVEMENT_XP XP kazandırdı.", "Your new achievement earned +$ACHIEVEMENT_XP XP."); quests.all { it.completed } -> com.hedefit.app.ui.i18n.tr("Bugünün $DAILY_XP_CAP XP'lik görevlerinin tamamını bitirdin.", "You finished all of today's $DAILY_XP_CAP XP quests."); streak > 0 && streak % 7 == 0 -> com.hedefit.app.ui.i18n.tr("$streak günlük ritmin sağlamlaşıyor.", "Your $streak-day rhythm is getting solid."); else -> "Bugün küçük ama gerçek bir adım at." }
    private fun xpForLevel(level: Int) = 300 + (level - 1) * 50
    private fun instantDate(value: String, zoneId: ZoneId) = runCatching { Instant.parse(value).atZone(zoneId).toLocalDate() }.getOrNull()
    private fun localHour(value: String, zoneId: ZoneId) = runCatching { Instant.parse(value).atZone(zoneId).hour }.getOrNull()
    private fun formatInt(value: Int) = "%,d".format(value).replace(',', '.')
}
