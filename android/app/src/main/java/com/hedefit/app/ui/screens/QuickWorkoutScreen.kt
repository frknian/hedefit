package com.hedefit.app.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.CoachActionData
import com.hedefit.app.data.model.PreviousSetData
import com.hedefit.app.data.model.WorkoutExerciseData
import com.hedefit.app.data.model.WorkoutFeedbackData
import com.hedefit.app.data.model.WorkoutSetInput
import com.hedefit.app.ui.components.ActivityRing
import com.hedefit.app.ui.components.ExerciseMedia
import com.hedefit.app.ui.components.workoutExerciseImagePaths
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.state.ChatMessageState
import com.hedefit.app.data.model.ExerciseReplacementCandidate
import com.hedefit.app.data.model.WorkoutCoachContext
import com.hedefit.app.ui.theme.HedefitColors
import kotlinx.coroutines.delay

private const val MODE_PREFS = "hedefit-workout-mode"

/**
 * Aktif antrenman giriş noktası: Hızlı (yaptım/yapmadım) ya da Detaylı (ağırlık, tekrar, RPE)
 * mod. Seçim cihazda hatırlanır; iki mod arasında tek dokunuşla geçilir.
 */
@Composable
fun ActiveWorkoutScreen(
    onBack: () -> Unit,
    exercises: List<WorkoutExerciseData>,
    previousPerformance: Map<String, List<PreviousSetData>>,
    saving: Boolean,
    language: String = "tr",
    onFinish: (durationSeconds: Int, calories: Int, sets: List<WorkoutSetInput>, feedback: WorkoutFeedbackData) -> Unit,
    onRequestReplacementCandidate: (currentExerciseId: String, reason: String, discomfortArea: String?, (ExerciseReplacementCandidate?) -> Unit) -> Unit = { _, _, _, _ -> },
    onApplyReplacementCandidate: (ExerciseReplacementCandidate) -> Unit = {},
    replacementCandidate: ExerciseReplacementCandidate? = null,
    replacementBusy: Boolean = false,
    chatMessages: List<ChatMessageState> = emptyList(),
    chatBusy: Boolean = false,
    onSendChatMessage: (String, WorkoutCoachContext?) -> Unit = { _, _ -> },
    onExecuteCoachAction: (CoachActionData) -> Unit = {},
    onSkip: (postpone: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(MODE_PREFS, Context.MODE_PRIVATE) }
    var quick by remember { mutableStateOf(prefs.getBoolean("quick", true)) }
    fun setQuick(value: Boolean) { quick = value; prefs.edit().putBoolean("quick", value).apply() }
    if (quick) {
        QuickWorkoutScreen(exercises, saving, onBack, onFinish, onDetailed = { setQuick(false) }, onSkip = onSkip)
    } else {
        Box(Modifier.fillMaxSize()) {
            DetailedActiveWorkoutScreen(
                onBack, exercises, previousPerformance, saving, language, onFinish, onRequestReplacementCandidate,
                onApplyReplacementCandidate, replacementCandidate, replacementBusy, chatMessages, chatBusy, onSendChatMessage, onExecuteCoachAction, onSkip,
            )
            Surface(
                onClick = { setQuick(true) },
                color = HedefitColors.Lime, shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp),
            ) { Text(tr("⚡ Hızlı moda geç", "⚡ Switch to quick mode"), Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = HedefitColors.OnLime, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

/** Tekrar aralığının üst değeri ("8–12" → 12, "30–45 sn" → null). */
private fun repsFrom(text: String): Int? = if (text.contains("sn") || text.contains("s ")) null else Regex("\\d+").findAll(text).lastOrNull()?.value?.toIntOrNull()

@Composable
private fun QuickWorkoutScreen(
    exercises: List<WorkoutExerciseData>,
    saving: Boolean,
    onBack: () -> Unit,
    onFinish: (Int, Int, List<WorkoutSetInput>, WorkoutFeedbackData) -> Unit,
    onDetailed: () -> Unit,
    onSkip: (postpone: Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // done[exerciseIndex] = tamamlanan set sayısı
    val done = remember(exercises) { mutableStateListOf<Int>().apply { repeat(exercises.size) { add(0) } } }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var rest by remember { mutableIntStateOf(0) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var showSkip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(1000); elapsed++; if (rest > 0) rest-- } }
    val totalSets = exercises.sumOf { it.sets }
    val doneSets = done.sum()
    BackHandler { confirmExit = true }

    fun markSet(index: Int, sets: Int) {
        if (done[index] < sets) {
            done[index] = done[index] + 1
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (done[index] < sets) rest = exercises[index].restSeconds else haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else done[index] = 0
    }

    Column(Modifier.fillMaxSize().background(HedefitColors.Background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmExit = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri", "Back"), tint = HedefitColors.TextPrimary) }
            Column(Modifier.weight(1f)) {
                Text(tr("Hızlı antrenman", "Quick workout"), fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary, fontSize = 18.sp)
                Text("%02d:%02d".format(elapsed / 60, elapsed % 60), color = HedefitColors.Lime, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDetailed) { Text(tr("Detaylı mod", "Detailed mode"), color = HedefitColors.TextSecondary) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActivityRing(if (totalSets == 0) 0f else doneSets / totalSets.toFloat(), HedefitColors.Lime, size = 64.dp, stroke = 7.dp) {
                Text("$doneSets/$totalSets", fontWeight = FontWeight.Black, fontSize = 13.sp, color = HedefitColors.TextPrimary)
            }
            Spacer(Modifier.width(14.dp))
            Text(tr("Setlere dokunarak işaretle. Kartın tikine basarsan hareketin tüm setleri tamamlanır.", "Tap the dots to mark sets. The tick completes all sets of an exercise."), color = HedefitColors.TextSecondary, fontSize = 13.sp)
        }
        AnimatedVisibility(rest > 0, enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(HedefitColors.Water.copy(alpha = .15f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Dinlen", "Rest") + "  %d:%02d".format(rest / 60, rest % 60), color = HedefitColors.Water, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton(onClick = { rest = 0 }) { Text(tr("Atla", "Skip"), color = HedefitColors.Water) }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(exercises, key = { i, ex -> "${ex.id}-$i" }) { index, ex ->
                val complete = done[index] >= ex.sets
                val border by animateColorAsState(if (complete) HedefitColors.Lime else HedefitColors.Divider, label = "qw")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(HedefitColors.Surface).border(1.5.dp, border, RoundedCornerShape(20.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseMedia(workoutExerciseImagePaths(ex.id, ex.name), ex.name, Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, fontWeight = FontWeight.Bold, color = HedefitColors.TextPrimary, maxLines = 2)
                            Text("${ex.sets} × ${ex.reps}" + (ex.targetWeightKg?.let { " • ${it.toInt()} kg" } ?: ""), color = HedefitColors.TextSecondary, fontSize = 13.sp)
                        }
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(if (complete) HedefitColors.Lime else HedefitColors.SurfaceHigh)
                                .clickable { done[index] = if (complete) 0 else ex.sets; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Check, tr("Tamamlandı", "Done"), tint = if (complete) HedefitColors.OnLime else HedefitColors.TextMuted) }
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(ex.sets) { set ->
                            val on = set < done[index]
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(if (on) HedefitColors.Lime else HedefitColors.SurfaceHigh).clickable { markSet(index, ex.sets) },
                                contentAlignment = Alignment.Center,
                            ) { Text("${set + 1}", color = if (on) HedefitColors.OnLime else HedefitColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { if (doneSets > 0) confirmFinish = true else showSkip = true }, enabled = !saving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).height(56.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
        ) { Text(if (saving) tr("Kaydediliyor…", "Saving…") else tr("Antrenmanı bitir", "Finish workout"), fontWeight = FontWeight.Bold) }
    }

    if (confirmFinish) {
        var difficulty by remember { mutableStateOf("Uygun") }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(tr("Nasıl geçti?", "How was it?")) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Kolay" to tr("Kolay", "Easy"), "Uygun" to tr("Uygun", "Just right"), "Zor" to tr("Zor", "Hard")).forEach { (value, label) ->
                        FilterChip(difficulty == value, { difficulty = value }, label = { Text(label) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    val sets = exercises.flatMapIndexed { order, ex ->
                        (1..done[order]).map { n -> WorkoutSetInput(ex.id, ex.name, order, n, ex.targetWeightKg, repsFrom(ex.reps), null, null) }
                    }
                    // Tahmini yakım: kuvvet antrenmanı ~5 kcal/dk (kilo bilinmediğinde muhafazakâr).
                    onFinish(elapsed, (elapsed / 60 * 5).coerceAtLeast(10), sets, WorkoutFeedbackData(difficulty = difficulty))
                }) { Text(tr("Kaydet", "Save"), color = HedefitColors.Lime, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(tr("Devam et", "Keep going")) } },
        )
    }
    if (showSkip) SkipWorkoutDialog(onDismiss = { showSkip = false }) { postpone -> showSkip = false; onSkip(postpone) }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text(tr("Antrenmandan çıkılsın mı?", "Leave the workout?")) },
        text = { Text(tr("İşaretlediğin setler kaydedilmez.", "Marked sets won't be saved.")) },
        confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text(tr("Çık", "Leave"), color = HedefitColors.Coral) } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(tr("Vazgeç", "Cancel")) } },
    )
}

/**
 * Hiç set yapılmadan bitirme: antrenmanı bir sonraki boş güne ertele ya da bugün için
 * tamamen pas geç. İkisi de takvime işlenir; seri ve istatistikler etkilenmez.
 */
@Composable
internal fun SkipWorkoutDialog(onDismiss: () -> Unit, onChoose: (postpone: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Hiç set yapmadın", "No sets done yet")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Bugün olmuyorsa sorun değil. Ne yapalım?", "Not today? No problem. What should we do?"), color = HedefitColors.TextSecondary)
                SkipOption("📅", tr("Başka güne ertele", "Move to another day"), tr("Takvimde sıradaki boş güne otomatik eklenir.", "Added automatically to the next free day."), HedefitColors.Lime) { onChoose(true) }
                SkipOption("⏭️", tr("Tamamen pas geç", "Skip entirely"), tr("Bugün dinlenme günü olarak işaretlenir.", "Today is marked as a rest day."), HedefitColors.TextSecondary) { onChoose(false) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Antrenmana devam et", "Keep training"), color = HedefitColors.Lime) } },
    )
}

@Composable
private fun SkipOption(emoji: String, title: String, body: String, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HedefitColors.SurfaceHigh).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = accent, fontWeight = FontWeight.Bold)
            Text(body, color = HedefitColors.TextMuted, fontSize = 12.sp)
        }
    }
}
