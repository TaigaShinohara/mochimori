package com.taigashinohara.mochimori

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
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
    // Health fields (v0.3.13). Each carries its own "not reported"
    // sentinel; the UI shows only what the device actually reported
    // and never fills a gap with a guess (DESIGN §10).
    /** BatteryManager.EXTRA_HEALTH value; -1 when the broadcast has none. */
    val health: Int = -1,
    /** EXTRA_VOLTAGE in millivolts; -1 when not reported. */
    val voltageMv: Int = -1,
    /** EXTRA_CYCLE_COUNT (API 34+); -1 when the device does not report it. */
    val cycleCount: Int = -1,
    /** BATTERY_PROPERTY_CHARGE_COUNTER in microamp-hours; Long.MIN_VALUE when unsupported. */
    val chargeCounterMicroAh: Long = Long.MIN_VALUE,
    /** BatteryManager.computeChargeTimeRemaining() (API 28+); -1 when unavailable. */
    val chargeTimeRemainingMs: Long = -1L,
) {
    companion object {
        fun read(context: Context): BatterySnapshot {
            val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            var level = -1
            var known = false
            var temp = 0f
            var tempKnown = false
            var charging = false
            var health = -1
            var voltageMv = -1
            var cycleCount = -1
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
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                // EXTRA_CYCLE_COUNT exists only from API 34; on older
                // systems (or devices that simply don't fill it) the
                // extra is absent and the count stays "not reported".
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    cycleCount = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
                }
            }
            val batteryManager = context.getSystemService(BatteryManager::class.java)
            // getLongProperty returns Long.MIN_VALUE when the property
            // is unsupported — kept as the sentinel, never coerced.
            val chargeCounter = batteryManager
                ?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                ?: Long.MIN_VALUE
            // -1 when the platform cannot compute it (or pre-API 28).
            val chargeTimeRemaining = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                batteryManager != null
            ) {
                batteryManager.computeChargeTimeRemaining()
            } else {
                -1L
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
                health = health,
                voltageMv = voltageMv,
                cycleCount = cycleCount,
                chargeCounterMicroAh = chargeCounter,
                chargeTimeRemainingMs = chargeTimeRemaining,
            )
        }
    }
}
