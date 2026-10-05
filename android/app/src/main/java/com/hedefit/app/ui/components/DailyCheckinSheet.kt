package com.hedefit.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.CheckinChoices
import com.hedefit.app.data.model.CheckinData
import com.hedefit.app.data.model.CycleStateData
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.theme.HedefitColors
import java.time.LocalDate

/**
 * Kısa günlük check-in: enerji, uyku, kas ağrısı, ağrı ve müsait süre (5 satır, hepsi tek dokunuş).
 * Uzun sağlık formu yok. Döngü bilgisi SORULMAZ; kullanıcı etkinleştirdiyse yalnızca bilgi satırı olarak gösterilir.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyCheckinSheet(
    onDismiss: () -> Unit,
    onSave: (CheckinData) -> Unit,
    saved: Boolean = false,
    cycle: CycleStateData? = null,
    initial: CheckinData? = null,
) {
    var energy by remember { mutableStateOf(initial?.energy) }
    var sleep by remember { mutableStateOf(initial?.let { data -> CheckinChoices.sleep.indexOfFirst { it.second == data.sleepQuality }.takeIf { it >= 0 } }) }
    var soreness by remember { mutableStateOf(initial?.soreness ?: 0) }
    var pain by remember { mutableStateOf(initial?.pain ?: 0) }
    var minutes by remember { mutableStateOf(initial?.availableMinutes ?: 30) }

    @Composable
    fun Row5(title: String, content: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = HedefitColors.TextPrimary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
    @Composable
    fun Chip(selected: Boolean, label: String, onClick: () -> Unit) = FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HedefitColors.Lime, selectedLabelColor = HedefitColors.OnLime),
    )
    val energyLabels = listOf(tr("Bitkin", "Drained"), tr("Düşük", "Low"), tr("Orta", "Okay"), tr("İyi", "Good"), tr("Zinde", "Great"))
    val levelLabels = listOf(tr("Yok", "None"), tr("Hafif", "Mild"), tr("Orta", "Moderate"), tr("Yüksek", "High"))
    val sleepLabels = listOf(tr("5 sa altı", "Under 5 h"), tr("5–6 sa", "5–6 h"), tr("7–8 sa", "7–8 h"), tr("9 sa+", "9 h+"))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = HedefitColors.Surface, dragHandle = { BottomSheetDefaults.DragHandle(color = HedefitColors.TextSecondary.copy(alpha = .4f)) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("Bugün nasılsın?", "How are you today?"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = HedefitColors.TextPrimary)
                Text(tr("5 dokunuş. Antrenmanını gününe göre uyarlamamıza yardım eder.", "Five taps. Helps us adapt your workout to your day."), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (saved) {
                Text(tr("Kaydedildi ✓", "Saved ✓"), color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                if (cycle != null) Text(
                    tr("Döngü: gün ${cycle.cycleDay}" + (if (cycle.periodLikely) " • adet dönemi" else "") + ". Bu yalnızca bilgi; antrenman kararında senin cevapların öncelikli.",
                        "Cycle: day ${cycle.cycleDay}" + (if (cycle.periodLikely) " • period" else "") + ". This is only context; your answers always come first."),
                    color = HedefitColors.TextSecondary,
                )
                PrimaryButton(text = tr("Tamam", "Done"), onClick = onDismiss)
            } else {
                Row5(tr("Enerji", "Energy")) { CheckinChoices.energy.forEachIndexed { i, value -> Chip(energy == value, energyLabels[i]) { energy = value } } }
                Row5(tr("Uyku", "Sleep")) { sleepLabels.forEachIndexed { i, label -> Chip(sleep == i, label) { sleep = i } } }
                Row5(tr("Kas ağrısı", "Soreness")) { CheckinChoices.soreness.forEachIndexed { i, value -> Chip(soreness == value, levelLabels[i]) { soreness = value } } }
                Row5(tr("Ağrı (eklem/sakatlık)", "Pain (joint/injury)")) { CheckinChoices.pain.forEachIndexed { i, value -> Chip(pain == value, levelLabels[i]) { pain = value } } }
                Row5(tr("Müsait süre", "Time available")) { CheckinChoices.minutes.forEach { value -> Chip(minutes == value, if (value == 60) tr("60+ dk", "60+ min") else tr("$value dk", "$value min")) { minutes = value } } }
                PrimaryButton(
                    text = tr("Kaydet", "Save"),
                    enabled = energy != null && sleep != null,
                    onClick = {
                        val (hours, quality) = CheckinChoices.sleep[sleep ?: 2]
                        onSave(CheckinData(LocalDate.now().toString(), energy ?: 6, quality, hours, soreness, pain, minutes))
                    },
                )
            }
        }
    }
}
