package com.hedefit.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.gamification.GamificationEngine
import com.hedefit.app.gamification.GamificationInput
import com.hedefit.app.gamification.GamificationSnapshot
import com.hedefit.app.gamification.AchievementRarity
import com.hedefit.app.ui.theme.HedefitColors
import java.time.ZoneId

fun gamificationSnapshotOf(data: DashboardData, stepGoal: Int, waterGoalMl: Int, weeklyActivityGoal: Int): GamificationSnapshot =
    GamificationEngine.snapshot(
        GamificationInput(
            sessions = data.sessions,
            routes = data.routeActivities,
            stepHistory = data.stepHistory,
            todaySteps = data.steps,
            todayWaterMl = data.waterMl,
            sleepMinutes = data.sleepMinutes,
            nutritionLogs = data.nutritionLogs,
            nutritionGoal = data.nutritionGoal,
            schedule = data.schedule,
            dailyStepGoal = stepGoal,
            dailyWaterGoalMl = waterGoalMl,
            weeklyActivityGoal = weeklyActivityGoal,
            zoneId = ZoneId.systemDefault(),
            authoritativeTotalXp = data.gamificationTotalXp,
            authoritativeWeeklyXp = data.gamificationWeeklyXp,
            unlockedAchievements = data.unlockedAchievements,
        ),
    )

/** Oturum içinde görülen son durum; yalnızca bu oturumda olan değişiklikleri kutlarız. */
private class CelebrationBaseline {
    var level: Int? = null
    var unlocked: Set<String> = emptySet()
    var completedQuests: Set<String> = emptySet()
}

/**
 * Oyunlaştırma durumunu izler ve gerçek başarı anlarında (seviye atlama, rozet açılışı,
 * günlük görev kapanışı) kutlama olayı üretir. İlk yüklemede yalnızca taban çizgisi
 * kaydedilir; uygulamayı açmak tek başına konfeti patlatmaz.
 */
@Composable
fun CelebrationWatcher(snapshot: GamificationSnapshot?, onCelebrate: (CelebrationEvent) -> Unit, onQuestCompleted: (xp: Int) -> Unit = {}) {
    val baseline = remember { CelebrationBaseline() }
    LaunchedEffect(snapshot) {
        val snap = snapshot ?: return@LaunchedEffect
        val unlocked = snap.achievements.filter { it.unlockedAt != null }.map { it.id }.toSet()
        val quests = snap.dailyQuests.filter { it.completed }.map { it.id }.toSet()
        val previousLevel = baseline.level
        if (previousLevel != null) {
            if (snap.level.level > previousLevel) {
                onCelebrate(CelebrationEvent(
                    kind = CelebrationKind.LevelUp,
                    title = com.hedefit.app.ui.i18n.tr("Seviye atladın!", "Level up!"),
                    subtitle = com.hedefit.app.ui.i18n.tr("Artık seviye ${snap.level.level}. İstikrarın karşılığını alıyorsun.", "You are now level ${snap.level.level}. Consistency pays off."),
                    levelProgressFrom = 0f,
                    levelProgressTo = snap.level.progress,
                    level = snap.level.level,
                    streakDays = snap.streakDays,
                ))
            }
            snap.achievements.filter { it.id in unlocked - baseline.unlocked }.forEach { achievement ->
                onCelebrate(CelebrationEvent(
                    kind = CelebrationKind.Achievement,
                    title = achievement.title,
                    subtitle = achievement.description,
                    xpGained = achievement.rewardXp,
                    emoji = when (achievement.rarity) {
                        AchievementRarity.COMMON -> "🏅"
                        AchievementRarity.RARE -> "💎"
                        AchievementRarity.EPIC -> "👑"
                    },
                    accent = when (achievement.rarity) {
                        AchievementRarity.COMMON -> HedefitColors.Lime
                        AchievementRarity.RARE -> HedefitColors.Water
                        AchievementRarity.EPIC -> HedefitColors.Sleep
                    },
                ))
            }
            snap.dailyQuests.filter { it.id in quests - baseline.completedQuests }.forEach { onQuestCompleted(it.xp) }
        }
        baseline.level = snap.level.level
        baseline.unlocked = unlocked
        baseline.completedQuests = quests
    }
}
