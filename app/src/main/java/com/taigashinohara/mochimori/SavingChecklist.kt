package com.taigashinohara.mochimori

/**
 * Pure judgement for the home screen's saving checklist (v0.3.15,
 * expanded in v0.3.16, split into two tiers in v0.3.17).
 *
 * The checklist answers "節電できてるか" item by item, from measured
 * values only. Every criterion reuses an existing threshold so the
 * checklist can never contradict the face above it:
 *  - brightness: BatteryJudge.BRIGHTNESS_NEAR_MAX (230/255)
 *  - temperature: BatteryJudge.HOT_TEMP_C (40℃)
 *  - power saver need: the auto profile's stage threshold (30%)
 *
 * Honesty rules (DESIGN v0.3): a value the device does not report is
 * UNKNOWN and shown as 「確認中」 — never dressed up as OK. The power
 * saver is only NG when it is OFF *and* the level is at or below the
 * saver threshold; with charge to spare, OFF is fine and the value
 * text says so. The app's own auto-saving counts as UNKNOWN while
 * the WRITE_SETTINGS permission is missing, because the toggle alone
 * cannot make the profile run (same line as BatteryJudge's
 * NEED_PERMISSION).
 *
 * v0.3.16 additions (user request: more checks, and the score as a
 * percent with a donut chart). Every new row is judged from a value
 * the platform hands to any app with NO new permission (the manifest
 * still carries exactly the three permissions of v0.1):
 *  - Wi-Fi: WifiManager.isWifiEnabled plus the supplicant state,
 *    which survives the location-permission redaction of WifiInfo
 *    (only SSID/BSSID/networkId etc. are redacted). Connectivity
 *    Manager's network reads are NOT used for this: they require
 *    ACCESS_NETWORK_STATE, a permission this app refuses to add.
 *  - Mobile data: the "mobile_data" global setting, read directly.
 *    When the key cannot be read the row is omitted, never guessed.
 *  - Location: LocationManager.isLocationEnabled (API 28+) /
 *    Settings.Secure.LOCATION_MODE below that.
 *  - NFC: NfcAdapter state; the row exists only on devices that
 *    have NFC hardware.
 *  - Dark theme: the effective night mode in the configuration.
 *    The saving claim is an OLED premise, and the item name says so.
 *  - Haptic feedback: Settings.System.HAPTIC_FEEDBACK_ENABLED.
 * Not adopted: master auto-sync. ContentResolver's getter is
 * enforced behind READ_SYNC_SETTINGS in the system server, so it
 * cannot be read under the no-new-permissions rule.
 *
 * v0.3.17: the checklist splits into two tiers (user request:
 * 「通常の節電と、限界までやる節電でチェックリストを分けて」).
 * The yardstick for the split is whether reaching OK costs everyday
 * convenience:
 *  - NORMAL (always shown): brightness, screen timeout,
 *    temperature, power saver, auto saving, Wi-Fi, dark theme —
 *    states that are either free to keep (Wi-Fi connected, dark
 *    theme on an OLED) or already the app's own business.
 *  - LIMIT (shown only while the user's 「限界まで節電」 mode is
 *    ON): mobile data, location, NFC, Bluetooth, haptic feedback,
 *    data saver — radios and services whose OFF state is a real
 *    daily-life trade-off, so they are only asked for when the user
 *    has explicitly chosen to squeeze the battery to the limit.
 * Bluetooth moves with the split: on the normal side it could only
 * ever be an advice row (whether anything is connected cannot be
 * read without BLUETOOTH_CONNECT), but the limit tier's premise is
 * "sacrifice convenience", so there ON is judged NG outright and
 * the row scores like any other. The normal side's INFO row is
 * gone. Data saver joins the limit tier: ConnectivityManager
 * .getRestrictBackgroundStatus() carries no permission annotation
 * in the platform (verified against the SDK's annotation set —
 * methods that need ACCESS_NETWORK_STATE are annotated, this one
 * is not), and the caller still guards the read so a failure omits
 * the row instead of guessing. ENABLED and WHITELISTED both mean
 * the device's data saver is in force (WHITELISTED only exempts
 * this app), so both judge OK; DISABLED judges NG.
 * Each tier scores independently: percent() and summary() are
 * computed per list, with the same honest counting as before.
 */
object SavingChecklist {

    enum class Status { OK, NG, UNKNOWN, INFO }

    /** Which card a row belongs to (v0.3.17). */
    enum class Tier { NORMAL, LIMIT }

    enum class Item {
        BRIGHTNESS, SCREEN_TIMEOUT, TEMPERATURE, POWER_SAVER, AUTO_SAVING,
        WIFI, MOBILE_DATA, LOCATION, NFC, BLUETOOTH, DARK_THEME, HAPTIC_FEEDBACK,
        DATA_SAVER,
    }

    /** Screen-off timeout at or below this counts as saving (30s). */
    const val TIMEOUT_OK_MS = 30_000

    data class Input(
        val levelPercent: Int,
        /** False when the battery level could not be read. */
        val levelKnown: Boolean,
        val charging: Boolean,
        val tempC: Float,
        /** False when the temperature extra was absent. */
        val tempKnown: Boolean,
        /** 0..255, -1 when unknown. */
        val brightness: Int,
        /** True in automatic brightness mode; null when the mode
         *  setting itself could not be read. */
        val brightnessAuto: Boolean?,
        /** SCREEN_OFF_TIMEOUT in ms; -1 when unknown,
         *  Int.MAX_VALUE when the screen is set to never turn off. */
        val screenOffTimeoutMs: Int,
        /** PowerManager.isPowerSaveMode; null when unreadable. */
        val powerSaveMode: Boolean?,
        /** The rules screen's auto-saving toggle. */
        val autoSavingEnabled: Boolean,
        /** True when WRITE_SETTINGS has been granted. */
        val canWriteSettings: Boolean,
        val saverThresholdPercent: Int = 30,
        // ---- v0.3.16 additions (defaults keep older callers honest:
        // ---- unread values, absent hardware, no advice row) ----
        /** WifiManager.isWifiEnabled; null when unreadable. */
        val wifiEnabled: Boolean? = null,
        /** True when the supplicant reports a completed association;
         *  false when it positively reports no association; null
         *  when mid-transition or unreadable. Only consulted when
         *  wifiEnabled is true. */
        val wifiConnected: Boolean? = null,
        /** Location services on/off; null when unreadable. */
        val locationEnabled: Boolean? = null,
        /** True when the device has NFC hardware. The NFC row is
         *  omitted entirely without it — nothing to save there. */
        val nfcPresent: Boolean = false,
        /** NfcAdapter.isEnabled; null when unreadable. */
        val nfcEnabled: Boolean? = null,
        /** True when the "mobile_data" global setting could be
         *  read. The row is omitted when it cannot — never guessed. */
        val mobileDataReadable: Boolean = false,
        /** Mobile data on/off; only consulted when readable. */
        val mobileDataEnabled: Boolean? = null,
        /** Effective dark theme (night mode) in the configuration. */
        val darkTheme: Boolean = false,
        /** HAPTIC_FEEDBACK_ENABLED; null when the setting is absent. */
        val hapticEnabled: Boolean? = null,
        /** BluetoothAdapter.isEnabled; null when there is no
         *  adapter or the state cannot be read. On the limit tier
         *  (v0.3.17) this is judged like any other radio: ON is NG. */
        val bluetoothOn: Boolean? = null,
        // ---- v0.3.17 addition ----
        /** True when getRestrictBackgroundStatus() returned a
         *  value. The data saver row is omitted when it cannot be
         *  read — never guessed. */
        val dataSaverReadable: Boolean = false,
        /** True when the device's data saver is in force for this
         *  app (status ENABLED or WHITELISTED); false when DISABLED.
         *  Only consulted when dataSaverReadable is true. */
        val dataSaverRestricted: Boolean? = null,
    )

    data class Entry(
        val item: Item,
        val name: String,
        val status: Status,
        /** The measured value in one short line ("200/255", "30秒"…). */
        val valueText: String,
        /** False for rows that are shown but never enter the
         *  percent or the summary counts. Since v0.3.17 no row uses
         *  this (Bluetooth scores on the limit tier); the mechanism
         *  stays for percent()/summary() honesty tests. */
        val countsTowardScore: Boolean = true,
        /** Optional second line under the row. */
        val note: String? = null,
        /** The card this row belongs to (v0.3.17). */
        val tier: Tier = Tier.NORMAL,
    )

    /** The normal tier: always shown (7 items). */
    fun evaluateNormal(input: Input): List<Entry> = listOfNotNull(
        brightnessEntry(input),
        timeoutEntry(input),
        temperatureEntry(input),
        powerSaverEntry(input),
        autoSavingEntry(input),
        wifiEntry(input),
        darkThemeEntry(input),
    )

    /**
     * The limit tier: shown only while 「限界まで節電」 mode is ON.
     * Every row here costs everyday convenience when fixed, which
     * is exactly why the tier is opt-in.
     */
    fun evaluateLimit(input: Input): List<Entry> = listOfNotNull(
        if (input.mobileDataReadable) mobileDataEntry(input) else null,
        locationEntry(input),
        if (input.nfcPresent) nfcEntry(input) else null,
        bluetoothEntry(input),
        hapticEntry(input),
        if (input.dataSaverReadable) dataSaverEntry(input) else null,
    )

    /** Both tiers, normal first. Kept for callers that want the
     *  whole picture; the home screen renders the tiers separately. */
    fun evaluate(input: Input): List<Entry> =
        evaluateNormal(input) + evaluateLimit(input)

    /** The scored rows: everything except non-scoring rows. */
    private fun scored(entries: List<Entry>): List<Entry> =
        entries.filter { it.countsTowardScore }

    /**
     * Percent of judged items that are OK, for the donut chart.
     * UNKNOWN rows are excluded from the denominator (the honest
     * counting the summary line has always used), and non-scoring
     * rows never enter it. Null when nothing could be judged at
     * all, so the UI can show a dash instead of a fake number.
     * Computed per tier by passing that tier's list.
     */
    fun percent(entries: List<Entry>): Int? {
        val judged = scored(entries).filter { it.status != Status.UNKNOWN }
        if (judged.isEmpty()) return null
        val ok = judged.count { it.status == Status.OK }
        return Math.round(ok * 100.0 / judged.size).toInt()
    }

    /** One-line summary for a card head, e.g. "5つ中4つ できてる". */
    fun summary(entries: List<Entry>): String {
        val scored = scored(entries)
        val ok = scored.count { it.status == Status.OK }
        val unknown = scored.count { it.status == Status.UNKNOWN }
        val total = scored.size
        return when {
            ok == total -> "${total}つとも できてる"
            unknown > 0 -> "${total - unknown}つ中${ok}つ できてる（${unknown}つ確認中）"
            else -> "${total}つ中${ok}つ できてる"
        }
    }

    private fun brightnessEntry(input: Input): Entry {
        val name = "画面の明るさ"
        return when {
            input.brightnessAuto == true ->
                Entry(Item.BRIGHTNESS, name, Status.OK, "自動")
            input.brightness < 0 ->
                Entry(Item.BRIGHTNESS, name, Status.UNKNOWN, "確認中")
            input.brightness >= BatteryJudge.BRIGHTNESS_NEAR_MAX ->
                Entry(Item.BRIGHTNESS, name, Status.NG, "${input.brightness}/255")
            else ->
                Entry(Item.BRIGHTNESS, name, Status.OK, "${input.brightness}/255")
        }
    }

    private fun timeoutEntry(input: Input): Entry {
        val name = "画面が消えるまでの時間"
        val ms = input.screenOffTimeoutMs
        return when {
            ms < 0 -> Entry(Item.SCREEN_TIMEOUT, name, Status.UNKNOWN, "確認中")
            ms == Int.MAX_VALUE -> Entry(Item.SCREEN_TIMEOUT, name, Status.NG, "消えない設定")
            ms <= TIMEOUT_OK_MS -> Entry(Item.SCREEN_TIMEOUT, name, Status.OK, formatTimeout(ms))
            else -> Entry(Item.SCREEN_TIMEOUT, name, Status.NG, formatTimeout(ms))
        }
    }

    private fun temperatureEntry(input: Input): Entry {
        val name = "バッテリーの温度"
        return when {
            !input.tempKnown ->
                Entry(Item.TEMPERATURE, name, Status.UNKNOWN, "確認中")
            input.tempC >= BatteryJudge.HOT_TEMP_C ->
                Entry(Item.TEMPERATURE, name, Status.NG, "${formatTemp(input.tempC)}℃")
            else ->
                Entry(Item.TEMPERATURE, name, Status.OK, "${formatTemp(input.tempC)}℃")
        }
    }

    private fun powerSaverEntry(input: Input): Entry {
        val name = "省電力モード"
        return when {
            input.powerSaveMode == null ->
                Entry(Item.POWER_SAVER, name, Status.UNKNOWN, "確認中")
            input.powerSaveMode ->
                Entry(Item.POWER_SAVER, name, Status.OK, "ON")
            input.charging ->
                // The system turns the saver off while charging; that
                // is the expected state, not a waste.
                Entry(Item.POWER_SAVER, name, Status.OK, "充電中")
            !input.levelKnown ->
                Entry(Item.POWER_SAVER, name, Status.UNKNOWN, "確認中")
            input.levelPercent <= input.saverThresholdPercent ->
                Entry(Item.POWER_SAVER, name, Status.NG, "OFF")
            else ->
                Entry(Item.POWER_SAVER, name, Status.OK, "OFF（今は大丈夫）")
        }
    }

    private fun autoSavingEntry(input: Input): Entry {
        val name = "自動節電（このアプリのルール）"
        return when {
            !input.autoSavingEnabled ->
                Entry(Item.AUTO_SAVING, name, Status.NG, "OFF")
            !input.canWriteSettings ->
                Entry(Item.AUTO_SAVING, name, Status.UNKNOWN, "許可が必要")
            else ->
                Entry(Item.AUTO_SAVING, name, Status.OK, "ON")
        }
    }

    private fun wifiEntry(input: Input): Entry {
        val name = "Wi-Fi"
        return when {
            input.wifiEnabled == null ->
                Entry(Item.WIFI, name, Status.UNKNOWN, "確認中")
            !input.wifiEnabled ->
                Entry(Item.WIFI, name, Status.OK, "OFF")
            input.wifiConnected == true ->
                Entry(Item.WIFI, name, Status.OK, "接続中")
            input.wifiConnected == false ->
                // On but associated with nothing: the radio is awake
                // and scanning for no benefit.
                Entry(Item.WIFI, name, Status.NG, "ON（未接続）")
            else ->
                // Mid-transition (associating, scanning…): the
                // supplicant has not settled, so no verdict.
                Entry(Item.WIFI, name, Status.UNKNOWN, "確認中")
        }
    }

    private fun mobileDataEntry(input: Input): Entry {
        val name = "モバイルデータ"
        return when {
            input.mobileDataEnabled == null ->
                Entry(Item.MOBILE_DATA, name, Status.UNKNOWN, "確認中", tier = Tier.LIMIT)
            !input.mobileDataEnabled ->
                Entry(Item.MOBILE_DATA, name, Status.OK, "OFF", tier = Tier.LIMIT)
            input.wifiConnected == true ->
                // On, but traffic rides Wi-Fi; the cellular radio is
                // not the path in use, so this is not called a waste.
                Entry(Item.MOBILE_DATA, name, Status.OK, "Wi-Fi接続中", tier = Tier.LIMIT)
            else ->
                Entry(Item.MOBILE_DATA, name, Status.NG, "ON", tier = Tier.LIMIT)
        }
    }

    private fun locationEntry(input: Input): Entry {
        val name = "位置情報"
        return when {
            input.locationEnabled == null ->
                Entry(Item.LOCATION, name, Status.UNKNOWN, "確認中", tier = Tier.LIMIT)
            input.locationEnabled ->
                Entry(Item.LOCATION, name, Status.NG, "ON", tier = Tier.LIMIT)
            else ->
                Entry(Item.LOCATION, name, Status.OK, "OFF", tier = Tier.LIMIT)
        }
    }

    private fun nfcEntry(input: Input): Entry {
        val name = "NFC"
        return when {
            input.nfcEnabled == null ->
                Entry(Item.NFC, name, Status.UNKNOWN, "確認中", tier = Tier.LIMIT)
            input.nfcEnabled ->
                Entry(Item.NFC, name, Status.NG, "ON", tier = Tier.LIMIT)
            else ->
                Entry(Item.NFC, name, Status.OK, "OFF", tier = Tier.LIMIT)
        }
    }

    /**
     * Bluetooth on the limit tier (v0.3.17): the tier's premise is
     * squeezing the battery at the cost of convenience, so ON is a
     * plain NG and the row scores — unlike the normal tier's old
     * advice row, which never judged because connected-or-not
     * cannot be read without BLUETOOTH_CONNECT. OFF is OK; an
     * unreadable state stays UNKNOWN rather than disappearing, so
     * the tier's denominator stays honest.
     */
    private fun bluetoothEntry(input: Input): Entry {
        val name = "Bluetooth"
        return when (input.bluetoothOn) {
            null -> Entry(Item.BLUETOOTH, name, Status.UNKNOWN, "確認中", tier = Tier.LIMIT)
            true -> Entry(Item.BLUETOOTH, name, Status.NG, "ON", tier = Tier.LIMIT)
            false -> Entry(Item.BLUETOOTH, name, Status.OK, "OFF", tier = Tier.LIMIT)
        }
    }

    private fun darkThemeEntry(input: Input): Entry {
        // The saving premise is OLED (dark pixels draw ~nothing);
        // the premise rides in the item name so the verdict never
        // overclaims on an LCD device.
        val name = "ダークテーマ（有機EL）"
        return if (input.darkTheme) {
            Entry(Item.DARK_THEME, name, Status.OK, "ON")
        } else {
            Entry(Item.DARK_THEME, name, Status.NG, "OFF")
        }
    }

    private fun hapticEntry(input: Input): Entry {
        val name = "タッチ時のバイブ"
        return when {
            input.hapticEnabled == null ->
                Entry(Item.HAPTIC_FEEDBACK, name, Status.UNKNOWN, "確認中", tier = Tier.LIMIT)
            input.hapticEnabled ->
                Entry(Item.HAPTIC_FEEDBACK, name, Status.NG, "ON", tier = Tier.LIMIT)
            else ->
                Entry(Item.HAPTIC_FEEDBACK, name, Status.OK, "OFF", tier = Tier.LIMIT)
        }
    }

    /**
     * Data saver (v0.3.17, limit tier): the platform reports the
     * restriction state for this app — ENABLED (saver on, this app
     * restricted) and WHITELISTED (saver on, this app exempted)
     * both mean the device's data saver is in force, so both are
     * OK; DISABLED means the saver is off, which on this tier is
     * NG. The row exists only when the status could be read.
     */
    private fun dataSaverEntry(input: Input): Entry {
        val name = "データセーバー"
        return when (input.dataSaverRestricted) {
            null -> Entry(Item.DATA_SAVER, name, Status.UNKNOWN, "確認中", tier = Tier.LIMIT)
            true -> Entry(Item.DATA_SAVER, name, Status.OK, "ON", tier = Tier.LIMIT)
            false -> Entry(Item.DATA_SAVER, name, Status.NG, "OFF", tier = Tier.LIMIT)
        }
    }

    /** "15秒" / "2分" / "1分30秒" for a timeout in milliseconds. */
    fun formatTimeout(ms: Int): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val rest = seconds % 60
        return when {
            minutes <= 0 -> "${seconds}秒"
            rest == 0 -> "${minutes}分"
            else -> "${minutes}分${rest}秒"
        }
    }

    private fun formatTemp(t: Float): String =
        if (t % 1f == 0f) t.toInt().toString() else String.format("%.1f", t)
}
