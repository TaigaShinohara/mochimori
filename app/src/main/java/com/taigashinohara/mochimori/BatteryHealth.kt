package com.taigashinohara.mochimori

import android.os.BatteryManager
import kotlin.math.roundToInt

/**
 * Pure wording/maths for the battery-health lines (v0.3.13).
 *
 * Honesty rules (DESIGN §10): a general app cannot read the design
 * capacity, so a degradation percentage is never shown. The full-charge
 * capacity below is an estimate derived from the reported charge
 * counter and is always labelled 推定. Anything the device does not
 * report maps to null, and the UI then omits the line entirely —
 * absence over a made-up "不明".
 */
object BatteryHealth {

    /** Below this level the counter ÷ level ratio amplifies the whole-%
     *  rounding too much to quote even as an estimate. */
    const val MIN_LEVEL_FOR_CAPACITY_PERCENT = 20

    /** Human wording for BatteryManager health values; null = don't show. */
    fun healthLabel(health: Int): String? = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "過熱しています"
        BatteryManager.BATTERY_HEALTH_DEAD -> "異常"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "電圧が高すぎます"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "異常"
        BatteryManager.BATTERY_HEALTH_COLD -> "冷えすぎています"
        else -> null // BATTERY_HEALTH_UNKNOWN or not reported
    }

    /**
     * Estimated current full-charge capacity in mAh, extrapolated from
     * the charge counter (µAh remaining) and the level %. Null when the
     * counter is unsupported, the level is too low to extrapolate, or
     * the result falls outside a plausible phone-battery range.
     */
    fun estimatedFullCapacityMah(chargeCounterMicroAh: Long, levelPercent: Int): Int? {
        if (chargeCounterMicroAh == Long.MIN_VALUE || chargeCounterMicroAh <= 0) return null
        if (levelPercent < MIN_LEVEL_FOR_CAPACITY_PERCENT) return null
        val mah = chargeCounterMicroAh * 100.0 / levelPercent / 1000.0
        if (mah < 500.0 || mah > 30_000.0) return null
        return mah.roundToInt()
    }
}
