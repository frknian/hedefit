package com.hedefit.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.ChallengeData
import com.hedefit.app.data.model.ChallengeProgressEntryData
import com.hedefit.app.data.model.FriendRequestData
import com.hedefit.app.ui.components.AlertDialog
import com.hedefit.app.ui.components.*
import com.hedefit.app.ui.theme.HedefitColors
import java.time.Duration
import java.time.Instant
import kotlin.math.max

private val METRIC_KEYS = listOf("xp", "workouts", "distance_km")
private val Gold = Color(0xFFFFD54A)
private val Silver = Color(0xFFC7CDD6)
private val Bronze = Color(0xFFD98A55)

private fun metricLabel(metric: String, en: Boolean): String = when (metric) {
    "xp" -> "XP"
    "workouts" -> if (en) "Workouts" else "Antrenman"
    "steps" -> if (en) "Steps" else "Adım"
    "challenge_days" -> if (en) "Challenge days" else "Challenge günü"
    else -> if (en) "Distance (km)" else "Mesafe (km)"
}

private fun metricIcon(metric: String): ImageVector = when (metric) {
    "xp" -> Icons.Default.Bolt
    "workouts" -> Icons.Default.FitnessCenter
    "steps" -> Icons.Default.DirectionsWalk
    "challenge_days" -> Icons.Default.EmojiEvents
    else -> Icons.Default.DirectionsRun
}

private fun metricColor(metric: String): Color = when (metric) {
    "xp" -> HedefitColors.Lime
    "workouts" -> HedefitColors.Coral
    "steps" -> HedefitColors.Coral
    "challenge_days" -> HedefitColors.Lime
    else -> HedefitColors.Water
}

private fun progressText(metric: String, value: Double): String = when (metric) {
    "workouts" -> "${value.toInt()}"
    "steps" -> com.hedefit.app.ui.i18n.tr("%,d adım".format(java.util.Locale.US, value.toInt()).replace(',', '.'), "%,d steps".format(java.util.Locale.US, value.toInt()))
    "challenge_days" -> com.hedefit.app.ui.i18n.tr("${value.toInt()} gün", "${value.toInt()} days")
    "distance_km" -> "%.1f km".format(value)
    else -> "${value.toInt()} XP"
}

private fun targetText(challenge: ChallengeData): String = when (challenge.metric) {
    "distance_km" -> "%.0f".format(challenge.targetValue)
    "steps" -> "%,d".format(java.util.Locale.US, challenge.targetValue.toInt()).let { if (com.hedefit.app.ui.i18n.AppLang.en) it else it.replace(',', '.') }
    else -> "${challenge.targetValue.toInt()}"
}

/** Birlikte Tamamla / Rekabet Et etiketi (katalog challenge'ları için). */
internal fun challengeModeLabel(challenge: ChallengeData): String? = if (challenge.templateKey == null) null
    else if (challenge.mode == "together") com.hedefit.app.ui.i18n.tr("Birlikte Tamamla", "Complete Together") else com.hedefit.app.ui.i18n.tr("Rekabet Et", "Compete")

/** 0f–1f: meydan okuma penceresinde geçen süre oranı (canlı ilerleme çekmeden görsel ipucu). */
private fun timeElapsedFraction(challenge: ChallengeData): Float = runCatching {
    val start = Instant.parse(challenge.startsAt)
    val end = Instant.parse(challenge.endsAt)
    val now = Instant.now()
    val total = Duration.between(start, end).toMillis().toFloat()
    if (total <= 0f) return@runCatching 0f
    (Duration.between(start, now).toMillis() / total).coerceIn(0f, 1f)
}.getOrDefault(0f)

private fun daysLeftText(challenge: ChallengeData, en: Boolean): String = runCatching {
    val end = Instant.parse(challenge.endsAt)
    val hours = Duration.between(Instant.now(), end).toHours()
    when {
        hours <= 0 -> if (en) "Ended" else "Bitti"
        hours < 24 -> if (en) "${hours}h left" else "${hours}sa kaldı"
        else -> { val d = hours / 24; if (en) "${d}d left" else "$d gün kaldı" }
    }
}.getOrDefault("")

@Composable
fun ChallengesScreen(
    challenges: List<ChallengeData>,
    busy: Boolean,
    creating: Boolean,
    friends: List<FriendRequestData>,
    progress: List<ChallengeProgressEntryData>,
    progressBusy: Boolean,
    activeChallengeId: String?,
    language: String,
    onBack: () -> Unit,
    onCreate: (title: String, metric: String, targetValue: Double, days: Int, friendIds: List<String>) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onLeaveOrCancel: (String) -> Unit,
    onOpenProgress: (String) -> Unit,
    onCloseProgress: () -> Unit,
) {
    val en = language == "en"
    var showCreate by remember { mutableStateOf(false) }

    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HfScreenHeader(if (en) "Challenges" else "Meydan okumalar", onBack = onBack, backLabel = if (en) "Back" else "Geri") {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = HedefitColors.Lime)
                    HfCircleButton(Icons.Default.Add, if (en) "New challenge" else "Yeni meydan okuma", { showCreate = true }, tint = HedefitColors.Lime)
                }
            }
            val invites = challenges.filter { it.myStatus == "invited" || it.myStatus == "expired" }
            val active = challenges.filter { it.myStatus == "joined" }
            if (invites.isNotEmpty()) {
                item { HfSectionHeader(if (en) "Invites" else "Davetler") }
                itemsIndexed(invites, key = { _, it -> "inv-${it.id}" }) { index, challenge ->
                    StaggeredEntrance(index) {
                        InviteChallengeCard(challenge, en, onAccept = { onAccept(challenge.id) }, onDecline = { onDecline(challenge.id) })
                    }
                }
            }
            item { HfSectionHeader(if (en) "Active" else "Aktif") }
            if (active.isEmpty()) {
                item {
                    AnimatedEmptyState(en, onCreate = { showCreate = true })
                }
            } else {
                itemsIndexed(active, key = { _, it -> "act-${it.id}" }) { index, challenge ->
                    StaggeredEntrance(index) {
                        ActiveChallengeCard(
                            challenge = challenge,
                            en = en,
                            onClick = { onOpenProgress(challenge.id) },
                            onLeaveOrCancel = { onLeaveOrCancel(challenge.id) },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateChallengeDialog(friends = friends, en = en, creating = creating, onDismiss = { showCreate = false }, onCreate = { title, metric, target, days, friendIds ->
            onCreate(title, metric, target, days, friendIds)
            showCreate = false
        })
    }

    val open = challenges.firstOrNull { it.id == activeChallengeId }
    if (open != null) {
        ChallengeProgressDialog(open, progress, progressBusy, en, onDismiss = onCloseProgress)
    }
}

/** Liste öğelerinin sırayla (kademeli) belirmesi için hafif giriş animasyonu. */
@Composable
private fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(280, delayMillis = (index * 45).coerceAtMost(240))) +
            slideInVertically(tween(280, delayMillis = (index * 45).coerceAtMost(240)), initialOffsetY = { it / 4 }),
    ) { content() }
}

@Composable
private fun AnimatedEmptyState(en: Boolean, onCreate: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "empty-pulse")
    val scale by infinite.animateFloat(
        initialValue = 0.96f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(64.dp).graphicsLayerScale(scale)
                .background(HedefitColors.Coral.copy(alpha = .14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.EmojiEvents, null, tint = HedefitColors.Coral, modifier = Modifier.size(30.dp)) }
        Text(if (en) "No challenges yet" else "Henüz meydan okuma yok", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (en) "Create one to compete with friends this week." else "Bu hafta arkadaşlarınla yarışmak için bir tane oluştur.",
            style = MaterialTheme.typography.bodyMedium, color = HedefitColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        HfPrimaryButton(if (en) "New challenge" else "Yeni meydan okuma", onCreate, icon = Icons.Default.Add)
    }
}

private fun Modifier.graphicsLayerScale(scale: Float) = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

@Composable
internal fun InviteChallengeCard(challenge: ChallengeData, en: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    val color = metricColor(challenge.metric)
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HfIconBadge(metricIcon(challenge.metric), color, 42.dp, 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("${metricLabel(challenge.metric, en)} • ${targetText(challenge)} ${if (en) "target" else "hedef"}", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                HfPill(if (challenge.myStatus == "expired") (if (en) "Expired" else "Süresi doldu") else challengeModeLabel(challenge) ?: (if (en) "Invited" else "Davet"), color = if (challenge.myStatus == "expired") HedefitColors.TextMuted else color)
            }
            if (challenge.myStatus == "expired") Text(if (en) "This invite expired after 72 hours." else "Bu davetin süresi 72 saat sonunda doldu.", color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HfPrimaryButton(if (en) "Accept" else "Kabul et", onAccept, modifier = Modifier.weight(1f), icon = Icons.Default.Check)
                HfPrimaryButton(if (en) "Decline" else "Reddet", onDecline, modifier = Modifier.weight(1f), secondary = true, icon = Icons.Default.Close)
            }
        }
    }
}

@Composable
internal fun ActiveChallengeCard(challenge: ChallengeData, en: Boolean, onClick: () -> Unit, onLeaveOrCancel: () -> Unit) {
    val color = metricColor(challenge.metric)
    val elapsed by animateFloatAsState(timeElapsedFraction(challenge), animationSpec = tween(600), label = "elapsed")
    HedefitCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HfIconBadge(metricIcon(challenge.metric), color, 42.dp, 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        listOfNotNull(challengeModeLabel(challenge), "${metricLabel(challenge.metric, en)} • ${targetText(challenge)} ${if (en) "target" else "hedef"}", "${challenge.participantCount} ${if (en) "joined" else "katılımcı"}").joinToString(" • "),
                        color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                }
                HfCircleButton(
                    if (challenge.isCreator) Icons.Default.Delete else Icons.Default.ExitToApp,
                    if (challenge.isCreator) (if (en) "Cancel" else "İptal et") else (if (en) "Leave" else "Ayrıl"),
                    onLeaveOrCancel,
                    tint = HedefitColors.TextMuted,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { elapsed },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = HedefitColors.SurfaceHigh,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(daysLeftText(challenge, en), color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(if (en) "See ranking" else "Sıralamayı gör", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun CreateChallengeDialog(
    friends: List<FriendRequestData>,
    en: Boolean,
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Double, Int, List<String>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var metric by remember { mutableStateOf("xp") }
    var target by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("7") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val targetValue = target.replace(',', '.').toDoubleOrNull()
    val daysValue = days.toIntOrNull()
    val valid = title.trim().isNotEmpty() && targetValue != null && targetValue > 0 && daysValue != null && daysValue in 1..30
    val accent = metricColor(metric)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HfIconBadge(Icons.Default.EmojiEvents, HedefitColors.Coral, 34.dp, 18.dp)
                Text(if (en) "New challenge" else "Yeni meydan okuma")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title, { title = it.take(60) },
                    label = { Text(if (en) "Title" else "Başlık") }, singleLine = true,
                    placeholder = { Text(if (en) "e.g. 50km together" else "ör. birlikte 50km") },
                    colors = challengeFieldColors(accent),
                )
                Text(if (en) "Metric" else "Metrik", style = MaterialTheme.typography.labelLarge, color = HedefitColors.TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    METRIC_KEYS.forEach { key -> MetricOption(key, metric == key, en, Modifier.weight(1f)) { metric = key } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        target, { target = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (en) "Target" else "Hedef") }, singleLine = true,
                        colors = challengeFieldColors(accent),
                    )
                    OutlinedTextField(
                        days, { days = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (en) "Days" else "Gün") }, singleLine = true,
                        colors = challengeFieldColors(accent),
                    )
                }
                AnimatedVisibility(visible = friends.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (en) "Invite friends" else "Arkadaş davet et", style = MaterialTheme.typography.labelLarge, color = HedefitColors.TextSecondary)
                        friends.forEach { friend -> FriendInviteRow(friend, friend.user.id in selected, accent) { on ->
                            selected = if (on) selected + friend.user.id else selected - friend.user.id
                        } }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = {
            Button(
                enabled = valid && !creating,
                onClick = { onCreate(title.trim(), metric, requireNotNull(targetValue), requireNotNull(daysValue), selected.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = HedefitColors.OnLime),
            ) {
                if (creating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HedefitColors.OnLime)
                else Text(if (en) "Create" else "Oluştur")
            }
        },
    )
}

@Composable
private fun challengeFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = HedefitColors.Divider,
    focusedContainerColor = HedefitColors.Surface,
    unfocusedContainerColor = HedefitColors.Surface,
)

@Composable
private fun MetricOption(key: String, selected: Boolean, en: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = metricColor(key)
    val bgAlpha by animateFloatAsState(if (selected) .22f else 0f, tween(180), label = "metric-bg")
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = bgAlpha))
            .then(if (selected) Modifier.border(1.5.dp, color, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(metricIcon(key), null, tint = if (selected) color else HedefitColors.TextSecondary, modifier = Modifier.size(20.dp))
        Text(
            metricLabel(key, en), style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else HedefitColors.TextSecondary,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun FriendInviteRow(friend: FriendRequestData, checked: Boolean, accent: Color, onCheckedChange: (Boolean) -> Unit) {
    val name = friend.user.displayName?.takeIf { it.isNotBlank() } ?: "@${friend.user.username}"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (checked) accent.copy(alpha = .10f) else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .then(Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(HedefitColors.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = HedefitColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = accent))
    }
}

@Composable
internal fun ChallengeProgressDialog(challenge: ChallengeData, progress: List<ChallengeProgressEntryData>, busy: Boolean, en: Boolean, onDismiss: () -> Unit) {
    val accent = metricColor(challenge.metric)
    val maxValue = max(1.0, progress.maxOfOrNull { it.progressValue } ?: 1.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HfIconBadge(metricIcon(challenge.metric), accent, 34.dp, 18.dp)
                Text(challenge.title)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = accent)
                    }
                } else if (progress.isEmpty()) {
                    Text(if (en) "No data yet." else "Henüz veri yok.", color = HedefitColors.TextSecondary)
                } else {
                    progress.forEach { entry ->
                        ProgressRankRow(entry, challenge.metric, accent, entry.progressValue / maxValue)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (en) "Close" else "Kapat") } },
    )
}

@Composable
private fun ProgressRankRow(entry: ChallengeProgressEntryData, metric: String, accent: Color, fraction: Double) {
    val name = entry.user.displayName?.takeIf { it.isNotBlank() } ?: "@${entry.user.username}"
    val medal = when (entry.rank) { 1 -> Gold; 2 -> Silver; 3 -> Bronze; else -> null }
    val ringColor = medal ?: if (entry.isCurrentUser) accent else HedefitColors.SurfaceHigh
    val animatedFraction by animateFloatAsState(fraction.toFloat().coerceIn(0f, 1f), tween(700), label = "progress")
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(ringColor.copy(alpha = if (medal != null) 1f else .18f), CircleShape), contentAlignment = Alignment.Center) {
                    if (medal != null) Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFF1A1200), modifier = Modifier.size(15.dp))
                    else Text("${entry.rank}", color = if (entry.isCurrentUser) accent else HedefitColors.TextSecondary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    name + if (entry.isCurrentUser) " •" else "",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (entry.isCurrentUser) FontWeight.ExtraBold else FontWeight.Bold,
                )
                Text(progressText(metric, entry.progressValue), color = accent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = HedefitColors.SurfaceHigh,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    list: List<ChallengeData>,
    key: (Int, ChallengeData) -> Any,
    itemContent: @Composable (Int, ChallengeData) -> Unit,
) {
    items(count = list.size, key = { index -> key(index, list[index]) }) { index -> itemContent(index, list[index]) }
}
