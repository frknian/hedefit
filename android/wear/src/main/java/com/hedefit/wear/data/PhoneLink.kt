package com.hedefit.wear.data

import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import android.content.ComponentName
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.hedefit.wear.complication.StepsComplicationService
import com.hedefit.wear.complication.StreakComplicationService
import com.hedefit.wear.complication.WaterComplicationService
import com.hedefit.wear.tile.SummaryTileService
import kotlinx.coroutines.tasks.await

private const val SNAPSHOT_PATH = "/hedefit/snapshot"
private const val WATER_PATH = "/hedefit/water"
private const val WORKOUT_PATH = "/hedefit/workout"

/** Saat -> telefon: su ekleme mesajı. Telefon erişilemezse miktar kuyruğa alınır. */
object PhoneLink {
    private const val PREFS = "phone_link"

    /** Özeti iyimser günceller, mesajı gönderir. */
    suspend fun addWater(context: Context, ml: Int) {
        val current = SnapshotStore.snapshot.value
        publish(context, current.copy(waterMl = (current.waterMl + ml).coerceIn(0, 20_000)))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt("pending", prefs.getInt("pending", 0) + ml).apply()
        flush(context)
    }

    /** Biten antrenmanı kuyruğa alır ve gönderir; telefon erişilemezse sonraki açılışta tekrar dener. */
    suspend fun queueWorkout(context: Context, kind: String, durationSec: Long, distanceM: Double, calories: Int) {
        if (durationSec < 60) return
        val json = org.json.JSONObject()
            .put("id", java.util.UUID.randomUUID().toString()).put("kind", kind)
            .put("durationSec", durationSec).put("distanceM", distanceM).put("calories", calories).toString()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("workouts", prefs.getStringSet("workouts", emptySet()).orEmpty() + json).apply()
        flush(context)
    }

    suspend fun flush(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val water = prefs.getInt("pending", 0)
        val workouts = prefs.getStringSet("workouts", emptySet()).orEmpty()
        if (water <= 0 && workouts.isEmpty()) return
        runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return
            val client = Wearable.getMessageClient(context)
            if (water > 0) {
                nodes.forEach { client.sendMessage(it.id, WATER_PATH, water.toString().toByteArray()).await() }
                prefs.edit().putInt("pending", 0).apply()
            }
            // Her antrenman ayrı gönderilir; başarılı olan kuyruktan çıkar. Telefon aynı id'yi iki kez kaydetmez.
            for (json in workouts) {
                nodes.forEach { client.sendMessage(it.id, WORKOUT_PATH, json.toByteArray()).await() }
                prefs.edit().putStringSet("workouts", prefs.getStringSet("workouts", emptySet()).orEmpty() - json).apply()
            }
        }
    }

    fun publish(context: Context, value: Snapshot) {
        SnapshotStore.save(context, value)
        TileService.getUpdater(context).requestUpdate(SummaryTileService::class.java)
        listOf(StepsComplicationService::class.java, WaterComplicationService::class.java, StreakComplicationService::class.java).forEach {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, it)).requestUpdateAll()
        }
    }
}

/** Telefonun gönderdiği özeti karşılar (uygulama kapalıyken de). */
class WatchListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == SNAPSHOT_PATH }.forEach {
            PhoneLink.publish(applicationContext, Snapshot.from(DataMapItem.fromDataItem(it.dataItem).dataMap))
        }
    }
}
