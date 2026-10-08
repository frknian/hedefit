package com.hedefit.app.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hedefit.app.data.model.CoachChallengePreview
import com.hedefit.app.ui.components.HedefitAppFrame
import com.hedefit.app.ui.components.gamificationSnapshotOf
import com.hedefit.app.ui.model.AppDestination
import com.hedefit.app.ui.screens.ChallengeDetailScreen
import com.hedefit.app.ui.screens.ChallengeHubScreen
import com.hedefit.app.ui.screens.CoachChallengeScreen
import com.hedefit.app.ui.screens.CoachScreen
import com.hedefit.app.ui.screens.CommunityScreen
import com.hedefit.app.ui.screens.ExploreHeader
import com.hedefit.app.ui.screens.ExploreSegment
import com.hedefit.app.ui.screens.HomeScreen
import com.hedefit.app.ui.screens.NutritionScreen
import com.hedefit.app.ui.screens.ProgressScreen
import com.hedefit.app.ui.screens.WorkoutPlanScreen
import com.hedefit.app.ui.state.ChatMessageState

/**
 * YALNIZ DEBUG. Gerçek uygulama ekranlarını (aynı composable'lar, alt bar dahil) demo veriyle çizer:
 *   adb shell am start -n com.hedefit.app/com.hedefit.app.debug.DebugGalleryActivity --es screen home --es lang tr
 * Ekranlar: home, explore, challenges, challenge_detail, community, step_race, progress, coach, coach_challenge, nutrition.
 * Ağ çağrısı ve hesap yoktur; geri bildirimler yalnızca logcat'e yazılır.
 */
@Composable
fun Showcase(screen: String, lang: String, onFinish: () -> Unit) {
    val en = lang == "en"
    val data = remember(lang) { DemoData.dashboard(en) }
    val hub = remember { DemoData.hub() }
    val coachName = if (en) "Fit Coach" else "Fit Koç"
    val tab = when (screen) {
        "home" -> AppDestination.Home
        "explore", "challenges", "community", "step_race" -> AppDestination.Explore
        "progress" -> AppDestination.Progress
        "coach" -> AppDestination.Coach
        "nutrition" -> AppDestination.Nutrition
        else -> null
    }
    var segment by remember { mutableStateOf(when (screen) { "challenges" -> ExploreSegment.Challenges; "community", "step_race" -> ExploreSegment.Community; else -> ExploreSegment.Programs }) }
    val log: (String) -> Unit = { android.util.Log.i("HedefitDebug", it) }
    if (tab == null) {
        when (screen) {
            "challenge_detail" -> ChallengeDetailScreen(
                template = null, challenge = hub.challenges.first(), rules = hub.rules, today = null, todayBusy = false, actionBusy = false, canChallengeFriends = true,
                onBack = onFinish, onLoadToday = {}, onJoin = {}, onDoTask = {}, onRecovery = {}, onAbandon = {}, onShare = { _, _ -> }, onChallengeFriend = {},
            )
            "coach_challenge" -> CoachChallengeScreen(
                coachName = coachName, profile = data.profile,
                preview = CoachChallengePreview(DemoData.coachPlan(), 14 * 25 + 30 + 200 + 250, 10 to 15, com.hedefit.app.data.model.CoachChallengePreferences("core", 14, 15, "band", "beginner")),
                busy = false, onBack = onFinish, onPreview = {}, onStart = {},
            )
        }
        return
    }
    HedefitAppFrame(selected = tab, onSelect = {}, language = lang, coachName = coachName) { padding, expanded ->
        when (tab) {
            AppDestination.Home -> HomeScreen(
                padding, expanded, data, null, false, null, onRetry = {}, onSignOut = {}, onOpenProfile = {}, onOpenCalendar = {}, onOpenRoute = {}, onOpenGoal = {},
                onOpenNutrition = {}, onOpenProgram = {}, onProgramHomeVisibilityChange = { _, _ -> }, unitSystem = "metric", stepGoal = 10_000, onStepGoalChange = {},
                waterGoalMl = 2_500, onWaterGoalChange = {}, onAddWater = {}, language = lang, checkinToday = DemoData.checkin(), dailyStreak = 9,
                activeChallenge = hub.primaryActive(), otherActiveChallenges = hub.active.size - 1, onDoChallengeTask = { log("challenge task") },
            )
            AppDestination.Explore -> {
                val header: @Composable () -> Unit = { ExploreHeader(segment) { segment = it } }
                when (segment) {
                    ExploreSegment.Programs -> WorkoutPlanScreen(
                        padding, expanded, data.workouts, data.workoutPrograms, emptyList(), false, false, false, {}, onStartWorkout = {}, hasActiveWorkout = false, onResumeWorkout = {},
                        onOpenScanner = {}, onOpenLibrary = {}, onOpenActivityLog = {}, onOpenRoute = {}, onGenerateRegional = { _, _, _ -> }, onLoadRegional = {},
                        onGenerateQuickWorkout = { _, _, _, _, _, _ -> }, onCreateOwnPlan = {}, onAddPushPullTemplate = {}, onSelectProgram = {}, onRemoveProgram = {}, onCopyProgram = {},
                        onUpdateExercise = {}, onReplaceExercise = { _, _ -> }, onRemoveExercise = {}, onMoveExercise = { _, _ -> }, onLoadReplacementOptions = {}, language = lang,
                        profile = data.profile, exploreHeader = header,
                    )
                    ExploreSegment.Challenges -> ChallengeHubScreen(
                        padding, header, hub, busy = false, offline = false, error = null, coachName = coachName, onRetry = {}, onOpenCoachChallenge = {},
                        onOpenTemplate = {}, onOpenChallenge = {}, onJoin = {},
                    )
                    ExploreSegment.Community -> CommunityScreen(
                        padding, header, isGuest = false, summary = DemoData.friends(en), busy = false, searchResults = emptyList(), searchBusy = false,
                        discoverable = true, shareProgress = true, leaderboard = DemoData.leaderboard(en), friendChallenges = DemoData.friendChallenges(en),
                        progress = DemoData.stepRace(en), progressBusy = false, openProgressId = if (screen == "step_race") "social1" else null,
                        onSearch = {}, onClearSearch = {}, onSendRequest = {}, onAccept = {}, onDecline = {}, onRemove = {}, onDiscoverableChange = {}, onShareProgressChange = {},
                        onOpenFriend = {}, onAcceptChallenge = {}, onDeclineChallenge = {}, onLeaveChallenge = {}, onOpenProgress = {}, onCloseProgress = {}, onSaveAccount = {},
                    )
                }
            }
            AppDestination.Progress -> ProgressScreen(
                padding, expanded, data, lang, weeklyWorkoutGoal = 4,
                gamification = gamificationSnapshotOf(data, 10_000, 2_500, 4), challengeHub = hub,
            )
            AppDestination.Coach -> CoachScreen(
                padding, expanded,
                listOf(
                    ChatMessageState(if (en) "I slept badly and my legs are sore. Should I still train today?" else "Kötü uyudum, bacaklarım da ağrıyor. Bugün yine de antrenman yapayım mı?", true),
                    ChatMessageState(
                        if (en) "Thanks for checking in. With low sleep and sore legs, a lighter day is smarter. I adapted today's Core Challenge task to 8 minutes of controlled core and mobility — it still counts in full and your streak stays."
                        else "Haber verdiğin için teşekkürler. Az uyku ve bacak ağrısıyla bugün daha hafif bir gün daha akıllıca. Core Challenge'daki bugünkü görevini 8 dakikalık kontrollü core ve mobiliteye uyarladım — gün yine tam sayılır, serin bozulmaz.",
                        false,
                    ),
                ),
                false, {}, data, onOpenPlan = {}, language = lang, coachName = coachName, onCreateChallenge = {}, usageUsed = 2, usageLimit = 30,
            )
            AppDestination.Nutrition -> NutritionScreen(
                padding, expanded, data, false, false, emptyList(), null, { _, _, _ -> }, {}, { _, _, _ -> }, {}, {}, { _, _, _ -> }, {}, {}, {},
                waterGoalMl = 2_500, onWaterGoalChange = {}, language = lang,
            )
            else -> Unit
        }
    }
}
