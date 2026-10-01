package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.FeedItemData
import com.hedefit.app.data.model.FriendRequestData
import com.hedefit.app.data.model.FriendUserData
import com.hedefit.app.data.model.FriendsSummaryData
import com.hedefit.app.data.model.LeaderboardEntryData
import com.hedefit.app.ui.components.*
import com.hedefit.app.ui.theme.HedefitColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FriendsScreen(
    summary: FriendsSummaryData?,
    busy: Boolean,
    leaderboard: List<LeaderboardEntryData>,
    leaderboardBusy: Boolean,
    feed: List<FeedItemData>,
    feedBusy: Boolean,
    searchResults: List<FriendUserData>,
    searchBusy: Boolean,
    discoverable: Boolean?,
    language: String,
    onBack: () -> Unit,
    onDiscoverableChange: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSendRequest: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOpenChallenges: () -> Unit,
) {
    val en = language == "en"
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HfScreenHeader(if (en) "Friends" else "Arkadaşlar", onBack = onBack, backLabel = if (en) "Back" else "Geri") {
                    if (busy || leaderboardBusy || feedBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = HedefitColors.Lime)
                }
            }
            item {
                HfNavRow(
                    icon = Icons.Default.EmojiEvents,
                    tint = HedefitColors.Coral,
                    title = if (en) "Challenges" else "Meydan okumalar",
                    subtitle = if (en) "Weekly goals with friends" else "Arkadaşlarla haftalık ortak hedefler",
                    onClick = onOpenChallenges,
                )
            }
            item {
                HfSegmented(
                    options = listOf(if (en) "Friends" else "Arkadaşlar", if (en) "Feed" else "Akış", if (en) "Leaderboard" else "Sıralama"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }
            if (tab == 0) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { value: String ->
                            query = value.take(20)
                            if (query.trim().length >= 2) onSearch(query) else onClearSearch()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (en) "Search by username…" else "Kullanıcı adıyla ara…") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = HedefitColors.TextSecondary) },
                        trailingIcon = { if (searchBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = HedefitColors.Lime) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HedefitColors.Lime,
                            unfocusedBorderColor = HedefitColors.Divider,
                            focusedContainerColor = HedefitColors.Surface,
                            unfocusedContainerColor = HedefitColors.Surface,
                        ),
                    )
                }
                if (discoverable != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HfNavRow(
                                icon = Icons.Default.Search,
                                tint = HedefitColors.Lime,
                                title = if (en) "Appear in search" else "Aramada görün",
                                onClick = { onDiscoverableChange(!discoverable) },
                                chevron = false,
                            ) {
                                Switch(
                                    checked = discoverable,
                                    onCheckedChange = onDiscoverableChange,
                                    colors = SwitchDefaults.colors(checkedThumbColor = HedefitColors.OnLime, checkedTrackColor = HedefitColors.Lime),
                                )
                            }
                            Text(
                                if (en) "When off, you won't show up in username search. Anyone who knows your exact username can still send you a friend request; current friends are unaffected."
                                else "Kapalıyken kullanıcı adı aramasında çıkmazsın. Kullanıcı adını tam olarak bilen biri yine de sana arkadaşlık isteği gönderebilir; mevcut arkadaşların etkilenmez.",
                                color = HedefitColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                if (searchResults.isNotEmpty()) {
                    item { HfSectionHeader(if (en) "Results" else "Sonuçlar") }
                    items(searchResults, key = { "search-${it.id}" }) { user ->
                        HfNavRow(
                            icon = Icons.Default.Person,
                            tint = HedefitColors.Lime,
                            title = user.displayName?.takeIf { it.isNotBlank() } ?: "@${user.username}",
                            subtitle = user.username?.let { "@$it" },
                            onClick = null,
                        ) {
                            HfPrimaryButton(
                                text = if (en) "Add" else "Ekle",
                                onClick = { user.username?.let { onSendRequest(it) }; query = ""; onClearSearch() },
                                modifier = Modifier.height(36.dp),
                            )
                        }
                    }
                }
                val incoming = summary?.incomingRequests.orEmpty()
                if (incoming.isNotEmpty()) {
                    item { HfSectionHeader(if (en) "Incoming requests" else "Gelen istekler") }
                    items(incoming, key = { "in-${it.id}" }) { request ->
                        FriendRequestRow(request, en) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HfCircleButton(Icons.Default.Check, if (en) "Accept" else "Kabul et", { onAccept(request.id) }, tint = HedefitColors.Lime)
                                HfCircleButton(Icons.Default.Close, if (en) "Decline" else "Reddet", { onDecline(request.id) }, tint = HedefitColors.Coral)
                            }
                        }
                    }
                }
                val outgoing = summary?.outgoingRequests.orEmpty()
                if (outgoing.isNotEmpty()) {
                    item { HfSectionHeader(if (en) "Sent requests" else "Gönderilen istekler") }
                    items(outgoing, key = { "out-${it.id}" }) { request ->
                        FriendRequestRow(request, en) {
                            HfPill(if (en) "Pending" else "Bekliyor", color = HedefitColors.TextSecondary)
                        }
                    }
                }
                val friends = summary?.friends.orEmpty()
                item { HfSectionHeader(if (en) "Your friends" else "Arkadaşların") }
                if (friends.isEmpty() && incoming.isEmpty() && outgoing.isEmpty() && searchResults.isEmpty()) {
                    item {
                        UnifiedEmptyState(
                            icon = Icons.Default.Group,
                            title = if (en) "No friends yet" else "Henüz arkadaşın yok",
                            description = if (en) "Search for a username above to send a friend request." else "Arkadaşlık isteği göndermek için yukarıdan kullanıcı adı ara.",
                        )
                    }
                } else {
                    items(friends, key = { "fr-${it.id}" }) { friend ->
                        FriendRequestRow(friend, en) {
                            HfCircleButton(Icons.Default.PersonRemove, if (en) "Remove" else "Çıkar", { onRemove(friend.id) }, tint = HedefitColors.TextSecondary)
                        }
                    }
                }
            } else if (tab == 1) {
                if (feed.isEmpty()) {
                    item {
                        UnifiedEmptyState(
                            icon = Icons.Default.Timeline,
                            title = if (en) "No activity yet" else "Henüz aktivite yok",
                            description = if (en) "Your friends' workouts and achievements will show up here." else "Arkadaşlarının antrenman ve başarımları burada görünecek.",
                        )
                    }
                } else {
                    items(feed, key = { it.id }) { item -> FeedRow(item, en) }
                }
            } else {
                if (leaderboard.isEmpty()) {
                    item {
                        UnifiedEmptyState(
                            icon = Icons.Default.EmojiEvents,
                            title = if (en) "No ranking yet" else "Henüz sıralama yok",
                            description = if (en) "Add friends to see this week's XP leaderboard." else "Bu haftaki XP sıralamasını görmek için arkadaş ekle.",
                        )
                    }
                } else {
                    items(leaderboard, key = { it.user.id }) { entry -> RankRow(entry.rank, entry.user, entry.isCurrentUser, "${entry.weeklyXp} XP", en) }
                }
            }
        }
    }
}

@Composable
private fun FriendRequestRow(request: FriendRequestData, en: Boolean, trailing: @Composable RowScope.() -> Unit) {
    HfNavRow(
        icon = Icons.Default.Person,
        tint = HedefitColors.Lime,
        title = request.user.displayName?.takeIf { it.isNotBlank() } ?: "@${request.user.username}",
        subtitle = request.user.username?.let { "@$it" },
        onClick = null,
        trailing = trailing,
    )
}

/** Lider tablosu ve meydan okuma ilerlemesi arasında paylaşılan sıra satırı. */
@Composable
fun RankRow(rank: Int, user: FriendUserData, isCurrentUser: Boolean, valueText: String, en: Boolean) {
    val name = user.displayName?.takeIf { it.isNotBlank() } ?: "@${user.username}"
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(if (isCurrentUser) HedefitColors.Lime else HedefitColors.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
                Text("$rank", color = if (isCurrentUser) HedefitColors.OnLime else HedefitColors.TextSecondary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                name + if (isCurrentUser) (if (en) " (you)" else " (sen)") else "",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrentUser) FontWeight.ExtraBold else FontWeight.Bold,
            )
            Text(valueText, color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun feedSourceLabel(source: String, en: Boolean): String = when (source) {
    "WORKOUT_COMPLETED" -> if (en) "completed a workout" else "antrenmanı tamamladı"
    "ROUTE_DISTANCE" -> if (en) "finished a route" else "bir rotayı tamamladı"
    "ACHIEVEMENT_UNLOCKED" -> if (en) "unlocked an achievement" else "bir başarım kazandı"
    "WEEKLY_GOAL_COMPLETED" -> if (en) "hit their weekly goal" else "haftalık hedefini tuttu"
    "WEEKLY_CHALLENGE_COMPLETED" -> if (en) "completed a challenge" else "bir meydan okumayı tamamladı"
    else -> if (en) "earned XP" else "XP kazandı"
}

private fun feedSourceIcon(source: String) = when (source) {
    "WORKOUT_COMPLETED" -> Icons.Default.FitnessCenter
    "ROUTE_DISTANCE" -> Icons.Default.DirectionsRun
    "ACHIEVEMENT_UNLOCKED" -> Icons.Default.EmojiEvents
    "WEEKLY_CHALLENGE_COMPLETED" -> Icons.Default.Flag
    else -> Icons.Default.Bolt
}

private fun relativeFeedTime(occurredAt: String, en: Boolean): String = runCatching {
    val instant = Instant.parse(occurredAt)
    val minutes = java.time.Duration.between(instant, Instant.now()).toMinutes()
    when {
        minutes < 1 -> if (en) "just now" else "az önce"
        minutes < 60 -> if (en) "${minutes}m ago" else "${minutes}dk önce"
        minutes < 60 * 24 -> if (en) "${minutes / 60}h ago" else "${minutes / 60}sa önce"
        minutes < 60 * 24 * 7 -> if (en) "${minutes / (60 * 24)}d ago" else "${minutes / (60 * 24)}g önce"
        else -> instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))
    }
}.getOrDefault("")

@Composable
private fun FeedRow(item: FeedItemData, en: Boolean) {
    val name = item.user.displayName?.takeIf { it.isNotBlank() } ?: "@${item.user.username}"
    HfNavRow(
        icon = feedSourceIcon(item.source),
        tint = HedefitColors.Lime,
        title = "$name ${feedSourceLabel(item.source, en)}",
        subtitle = relativeFeedTime(item.occurredAt, en),
        onClick = null,
    ) {
        HfPill("+${item.amount} XP")
    }
}
