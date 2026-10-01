package com.hedefit.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.hedefit.wear.MainActivity
import com.hedefit.wear.data.Snapshot
import com.hedefit.wear.data.SnapshotStore

abstract class BaseComplication : SuspendingComplicationDataSourceService() {
    abstract fun build(type: ComplicationType, s: Snapshot, tap: PendingIntent): ComplicationData?

    /** Dokununca ilgili sayfada açılır; istek kodu sayfaya göre ayrılır ki extra'lar karışmasın. */
    abstract val targetPage: Int

    private fun tap() = PendingIntent.getActivity(
        this, targetPage + 1, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, targetPage), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Snapshot(steps = 6_400, waterMl = 1_250, streakDays = 12), tap())

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, SnapshotStore.load(applicationContext), tap())
}

private fun text(value: String) = PlainComplicationText.Builder(value).build()
private fun short(n: Int) = if (n >= 1000) "%.1fK".format(n / 1000f) else "$n"

class StepsComplicationService : BaseComplication() {
    override val targetPage = com.hedefit.wear.ui.Pages.SUMMARY
    override fun build(type: ComplicationType, s: Snapshot, tap: PendingIntent): ComplicationData? = when (type) {
        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(s.steps.toFloat().coerceAtMost(s.stepGoal.toFloat()), 0f, s.stepGoal.toFloat(), text(if (s.isEnglish) "Steps" else "Adım"))
            .setText(text(short(s.steps))).setTapAction(tap).build()
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(short(s.steps)), text(if (s.isEnglish) "Steps" else "Adım")).setTapAction(tap).build()
        else -> null
    }
}

class WaterComplicationService : BaseComplication() {
    override val targetPage = com.hedefit.wear.ui.Pages.WATER
    override fun build(type: ComplicationType, s: Snapshot, tap: PendingIntent): ComplicationData? {
        val liters = "%.1f".format(s.waterMl / 1000f)
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(s.waterMl.toFloat().coerceAtMost(s.waterGoal.toFloat()), 0f, s.waterGoal.toFloat(), text(if (s.isEnglish) "Water" else "Su"))
                .setText(text(liters)).setTapAction(tap).build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(liters), text(if (s.isEnglish) "Water" else "Su")).setTapAction(tap).build()
            else -> null
        }
    }
}

class StreakComplicationService : BaseComplication() {
    override val targetPage = com.hedefit.wear.ui.Pages.SUMMARY
    override fun build(type: ComplicationType, s: Snapshot, tap: PendingIntent): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) ShortTextComplicationData.Builder(text("${s.streakDays}"), text(if (s.isEnglish) "Streak" else "Seri")).setTapAction(tap).build() else null
}
