package com.hedefit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.gamification.Achievement
import com.hedefit.app.gamification.DailyQuest
import com.hedefit.app.gamification.GamificationEngine
import com.hedefit.app.gamification.GamificationInput
import com.hedefit.app.gamification.GamificationSnapshot
import com.hedefit.app.gamification.HabitDay
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.ui.components.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.clickable
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// These must be getters. Capturing the mutable palette at class-load time made
// the Tasks screen keep the old dark/green colors after a theme or accent
// change while the rest of the app updated.
private val GameSurfaceHigh get() = HedefitColors.SurfaceHigh
private val GameLime get() = HedefitColors.Lime
private val GameLimeDim get() = HedefitColors.Lime.copy(alpha = .75f)
private val GameInk get() = HedefitColors.TextSecondary
private val GameHeat get() = HedefitColors.Warning
private val GameText get() = HedefitColors.TextPrimary
private val GameTextMuted get() = HedefitColors.TextSecondary

@Composable
fun GameScreen(
    padding: PaddingValues,
    data: DashboardData?,
    stepGoal: Int,
    waterGoalMl: Int,
    weeklyActivityGoal: Int,
    language: String = "tr",
    onBack: (() -> Unit)? = null,
) {
    val en = language == "en"
    val snapshot = remember(data, stepGoal, waterGoalMl, weeklyActivityGoal) {
        data?.let {
            GamificationEngine.snapshot(
                GamificationInput(
                    sessions = it.sessions,
                    routes = it.routeActivities,
                    stepHistory = it.stepHistory,
                    todaySteps = it.steps,
                    todayWaterMl = it.waterMl,
                    sleepMinutes = it.sleepMinutes,
                    nutritionLogs = it.nutritionLogs,
                    nutritionGoal = it.nutritionGoal,
                    schedule = it.schedule,
                    dailyStepGoal = stepGoal,
                    dailyWaterGoalMl = waterGoalMl,
                    weeklyActivityGoal = weeklyActivityGoal,
                    zoneId = ZoneId.systemDefault(),
                    authoritativeTotalXp = it.gamificationTotalXp,
                    authoritativeWeeklyXp = it.gamificationWeeklyXp,
                    unlockedAchievements = it.unlockedAchievements,
                ),
            )
        }
    }
    ScreenContainer(padding) {
        if (snapshot == null || data == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GameLime) }
        } else {
            GameContent(snapshot, data.profile.displayName, en, onBack)
        }
    }
}

@Composable
private fun GameContent(snapshot: GamificationSnapshot, displayName: String, en: Boolean, onBack: (() -> Unit)?) {
    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }
    var showAllAchievements by remember { mutableStateOf(false) }
    val quests = snapshot.dailyQuests
    val unlocked = snapshot.achievements.count { it.unlockedAt != null }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HfScreenHeader(if (en) "Rewards" else "Ödüller", "${if (en) "Level" else "Seviye"} ${snapshot.level.level} • ${leagueFor(snapshot.totalXp)}", onBack = onBack, backLabel = if (en) "Back" else "Geri") }
        item { HeroCard(snapshot, en) }
        item { HfSectionHeader(if (en) "Today's quests" else "Bugünün görevleri", "${quests.count { it.completed }} / ${quests.size}") }
        item { QuestCard(snapshot) }
        item { HfSectionHeader(if (en) "Habit strength" else "Alışkanlık gücü", "%${snapshot.habitStrength}") }
        item { HabitCard(snapshot.habitWeek) }
        item { HfSectionHeader(if (en) "Weekly challenge" else "Haftalık meydan okuma", "250 XP") }
        item { ChallengeCard(snapshot) }
        item {
            HfSectionHeader(
                if (en) "Achievements" else "Başarımlar",
                "$unlocked / ${snapshot.achievements.size}",
            )
        }
        item {
            val shown = if (showAllAchievements) snapshot.achievements else snapshot.achievements.take(8)
            HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    shown.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { AchievementBadge(it, Modifier.weight(1f)) { selectedAchievement = it } }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (snapshot.achievements.size > 8) TextButton(onClick = { showAllAchievements = !showAllAchievements }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (showAllAchievements) (if (en) "Show less" else "Daha az") else (if (en) "See all" else "Tümünü gör"), color = GameLime, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { HfSectionHeader(if (en) "League" else "Lig durumu", leagueFor(snapshot.totalXp)) }
        item { LeagueCard(displayName.ifBlank { if (en) "You" else "Sen" }, snapshot.weeklyXp, en) }
        item { MotivationCard(snapshot.motivation) }
    }
    selectedAchievement?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedAchievement = null },
            title = { Text(item.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.description, color = GameTextMuted)
                    HfProgressBar((item.progress / item.target).toFloat())
                    Text("${item.progress.toInt()}/${if (item.target % 1.0 == 0.0) item.target.toInt() else item.target} • +${item.rewardXp} XP", color = GameLime, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { selectedAchievement = null }) { Text(if (en) "Close" else "Kapat") } },
        )
    }
}

@Composable
private fun GameCard(border: Color = GameInk.copy(alpha = .45f), content: @Composable ColumnScope.() -> Unit) = HedefitCard(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(20.dp),
) { Column(content = content) }

@Composable
private fun HeroCard(snapshot: GamificationSnapshot, en: Boolean) = GameCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(snapshot.level.progress, Modifier.size(88.dp), 9.dp, color = GameHeat) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${snapshot.level.level}", color = GameText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (en) "level" else "seviye", color = GameTextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${snapshot.level.currentXp} XP", color = GameText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (en) "${(snapshot.level.nextLevelXp - snapshot.level.currentXp).coerceAtLeast(0)} XP to level ${snapshot.level.level + 1}" else "Seviye ${snapshot.level.level + 1} için ${(snapshot.level.nextLevelXp - snapshot.level.currentXp).coerceAtLeast(0)} XP kaldı", color = GameTextMuted, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.background(HedefitColors.Coral.copy(alpha = .15f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = HedefitColors.Coral, modifier = Modifier.size(14.dp))
                Text(if (en) "${snapshot.streakDays} day streak" else "${snapshot.streakDays} günlük seri", color = HedefitColors.Coral, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    HfDivider()
    Spacer(Modifier.height(14.dp))
    val reward = snapshot.aiReward
    Row(verticalAlignment = Alignment.CenterVertically) {
        HfIconBadge(Icons.Default.Psychology, GameLime, 34.dp, 17.dp, 11.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                reward.nextThreshold?.let { if (en) "Next reward at $it XP" else "Sonraki ödül: $it XP" }
                    ?: if (reward.extraQuestions > 0) (if (en) "+${reward.extraQuestions} daily AI questions" else "+${reward.extraQuestions} günlük FitKoç sorusu") else if (en) "Fit Coach reward" else "FitKoç ödülü",
                color = GameText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            )
            reward.nextThreshold?.let { target -> HfProgressBar(snapshot.totalXp / target.toFloat()) }
            if (reward.extraQuestions > 0) Text(if (en) "You earned +${reward.extraQuestions} daily questions" else "+${reward.extraQuestions} günlük soru hakkı kazandın", color = GameLime, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HabitCard(days: List<HabitDay>) = GameCard {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { HabitDayColumn(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun HabitDayColumn(day: HabitDay, modifier: Modifier = Modifier) {
    val locale = Locale.forLanguageTag("tr-TR")
    val label = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).take(3)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (day.today) Box(Modifier.size(7.dp).background(GameLime, CircleShape)) else Spacer(Modifier.height(7.dp))
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.fillMaxWidth().height(if (day.today) 76.dp else 68.dp)
                .clip(CircleShape)
                .background(if (day.active) GameLime else GameSurfaceHigh)
                .then(if (day.today) Modifier.background(GameLimeDim.copy(alpha = .35f)) else Modifier),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (day.active) Box(Modifier.fillMaxWidth().height(22.dp).padding(4.dp).background(GameInk.copy(alpha = .35f), CircleShape))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = if (day.today) GameText else GameTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun QuestCard(snapshot: GamificationSnapshot) = GameCard {
    snapshot.dailyQuests.forEachIndexed { index, quest ->
        if (index > 0) HfDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(if (quest.completed) GameLime else GameSurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (quest.completed) Icons.Default.Check else Icons.Outlined.RadioButtonUnchecked, null, tint = if (quest.completed) HedefitColors.OnLime else GameTextMuted, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(quest.title, color = if (quest.completed) GameTextMuted else GameText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textDecoration = if (quest.completed) TextDecoration.LineThrough else null, maxLines = 2)
            Text("+${quest.xp} XP", color = GameHeat, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChallengeCard(snapshot: GamificationSnapshot) = GameCard(GameHeat.copy(alpha = .45f)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(snapshot.challenge.title, color = GameText, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("${"%.1f".format(snapshot.challenge.progress)} / ${snapshot.challenge.target.toInt()} km", color = GameTextMuted, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { (snapshot.challenge.progress / snapshot.challenge.target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxSize(), color = GameHeat, trackColor = HedefitColors.SurfaceSoft, strokeWidth = 7.dp)
            Text("${"%.1f".format(snapshot.challenge.progress)}", color = GameHeat, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LeagueCard(displayName: String, weeklyXp: Int, en: Boolean) = GameCard {
    Row(Modifier.fillMaxWidth().background(GameLime.copy(alpha = .12f), RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(GameLime, CircleShape), contentAlignment = Alignment.Center) { Text(displayName.take(1).uppercase(), color = HedefitColors.OnLime, fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.width(12.dp))
        Text(if (en) "$displayName (you)" else "$displayName (sen)", color = GameText, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$weeklyXp XP", color = GameText, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    Text(if (en) "Only verified real users will appear here when the shared leaderboard is enabled." else "Ortak liderlik tablosu açıldığında burada yalnızca doğrulanmış gerçek kullanıcılar görünecek.", color = GameTextMuted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun AchievementBadge(item: Achievement, modifier: Modifier, onClick: () -> Unit) {
    val unlocked = item.unlockedAt != null
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = item.title, onClick = onClick).padding(vertical = 4.dp).alpha(if (unlocked) 1f else .5f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(52.dp).background(if (unlocked) GameLime.copy(alpha = .15f) else GameSurfaceHigh, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(if (unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock, null, tint = if (unlocked) GameLime else GameTextMuted, modifier = Modifier.size(24.dp))
        }
        Text(item.title, color = GameTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MotivationCard(message: String) = GameCard(GameLime.copy(alpha = .55f)) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Psychology, null, tint = GameLime, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, color = GameText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun leagueFor(totalXp: Int) = when {
    totalXp >= 20_000 -> "Şampiyon"
    totalXp >= 10_000 -> "Elmas Lig"
    totalXp >= 5_000 -> "Altın Lig"
    totalXp >= 2_000 -> "Gümüş Lig"
    else -> "Bronz Lig"
}
