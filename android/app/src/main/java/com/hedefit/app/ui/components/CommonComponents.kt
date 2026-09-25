package com.hedefit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.hedefit.app.R
import com.hedefit.app.ui.model.AppDestination
import com.hedefit.app.ui.layout.LayoutPolicy
import com.hedefit.app.ui.theme.HedefitColors

val ScreenHorizontalPadding = 16.dp
val CardRadius = 22.dp

@Composable
fun FitCoachRobotAvatar(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.fit_coach_robot),
        contentDescription = com.hedefit.app.ui.i18n.tr("Fit Koç spor robotu", "Fit Coach robot"),
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun HedefitAppFrame(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
    language: String = "tr",
    coachName: String = if (language == "en") "Fit Coach" else "Fit Koç",
    content: @Composable (PaddingValues, Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = LayoutPolicy.usesExpandedNavigation(maxWidth.value.toInt())
        if (expanded) {
            Row(Modifier.fillMaxSize().background(HedefitColors.Background)) {
                HedefitNavigationRail(selected, onSelect, language, coachName)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    content(PaddingValues(horizontal = 24.dp, vertical = 12.dp), true)
                }
            }
        } else {
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Scaffold(
                containerColor = HedefitColors.Background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = { if (!imeVisible) HedefitBottomBar(selected, onSelect, language, coachName) },
            ) { padding -> content(padding, false) }
        }
    }
}

@Composable
private fun HedefitBottomBar(selected: AppDestination, onSelect: (AppDestination) -> Unit, language: String, coachName: String) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(HedefitColors.SurfaceHigh.copy(alpha = .78f))
                .border(1.dp, HedefitColors.TextPrimary.copy(alpha = .06f), RoundedCornerShape(30.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppDestination.primaryTabs.forEach { destination ->
                val label = if (destination == AppDestination.Coach) coachName else destination.localizedLabel(language)
                val active = destination == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = active, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { if (!active) onSelect(destination) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(44.dp).background(if (active) HedefitColors.Lime.copy(alpha = .16f) else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (destination == AppDestination.Coach) FitCoachRobotAvatar(Modifier.size(if (active) 34.dp else 30.dp).alpha(if (active) 1f else .7f))
                        else Icon(destination.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (active) HedefitColors.Lime else HedefitColors.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun HedefitNavigationRail(selected: AppDestination, onSelect: (AppDestination) -> Unit, language: String, coachName: String) {
    NavigationRail(
        containerColor = HedefitColors.Surface,
        modifier = Modifier.fillMaxHeight().width(92.dp).padding(top = 20.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(HedefitColors.Lime, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("H", color = HedefitColors.OnLime, fontWeight = FontWeight.Black, fontSize = 24.sp)
        }
        Spacer(Modifier.height(28.dp))
        AppDestination.primaryTabs.forEach { destination ->
            val label = if (destination == AppDestination.Coach) coachName else destination.localizedLabel(language)
            NavigationRailItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { if (destination == AppDestination.Coach) FitCoachRobotAvatar(Modifier.size(38.dp)) else Icon(destination.icon, label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = HedefitColors.OnLime,
                    selectedTextColor = HedefitColors.Lime,
                    indicatorColor = if (destination == AppDestination.Coach) Color.Transparent else HedefitColors.Lime,
                    unselectedIconColor = HedefitColors.TextSecondary,
                    unselectedTextColor = HedefitColors.TextSecondary,
                ),
            )
        }
    }
}

@Composable
fun ScreenContainer(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(HedefitColors.Background).padding(padding),
        contentAlignment = Alignment.TopCenter,
    ) {
        val horizontalPadding = LayoutPolicy.horizontalPadding(maxWidth.value.toInt()).dp
        Box(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).widthIn(max = 1120.dp), content = content)
    }
}

/**
 * `ScreenContainer`'ın, `HedefitAppFrame`'in Scaffold'ı DIŞINDA tam ekran
 * açılan rotalar (ör. Auth, Onboarding, Profil Ayarları, Rota) için sürümü.
 *
 * Bu rotalar bir Scaffold'dan `PaddingValues` almaz; kendi kenar boşluklarını
 * kendileri hesaplar. Öncesinde her ekran `statusBarsPadding()` /
 * `navigationBarsPadding()` / `systemBarsPadding()` arasından farklı bir
 * kombinasyon seçiyordu — bu tek, tutarlı `safeDrawingPadding()` seçimiyle
 * hem arka plan hem kenar boşluğu tüm ekranlarda aynı olur.
 *
 * Yatay padding/genişlik sınırlaması BİLEREK yok: bu rotaların çoğu kendi
 * `LazyColumn` `contentPadding`'ini veya kenardan kenara başlık satırını
 * yönetiyor; burada ikinci bir yatay padding katmanı eklemek çift boşluğa
 * yol açardı. Yalnız arka plan + kenar boşluğu tutarlılığı hedeflenir.
 */
@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().background(HedefitColors.Background).safeDrawingPadding(),
        content = content,
    )
}

@Composable
fun HedefitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = HedefitColors.Surface, contentColor = HedefitColors.TextPrimary),
        border = if (HedefitColors.isLight) BorderStroke(.6.dp, HedefitColors.Divider) else null,
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun SectionTitle(title: String, trailing: String? = null, onTrailingClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                color = HedefitColors.Lime,
                style = MaterialTheme.typography.labelLarge,
                modifier = if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick).padding(4.dp) else Modifier,
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.SurfaceHigh, contentColor = HedefitColors.TextPrimary),
        border = BorderStroke(0.6.dp, HedefitColors.Divider),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = HedefitColors.TextSecondary)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MetricCapsule(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    accent: Color = HedefitColors.Lime,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .background(HedefitColors.SurfaceHigh.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .then(clickModifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HedefitColors.TextSecondary, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HedefitColors.TextPrimary, maxLines = 1)
        if (sublabel != null) {
            Text(sublabel, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
        }
    }
}

@Composable
fun UnifiedEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(HedefitColors.SurfaceHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = HedefitColors.TextSecondary, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HedefitColors.TextPrimary)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = HedefitColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            PrimaryButton(text = actionText, onClick = onAction, modifier = Modifier.fillMaxWidth(0.6f))
        }
    }
}

@Composable
fun OutlineAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Box(
        modifier.fillMaxWidth().height(52.dp)
            .background(Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(HedefitColors.Lime, style = Stroke(1.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
        }
        if (icon != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = HedefitColors.Lime)
            Text(text, color = HedefitColors.Lime, style = MaterialTheme.typography.titleMedium)
        } else Text(text, color = HedefitColors.Lime, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    color: Color = HedefitColors.Lime,
    center: @Composable BoxScope.() -> Unit,
) {
    // Açılışta 0'dan dolarak gelir, sonraki değişimlerde de yumuşakça ilerler.
    val started = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { started.value = true }
    val reduced = rememberReducedMotion()
    val animated = androidx.compose.animation.core.animateFloatAsState(
        if (started.value) progress.coerceIn(0f, 1f) else 0f,
        androidx.compose.animation.core.tween(if (reduced) 0 else 900),
        label = "progressRing",
    ).value
    Box(modifier, contentAlignment = Alignment.Center) {
        val track = HedefitColors.SurfaceSoft
        Canvas(Modifier.fillMaxSize()) {
            drawArc(track, -90f, 360f, false, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * animated, false, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
fun MetricCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = HedefitColors.Lime,
    onClick: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    HedefitCard(modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (footer != null) footer()
        }
    }
}

@Composable
fun MacroBar(label: String, value: String, progress: Float, icon: ImageVector? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, tint = HedefitColors.Lime, modifier = Modifier.size(18.dp))
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(value, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).background(HedefitColors.Divider, CircleShape)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(HedefitColors.Lime, CircleShape))
        }
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = HedefitColors.Lime,
    showGrid: Boolean = false,
) {
    // Çizgi soldan sağa çizilerek gelir; veri değişince yeniden çizilir.
    val reduced = rememberReducedMotion()
    val draw = androidx.compose.runtime.remember(values) { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(values) { draw.animateTo(1f, androidx.compose.animation.core.tween(1000)) }
    Canvas(modifier) {
        if (showGrid) {
            repeat(4) { index ->
                val y = size.height * index / 3f
                drawLine(HedefitColors.Divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
        }
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            val y = size.height - ((value - min) / range) * (size.height * .8f) - size.height * .1f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * draw.value, partial, true)
        drawPath(partial, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        if (draw.value < 1f) return@Canvas
        val last = values.last()
        val y = size.height - ((last - min) / range) * (size.height * .8f) - size.height * .1f
        drawCircle(color, 5.dp.toPx(), Offset(size.width, y))
        drawCircle(HedefitColors.Background, 2.dp.toPx(), Offset(size.width, y))
    }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, accent: Color = HedefitColors.TextPrimary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = accent, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun CardDivider() {
    HorizontalDivider(color = HedefitColors.Divider, thickness = .6.dp)
}

/**
 * iOS bildirim damlası tarzı, ekranın üstünden aşağı kayarak açılan uygulama
 * içi bildirim. `Toast.makeText(...)`'in yerini alır: sistem toast'ı her zaman
 * ekranın ALTINDA çıkar ve uygulama temasından bağımsızdır; bu, tıklanabilir,
 * temayla tutarlı ve üstten açılan bir alternatiftir.
 */
@Composable
fun TopNotificationBanner(message: String?, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3_600)
            onDismiss()
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        enter = androidx.compose.animation.slideInVertically(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
            ),
            initialOffsetY = { -it * 2 },
        ) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it * 2 }) + androidx.compose.animation.fadeOut(),
        modifier = modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xF01C222B),
            shadowElevation = 14.dp,
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = .08f)),
            modifier = Modifier.fillMaxWidth().clickable { onDismiss() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(Modifier.size(30.dp).background(HedefitColors.Lime, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Text("H", color = HedefitColors.OnLime, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                Text(
                    message ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun ArrowLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = HedefitColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun HedefitTabPager(selected: AppDestination, onSelect: (AppDestination) -> Unit, content: @Composable (AppDestination) -> Unit) {
    val tabs = AppDestination.primaryTabs
    if (selected !in tabs) {
        content(selected)
        return
    }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = tabs.indexOf(selected)) { tabs.size }
    val currentOnSelect by androidx.compose.runtime.rememberUpdatedState(onSelect)
    androidx.compose.runtime.LaunchedEffect(selected) {
        val target = tabs.indexOf(selected)
        if (pagerState.currentPage != target) {
            if (kotlin.math.abs(pagerState.currentPage - target) == 1) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
        }
    }
    androidx.compose.runtime.LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { currentOnSelect(tabs[it]) }
    }
    androidx.compose.foundation.pager.HorizontalPager(pagerState, Modifier.fillMaxSize(), key = { tabs[it].name }) { page -> content(tabs[page]) }
}
