package com.taigashinohara.mochimori

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Periodic battery check, scheduled with AlarmManager (inexact repeating,
 * ~15 min). Deliberately NOT a foreground service and NOT WorkManager:
 * standard APIs only, zero extra dependencies, minimal battery cost.
 *
 * Honest limits:
 * - Inexact: Android may batch/defer alarms, so events can be delayed by
 *   up to ~15 min (more in Doze). Notifications and profile changes are
 *   therefore "within about 15 minutes", never instant.
 * - The alarm is (re)scheduled when the app is opened. After a reboot it
 *   stops until the app is opened again (no boot receiver, by design:
 *   no extra permission).
 */
class BatteryCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        val snap = BatterySnapshot.read(context)
        if (!snap.levelKnown) return

        // --- Notifications (R2) ---
        // 80%: once per charging session. The flag clears when unplugged or
        // when the level drops below 75%, so the next session notifies again.
        if (snap.charging && snap.levelPercent >= BatteryJudge.CHARGE_GOOD_PERCENT) {
            if (prefs.getBoolean("notify_80", true) &&
                !prefs.getBoolean("notified_80_session", false)
            ) {
                Notifier.notifyCharge80(context, snap.levelPercent)
                prefs.edit().putBoolean("notified_80_session", true).apply()
            }
        } else if (!snap.charging || snap.levelPercent < 75) {
            if (prefs.getBoolean("notified_80_session", false)) {
                prefs.edit().putBoolean("notified_80_session", false).apply()
            }
        }
        // Heat: at most once per hour.
        if (snap.tempKnown && snap.tempC >= BatteryJudge.HOT_TEMP_C &&
            prefs.getBoolean("notify_heat", true)
        ) {
            val last = prefs.getLong("last_heat_notify_ms", 0L)
            val now = System.currentTimeMillis()
            if (now - last >= 60 * 60 * 1000L) {
                Notifier.notifyHeat(context, snap.tempC)
                prefs.edit().putLong("last_heat_notify_ms", now).apply()
            }
        }

        // --- Auto profile (R1) ---
        val autoOn = prefs.getBoolean("auto_saving", true)
        val threshold = 30
        if (!snap.charging && autoOn && snap.levelPercent <= threshold) {
            ProfileManager.apply(context) // no-op without WRITE_SETTINGS grant
        } else {
            ProfileManager.restore(context) // no-op when not applied
        }
    }

    companion object {
        const val ACTION_CHECK = "com.taigashinohara.mochimori.ACTION_PERIODIC_CHECK"
        const val INTERVAL_MS = 15 * 60 * 1000L

        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, BatteryCheckReceiver::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                pi,
            )
        }
    }
}
