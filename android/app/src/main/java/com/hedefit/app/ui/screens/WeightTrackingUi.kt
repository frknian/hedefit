package com.hedefit.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.BodyMeasurementData
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.ui.components.AlertDialog
import com.hedefit.app.ui.components.HfProgressBar
import com.hedefit.app.ui.components.LabeledValue
import com.hedefit.app.ui.settings.MeasurementUnits
import com.hedefit.app.ui.settings.WeightTrend
import com.hedefit.app.ui.theme.HedefitColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun weightDate(raw: String, en: Boolean): String = runCatching {
    LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", if (en) Locale.ENGLISH else Locale("tr", "TR")))
}.getOrDefault(raw.take(10))

/** Kilo kartının altı: haftalık hız, hedefe ilerleme, tahmini tarih ve (yetişkinler için) VKİ. */
@Composable
internal fun WeightInsights(allData: DashboardData?, en: Boolean, unitSystem: String, onShowHistory: () -> Unit) {
    val profile = allData?.profile
    val points = WeightTrend.points(allData?.measurements.orEmpty())
    val current = points.lastOrNull()?.kg ?: profile?.weightKg
    val rate = WeightTrend.weeklyRateKg(points)
    val target = profile?.targetWeightKg
    val unit = MeasurementUnits.weightUnit(unitSystem)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rate != null || (target != null && current != null)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LabeledValue(
                if (en) "Weekly trend" else "Haftalık trend",
                rate?.let { "%+.1f %s/%s".format(MeasurementUnits.weightValue(it, unitSystem), unit, if (en) "wk" else "hf") } ?: "—",
            )
            if (target != null && current != null) {
                LabeledValue(if (en) "Goal" else "Hedef", MeasurementUnits.formatWeight(target, unitSystem))
                LabeledValue(
                    if (en) "To go" else "Kalan",
                    MeasurementUnits.formatWeight(kotlin.math.abs(target - current), unitSystem),
                    accent = HedefitColors.Lime,
                )
            }
        }

        if (target != null && current != null) {
            val start = points.firstOrNull()?.kg ?: current
            val progress = WeightTrend.goalProgress(start, current, target)
            if (progress != null) {
                HfProgressBar(progress, height = 8.dp)
                Text(
                    if (en) "${(progress * 100).toInt()}% of the way from ${MeasurementUnits.formatWeight(start, unitSystem)} to your goal"
                    else "${MeasurementUnits.formatWeight(start, unitSystem)} → hedef yolunun %${(progress * 100).toInt()}'i tamam",
                    color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                )
            }
            WeightTrend.projectedDate(current, target, rate)?.let { date ->
                Text(
                    if (en) "At this pace you reach your goal around ${weightDate(date.toString(), true)}."
                    else "Bu hızla hedefine yaklaşık ${weightDate(date.toString(), false)} civarı ulaşırsın.",
                    color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val bmi = if (WeightTrend.bmiApplies(profile?.age)) WeightTrend.bmi(current, profile?.heightCm) else null
        if (bmi != null) {
            val band = when (WeightTrend.bmiBand(bmi)) {
                WeightTrend.BmiBand.Low -> if (en) "below the typical range" else "tipik aralığın altında"
                WeightTrend.BmiBand.Healthy -> if (en) "within the typical range" else "tipik aralıkta"
                WeightTrend.BmiBand.High -> if (en) "above the typical range" else "tipik aralığın üzerinde"
            }
            Text(
                if (en) "BMI %.1f · %s. It doesn't account for muscle mass, so treat it as a rough guide.".format(bmi, band)
                else "VKİ %.1f · %s. Kas kütlesini hesaba katmaz; yalnızca kaba bir gösterge olarak düşün.".format(bmi, band),
                color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall,
            )
        }

        if (points.isNotEmpty()) TextButton(onClick = onShowHistory) {
            Text(if (en) "Weight history (${points.size})" else "Kilo geçmişi (${points.size})", color = HedefitColors.Lime)
        }
    }
}

/** Tüm ölçüm kayıtları: dokununca düzenle, çöp kutusuyla sil (onaylı). */
@Composable
internal fun WeightHistoryDialog(
    measurements: List<BodyMeasurementData>,
    en: Boolean,
    unitSystem: String,
    busy: Boolean,
    onEdit: (BodyMeasurementData) -> Unit,
    onDelete: (BodyMeasurementData) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<BodyMeasurementData?>(null) }
    val rows = measurements.sortedByDescending { it.date }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Measurement history" else "Ölçüm geçmişi") },
        text = {
            if (rows.isEmpty()) Text(if (en) "No records yet." else "Henüz kayıt yok.", color = HedefitColors.TextSecondary)
            else LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows, key = { it.date }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !busy) { onEdit(entry) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.weightKg?.let { MeasurementUnits.formatWeight(it, unitSystem) } ?: (if (en) "No weight" else "Kilo yok"),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(weightDate(entry.date, en), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { pendingDelete = entry }, enabled = !busy) {
                            Icon(Icons.Default.Delete, if (en) "Delete" else "Sil", tint = HedefitColors.TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (en) "Close" else "Kapat") } },
    )

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (en) "Delete this record?" else "Bu kayıt silinsin mi?") },
            text = { Text(weightDate(entry.date, en) + (if (en) " measurements will be removed." else " tarihli ölçümler silinecek."), color = HedefitColors.TextSecondary) },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(if (en) "Cancel" else "Vazgeç") } },
            confirmButton = { TextButton(onClick = { onDelete(entry); pendingDelete = null }) { Text(if (en) "Delete" else "Sil", color = HedefitColors.Coral) } },
        )
    }
}

/** Home'dan tek alanlı hızlı kilo girişi; bugünün kaydına yazar. */
@Composable
internal fun QuickWeightDialog(
    lastKg: Double?,
    en: Boolean,
    unitSystem: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var value by remember { mutableStateOf(lastKg?.let { "%.1f".format(MeasurementUnits.weightValue(it, unitSystem)) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Log today's weight" else "Bugünkü kilonu kaydet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text("${if (en) "Weight" else "Kilo"} (${MeasurementUnits.weightUnit(unitSystem)})") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = {
            TextButton(onClick = {
                val kg = value.trim().replace(',', '.').toDoubleOrNull()?.let { MeasurementUnits.weightToKg(it, unitSystem) }
                if (kg == null || kg !in 20.0..400.0) error = if (en) "Weight must be between 20 and 400 kg." else "Kilo 20 ile 400 kg arasında olmalı."
                else onSave(kg)
            }) { Text(if (en) "Save" else "Kaydet", color = HedefitColors.Lime) }
        },
    )
}
