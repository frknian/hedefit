package com.hedefit.app.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Özel widget ana ekrana sabitlendiğinde bekleyen ayarı yeni widget'a uygular. */
class CustomWidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        CustomWidgetConfigStore.takePending(context)?.let { CustomWidgetConfigStore.write(context, id, it) }
        HedefitWidgets.render(context, AppWidgetManager.getInstance(context), id, "CustomWidget")
    }
}
