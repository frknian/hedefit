package com.hedefit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val section: Dp = 32.dp
}

object HedefitColors {
    var Background = Color(0xFF090A0C)
    var Surface = Color(0xFF12151B)
    var SurfaceHigh = Color(0xFF1A1F27)
    var SurfaceSoft = Color(0xFF222934)
    var Lime = Color(0xFF22C55E)
    var LimeDark = Color(0xFF16A34A)
    var OnLime = Color(0xFF051B0B)
    var TextPrimary = Color(0xFFF8FAFC)
    var TextSecondary = Color(0xFF94A3B8)
    // WCAG AA (küçük metin için 4.5:1) hedefiyle ölçülüp ayarlandı: eski
    // #64748B, koyu temada Surface/SurfaceHigh zemininde ~3.5:1'e düşüyordu.
    var TextMuted = Color(0xFF79899F)
    var Divider = Color(0x18FFFFFF)
    val Coral = Color(0xFFFF6B6B)
    val Water = Color(0xFF38BDF8)
    val Sleep = Color(0xFFA78BFA)
    val Warning = Color(0xFFFBBF24)

    fun applyTheme(dark: Boolean, accentHue: Float) {
        val hue = ((accentHue % 360f) + 360f) % 360f
        Lime = Color.hsl(hue, .68f, if (dark) .58f else .46f)
        LimeDark = Color.hsl(hue, .72f, if (dark) .42f else .36f)
        OnLime = if (Lime.luminance() > .45f) Color(0xFF051B0B) else Color.White
        Background = if (dark) Color(0xFF090A0C) else Color(0xFFF8FAFC)
        Surface = if (dark) Color(0xFF12151B) else Color.White
        SurfaceHigh = if (dark) Color(0xFF1A1F27) else Color(0xFFF1F5F9)
        SurfaceSoft = if (dark) Color(0xFF222934) else Color(0xFFE2E8F0)
        TextPrimary = if (dark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
        TextSecondary = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
        // Her iki ton da WCAG AA (4.5:1) için ölçülüp ayarlandı.
        TextMuted = if (dark) Color(0xFF79899F) else Color(0xFF607490)
        Divider = if (dark) Color(0x18FFFFFF) else Color(0x14000000)
    }
}

private fun hedefitDarkScheme() = darkColorScheme(
    primary = HedefitColors.Lime,
    onPrimary = HedefitColors.OnLime,
    primaryContainer = HedefitColors.LimeDark.copy(alpha = .34f),
    onPrimaryContainer = HedefitColors.Lime,
    secondary = HedefitColors.LimeDark,
    background = HedefitColors.Background,
    onBackground = HedefitColors.TextPrimary,
    surface = HedefitColors.Surface,
    onSurface = HedefitColors.TextPrimary,
    surfaceVariant = HedefitColors.SurfaceHigh,
    onSurfaceVariant = HedefitColors.TextSecondary,
    error = HedefitColors.Coral,
    outline = HedefitColors.Divider,
)

private fun hedefitLightScheme() = lightColorScheme(
    primary = HedefitColors.Lime,
    onPrimary = HedefitColors.OnLime,
    primaryContainer = HedefitColors.Lime.copy(alpha = .22f),
    onPrimaryContainer = HedefitColors.LimeDark,
    secondary = HedefitColors.LimeDark,
    background = HedefitColors.Background,
    onBackground = HedefitColors.TextPrimary,
    surface = HedefitColors.Surface,
    onSurface = HedefitColors.TextPrimary,
    surfaceVariant = HedefitColors.SurfaceHigh,
    onSurfaceVariant = HedefitColors.TextSecondary,
    error = Color(0xFFB3261E),
    outline = HedefitColors.Divider,
)

private val baseTypography = androidx.compose.material3.Typography()
// Android'in yerel ve her ağırlıkta güvenilir UI yazı ailesi: Roboto.
private val HedefitFontFamily = FontFamily.SansSerif
private val HedefitTypography = baseTypography.copy(
    displayLarge = baseTypography.displayLarge.copy(fontFamily = HedefitFontFamily),
    displayMedium = baseTypography.displayMedium.copy(fontFamily = HedefitFontFamily),
    displaySmall = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = baseTypography.titleSmall.copy(fontFamily = HedefitFontFamily),
    bodyLarge = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = HedefitFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = baseTypography.labelSmall.copy(fontFamily = HedefitFontFamily),
)

@Composable
fun HedefitTheme(darkTheme: Boolean = isSystemInDarkTheme(), accentHue: Float = 106f, content: @Composable () -> Unit) {
    HedefitColors.applyTheme(darkTheme, accentHue)
    MaterialTheme(
        colorScheme = if (darkTheme) hedefitDarkScheme() else hedefitLightScheme(),
        typography = HedefitTypography,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
    }
}
