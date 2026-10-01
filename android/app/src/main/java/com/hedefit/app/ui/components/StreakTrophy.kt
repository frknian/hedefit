package com.hedefit.app.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.ui.theme.HedefitColors
import java.time.LocalDate

/**
 * Günlük giriş serisi: uygulama her gün açıldığında +1, bir gün atlanırsa 1'den başlar.
 * Cihazda tutulur; aynı gün içindeki tekrar açılışlar sayılmaz.
 */
object DailyStreak {
    private const val FILE = "hedefit-daily-streak"
    fun touch(context: Context, today: LocalDate = LocalDate.now()): Int {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val last = p.getString("last", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val count = p.getInt("count", 0)
        val next = when {
            last == today -> count.coerceAtLeast(1)
            last == today.minusDays(1) -> count + 1
            else -> 1
        }
        if (last != today) p.edit().putString("last", today.toString()).putInt("count", next).putInt("best", maxOf(next, p.getInt("best", 0))).apply()
        return next
    }
}

/** Kupa ve alev birleşik ikon + seri sayısı; dokununca ödüller açılır. */
@Composable
fun StreakTrophyButton(streak: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(HedefitColors.SurfaceHigh).clickable(onClick = onClick).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreakTrophyIcon(Modifier.size(30.dp))
        Spacer(Modifier.width(4.dp))
        Text("$streak", fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary, fontSize = 16.sp)
    }
}

/** Kupa gövdesi uygulama renginde, ağzından çıkan alev sıcak tonda. */
@Composable
fun StreakTrophyIcon(modifier: Modifier) {
    val cup = HedefitColors.Lime
    val flame = HedefitColors.Warning
    val core = HedefitColors.Coral
    Canvas(modifier) {
        val u = size.minDimension / 24f
        // Alev (kupanın ağzından yükselir)
        val outer = Path().apply {
            moveTo(12f * u, 1f * u)
            cubicTo(16f * u, 5f * u, 17f * u, 8f * u, 15.5f * u, 11f * u)
            lineTo(8.5f * u, 11f * u)
            cubicTo(7f * u, 8f * u, 9f * u, 6f * u, 10f * u, 4f * u)
            cubicTo(10.5f * u, 6f * u, 11.5f * u, 6.5f * u, 12f * u, 1f * u)
            close()
        }
        drawPath(outer, flame)
        val inner = Path().apply {
            moveTo(12f * u, 5f * u)
            cubicTo(14f * u, 7.5f * u, 14.2f * u, 9.5f * u, 13.4f * u, 11f * u)
            lineTo(10.6f * u, 11f * u)
            cubicTo(10f * u, 9.5f * u, 10.8f * u, 7.8f * u, 12f * u, 5f * u)
            close()
        }
        drawPath(inner, core)
        // Kupa gövdesi
        val body = Path().apply {
            moveTo(6f * u, 10.5f * u); lineTo(18f * u, 10.5f * u)
            cubicTo(18f * u, 15.5f * u, 15.5f * u, 17.5f * u, 12f * u, 17.5f * u)
            cubicTo(8.5f * u, 17.5f * u, 6f * u, 15.5f * u, 6f * u, 10.5f * u)
            close()
        }
        drawPath(body, cup)
        // Kulplar
        drawArc(cup, 90f, 180f, false, Offset(3f * u, 11f * u), Size(4.5f * u, 4.5f * u), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * u))
        drawArc(cup, -90f, 180f, false, Offset(16.5f * u, 11f * u), Size(4.5f * u, 4.5f * u), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * u))
        // Ayak ve kaide
        drawRect(cup, Offset(11f * u, 17.2f * u), Size(2f * u, 3f * u))
        drawRoundRect(cup, Offset(7.5f * u, 20f * u), Size(9f * u, 2.6f * u), CornerRadius(1f * u))
    }
}
