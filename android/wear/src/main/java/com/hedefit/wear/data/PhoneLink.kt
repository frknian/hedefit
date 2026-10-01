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

    suspend fun flush(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getInt("pending", 0)
        if (pending <= 0) return
        runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return
            nodes.forEach { Wearable.getMessageClient(context).sendMessage(it.id, WATER_PATH, pending.toString().toByteArray()).await() }
            prefs.edit().putInt("pending", 0).apply()
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
