package com.hedefit.app.gym

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.hedefit.app.data.model.UserChallengeData
import com.hedefit.app.ui.i18n.tr
import java.io.File
import java.io.FileOutputStream

/**
 * Challenge paylaşım kartı (1080×1350, sosyal akışlara uygun 4:5). Uygulamanın koyu zemini ve yeşil vurgusu;
 * yalnızca challenge adı, gün, seri ve kazanılan XP. Sağlık verisi içermez.
 */
object ChallengeShareCard {
    private const val BRAND = 0xFF22C55E.toInt()
    private val BACKGROUND = Color.rgb(9, 10, 12)
    private val SURFACE = Color.rgb(18, 21, 27)
    private val MUTED = Color.rgb(148, 163, 184)

    fun share(context: Context, challenge: UserChallengeData, earnedXp: Int) {
        val bitmap = render(challenge, earnedXp)
        val directory = File(context.cacheDir, "shares").apply { mkdirs() }
        val output = File(directory, "hedefit-challenge.png")
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", output)
        val state = challenge.state()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, tr(
                "${challenge.plan.title.tr} · Gün ${state.doneDays}/${state.totalDays} · Hedefit",
                "${challenge.plan.title.en} · Day ${state.doneDays}/${state.totalDays} · Hedefit",
            ))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, tr("Challenge'ı paylaş", "Share challenge")))
    }

    fun render(challenge: UserChallengeData, earnedXp: Int): Bitmap {
        val width = 1080; val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height * .6f, Color.argb(70, 34, 197, 94), Color.argb(0, 34, 197, 94), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glow)
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val state = challenge.state()

        bold.color = BRAND; bold.textSize = 46f
        canvas.drawText("HEDEFİT", 90f, 140f, bold)
        bold.color = MUTED; bold.textSize = 36f
        canvas.drawText(tr("CHALLENGE", "CHALLENGE"), 90f, 250f, bold)

        bold.color = Color.WHITE; bold.textSize = 92f
        val titleBottom = drawMultiline(canvas, challenge.plan.title.text(), 90f, 360f, 900f, bold)

        val cardTop = titleBottom + 70f
        val card = RectF(90f, cardTop, width - 90f, cardTop + 360f)
        canvas.drawRoundRect(card, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE })
        bold.color = BRAND; bold.textSize = 120f
        canvas.drawText(tr("Gün ${state.doneDays}", "Day ${state.doneDays}"), card.left + 56f, card.top + 160f, bold)
        bold.color = MUTED; bold.textSize = 52f
        canvas.drawText("/ ${state.totalDays}", card.left + 56f + bold.measureText(" ") + Paint(bold).apply { textSize = 120f }.measureText(tr("Gün ${state.doneDays}", "Day ${state.doneDays}")), card.top + 160f, bold)
        // İlerleme çubuğu
        val barTop = card.top + 220f
        val track = RectF(card.left + 56f, barTop, card.right - 56f, barTop + 22f)
        canvas.drawRoundRect(track, 11f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(34, 41, 52) })
        val fill = RectF(track.left, track.top, track.left + track.width() * (state.percent / 100f), track.bottom)
        canvas.drawRoundRect(fill, 11f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND })
        bold.color = MUTED; bold.textSize = 36f
        canvas.drawText("%${state.percent}".let { tr(it, "${state.percent}%") }, card.left + 56f, card.top + 310f, bold)

        var y = card.bottom + 130f
        bold.textSize = 64f; bold.color = Color.WHITE
        if (state.streak > 0) {
            canvas.drawText(tr("🔥 ${state.streak} günlük seri", "🔥 ${state.streak}-day streak"), 90f, y, bold)
            y += 100f
        }
        if (earnedXp > 0) {
            bold.color = BRAND
            canvas.drawText("+$earnedXp XP", 90f, y, bold)
        }
        bold.color = MUTED; bold.textSize = 34f
        canvas.drawText(tr("Hedefit ile takip ediliyor", "Tracked with Hedefit"), 90f, height - 90f, bold)
        return bitmap
    }

    private fun drawMultiline(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint): Float {
        var line = ""
        var baseline = y
        text.split(' ').forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, baseline, paint); baseline += paint.textSize * 1.1f; line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) canvas.drawText(line, x, baseline, paint)
        return baseline
    }
}
