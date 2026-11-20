package com.taigashinohara.mochimori

/**
 * Pure judgement for the home screen's saving checklist (v0.3.15).
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
 */
object SavingChecklist {

    enum class Status { OK, NG, UNKNOWN }

    enum class Item { BRIGHTNESS, SCREEN_TIMEOUT, TEMPERATURE, POWER_SAVER, AUTO_SAVING }

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
    )

    data class Entry(
        val item: Item,
        val name: String,
        val status: Status,
        /** The measured value in one short line ("200/255", "30秒"…). */
        val valueText: String,
    )

    fun evaluate(input: Input): List<Entry> = listOf(
        brightnessEntry(input),
        timeoutEntry(input),
        temperatureEntry(input),
        powerSaverEntry(input),
        autoSavingEntry(input),
    )

    /** One-line summary for the card head, e.g. "5つ中4つ できてる". */
    fun summary(entries: List<Entry>): String {
        val ok = entries.count { it.status == Status.OK }
        val unknown = entries.count { it.status == Status.UNKNOWN }
        val total = entries.size
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
