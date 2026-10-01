package com.hedefit.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.ui.theme.HedefitColors
import kotlinx.coroutines.launch

private const val PREFS = "hedefit-curl-game"

/** Oyundaki kas grupları; gelişim cihazda kalıcı tutulur. */
private enum class Muscle(private val tr: String, private val en: String) {
    Arms("Kol", "Arms"), Upper("Omuz & göğüs", "Shoulders & chest"), Legs("Bacak", "Legs"), Core("Karın", "Core");
    val label: String get() = com.hedefit.app.ui.i18n.tr(tr, en)
}

/** Ekranın üstündeki aletler: her biri bir hareket yapar ve bir kas grubunu büyütür. */
private enum class Gear(private val titleTr: String, private val titleEn: String, private val moveTr: String, private val moveEn: String, val muscle: Muscle) {
    Dumbbell("Dambıl", "Dumbbell", "Biceps curl", "Biceps curl", Muscle.Arms),
    Barbell("Halter", "Barbell", "Omuz press", "Shoulder press", Muscle.Upper),
    Squat("Squat", "Squat", "Vücut ağırlığı squat", "Bodyweight squat", Muscle.Legs),
    PullBar("Barfiks", "Pull-up bar", "Bacak kaldırma", "Leg raise", Muscle.Core);
    val title: String get() = com.hedefit.app.ui.i18n.tr(titleTr, titleEn)
    val move: String get() = com.hedefit.app.ui.i18n.tr(moveTr, moveEn)
}

/** Tekrar → kas büyüklüğü: başta hızlı, sonra yavaşça doyar (1.0 … 2.4). */
private fun growth(reps: Int) = 1f + reps / (reps + 40f) * 1.4f

private fun title(total: Int) = when {
    total >= 1000 -> com.hedefit.app.ui.i18n.tr("Olimpiya adayı 🏆", "Olympic hopeful 🏆")
    total >= 500 -> com.hedefit.app.ui.i18n.tr("Salonun yıldızı 🌟", "Gym star 🌟")
    total >= 200 -> com.hedefit.app.ui.i18n.tr("Kas makinesi 💪", "Muscle machine 💪")
    total >= 80 -> com.hedefit.app.ui.i18n.tr("Pompa geldi 🔥", "Feeling the pump 🔥")
    total >= 20 -> com.hedefit.app.ui.i18n.tr("Isındın", "Warmed up")
    total >= 1 -> com.hedefit.app.ui.i18n.tr("Başladın", "Getting started")
    else -> com.hedefit.app.ui.i18n.tr("Bir alet seç ve dokun", "Pick a tool and tap")
}

/**
 * Bekleme ekranı mini oyunu: üstten alet seç, karta dokundukça hareketi yap.
 * Her alet farklı kas grubunu geliştirir; karakter zamanla kaslı hale gelir.
 */
@Composable
fun CurlGame(modifier: Modifier = Modifier, status: String, statusIsError: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
    val lifetime = remember { mutableStateMapOf<Muscle, Int>().apply { Muscle.entries.forEach { put(it, prefs.getInt("reps_${it.name}", 0)) } } }
    var gear by remember { mutableStateOf(Gear.Dumbbell) }
    var sessionReps by remember { mutableIntStateOf(0) }
    var popKey by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    val move = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val sizes = Muscle.entries.associateWith { m ->
        animateFloatAsState(growth(lifetime[m] ?: 0), spring(dampingRatio = .45f, stiffness = Spring.StiffnessLow), label = "muscle_${m.name}").value
    }
    val pump by animateFloatAsState(if (move.value > .6f) 1.12f else 1f, spring(dampingRatio = .5f), label = "pump")
    val total = lifetime.values.sum()

    fun rep() {
        val m = gear.muscle
        lifetime[m] = (lifetime[m] ?: 0) + 1
        prefs.edit().putInt("reps_${m.name}", lifetime[m] ?: 0).apply()
        sessionReps++
        popKey++
        haptic.performHapticFeedback(if (sessionReps % 10 == 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        scope.launch {
            move.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
            move.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        }
    }

    Column(
        modifier.fillMaxWidth().background(HedefitColors.Surface, RoundedCornerShape(28.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Alet rafı
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gear.entries.forEach { g ->
                val selected = g == gear
                val border by animateColorAsState(if (selected) HedefitColors.Lime else HedefitColors.Divider, label = "gearBorder")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (selected) HedefitColors.Lime.copy(alpha = .14f) else HedefitColors.SurfaceHigh)
                        .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
                        .clickable { gear = g; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GearIcon(g, if (selected) HedefitColors.Lime else HedefitColors.TextPrimary, Modifier.size(28.dp))
                    Text(g.title, color = HedefitColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(g.muscle.label, color = HedefitColors.TextMuted, fontSize = 9.sp, maxLines = 1)
                }
            }
        }

        // Sahne: dokundukça tekrar
        Box(
            Modifier.fillMaxWidth().height(270.dp).padding(top = 6.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::rep),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) { drawAthlete(gear, move.value, sizes) }
            Column(Modifier.align(Alignment.TopStart)) {
                Text(com.hedefit.app.ui.i18n.tr("TEKRAR", "REPS"), color = HedefitColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("$sessionReps", color = HedefitColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Black, modifier = Modifier.graphicsLayer { scaleX = pump; scaleY = pump })
            }
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                Text(com.hedefit.app.ui.i18n.tr("TOPLAM", "TOTAL"), color = HedefitColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("$total", color = HedefitColors.Lime, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            if (popKey > 0) FloatingPlusOne(popKey, gear.muscle.label, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            Text("${gear.move} • " + com.hedefit.app.ui.i18n.tr("dokun", "tap"), color = HedefitColors.TextMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter))
        }

        // Kas gelişimi
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Muscle.entries.forEach { m ->
                val reps = lifetime[m] ?: 0
                val level = reps / 25 + 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.label, Modifier.width(96.dp), color = if (m == gear.muscle) HedefitColors.Lime else HedefitColors.TextSecondary, fontSize = 12.sp, fontWeight = if (m == gear.muscle) FontWeight.Bold else FontWeight.Normal)
                    LinearProgressIndicator(
                        progress = { (reps % 25) / 25f },
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)),
                        color = if (m == gear.muscle) HedefitColors.Lime else HedefitColors.Water,
                        trackColor = HedefitColors.Divider,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(com.hedefit.app.ui.i18n.tr("Sv $level", "Lv $level"), Modifier.width(44.dp), color = HedefitColors.TextMuted, fontSize = 11.sp, textAlign = TextAlign.End)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (total > 0) androidx.compose.material3.TextButton(onClick = { confirmReset = true }) {
            Text(com.hedefit.app.ui.i18n.tr("↺ Gelişimi sıfırla", "↺ Reset progress"), color = HedefitColors.TextMuted, fontSize = 12.sp)
        }
        Text(title(total), color = HedefitColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (sessionReps == 0) com.hedefit.app.ui.i18n.tr("Üstten bir alet seç, adama dokundukça hareketi yap. Verilerin hazır olunca ekran kendiliğinden açılır.", "Pick a tool above and tap the athlete to do reps. The app opens by itself when your data is ready.") else status,
            color = if (statusIsError && sessionReps > 0) HedefitColors.Warning else HedefitColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }

    if (confirmReset) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(com.hedefit.app.ui.i18n.tr("Gelişim sıfırlansın mı?", "Reset progress?")) },
        text = { Text(com.hedefit.app.ui.i18n.tr("Tüm kas seviyeleri ve toplam tekrar sıfırlanır; adam baştan başlar.", "All muscle levels and total reps are reset; the athlete starts over.")) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                confirmReset = false
                val editor = prefs.edit()
                Muscle.entries.forEach { lifetime[it] = 0; editor.remove("reps_${it.name}") }
                editor.apply()
                sessionReps = 0
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) { Text(com.hedefit.app.ui.i18n.tr("Sıfırla", "Reset"), color = HedefitColors.Coral) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmReset = false }) { Text(com.hedefit.app.ui.i18n.tr("Vazgeç", "Cancel")) } },
    )
}

/** Aletlerin çizilmiş ikonları (Unicode'da dambıl/halter/barfiks emojisi yok). */
@Composable
private fun GearIcon(gear: Gear, color: Color, modifier: Modifier) {
    val plate = HedefitColors.Warning
    Canvas(modifier) {
        val w = size.width; val h = size.height; val u = w / 24f
        when (gear) {
            Gear.Dumbbell -> {
                drawLine(color, Offset(6 * u, h / 2), Offset(18 * u, h / 2), 2f * u, StrokeCap.Round)
                for (x in listOf(3.5f, 17f)) drawRoundRect(plate, Offset(x * u, h / 2 - 5 * u), Size(3.5f * u, 10 * u), CornerRadius(u))
                for (x in listOf(1.5f, 20.5f)) drawRoundRect(plate.copy(alpha = .75f), Offset(x * u, h / 2 - 3 * u), Size(2 * u, 6 * u), CornerRadius(u))
            }
            Gear.Barbell -> {
                drawLine(color, Offset(0f, h / 2), Offset(w, h / 2), 1.6f * u, StrokeCap.Round)
                for (x in listOf(3f, 18.5f)) drawRoundRect(plate, Offset(x * u, h / 2 - 7 * u), Size(2.5f * u, 14 * u), CornerRadius(u))
                for (x in listOf(5.8f, 16f)) drawRoundRect(plate.copy(alpha = .8f), Offset(x * u, h / 2 - 5 * u), Size(2.2f * u, 10 * u), CornerRadius(u))
            }
            Gear.Squat -> {
                // Yandan çömelmiş figür: kafa, gövde, bükülü bacak, öne uzanan kol
                drawCircle(color, 2.4f * u, Offset(9 * u, 4.5f * u))
                drawLine(color, Offset(9 * u, 7 * u), Offset(7 * u, 14 * u), 2f * u, StrokeCap.Round)
                drawLine(color, Offset(9 * u, 9 * u), Offset(17 * u, 9 * u), 1.8f * u, StrokeCap.Round)
                drawLine(color, Offset(7 * u, 14 * u), Offset(14 * u, 16 * u), 2.2f * u, StrokeCap.Round)
                drawLine(color, Offset(14 * u, 16 * u), Offset(12 * u, 22 * u), 2.2f * u, StrokeCap.Round)
                drawLine(plate, Offset(3 * u, 22.5f * u), Offset(21 * u, 22.5f * u), 1.2f * u, StrokeCap.Round)
            }
            Gear.PullBar -> {
                drawLine(color.copy(alpha = .6f), Offset(3 * u, 3 * u), Offset(3 * u, 23 * u), 1.6f * u, StrokeCap.Round)
                drawLine(color.copy(alpha = .6f), Offset(21 * u, 3 * u), Offset(21 * u, 23 * u), 1.6f * u, StrokeCap.Round)
                drawLine(plate, Offset(1.5f * u, 4 * u), Offset(22.5f * u, 4 * u), 2f * u, StrokeCap.Round)
                // Asılı küçük figür
                drawLine(color, Offset(9 * u, 4 * u), Offset(10.5f * u, 9 * u), 1.4f * u, StrokeCap.Round)
                drawLine(color, Offset(15 * u, 4 * u), Offset(13.5f * u, 9 * u), 1.4f * u, StrokeCap.Round)
                drawCircle(color, 1.8f * u, Offset(12 * u, 10 * u))
                drawLine(color, Offset(12 * u, 12 * u), Offset(12 * u, 17 * u), 1.8f * u, StrokeCap.Round)
                drawLine(color, Offset(12 * u, 17 * u), Offset(10.5f * u, 21.5f * u), 1.5f * u, StrokeCap.Round)
                drawLine(color, Offset(12 * u, 17 * u), Offset(13.5f * u, 21.5f * u), 1.5f * u, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun FloatingPlusOne(key: Int, muscle: String, modifier: Modifier) {
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(1f, tween(650)) }
    if (anim.value >= 1f) return
    Text(
        "+1 $muscle",
        modifier.graphicsLayer { translationY = -anim.value * 36.dp.toPx(); alpha = 1f - anim.value },
        color = HedefitColors.Lime, fontWeight = FontWeight.Black, fontSize = 16.sp,
    )
}

/** Önden görünen sporcu. t: hareketin anlık konumu (0 = başlangıç, 1 = tepe). */
private fun DrawScope.drawAthlete(gear: Gear, t: Float, sizes: Map<Muscle, Float>) {
    val u = size.minDimension / 100f
    val cx = size.width / 2f
    val ink = HedefitColors.TextPrimary
    val muscle = HedefitColors.Lime
    val arms = sizes[Muscle.Arms] ?: 1f
    val upper = sizes[Muscle.Upper] ?: 1f
    val legs = sizes[Muscle.Legs] ?: 1f
    val core = sizes[Muscle.Core] ?: 1f
    fun p(x: Float, y: Float) = Offset(cx + x * u, y * u)

    // Squat: kalça ve gövde aşağı iner, dizler dışa açılır.
    val squat = if (gear == Gear.Squat) t else 0f
    val drop = 12f * squat
    // Barfiks: adam bara asılı; bar sahnenin üstünde.
    val hanging = gear == Gear.PullBar
    if (hanging) {
        drawLine(HedefitColors.TextSecondary, p(-30f, 4f), p(30f, 4f), 2.2f * u, StrokeCap.Round)
        drawLine(HedefitColors.TextMuted, p(-30f, 4f), p(-30f, 96f), 1.2f * u)
        drawLine(HedefitColors.TextMuted, p(30f, 4f), p(30f, 96f), 1.2f * u)
    }
    // Zemin gölgesi
    drawOval(ink.copy(alpha = .07f), Offset(cx - 24 * u, 94 * u), Size(48 * u, 4 * u))

    val headY = 14f + drop
    val shoulderY = 26f + drop
    val hipY = 58f + drop
    val shoulderHalf = 10f + 4f * (upper - 1f)
    val waistHalf = 6.5f + 1f * (core - 1f)

    // Bacaklar
    val legW = (3.2f + 2.6f * (legs - 1f)) * u
    val kneeY = 76f + drop * .45f
    val raise = if (hanging) t else 0f
    for (side in listOf(-1f, 1f)) {
        val hip = p(side * 4.5f, hipY)
        val knee = if (raise > 0f) p(side * 5f, hipY + 14f - 10f * raise) else p(side * (5f + 7f * squat), kneeY)
        val foot = if (raise > 0f) p(side * 5.5f, hipY + 30f - 30f * raise) else p(side * 8f, 93f)
        drawLine(ink, hip, knee, legW, StrokeCap.Round)
        drawLine(ink, knee, foot, legW * .85f, StrokeCap.Round)
        // Ön bacak kası
        if (legs > 1.08f) {
            val mid = Offset((hip.x + knee.x) / 2f, (hip.y + knee.y) / 2f)
            drawOval(muscle.copy(alpha = ((legs - 1f) / 1.2f).coerceIn(0f, .9f)), Offset(mid.x - legW * .55f, mid.y - legW), Size(legW * 1.1f, legW * 2f))
        }
    }

    // Gövde (V şekli: omuz genişledikçe belirginleşir)
    val torso = Path().apply {
        moveTo(cx - shoulderHalf * u, shoulderY * u)
        lineTo(cx + shoulderHalf * u, shoulderY * u)
        lineTo(cx + waistHalf * u, hipY * u)
        lineTo(cx - waistHalf * u, hipY * u)
        close()
    }
    drawPath(torso, ink)
    // Göğüs kasları
    if (upper > 1.05f) {
        val a = ((upper - 1f) / 1.2f).coerceIn(.15f, .9f)
        val pecW = (shoulderHalf - 1.5f) * u
        drawRoundRect(muscle.copy(alpha = a), Offset(cx - pecW - .5f * u, (shoulderY + 3f) * u), Size(pecW, 8f * u), CornerRadius(4f * u))
        drawRoundRect(muscle.copy(alpha = a), Offset(cx + .5f * u, (shoulderY + 3f) * u), Size(pecW, 8f * u), CornerRadius(4f * u))
    }
    // Karın: altı parça, gelişimle belirir; bacak kaldırırken kasılır.
    val abAlpha = (((core - 1f) / 1.2f) + if (hanging) t * .3f else 0f).coerceIn(.08f, .95f)
    for (row in 0 until 3) for (col in listOf(-1f, 1f)) {
        val x = cx + (if (col < 0) -3.6f else .4f) * u
        val y = (shoulderY + 14f + row * 5.5f) * u
        drawRoundRect(muscle.copy(alpha = abAlpha), Offset(x, y), Size(3.2f * u, 4.4f * u), CornerRadius(1.2f * u))
    }

    // Kafa
    drawCircle(ink, 7.5f * u, p(0f, headY))
    val eyeH = if (t > .5f) .5f * u else 1.3f * u
    drawOval(HedefitColors.Background, Offset(cx - 3.2f * u, (headY - 1f) * u), Size(1.4f * u, eyeH))
    drawOval(HedefitColors.Background, Offset(cx + 1.8f * u, (headY - 1f) * u), Size(1.4f * u, eyeH))

    // Kollar
    val armW = (3f + 2.2f * (arms - 1f)) * u
    val weight = HedefitColors.Warning
    val handsPos = mutableListOf<Offset>()
    for (side in listOf(-1f, 1f)) {
        val shoulder = p(side * shoulderHalf, shoulderY + 1f)
        val (elbow, hand) = when (gear) {
            Gear.Dumbbell -> p(side * (shoulderHalf + 2f), shoulderY + 17f) to p(side * (shoulderHalf + 3f - 1f * t), shoulderY + 31f - 26f * t)
            Gear.Barbell -> p(side * (shoulderHalf + 7f), shoulderY + 3f - 12f * t) to p(side * (shoulderHalf + 5f), shoulderY - 4f - 22f * t)
            Gear.Squat -> p(side * (shoulderHalf + 1f), shoulderY + 8f - 4f * t) to p(side * 4f, shoulderY + 6f - 6f * t)
            Gear.PullBar -> p(side * (shoulderHalf + 4f), shoulderY - 10f) to p(side * (shoulderHalf + 4f), 4f)
        }
        drawLine(ink, shoulder, elbow, armW, StrokeCap.Round)
        drawLine(ink, elbow, hand, armW * .85f, StrokeCap.Round)
        // Omuz kası
        if (upper > 1.05f) drawCircle(muscle.copy(alpha = ((upper - 1f) / 1.2f).coerceIn(.15f, .9f)), (2.4f + 1.8f * (upper - 1f)) * u, shoulder)
        // Pazı: üst kolda, kaldırırken kabarır
        val flex = if (gear == Gear.Dumbbell) t else 0f
        val b = (2.6f + 2f * flex) * arms * u
        val mid = Offset((shoulder.x + elbow.x) / 2f, (shoulder.y + elbow.y) / 2f)
        drawOval(muscle, Offset(mid.x - b * .6f, mid.y - b / 2f), Size(b * 1.2f, b))
        handsPos += hand
    }

    // Aletler
    when (gear) {
        Gear.Dumbbell -> handsPos.forEach { h ->
            drawLine(HedefitColors.TextSecondary, Offset(h.x - 4f * u, h.y), Offset(h.x + 4f * u, h.y), 1.4f * u, StrokeCap.Round)
            drawRoundRect(weight, Offset(h.x - 5.5f * u, h.y - 2.6f * u), Size(2.4f * u, 5.2f * u), CornerRadius(u))
            drawRoundRect(weight, Offset(h.x + 3.1f * u, h.y - 2.6f * u), Size(2.4f * u, 5.2f * u), CornerRadius(u))
        }
        Gear.Barbell -> {
            val y = (handsPos[0].y + handsPos[1].y) / 2f
            drawLine(HedefitColors.TextSecondary, Offset(cx - 34f * u, y), Offset(cx + 34f * u, y), 1.6f * u, StrokeCap.Round)
            for (side in listOf(-1f, 1f)) {
                drawRoundRect(weight, Offset(cx + side * 29f * u - 2f * u, y - 7f * u), Size(4f * u, 14f * u), CornerRadius(u))
                drawRoundRect(weight.copy(alpha = .8f), Offset(cx + side * 33f * u - 1.5f * u, y - 5f * u), Size(3f * u, 10f * u), CornerRadius(u))
            }
        }
        Gear.Squat -> Unit
        Gear.PullBar -> Unit
    }
}
