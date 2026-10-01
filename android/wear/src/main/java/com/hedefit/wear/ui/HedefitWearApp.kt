package com.hedefit.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.hedefit.wear.data.NavCue
import com.hedefit.wear.data.PlannedExercise
import com.hedefit.wear.data.SetLog
import com.hedefit.wear.data.Snapshot
import com.hedefit.wear.exercise.ExerciseUi
import com.hedefit.wear.exercise.WorkoutKind
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Sayfa sırası: Özet, Su, Sosyal, Sor, Antrenman. Complication'lar bu numaralarla açar. */
object Pages { const val SUMMARY = 0; const val WATER = 1; const val SOCIAL = 2; const val ASK = 3; const val START = 4 }

/** Sesli istek durumu. */
sealed interface AskState {
    data object Idle : AskState
    data object Listening : AskState
    data object Waiting : AskState
    data class Result(val text: String, val ok: Boolean) : AskState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HedefitWearApp(
    snapshot: Snapshot,
    exercise: ExerciseUi,
    loggedSets: List<SetLog>,
    connected: Boolean?,
    initialPage: Int,
    nav: NavCue?,
    askState: AskState,
    onAddWater: (Int) -> Unit,
    onStart: (WorkoutKind) -> Unit,
    onTogglePause: () -> Unit,
    onEnd: () -> Unit,
    onLogSet: (SetLog) -> Unit,
    onVoice: (String) -> Unit,
) {
    CompositionLocalProvider(LocalEnglish provides snapshot.isEnglish) {
        MaterialTheme(colors = Colors(primary = HedefitColors.Green, onPrimary = HedefitColors.OnGreen, background = Color.Black, surface = HedefitColors.Surface)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (exercise.active) ActiveWorkout(snapshot, exercise, loggedSets, onTogglePause, onEnd, onLogSet)
                else {
                    val pager = rememberPagerState(initialPage = initialPage) { 5 }
                    LaunchedEffect(initialPage) { pager.scrollToPage(initialPage) }
                    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            Pages.SUMMARY -> SummaryPage(snapshot, connected)
                            Pages.WATER -> WaterPage(snapshot, onAddWater)
                            Pages.SOCIAL -> SocialPage(snapshot)
                            Pages.ASK -> AskPage(askState, onVoice)
                            else -> StartPage(snapshot, onStart)
                        }
                    }
                    TimeText()
                }
                if (nav != null) NavOverlay(nav)
            }
        }
    }
}

@Composable
private fun SummaryPage(s: Snapshot, connected: Boolean?) {
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 9.dp.toPx()
            listOf(Triple(0f, HedefitColors.Green, s.stepFraction()), Triple(1f, HedefitColors.Water, s.waterFraction()), Triple(2f, HedefitColors.Coral, s.proteinFraction())).forEach { (ring, color, progress) ->
                val inset = width / 2 + ring * (width + 5.dp.toPx())
                val topLeft = Offset(inset, inset)
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(HedefitColors.Surface, 0f, 360f, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
                if (progress > 0f) drawArc(color, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%,d".format(s.steps), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(tx("adım", "steps"), fontSize = 11.sp, color = HedefitColors.Muted)
            if (s.streakDays > 0) Text(tx("${s.streakDays} gün", "${s.streakDays} days"), fontSize = 11.sp, color = HedefitColors.Amber)
            if (connected == false) Text(tx("Telefona bağlı değil", "Phone not connected"), fontSize = 9.sp, color = HedefitColors.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WaterPage(s: Snapshot, onAdd: (Int) -> Unit) {
    var amount by remember { mutableIntStateOf(250) }
    var scroll by remember { mutableStateOf(0f) }
    var sent by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(sent) { if (sent) { delay(900); sent = false } }
    Column(
        Modifier.fillMaxSize()
            .onRotaryScrollEvent { e ->
                scroll += e.verticalScrollPixels
                if (abs(scroll) > 40f) { amount = (amount + if (scroll > 0) 50 else -50).coerceIn(50, 1000); scroll = 0f }
                true
            }
            .focusRequester(focus).focusable(),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tx("Su", "Water"), fontSize = 12.sp, color = HedefitColors.Water)
        Text("%.1f L".format(s.waterMl / 1000f), fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = HedefitColors.Water)
        Text(tx("hedef", "goal") + " %.1f L".format(s.waterGoal / 1000f), fontSize = 11.sp, color = HedefitColors.Muted)
        Chip(
            onClick = { onAdd(amount); sent = true },
            label = { Text(if (sent) tx("Eklendi", "Added") else "+$amount ml", fontWeight = FontWeight.SemiBold) },
            colors = ChipDefaults.chipColors(backgroundColor = HedefitColors.Water, contentColor = Color(0xFF04202E)),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Haftalık sıralama ve meydan okuma; telefon veriyi yüklemediyse boş durum gösterir. */
@Composable
private fun SocialPage(s: Snapshot) {
    val social = s.social
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tx("Bu hafta", "This week"), fontSize = 12.sp, color = HedefitColors.Green)
        if (social.rank <= 0) {
            Text(tx("Sıralama için telefonda Arkadaşlar sayfasını aç.", "Open Friends on your phone to see rankings."), fontSize = 12.sp, color = HedefitColors.Muted, textAlign = TextAlign.Center)
        } else {
            Text("#${social.rank}", fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("${social.weeklyXp} XP", fontSize = 12.sp, color = HedefitColors.Amber)
            social.leaders.forEachIndexed { i, l ->
                Text("${i + 1}. ${l.name.take(12)}  ${l.xp}", fontSize = 11.sp, color = if (l.me) HedefitColors.Green else HedefitColors.Muted, maxLines = 1)
            }
            if (social.challenge.isNotBlank()) Text(social.challenge.take(22), fontSize = 11.sp, color = HedefitColors.Coral, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Sesle Fit Koç'a soru sor veya yemek ekle; yanıt telefondan gelir. */
@Composable
private fun AskPage(state: AskState, onVoice: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            AskState.Idle -> {
                Chip(onClick = { onVoice("chat") }, label = { Text(tx("Fit Koç'a sor", "Ask Fit Coach")) }, colors = ChipDefaults.chipColors(backgroundColor = HedefitColors.Green, contentColor = HedefitColors.OnGreen), modifier = Modifier.fillMaxWidth())
                Chip(onClick = { onVoice("food") }, label = { Text(tx("Yemek ekle", "Log food")) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            AskState.Listening -> Text(tx("Dinleniyor…", "Listening…"), color = HedefitColors.Muted)
            AskState.Waiting -> Text(tx("Yanıt bekleniyor…", "Waiting for reply…"), color = HedefitColors.Muted)
            is AskState.Result -> Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.text, fontSize = 12.sp, color = if (state.ok) Color.White else HedefitColors.Coral, textAlign = TextAlign.Center)
                Chip(onClick = { onVoice("chat") }, label = { Text(tx("Tekrar", "Again")) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun StartPage(s: Snapshot, onStart: (WorkoutKind) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(top = 36.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(s.workoutName.ifBlank { tx("Antrenman", "Workout") }, fontSize = 12.sp, color = HedefitColors.Green) }
        items(WorkoutKind.entries.toList()) { kind ->
            Chip(onClick = { onStart(kind) }, label = { Text(tx(kind.title, kind.titleEn)) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Rota yönlendirme: ok ve mesafe 12 sn gösterilir, titreşim servis tarafında verilir. */
@Composable
private fun NavOverlay(cue: NavCue) {
    val arrow = when (cue.type) {
        "LEFT", "SHARP_LEFT" -> "←"; "SLIGHT_LEFT" -> "↖"; "RIGHT", "SHARP_RIGHT" -> "→"; "SLIGHT_RIGHT" -> "↗"; "ARRIVE" -> "⚑"; else -> "↑"
    }
    Column(Modifier.fillMaxSize().background(Color.Black), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(arrow, fontSize = 64.sp, color = HedefitColors.Green)
        Text("${cue.distanceM} m", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun parseKg(planned: PlannedExercise?, last: SetLog?): Double = last?.weightKg ?: planned?.kg ?: 20.0

@Composable
private fun ActiveWorkout(s: Snapshot, e: ExerciseUi, logged: List<SetLog>, onTogglePause: () -> Unit, onEnd: () -> Unit, onLogSet: (SetLog) -> Unit) {
    var rest by remember { mutableIntStateOf(0) }
    LaunchedEffect(rest) { if (rest > 0) { delay(1000); rest -= 1 } }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (e.kind == WorkoutKind.STRENGTH) StrengthBody(s, e, logged, rest, { rest = it }, onLogSet)
        else {
            Text(tx(e.kind.title, e.kind.titleEn), fontSize = 12.sp, color = HedefitColors.Green)
            Text("%.2f km".format(e.distanceM / 1000), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("${if (e.paceSecondsPerKm > 0) clock(e.paceSecondsPerKm.toLong()) else "--:--"} /km   ${clock(e.elapsedSeconds)}", fontSize = 13.sp)
            Text("♥ ${e.heartRate}", fontSize = 14.sp, color = HedefitColors.Coral)
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onTogglePause, modifier = Modifier.size(38.dp), colors = ButtonDefaults.secondaryButtonColors()) { Text(if (e.paused) "▶" else "❚❚", fontSize = 13.sp) }
            Button(onClick = onEnd, modifier = Modifier.size(38.dp), colors = ButtonDefaults.buttonColors(backgroundColor = HedefitColors.Coral)) { Text("■", fontSize = 13.sp) }
        }
    }
}

/**
 * Ağırlık antrenmanı: telefondaki programdan egzersiz, tuşla kg, +/- ile tekrar.
 * Program yoksa yalnızca set sayacı ve dinlenme çalışır.
 */
@Composable
private fun StrengthBody(s: Snapshot, e: ExerciseUi, logged: List<SetLog>, rest: Int, setRest: (Int) -> Unit, onLogSet: (SetLog) -> Unit) {
    val plan = s.exercises
    var index by rememberSaveable { mutableIntStateOf(0) }
    val current = plan.getOrNull(index.coerceIn(0, (plan.size - 1).coerceAtLeast(0)))
    val doneForCurrent = logged.count { it.exerciseId == current?.id }
    val lastForCurrent = logged.lastOrNull { it.exerciseId == current?.id }
    var kg by remember(current?.id, doneForCurrent) { mutableDoubleStateOf(parseKg(current, lastForCurrent)) }
    var reps by remember(current?.id, doneForCurrent) { mutableIntStateOf(current?.reps ?: 10) }
    var scroll by remember { mutableStateOf(0f) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    if (rest > 0) {
        Text(tx("Dinlenme", "Rest"), fontSize = 12.sp, color = HedefitColors.Amber)
        Text(clock(rest.toLong()), fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = HedefitColors.Amber)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(onClick = { setRest(rest + 15) }, label = { Text("+15 " + tx("sn", "s"), fontSize = 12.sp) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.size(width = 70.dp, height = 32.dp))
            Chip(onClick = { setRest(0) }, label = { Text(tx("Atla", "Skip"), fontSize = 12.sp) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.size(width = 60.dp, height = 32.dp))
        }
        return
    }
    Column(
        Modifier.onRotaryScrollEvent { ev ->
            scroll += ev.verticalScrollPixels
            if (abs(scroll) > 30f) { kg = (kg + if (scroll > 0) 2.5 else -2.5).coerceIn(0.0, 500.0); scroll = 0f }
            true
        }.focusRequester(focus).focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (current != null) {
            Text(
                current.name, fontSize = 12.sp, color = HedefitColors.Green, maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp).clickableNoRipple { if (plan.size > 1) index = (index + 1) % plan.size },
            )
            Text("Set ${doneForCurrent + 1}/${current.sets}  ♥ ${e.heartRate}", fontSize = 11.sp, color = HedefitColors.Muted)
            Stepper("%.1f kg".format(kg), { kg = (kg - 2.5).coerceAtLeast(0.0) }, { kg = (kg + 2.5).coerceAtMost(500.0) })
            Stepper("$reps " + tx("tekrar", "reps"), { reps = (reps - 1).coerceAtLeast(1) }, { reps = (reps + 1).coerceAtMost(100) })
            Chip(
                onClick = {
                    onLogSet(SetLog(current.id, current.name, index, doneForCurrent + 1, kg, reps))
                    setRest(current.restSeconds)
                    // Planlanan setler bitince sıradaki egzersize geç.
                    if (doneForCurrent + 1 >= current.sets && index < plan.size - 1) index += 1
                },
                label = { Text(tx("Seti bitir", "Finish set"), fontWeight = FontWeight.SemiBold) },
                colors = ChipDefaults.chipColors(backgroundColor = HedefitColors.Green, contentColor = HedefitColors.OnGreen),
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            val freeName = tx("Antrenman", "Workout")
            Text("Set ${logged.size + 1}", fontSize = 12.sp, color = HedefitColors.Green)
            Text("♥ ${e.heartRate}", fontSize = 18.sp, color = HedefitColors.Coral)
            Text(clock(e.elapsedSeconds), fontSize = 22.sp)
            Chip(
                onClick = { onLogSet(SetLog("free", freeName, 0, logged.size + 1, 0.0, 0)); setRest(90) },
                label = { Text(tx("Seti bitir", "Finish set"), fontWeight = FontWeight.SemiBold) },
                colors = ChipDefaults.chipColors(backgroundColor = HedefitColors.Green, contentColor = HedefitColors.OnGreen),
            )
        }
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = onMinus, modifier = Modifier.size(28.dp), colors = ButtonDefaults.secondaryButtonColors()) { Text("−", fontSize = 14.sp) }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.size(width = 74.dp, height = 22.dp), textAlign = TextAlign.Center)
        Button(onClick = onPlus, modifier = Modifier.size(28.dp), colors = ButtonDefaults.secondaryButtonColors()) { Text("+", fontSize = 14.sp) }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}
