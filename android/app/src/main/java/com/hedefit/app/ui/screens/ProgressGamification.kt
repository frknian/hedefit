package com.hedefit.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.ChallengeHubData
import com.hedefit.app.data.model.UserChallengeData
import com.hedefit.app.gamification.GamificationSnapshot
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.HfChipRow
import com.hedefit.app.ui.components.HfIconBadge
import com.hedefit.app.ui.components.HfProgressBar
import com.hedefit.app.ui.components.HfSectionHeader
import com.hedefit.app.ui.components.HfStatTile
import com.hedefit.app.ui.components.HfTag
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.theme.HedefitColors
import java.time.LocalDate

/**
 * İlerleme ekranının oyunlaştırma BÖLÜMÜ (ekranın tamamı değil): level, XP, seri, challenge'lar, rozetler.
 * Kilo, vücut ölçüleri ve performans grafikleri olduğu gibi aşağıda kalır.
 */
@Composable
fun ProgressGamificationSection(
    snapshot: GamificationSnapshot?,
    hub: ChallengeHubData?,
    totalWorkouts: Int,
    onOpenRewards: () -> Unit,
    onOpenChallenge: (UserChallengeData) -> Unit,
) {
    if (snapshot == null) return
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val level = snapshot.level
    val challenges = hub?.challenges.orEmpty()
    val currentStreak = challenges.filter { it.status == "active" }.maxOfOrNull { it.state(today).streak } ?: 0
    val longestStreak = challenges.maxOfOrNull { it.state(today).longestStreak } ?: 0
    val completed = challenges.count { it.status == "completed" }
    val badges = snapshot.achievements.filter { it.unlockedAt != null }.sortedByDescending { it.unlockedAt }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HfSectionHeader(tr("Seviye ve seri", "Level & streak"), tr("Ödüller", "Rewards"), onOpenRewards)
        HedefitCard(Modifier.fillMaxWidth(), onClick = onOpenRewards, contentPadding = PaddingValues(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HfIconBadge(Icons.Default.EmojiEvents, HedefitColors.Lime, 44.dp, 22.dp, 14.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(tr("Seviye ${level.level}", "Level ${level.level}"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text(tr("Toplam ${snapshot.totalXp} XP", "${snapshot.totalXp} XP total"), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${level.currentXp} / ${level.nextLevelXp} XP", color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium)
                }
                HfProgressBar(level.progress, height = 8.dp)
                Text(tr("Seviye ${level.level + 1} için ${level.nextLevelXp - level.currentXp} XP kaldı", "${level.nextLevelXp - level.currentXp} XP to Level ${level.level + 1}"), color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HfStatTile(tr("Seri", "Streak"), "🔥 $currentStreak", Modifier.weight(1f), sub = tr("En uzun $longestStreak", "Best $longestStreak"))
            HfStatTile("Challenge", "$completed", Modifier.weight(1f), sub = tr("tamamlandı", "completed"))
            HfStatTile(tr("Antrenman", "Workouts"), "$totalWorkouts", Modifier.weight(1f), sub = tr("toplam", "total"))
        }
        if (badges.isNotEmpty()) HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Kazanılan rozetler · ${badges.size}", "Achievements earned · ${badges.size}"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                HfChipRow { badges.take(8).forEach { HfTag("🏅 ${it.title}") } }
            }
        }
        val history = challenges.filter { it.status != "active" }
        if (history.isNotEmpty()) {
            HfSectionHeader(tr("Challenge geçmişi", "Challenge history"), if (history.size > 3) (if (showHistory) tr("Daha az", "Less") else tr("Tümü", "All")) else null, if (history.size > 3) ({ showHistory = !showHistory }) else null)
            (if (showHistory) history else history.take(3)).forEach { challenge -> ActiveChallengeRow(challenge, today) { onOpenChallenge(challenge) } }
        }
    }
}
