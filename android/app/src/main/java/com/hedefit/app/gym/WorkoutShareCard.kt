package com.hedefit.app.gym

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.hedefit.app.data.model.WorkoutSetInput
import java.io.File
import java.io.FileOutputStream

data class WorkoutSummary(
    val title: String,
    val durationSeconds: Int,
    val calories: Int,
    val sets: List<WorkoutSetInput>,
    val personalRecords: List<PersonalRecordResult>,
    val exerciseAreas: Map<String, String> = emptyMap(),
) {
    val exerciseCount = sets.distinctBy { it.exerciseId }.size
    val setCount = sets.size
    val repetitions = sets.sumOf { it.reps ?: 0 }
    val volumeKg = sets.sumOf { setVolume(it.weightKg, it.reps) }
    val strongestExercise = sets.groupBy { it.exerciseName }.maxByOrNull { (_, exerciseSets) -> exerciseSets.sumOf { setVolume(it.weightKg, it.reps) } }?.key
    val muscleGroups = sets.map { normalizeMuscle(exerciseAreas[it.exerciseId]?.takeIf(String::isNotBlank) ?: it.exerciseName) }
        .filter { muscle -> muscleRegions.any { it.muscle == muscle } }
        .distinct()
}

object WorkoutShareCard {
    enum class Template { WORKOUT_SUMMARY, PERSONAL_RECORD, MUSCLE_MAP }

    fun share(context: Context, summary: WorkoutSummary, template: Template = Template.WORKOUT_SUMMARY) {
        shareBitmap(context, render(context, summary, template), summary, template)
    }

    fun render(context: Context, summary: WorkoutSummary, template: Template = Template.WORKOUT_SUMMARY): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(8, 10, 9))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) }
        paint.color = Color.rgb(181, 255, 43); paint.textSize = 54f
        canvas.drawText("HEDEFİT", 84f, 150f, paint)
        when (template) {
            Template.WORKOUT_SUMMARY -> {
                paint.color = Color.WHITE; paint.textSize = 112f
                drawMultiline(canvas, summary.title.uppercase(), 84f, 390f, 920f, paint)
                paint.color = Color.rgb(181, 255, 43); paint.textSize = 126f
                canvas.drawText("${summary.setCount} SET", 84f, 830f, paint)
                paint.color = Color.WHITE; paint.textSize = 94f
                canvas.drawText(summary.volumeKg.compactKg().uppercase(), 84f, 1020f, paint)
                canvas.drawText(formatDuration(summary.durationSeconds), 84f, 1190f, paint)
                paint.color = Color.rgb(158, 166, 161); paint.textSize = 44f
                canvas.drawText("${summary.exerciseCount} hareket  •  ${summary.repetitions} tekrar  •  ${summary.calories} kcal", 84f, 1320f, paint)
            }
            Template.PERSONAL_RECORD -> {
                val record = summary.personalRecords.firstOrNull()
                paint.color = Color.rgb(181, 255, 43); paint.textSize = 100f
                canvas.drawText("YENİ PR", 84f, 460f, paint)
                paint.color = Color.WHITE; paint.textSize = 108f
                drawMultiline(canvas, record?.exerciseName?.uppercase() ?: summary.strongestExercise?.uppercase() ?: summary.title.uppercase(), 84f, 700f, 920f, paint)
                paint.color = Color.rgb(181, 255, 43); paint.textSize = 78f
                canvas.drawText("TAHMİNİ 1RM  %.1f KG".format(record?.estimatedOneRepMax ?: 0.0), 84f, 1160f, paint)
            }
            Template.MUSCLE_MAP -> {
                paint.color = Color.WHITE; paint.textSize = 80f
                canvas.drawText("BUGÜN ÇALIŞAN KASLAR", 84f, 300f, paint)
                drawAnatomy(context, canvas, android.graphics.RectF(40f, 360f, 1040f, 1360f), summary.muscleGroups.toSet())
                paint.color = Color.rgb(181, 255, 43); paint.textSize = 58f
                drawMultiline(canvas, summary.muscleGroups.joinToString("  •  ") { muscleNameTr(it).uppercase() }.ifBlank { "ANTRENMAN TAMAMLANDI" }, 84f, 1470f, 920f, paint)
            }
        }
        paint.color = Color.rgb(75, 82, 78); paint.strokeWidth = 4f
        canvas.drawLine(84f, 1690f, 996f, 1690f, paint)
        paint.color = Color.WHITE; paint.textSize = 42f
        canvas.drawText("Bugün dünden daha güçlüsün.", 84f, 1780f, paint)
        return bitmap
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, summary: WorkoutSummary, template: Template) {
        val directory = File(context.cacheDir, "shares").apply { mkdirs() }
        val output = File(directory, "hedefit-${template.name.lowercase()}.png")
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", output)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${summary.title} • ${summary.setCount} set • ${summary.volumeKg.compactKg()} • ${formatDuration(summary.durationSeconds)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Antrenman özetini paylaş"))
    }

    private fun drawAnatomy(context: Context, canvas: Canvas, area: android.graphics.RectF, worked: Set<String>) {
        val anatomy = android.graphics.BitmapFactory.decodeResource(context.resources, com.hedefit.app.R.drawable.muscle_anatomy) ?: return
        val side = minOf(area.width(), area.height())
        val target = android.graphics.RectF(area.centerX() - side / 2, area.centerY() - side / 2, area.centerX() + side / 2, area.centerY() + side / 2)
        val body = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
            alpha = 150
        }
        canvas.drawBitmap(anatomy, null, target, body)
        val heat = Paint(Paint.ANTI_ALIAS_FLAG)
        muscleRegions.filter { it.muscle in worked }.forEach { region ->
            val cx = target.left + region.x * side
            val cy = target.top + region.y * side
            val rx = region.rx * side * 1.45f
            val ry = region.ry * side * 1.45f
            heat.shader = android.graphics.RadialGradient(cx, cy, rx, intArrayOf(Color.argb(235, 181, 255, 43), Color.argb(150, 181, 255, 43), Color.argb(0, 181, 255, 43)), floatArrayOf(0f, .55f, 1f), android.graphics.Shader.TileMode.CLAMP)
            canvas.save()
            canvas.scale(1f, ry / rx, cx, cy)
            canvas.drawCircle(cx, cy, rx, heat)
            canvas.restore()
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(158, 166, 161); textSize = 38f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        canvas.drawText("ÖN", target.left + side * .29f, target.bottom - 8f, label)
        canvas.drawText("ARKA", target.left + side * .65f, target.bottom - 8f, label)
    }

    private fun drawMultiline(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        val words = text.split(' ')
        var line = ""
        var baseline = y
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, baseline, paint); baseline += paint.textSize * 1.12f; line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) canvas.drawText(line, x, baseline, paint)
    }

    private fun formatDuration(seconds: Int) = "%02d:%02d".format(seconds / 3600, seconds / 60 % 60)
}
