package com.hedefit.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.ui.theme.HedefitColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sistemde "animasyonları kaldır" açıksa (ANIMATOR_DURATION_SCALE = 0) kutlamaları
 * sade geçişlere indiririz; hareket hassasiyeti olan kullanıcıyı konfetiyle yormayız.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

private data class ConfettiPiece(val angle: Float, val speed: Float, val spin: Float, val color: Color, val size: Float, val drift: Float)

/** Merkezden patlayıp yerçekimiyle düşen konfeti. [burstKey] her değiştiğinde yeniden patlar. */
@Composable
fun ConfettiBurst(burstKey: Any, modifier: Modifier = Modifier, pieceCount: Int = 90, colors: List<Color>? = null) {
    if (rememberReducedMotion()) return
    val palette = colors ?: listOf(HedefitColors.Lime, HedefitColors.Warning, HedefitColors.Water, HedefitColors.Coral, HedefitColors.Sleep)
    val pieces = remember(burstKey) {
        List(pieceCount) {
            ConfettiPiece(
                angle = Random.nextFloat() * 360f,
                speed = 0.35f + Random.nextFloat() * 0.65f,
                spin = Random.nextFloat() * 720f - 360f,
                color = palette[it % palette.size],
                size = 6f + Random.nextFloat() * 8f,
                drift = Random.nextFloat() * 2f - 1f,
            )
        }
    }
    val progress = remember(burstKey) { Animatable(0f) }
    LaunchedEffect(burstKey) { progress.animateTo(1f, tween(2200, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        val origin = Offset(size.width / 2f, size.height * 0.38f)
        val reach = size.minDimension * 0.75f
        pieces.forEach { p ->
            val rad = Math.toRadians(p.angle.toDouble())
            // Hızla dışarı fırla, sonra yerçekimiyle aşağı düş.
            val burst = (1f - (1f - t) * (1f - t)) * p.speed * reach
            val x = origin.x + (cos(rad) * burst).toFloat() + p.drift * t * 60f
            val y = origin.y + (sin(rad) * burst).toFloat() + t * t * size.height * 0.55f
            rotate(p.spin * t, Offset(x, y)) {
                drawRect(p.color.copy(alpha = (1f - t).coerceIn(0f, 1f)), Offset(x, y), Size(p.size, p.size * 0.55f))
            }
        }
    }
}

/** 0'dan hedefe doğru sayarak artan sayı. */
@Composable
fun CountUpText(target: Int, modifier: Modifier = Modifier, prefix: String = "", suffix: String = "", color: Color = HedefitColors.Lime, fontSizeSp: Int = 44, durationMs: Int = 900) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(target) { started = true }
    val reduced = rememberReducedMotion()
    val value by animateIntAsState(if (started) target else 0, tween(if (reduced) 0 else durationMs, easing = FastOutSlowInEasing), label = "countUp")
    Text("$prefix$value$suffix", modifier, color = color, fontSize = fontSizeSp.sp, fontWeight = FontWeight.Black)
}

/** Apple Watch tarzı halka; açılışta dolarak gelir, hedefe ulaşınca hafifçe parlar. */
@Composable
fun ActivityRing(progress: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 64.dp, stroke: Dp = 8.dp, content: @Composable BoxScope.() -> Unit = {}) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val reduced = rememberReducedMotion()
    val animated by animateFloatAsState(if (started) progress.coerceIn(0f, 1f) else 0f, tween(if (reduced) 0 else 1100, easing = FastOutSlowInEasing), label = "ring")
    val closed = progress >= 1f
    val glow by animateFloatAsState(if (closed && started) 1.06f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "ringGlow")
    Box(modifier.size(size).scale(glow), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2f
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(color.copy(alpha = .18f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (closed) drawCircle(color.copy(alpha = .10f), radius = this.size.minDimension / 2f)
        }
        content()
    }
}

/** Seri büyüdükçe büyüyen, seri tehlikedeyse sönükleşen alev. */
@Composable
fun StreakFlame(streakDays: Int, atRisk: Boolean, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val base = when {
        streakDays >= 30 -> 1.35f
        streakDays >= 7 -> 1.18f
        streakDays >= 3 -> 1.06f
        else -> 0.94f
    }
    val flicker = if (reduced || streakDays == 0) 1f else {
        val transition = rememberInfiniteTransition(label = "flame")
        transition.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flicker").value
    }
    Text(
        "🔥",
        modifier.graphicsLayer { scaleX = base * flicker; scaleY = base * flicker }.alpha(if (atRisk || streakDays == 0) .45f else 1f),
        fontSize = 26.sp,
    )
}

enum class CelebrationKind { Workout, LevelUp, Achievement, GoalClosed }

data class CelebrationEvent(
    val kind: CelebrationKind,
    val title: String,
    val subtitle: String,
    val xpGained: Int = 0,
    val levelProgressFrom: Float = 0f,
    val levelProgressTo: Float = 0f,
    val level: Int? = null,
    val streakDays: Int = 0,
    val emoji: String = "🏆",
    val accent: Color? = null,
    val id: Long = System.nanoTime(),
)

/**
 * Tam ekran kutlama katmanı. Dokununca kapanır; animasyonlar kapalıysa yalnızca
 * içerik belirir. Büyük kutlamayı yalnızca gerçek başarı anlarına ayırıyoruz.
 */
@Composable
fun CelebrationOverlay(event: CelebrationEvent?, onDismiss: () -> Unit, footer: (@Composable ColumnScope.() -> Unit)? = null) {
    if (event == null) return
    val reduced = rememberReducedMotion()
    val haptic = LocalHapticFeedback.current
    val accent = event.accent ?: when (event.kind) {
        CelebrationKind.LevelUp -> HedefitColors.Warning
        CelebrationKind.Achievement -> HedefitColors.Sleep
        else -> HedefitColors.Lime
    }
    val appear = remember(event.id) { Animatable(if (reduced) 1f else 0f) }
    val badgeSpin = remember(event.id) { Animatable(if (reduced || event.kind != CelebrationKind.Achievement) 0f else 180f) }
    var barTarget by remember(event.id) { mutableFloatStateOf(event.levelProgressFrom) }
    val bar by animateFloatAsState(barTarget, tween(if (reduced) 0 else 1000, delayMillis = 350, easing = FastOutSlowInEasing), label = "levelBar")
    LaunchedEffect(event.id) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        appear.animateTo(1f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessLow))
        barTarget = event.levelProgressTo
        if (badgeSpin.value != 0f) badgeSpin.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        delay(1200)
        if (event.kind == CelebrationKind.LevelUp) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(accent.copy(alpha = .30f), HedefitColors.Background.copy(alpha = .96f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        ConfettiBurst(event.id, pieceCount = if (event.kind == CelebrationKind.Achievement) 60 else 110)
        Column(
            Modifier.padding(28.dp).alpha(appear.value.coerceIn(0f, 1f)).scale(0.7f + 0.3f * appear.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(128.dp)
                    .graphicsLayer { rotationY = badgeSpin.value; cameraDistance = 14f * density }
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = .55f), accent.copy(alpha = .08f))), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (event.kind == CelebrationKind.LevelUp && event.level != null) {
                    Text("${event.level}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary)
                } else Text(if (badgeSpin.value > 90f) "❔" else event.emoji, fontSize = 60.sp)
            }
            Text(event.title, style = MaterialTheme.typography.headlineMedium, color = HedefitColors.TextPrimary, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(event.subtitle, color = HedefitColors.TextSecondary, textAlign = TextAlign.Center)
            if (event.xpGained > 0) CountUpText(event.xpGained, prefix = "+", suffix = " XP", color = accent)
            if (event.level != null) Column(Modifier.fillMaxWidth(.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { bar },
                    modifier = Modifier.fillMaxWidth().height(10.dp).background(Color.Transparent, RoundedCornerShape(50)),
                    color = accent,
                    trackColor = HedefitColors.Divider,
                    strokeCap = StrokeCap.Round,
                )
                Text(com.hedefit.app.ui.i18n.tr("Seviye ${event.level}", "Level ${event.level}"), Modifier.padding(top = 6.dp), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            if (event.streakDays > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StreakFlame(event.streakDays, atRisk = false)
                Text(com.hedefit.app.ui.i18n.tr("Serin: ${event.streakDays} gün", "Streak: ${event.streakDays} days"), color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
            footer?.invoke(this)
            Text(com.hedefit.app.ui.i18n.tr("Devam etmek için dokun", "Tap to continue"), color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Seçimde yay (spring) ile hafifçe büyüyüp küçülen kart ölçeği. */
@Composable
fun selectionBounce(selected: Boolean): Float {
    val reduced = rememberReducedMotion()
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (selected && !reduced) { pulse = true; delay(110); pulse = false }
    }
    return animateFloatAsState(if (pulse) 1.04f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "bounce").value
}

/** Sıralı giriş: [index] numaralı öğe 40 ms arayla aşağıdan belirir. */
@Composable
fun Modifier.staggeredEntrance(key: Any, index: Int): Modifier {
    val reduced = rememberReducedMotion()
    val anim = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) {
        delay(40L * index)
        anim.animateTo(1f, spring(dampingRatio = .8f, stiffness = Spring.StiffnessMediumLow))
    }
    return this.graphicsLayer { alpha = anim.value; translationY = (1f - anim.value) * 36.dp.toPx() }
}

/** "Programın hazırlanıyor" sahnesi: dönen halka ve adım adım değişen metin. */
@Composable
fun PlanBuildingScene(modifier: Modifier = Modifier) {
    val steps = listOf(com.hedefit.app.ui.i18n.tr("Hedefin analiz ediliyor…", "Analysing your goal…"), com.hedefit.app.ui.i18n.tr("Ekipmanına uygun hareketler seçiliyor…", "Picking moves for your equipment…"), com.hedefit.app.ui.i18n.tr("Haftalık tempon ayarlanıyor…", "Setting your weekly pace…"), com.hedefit.app.ui.i18n.tr("Kalori hedefin hesaplanıyor…", "Calculating your calorie target…"))
    var stepIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(900); stepIndex = (stepIndex + 1) % steps.size } }
    val transition = rememberInfiniteTransition(label = "build")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweep")
    Column(modifier.fillMaxSize().background(HedefitColors.Background), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(120.dp)) {
            val w = 10.dp.toPx()
            drawArc(HedefitColors.Divider, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
            drawArc(HedefitColors.Lime, sweep, 110f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(28.dp))
        Text(com.hedefit.app.ui.i18n.tr("Programın hazırlanıyor", "Building your program"), style = MaterialTheme.typography.headlineSmall, color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        androidx.compose.animation.AnimatedContent(steps[stepIndex], label = "buildStep") { text ->
            Text(text, color = HedefitColors.TextSecondary)
        }
    }
}

/** Uçan "+XP" rozeti: bir kez yukarı süzülüp kaybolur. */
@Composable
fun FloatingXp(amount: Int, key: Any, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { if (!reduced) anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) else anim.snapTo(1f) }
    if (anim.value >= 1f) return
    Text(
        "+$amount XP",
        modifier.graphicsLayer { translationY = -anim.value * 60.dp.toPx(); alpha = 1f - anim.value },
        color = HedefitColors.Lime,
        fontWeight = FontWeight.Black,
    )
}

/** Su bardağı: dolum seviyesi dalgalanarak yükselir. */
@Composable
fun WaterGlass(fill: Float, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val level by animateFloatAsState(fill.coerceIn(0f, 1f), spring(dampingRatio = .6f, stiffness = Spring.StiffnessLow), label = "water")
    val phase = if (reduced) 0f else rememberInfiniteTransition(label = "wave")
        .animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "phase").value
    Canvas(modifier) {
        val top = size.height * (1f - level)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width) {
                lineTo(x, top + sin(x / size.width * 2 * Math.PI.toFloat() * 1.5f + phase) * 4.dp.toPx())
                x += 4f
            }
            lineTo(size.width, size.height); close()
        }
        drawRoundRect(HedefitColors.Water.copy(alpha = .12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
        drawPath(path, HedefitColors.Water.copy(alpha = .75f))
    }
}
