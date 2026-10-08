package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.ChallengeData
import com.hedefit.app.data.model.ChallengeProgressEntryData
import com.hedefit.app.data.model.ChallengeTemplateData
import com.hedefit.app.data.model.FriendProfileData
import com.hedefit.app.data.model.FriendRequestData
import com.hedefit.app.data.model.FriendUserData
import com.hedefit.app.data.model.FriendsSummaryData
import com.hedefit.app.data.model.LeaderboardEntryData
import com.hedefit.app.data.model.achievementTitle
import com.hedefit.app.gamification.GamificationEngine
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.HfChip
import com.hedefit.app.ui.components.HfChipRow
import com.hedefit.app.ui.components.HfCircleButton
import com.hedefit.app.ui.components.HfNavRow
import com.hedefit.app.ui.components.HfPill
import com.hedefit.app.ui.components.HfPrimaryButton
import com.hedefit.app.ui.components.HfProgressBar
import com.hedefit.app.ui.components.HfSectionHeader
import com.hedefit.app.ui.components.HfSegmented
import com.hedefit.app.ui.components.HfStatTile
import com.hedefit.app.ui.components.HfTag
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.components.UnifiedEmptyState
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.theme.HedefitColors

private fun displayNameOf(user: FriendUserData) = user.displayName?.takeIf { it.isNotBlank() } ?: user.username?.let { "@$it" } ?: tr("Sporcu", "Athlete")

/**
 * Keşfet > Topluluk: fitness odaklı arkadaş sistemi. Bilerek akış, yorum, gönderi ya da mesajlaşma YOK;
 * yalnızca arkadaşlar, istekler, meydan okumalar ve challenge sıralamaları.
 */
@Composable
fun CommunityScreen(
    padding: PaddingValues,
    header: @Composable () -> Unit,
    isGuest: Boolean,
    summary: FriendsSummaryData?,
    busy: Boolean,
    searchResults: List<FriendUserData>,
    searchBusy: Boolean,
    discoverable: Boolean?,
    shareProgress: Boolean?,
    leaderboard: List<LeaderboardEntryData>,
    friendChallenges: List<ChallengeData>,
    progress: List<ChallengeProgressEntryData>,
    progressBusy: Boolean,
    openProgressId: String?,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSendRequest: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDiscoverableChange: (Boolean) -> Unit,
    onShareProgressChange: (Boolean) -> Unit,
    onOpenFriend: (FriendUserData) -> Unit,
    onAcceptChallenge: (String) -> Unit,
    onDeclineChallenge: (String) -> Unit,
    onLeaveChallenge: (String) -> Unit,
    onOpenProgress: (String) -> Unit,
    onCloseProgress: () -> Unit,
    onSaveAccount: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    ScreenContainer(padding) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { header() }
            if (isGuest) {
                item {
                    UnifiedEmptyState(
                        icon = Icons.Default.Lock,
                        title = tr("Topluluk için hesabını kaydet", "Save your account to use Community"),
                        description = tr("Arkadaş eklemek ve birlikte challenge yapmak için kalıcı bir hesap gerekir. İlerlemen korunur.", "Adding friends and doing challenges together needs a saved account. Your progress is kept."),
                        actionText = tr("Hesabı kaydet", "Save account"),
                        onAction = onSaveAccount,
                    )
                }
                return@LazyColumn
            }
            item { HfSegmented(listOf(tr("Arkadaşlar", "Friends"), tr("Challenge'lar", "Challenges"), tr("Sıralama", "Ranking")), tab, { tab = it }) }
            when (tab) {
                0 -> {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { value: String -> query = value.take(20); if (query.trim().length >= 2) onSearch(query) else onClearSearch() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(tr("Arkadaş ekle: kullanıcı adıyla ara…", "Add a friend: search by username…")) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = HedefitColors.TextSecondary) },
                            trailingIcon = { if (searchBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = HedefitColors.Lime) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HedefitColors.Lime, unfocusedBorderColor = HedefitColors.Divider, focusedContainerColor = HedefitColors.Surface, unfocusedContainerColor = HedefitColors.Surface),
                        )
                    }
                    if (searchResults.isNotEmpty()) {
                        item { HfSectionHeader(tr("Sonuçlar", "Results")) }
                        items(searchResults, key = { "search-${it.id}" }) { user ->
                            HfNavRow(Icons.Default.Person, HedefitColors.Lime, displayNameOf(user), onClick = null) {
                                HfCircleButton(Icons.Default.PersonAdd, tr("Ekle", "Add"), { user.username?.let(onSendRequest); query = ""; onClearSearch() }, tint = HedefitColors.Lime)
                            }
                        }
                    }
                    val incoming = summary?.incomingRequests.orEmpty()
                    if (incoming.isNotEmpty()) {
                        item { HfSectionHeader(tr("Arkadaş istekleri", "Friend requests"), "${incoming.size}") }
                        items(incoming, key = { "in-${it.id}" }) { request ->
                            HfNavRow(Icons.Default.Person, HedefitColors.Lime, displayNameOf(request.user), onClick = null) {
                                HfCircleButton(Icons.Default.Check, tr("Kabul et", "Accept"), { onAccept(request.id) }, tint = HedefitColors.Lime)
                                HfCircleButton(Icons.Default.Close, tr("Reddet", "Decline"), { onDecline(request.id) }, tint = HedefitColors.Coral)
                            }
                        }
                    }
                    val friends = summary?.friends.orEmpty()
                    item { HfSectionHeader(tr("Arkadaşlarım", "My friends"), if (friends.isNotEmpty()) "${friends.size}" else null) }
                    if (summary == null && busy) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HedefitColors.Lime) } }
                    else if (friends.isEmpty()) item {
                        UnifiedEmptyState(Icons.Default.Group, tr("Henüz arkadaşın yok", "No friends yet"), tr("Yukarıdan kullanıcı adıyla ara ve istek gönder. Birlikte challenge yapmak motivasyonu ikiye katlar.", "Search a username above and send a request. Doing challenges together doubles the motivation."))
                    } else items(friends, key = { "fr-${it.id}" }) { friend ->
                        HfNavRow(Icons.Default.Person, HedefitColors.Lime, displayNameOf(friend.user), onClick = { onOpenFriend(friend.user) })
                    }
                    val outgoing = summary?.outgoingRequests.orEmpty()
                    if (outgoing.isNotEmpty()) {
                        item { HfSectionHeader(tr("Gönderilen istekler", "Sent requests")) }
                        items(outgoing, key = { "out-${it.id}" }) { request ->
                            HfNavRow(Icons.Default.Person, HedefitColors.TextSecondary, displayNameOf(request.user), onClick = null) { HfPill(tr("Bekliyor", "Pending"), HedefitColors.TextSecondary) }
                        }
                    }
                    item { HfSectionHeader(tr("Gizlilik", "Privacy")) }
                    if (discoverable != null) item {
                        HfNavRow(Icons.Default.Search, HedefitColors.Lime, tr("Aramada görün", "Appear in search"), onClick = { onDiscoverableChange(!discoverable) }, chevron = false) {
                            Switch(discoverable, onDiscoverableChange, colors = SwitchDefaults.colors(checkedThumbColor = HedefitColors.OnLime, checkedTrackColor = HedefitColors.Lime))
                        }
                    }
                    if (shareProgress != null) item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HfNavRow(Icons.Default.Visibility, HedefitColors.Lime, tr("İlerlememi arkadaşlarım görsün", "Let friends see my progress"), onClick = { onShareProgressChange(!shareProgress) }, chevron = false) {
                                Switch(shareProgress, onShareProgressChange, colors = SwitchDefaults.colors(checkedThumbColor = HedefitColors.OnLime, checkedTrackColor = HedefitColors.Lime))
                            }
                            Text(
                                tr("Arkadaşların yalnızca level, seri, challenge ve rozetlerini görür. Kilo, kalori, uyku, beslenme, check-in ve döngü bilgilerin hiçbir zaman paylaşılmaz.", "Friends only see your level, streak, challenges and badges. Your weight, calories, sleep, nutrition, check-ins and cycle data are never shared."),
                                color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                1 -> {
                    val invites = friendChallenges.filter { it.myStatus == "invited" || it.myStatus == "expired" }
                    val active = friendChallenges.filter { it.myStatus == "joined" }
                    if (invites.isNotEmpty()) {
                        item { HfSectionHeader(tr("Meydan okumalar", "Invites")) }
                        items(invites, key = { "inv-${it.id}" }) { challenge -> InviteChallengeCard(challenge, com.hedefit.app.ui.i18n.AppLang.en, onAccept = { onAcceptChallenge(challenge.id) }, onDecline = { onDeclineChallenge(challenge.id) }) }
                    }
                    item { HfSectionHeader(tr("Aktif arkadaş challenge'ları", "Active friend challenges")) }
                    if (active.isEmpty()) item {
                        UnifiedEmptyState(Icons.Default.Groups, tr("Aktif arkadaş challenge'ı yok", "No active friend challenge"), tr("Bir arkadaşının profilinden Meydan Oku'ya dokun; birlikte tamamlayın ya da rekabet edin.", "Tap Challenge on a friend's profile — complete it together or compete."))
                    } else items(active, key = { "act-${it.id}" }) { challenge ->
                        ActiveChallengeCard(challenge, com.hedefit.app.ui.i18n.AppLang.en, onClick = { onOpenProgress(challenge.id) }, onLeaveOrCancel = { onLeaveChallenge(challenge.id) })
                    }
                }
                else -> {
                    item { HfSectionHeader(tr("Bu haftanın XP sıralaması", "This week's XP ranking")) }
                    if (leaderboard.isEmpty()) item {
                        UnifiedEmptyState(Icons.Default.EmojiEvents, tr("Henüz sıralama yok", "No ranking yet"), tr("Arkadaş ekleyince haftalık XP sıralaması burada görünür.", "Add friends to see the weekly XP ranking here."))
                    } else items(leaderboard, key = { "lb-${it.user.id}" }) { entry -> RankRow(entry.rank, entry.user, entry.isCurrentUser, "${entry.weeklyXp} XP", com.hedefit.app.ui.i18n.AppLang.en) }
                    item {
                        Text(tr("Challenge sıralamaları için Challenge'lar sekmesinde bir challenge'a dokun.", "For challenge rankings, tap a challenge in the Challenges tab."), color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
    friendChallenges.firstOrNull { it.id == openProgressId }?.let { open ->
        ChallengeProgressDialog(open, progress, progressBusy, com.hedefit.app.ui.i18n.AppLang.en, onDismiss = onCloseProgress)
    }
}

/** Arkadaş profili: gizlilik izinleri dahilinde level, seri, challenge ve rozetler. Sağlık verisi yoktur. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendProfileSheet(
    user: FriendUserData,
    profile: FriendProfileData?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onChallenge: () -> Unit,
    onRemove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmRemove by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = HedefitColors.Background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(HedefitColors.Lime.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(displayNameOf(user).trimStart('@').take(1).uppercase(), color = HedefitColors.Lime, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayNameOf(user), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    user.username?.let { Text("@$it", color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                }
            }
            when {
                busy || profile == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HedefitColors.Lime) }
                !profile.shared -> Text(tr("Bu arkadaşın ilerlemesini paylaşmıyor.", "This friend keeps their progress private."), color = HedefitColors.TextSecondary)
                else -> {
                    val level = GamificationEngine.levelFor(profile.totalXp)
                    HedefitCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Seviye ${level.level}", "Level ${level.level}"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                Text("${profile.totalXp} XP", color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold)
                            }
                            HfProgressBar(level.progress)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HfStatTile(tr("En iyi seri", "Best streak"), "🔥 ${profile.bestStreak}", Modifier.weight(1f))
                        HfStatTile(tr("Challenge", "Challenges"), "${profile.completedChallenges}", Modifier.weight(1f), sub = tr("tamamlandı", "completed"))
                    }
                    if (profile.activeChallenges.isNotEmpty()) {
                        HfSectionHeader(tr("Aktif challenge'lar", "Active challenges"))
                        profile.activeChallenges.forEach { (title, done, total) ->
                            HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row { Text(title.text(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("$done/$total", color = HedefitColors.TextSecondary) }
                                    HfProgressBar(if (total > 0) done / total.toFloat() else 0f)
                                }
                            }
                        }
                    }
                    if (profile.achievements.isNotEmpty()) {
                        HfSectionHeader(tr("Rozetler", "Achievements"))
                        HfChipRow { profile.achievements.take(8).forEach { HfTag("🏅 ${achievementTitle(it)}") } }
                    }
                }
            }
            HfPrimaryButton(tr("Meydan Oku", "Challenge"), onChallenge, Modifier.fillMaxWidth(), Icons.Default.EmojiEvents)
            TextButton(onClick = { confirmRemove = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.PersonRemove, null, tint = HedefitColors.TextMuted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(tr("Arkadaşlıktan çıkar", "Remove friend"), color = HedefitColors.TextMuted)
            }
        }
    }
    if (confirmRemove) com.hedefit.app.ui.components.AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(tr("Arkadaşlıktan çıkarılsın mı?", "Remove this friend?")) },
        text = { Text(tr("Ortak challenge'larınız kişisel challenge olarak devam eder; kazanılan XP korunur.", "Your shared challenges continue as personal ones; earned XP is kept.")) },
        confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text(tr("Çıkar", "Remove"), color = HedefitColors.Coral) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(tr("Vazgeç", "Cancel")) } },
    )
}

/** Meydan Oku: challenge seç + mod (Birlikte Tamamla / Rekabet Et) + arkadaş(lar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendChallengeSheet(
    templates: List<ChallengeTemplateData>,
    friends: List<FriendRequestData>,
    initialTemplateKey: String?,
    initialFriendId: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (templateKey: String, mode: String, friendIds: List<String>) -> Unit,
) {
    val eligible = templates.filter { it.plan.days.size <= 30 }
    var templateKey by rememberSaveable { mutableStateOf(initialTemplateKey ?: eligible.firstOrNull()?.plan?.key) }
    var mode by rememberSaveable { mutableStateOf("together") }
    var selected by remember { mutableStateOf(setOfNotNull(initialFriendId)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = HedefitColors.Background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("Meydan Oku", "Challenge a friend"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            HfSectionHeader(tr("Mod", "Mode"))
            HfSegmented(listOf(tr("Birlikte Tamamla", "Complete Together"), tr("Rekabet Et", "Compete")), if (mode == "together") 0 else 1, { mode = if (it == 0) "together" else "compete" })
            Text(
                if (mode == "together") tr("Amaç ikinizin de challenge'ı bitirmesi. Bitirince ikiniz de bonus XP kazanırsınız.", "The goal is for both of you to finish. You both earn bonus XP when you do.")
                else tr("Aynı challenge'da kim daha istikrarlı? Adım challenge'larında toplam adım, diğerlerinde tamamlanan gün sayılır.", "Who's more consistent? Step challenges count total steps; others count completed days."),
                color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
            )
            HfSectionHeader("Challenge")
            HfChipRow { eligible.forEach { template -> HfChip(template.plan.title.text(), templateKey == template.plan.key, { templateKey = template.plan.key }) } }
            HfSectionHeader(tr("Arkadaşlar", "Friends"))
            if (friends.isEmpty()) Text(tr("Önce Topluluk'tan arkadaş ekle.", "Add a friend in Community first."), color = HedefitColors.TextMuted)
            friends.forEach { friend ->
                val checked = friend.user.id in selected
                HfNavRow(Icons.Default.Person, if (checked) HedefitColors.Lime else HedefitColors.TextSecondary, displayNameOf(friend.user), onClick = { selected = if (checked) selected - friend.user.id else selected + friend.user.id }, chevron = false) {
                    if (checked) Icon(Icons.Default.Check, null, tint = HedefitColors.Lime)
                }
            }
            Spacer(Modifier.height(4.dp))
            HfPrimaryButton(
                if (busy) tr("Gönderiliyor…", "Sending…") else tr("Meydan okumayı gönder", "Send challenge"),
                { val key = templateKey; if (!busy && key != null && selected.isNotEmpty()) onSend(key, mode, selected.toList()) },
                Modifier.fillMaxWidth(), Icons.Default.EmojiEvents, enabled = templateKey != null && selected.isNotEmpty(),
            )
        }
    }
}
