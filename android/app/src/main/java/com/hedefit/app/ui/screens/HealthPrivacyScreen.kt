package com.hedefit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.CycleProfileData
import com.hedefit.app.data.model.cycleSettingsVisible
import com.hedefit.app.ui.components.*
import com.hedefit.app.ui.state.HealthPrivacyState
import com.hedefit.app.ui.theme.HedefitColors

/**
 * Ayarlar → Sağlık verisi ve kişiselleştirme. Uyarlama, isteğe bağlı döngü takibi (erkek olarak belirten kullanıcıya
 * gösterilmez), koçun sağlık bağlamını kullanma izni ve tüm sağlık verisini kalıcı silme.
 * Hiçbir değer burada günlüğe yazılmaz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthPrivacyScreen(
    state: HealthPrivacyState,
    gender: String,
    language: String,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onAdaptive: (Boolean) -> Unit,
    onAiContext: (Boolean) -> Unit,
    onSaveCycle: (CycleProfileData) -> Unit,
    onTurnOffCycle: () -> Unit,
    onDeleteCycle: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val en = language == "en"
    LaunchedEffect(Unit) { onLoad() }
    var editingCycle by remember { mutableStateOf(false) }
    var confirmDeleteCycle by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val showCycle = cycleSettingsVisible(gender)
    val cycle = state.cycle
    val tracking = cycle?.profile?.trackingEnabled == true

    if (confirmDeleteCycle) AlertDialog(
        onDismissRequest = { confirmDeleteCycle = false },
        title = { Text(if (en) "Delete cycle data?" else "Döngü verisi silinsin mi?") },
        text = { Text(if (en) "Your cycle information is permanently deleted from our servers and cycle-based adaptation turns off." else "Döngü bilgilerin sunucularımızdan kalıcı olarak silinir ve döngüye göre uyarlama kapanır.") },
        dismissButton = { TextButton(onClick = { confirmDeleteCycle = false }) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = { TextButton(onClick = { confirmDeleteCycle = false; onDeleteCycle() }) { Text(if (en) "Delete" else "Sil", color = HedefitColors.Coral) } },
    )
    if (confirmDeleteAll) AlertDialog(
        onDismissRequest = { confirmDeleteAll = false },
        title = { Text(if (en) "Delete all health data?" else "Tüm sağlık verisi silinsin mi?") },
        text = { Text(if (en) "Your cycle information, all daily check-ins and these personalization settings are permanently deleted. Your account, workouts and meals are not affected." else "Döngü bilgilerin, tüm günlük check-in'ler ve bu kişiselleştirme ayarları kalıcı olarak silinir. Hesabın, antrenmanların ve öğünlerin etkilenmez.") },
        dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = { TextButton(onClick = { confirmDeleteAll = false; onDeleteAll() }) { Text(if (en) "Delete everything" else "Hepsini sil", color = HedefitColors.Coral) } },
    )
    if (editingCycle) CycleEditDialog(en, cycle?.profile, onDismiss = { editingCycle = false }, onSave = { editingCycle = false; onSaveCycle(it) })

    @Composable
    fun ToggleRow(title: String, body: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(body, color = HedefitColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = HedefitColors.Lime, checkedThumbColor = HedefitColors.OnLime))
        }
    }

    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HfScreenHeader(if (en) "Health data and personalization" else "Sağlık verisi ve kişiselleştirme", onBack = onBack, backLabel = if (en) "Back" else "Geri") {
                    if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = HedefitColors.Lime)
                }
            }
            if (state.loaded && !state.available) item {
                Text(if (en) "Personalization settings aren't available yet. Nothing is collected until they are." else "Kişiselleştirme ayarları henüz kullanılamıyor. Kullanılabilir olana kadar hiçbir veri toplanmaz.", color = HedefitColors.TextSecondary, fontSize = 13.sp)
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToggleRow(
                            if (en) "Adapt my plan to my daily check-in" else "Planımı günlük check-in'e göre uyarla",
                            if (en) "When off, your plan stays exactly as written." else "Kapalıyken planın olduğu gibi kalır.",
                            state.personalization.adaptiveEnabled, state.available, onAdaptive,
                        )
                        if (showCycle) ToggleRow(
                            if (en) "Let Fit Coach use my cycle and check-in context" else "Fit Koç döngü ve check-in bağlamımı kullanabilsin",
                            if (en) "Premium only. Off by default; when off, the coach never sees this information." else "Yalnızca Premium. Varsayılan kapalı; kapalıyken koç bu bilgileri hiç görmez.",
                            state.personalization.aiHealthContextEnabled, state.available, onAiContext,
                        )
                    }
                }
            }
            if (showCycle) item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (en) "Cycle tracking (optional)" else "Döngü takibi (isteğe bağlı)", color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            if (tracking) (if (en) "On. It only nudges your plan slightly, and your daily check-in always comes first." else "Açık. Planını yalnızca küçük ölçüde etkiler; günlük check-in cevapların her zaman önceliklidir.")
                            else (if (en) "Off. Nothing about your cycle is stored." else "Kapalı. Döngünle ilgili hiçbir şey saklanmaz."),
                            color = HedefitColors.TextSecondary, fontSize = 12.sp,
                        )
                        HfPrimaryButton(text = if (tracking) (if (en) "Edit cycle details" else "Döngü bilgilerini düzenle") else (if (en) "Turn on cycle tracking" else "Döngü takibini aç"), onClick = { editingCycle = true }, enabled = state.available, modifier = Modifier.fillMaxWidth())
                        if (tracking) {
                            TextButton(onClick = onTurnOffCycle) { Text(if (en) "Turn off (keep my data)" else "Kapat (verim kalsın)", color = HedefitColors.TextSecondary) }
                            TextButton(onClick = { confirmDeleteCycle = true }) { Text(if (en) "Delete my cycle data" else "Döngü verimi sil", color = HedefitColors.Coral) }
                        }
                    }
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (en) "Delete all health data" else "Tüm sağlık verisini sil", color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text(if (en) "Removes your cycle information, daily check-ins and these settings from our servers for good." else "Döngü bilgilerini, günlük check-in'leri ve bu ayarları sunucularımızdan kalıcı olarak kaldırır.", color = HedefitColors.TextSecondary, fontSize = 12.sp)
                        TextButton(onClick = { confirmDeleteAll = true }, enabled = state.available) { Text(if (en) "Delete all health data" else "Tüm sağlık verisini sil", color = HedefitColors.Coral) }
                    }
                }
            }
            item { Text(if (en) "None of this is medical advice." else "Bunların hiçbiri tıbbi tavsiye değildir.", color = HedefitColors.TextMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun CycleEditDialog(en: Boolean, current: CycleProfileData?, onDismiss: () -> Unit, onSave: (CycleProfileData) -> Unit) {
    val draft = remember {
        CycleDraft().apply {
            enabled = true
            lastPeriod = current?.lastPeriodStart?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            current?.cycleLengthDays?.let { cycleLength = it }
            current?.periodLengthDays?.let { periodLength = it }
            current?.regularity?.let { regularity = it }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Cycle details" else "Döngü bilgileri") },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())) { CycleOptInStep(draft) { draft.enabled = it } } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = {
            TextButton(onClick = { onSave(CycleProfileData(draft.enabled == true, draft.lastPeriod?.toString(), draft.cycleLength, draft.periodLength, draft.regularity)) }) { Text(if (en) "Save" else "Kaydet", color = HedefitColors.Lime) }
        },
    )
}
