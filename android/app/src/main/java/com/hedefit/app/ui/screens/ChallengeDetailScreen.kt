package com.hedefit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.ChallengePlanData
import com.hedefit.app.data.model.ChallengeRulesData
import com.hedefit.app.data.model.ChallengeTemplateData
import com.hedefit.app.data.model.ChallengeTodayData
import com.hedefit.app.data.model.UserChallengeData
import com.hedefit.app.data.model.difficultyLabel
import com.hedefit.app.data.model.equipmentLabel
import com.hedefit.app.data.model.minutesLabel
import com.hedefit.app.data.model.taskLabel
import com.hedefit.app.ui.components.AlertDialog
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.HfChipRow
import com.hedefit.app.ui.components.HfCircleButton
import com.hedefit.app.ui.components.HfIconBadge
import com.hedefit.app.ui.components.HfPill
import com.hedefit.app.ui.components.HfPrimaryButton
import com.hedefit.app.ui.components.HfProgressBar
import com.hedefit.app.ui.components.HfScreenHeader
import com.hedefit.app.ui.components.HfSectionHeader
import com.hedefit.app.ui.components.HfStatTile
import com.hedefit.app.ui.components.HfStepRow
import com.hedefit.app.ui.components.HfTag
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.i18n.upperLocalized
import com.hedefit.app.ui.theme.HedefitColors
import java.time.LocalDate

/** Bir challenge'ın tüm XP'si (gösterim): sunucudaki ödül kurallarıyla aynı hesap. */
internal fun earnedChallengeXp(challenge: UserChallengeData, rules: ChallengeRulesData): Int {
    val state = challenge.state()
    val worked = challenge.days.count { it.status != "recovery" }
    val recovery = challenge.days.count { it.status == "recovery" }
    return worked * rules.dayXp + recovery * rules.recoveryXp +
        (if (state.longestStreak >= 3) rules.streak3Xp else 0) + (state.longestStreak / 7) * rules.streak7Xp +
        (if (challenge.status == "completed") rules.completeXp + (if (challenge.socialChallengeId != null) rules.friendBonusXp else 0) else 0)
}

/**
 * Challenge detayı. `challenge` doluysa kullanıcının katıldığı challenge (ilerleme + bugünkü görev),
 * değilse katalog şablonu önizlemesi (Katıl).
 */
@Composable
fun ChallengeDetailScreen(
    template: ChallengeTemplateData?,
    challenge: UserChallengeData?,
    rules: ChallengeRulesData,
    today: ChallengeTodayData?,
    todayBusy: Boolean,
    actionBusy: Boolean,
    canChallengeFriends: Boolean,
    onBack: () -> Unit,
    onLoadToday: (String) -> Unit,
    onJoin: (String) -> Unit,
    onDoTask: (String) -> Unit,
    onRecovery: (String) -> Unit,
    onAbandon: (String) -> Unit,
    onShare: (UserChallengeData, Int) -> Unit,
    onChallengeFriend: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val plan: ChallengePlanData = challenge?.plan ?: template?.plan ?: return
    val todayDate = LocalDate.now()
    val state = challenge?.state(todayDate)
    val active = challenge?.status == "active"
    var confirmAbandon by remember { mutableStateOf(false) }
    var confirmRecovery by remember { mutableStateOf(false) }
    LaunchedEffect(challenge?.id, state?.todayDone) { if (challenge != null && active && state?.todayDone == false) onLoadToday(challenge.id) }
    val rewardXp = template?.rewardXp ?: (plan.days.size * rules.dayXp + (if (plan.days.size >= 3) rules.streak3Xp else 0) + (plan.days.size / 7) * rules.streak7Xp + rules.completeXp)
    val color = categoryColor(plan.category)

    ScreenContainer {
        LazyColumn(Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                HfScreenHeader("Challenge", onBack = onBack, backLabel = tr("Geri", "Back")) {
                    if (challenge != null) HfCircleButton(Icons.Default.Share, tr("Paylaş", "Share"), { onShare(challenge, earnedChallengeXp(challenge, rules)) })
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HfIconBadge(categoryIcon(plan.category), color, 48.dp, 24.dp, 15.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(com.hedefit.app.data.model.categoryLabel(plan.category).upperLocalized() + " · +$rewardXp XP", color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                                Text(plan.title.text(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            }
                            if (plan.source == "coach") HfPill(tr("Fit Koç", "Fit Coach"))
                        }
                        Text(plan.description.text(), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        HfChipRow {
                            HfTag(tr("${plan.days.size} gün", "${plan.days.size} days"))
                            HfTag(difficultyLabel(plan.difficulty))
                            minutesLabel(template?.minutes ?: plan.days.mapNotNull { it.minutes }.takeIf { it.isNotEmpty() }?.let { it.min() to it.max() })?.let { HfTag(it) }
                            equipmentLabel(plan.equipment)?.let { HfTag(it) }
                        }
                    }
                }
            }
            if (challenge != null && state != null) {
                item {
                    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (challenge.status) {
                                        "completed" -> tr("Tamamlandı 🎉", "Completed 🎉")
                                        "abandoned" -> tr("Bırakıldı", "Left")
                                        else -> tr("Gün ${state.currentDay} / ${state.totalDays}", "Day ${state.currentDay} / ${state.totalDays}")
                                    },
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
                                )
                                Text("%${state.percent}".let { tr(it, "${state.percent}%") }, color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold)
                            }
                            HfProgressBar(state.percent / 100f, color = color)
                            ChallengeDayDots(state.totalDays, challenge.days.map { it.status }, state.todayDone || !active)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HfStatTile(tr("Seri", "Streak"), "🔥 ${state.streak}", Modifier.weight(1f), sub = tr("En uzun ${state.longestStreak}", "Best ${state.longestStreak}"))
                        HfStatTile(tr("Toparlanma", "Recovery"), "${state.recoveryAllowance - state.recoveryUsed}/${state.recoveryAllowance}", Modifier.weight(1f), sub = tr("hak kaldı", "days left"))
                        HfStatTile("XP", "+${earnedChallengeXp(challenge, rules)}", Modifier.weight(1f), valueColor = HedefitColors.Lime, sub = tr("kazanıldı", "earned"))
                    }
                }
                if (active && !state.todayDone) item {
                    TodayTaskCard(challenge, state.recoveryAllowance - state.recoveryUsed, today?.takeIf { it.challenge.id == challenge.id }, todayBusy, actionBusy, rules.dayXp,
                        onDoTask = { onDoTask(challenge.id) }, onRecovery = { confirmRecovery = true })
                } else if (active) item {
                    HedefitCard(Modifier.fillMaxWidth()) {
                        Text(tr("Bugünkü görev tamam. Yarın Gün ${minOf(state.doneDays + 1, state.totalDays)} seni bekliyor.", "Today's task is done. Day ${minOf(state.doneDays + 1, state.totalDays)} is waiting tomorrow."), color = HedefitColors.TextSecondary)
                    }
                }
            } else if (template != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HfPrimaryButton(if (actionBusy) tr("Başlatılıyor…", "Starting…") else tr("Challenge'a Katıl", "Join Challenge"), { if (!actionBusy) onJoin(plan.key) }, Modifier.fillMaxWidth(), Icons.Default.PlayArrow)
                        if (canChallengeFriends && plan.days.size <= 30) HfPrimaryButton(tr("Arkadaşınla yap", "Do it with a friend"), { onChallengeFriend(plan.key) }, Modifier.fillMaxWidth(), Icons.Default.Groups, secondary = true)
                    }
                }
            }
            item { HfSectionHeader(tr("Gün gün plan", "Day-by-day plan")) }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Column {
                        plan.days.forEachIndexed { index, task ->
                            val log = challenge?.days?.firstOrNull { it.dayIndex == index }
                            val meta = when (log?.status) {
                                "recovery" -> tr("Toparlanma günü", "Recovery day")
                                "adapted" -> tr("Uyarlandı · tamamlandı", "Adapted · completed")
                                "completed" -> tr("Tamamlandı", "Completed")
                                else -> if (challenge != null && index == (state?.doneDays ?: -1) && active) tr("Sıradaki", "Up next") else "+${rules.dayXp} XP"
                            }
                            HfStepRow(index + 1, taskLabel(task), meta, done = log != null)
                        }
                    }
                }
            }
            item { HfSectionHeader(tr("Ödüller", "Rewards")) }
            item {
                HedefitCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RewardLine(tr("Her tamamlanan gün", "Each completed day"), "+${rules.dayXp} XP")
                        RewardLine(tr("3 günlük seri", "3-day streak"), "+${rules.streak3Xp} XP")
                        if (plan.days.size >= 7) RewardLine(tr("Her 7 günlük seri", "Every 7-day streak"), "+${rules.streak7Xp} XP")
                        RewardLine(tr("Challenge'ı bitir", "Finish the challenge"), "+${rules.completeXp} XP")
                        RewardLine(tr("Arkadaşınla bitir", "Finish with a friend"), "+${rules.friendBonusXp} XP")
                        RewardLine(tr("Toparlanma günü", "Recovery day"), "+${rules.recoveryXp} XP")
                        Text(tr("Rozetler: İlk Challenge, 3/7/30 Günlük Seri, 10 Challenge ve kategori rozetleri.", "Badges: First Challenge, 3/7/30-Day Streak, 10 Challenges and category badges."), color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { HfSectionHeader(tr("Kurallar", "Rules")) }
            item {
                HedefitCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            tr("Her gün bir görev var; bir günde yalnızca bir gün tamamlanır.", "There's one task per day; you can complete one day per calendar day."),
                            tr("Gün kaçırırsan challenge bozulmaz, kaldığın günden devam edersin; yalnızca serin sıfırlanır.", "Missing a day doesn't break the challenge — you continue where you left off; only your streak resets."),
                            tr("Check-in'ine göre görev hafifletilebilir; uyarlanmış gün tam sayılır.", "Your task can be lightened based on your check-in; an adapted day counts in full."),
                            tr("Toparlanma günü seriyi bozmaz ama antrenman sayılmaz. Her 7 gün için 1 hakkın var.", "A recovery day keeps your streak but doesn't count as a workout. You get 1 per 7 days."),
                            tr("Kazandığın XP asla silinmez; bırakırsan da seninle kalır.", "XP you earn is never taken away, even if you leave."),
                        ).forEach { rule -> Text("• $rule", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (challenge != null && active) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canChallengeFriends && challenge.socialChallengeId == null && challenge.plan.source == "catalog") HfPrimaryButton(tr("Arkadaşa meydan oku", "Challenge a friend"), { onChallengeFriend(challenge.templateKey) }, Modifier.fillMaxWidth(), Icons.Default.Groups, secondary = true)
                    TextButton(onClick = { confirmAbandon = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = HedefitColors.TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Challenge'ı bırak", "Leave challenge"), color = HedefitColors.TextMuted)
                    }
                }
            }
        }
    }
    if (confirmAbandon && challenge != null) AlertDialog(
        onDismissRequest = { confirmAbandon = false },
        title = { Text(tr("Challenge'ı bırakmak istiyor musun?", "Leave this challenge?")) },
        text = { Text(tr("Kazandığın XP ve rozetler seninle kalır. İstersen daha sonra baştan başlayabilirsin.", "Your XP and badges stay with you. You can start again from the beginning later.")) },
        confirmButton = { TextButton(onClick = { confirmAbandon = false; onAbandon(challenge.id) }) { Text(tr("Bırak", "Leave"), color = HedefitColors.Coral) } },
        dismissButton = { TextButton(onClick = { confirmAbandon = false }) { Text(tr("Vazgeç", "Cancel")) } },
    )
    if (confirmRecovery && challenge != null) AlertDialog(
        onDismissRequest = { confirmRecovery = false },
        title = { Text(tr("Toparlanma günü kullanılsın mı?", "Use a recovery day?")) },
        text = { Text(tr("Serin bozulmaz ve gün ilerler; bu gün antrenman olarak sayılmaz.", "Your streak stays and the day moves on; it won't count as a workout.")) },
        confirmButton = { TextButton(onClick = { confirmRecovery = false; onRecovery(challenge.id) }) { Text(tr("Kullan", "Use it")) } },
        dismissButton = { TextButton(onClick = { confirmRecovery = false }) { Text(tr("Vazgeç", "Cancel")) } },
    )
}

@Composable
private fun RewardLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TodayTaskCard(
    challenge: UserChallengeData,
    recoveryLeft: Int,
    today: ChallengeTodayData?,
    loading: Boolean,
    busy: Boolean,
    dayXp: Int,
    onDoTask: () -> Unit,
    onRecovery: () -> Unit,
) {
    val planned = challenge.nextTask()
    val adaptation = today?.adaptation
    val effective = adaptation?.task ?: planned
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("BUGÜNKÜ GÖREV", "TODAY'S TASK"), color = HedefitColors.Lime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HedefitColors.Lime)
                else Text("+$dayXp XP", color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold)
            }
            effective?.let { Text(taskLabel(it), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
            if (adaptation?.adapted == true && planned != null) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HfIconBadge(Icons.Default.AutoAwesome, HedefitColors.Lime, 32.dp, 16.dp, 10.dp)
                    Column {
                        adaptation.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(tr("Planlanan: ${taskLabel(planned)} · seri bozulmaz", "Planned: ${taskLabel(planned)} · streak stays"), color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (today?.session != null && effective?.kind == "session") {
                Text(today.session.exercises.take(4).joinToString(" · ") { it.name } + if (today.session.exercises.size > 4) " …" else "", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (adaptation?.suggestRecovery == true && recoveryLeft > 0) {
                Text(tr("Fit Koç bugün dinlenmeni öneriyor. Toparlanma günü serini korur.", "Fit Coach suggests resting today. A recovery day keeps your streak."), color = HedefitColors.Water, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            val autoVerified = effective?.kind in setOf("steps", "water", "meals")
            HfPrimaryButton(if (busy) tr("Hazırlanıyor…", "Preparing…") else if (autoVerified) tr("İlerlemeyi kontrol et", "Check my progress") else tr("Bugünkü görevi yap", "Do today's task"), { if (!busy) onDoTask() }, Modifier.fillMaxWidth(), Icons.Default.PlayArrow)
            if (recoveryLeft > 0) HfPrimaryButton(tr("Toparlanma günü kullan ($recoveryLeft)", "Use a recovery day ($recoveryLeft)"), { if (!busy) onRecovery() }, Modifier.fillMaxWidth(), Icons.Default.SelfImprovement, secondary = true)
            if (effective?.kind in setOf("steps", "water", "meals")) Text(
                tr("Bu görev kayıtlarından otomatik doğrulanır; hedefe ulaşınca gün kendiliğinden tamamlanır.", "This task is verified from your logs; the day completes on its own when you hit the goal."),
                color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
