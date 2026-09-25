package com.hedefit.app.ui.screens

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.CardioMachine
import com.hedefit.app.data.model.CardioPreset
import com.hedefit.app.data.model.cardioMachines
import com.hedefit.app.data.model.cardioPresets
import com.hedefit.app.data.model.cardioRate
import com.hedefit.app.data.model.ct
import com.hedefit.app.data.model.virtualRouteLandmarks
import com.hedefit.app.ui.components.ConfettiBurst
import com.hedefit.app.ui.components.CountUpText
import com.hedefit.app.ui.components.staggeredEntrance
import com.hedefit.app.ui.theme.HedefitColors
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private const val PREFS = "hedefit-cardio"
private const val KEY_FINISHER = "suggest_after_workout"

/** Antrenman sonrası kardiyo önerisi açık mı (MainActivity okur). */
fun cardioFinisherEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FINISHER, false)

private data class CardioSample(val second: Int, val progress: Double, val kcalPerMin: Double)

private sealed interface CardioStage {
    data object Picker : CardioStage
    data class Live(val machine: CardioMachine, val preset: CardioPreset?, val game: Boolean) : CardioStage
    data class Summary(
        val machine: CardioMachine,
        val seconds: Int,
        val kcal: Int,
        val distanceKm: Double,
        val samples: List<CardioSample>,
        val gameScore: Int,
        val newRecord: Boolean,
        val presetTitle: String?,
        val firstSession: Boolean = false,
    ) : CardioStage
}

/** Rekorla yarış: en uzun seansın ilerleme eğrisi (mesafe ya da kalori) cihazda saklanır. */
private object GhostStore {
    fun read(context: Context, machineKey: String): List<Pair<Int, Double>> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ghost_$machineKey", null)
            ?.split(";")?.mapNotNull { part -> part.split(":").takeIf { it.size == 2 }?.let { it[0].toIntOrNull()?.let { s -> it[1].toDoubleOrNull()?.let { d -> s to d } } } }
            .orEmpty()

    /** Yeni seans rekorun toplam ilerlemesini geçtiyse kaydeder; rekor kırıldıysa true. */
    fun saveIfBest(context: Context, machineKey: String, samples: List<CardioSample>): Boolean {
        val total = samples.lastOrNull()?.progress ?: return false
        val best = read(context, machineKey).lastOrNull()?.second ?: 0.0
        if (total <= best) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ghost_$machineKey", samples.joinToString(";") { "${it.second}:${"%.4f".format(Locale.US, it.progress)}" }).apply()
        return true
    }

    fun at(ghost: List<Pair<Int, Double>>, second: Int): Double? {
        if (ghost.isEmpty() || second > ghost.last().first) return null
        val after = ghost.indexOfFirst { it.first >= second }.takeIf { it >= 0 } ?: return null
        if (after == 0) return ghost[0].second * second / ghost[0].first.coerceAtLeast(1)
        val (s0, d0) = ghost[after - 1]; val (s1, d1) = ghost[after]
        return d0 + (d1 - d0) * (second - s0) / (s1 - s0).coerceAtLeast(1)
    }
}

@Composable
fun CardioScreen(
    weightKg: Double?,
    saving: Boolean,
    programsUnlocked: Boolean,
    gameUnlocked: Boolean,
    onLockedPrograms: () -> Unit,
    onLockedGame: () -> Unit,
    onBack: () -> Unit,
    onSave: (machineKey: String, seconds: Int, kcal: Int, summary: String) -> Unit,
) {
    var stage by remember { mutableStateOf<CardioStage>(CardioStage.Picker) }
    when (val current = stage) {
        CardioStage.Picker -> CardioPicker(programsUnlocked, gameUnlocked, onLockedPrograms, onLockedGame, onBack) { machine, preset, game -> stage = CardioStage.Live(machine, preset, game) }
        is CardioStage.Live -> CardioLive(current.machine, current.preset, current.game, weightKg, onCancel = { stage = CardioStage.Picker }) { summary -> stage = summary }
        is CardioStage.Summary -> CardioSummary(current, saving, onDiscard = { stage = CardioStage.Picker }) {
            val distance = if (current.machine.tracksDistance) " • ${"%.2f".format(current.distanceKm)} km" else ""
            val preset = current.presetTitle?.let { " • $it" } ?: ""
            onSave(current.machine.key, current.seconds, current.kcal, "${ct(current.machine.title)}$distance$preset")
        }
    }
}

// ---------------------------------------------------------------- Seçim

@Composable
private fun CardioPicker(
    programsUnlocked: Boolean,
    gameUnlocked: Boolean,
    onLockedPrograms: () -> Unit,
    onLockedGame: () -> Unit,
    onBack: () -> Unit,
    onStart: (CardioMachine, CardioPreset?, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var game by rememberSaveable { mutableStateOf(false) }
    var finisher by remember { mutableStateOf(prefs.getBoolean(KEY_FINISHER, false)) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(HedefitColors.Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, ct("Geri"), tint = HedefitColors.TextPrimary) }
            Text(ct("Kardiyo"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary)
        }
        Text(ct("Makineni seç, ayarları canlı değiştir; yaktığın kalori günlük hesabına eklenir."), color = HedefitColors.TextSecondary)
        Spacer(Modifier.height(16.dp))
        cardioMachines.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { i, machine ->
                    Column(
                        Modifier.weight(1f).staggeredEntrance("cardio", row * 2 + i).clip(RoundedCornerShape(20.dp)).background(HedefitColors.Surface)
                            .clickable { onStart(machine, null, game) }.padding(16.dp),
                    ) {
                        com.hedefit.app.ui.components.CardioMachineIcon(machine.key, Modifier.size(58.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(ct(machine.title), color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text(machine.controls.joinToString(" • ") { ct(it.label) }, color = HedefitColors.TextMuted, fontSize = 12.sp)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        ToggleRow(androidx.compose.material.icons.Icons.Default.SportsEsports, ct("Oyun modu"), ct("Sanal rota, rekorunla yarış ve hedef görevleri"), game, locked = !gameUnlocked) {
            if (!gameUnlocked) onLockedGame() else game = !game
        }
        ToggleRow(androidx.compose.material.icons.Icons.Default.FitnessCenter, ct("Antrenman sonrası kardiyo öner"), ct("Kuvvet antrenmanını bitirince kardiyoya geçmeyi hatırlatır"), finisher, locked = false) {
            finisher = !finisher
            prefs.edit().putBoolean(KEY_FINISHER, finisher).apply()
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ct("Hazır programlar"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = HedefitColors.TextPrimary, modifier = Modifier.weight(1f))
            if (!programsUnlocked) Icon(Icons.Default.Lock, null, tint = HedefitColors.Warning, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        cardioPresets.forEach { preset ->
            val machine = cardioMachines.first { it.key == preset.machineKey }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(HedefitColors.Surface)
                    .clickable { if (programsUnlocked) onStart(machine, preset, game) else onLockedPrograms() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.hedefit.app.ui.components.CardioMachineIcon(machine.key, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ct(preset.title), color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(ct(preset.description), color = HedefitColors.TextSecondary, fontSize = 12.sp)
                }
                Text(com.hedefit.app.ui.i18n.tr("${preset.totalSeconds / 60} dk", "${preset.totalSeconds / 60} min"), color = HedefitColors.Lime, fontWeight = FontWeight.Bold)
            }
        }
        Text(ct("Kalori tahminidir: kilo, hız, eğim ve dirence göre hesaplanır. Makinelerin gösterdiği değer genelde daha yüksektir."), color = HedefitColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun ToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, locked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).background(HedefitColors.Surface).clickable(onClick = onToggle).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = HedefitColors.Sleep, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(subtitle, color = HedefitColors.TextSecondary, fontSize = 12.sp)
        }
        if (locked) Icon(Icons.Default.Lock, null, tint = HedefitColors.Warning)
        else Switch(checked, { onToggle() }, colors = SwitchDefaults.colors(checkedTrackColor = HedefitColors.Lime, checkedThumbColor = HedefitColors.OnLime))
    }
}

// ---------------------------------------------------------------- Canlı seans

private fun formatClock(seconds: Int) = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)

private fun formatValue(value: Double, step: Double) = if (step < 1.0) "%.1f".format(value) else value.roundToInt().toString()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardioLive(
    machine: CardioMachine,
    preset: CardioPreset?,
    game: Boolean,
    weightKg: Double?,
    onCancel: () -> Unit,
    onFinish: (CardioStage.Summary) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val values = remember { mutableStateMapOf<String, Double>().apply { machine.controls.forEach { put(it.key, preset?.segments?.firstOrNull()?.targets?.get(it.key) ?: it.default) } } }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var kcal by rememberSaveable { mutableDoubleStateOf(0.0) }
    var distance by rememberSaveable { mutableDoubleStateOf(0.0) }
    var running by rememberSaveable { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val samples = remember { mutableStateListOf<CardioSample>() }
    val ghost = remember { GhostStore.read(context, machine.key) }
    // Oyun modu: hedef görevleri
    var score by rememberSaveable { mutableIntStateOf(0) }
    var challengeText by remember { mutableStateOf<String?>(null) }
    var challengeKey by remember { mutableStateOf<String?>(null) }
    var challengeTarget by remember { mutableDoubleStateOf(0.0) }
    var challengeHeld by remember { mutableIntStateOf(0) }
    var challengeLeft by remember { mutableIntStateOf(0) }
    var celebrate by remember { mutableIntStateOf(0) }

    // Seslendirme (Türkçe). Motor yoksa sessizce devre dışı kalır.
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) { engine?.language = if (com.hedefit.app.ui.i18n.AppLang.en) Locale.US else Locale("tr", "TR"); tts.value = engine } }
        onDispose { engine?.stop(); engine?.shutdown() }
    }
    fun say(text: String) { tts.value?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString()) }

    // Koşu bandında telefon önünde durur: ekranı açık tut.
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    val rate = cardioRate(machine.key, values, weightKg)
    val segmentIndex = preset?.let { p ->
        var acc = 0
        p.segments.indexOfFirst { seg -> acc += seg.seconds; elapsed < acc }.takeIf { it >= 0 } ?: p.segments.lastIndex
    }
    val segmentEnd = preset?.segments?.take((segmentIndex ?: 0) + 1)?.sumOf { it.seconds } ?: 0
    val progressMetric = if (machine.tracksDistance) distance else kcal

    // Bölüm değişince hedef ayarları uygula, titret ve seslendir.
    LaunchedEffect(segmentIndex) {
        val seg = segmentIndex?.let { preset?.segments?.getOrNull(it) } ?: return@LaunchedEffect
        seg.targets.forEach { (k, v) -> values[k] = v }
        if (elapsed > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val detail = machine.controls.mapNotNull { c -> seg.targets[c.key]?.let { "${ct(c.label)} ${formatValue(it, c.step)}" } }.joinToString(", ")
            say("${ct(seg.label)}. $detail")
        }
    }

    // Saat: her saniye yakım ve mesafe birikir.
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            val r = cardioRate(machine.key, values, weightKg)
            elapsed++
            kcal += r.activeKcalPerMinute / 60.0
            distance += r.speedKmh / 3600.0
            if (elapsed % 10 == 0) samples += CardioSample(elapsed, if (machine.tracksDistance) distance else kcal, r.activeKcalPerMinute)
            preset?.let { p -> if (elapsed == p.totalSeconds / 2) say(ct("Yarıya geldin, harika gidiyorsun.")) }
            if (preset == null && elapsed % 600 == 0) say(com.hedefit.app.ui.i18n.tr("${elapsed / 60} dakika oldu. ${kcal.roundToInt()} kalori yaktın.", "${elapsed / 60} minutes done. ${kcal.roundToInt()} calories burned."))
            // Oyun modu: 3 dakikada bir yeni görev, 60 sn tutunca başarı.
            if (game) {
                if (challengeKey == null && elapsed % 180 == 90) {
                    val control = machine.controls.first()
                    val target = ((values[control.key] ?: control.default) + control.step * 2).coerceAtMost(control.max)
                    challengeKey = control.key; challengeTarget = target; challengeHeld = 0; challengeLeft = 90
                    challengeText = com.hedefit.app.ui.i18n.tr("60 sn boyunca ${control.label} ≥ ${formatValue(target, control.step)} ${control.unit}", "Hold ${ct(control.label)} ≥ ${formatValue(target, control.step)} ${ct(control.unit)} for 60 s")
                    say(com.hedefit.app.ui.i18n.tr("Yeni görev. ", "New quest. ") + challengeText)
                }
                challengeKey?.let { key ->
                    if ((values[key] ?: 0.0) >= challengeTarget) challengeHeld++
                    challengeLeft--
                    if (challengeHeld >= 60) {
                        score += 10; celebrate++; challengeKey = null; challengeText = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress); say(ct("Görev tamam! On puan."))
                    } else if (challengeLeft <= 0) { challengeKey = null; challengeText = null }
                }
            }
            if (preset != null && elapsed >= preset.totalSeconds) { running = false; say(ct("Program tamamlandı. Tebrikler!")) }
        }
    }

    fun finish() {
        running = false
        val finalSamples = samples.toList() + CardioSample(elapsed, progressMetric, rate.activeKcalPerMinute)
        val record = elapsed >= 60 && GhostStore.saveIfBest(context, machine.key, finalSamples)
        onFinish(CardioStage.Summary(machine, elapsed, kcal.roundToInt(), distance, finalSamples, score, record, preset?.title, firstSession = ghost.isEmpty()))
    }

    BackHandler { if (locked) Unit else confirmExit = true }

    Box(Modifier.fillMaxSize().background(HedefitColors.Background)) {
        // Yerleşim (tüm makineler): üstte makine + kilit, süre, (varsa) program ve oyun;
        // ortada canlı ayarlar; en altta Duraklat / Bitir.
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.hedefit.app.ui.components.CardioMachineIcon(machine.key, Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(ct(machine.title), color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { locked = true }) { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(ct("Kilitle")) }
            }
            // Büyük saat
            Text(formatClock(elapsed), color = HedefitColors.TextPrimary, fontSize = 64.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LiveStat("${kcal.roundToInt()}", ct("kcal (tahmini)"), HedefitColors.Lime)
                if (machine.tracksDistance) LiveStat("%.2f".format(distance), ct("km"), HedefitColors.TextPrimary)
                LiveStat("%.1f".format(rate.activeKcalPerMinute), ct("kcal/dk"), HedefitColors.TextPrimary)
            }

            // Program bölümü
            if (preset != null && segmentIndex != null) {
                val seg = preset.segments[segmentIndex]
                val segStart = segmentEnd - seg.seconds
                val segProgress by animateFloatAsState(((elapsed - segStart).toFloat() / seg.seconds).coerceIn(0f, 1f), label = "segment")
                Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(18.dp)).background(HedefitColors.Surface).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ct(seg.label), color = HedefitColors.Lime, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text("${segmentIndex + 1}/${preset.segments.size} • ${formatClock((segmentEnd - elapsed).coerceAtLeast(0))}", color = HedefitColors.TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { segProgress }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)), color = HedefitColors.Lime, trackColor = HedefitColors.Divider, strokeCap = StrokeCap.Round)
                    preset.segments.getOrNull(segmentIndex + 1)?.let { next -> Text(com.hedefit.app.ui.i18n.tr("Sıradaki: ", "Next: ") + ct(next.label), color = HedefitColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
            }

            // Oyun paneli
            if (game) GamePanel(machine, progressMetric, elapsed, ghost, score, challengeText, challengeHeld)

            // Canlı ayarlar: kalan alanın ortasında
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val middleH = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = middleH).padding(vertical = 8.dp), verticalArrangement = Arrangement.Center) {
            machine.controls.forEach { control ->
                val value = values[control.key] ?: control.default
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).background(HedefitColors.Surface).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BigStepButton(Icons.Default.Remove) { values[control.key] = (value - control.step).coerceAtLeast(control.min); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val shown = control.labels?.getOrNull(value.toInt())?.let(::ct) ?: formatValue(value, control.step)
                        Text(shown, color = HedefitColors.TextPrimary, fontSize = if (control.labels != null) 24.sp else 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        Text(if (control.unit.isBlank()) ct(control.label) else "${ct(control.label)} (${ct(control.unit)})", color = HedefitColors.TextSecondary, fontSize = 12.sp)
                    }
                    BigStepButton(Icons.Default.Add) { values[control.key] = (value + control.step).coerceAtMost(control.max); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
            }
            }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { running = !running },
                    modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.SurfaceHigh, contentColor = HedefitColors.TextPrimary),
                ) { Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (running) ct("Duraklat") else ct("Devam"), fontWeight = FontWeight.Bold) }
                Button(
                    onClick = ::finish,
                    modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
                ) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text(ct("Bitir"), fontWeight = FontWeight.Bold) }
            }
        }
        ConfettiBurst(celebrate, pieceCount = 50)

        // Kilit modu: yanlışlıkla dokunmayı engeller, uzun basınca açılır.
        AnimatedVisibility(locked, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(HedefitColors.Background.copy(alpha = .92f))
                    .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}, onLongClick = { locked = false; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatClock(elapsed), color = HedefitColors.TextPrimary, fontSize = 88.sp, fontWeight = FontWeight.Black)
                    Text("${kcal.roundToInt()} kcal" + if (machine.tracksDistance) " • ${"%.2f".format(distance)} km" else "", color = HedefitColors.Lime, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(30.dp))
                    Text(ct("🔒 Açmak için uzun bas"), color = HedefitColors.TextMuted)
                }
            }
        }
    }

    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text(ct("Seans bitsin mi?")) },
        text = { Text(ct("Bitirirsen özet ekranına geçersin. Vazgeçersen kayıt yapılmaz.")) },
        confirmButton = { TextButton(onClick = { confirmExit = false; finish() }) { Text(ct("Bitir ve özetle"), color = HedefitColors.Lime) } },
        dismissButton = { TextButton(onClick = { confirmExit = false; running = false; onCancel() }) { Text(ct("Kaydetmeden çık"), color = HedefitColors.Coral) } },
    )
}

@Composable
private fun LiveStat(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(label, color = HedefitColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun BigStepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(HedefitColors.SurfaceHigh).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = HedefitColors.TextPrimary, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun GamePanel(machine: CardioMachine, progress: Double, elapsed: Int, ghost: List<Pair<Int, Double>>, score: Int, challenge: String?, held: Int) {
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(18.dp)).background(HedefitColors.Surface)
            .border(1.dp, HedefitColors.Sleep.copy(alpha = .5f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ct("Oyun modu"), color = HedefitColors.Sleep, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text(com.hedefit.app.ui.i18n.tr("$score puan", "$score pts"), color = HedefitColors.Warning, fontWeight = FontWeight.Black)
        }
        // Sanal rota (mesafe ölçen makinelerde)
        if (machine.tracksDistance) {
            val next = virtualRouteLandmarks.firstOrNull { it.first > progress }
            val prev = virtualRouteLandmarks.lastOrNull { it.first <= progress }
            val segProgress = if (next != null && prev != null) ((progress - prev.first) / (next.first - prev.first)).toFloat() else 1f
            Text("📍 " + ct(prev?.second ?: ct("Başlangıç")), color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { segProgress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = HedefitColors.Sleep, trackColor = HedefitColors.Divider, strokeCap = StrokeCap.Round)
            next?.let { Text(com.hedefit.app.ui.i18n.tr("Sıradaki: ", "Next: ") + "${ct(it.second)} (${"%.1f".format(it.first - progress)} km)", color = HedefitColors.TextSecondary, fontSize = 12.sp) }
        }
        // Rekorla yarış
        val ghostAt = GhostStore.at(ghost, elapsed)
        if (ghostAt != null) {
            val diff = progress - ghostAt
            val unit = if (machine.tracksDistance) "m" else "kcal"
            val shown = if (machine.tracksDistance) (diff * 1000).roundToInt() else diff.roundToInt()
            Text(
                if (diff >= 0) com.hedefit.app.ui.i18n.tr("👻 Rekorunun $shown $unit önündesin", "👻 $shown $unit ahead of your record") else com.hedefit.app.ui.i18n.tr("👻 Rekorunun ${-shown} $unit gerisindesin", "👻 ${-shown} $unit behind your record"),
                color = if (diff >= 0) HedefitColors.Lime else HedefitColors.Coral, fontWeight = FontWeight.Bold,
            )
        } else if (ghost.isEmpty()) Text(ct("👻 Bu ilk seansın; bitirince rekorun olacak."), color = HedefitColors.TextMuted, fontSize = 12.sp)
        // Görev
        challenge?.let {
            Text("🎯 $it", color = HedefitColors.Warning, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { held / 60f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = HedefitColors.Warning, trackColor = HedefitColors.Divider, strokeCap = StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- Özet

@Composable
private fun CardioSummary(summary: CardioStage.Summary, saving: Boolean, onDiscard: () -> Unit, onSave: () -> Unit) {
    var confirmDiscard by remember { mutableStateOf(false) }
    BackHandler { confirmDiscard = true }
    Box(Modifier.fillMaxSize().background(HedefitColors.Background)) {
        // Tam ekran: üstte başlık, ortada kalori / süre / km ve yoğunluk, en altta iki buton.
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            com.hedefit.app.ui.components.CardioMachineIcon(summary.machine.key, Modifier.size(64.dp), accent = HedefitColors.Lime)
            Text(if (summary.newRecord && summary.firstSession) ct("İlk rekorun kaydedildi! 🎉") else if (summary.newRecord) ct("Yeni rekor! 🏆") else ct("Kardiyo tamam!"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
            summary.presetTitle?.let { Text(ct(it), color = HedefitColors.TextSecondary) }
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CountUpText(summary.kcal, suffix = " kcal", color = HedefitColors.Lime, fontSizeSp = 56)
                Text(ct("Günlük kalori hedefine eklenecek (tahmini)"), color = HedefitColors.TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LiveStat(formatClock(summary.seconds), ct("süre"), HedefitColors.TextPrimary)
                    if (summary.machine.tracksDistance) LiveStat("%.2f".format(summary.distanceKm), ct("km"), HedefitColors.TextPrimary)
                    if (summary.gameScore > 0) LiveStat("${summary.gameScore}", ct("oyun puanı"), HedefitColors.TextPrimary)
                }
                if (summary.samples.size >= 3) {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(18.dp)).background(HedefitColors.Surface).padding(14.dp)) {
                        Text(ct("Yoğunluk (kcal/dk)"), color = HedefitColors.TextSecondary, fontSize = 12.sp)
                        val points = summary.samples.map { it.kcalPerMin }
                        val max = (points.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                        val lineColor = HedefitColors.Lime
                        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp)) {
                            val path = Path()
                            points.forEachIndexed { i, p ->
                                val x = size.width * i / (points.size - 1)
                                val y = size.height - (p / max * size.height * .9).toFloat()
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                            drawCircle(lineColor, 4.dp.toPx(), Offset(size.width, size.height - (points.last() / max * size.height * .9).toFloat()))
                        }
                    }
                }
                if (summary.seconds < 60) Text(ct("1 dakikadan kısa seanslar kaydedilmez."), color = HedefitColors.Warning, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { confirmDiscard = true },
                    modifier = Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(18.dp),
                ) { Text(ct("Kaydetmeden çık"), color = HedefitColors.TextSecondary, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onSave, enabled = !saving && summary.seconds >= 60,
                    modifier = Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
                ) { Text(if (saving) ct("Kaydediliyor…") else ct("Kaydet"), fontWeight = FontWeight.Bold) }
            }
        }
        if (summary.newRecord) ConfettiBurst(summary.seconds)
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(ct("Kaydetmeden çıkılsın mı?")) },
        text = { Text(ct("Bu seansın kalorisi günlük hesabına eklenmez.")) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text(ct("Çık"), color = HedefitColors.Coral) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(ct("Vazgeç")) } },
    )
}
