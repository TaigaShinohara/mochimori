package com.taigashinohara.mochimori

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings

/** One sticky-broadcast read of the battery, shared by UI and the checker. */
data class BatterySnapshot(
    val levelPercent: Int,
    val levelKnown: Boolean,
    val tempC: Float,
    val tempKnown: Boolean,
    val charging: Boolean,
    val brightness: Int, // -1 unknown
    val canWriteSettings: Boolean,
) {
    companion object {
        fun read(context: Context): BatterySnapshot {
            val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            var level = -1
            var known = false
            var temp = 0f
            var tempKnown = false
            var charging = false
            if (intent != null) {
                val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (lvl >= 0 && scale > 0) {
                    level = lvl * 100 / scale
                    known = true
                }
                if (intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                    temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                    tempKnown = true
                }
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
            val brightness = try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Settings.SettingNotFoundException) {
                -1
            }
            return BatterySnapshot(
                levelPercent = if (known) level else 0,
                levelKnown = known,
                tempC = temp,
                tempKnown = tempKnown,
                charging = charging,
                brightness = brightness,
                canWriteSettings = Settings.System.canWrite(context),
            )
        }
    }
}
