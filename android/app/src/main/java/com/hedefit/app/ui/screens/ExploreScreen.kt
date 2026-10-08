package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.ChallengeHubData
import com.hedefit.app.data.model.ChallengeTemplateData
import com.hedefit.app.data.model.UserChallengeData
import com.hedefit.app.data.model.categoryLabel
import com.hedefit.app.data.model.difficultyLabel
import com.hedefit.app.data.model.fitLabel
import com.hedefit.app.data.model.minutesLabel
import com.hedefit.app.data.model.participantsLabel
import com.hedefit.app.ui.components.FitCoachRobotAvatar
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.HfChip
import com.hedefit.app.ui.components.HfChipRow
import com.hedefit.app.ui.components.HfIconBadge
import com.hedefit.app.ui.components.HfPill
import com.hedefit.app.ui.components.HfProgressBar
import com.hedefit.app.ui.components.HfScreenHeader
import com.hedefit.app.ui.components.HfSectionHeader
import com.hedefit.app.ui.components.HfSegmented
import com.hedefit.app.ui.components.HfTag
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.components.UnifiedEmptyState
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.theme.HedefitColors
import java.time.LocalDate

/** Keşfet = yeni içerik ve challenge bulma. Üç bölüm; alt barda ek sekme yok. */
enum class ExploreSegment { Programs, Challenges, Community }

@Composable
fun ExploreHeader(segment: ExploreSegment, onSegment: (ExploreSegment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HfScreenHeader(tr("Keşfet", "Explore"))
        HfSegmented(
            options = listOf(tr("Programlar", "Programs"), "Challenge", tr("Topluluk", "Community")),
            selectedIndex = segment.ordinal,
            onSelect = { onSegment(ExploreSegment.entries[it]) },
        )
    }
}

internal fun categoryIcon(category: String): ImageVector = when (category) {
    "nutrition" -> Icons.Default.Restaurant
    "steps" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "pilates" -> Icons.Default.SelfImprovement
    "flexibility" -> Icons.Default.AccessibilityNew
    "coach" -> Icons.Default.AutoAwesome
    else -> Icons.Default.FitnessCenter
}

internal fun categoryColor(category: String): Color = when (category) {
    "nutrition" -> HedefitColors.Warning
    "steps" -> HedefitColors.Coral
    "pilates" -> HedefitColors.Sleep
    "flexibility" -> HedefitColors.Water
    else -> HedefitColors.Lime
}

private enum class ChallengeFilter(val key: String) { ForYou("for_you"), Popular("popular"), Workout("workout"), Nutrition("nutrition"), Steps("steps"), Pilates("pilates"), Flexibility("flexibility"), Coach("coach") }

private fun filterLabel(filter: ChallengeFilter) = when (filter) {
    ChallengeFilter.ForYou -> tr("Sana Özel", "For You")
    ChallengeFilter.Popular -> tr("Popüler", "Popular")
    ChallengeFilter.Coach -> tr("Fit Koç", "Fit Coach")
    else -> categoryLabel(filter.key)
}

@Composable
fun ChallengeHubScreen(
    padding: PaddingValues,
    header: @Composable () -> Unit,
    hub: ChallengeHubData?,
    busy: Boolean,
    offline: Boolean,
    error: String?,
    coachName: String,
    onRetry: () -> Unit,
    onOpenCoachChallenge: () -> Unit,
    onOpenTemplate: (ChallengeTemplateData) -> Unit,
    onOpenChallenge: (UserChallengeData) -> Unit,
    onJoin: (ChallengeTemplateData) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(ChallengeFilter.ForYou) }
    val today = LocalDate.now()
    ScreenContainer(padding) {
      androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 680.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { header() }
            item { CoachChallengeEntry(coachName, onOpenCoachChallenge) }
            if (offline) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudOff, null, tint = HedefitColors.TextMuted, modifier = Modifier.size(16.dp))
                    Text(tr("Çevrimdışı: son kaydedilen hâli gösteriliyor.", "Offline: showing the last saved state."), color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (hub == null) {
                item {
                    if (busy) Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HedefitColors.Lime) }
                    else UnifiedEmptyState(
                        icon = Icons.Default.EmojiEvents,
                        title = tr("Challenge'lar yüklenemedi", "Couldn't load challenges"),
                        description = error ?: tr("Bağlantını kontrol edip tekrar dene.", "Check your connection and try again."),
                        actionText = tr("Tekrar dene", "Try again"),
                        onAction = onRetry,
                    )
                }
                return@LazyColumn
            }
            val active = hub.active
            if (active.isNotEmpty()) {
                item { HfSectionHeader(tr("Devam eden", "In progress"), "${active.size}/3") }
                items(active, key = { "active-${it.id}" }) { challenge -> ActiveChallengeRow(challenge, today) { onOpenChallenge(challenge) } }
            }
            item {
                HfChipRow { ChallengeFilter.entries.forEach { option -> HfChip(filterLabel(option), filter == option, { filter = option }) } }
            }
            val activeKeys = active.map { it.templateKey }.toSet()
            if (filter == ChallengeFilter.Coach) {
                val mine = hub.challenges.filter { it.plan.source == "coach" }
                if (mine.isEmpty()) item {
                    UnifiedEmptyState(
                        icon = Icons.Default.AutoAwesome,
                        title = tr("Henüz Fit Koç challenge'ın yok", "No Fit Coach challenge yet"),
                        description = tr("Hedefine, zamanına ve ekipmanına göre sana özel bir challenge oluştur.", "Create a challenge built around your goal, time and equipment."),
                        actionText = tr("Oluştur", "Create"),
                        onAction = onOpenCoachChallenge,
                    )
                } else items(mine, key = { "coach-${it.id}" }) { challenge -> ActiveChallengeRow(challenge, today) { onOpenChallenge(challenge) } }
            } else {
                val list = when (filter) {
                    ChallengeFilter.ForYou -> hub.templates.filter { it.recommended }.ifEmpty { hub.templates.filter { it.fit == "fit" } }
                    ChallengeFilter.Popular -> hub.templates.sortedWith(compareByDescending<ChallengeTemplateData> { it.participants }.thenByDescending { it.popular }).take(6)
                    else -> hub.templates.filter { it.plan.category == filter.key }
                }
                if (list.isEmpty()) item {
                    UnifiedEmptyState(icon = Icons.Default.EmojiEvents, title = tr("Bu kategoride challenge yok", "No challenges in this category"), description = tr("Başka bir kategoriye göz at.", "Take a look at another category."))
                } else if (wide) items(list.chunked(2), key = { row -> "tpl-row-${row.first().plan.key}" }) { row ->
                    // Tablet / açık katlanabilir: iki sütun.
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                        row.forEach { template -> Box(Modifier.weight(1f).fillMaxHeight()) { ChallengeTemplateCard(template, active = template.plan.key in activeKeys, onClick = { onOpenTemplate(template) }, onJoin = { onJoin(template) }) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                } else items(list, key = { "tpl-${it.plan.key}" }) { template ->
                    ChallengeTemplateCard(template, active = template.plan.key in activeKeys, onClick = { onOpenTemplate(template) }, onJoin = { onJoin(template) })
                }
            }
        }
      }
    }
}

@Composable
private fun CoachChallengeEntry(coachName: String, onClick: () -> Unit) {
    HedefitCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).background(HedefitColors.Lime, CircleShape), contentAlignment = Alignment.Center) { FitCoachRobotAvatar(Modifier.size(38.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("$coachName ile Challenge Oluştur", "Create a Challenge with $coachName"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(tr("Hedefin, zamanın ve ekipmanına göre kişisel plan; her gün check-in'ine uyum sağlar.", "A personal plan for your goal, time and equipment that adapts to your daily check-in."), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = HedefitColors.Lime)
        }
    }
}

@Composable
internal fun ActiveChallengeRow(challenge: UserChallengeData, today: LocalDate, onClick: () -> Unit) {
    val state = challenge.state(today)
    val color = categoryColor(challenge.plan.category)
    HedefitCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HfIconBadge(categoryIcon(challenge.plan.category), color, 38.dp, 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(challenge.plan.title.text(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            challenge.status == "completed" -> tr("Tamamlandı", "Completed")
                            challenge.status == "abandoned" -> tr("Bırakıldı", "Left")
                            state.todayDone -> tr("Gün ${state.doneDays} / ${state.totalDays} · bugün tamam", "Day ${state.doneDays} / ${state.totalDays} · done today")
                            else -> tr("Gün ${state.currentDay} / ${state.totalDays}", "Day ${state.currentDay} / ${state.totalDays}")
                        },
                        color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.streak > 0 && challenge.status == "active") HfPill("🔥 ${state.streak}", HedefitColors.Warning)
            }
            HfProgressBar(state.percent / 100f, color = color)
        }
    }
}

@Composable
internal fun ChallengeTemplateCard(template: ChallengeTemplateData, active: Boolean, onClick: () -> Unit, onJoin: () -> Unit) {
    val plan = template.plan
    val color = categoryColor(plan.category)
    HedefitCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                HfIconBadge(categoryIcon(plan.category), color, 44.dp, 22.dp, 14.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(plan.title.text(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(plan.description.text(), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            HfChipRow {
                HfTag(difficultyLabel(plan.difficulty))
                HfTag(tr("${plan.days.size} gün", "${plan.days.size} days"))
                minutesLabel(template.minutes)?.let { HfTag(it) }
                if (template.fit != "fit") HfTag(fitLabel(template.fit))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("+${template.rewardXp} XP", color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                    participantsLabel(template.participants)?.let { Text(it, color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall) }
                }
                Text(
                    if (active) tr("Devam et", "Continue") else tr("Katıl", "Join"),
                    color = if (active) HedefitColors.TextPrimary else HedefitColors.OnLime,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) HedefitColors.SurfaceHigh else HedefitColors.Lime)
                        .clickable(onClick = if (active) onClick else onJoin).heightIn(min = 40.dp).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Ana ekrandaki tek, kompakt aktif challenge kartı. Aktif challenge yoksa çağrılmaz. */
@Composable
fun HomeActiveChallengeCard(challenge: UserChallengeData, dayXp: Int, busy: Boolean, onOpen: () -> Unit, onDoTask: () -> Unit, extraCount: Int = 0) {
    val today = LocalDate.now()
    val state = challenge.state(today)
    val task = challenge.nextTask(today)
    HedefitCard(Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("AKTİF CHALLENGE", "ACTIVE CHALLENGE"), color = HedefitColors.Lime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (extraCount > 0) Text(tr("+$extraCount diğer", "+$extraCount more"), color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(challenge.plan.title.text(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (state.todayDone) tr("Gün ${state.doneDays} / ${state.totalDays} · bugün tamam", "Day ${state.doneDays} / ${state.totalDays} · done today")
                        else tr("Gün ${state.currentDay} / ${state.totalDays}", "Day ${state.currentDay} / ${state.totalDays}"),
                        color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.streak > 0) HfPill("🔥 ${state.streak}", HedefitColors.Warning)
            }
            ChallengeDayDots(state.totalDays, challenge.days.map { it.status }, state.todayDone, compact = true)
            if (!state.todayDone && task != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Bugünkü görev", "Today's task"), color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                        Text(com.hedefit.app.data.model.taskLabel(task), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("+$dayXp XP", color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold)
                }
                com.hedefit.app.ui.components.HfPrimaryButton(
                    if (busy) tr("Hazırlanıyor…", "Preparing…") else if (task.kind in setOf("steps", "water", "meals")) tr("İlerlemeyi kontrol et", "Check my progress") else tr("Bugünkü görevi yap", "Do today's task"),
                    { if (!busy) onDoTask() }, Modifier.fillMaxWidth(), Icons.Default.PlayArrow,
                )
            } else if (state.todayDone) {
                Text(tr("Harika, bugünkü görev tamam. Yarın Gün ${minOf(state.doneDays + 1, state.totalDays)} seni bekliyor.", "Nice, today's task is done. Day ${minOf(state.doneDays + 1, state.totalDays)} is waiting tomorrow."), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** ✓ ✓ ● ○ ○ — tamamlanan, toparlanma, bugün ve kalan günler. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChallengeDayDots(totalDays: Int, statuses: List<String>, todayDone: Boolean, compact: Boolean = false) {
    // 14 güne kadar tek satır; daha uzun challenge'larda satır kırılır.
    val size = if (compact) 10.dp else if (totalDays <= 14) 18.dp else 16.dp
    val gap = if (compact) 6.dp else 6.dp
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(totalDays) { index ->
            val status = statuses.getOrNull(index)
            val isToday = status == null && index == statuses.size && !todayDone
            val background = when {
                status == "recovery" -> HedefitColors.Water
                status != null -> HedefitColors.Lime
                isToday -> HedefitColors.Lime.copy(alpha = .35f)
                else -> HedefitColors.SurfaceSoft
            }
            Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
                if (!compact && status != null) Icon(
                    if (status == "recovery") Icons.Default.SelfImprovement else Icons.Default.Check,
                    null, tint = HedefitColors.OnLime, modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
