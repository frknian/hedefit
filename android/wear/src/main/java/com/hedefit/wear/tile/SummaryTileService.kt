package com.hedefit.wear.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hedefit.wear.MainActivity
import com.hedefit.wear.data.SnapshotStore

private const val VERSION = "1"

/** Günlük özet tile'ı: adım, su ve seri; dokununca uygulama açılır. */
class SummaryTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val s = SnapshotStore.load(applicationContext)
        fun text(value: String, color: Long, size: Float) = LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setColor(argb(color.toInt())).setSize(sp(size)).build())
            .build()
        val open = ModifiersBuilders.Clickable.Builder().setId("open").setOnClick(
            ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(MainActivity::class.java.name).build()
            ).build()
        ).build()
        val layout = LayoutElementBuilders.Box.Builder()
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(text("Hedefit", 0xFF22C55E, 12f))
                    .addContent(text("%,d adım".format(s.steps), 0xFFF8FAFC, 22f))
                    .addContent(text("%.1f / %.1f L su".format(s.waterMl / 1000f, s.waterGoal / 1000f), 0xFF38BDF8, 14f))
                    .addContent(text("${s.streakDays} gün seri", 0xFFFBBF24, 14f))
                    .build()
            ).build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(VERSION)
            .setFreshnessIntervalMillis(15 * 60_000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(VERSION).build())
}
