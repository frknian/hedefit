package com.hedefit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.ui.theme.HedefitColors

/** Square tinted icon container used across cards, rows and tiles. */
@Composable
fun HfIconBadge(icon: ImageVector, tint: Color, size: Dp = 38.dp, iconSize: Dp = 20.dp, radius: Dp = 12.dp) {
    Box(Modifier.size(size).background(tint.copy(alpha = .15f), RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun HfCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color = HedefitColors.TextPrimary, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp).background(HedefitColors.SurfaceHigh, CircleShape)) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun HfScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Geri",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) HfCircleButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun HfSectionHeader(title: String, trailing: String? = null, onTrailingClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                color = if (onTrailingClick != null) HedefitColors.Lime else HedefitColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (onTrailingClick != null) FontWeight.Bold else FontWeight.Medium,
                modifier = if (onTrailingClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTrailingClick).padding(horizontal = 6.dp, vertical = 8.dp) else Modifier,
            )
        }
    }
}

/** Full-width tappable row card: badge, title, subtitle, optional trailing content and a chevron. */
@Composable
fun HfNavRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    chevron: Boolean = onClick != null,
    titleColor: Color = HedefitColors.TextPrimary,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    HedefitCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        HfRowContent(icon, tint, title, subtitle, chevron, titleColor, trailing)
    }
}

/** Row content for grouped list cards (settings style). */
@Composable
fun HfListRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)?,
    chevron: Boolean = onClick != null,
    titleColor: Color = HedefitColors.TextPrimary,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) { HfRowContent(icon, tint, title, subtitle, chevron, titleColor, trailing, badgeSize = 34.dp) }
}

@Composable
private fun HfRowContent(icon: ImageVector, tint: Color, title: String, subtitle: String?, chevron: Boolean, titleColor: Color, trailing: @Composable RowScope.() -> Unit, badgeSize: Dp = 38.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HfIconBadge(icon, tint, badgeSize, if (badgeSize < 38.dp) 18.dp else 20.dp, if (badgeSize < 38.dp) 11.dp else 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = titleColor)
            if (subtitle != null) Text(subtitle, color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = HedefitColors.TextMuted, modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
}

/** Grid tile for quick actions and tools. */
@Composable
fun HfActionTile(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    HedefitCard(modifier, onClick = if (enabled) onClick else null, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HfIconBadge(icon, tint, 36.dp, 18.dp)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun HfStatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, valueColor: Color = HedefitColors.TextPrimary, onClick: (() -> Unit)? = null) {
    HedefitCard(modifier, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun HfPill(text: String, color: Color = HedefitColors.Lime, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier = modifier.background(color.copy(alpha = .15f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun HfTag(text: String) {
    Text(text, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.background(HedefitColors.SurfaceHigh, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
fun HfChip(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, enabled: Boolean = true) {
    Row(
        Modifier.heightIn(min = 40.dp).clip(CircleShape)
            .background(if (selected) HedefitColors.Lime else HedefitColors.Surface)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val color = if (selected) HedefitColors.OnLime else HedefitColors.TextSecondary
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun HfChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun HfSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(HedefitColors.Surface, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index, option ->
            val on = index == selectedIndex
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) HedefitColors.Lime else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) { Text(option, color = if (on) HedefitColors.OnLime else HedefitColors.TextSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1) }
        }
    }
}

@Composable
fun HfPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, secondary: Boolean = false) {
    val bg = if (secondary) HedefitColors.SurfaceHigh else HedefitColors.Lime
    val fg = if (secondary) HedefitColors.TextPrimary else HedefitColors.OnLime
    Row(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) bg else bg.copy(alpha = .4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HfProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = HedefitColors.Lime, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).background(HedefitColors.SurfaceSoft, CircleShape)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).background(color, CircleShape))
    }
}

@Composable
fun HfDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(HedefitColors.Divider))
}

/** Numbered or completed step marker for exercise lists. */
@Composable
fun HfStepRow(index: Int, title: String, meta: String, done: Boolean = false, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).background(if (done) HedefitColors.Lime else HedefitColors.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Default.Check, null, tint = HedefitColors.OnLime, modifier = Modifier.size(16.dp))
            else Text("$index", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (done) HedefitColors.TextMuted else HedefitColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}
