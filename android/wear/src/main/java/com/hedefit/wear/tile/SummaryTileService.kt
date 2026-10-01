package com.hedefit.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
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
import com.hedefit.wear.R
import com.hedefit.wear.data.PhoneLink
import com.hedefit.wear.data.SnapshotStore

private const val VERSION = "1"
private const val WATER_ML = 250

/** Günlük özet tile'ı: adım, su ve seri. "+250 ml" düğmesi tek dokunuşla su ekler, gerisi uygulamayı açar. */
class SummaryTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        if (requestParams.currentState.lastClickableId == "water") PhoneLink.addWaterInBackground(applicationContext, WATER_ML)
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
        val waterButton = LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(0xFF38BDF8.toInt())).setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(16f)).build()).build())
                    .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(14f)).setEnd(dp(14f)).setTop(dp(5f)).setBottom(dp(5f)).build())
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId("water").setOnClick(ActionBuilders.LoadAction.Builder().build()).build())
                    .build()
            )
            .addContent(text("+$WATER_ML ml", 0xFF04202E, 14f))
            .build()
        val layout = LayoutElementBuilders.Box.Builder()
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(text("Hedefit", 0xFF22C55E, 12f))
                    .addContent(text(getString(R.string.tile_steps, "%,d".format(s.steps)), 0xFFF8FAFC, 22f))
                    .addContent(text("%.1f / %.1f L %s".format(s.waterMl / 1000f, s.waterGoal / 1000f, getString(R.string.tile_water_label)), 0xFF38BDF8, 14f))
                    .addContent(text(getString(R.string.tile_streak, s.streakDays), 0xFFFBBF24, 14f))
                    .addContent(waterButton)
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
