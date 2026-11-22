package com.taigashinohara.mochimori

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/**
 * Builds and pushes the home-screen widget's RemoteViews (v0.3.12).
 *
 * The judgement is [BatteryJudge] with exactly the inputs MainActivity
 * uses — the widget never re-implements the rules. The face is a still
 * Bitmap from [WidgetFaceRenderer]. Since v0.3.27 the still also
 * carries the checklist heat scene (HeatScene over the shared
 * ChecklistReader reads), judged the same way as the home screen.
 *
 * Update timing (honest limits, also in DESIGN.md §9):
 *  - the system's widget schedule: every 30 min (updatePeriodMillis —
 *    1800000 is the platform minimum, it cannot be shorter),
 *  - whenever the app's own periodic battery check runs
 *    ([BatteryCheckReceiver], ~15 min), it calls [updateAll].
 * There is no instant push: plugging in the charger does not refresh
 * the widget until the next scheduled moment.
 */
object WidgetUpdater {

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(context, MochiWidgetProvider::class.java))
        for (id in ids) updateOne(context, mgr, id)
    }

    fun updateOne(context: Context, mgr: AppWidgetManager, appWidgetId: Int) {
        val prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        val snap = BatterySnapshot.read(context)
        val result = BatteryJudge.judge(
            BatteryJudge.Input(
                levelPercent = snap.levelPercent,
                tempC = if (snap.tempKnown) snap.tempC else 0f,
                brightness = snap.brightness,
                autoSavingEnabled = prefs.getBoolean("auto_saving", true),
                saverThresholdPercent = 30,
                charging = snap.charging,
                profileApplied = ProfileManager.isApplied(context),
                canWriteSettings = snap.canWriteSettings,
                levelKnown = snap.levelKnown,
            ),
        )
        // Heat scene (v0.3.27): the still's burning → cooling
        // expression, judged from the same checklist reads as the
        // home screen (shared ChecklistReader + SavingChecklist,
        // limit items excluded) — the widget never judges
        // differently. The auto-saving flag is the same one this
        // updater already feeds the judge.
        val normalEntries = SavingChecklist.evaluateNormal(
            ChecklistReader.read(
                context, snap,
                autoSavingEnabled = prefs.getBoolean("auto_saving", true),
            ),
        )
        val scene = HeatScene.of(normalEntries, charging = snap.charging)
        val (labelText, labelColorRes) = when (result.state) {
            BatteryJudge.State.SAVING -> "節電できてる" to R.color.mochi_state_cool
            BatteryJudge.State.CHARGING -> "充電中" to R.color.mochi_state_cool
            BatteryJudge.State.EMERGENCY -> "残量わずか" to R.color.m3_error
            BatteryJudge.State.UNKNOWN -> "確認中" to R.color.m3_onSurfaceVariant
            BatteryJudge.State.NOT_SAVING -> "節電できてない" to R.color.mochi_warning
        }
        val views = RemoteViews(context.packageName, R.layout.widget_mochi)
        views.setImageViewBitmap(
            R.id.widget_face,
            WidgetFaceRenderer.render(context, result.state, snap.levelPercent, scene),
        )
        views.setTextViewText(R.id.widget_label, labelText)
        views.setTextColor(R.id.widget_label, ContextCompat.getColor(context, labelColorRes))
        views.setTextViewText(
            R.id.widget_percent,
            if (snap.levelKnown) "${snap.levelPercent}%" else "--%",
        )
        // Tap anywhere on the widget opens the app's home screen.
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        mgr.updateAppWidget(appWidgetId, views)
    }
}
