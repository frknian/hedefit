package com.hedefit.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.hedefit.wear.data.Snapshot
import com.hedefit.wear.exercise.ExerciseUi
import com.hedefit.wear.exercise.WorkoutKind
import kotlinx.coroutines.delay

@Composable
fun HedefitWearApp(
    snapshot: Snapshot,
    exercise: ExerciseUi,
    onAddWater: (Int) -> Unit,
    onStart: (WorkoutKind) -> Unit,
    onTogglePause: () -> Unit,
    onEnd: () -> Unit,
) {
    MaterialTheme(colors = Colors(primary = HedefitColors.Green, onPrimary = HedefitColors.OnGreen, background = Color.Black, surface = HedefitColors.Surface)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (exercise.active) ActiveWorkout(exercise, onTogglePause, onEnd)
            else {
                @OptIn(ExperimentalFoundationApi::class)
                VerticalPager(state = rememberPagerState { 3 }, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> SummaryPage(snapshot)
                        1 -> WaterPage(snapshot, onAddWater)
                        else -> StartPage(snapshot, onStart)
                    }
                }
                TimeText()
            }
        }
    }
}

@Composable
private fun SummaryPage(s: Snapshot) {
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
            Text("adım", fontSize = 11.sp, color = HedefitColors.Muted)
            if (s.streakDays > 0) Text("${s.streakDays} gün", fontSize = 11.sp, color = HedefitColors.Amber)
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
                if (kotlin.math.abs(scroll) > 40f) { amount = (amount + if (scroll > 0) 50 else -50).coerceIn(50, 1000); scroll = 0f }
                true
            }
            .focusRequester(focus).focusable(),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Su", fontSize = 12.sp, color = HedefitColors.Water)
        Text("%.1f L".format(s.waterMl / 1000f), fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = HedefitColors.Water)
        Text("hedef %.1f L".format(s.waterGoal / 1000f), fontSize = 11.sp, color = HedefitColors.Muted)
        Chip(
            onClick = { onAdd(amount); sent = true },
            label = { Text(if (sent) "Eklendi" else "+$amount ml", fontWeight = FontWeight.SemiBold) },
            colors = ChipDefaults.chipColors(backgroundColor = HedefitColors.Water, contentColor = Color(0xFF04202E)),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StartPage(s: Snapshot, onStart: (WorkoutKind) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 36.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(s.workoutName.ifBlank { "Antrenman" }, fontSize = 12.sp, color = HedefitColors.Green) }
        items(WorkoutKind.entries.toList()) { kind ->
            Chip(onClick = { onStart(kind) }, label = { Text(kind.title) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ActiveWorkout(e: ExerciseUi, onTogglePause: () -> Unit, onEnd: () -> Unit) {
    var sets by remember { mutableIntStateOf(1) }
    var rest by remember { mutableIntStateOf(0) }
    LaunchedEffect(rest) { if (rest > 0) { delay(1000); rest -= 1 } }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (e.kind == WorkoutKind.STRENGTH) {
            if (rest > 0) {
                Text("Dinlenme", fontSize = 12.sp, color = HedefitColors.Amber)
                Text(clock(rest.toLong()), fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = HedefitColors.Amber)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(onClick = { rest += 15 }, label = { Text("+15 sn", fontSize = 12.sp) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.size(width = 70.dp, height = 32.dp))
                    Chip(onClick = { rest = 0 }, label = { Text("Atla", fontSize = 12.sp) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.size(width = 60.dp, height = 32.dp))
                }
            } else {
                Text("Set $sets", fontSize = 12.sp, color = HedefitColors.Green)
                Text("♥ ${e.heartRate}", fontSize = 18.sp, color = HedefitColors.Coral)
                Text(clock(e.elapsedSeconds), fontSize = 22.sp)
                Chip(onClick = { sets += 1; rest = 90 }, label = { Text("Seti bitir", fontWeight = FontWeight.SemiBold) }, colors = ChipDefaults.chipColors(backgroundColor = HedefitColors.Green, contentColor = HedefitColors.OnGreen))
            }
        } else {
            Text(e.kind.title, fontSize = 12.sp, color = HedefitColors.Green)
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
