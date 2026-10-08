package com.hedefit.app.data.model

import com.hedefit.app.ui.i18n.tr
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Keşfet > Challenge veri modeli. Sunucu: /api/challenges (lib/challenges). Kurallar sunucuyla aynı:
 * ilerleme TAMAMLANAN güne bağlıdır (kaçırılan gün challenge'ı bozmaz, yalnız seriyi sıfırlar), takvim günü
 * başına bir gün, toparlanma günü seriyi bozmaz. XP her zaman sunucuda verilir; burada yalnızca gösterilir.
 */
data class L10nText(val tr: String, val en: String) {
    fun text(): String = tr(tr, en)
}

/** kind: session | workout | steps | water | meals | checkin. */
data class ChallengeTaskData(
    val kind: String,
    val session: String? = null,
    val minutes: Int? = null,
    val target: Int? = null,
)

data class ChallengeDayLog(val dayIndex: Int, val localDate: String, val status: String, val minutes: Int?)

data class ChallengePlanData(
    val key: String,
    val source: String,
    val category: String,
    val difficulty: String,
    val equipment: String,
    val title: L10nText,
    val description: L10nText,
    val days: List<ChallengeTaskData>,
)

data class ChallengeTemplateData(
    val plan: ChallengePlanData,
    val minutes: Pair<Int, Int>?,
    val rewardXp: Int,
    val participants: Int,
    val popular: Boolean,
    /** fit | stretch | needs_band */
    val fit: String,
    val recommended: Boolean,
)

data class UserChallengeData(
    val id: String,
    val templateKey: String,
    val plan: ChallengePlanData,
    /** active | completed | abandoned */
    val status: String,
    val socialChallengeId: String?,
    val startedOn: String,
    val completedAt: String?,
    val days: List<ChallengeDayLog>,
) {
    val totalDays: Int get() = plan.days.size
    fun state(today: LocalDate = LocalDate.now()): ChallengeStateData = challengeState(totalDays, days, today)
    fun nextTask(today: LocalDate = LocalDate.now()): ChallengeTaskData? = state(today).let { if (it.finished) null else plan.days.getOrNull(it.doneDays) }
}

data class ChallengeStateData(
    val totalDays: Int,
    val doneDays: Int,
    val currentDay: Int,
    val todayDone: Boolean,
    val streak: Int,
    val longestStreak: Int,
    val recoveryUsed: Int,
    val recoveryAllowance: Int,
    val percent: Int,
    val finished: Boolean,
)

data class ChallengeRulesData(val dayXp: Int = 25, val recoveryXp: Int = 5, val streak3Xp: Int = 30, val streak7Xp: Int = 100, val completeXp: Int = 250, val friendBonusXp: Int = 100)

data class ChallengeHubData(
    val rules: ChallengeRulesData,
    val templates: List<ChallengeTemplateData>,
    val challenges: List<UserChallengeData>,
) {
    val active: List<UserChallengeData> get() = challenges.filter { it.status == "active" }
    val history: List<UserChallengeData> get() = challenges.filter { it.status != "active" }

    /** Ana ekranda tek kart: bugünkü görevi henüz yapılmamış olan, en çok ilerlemiş aktif challenge öncelikli. */
    fun primaryActive(today: LocalDate = LocalDate.now()): UserChallengeData? =
        active.sortedWith(compareBy<UserChallengeData> { it.state(today).todayDone }.thenByDescending { it.state(today).percent }).firstOrNull()
}

data class ChallengeAdaptationData(
    val adapted: Boolean,
    val task: ChallengeTaskData,
    val message: String?,
    val suggestRecovery: Boolean,
    /** completed | adapted — gün tamamlanırken sunucuya gönderilir. */
    val completionStatus: String,
)

data class ChallengeTodayData(
    val challenge: UserChallengeData,
    val task: ChallengeTaskData?,
    val adaptation: ChallengeAdaptationData?,
    val session: WellnessSessionData?,
    val checkinDone: Boolean,
)

data class ChallengeCompletionData(val alreadyDone: Boolean, val xp: Int, val streak: Int, val finished: Boolean)

data class CoachChallengePreferences(
    val focus: String? = null,
    val days: Int? = null,
    val minutes: Int? = null,
    val equipment: String? = null,
    val level: String? = null,
)

data class CoachChallengePreview(val plan: ChallengePlanData, val rewardXp: Int, val minutes: Pair<Int, Int>?, val resolved: CoachChallengePreferences)

data class FriendProfileData(
    val id: String,
    val username: String?,
    val displayName: String?,
    val shared: Boolean,
    val totalXp: Int,
    val completedChallenges: Int,
    val bestStreak: Int,
    val activeChallenges: List<Triple<L10nText, Int, Int>>,
    val achievements: List<String>,
)

// ---- Durum hesabı (lib/challenges/engine.ts ile aynı) ----------------------------------------------------

fun recoveryAllowance(totalDays: Int): Int = maxOf(1, totalDays / 7)

fun challengeState(totalDays: Int, logs: List<ChallengeDayLog>, today: LocalDate): ChallengeStateData {
    val dates = logs.mapNotNull { runCatching { LocalDate.parse(it.localDate).toEpochDay() }.getOrNull() }.distinct().sorted()
    var longest = 0; var run = 0; var previous: Long? = null
    for (day in dates) { run = if (previous != null && day - previous == 1L) run + 1 else 1; longest = maxOf(longest, run); previous = day }
    val streak = if (previous != null && today.toEpochDay() - previous <= 1) run else 0
    val done = minOf(totalDays, logs.size)
    val finished = done >= totalDays
    return ChallengeStateData(
        totalDays = totalDays,
        doneDays = done,
        currentDay = if (finished) totalDays else done + 1,
        todayDone = today.toEpochDay() in dates,
        streak = streak,
        longestStreak = longest,
        recoveryUsed = logs.count { it.status == "recovery" },
        recoveryAllowance = recoveryAllowance(totalDays),
        percent = if (totalDays > 0) done * 100 / totalDays else 0,
        finished = finished,
    )
}

// ---- Metinler (TR/EN) ------------------------------------------------------------------------------------

fun sessionLabel(session: String?): String = when (session) {
    "core_focus" -> "Core"
    "band_strength" -> tr("Direnç bandı", "Resistance band")
    "flexibility" -> tr("Esneklik", "Flexibility")
    "home_strength" -> tr("Evde güç", "Home strength")
    "pilates_today" -> "Pilates"
    "low_impact_recovery" -> tr("Düşük etkili toparlanma", "Low impact recovery")
    "posture_mobility" -> tr("Duruş ve mobilite", "Posture & mobility")
    else -> tr("Antrenman", "Workout")
}

private fun formatThousands(value: Int): String =
    if (com.hedefit.app.ui.i18n.AppLang.en) "%,d".format(java.util.Locale.US, value) else "%,d".format(java.util.Locale.US, value).replace(',', '.')

/** "12 dk Core", "8.000 adım", "3 öğün kaydet", "2 L su", "Programındaki antrenman" … */
fun taskLabel(task: ChallengeTaskData): String = when (task.kind) {
    "session" -> tr("${task.minutes ?: 10} dk ${sessionLabel(task.session)}", "${task.minutes ?: 10} min ${sessionLabel(task.session)}")
    "workout" -> tr("Programındaki antrenman", "Your program workout")
    "steps" -> tr("${formatThousands(task.target ?: 0)} adım", "${formatThousands(task.target ?: 0)} steps")
    "water" -> {
        val litres = (task.target ?: 0) / 1000.0
        val text = if (litres % 1.0 == 0.0) litres.toInt().toString() else (if (com.hedefit.app.ui.i18n.AppLang.en) "%.1f".format(java.util.Locale.US, litres) else "%.1f".format(java.util.Locale.US, litres).replace('.', ','))
        tr("$text L su iç", "Drink $text L of water")
    }
    "meals" -> tr("${task.target ?: 3} öğün kaydet", "Log ${task.target ?: 3} meals")
    "checkin" -> tr("Günlük check-in", "Daily check-in")
    else -> tr("Görev", "Task")
}

fun categoryLabel(category: String): String = when (category) {
    "workout" -> tr("Antrenman", "Workout")
    "nutrition" -> tr("Beslenme", "Nutrition")
    "steps" -> tr("Adım", "Steps")
    "pilates" -> "Pilates"
    "flexibility" -> tr("Esneklik", "Flexibility")
    "coach" -> tr("Fit Koç", "Fit Coach")
    else -> category
}

fun difficultyLabel(difficulty: String): String = when (difficulty) {
    "intermediate" -> tr("Orta", "Intermediate")
    "advanced" -> tr("İleri", "Advanced")
    else -> tr("Başlangıç", "Beginner")
}

fun equipmentLabel(equipment: String): String? = when (equipment) {
    "band" -> tr("Direnç bandı", "Resistance band")
    "mat" -> tr("Mat", "Mat")
    "program" -> tr("Kendi programın", "Your own program")
    else -> null
}

fun fitLabel(fit: String): String = when (fit) {
    "stretch" -> tr("Zorlayıcı", "Challenging")
    "needs_band" -> tr("Bant gerekir", "Needs a band")
    else -> tr("Sana uygun", "Good fit")
}

fun minutesLabel(range: Pair<Int, Int>?): String? = range?.let { (low, high) ->
    if (low == high) tr("$low dk/gün", "$low min/day") else tr("$low–$high dk/gün", "$low–$high min/day")
}

fun participantsLabel(count: Int): String? = when {
    count <= 0 -> null
    count >= 1000 -> {
        val k = count / 1000.0
        val text = if (com.hedefit.app.ui.i18n.AppLang.en) "%.1fK".format(java.util.Locale.US, k) else "%.1fB".format(java.util.Locale.US, k).replace('.', ',')
        tr("$text katılımcı", "$text participants")
    }
    else -> tr("$count katılımcı", if (count == 1) "1 participant" else "$count participants")
}

fun achievementTitle(id: String): String = when (id) {
    "first_challenge" -> tr("İlk Challenge", "First Challenge")
    "streak_3" -> tr("3 Günlük Seri", "3-Day Streak")
    "streak_7" -> tr("7 Günlük Seri", "7-Day Streak")
    "streak_30" -> tr("30 Günlük Seri", "30-Day Streak")
    "workouts_10" -> tr("10 Antrenman", "10 Workouts")
    "workouts_50" -> tr("50 Antrenman", "50 Workouts")
    "first_pilates_challenge" -> tr("İlk Pilates Challenge", "First Pilates Challenge")
    "first_nutrition_challenge" -> tr("İlk Beslenme Challenge", "First Nutrition Challenge")
    "first_friend_challenge" -> tr("İlk Arkadaş Challenge", "First Friend Challenge")
    "challenges_10" -> tr("10 Challenge Tamamlandı", "10 Challenges Completed")
    "first_activity" -> tr("İlk Adım", "First Step")
    "steps_100k" -> "100K Club"
    "marathon_distance" -> "42.2"
    "early_bird" -> tr("Sabah Disiplini", "Morning Discipline")
    "night_athlete" -> tr("Gece Sporcusu", "Night Athlete")
    else -> id.replace('_', ' ').replaceFirstChar(Char::uppercase)
}

/** Sunucu hata kodlarının kullanıcı metni. */
fun challengeErrorText(code: String?): String = when (code) {
    "already_joined" -> tr("Bu challenge zaten aktif.", "This challenge is already active.")
    "too_many_active" -> tr("Aynı anda en fazla 3 challenge yürütebilirsin. Önce birini bitir ya da bırak.", "You can run up to 3 challenges at once. Finish or leave one first.")
    "task_not_verified" -> tr("Bugünkü görev henüz tamamlanmamış görünüyor. Görevi yaptıktan sonra tekrar dene.", "Today's task doesn't look complete yet. Try again after you've done it.")
    "adaptation_not_allowed" -> tr("Uyarlanmış görev için önce bugünün check-in'ini yap.", "Do today's check-in first to use the adapted task.")
    "no_recovery_left" -> tr("Bu challenge için toparlanma hakkın kalmadı.", "No recovery days left for this challenge.")
    "invalid_day" -> tr("Bugünün görevi zaten kaydedildi ya da tarih geçersiz.", "Today's task is already logged or the date is invalid.")
    "not_active" -> tr("Bu challenge artık aktif değil.", "This challenge is no longer active.")
    "not_friends" -> tr("Bu profili yalnızca arkadaşlar görebilir.", "Only friends can see this profile.")
    "no_friends" -> tr("Meydan okumak için en az bir arkadaş seç.", "Pick at least one friend to challenge.")
    "challenges_unavailable" -> tr("Challenge'lar şu an kullanılamıyor. Biraz sonra tekrar dene.", "Challenges are unavailable right now. Try again shortly.")
    else -> tr("İşlem tamamlanamadı. Bağlantını kontrol edip tekrar dene.", "Couldn't complete that. Check your connection and try again.")
}

// ---- JSON ------------------------------------------------------------------------------------------------

private fun JSONObject?.l10n(): L10nText = L10nText(this?.optString("tr").orEmpty(), this?.optString("en").orEmpty())

private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null

fun parseChallengeTask(json: JSONObject): ChallengeTaskData = ChallengeTaskData(
    kind = json.optString("kind"),
    session = json.optString("session").takeIf { it.isNotBlank() && it != "null" },
    minutes = json.optIntOrNull("minutes"),
    target = json.optIntOrNull("target"),
)

fun ChallengeTaskData.toJson(): JSONObject = JSONObject().put("kind", kind).apply {
    session?.let { put("session", it) }; minutes?.let { put("minutes", it) }; target?.let { put("target", it) }
}

fun parseChallengePlan(json: JSONObject): ChallengePlanData {
    val days = json.optJSONArray("days") ?: JSONArray()
    return ChallengePlanData(
        key = json.optString("key"),
        source = json.optString("source", "catalog"),
        category = json.optString("category"),
        difficulty = json.optString("difficulty", "beginner"),
        equipment = json.optString("equipment", "none"),
        title = json.optJSONObject("title").l10n(),
        description = json.optJSONObject("description").l10n(),
        days = buildList { for (index in 0 until days.length()) days.optJSONObject(index)?.let { add(parseChallengeTask(it)) } },
    )
}

fun ChallengePlanData.toJson(): JSONObject = JSONObject()
    .put("key", key).put("source", source).put("category", category).put("difficulty", difficulty).put("equipment", equipment)
    .put("title", JSONObject().put("tr", title.tr).put("en", title.en))
    .put("description", JSONObject().put("tr", description.tr).put("en", description.en))
    .put("days", JSONArray(days.map { it.toJson() }))

private fun JSONArray?.minutesPair(): Pair<Int, Int>? = if (this != null && length() == 2) optInt(0) to optInt(1) else null

fun parseUserChallenge(json: JSONObject): UserChallengeData {
    val days = json.optJSONArray("days") ?: JSONArray()
    return UserChallengeData(
        id = json.optString("id"),
        templateKey = json.optString("templateKey"),
        plan = parseChallengePlan(json.optJSONObject("plan") ?: JSONObject()),
        status = json.optString("status", "active"),
        socialChallengeId = json.optString("socialChallengeId").takeIf { it.isNotBlank() && it != "null" },
        startedOn = json.optString("startedOn"),
        completedAt = json.optString("completedAt").takeIf { it.isNotBlank() && it != "null" },
        days = buildList { for (index in 0 until days.length()) days.optJSONObject(index)?.let { add(ChallengeDayLog(it.optInt("dayIndex"), it.optString("localDate"), it.optString("status"), it.optIntOrNull("minutes"))) } },
    )
}

fun UserChallengeData.toJson(): JSONObject = JSONObject()
    .put("id", id).put("templateKey", templateKey).put("plan", plan.toJson()).put("status", status)
    .put("socialChallengeId", socialChallengeId ?: JSONObject.NULL).put("startedOn", startedOn).put("completedAt", completedAt ?: JSONObject.NULL)
    .put("days", JSONArray(days.map { JSONObject().put("dayIndex", it.dayIndex).put("localDate", it.localDate).put("status", it.status).put("minutes", it.minutes ?: JSONObject.NULL) }))

fun parseChallengeHub(json: JSONObject): ChallengeHubData {
    val rules = json.optJSONObject("rules")
    fun rule(name: String, fallback: Int) = rules?.optJSONObject(name)?.optInt("amount", fallback) ?: fallback
    val templates = json.optJSONArray("templates") ?: JSONArray()
    val challenges = json.optJSONArray("challenges") ?: JSONArray()
    return ChallengeHubData(
        rules = ChallengeRulesData(rule("CHALLENGE_DAY_COMPLETED", 25), rule("CHALLENGE_RECOVERY_DAY", 5), rule("CHALLENGE_STREAK_3", 30), rule("CHALLENGE_STREAK_7", 100), rule("CHALLENGE_COMPLETED", 250), rule("FRIEND_CHALLENGE_BONUS", 100)),
        templates = buildList {
            for (index in 0 until templates.length()) templates.optJSONObject(index)?.let { item ->
                add(ChallengeTemplateData(
                    plan = parseChallengePlan(item),
                    minutes = item.optJSONArray("minutes").minutesPair(),
                    rewardXp = item.optInt("rewardXp"),
                    participants = item.optInt("participants"),
                    popular = item.optBoolean("popular"),
                    fit = item.optString("fit", "fit"),
                    recommended = item.optBoolean("recommended"),
                ))
            }
        },
        challenges = buildList { for (index in 0 until challenges.length()) challenges.optJSONObject(index)?.let { add(parseUserChallenge(it)) } },
    )
}

/** Çevrimdışı önbellek için hub'ı sunucu biçiminde geri yazar. */
fun ChallengeHubData.toJson(): JSONObject = JSONObject()
    .put("rules", JSONObject()
        .put("CHALLENGE_DAY_COMPLETED", JSONObject().put("amount", rules.dayXp))
        .put("CHALLENGE_RECOVERY_DAY", JSONObject().put("amount", rules.recoveryXp))
        .put("CHALLENGE_STREAK_3", JSONObject().put("amount", rules.streak3Xp))
        .put("CHALLENGE_STREAK_7", JSONObject().put("amount", rules.streak7Xp))
        .put("CHALLENGE_COMPLETED", JSONObject().put("amount", rules.completeXp))
        .put("FRIEND_CHALLENGE_BONUS", JSONObject().put("amount", rules.friendBonusXp)))
    .put("templates", JSONArray(templates.map { template ->
        template.plan.toJson().put("rewardXp", template.rewardXp).put("participants", template.participants).put("popular", template.popular)
            .put("fit", template.fit).put("recommended", template.recommended)
            .put("minutes", template.minutes?.let { JSONArray(listOf(it.first, it.second)) } ?: JSONObject.NULL)
    }))
    .put("challenges", JSONArray(challenges.map { it.toJson() }))

fun parseChallengeToday(json: JSONObject): ChallengeTodayData {
    val adaptation = json.optJSONObject("adaptation")
    return ChallengeTodayData(
        challenge = parseUserChallenge(json.optJSONObject("challenge") ?: JSONObject()),
        task = json.optJSONObject("task")?.let(::parseChallengeTask),
        adaptation = adaptation?.let {
            ChallengeAdaptationData(
                adapted = it.optBoolean("adapted"),
                task = parseChallengeTask(it.optJSONObject("task") ?: JSONObject()),
                message = it.optString("message").takeIf { text -> text.isNotBlank() && text != "null" },
                suggestRecovery = it.optBoolean("suggestRecovery"),
                completionStatus = it.optString("completionStatus", "completed"),
            )
        },
        session = json.optJSONObject("session")?.let { parseWellnessSession(JSONObject().put("session", it)) },
        checkinDone = json.optBoolean("checkinDone"),
    )
}

fun parseChallengeCompletion(json: JSONObject?): ChallengeCompletionData = ChallengeCompletionData(
    alreadyDone = json?.optBoolean("alreadyDone") == true,
    xp = json?.optInt("xp") ?: 0,
    streak = json?.optInt("streak") ?: 0,
    finished = json?.optBoolean("finished") == true,
)

fun parseCoachPreview(json: JSONObject): CoachChallengePreview {
    val inputs = json.optJSONObject("inputs")
    return CoachChallengePreview(
        plan = parseChallengePlan(json.optJSONObject("plan") ?: JSONObject()),
        rewardXp = json.optInt("rewardXp"),
        minutes = json.optJSONArray("minutes").minutesPair(),
        resolved = CoachChallengePreferences(inputs?.optString("focus"), inputs?.optInt("days"), inputs?.optInt("minutes"), inputs?.optString("equipment"), inputs?.optString("level")),
    )
}

fun CoachChallengePreferences.toJson(): JSONObject = JSONObject().apply {
    focus?.let { put("focus", it) }; days?.let { put("days", it) }; minutes?.let { put("minutes", it) }
    equipment?.let { put("equipment", it) }; level?.let { put("level", it) }
}

fun parseFriendProfile(json: JSONObject): FriendProfileData {
    val active = json.optJSONArray("activeChallenges") ?: JSONArray()
    val achievements = json.optJSONArray("achievements") ?: JSONArray()
    return FriendProfileData(
        id = json.optString("id"),
        username = json.optString("username").takeIf { it.isNotBlank() && it != "null" },
        displayName = json.optString("displayName").takeIf { it.isNotBlank() && it != "null" },
        shared = json.optBoolean("shared", true),
        totalXp = json.optInt("totalXp"),
        completedChallenges = json.optInt("completedChallenges"),
        bestStreak = json.optInt("bestStreak"),
        activeChallenges = buildList { for (index in 0 until active.length()) active.optJSONObject(index)?.let { add(Triple(it.optJSONObject("title").l10n(), it.optInt("doneDays"), it.optInt("totalDays"))) } },
        achievements = buildList { for (index in 0 until achievements.length()) achievements.optString(index).takeIf { it.isNotBlank() }?.let(::add) },
    )
}
