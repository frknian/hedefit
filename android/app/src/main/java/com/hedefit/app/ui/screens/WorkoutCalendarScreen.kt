package com.hedefit.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.WorkoutScheduleData
import com.hedefit.app.data.model.WorkoutProgramData
import com.hedefit.app.ui.components.*
import com.hedefit.app.ui.theme.HedefitColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutCalendarScreen(
    schedule: List<WorkoutScheduleData>,
    programs: List<WorkoutProgramData>,
    onBack: () -> Unit,
    onSchedule: (LocalDate, String, String?, String?, String?) -> Unit,
    onAutoDistribute: (YearMonth, Set<DayOfWeek>, String, List<Pair<String, String>>) -> Unit,
    language: String = "tr",
) {
    val context = LocalContext.current
    val en = language == "en"
    val locale = Locale.forLanguageTag(if (en) "en" else "tr")
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var selectedTime by remember { mutableStateOf("19:00") }
    val defaultProgramId = programs.firstOrNull(WorkoutProgramData::isActive)?.id ?: programs.firstOrNull()?.id
    var selectedProgramId by remember(programs) { mutableStateOf(defaultProgramId) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showAutoDialog by remember { mutableStateOf(false) }
    val entryMap = schedule.associateBy { it.date }
    val today = LocalDate.now()

    val leadingBlanks = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val monthDays: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val trailingBlanks = (7 - monthDays.size % 7) % 7
    val cells: List<LocalDate?> = monthDays + List(trailingBlanks) { null }
    val weeks = cells.chunked(7)
    val weekdayLabels = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        .map { it.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar(Char::uppercase) }

    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { HfScreenHeader(if (en) "Workout Calendar" else "Antrenman Takvimi", if (en) "Monthly view" else "Aylık görünüm", onBack) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    HfCircleButton(Icons.Default.ChevronLeft, if (en) "Previous month" else "Önceki ay", { month = month.minusMonths(1) })
                    Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    HfCircleButton(Icons.Default.ChevronRight, if (en) "Next month" else "Sonraki ay", { month = month.plusMonths(1) })
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            weekdayLabels.forEach { label -> Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                        }
                        weeks.forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { date ->
                                    Box(Modifier.weight(1f).aspectRatio(0.85f).padding(2.dp)) {
                                        if (date != null) {
                                            val entry = entryMap[date.toString()]
                                            val isToday = date == today
                                            val dotColor = when (entry?.status) {
                                                "completed" -> HedefitColors.Lime
                                                "planned" -> HedefitColors.Water
                                                "deferred" -> HedefitColors.Warning
                                                else -> null
                                            }
                                            Column(
                                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                                    .background(if (isToday) HedefitColors.Lime.copy(alpha = .16f) else HedefitColors.SurfaceHigh)
                                                    .clickable {
                                                        selected = date
                                                        selectedTime = entry?.time ?: "19:00"
                                                        selectedProgramId = entry?.programId ?: defaultProgramId
                                                        showScheduleDialog = true
                                                    }.padding(vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Text(date.dayOfMonth.toString(), color = if (isToday) HedefitColors.Lime else HedefitColors.TextPrimary, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                                Spacer(Modifier.height(4.dp))
                                                Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor ?: Color.Transparent))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(HedefitColors.Water, if (en) "Planned" else "Planlandı")
                    LegendDot(HedefitColors.Lime, if (en) "Completed" else "Tamamlandı")
                    LegendDot(HedefitColors.Warning, if (en) "Deferred" else "Ertelendi")
                }
            }
            item {
                HfPrimaryButton(
                    if (en) "Auto-distribute program" else "Programı otomatik dağıt",
                    { showAutoDialog = true },
                    Modifier.fillMaxWidth(),
                    Icons.Default.AutoAwesome,
                    enabled = programs.isNotEmpty(),
                )
            }
            if (programs.isEmpty()) item {
                Text(if (en) "Create a workout program first, then schedule it here." else "Önce bir antrenman programı oluştur, sonra buradan takvime ekle.", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (showScheduleDialog) {
        val selectedEntry = entryMap[selected.toString()]
        val selectedProgram = programs.firstOrNull { it.id == selectedProgramId }
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text(if (en) "Plan for ${selected.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale))}" else "${selected.format(DateTimeFormatter.ofPattern("d MMMM EEEE", locale))} için planla") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (en) "Workout program" else "Antrenman programı", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                if (programs.isEmpty()) {
                    Text(if (en) "Create a workout program first." else "Önce bir antrenman programı oluştur.", color = HedefitColors.Coral, style = MaterialTheme.typography.bodySmall)
                } else {
                    programs.forEach { program ->
                        FilterChip(
                            selected = selectedProgramId == program.id,
                            onClick = { selectedProgramId = program.id },
                            label = { Text(program.name, maxLines = 1) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().clickable {
                        val parts = selectedTime.split(':').mapNotNull(String::toIntOrNull)
                        TimePickerDialog(context, { _, h, m -> selectedTime = "%02d:%02d".format(h, m) }, parts.getOrElse(0) { 19 }, parts.getOrElse(1) { 0 }, true).show()
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = HedefitColors.Lime)
                        Spacer(Modifier.width(10.dp))
                        Text(selectedTime, style = MaterialTheme.typography.headlineSmall)
                    }
            } },
            dismissButton = { TextButton(onClick = { showScheduleDialog = false }) { Text(if (en) "Cancel" else "Vazgeç") } },
            confirmButton = {
                Button(enabled = selectedProgram != null, onClick = {
                    onSchedule(selected, selectedTime, selectedEntry?.originalDate, selectedProgram?.id, selectedProgram?.name)
                    showScheduleDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) {
                    Text(if (selectedEntry == null) (if (en) "Add to calendar" else "Takvime ekle") else if (en) "Update time" else "Saati güncelle")
                }
            },
        )
    }
    if (showAutoDialog) {
        AutoDistributeDialog(
            en = en,
            programs = programs,
            month = month,
            locale = locale,
            defaultProgramId = defaultProgramId,
            onDismiss = { showAutoDialog = false },
            onConfirm = { weekdays, time, chosenPrograms -> onAutoDistribute(month, weekdays, time, chosenPrograms) },
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoDistributeDialog(
    en: Boolean,
    programs: List<WorkoutProgramData>,
    month: YearMonth,
    locale: Locale,
    defaultProgramId: String?,
    onDismiss: () -> Unit,
    onConfirm: (Set<DayOfWeek>, String, List<Pair<String, String>>) -> Unit,
) {
    val context = LocalContext.current
    var selectedProgramIds by remember { mutableStateOf(setOfNotNull(defaultProgramId)) }
    var selectedDays by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) }
    var time by remember { mutableStateOf("19:00") }
    val dayOptions = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        .map { it to it.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar(Char::uppercase) }
    val allSelected = programs.isNotEmpty() && selectedProgramIds.size == programs.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Auto-distribute" else "Otomatik dağıt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (en) "Programs" else "Programlar", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { selectedProgramIds = if (allSelected) emptySet() else programs.map { it.id }.toSet() }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = allSelected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = HedefitColors.Lime))
                        Spacer(Modifier.width(8.dp))
                        Text(if (en) "Select all" else "Tümünü seç", fontWeight = FontWeight.Bold)
                    }
                    HfDivider()
                    programs.forEach { p ->
                        val on = p.id in selectedProgramIds
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { selectedProgramIds = if (on) selectedProgramIds - p.id else selectedProgramIds + p.id }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = HedefitColors.Lime))
                            Spacer(Modifier.width(8.dp))
                            Text(p.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (en) "Which days?" else "Hangi günler?", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayOptions.forEach { (day, label) -> HfChip(label, day in selectedDays, onClick = { selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day }) }
                    }
                }
                Row(Modifier.fillMaxWidth().clickable {
                        val parts = time.split(':').mapNotNull(String::toIntOrNull)
                        TimePickerDialog(context, { _, h, m -> time = "%02d:%02d".format(h, m) }, parts.getOrElse(0) { 19 }, parts.getOrElse(1) { 0 }, true).show()
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = HedefitColors.Lime)
                        Spacer(Modifier.width(10.dp))
                        Text(time, style = MaterialTheme.typography.headlineSmall)
                }
                val monthLabel = month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM", locale))
                Text(
                    if (selectedProgramIds.size > 1) {
                        if (en) "Fills every matching day in $monthLabel, rotating through the selected programs." else "$monthLabel ayında uygun tüm günleri seçtiğin programlar arasında sırayla dağıtarak doldurur."
                    } else {
                        if (en) "Fills every matching day in $monthLabel that hasn't passed yet." else "$monthLabel ayında henüz geçmemiş, seçtiğin günlere denk gelen tüm tarihleri doldurur."
                    },
                    color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = {
            Button(
                enabled = selectedProgramIds.isNotEmpty() && selectedDays.isNotEmpty(),
                onClick = {
                    val chosen = programs.filter { it.id in selectedProgramIds }.map { it.id to it.name }
                    onConfirm(selectedDays, time, chosen)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) { Text(if (en) "Distribute" else "Dağıt") }
        },
    )
}
