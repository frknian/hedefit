package com.hedefit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hedefit.app.ui.theme.HedefitColors

/**
 * Kardiyo makinelerinin elle çizilmiş ikonları (emoji yerine). 24×24 birimlik ızgarada
 * çizilir; [color] figür ve makine, vurgu rengi hareketli parçalar içindir.
 */
@Composable
fun CardioMachineIcon(machineKey: String, modifier: Modifier, color: Color = HedefitColors.TextPrimary, accent: Color = HedefitColors.Coral) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(a: Offset, b: Offset, w: Float = 1.6f, c: Color = color) = drawLine(c, a, b, w * u, StrokeCap.Round)
        fun head(x: Float, y: Float) = drawCircle(color, 1.9f * u, p(x, y))
        when (machineKey) {
            "treadmill" -> {
                // Bant, konsol ve koşan figür
                drawRoundRect(accent, p(2f, 19.5f), Size(19f * u, 2.2f * u), CornerRadius(u))
                line(p(19.5f, 19.5f), p(21f, 8f), 1.4f); line(p(19.5f, 8f), p(22.5f, 8f), 1.6f)
                head(11f, 4.5f)
                line(p(10.6f, 6.6f), p(9.5f, 12.5f), 1.8f)
                line(p(10.4f, 8f), p(13.5f, 10f)); line(p(10.4f, 8f), p(7.5f, 9.5f))
                line(p(9.5f, 12.5f), p(13f, 15f)); line(p(13f, 15f), p(12.5f, 19f))
                line(p(9.5f, 12.5f), p(7.5f, 16f)); line(p(7.5f, 16f), p(5f, 17f))
            }
            "bike" -> {
                // Sabit bisiklet: gövde, sele, gidon, volan ve pedal çeviren figür
                drawCircle(accent, 3.4f * u, p(17.5f, 17.5f), style = Stroke(1.5f * u))
                line(p(5f, 21.5f), p(20f, 21.5f), 1.6f)
                line(p(8f, 21.5f), p(9.5f, 12f), 1.6f); line(p(9.5f, 12f), p(17.5f, 17.5f), 1.4f)
                line(p(15f, 9f), p(17f, 17.5f), 1.4f); line(p(14f, 8.5f), p(17f, 8.5f), 1.6f)
                line(p(8f, 11.5f), p(11f, 11.5f), 1.8f)
                head(12.5f, 3.8f)
                line(p(11.8f, 5.8f), p(10f, 11f), 1.8f); line(p(11.5f, 7f), p(15f, 8.5f))
                line(p(10f, 11f), p(13.5f, 13.5f)); line(p(13.5f, 13.5f), p(12.5f, 17f))
            }
            "elliptical" -> {
                // Eliptik: iki kol, oval pedal yolu ve figür
                drawOval(accent, p(4f, 17f), Size(14f * u, 5f * u), style = Stroke(1.4f * u))
                line(p(19f, 21.5f), p(19f, 6f), 1.5f)
                line(p(16f, 5f), p(19f, 7f), 1.4f)
                head(11f, 4f)
                line(p(10.8f, 6f), p(10.5f, 12.5f), 1.8f)
                line(p(10.7f, 7.5f), p(15.5f, 6f)); line(p(10.7f, 8f), p(14f, 10.5f))
                line(p(10.5f, 12.5f), p(8f, 16.5f)); line(p(8f, 16.5f), p(7f, 19.5f))
                line(p(10.5f, 12.5f), p(13f, 16f)); line(p(13f, 16f), p(14f, 19f))
            }
            "rower" -> {
                // Kürek makinesi: ray, volan ve çeken figür
                line(p(2f, 20f), p(22f, 20f), 1.6f)
                drawCircle(accent, 2.8f * u, p(19.5f, 16.5f), style = Stroke(1.5f * u))
                drawRoundRect(color, p(6f, 16.5f), Size(4f * u, 2f * u), CornerRadius(u))
                head(6f, 7.5f)
                line(p(6.5f, 9.5f), p(8f, 16.5f), 1.8f)
                line(p(7f, 11f), p(11.5f, 13f)); line(p(11.5f, 13f), p(18f, 15.5f), 1f, accent)
                line(p(8f, 16.5f), p(12.5f, 13.5f)); line(p(12.5f, 13.5f), p(16f, 18f))
            }
            "stepper" -> {
                // Merdiven basamakları ve çıkan figür
                val steps = listOf(Offset(2f, 21f), Offset(8f, 17f), Offset(14f, 13f))
                steps.forEach { s -> drawRect(accent, p(s.x, s.y), Size(6f * u, 1.6f * u)); line(p(s.x + 6f, s.y), p(s.x + 6f, s.y + 4f), 1.2f, accent) }
                head(13f, 3.5f)
                line(p(12.8f, 5.5f), p(12f, 10.5f), 1.8f)
                line(p(12.6f, 7f), p(15.5f, 9f)); line(p(12.6f, 7f), p(9.5f, 8.5f))
                line(p(12f, 10.5f), p(15f, 11f)); line(p(15f, 11f), p(15.5f, 13f))
                line(p(12f, 10.5f), p(10.5f, 14f)); line(p(10.5f, 14f), p(10.5f, 17f))
            }
            else -> {
                // Açık hava: güneş, tepe ve yürüyen figür
                drawCircle(HedefitColors.Warning, 2.4f * u, p(19f, 4.5f))
                drawArc(accent, 180f, 180f, false, p(9f, 15f), Size(15f * u, 12f * u), style = Stroke(1.5f * u))
                line(p(1f, 21.5f), p(23f, 21.5f), 1.4f)
                head(7f, 5f)
                line(p(7f, 7f), p(7f, 13f), 1.8f)
                line(p(7f, 8.5f), p(10f, 11f)); line(p(7f, 8.5f), p(4.5f, 11f))
                line(p(7f, 13f), p(9.5f, 17f)); line(p(9.5f, 17f), p(10f, 21f))
                line(p(7f, 13f), p(5f, 17f)); line(p(5f, 17f), p(3.5f, 21f))
            }
        }
    }
}
