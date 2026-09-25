package com.hedefit.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.WorkoutProgramData
import com.hedefit.app.data.model.WorkoutScheduleData
import com.hedefit.app.ui.components.*
import com.hedefit.app.ui.components.AlertDialog
import com.hedefit.app.ui.theme.HedefitColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class DayState { NONE, PLANNED, DONE, MISSED, DEFERRED }

private val WEEK = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

@Composable
fun WorkoutCalendarScreen(
    schedule: List<WorkoutScheduleData>,
    programs: List<WorkoutProgramData>,
    onBack: () -> Unit,
    onSchedule: (LocalDate, String, String?, String?, String?) -> Unit,
    onAutoDistribute: (YearMonth, Map<DayOfWeek, String>, List<Pair<String, String>>) -> Unit,
    language: String = "tr",
    completedDates: Set<LocalDate> = emptySet(),
) {
    val en = language == "en"
    val locale = Locale.forLanguageTag(if (en) "en" else "tr")
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(today) }
    var editing by remember { mutableStateOf(false) }
    var showAutoDialog by remember { mutableStateOf(false) }
    val entryMap = schedule.associateBy { it.date.take(10) }
    val defaultProgramId = programs.firstOrNull(WorkoutProgramData::isActive)?.id ?: programs.firstOrNull()?.id

    fun stateOf(date: LocalDate): DayState {
        val entry = entryMap[date.toString()]
        return when {
            date in completedDates || entry?.status == "completed" -> DayState.DONE
            entry?.status == "deferred" -> DayState.DEFERRED
            entry?.status == "planned" && date.isBefore(today) -> DayState.MISSED
            entry?.status == "planned" -> DayState.PLANNED
            else -> DayState.NONE
        }
    }

    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val monthStates = monthDays.map(::stateOf)
    val leadingBlanks = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val cells: List<LocalDate?> = (List(leadingBlanks) { null } + monthDays).let { it + List((7 - it.size % 7) % 7) { null } }

    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                HfScreenHeader(if (en) "Calendar" else "Takvim", onBack = onBack, backLabel = if (en) "Back" else "Geri") {
                    if (month != YearMonth.now() || selected != today) HfChip(if (en) "Today" else "Bugün", false, { month = YearMonth.now(); selected = today })
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalendarStat(if (en) "Workout days" else "Spor günü", monthStates.count { it != DayState.NONE }, HedefitColors.Water, Modifier.weight(1f))
                    CalendarStat(if (en) "Done" else "Yapıldı", monthStates.count { it == DayState.DONE }, HedefitColors.Lime, Modifier.weight(1f))
                    CalendarStat(if (en) "Left" else "Kalan", monthStates.count { it == DayState.PLANNED }, HedefitColors.Warning, Modifier.weight(1f))
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HfCircleButton(Icons.Default.ChevronLeft, if (en) "Previous month" else "Önceki ay", { month = month.minusMonths(1) })
                            Text(
                                month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar(Char::uppercase),
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                            )
                            HfCircleButton(Icons.Default.ChevronRight, if (en) "Next month" else "Sonraki ay", { month = month.plusMonths(1) })
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            WEEK.forEach { day ->
                                Text(day.getDisplayName(TextStyle.SHORT, locale).take(3).replaceFirstChar(Char::uppercase), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { date ->
                                    Box(Modifier.weight(1f).aspectRatio(.78f).padding(2.dp)) {
                                        if (date != null) DayCell(date, stateOf(date), entryMap[date.toString()]?.time, date == today, date == selected) { selected = date }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SelectedDayCard(
                    date = selected,
                    state = stateOf(selected),
                    entry = entryMap[selected.toString()],
                    en = en,
                    locale = locale,
                    canPlan = !selected.isBefore(today) && programs.isNotEmpty(),
                    onEdit = { editing = true },
                )
            }
            item { HfSectionHeader(if (en) "This week" else "Bu hafta") }
            item {
                val weekStart = today.with(DayOfWeek.MONDAY)
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Column {
                        (0L..6L).map(weekStart::plusDays).forEachIndexed { index, date ->
                            if (index > 0) HfDivider()
                            val entry = entryMap[date.toString()]
                            val state = stateOf(date)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { selected = date; month = YearMonth.from(date) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.width(52.dp)) {
                                    Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Bold, color = if (date == today) HedefitColors.Lime else HedefitColors.TextPrimary)
                                    Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelMedium)
                                }
                                Text(
                                    entry?.programName ?: if (state == DayState.DONE) (if (en) "Workout done" else "Antrenman yapıldı") else if (en) "Rest" else "Dinlenme",
                                    modifier = Modifier.weight(1f), fontWeight = if (entry != null || state == DayState.DONE) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (entry != null || state == DayState.DONE) HedefitColors.TextPrimary else HedefitColors.TextSecondary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                entry?.time?.let { Text(it.take(5), fontWeight = FontWeight.Bold, color = stateColor(state), modifier = Modifier.padding(start = 8.dp)) }
                                if (state == DayState.DONE) Icon(Icons.Default.Check, null, tint = HedefitColors.Lime, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                            }
                        }
                    }
                }
            }
            item {
                HfPrimaryButton(if (en) "Auto-distribute programs" else "Programları otomatik dağıt", { showAutoDialog = true }, Modifier.fillMaxWidth(), Icons.Default.AutoAwesome, enabled = programs.isNotEmpty())
            }
        }
    }
    if (editing) ScheduleDayDialog(
        date = selected,
        entry = entryMap[selected.toString()],
        programs = programs,
        defaultProgramId = defaultProgramId,
        en = en,
        locale = locale,
        onDismiss = { editing = false },
        onSave = { time, program ->
            onSchedule(selected, time, entryMap[selected.toString()]?.originalDate, program.id, program.name)
            editing = false
        },
    )
    if (showAutoDialog) AutoDistributeDialog(
        en = en,
        programs = programs,
        month = month,
        locale = locale,
        defaultProgramId = defaultProgramId,
        onDismiss = { showAutoDialog = false },
        onConfirm = { times, chosen -> onAutoDistribute(month, times, chosen) },
    )
}

private fun stateColor(state: DayState): Color = when (state) {
    DayState.DONE -> HedefitColors.Lime
    DayState.PLANNED -> HedefitColors.Water
    DayState.MISSED -> HedefitColors.Coral
    DayState.DEFERRED -> HedefitColors.Warning
    DayState.NONE -> HedefitColors.TextSecondary
}

@Composable
private fun CalendarStat(label: String, value: Int, color: Color, modifier: Modifier) {
    HedefitCard(modifier, contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("$value", color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, state: DayState, time: String?, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val color = stateColor(state)
    val filled = state == DayState.DONE
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> HedefitColors.Lime
                    filled -> HedefitColors.Lime.copy(alpha = .18f)
                    state != DayState.NONE -> color.copy(alpha = .12f)
                    else -> HedefitColors.SurfaceHigh
                },
            )
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, HedefitColors.Lime, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            color = if (isSelected) HedefitColors.OnLime else HedefitColors.TextPrimary,
            fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        when {
            filled -> Icon(Icons.Default.Check, null, tint = if (isSelected) HedefitColors.OnLime else HedefitColors.Lime, modifier = Modifier.size(12.dp))
            time != null && state != DayState.NONE -> Text(time.take(5), color = if (isSelected) HedefitColors.OnLime else color, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            else -> Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SelectedDayCard(date: LocalDate, state: DayState, entry: WorkoutScheduleData?, en: Boolean, locale: Locale, canPlan: Boolean, onEdit: () -> Unit) {
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HfIconBadge(if (state == DayState.DONE) Icons.Default.EventAvailable else Icons.Default.FitnessCenter, stateColor(state).takeIf { state != DayState.NONE } ?: HedefitColors.Sleep, 42.dp, 22.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(date.format(DateTimeFormatter.ofPattern(if (en) "EEEE, MMMM d" else "d MMMM EEEE", locale)).replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        when (state) {
                            DayState.DONE -> if (en) "Completed" else "Tamamlandı"
                            DayState.PLANNED -> if (en) "Planned" else "Planlandı"
                            DayState.MISSED -> if (en) "Missed" else "Kaçırıldı"
                            DayState.DEFERRED -> if (en) "Deferred" else "Ertelendi"
                            DayState.NONE -> if (en) "Rest day" else "Dinlenme günü"
                        },
                        color = stateColor(state), fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (entry != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HfStatTile(if (en) "Program" else "Program", entry.programName ?: "—", Modifier.weight(1.4f))
                    HfStatTile(if (en) "Time" else "Saat", entry.time.take(5), Modifier.weight(1f), valueColor = HedefitColors.Lime)
                }
            }
            if (canPlan) HfPrimaryButton(
                if (entry == null) (if (en) "Plan a workout" else "Antrenman planla") else if (en) "Change program or time" else "Program veya saati değiştir",
                onEdit, Modifier.fillMaxWidth(), if (entry == null) Icons.Default.Schedule else Icons.Default.Edit, secondary = entry != null,
            )
        }
    }
}

@Composable
private fun ScheduleDayDialog(
    date: LocalDate,
    entry: WorkoutScheduleData?,
    programs: List<WorkoutProgramData>,
    defaultProgramId: String?,
    en: Boolean,
    locale: Locale,
    onDismiss: () -> Unit,
    onSave: (String, WorkoutProgramData) -> Unit,
) {
    val context = LocalContext.current
    var time by remember { mutableStateOf(entry?.time?.take(5) ?: "19:00") }
    var programId by remember { mutableStateOf(entry?.programId ?: defaultProgramId) }
    val program = programs.firstOrNull { it.id == programId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ofPattern(if (en) "EEEE, MMMM d" else "d MMMM EEEE", locale)).replaceFirstChar(Char::uppercase)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HfStatTile(if (en) "Time" else "Saat", time, Modifier.fillMaxWidth(), valueColor = HedefitColors.Lime, onClick = { pickTime(context, time) { time = it } })
                Column {
                    programs.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { programId = p.id }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = programId == p.id, onClick = { programId = p.id }, colors = RadioButtonDefaults.colors(selectedColor = HedefitColors.Lime))
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = {
            Button(enabled = program != null, onClick = { onSave(time, program!!) }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) {
                Text(if (en) "Save" else "Kaydet")
            }
        },
    )
}

private fun pickTime(context: android.content.Context, current: String, onPicked: (String) -> Unit) {
    val parsed = runCatching { LocalTime.parse(current.take(5)) }.getOrDefault(LocalTime.of(19, 0))
    TimePickerDialog(context, { _, h, m -> onPicked("%02d:%02d".format(h, m)) }, parsed.hour, parsed.minute, true).show()
}

@Composable
private fun AutoDistributeDialog(
    en: Boolean,
    programs: List<WorkoutProgramData>,
    month: YearMonth,
    locale: Locale,
    defaultProgramId: String?,
    onDismiss: () -> Unit,
    onConfirm: (Map<DayOfWeek, String>, List<Pair<String, String>>) -> Unit,
) {
    val context = LocalContext.current
    var selectedProgramIds by remember { mutableStateOf(setOfNotNull(defaultProgramId)) }
    var dayTimes by remember { mutableStateOf(mapOf(DayOfWeek.MONDAY to "19:00", DayOfWeek.WEDNESDAY to "19:00", DayOfWeek.FRIDAY to "19:00")) }
    val allSelected = programs.isNotEmpty() && selectedProgramIds.size == programs.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Auto-distribute" else "Otomatik dağıt") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(if (en) "Programs" else "Programlar", fontWeight = FontWeight.ExtraBold)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selectedProgramIds = if (allSelected) emptySet() else programs.map { it.id }.toSet() }.padding(vertical = 4.dp),
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
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selectedProgramIds = if (on) selectedProgramIds - p.id else selectedProgramIds + p.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = HedefitColors.Lime))
                            Spacer(Modifier.width(8.dp))
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (en) "Days and times" else "Günler ve saatler", fontWeight = FontWeight.ExtraBold)
                    WEEK.forEach { day ->
                        val time = dayTimes[day]
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = time != null,
                                onCheckedChange = { checked -> dayTimes = if (checked) dayTimes + (day to (dayTimes.values.lastOrNull() ?: "19:00")) else dayTimes - day },
                                colors = CheckboxDefaults.colors(checkedColor = HedefitColors.Lime),
                            )
                            Text(day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar(Char::uppercase), modifier = Modifier.weight(1f), fontWeight = if (time != null) FontWeight.Bold else FontWeight.Normal)
                            if (time != null) Text(
                                time,
                                color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(HedefitColors.Lime.copy(alpha = .14f))
                                    .clickable { pickTime(context, time) { picked -> dayTimes = dayTimes + (day to picked) } }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Text(
                    month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM", locale)).replaceFirstChar(Char::uppercase) + if (en) " • remaining days are filled" else " • kalan günler doldurulur",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = {
            Button(
                enabled = selectedProgramIds.isNotEmpty() && dayTimes.isNotEmpty(),
                onClick = {
                    onConfirm(dayTimes, programs.filter { it.id in selectedProgramIds }.map { it.id to it.name })
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) { Text(if (en) "Distribute" else "Dağıt") }
        },
    )
}
