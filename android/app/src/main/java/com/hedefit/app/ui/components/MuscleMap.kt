package com.hedefit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hedefit.app.ui.theme.HedefitColors

/**
 * Tıklanabilir kas haritası. Ön ve arka vücut yan yana çizilir; bir bölgeye dokunmak
 * `onSelect(kasKimliği)` çağırır. Kimlikler egzersiz kataloğunun kas filtresi
 * değerleriyle aynıdır ("chest", "lats", "abdominals"…), bu yüzden doğrudan arama filtresine verilir.
 *
 * Şekiller 100×220'lik birim uzayında SVG yol verisiyle (bezier eğrileri) tanımlıdır; yalnız sağ
 * yarı yazılır, sol yarı yansıtılır. Dokunma alanı çizimle aynı eğriden gelir.
 */
@Composable
fun MuscleMap(selected: Set<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, description: String = "") {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(2f * BODY_W / BODY_H).semantics { if (description.isNotBlank()) contentDescription = description }) {
            BodyCanvas(FRONT, FRONT_DETAILS, selected, onSelect, Modifier.weight(1f).aspectRatio(BODY_W / BODY_H))
            BodyCanvas(BACK, BACK_DETAILS, selected, onSelect, Modifier.weight(1f).aspectRatio(BODY_W / BODY_H))
        }
    }
}

@Composable
private fun BodyCanvas(body: List<Shape>, details: List<Path>, selected: Set<String>, onSelect: (String) -> Unit, modifier: Modifier) {
    val base = HedefitColors.SurfaceHigh
    val idleTop = HedefitColors.TextMuted.copy(alpha = .50f)
    val idleBottom = HedefitColors.TextMuted.copy(alpha = .26f)
    val line = HedefitColors.Background.copy(alpha = .75f)
    val accent = HedefitColors.Lime
    val accentDark = Color(red = accent.red * .72f, green = accent.green * .72f, blue = accent.blue * .72f, alpha = 1f)
    val hitRegions = remember(body) { body.map { shape -> shape to shape.hitRegion() } }
    val ordered = remember(body, selected) { body.sortedBy { it.id != null && it.id in selected } }
    Canvas(
        modifier.pointerInput(body) {
            detectTapGestures { tap ->
                val scale = size.width / BODY_W
                val x = (tap.x / scale * HIT_SCALE).toInt()
                val y = (tap.y / scale * HIT_SCALE).toInt()
                // Üstte çizilen (listede sonra gelen) bölge önce yakalansın.
                val hit = hitRegions.lastOrNull { (shape, region) -> shape.id != null && region.contains(x, y) }?.first
                if (hit?.id != null) onSelect(hit.id)
            }
        },
    ) {
        val scale = size.width / BODY_W
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            ordered.forEach { shape ->
                val active = shape.id != null && shape.id in selected
                val b = shape.path.getBounds()
                val brush = when {
                    shape.id == null -> Brush.verticalGradient(listOf(base, base))
                    active -> Brush.verticalGradient(listOf(accent, accentDark), startY = b.top, endY = b.bottom)
                    else -> Brush.verticalGradient(listOf(idleTop, idleBottom), startY = b.top, endY = b.bottom)
                }
                drawPath(shape.path, brush)
                drawPath(shape.path, if (active) accent else line, style = Stroke(width = .55f, join = StrokeJoin.Round))
            }
            details.forEach { d -> drawPath(d, line, style = Stroke(width = .45f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}

private const val BODY_W = 100f
private const val BODY_H = 220f
private const val HIT_SCALE = 10

/** `id == null` → dokunulmayan gövde parçası (baş, eller, ayaklar, gövde zemini). */
private class Shape(val id: String?, val path: Path) {
    fun hitRegion(): android.graphics.Region {
        val scaled = android.graphics.Path(path.asAndroidPath())
        scaled.transform(android.graphics.Matrix().apply { setScale(HIT_SCALE.toFloat(), HIT_SCALE.toFloat()) })
        return android.graphics.Region().apply { setPath(scaled, android.graphics.Region(0, 0, (BODY_W * HIT_SCALE).toInt(), (BODY_H * HIT_SCALE).toInt())) }
    }
}

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

private fun mirrored(path: Path): Path {
    val m = android.graphics.Matrix().apply { setScale(-1f, 1f, BODY_W / 2f, 0f) }
    val p = android.graphics.Path(path.asAndroidPath())
    p.transform(m)
    return p.asComposePath()
}

/** Sağ yarı verilir; sol yarı yansıtılır ve aynı kimlikle eklenir. */
private fun sym(id: String?, vararg d: String): List<Shape> = d.flatMap { s -> parse(s).let { listOf(Shape(id, it), Shape(id, mirrored(it))) } }

/** Orta hatta oturan tek parça (iki yarıyı zaten kapsar). */
private fun center(id: String?, d: String): List<Shape> = listOf(Shape(id, parse(d)))

private fun detail(vararg d: String): List<Path> = d.flatMap { s -> parse(s).let { listOf(it, mirrored(it)) } }

private const val HEAD = "M50 5.5 C55.5 5.5 59 9.5 59 15.5 C59 21.5 55.5 26.5 50 26.5 C44.5 26.5 41 21.5 41 15.5 C41 9.5 44.5 5.5 50 5.5 Z"
private const val NECK = "M45 25 L55 25 L56.5 36 C54 38 46 38 43.5 36 Z"
private const val TORSO = "M50 35 L58 35.5 C64 37 68 40 69.5 46 C70.5 56 69 70 66 84 C64.5 92 64 98 67 104 L50 108 Z"
private const val HIP = "M50 100 L68 100 C71.5 104 72 111 70 117 L50 119 Z"
private const val ARM_GAP = "M69 44 L82 46 C84 54 83 60 81 64 L72 62 Z"
private const val HAND = "M82 123 C85 123.5 89 123 91.5 122.5 C93.5 128 94 134 92 139 C89 140.5 85.5 139.5 83.5 135 Z"
private const val FOOT = "M55 209 C58.5 210 62.5 210 64.5 209 L68 216.5 C64 219 57.5 219 53.5 217.5 Z"
private const val KNEE = "M54 155.5 C58 157 63 157 66.5 155.5 C66.5 159.5 65.5 162.5 64.5 165 L55 165 C54.5 162 54 159 54 155.5 Z"

private const val DELTOID = "M62 36.5 C69 34.5 78 37 81.5 45 C83.5 51 83 57 80 62 L72 60.5 C70.5 54 67 47 62 42.5 Z"
private const val ARM_UPPER = "M72 62.5 C76 62 80 61.5 82.5 62.5 C86 70 87 80 85.5 89.5 C82 91.5 78 91.5 75 90.5 C72.5 80 71.5 70 72 62.5 Z"
private const val FOREARM = "M75 92.5 C79 92.5 83 91.5 85.5 91.5 C88.5 100 90.5 112 91.5 122 C88.5 123.5 85 124 82 123 C79 113 76 102 75 92.5 Z"

private val FRONT: List<Shape> =
    center(null, HEAD) + center(null, TORSO) + center(null, HIP) +
        sym(null, ARM_GAP, HAND, FOOT, KNEE) +
        center("neck", NECK) +
        sym("chest", "M50.5 40 C56 38 61.5 38 65.5 41 C69.5 45 70.5 52 67.5 57.5 C63.5 61.5 56 62 50.5 60 Z") +
        sym("shoulders", DELTOID) +
        sym("abdominals", "M58 64 C61.5 64 64.5 63 66 62.5 C67 75 65 90 60.5 100 C59.5 92 58.5 80 58 64 Z") +
        center("abdominals", "M43 63 C46.5 62 53.5 62 57 63 L58 80 C58 92 56 98 50 102 C44 98 42 92 42 80 Z") +
        sym("biceps", ARM_UPPER) +
        sym("forearms", FOREARM) +
        sym("quadriceps", "M53 110 C59.5 108 65.5 109 68.5 113 C69 127 67 143 64.5 156 C60.5 158 57 158 55 156 C53.5 142 52 126 53 110 Z") +
        sym("calves", "M55 165.5 C59 164.5 63 164.5 65 165.5 C66.5 178 64.5 196 62.5 208.5 C60.5 209.5 58 209.5 56 208.5 C54.5 196 53.5 178 55 165.5 Z")

private val FRONT_DETAILS: List<Path> =
    listOf(parse("M44.5 70 L55.5 70"), parse("M44.5 77.5 L55.5 77.5"), parse("M45 85 L55 85"), parse("M50 63 L50 100")) +
        detail("M58 64 C59 74 59 86 57 96")

private val BACK: List<Shape> =
    center(null, HEAD) + center(null, TORSO) + center(null, HIP) +
        sym(null, ARM_GAP, HAND, FOOT, KNEE) +
        center("neck", NECK) +
        sym("shoulders", DELTOID) +
        sym("lats", "M52 66 C57 58 62 53 67.5 52 C70.5 60 69.5 72 66.5 82 C63.5 88 58.5 91.5 52 92.5 Z") +
        sym("traps", "M50 31 C56 32 61 34 66 38 C69.5 41 70.5 43.5 68.5 46.5 C62 48.5 56 54.5 50 64.5 Z") +
        sym("middle back", "M50 64.5 C54 60.5 58 57.5 61.5 56.5 C61.5 62 60.5 68 57.5 74 C54.5 78 52 80 50 82 Z") +
        sym("lower back", "M50 84 C54 84 58 86 60.5 88 C60.5 94 59.5 99 57.5 103 C54.5 104 52 104 50 104 Z") +
        sym("triceps", ARM_UPPER) +
        sym("forearms", FOREARM) +
        sym("glutes", "M50 104.5 C57.5 102.5 65.5 103.5 69.5 107.5 C71.5 113.5 69.5 121.5 63.5 125.5 C57.5 127.5 52 124.5 50 120.5 Z") +
        sym("hamstrings", "M52 126.5 C58 127.5 64 126.5 68.5 122.5 C69.5 135 67.5 150 65.5 160.5 C61.5 162.5 57.5 162.5 54.5 160.5 C52.5 148 51 136 52 126.5 Z") +
        sym("calves", "M55 165.5 C59 164.5 63.5 164.5 65.5 165.5 C68 177 65.5 194 62.5 208.5 C60.5 209.5 58 209.5 56 208.5 C53 194 52.5 177 55 165.5 Z")

private val BACK_DETAILS: List<Path> =
    listOf(parse("M50 33 L50 106")) + detail("M53 70 C57 68 60 69 61 72")
