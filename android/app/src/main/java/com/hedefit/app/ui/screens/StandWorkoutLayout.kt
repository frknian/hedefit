package com.hedefit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.PreviousSetData
import com.hedefit.app.data.model.WorkoutExerciseData
import com.hedefit.app.data.model.WorkoutSetInput
import com.hedefit.app.ui.components.ExerciseMotionPlayer
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.ProgressRing
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.layout.LayoutPolicy
import com.hedefit.app.ui.theme.HedefitColors

/** Standa yerleştirilen tablet için büyük, uzaktan okunur antrenman yerleşimi. */
@Composable
internal fun StandWorkoutLayout(
    exercises: List<WorkoutExerciseData>,
    exerciseIndex: Int,
    completedSets: List<WorkoutSetInput>,
    currentSet: Int,
    totalSets: Int,
    weight: Int,
    reps: Int,
    rpe: Int,
    setType: String,
    restSeconds: Int,
    restTimerPaused: Boolean,
    timerSeconds: Int,
    timerRunning: Boolean,
    previous: List<PreviousSetData>,
    personalRecord: Boolean,
    saving: Boolean,
    isLastSet: Boolean,
    modifier: Modifier = Modifier,
    onWeight: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onRpe: (Int) -> Unit,
    onSetType: (String) -> Unit,
    onSkipRest: () -> Unit,
    onAddRest: () -> Unit,
    onToggleRest: () -> Unit,
    onTimer: (Int) -> Unit,
    onToggleTimer: () -> Unit,
    onComplete: () -> Unit,
    onSkipExercise: () -> Unit,
    onOpenReplacement: () -> Unit,
) {
    val exercise = exercises.getOrNull(exerciseIndex)
    BoxWithConstraints(modifier) {
        val sideBySide = LayoutPolicy.standSideBySide(maxWidth.value.toInt(), maxHeight.value.toInt())
        val video: @Composable (Modifier) -> Unit = { m ->
            StandVideoPanel(exercise, exercises, exerciseIndex, completedSets, currentSet, totalSets, m)
        }
        val controls: @Composable (Modifier) -> Unit = { m ->
            StandControls(
                currentSet, totalSets, weight, reps, rpe, setType, restSeconds, restTimerPaused, timerSeconds, timerRunning,
                previous, personalRecord, saving, isLastSet, exercise?.restSeconds ?: 90, m,
                onWeight, onReps, onRpe, onSetType, onSkipRest, onAddRest, onToggleRest, onTimer, onToggleTimer, onComplete, onSkipExercise, onOpenReplacement,
            )
        }
        if (sideBySide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                video(Modifier.weight(1.15f).fillMaxHeight())
                controls(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                video(Modifier.fillMaxWidth().weight(.9f))
                controls(Modifier.fillMaxWidth().weight(1.1f).verticalScroll(rememberScrollState()))
            }
        }
    }
}

@Composable
private fun StandVideoPanel(
    exercise: WorkoutExerciseData?,
    exercises: List<WorkoutExerciseData>,
    exerciseIndex: Int,
    completedSets: List<WorkoutSetInput>,
    currentSet: Int,
    totalSets: Int,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HedefitCard(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(0.dp)) {
            Box(Modifier.fillMaxSize()) {
                if (exercise != null) ExerciseMotionPlayer(exercise.id, exercise.name, exercise.name, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, HedefitColors.Background.copy(alpha = .94f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                    Text(
                        tr("Hareket ${exerciseIndex + 1}/${exercises.size}", "Exercise ${exerciseIndex + 1}/${exercises.size}"),
                        color = HedefitColors.Lime, style = MaterialTheme.typography.titleMedium,
                    )
                    Text(exercise?.name ?: tr("Program yüklenemedi", "Program couldn't load"), style = MaterialTheme.typography.headlineLarge, fontSize = 40.sp, lineHeight = 44.sp)
                    if (exercise != null) Text(
                        tr("Hedef ${exercise.reps} tekrar · ${exercise.area}", "Target ${exercise.reps} reps · ${exercise.area}"),
                        color = HedefitColors.TextSecondary, style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        SetDots(currentSet, totalSets)
        ExerciseQueue(exercises, exerciseIndex, completedSets)
    }
}

/** Set sayacı: tamamlananlar dolu, mevcut set çerçeveli, kalanlar soluk. */
@Composable
private fun SetDots(currentSet: Int, totalSets: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SET", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
        Text("$currentSet / $totalSets", color = HedefitColors.Lime, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        for (index in 1..totalSets) {
            val dot = Modifier.size(22.dp).clip(CircleShape)
            Box(
                when {
                    index < currentSet -> dot.background(HedefitColors.Lime)
                    index == currentSet -> dot.border(BorderStroke(3.dp, HedefitColors.Lime), CircleShape)
                    else -> dot.background(HedefitColors.SurfaceSoft)
                },
            )
        }
    }
}

@Composable
private fun ExerciseQueue(exercises: List<WorkoutExerciseData>, exerciseIndex: Int, completedSets: List<WorkoutSetInput>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(exercises) { index, item ->
            val done = completedSets.count { it.exerciseId == item.id } >= item.sets
            val current = index == exerciseIndex
            Box(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (current) HedefitColors.Lime.copy(alpha = .18f) else HedefitColors.Surface)
                    .border(BorderStroke(if (current) 2.dp else 0.dp, if (current) HedefitColors.Lime else Color.Transparent), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (done) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = HedefitColors.Lime)
                    Text(item.name, color = if (current) HedefitColors.TextPrimary else HedefitColors.TextSecondary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StandControls(
    currentSet: Int,
    totalSets: Int,
    weight: Int,
    reps: Int,
    rpe: Int,
    setType: String,
    restSeconds: Int,
    restTimerPaused: Boolean,
    timerSeconds: Int,
    timerRunning: Boolean,
    previous: List<PreviousSetData>,
    personalRecord: Boolean,
    saving: Boolean,
    isLastSet: Boolean,
    restTotalSeconds: Int,
    modifier: Modifier,
    onWeight: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onRpe: (Int) -> Unit,
    onSetType: (String) -> Unit,
    onSkipRest: () -> Unit,
    onAddRest: () -> Unit,
    onToggleRest: () -> Unit,
    onTimer: (Int) -> Unit,
    onToggleTimer: () -> Unit,
    onComplete: () -> Unit,
    onSkipExercise: () -> Unit,
    onOpenReplacement: () -> Unit,
) {
    val resting = restSeconds > 0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StandTimerCard(resting, restSeconds, restTotalSeconds, restTimerPaused, timerSeconds, timerRunning, onAddRest, onToggleRest, onTimer, onToggleTimer)
        previous.getOrNull(currentSet - 1)?.let { old ->
            Text(
                tr("Önceki: ", "Previous: ") + "${old.weightKg?.let { "${it.toInt()} kg" } ?: tr("vücut ağırlığı", "bodyweight")} × ${old.reps ?: "—"}",
                color = HedefitColors.TextSecondary, style = MaterialTheme.typography.titleMedium,
            )
        }
        if (personalRecord) Text(tr("Yeni kişisel rekor!", "New personal record!"), color = HedefitColors.Lime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StandStepper("$weight", "kg", tr("Ağırlık", "Weight"), Modifier.weight(1f), { onWeight(-5) }, { onWeight(5) })
            StandStepper("$reps", tr("tekrar", "reps"), tr("Tekrar", "Reps"), Modifier.weight(1f), { onReps(-1) }, { onReps(1) })
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(listOf("warmup" to tr("Isınma", "Warm-up"), "normal" to "Normal", "superset" to tr("Süper", "Superset"), "dropset" to "Drop", "failure" to tr("Tükeniş", "Failure"))) { _, (value, label) ->
                FilterChip(selected = setType == value, onClick = { onSetType(value) }, label = { Text(label, style = MaterialTheme.typography.titleSmall) }, modifier = Modifier.height(44.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Efor (RPE)", "Effort (RPE)"), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
            Slider(value = rpe.toFloat(), onValueChange = { onRpe(it.toInt()) }, valueRange = 1f..10f, steps = 8, modifier = Modifier.weight(1f))
            Text("$rpe/10", color = HedefitColors.Lime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        val label = when {
            saving -> tr("Kaydediliyor…", "Saving…")
            resting -> tr("Hazırım · Sonraki Set", "Ready · Next Set")
            isLastSet -> tr("Antrenmanı Değerlendir", "Review Workout")
            else -> tr("Seti Tamamla", "Complete Set")
        }
        StandPrimaryAction(label, if (saving) ({}) else if (resting) onSkipRest else onComplete)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StandSecondaryAction(Icons.Default.SkipNext, tr("Hareketi atla", "Skip exercise"), Modifier.weight(1f), onSkipExercise)
            StandSecondaryAction(Icons.Default.SwapHoriz, tr("Hareketi değiştir", "Swap exercise"), Modifier.weight(1f), onOpenReplacement)
        }
    }
}

/** Dinlenme sırasında dev geri sayım halkası; dinlenme yokken hızlı süre seçimli sayaç. */
@Composable
private fun StandTimerCard(
    resting: Boolean,
    restSeconds: Int,
    restTotalSeconds: Int,
    restTimerPaused: Boolean,
    timerSeconds: Int,
    timerRunning: Boolean,
    onAddRest: () -> Unit,
    onToggleRest: () -> Unit,
    onTimer: (Int) -> Unit,
    onToggleTimer: () -> Unit,
) {
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            val seconds = if (resting) restSeconds else timerSeconds
            val progress = if (resting) restSeconds / restTotalSeconds.coerceAtLeast(restSeconds).coerceAtLeast(1).toFloat()
            else if (timerSeconds > 0) 1f else 0f
            ProgressRing(progress, Modifier.size(190.dp), 14.dp, if (resting) HedefitColors.Lime else HedefitColors.Lime.copy(alpha = .6f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (resting) tr("Dinlenme", "Rest") else tr("Süre", "Timer"), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
                    Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (resting) {
                    StandSecondaryAction(null, "+30 sn", Modifier.fillMaxWidth(), onAddRest)
                    StandSecondaryAction(if (restTimerPaused) Icons.Default.PlayArrow else Icons.Default.Pause, if (restTimerPaused) tr("Devam", "Resume") else tr("Duraklat", "Pause"), Modifier.fillMaxWidth(), onToggleRest)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 90, 120).forEach { s ->
                            FilterChip(selected = timerSeconds == s, onClick = { onTimer(s) }, label = { Text("${s / 60}:${"%02d".format(s % 60)}", style = MaterialTheme.typography.titleSmall) }, modifier = Modifier.height(44.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StandSecondaryAction(if (timerRunning) Icons.Default.Pause else Icons.Default.PlayArrow, if (timerRunning) tr("Duraklat", "Pause") else tr("Başlat", "Start"), Modifier.weight(1f), onToggleTimer)
                        StandSecondaryAction(null, tr("Sıfırla", "Reset"), Modifier.weight(1f)) { onTimer(0) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandStepper(value: String, unit: String, label: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    HedefitCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 68.sp)
                Text(unit, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StandRoundButton(Icons.Default.Remove, "-$label", onMinus)
                StandRoundButton(Icons.Default.Add, "+$label", onPlus)
            }
        }
    }
}

private val StandTouchTarget: Dp = 72.dp

@Composable
private fun StandRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(StandTouchTarget).clip(CircleShape).background(HedefitColors.SurfaceSoft)
            .clickable(onClick = onClick).semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(34.dp), tint = HedefitColors.Lime) }
}

@Composable
private fun StandPrimaryAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(24.dp)).background(HedefitColors.Lime).clickable(onClick = onClick).semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = HedefitColors.OnLime, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun StandSecondaryAction(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(60.dp).clip(RoundedCornerShape(18.dp)).background(HedefitColors.SurfaceSoft).clickable(onClick = onClick).semantics { role = Role.Button }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(24.dp), tint = HedefitColors.Lime); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}
