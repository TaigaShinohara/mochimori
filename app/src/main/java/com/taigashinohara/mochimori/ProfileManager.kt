package com.taigashinohara.mochimori

import android.content.Context
import android.provider.Settings

/**
 * The real auto profile (R1): when applied, brightness is really lowered to
 * ~30% and the screen timeout is really shortened to 15s. The original values
 * are saved on first apply and restored when the battery recovers.
 */
object ProfileManager {
    const val SAVER_BRIGHTNESS = 77 // ~30% of 255
    const val SAVER_TIMEOUT_MS = 15_000

    private fun prefs(context: Context) =
        context.getSharedPreferences("rules", Context.MODE_PRIVATE)

    fun isApplied(context: Context): Boolean =
        prefs(context).getBoolean("profile_applied", false)

    /** Returns true when the profile is in effect after this call. */
    fun apply(context: Context): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val p = prefs(context)
        val cr = context.contentResolver
        if (!p.getBoolean("profile_applied", false)) {
            // Save originals exactly once, before overwriting them.
            val origBrightness = try {
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Settings.SettingNotFoundException) { -1 }
            val origMode = try {
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE)
            } catch (e: Settings.SettingNotFoundException) { -1 }
            val origTimeout = try {
                Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT)
            } catch (e: Settings.SettingNotFoundException) { -1 }
            p.edit()
                .putInt("orig_brightness", origBrightness)
                .putInt("orig_brightness_mode", origMode)
                .putInt("orig_timeout", origTimeout)
                .apply()
        }
        Settings.System.putInt(
            cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, SAVER_BRIGHTNESS)
        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, SAVER_TIMEOUT_MS)
        p.edit().putBoolean("profile_applied", true).apply()
        return true
    }

    /** Restore the saved originals. No-op when not applied. */
    fun restore(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean("profile_applied", false)) return
        if (Settings.System.canWrite(context)) {
            val cr = context.contentResolver
            val origBrightness = p.getInt("orig_brightness", -1)
            val origMode = p.getInt("orig_brightness_mode", -1)
            val origTimeout = p.getInt("orig_timeout", -1)
            if (origBrightness >= 0) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, origBrightness)
            }
            if (origMode >= 0) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, origMode)
            }
            if (origTimeout > 0) {
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, origTimeout)
            }
        }
        p.edit().putBoolean("profile_applied", false).apply()
    }
}
