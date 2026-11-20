package com.taigashinohara.mochimori

import android.content.Context

/**
 * Lightweight persistent history of (time, level, charging) samples —
 * the measured basis for the remaining-time estimate (v0.3.13).
 *
 * Recorded by the periodic checker (~every 15 min) and when the app
 * reads a snapshot on screen. Survives reboots because it lives in
 * SharedPreferences (no BOOT permission involved; recording simply
 * resumes at the next check after the app is opened again, same as
 * the checker itself). Storage is one compact string, capped by
 * [BatteryPace.prune] at 64 samples / 48 hours.
 */
object BatteryHistory {
    private const val PREFS_NAME = "battery_history"
    private const val KEY_SAMPLES = "samples_v1"

    /** Skip a record that adds no information: same level and charging
     *  state as the last sample, less than 5 minutes later. */
    private const val MIN_RECORD_GAP_MS = 5L * 60 * 1000

    fun load(context: Context): List<BatteryPace.Sample> =
        BatteryPace.decode(prefs(context).getString(KEY_SAMPLES, null))

    fun record(context: Context, snap: BatterySnapshot) {
        if (!snap.levelKnown) return
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val samples = BatteryPace.prune(BatteryPace.decode(p.getString(KEY_SAMPLES, null)), now)
        val last = samples.lastOrNull()
        if (last != null &&
            now - last.timeMs < MIN_RECORD_GAP_MS &&
            last.levelPercent == snap.levelPercent &&
            last.charging == snap.charging
        ) {
            return
        }
        val next = BatteryPace.prune(
            samples + BatteryPace.Sample(now, snap.levelPercent, snap.charging),
            now,
        )
        p.edit().putString(KEY_SAMPLES, BatteryPace.encode(next)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
