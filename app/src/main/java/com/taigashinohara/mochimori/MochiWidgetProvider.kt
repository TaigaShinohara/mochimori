package com.taigashinohara.mochimori

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Home-screen widget (v0.3.12). All rendering lives in [WidgetUpdater];
 * this provider only forwards the system's callbacks. The 30-minute
 * cadence is declared in res/xml/widget_mochi_info.xml
 * (updatePeriodMillis) — the platform floor.
 */
class MochiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) WidgetUpdater.updateOne(context, mgr, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // A resize gets a fresh render at the same still-frame spec.
        WidgetUpdater.updateOne(context, mgr, appWidgetId)
    }
}
