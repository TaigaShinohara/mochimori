package com.taigashinohara.mochimori

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.res.Configuration
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * The measured reads behind [SavingChecklist], shared by the home
 * screen (MainActivity) and the widget (WidgetUpdater) so both
 * judge from identical inputs.
 */
object ChecklistReader {

    /**
     * Assemble the checklist's measured inputs. The battery facts come
     * from the snapshot the home screen already read; the values the
     * snapshot does not carry (brightness mode, screen-off timeout,
     * power-save mode, and the v0.3.16 radio/theme/haptic reads) are
     * read here, each with its own "could not read" sentinel so
     * SavingChecklist can stay honest (UNKNOWN -> 「確認中」, or the
     * row omitted where the spec says so) instead of guessing.
     * Reading settings needs no permission beyond what the app
     * already uses.
     *
     * v0.3.27: moved out of MainActivity into this shared reader so
     * the home screen and the widget assemble the checklist from
     * exactly the same reads — the widget's heat expression must
     * never be able to disagree with the home screen's list.
     */
    fun read(context: Context, s: BatterySnapshot, autoSavingEnabled: Boolean): SavingChecklist.Input {
        val cr = context.contentResolver
        val brightnessAuto: Boolean? = try {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Settings.SettingNotFoundException) {
            null
        }
        val timeoutMs = try {
            Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (e: Settings.SettingNotFoundException) {
            -1
        }
        val powerSave: Boolean? = try {
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode
        } catch (e: Exception) {
            null
        }
        // v0.3.16: the expanded checklist's reads. Each one is a
        // plain state read that needs no permission beyond the three
        // the app already carries; each failure lands on its own
        // "could not read" sentinel (see SavingChecklist).
        val wifiManager = try {
            context.getSystemService(WifiManager::class.java)
        } catch (e: Exception) {
            null
        }
        val wifiEnabled: Boolean? = try {
            wifiManager?.isWifiEnabled
        } catch (e: Exception) {
            null
        }
        // The supplicant state is not among the WifiInfo fields the
        // platform redacts for apps without the location permission
        // (SSID / BSSID / network id are), so COMPLETED is a real
        // "associated" signal. Transitional states settle on null —
        // the row then says 確認中 instead of guessing either way.
        @Suppress("DEPRECATION")
        val wifiConnected: Boolean? = if (wifiEnabled == true) {
            try {
                when (wifiManager?.connectionInfo?.supplicantState) {
                    SupplicantState.COMPLETED -> true
                    SupplicantState.DISCONNECTED, SupplicantState.INACTIVE,
                    SupplicantState.UNINITIALIZED, SupplicantState.INTERFACE_DISABLED,
                    SupplicantState.INVALID -> false
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        val locationEnabled: Boolean? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.getSystemService(LocationManager::class.java)?.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(cr, Settings.Secure.LOCATION_MODE) !=
                    Settings.Secure.LOCATION_MODE_OFF
            }
        } catch (e: Exception) {
            null
        }
        val nfcAdapter = try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (e: Exception) {
            null
        }
        val nfcEnabled: Boolean? = if (nfcAdapter != null) {
            try {
                nfcAdapter.isEnabled
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        // The "mobile_data" global setting is a plain read (its
        // Settings.Global constant is not public API, so the key is
        // spelled out); when the key is absent the sentinel stays
        // down and the checklist omits the row rather than guessing.
        var mobileDataReadable = false
        var mobileDataEnabled: Boolean? = null
        try {
            when (Settings.Global.getInt(cr, "mobile_data", -1)) {
                1 -> {
                    mobileDataReadable = true
                    mobileDataEnabled = true
                }
                0 -> {
                    mobileDataReadable = true
                    mobileDataEnabled = false
                }
            }
        } catch (e: Exception) {
            // Unreadable: the row is omitted (see SavingChecklist).
        }
        val darkTheme =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val hapticEnabled: Boolean? = try {
            when (Settings.System.getInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, -1)) {
                1 -> true
                0 -> false
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        // isEnabled is one of the adapter reads that does not
        // require BLUETOOTH_CONNECT; whether anything is *connected*
        // would require it. (Since v0.3.17 Bluetooth is judged on
        // the limit tier, where that distinction is not needed.)
        val bluetoothOn: Boolean? = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
        } catch (e: Exception) {
            null
        }
        // Data saver (v0.3.17, limit tier): getRestrictBackground
        // Status() is the one ConnectivityManager read that carries
        // no permission annotation in the platform (the network-state
        // reads beside it do, and stay unused). ENABLED and
        // WHITELISTED both mean the device's data saver is in force
        // — WHITELISTED only exempts this app — so both read as
        // "restricted". Any failure leaves the sentinel down and the
        // checklist omits the row rather than guessing.
        var dataSaverReadable = false
        var dataSaverRestricted: Boolean? = null
        try {
            when (
                context.getSystemService(ConnectivityManager::class.java)
                    ?.getRestrictBackgroundStatus()
            ) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> {
                    dataSaverReadable = true
                    dataSaverRestricted = true
                }
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> {
                    dataSaverReadable = true
                    dataSaverRestricted = false
                }
            }
        } catch (e: Exception) {
            // Unreadable: the row is omitted (see SavingChecklist).
        }
        return SavingChecklist.Input(
            levelPercent = s.levelPercent,
            levelKnown = s.levelKnown,
            charging = s.charging,
            tempC = s.tempC,
            tempKnown = s.tempKnown,
            brightness = s.brightness,
            brightnessAuto = brightnessAuto,
            screenOffTimeoutMs = timeoutMs,
            powerSaveMode = powerSave,
            autoSavingEnabled = autoSavingEnabled,
            canWriteSettings = s.canWriteSettings,
            saverThresholdPercent = 30,
            wifiEnabled = wifiEnabled,
            wifiConnected = wifiConnected,
            locationEnabled = locationEnabled,
            nfcPresent = nfcAdapter != null,
            nfcEnabled = nfcEnabled,
            mobileDataReadable = mobileDataReadable,
            mobileDataEnabled = mobileDataEnabled,
            darkTheme = darkTheme,
            hapticEnabled = hapticEnabled,
            bluetoothOn = bluetoothOn,
            dataSaverReadable = dataSaverReadable,
            dataSaverRestricted = dataSaverRestricted,
        )
    }
}
