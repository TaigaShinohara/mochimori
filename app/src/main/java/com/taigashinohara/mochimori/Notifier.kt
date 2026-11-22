package com.taigashinohara.mochimori

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Real notifications for the 80%-charge and heat events (R2),
 *  and the user's own level alerts (v0.3.22). All share the one
 *  channel: they are the same kind of quiet heads-up. */
object Notifier {
    const val CHANNEL_ID = "mochimori_alerts"
    private const val ID_CHARGE_80 = 1001
    private const val ID_HEAT = 1002

    // Level alerts get one notification id per threshold (1101..
    // 1199), so several thresholds crossed between two checks each
    // keep their own notification instead of overwriting one.
    private const val ID_LEVEL_BASE = 1100

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "電池の気づき", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun post(context: Context, id: Int, title: String, text: String, icon: Int) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    fun notifyCharge80(context: Context, levelPercent: Int) {
        post(
            context, ID_CHARGE_80,
            "充電 $levelPercent% になりました",
            "ここで抜くと電池が長持ちします",
            android.R.drawable.ic_lock_idle_charging,
        )
    }

    /**
     * A user-chosen level threshold was reached while discharging
     * (v0.3.22). Same quiet tone as the other alerts: state the
     * fact, then the current level.
     */
    fun notifyLevel(context: Context, thresholdPercent: Int, levelPercent: Int) {
        post(
            context, ID_LEVEL_BASE + thresholdPercent,
            "残量が ${thresholdPercent}% になりました",
            "いまの残量は ${levelPercent}% です",
            android.R.drawable.ic_lock_idle_low_battery,
        )
    }

    fun notifyHeat(context: Context, tempC: Float) {
        post(
            context, ID_HEAT,
            "電池が熱くなっています",
            "現在 ${String.format("%.1f", tempC)}℃。冷ましてから使うと長持ちします",
            android.R.drawable.ic_dialog_alert,
        )
    }
}
