package com.hedefit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.CoachChallengePreferences
import com.hedefit.app.data.model.CoachChallengePreview
import com.hedefit.app.data.model.ProfileData
import com.hedefit.app.data.model.difficultyLabel
import com.hedefit.app.data.model.equipmentLabel
import com.hedefit.app.data.model.minutesLabel
import com.hedefit.app.data.model.taskLabel
import com.hedefit.app.ui.components.FitCoachRobotAvatar
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.HfChip
import com.hedefit.app.ui.components.HfChipRow
import com.hedefit.app.ui.components.HfPrimaryButton
import com.hedefit.app.ui.components.HfScreenHeader
import com.hedefit.app.ui.components.HfSectionHeader
import com.hedefit.app.ui.components.HfStepRow
import com.hedefit.app.ui.components.HfTag
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.i18n.tr

private fun focusLabel(value: String?) = when (value) {
    "core" -> "Core"
    "full_body" -> tr("Tüm vücut", "Full body")
    "pilates" -> "Pilates"
    "flexibility" -> tr("Esneklik", "Flexibility")
    "steps" -> tr("Adım", "Steps")
    else -> tr("Otomatik", "Auto")
}

private fun coachEquipmentLabel(value: String?) = when (value) {
    "none" -> tr("Ekipmansız", "No equipment")
    "band" -> tr("Direnç bandı", "Resistance band")
    "gym" -> tr("Spor salonu", "Gym")
    else -> tr("Otomatik", "Auto")
}

/**
 * Fit Koç ile challenge oluştur. Profilden bilinenler (hedef, seviye, ekipman, kısıtlamalar, son check-in'ler,
 * antrenman geçmişi) sunucuda kullanılır; burada yalnızca İSTEĞE BAĞLI değişiklikler sorulur ("Otomatik" = bilinen veri).
 */
@Composable
fun CoachChallengeScreen(
    coachName: String,
    profile: ProfileData?,
    preview: CoachChallengePreview?,
    busy: Boolean,
    onBack: () -> Unit,
    onPreview: (CoachChallengePreferences) -> Unit,
    onStart: (CoachChallengePreferences) -> Unit,
) {
    BackHandler(onBack = onBack)
    var focus by rememberSaveable { mutableStateOf<String?>(null) }
    var days by rememberSaveable { mutableStateOf<Int?>(null) }
    var minutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    val preferences = CoachChallengePreferences(focus, days, minutes, equipment, level)
    // Profil cevapları Türkçe etiketlerle saklanır; İngilizce arayüzde karışık dil göstermemek için yalnız TR'de listelenir.
    val known = if (com.hedefit.app.ui.i18n.AppLang.en) emptyList() else listOfNotNull(
        profile?.goal?.substringBefore(" | ")?.takeIf { it.isNotBlank() }?.let { tr("hedefin: $it", "goal: $it") },
        profile?.environment?.takeIf { it.isNotBlank() }?.let { tr("ortam: $it", "setting: $it") },
        profile?.equipment?.takeIf { it.isNotBlank() }?.let { tr("ekipman: $it", "equipment: $it") },
    )

    ScreenContainer {
        LazyColumn(Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { HfScreenHeader(tr("$coachName ile Challenge", "Challenge with $coachName"), onBack = onBack, backLabel = tr("Geri", "Back")) }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(42.dp).background(com.hedefit.app.ui.theme.HedefitColors.Lime, CircleShape), contentAlignment = Alignment.Center) { FitCoachRobotAvatar(Modifier.size(34.dp)) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("Bildiklerimi kullanıyorum", "I'll use what I already know"), fontWeight = FontWeight.ExtraBold)
                            Text(
                                (if (known.isEmpty()) tr("Profilin, check-in'lerin ve antrenman geçmişin", "Your profile, check-ins and training history") else known.joinToString(" · ")) +
                                    tr(". Seviyen, kısıtlamaların ve son günlerdeki enerjin de hesaba katılır. Aşağıdakiler isteğe bağlı.", ". Your level, limitations and recent energy are taken into account too. Everything below is optional."),
                                color = com.hedefit.app.ui.theme.HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item { HfSectionHeader(tr("Hedef", "Focus")) }
            item { HfChipRow { listOf(null, "core", "full_body", "pilates", "flexibility", "steps").forEach { value -> HfChip(focusLabel(value), focus == value, { focus = value }) } } }
            item { HfSectionHeader(tr("Süre", "Length")) }
            item { HfChipRow { listOf<Int?>(null, 7, 14, 21, 30).forEach { value -> HfChip(value?.let { tr("$it gün", "$it days") } ?: tr("Otomatik", "Auto"), days == value, { days = value }) } } }
            item { HfSectionHeader(tr("Günde ayırabileceğin süre", "Time per day")) }
            item { HfChipRow { listOf<Int?>(null, 10, 15, 20, 30).forEach { value -> HfChip(value?.let { tr("$it dk", "$it min") } ?: tr("Otomatik", "Auto"), minutes == value, { minutes = value }) } } }
            item { HfSectionHeader(tr("Ekipman", "Equipment")) }
            item { HfChipRow { listOf(null, "none", "band", "gym").forEach { value -> HfChip(coachEquipmentLabel(value), equipment == value, { equipment = value }) } } }
            item { HfSectionHeader(tr("Seviye", "Level")) }
            item { HfChipRow { listOf(null, "beginner", "intermediate", "advanced").forEach { value -> HfChip(value?.let(::difficultyLabel) ?: tr("Otomatik", "Auto"), level == value, { level = value }) } } }
            item {
                HfPrimaryButton(if (busy && preview == null) tr("Hazırlanıyor…", "Preparing…") else tr("Planı hazırla", "Build my plan"), { if (!busy) onPreview(preferences) }, Modifier.fillMaxWidth(), Icons.Default.AutoAwesome, secondary = preview != null)
            }
            if (preview != null) {
                item {
                    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(preview.plan.title.text(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                            Text(preview.plan.description.text(), color = com.hedefit.app.ui.theme.HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            HfChipRow {
                                HfTag(difficultyLabel(preview.plan.difficulty))
                                minutesLabel(preview.minutes)?.let { HfTag(it) }
                                equipmentLabel(preview.plan.equipment)?.let { HfTag(it) }
                            }
                            Text("+${preview.rewardXp} XP", color = com.hedefit.app.ui.theme.HedefitColors.Lime, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Column {
                                preview.plan.days.take(7).forEachIndexed { index, task -> HfStepRow(index + 1, taskLabel(task), tr("Gün ${index + 1}", "Day ${index + 1}")) }
                                if (preview.plan.days.size > 7) Text(tr("+${preview.plan.days.size - 7} gün daha", "+${preview.plan.days.size - 7} more days"), color = com.hedefit.app.ui.theme.HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            HfPrimaryButton(if (busy) tr("Başlatılıyor…", "Starting…") else tr("Challenge'ı başlat", "Start challenge"), { if (!busy) onStart(preferences) }, Modifier.fillMaxWidth(), Icons.Default.PlayArrow)
                        }
                    }
                }
            }
        }
    }
}
