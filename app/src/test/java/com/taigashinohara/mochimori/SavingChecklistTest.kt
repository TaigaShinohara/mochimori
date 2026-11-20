package com.taigashinohara.mochimori

import org.junit.Assert.assertEquals
import org.junit.Test

class SavingChecklistTest {

    /** A baseline where everything is honestly fine. */
    private fun baseInput() = SavingChecklist.Input(
        levelPercent = 80,
        levelKnown = true,
        charging = false,
        tempC = 32f,
        tempKnown = true,
        brightness = 100,
        brightnessAuto = false,
        screenOffTimeoutMs = 30_000,
        powerSaveMode = false,
        autoSavingEnabled = true,
        canWriteSettings = true,
        // v0.3.16 rows, all in their fine state: radios off, dark
        // theme on, haptics off. NFC hardware absent and the mobile
        // data value unreadable, so those two rows are omitted.
        // Since v0.3.17 Bluetooth off is a scored OK row on the
        // limit tier, and the data saver value is unreadable here,
        // so that row is omitted too.
        wifiEnabled = false,
        wifiConnected = null,
        locationEnabled = false,
        nfcPresent = false,
        nfcEnabled = null,
        mobileDataReadable = false,
        mobileDataEnabled = null,
        darkTheme = true,
        hapticEnabled = false,
        bluetoothOn = false,
    )

    private fun entry(
        input: SavingChecklist.Input,
        item: SavingChecklist.Item,
    ): SavingChecklist.Entry = SavingChecklist.evaluate(input).first { it.item == item }

    // ---- Brightness (threshold shared with BatteryJudge: 230) ----

    @Test fun brightnessNearMax_isNg() {
        val e = entry(baseInput().copy(brightness = 255), SavingChecklist.Item.BRIGHTNESS)
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("255/255", e.valueText)
    }

    @Test fun brightnessAtJudgeThreshold_isNg() {
        val e = entry(
            baseInput().copy(brightness = BatteryJudge.BRIGHTNESS_NEAR_MAX),
            SavingChecklist.Item.BRIGHTNESS,
        )
        assertEquals(SavingChecklist.Status.NG, e.status)
    }

    @Test fun brightnessBelowThreshold_isOk() {
        val e = entry(
            baseInput().copy(brightness = BatteryJudge.BRIGHTNESS_NEAR_MAX - 1),
            SavingChecklist.Item.BRIGHTNESS,
        )
        assertEquals(SavingChecklist.Status.OK, e.status)
    }

    @Test fun brightnessAuto_isOk() {
        val e = entry(
            baseInput().copy(brightness = 255, brightnessAuto = true),
            SavingChecklist.Item.BRIGHTNESS,
        )
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("自動", e.valueText)
    }

    @Test fun brightnessUnknown_isUnknownNotOk() {
        val e = entry(
            baseInput().copy(brightness = -1, brightnessAuto = null),
            SavingChecklist.Item.BRIGHTNESS,
        )
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
        assertEquals("確認中", e.valueText)
    }

    // ---- Screen-off timeout (OK at 30s and below) ----

    @Test fun timeout30s_isOk() {
        val e = entry(baseInput(), SavingChecklist.Item.SCREEN_TIMEOUT)
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("30秒", e.valueText)
    }

    @Test fun timeout15s_isOk() {
        val e = entry(
            baseInput().copy(screenOffTimeoutMs = 15_000),
            SavingChecklist.Item.SCREEN_TIMEOUT,
        )
        assertEquals(SavingChecklist.Status.OK, e.status)
    }

    @Test fun timeout1min_isNg() {
        val e = entry(
            baseInput().copy(screenOffTimeoutMs = 60_000),
            SavingChecklist.Item.SCREEN_TIMEOUT,
        )
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("1分", e.valueText)
    }

    @Test fun timeoutNever_isNg() {
        val e = entry(
            baseInput().copy(screenOffTimeoutMs = Int.MAX_VALUE),
            SavingChecklist.Item.SCREEN_TIMEOUT,
        )
        assertEquals(SavingChecklist.Status.NG, e.status)
    }

    @Test fun timeoutUnknown_isUnknown() {
        val e = entry(
            baseInput().copy(screenOffTimeoutMs = -1),
            SavingChecklist.Item.SCREEN_TIMEOUT,
        )
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    // ---- Temperature (threshold shared with BatteryJudge: 40C) ----

    @Test fun temperatureAtHeatThreshold_isNg() {
        val e = entry(
            baseInput().copy(tempC = BatteryJudge.HOT_TEMP_C),
            SavingChecklist.Item.TEMPERATURE,
        )
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("40℃", e.valueText)
    }

    @Test fun temperatureBelowThreshold_isOk() {
        val e = entry(baseInput().copy(tempC = 39.9f), SavingChecklist.Item.TEMPERATURE)
        assertEquals(SavingChecklist.Status.OK, e.status)
    }

    @Test fun temperatureUnknown_isUnknown() {
        val e = entry(
            baseInput().copy(tempKnown = false),
            SavingChecklist.Item.TEMPERATURE,
        )
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    // ---- Power saver (NG only when OFF and the level is low) ----

    @Test fun powerSaverOn_isOk() {
        val e = entry(baseInput().copy(powerSaveMode = true), SavingChecklist.Item.POWER_SAVER)
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("ON", e.valueText)
    }

    @Test fun powerSaverOffWithChargeToSpare_isOkNeutral() {
        val e = entry(baseInput(), SavingChecklist.Item.POWER_SAVER)
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("OFF（今は大丈夫）", e.valueText)
    }

    @Test fun powerSaverOffAtThreshold_isNg() {
        val e = entry(baseInput().copy(levelPercent = 30), SavingChecklist.Item.POWER_SAVER)
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("OFF", e.valueText)
    }

    @Test fun powerSaverOffJustAboveThreshold_isOk() {
        val e = entry(baseInput().copy(levelPercent = 31), SavingChecklist.Item.POWER_SAVER)
        assertEquals(SavingChecklist.Status.OK, e.status)
    }

    @Test fun powerSaverOffWhileCharging_isOk() {
        val e = entry(
            baseInput().copy(levelPercent = 10, charging = true),
            SavingChecklist.Item.POWER_SAVER,
        )
        assertEquals(SavingChecklist.Status.OK, e.status)
    }

    @Test fun powerSaverOffWithUnknownLevel_isUnknown() {
        val e = entry(
            baseInput().copy(levelKnown = false),
            SavingChecklist.Item.POWER_SAVER,
        )
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    @Test fun powerSaverUnreadable_isUnknown() {
        val e = entry(baseInput().copy(powerSaveMode = null), SavingChecklist.Item.POWER_SAVER)
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    // ---- Auto saving (this app's own rule) ----

    @Test fun autoSavingOn_isOk() {
        val e = entry(baseInput(), SavingChecklist.Item.AUTO_SAVING)
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("ON", e.valueText)
    }

    @Test fun autoSavingOff_isNg() {
        val e = entry(
            baseInput().copy(autoSavingEnabled = false),
            SavingChecklist.Item.AUTO_SAVING,
        )
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("OFF", e.valueText)
    }

    @Test fun autoSavingOnWithoutPermission_isUnknown() {
        val e = entry(
            baseInput().copy(canWriteSettings = false),
            SavingChecklist.Item.AUTO_SAVING,
        )
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
        assertEquals("許可が必要", e.valueText)
    }

    // ---- Wi-Fi (v0.3.16: NG only when on and unassociated) ----

    @Test fun wifiOff_isOk() {
        val e = entry(baseInput(), SavingChecklist.Item.WIFI)
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("OFF", e.valueText)
    }

    @Test fun wifiOnConnected_isOk() {
        val e = entry(
            baseInput().copy(wifiEnabled = true, wifiConnected = true),
            SavingChecklist.Item.WIFI,
        )
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("接続中", e.valueText)
    }

    @Test fun wifiOnUnconnected_isNg() {
        val e = entry(
            baseInput().copy(wifiEnabled = true, wifiConnected = false),
            SavingChecklist.Item.WIFI,
        )
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("ON（未接続）", e.valueText)
    }

    @Test fun wifiOnTransition_isUnknownNotNg() {
        val e = entry(
            baseInput().copy(wifiEnabled = true, wifiConnected = null),
            SavingChecklist.Item.WIFI,
        )
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    @Test fun wifiUnreadable_isUnknown() {
        val e = entry(baseInput().copy(wifiEnabled = null), SavingChecklist.Item.WIFI)
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    // ---- Mobile data (row exists only when the value was readable) ----

    @Test fun mobileDataUnreadable_omitsRow() {
        val items = SavingChecklist.evaluate(baseInput()).map { it.item }
        assertEquals(false, items.contains(SavingChecklist.Item.MOBILE_DATA))
    }

    @Test fun mobileDataOff_isOk() {
        val e = entry(
            baseInput().copy(mobileDataReadable = true, mobileDataEnabled = false),
            SavingChecklist.Item.MOBILE_DATA,
        )
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("OFF", e.valueText)
    }

    @Test fun mobileDataOn_isNg() {
        val e = entry(
            baseInput().copy(mobileDataReadable = true, mobileDataEnabled = true),
            SavingChecklist.Item.MOBILE_DATA,
        )
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("ON", e.valueText)
    }

    @Test fun mobileDataOnWhileWifiConnected_isOkNeutral() {
        val e = entry(
            baseInput().copy(
                mobileDataReadable = true, mobileDataEnabled = true,
                wifiEnabled = true, wifiConnected = true,
            ),
            SavingChecklist.Item.MOBILE_DATA,
        )
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("Wi-Fi接続中", e.valueText)
    }

    // ---- Location ----

    @Test fun locationOn_isNg() {
        val e = entry(baseInput().copy(locationEnabled = true), SavingChecklist.Item.LOCATION)
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("ON", e.valueText)
    }

    @Test fun locationOff_isOk() {
        val e = entry(baseInput(), SavingChecklist.Item.LOCATION)
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("OFF", e.valueText)
    }

    @Test fun locationUnreadable_isUnknown() {
        val e = entry(baseInput().copy(locationEnabled = null), SavingChecklist.Item.LOCATION)
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    // ---- NFC (row exists only on devices with the hardware) ----

    @Test fun nfcAbsent_omitsRow() {
        val items = SavingChecklist.evaluate(baseInput()).map { it.item }
        assertEquals(false, items.contains(SavingChecklist.Item.NFC))
    }

    @Test fun nfcOn_isNg() {
        val e = entry(
            baseInput().copy(nfcPresent = true, nfcEnabled = true),
            SavingChecklist.Item.NFC,
        )
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("ON", e.valueText)
    }

    @Test fun nfcOff_isOk() {
        val e = entry(
            baseInput().copy(nfcPresent = true, nfcEnabled = false),
            SavingChecklist.Item.NFC,
        )
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("OFF", e.valueText)
    }

    @Test fun nfcPresentUnreadable_isUnknown() {
        val e = entry(
            baseInput().copy(nfcPresent = true, nfcEnabled = null),
            SavingChecklist.Item.NFC,
        )
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    // ---- Bluetooth (v0.3.17: judged on the limit tier) ----

    @Test fun bluetoothOff_isOkOnLimitTier() {
        val e = entry(baseInput(), SavingChecklist.Item.BLUETOOTH)
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("OFF", e.valueText)
        assertEquals(SavingChecklist.Tier.LIMIT, e.tier)
    }

    @Test fun bluetoothOn_isNgAndScoresOnLimitTier() {
        val e = entry(baseInput().copy(bluetoothOn = true), SavingChecklist.Item.BLUETOOTH)
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("ON", e.valueText)
        assertEquals(true, e.countsTowardScore)
        assertEquals(SavingChecklist.Tier.LIMIT, e.tier)
    }

    @Test fun bluetoothUnreadable_isUnknownNotDropped() {
        val e = entry(baseInput().copy(bluetoothOn = null), SavingChecklist.Item.BLUETOOTH)
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
        assertEquals("確認中", e.valueText)
    }

    // ---- Data saver (v0.3.17, limit tier; row only when read) ----

    @Test fun dataSaverUnreadable_omitsRow() {
        val items = SavingChecklist.evaluate(baseInput()).map { it.item }
        assertEquals(false, items.contains(SavingChecklist.Item.DATA_SAVER))
    }

    @Test fun dataSaverRestricted_isOk() {
        val e = entry(
            baseInput().copy(dataSaverReadable = true, dataSaverRestricted = true),
            SavingChecklist.Item.DATA_SAVER,
        )
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("ON", e.valueText)
        assertEquals(SavingChecklist.Tier.LIMIT, e.tier)
    }

    @Test fun dataSaverOff_isNg() {
        val e = entry(
            baseInput().copy(dataSaverReadable = true, dataSaverRestricted = false),
            SavingChecklist.Item.DATA_SAVER,
        )
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("OFF", e.valueText)
    }

    @Test fun dataSaverReadableButNoValue_isUnknown() {
        val e = entry(
            baseInput().copy(dataSaverReadable = true, dataSaverRestricted = null),
            SavingChecklist.Item.DATA_SAVER,
        )
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    // ---- Tiers (v0.3.17: normal / limit split) ----

    @Test fun evaluateNormal_returnsTheSevenNormalItems() {
        val entries = SavingChecklist.evaluateNormal(baseInput())
        assertEquals(
            listOf(
                SavingChecklist.Item.BRIGHTNESS,
                SavingChecklist.Item.SCREEN_TIMEOUT,
                SavingChecklist.Item.TEMPERATURE,
                SavingChecklist.Item.POWER_SAVER,
                SavingChecklist.Item.AUTO_SAVING,
                SavingChecklist.Item.WIFI,
                SavingChecklist.Item.DARK_THEME,
            ),
            entries.map { it.item },
        )
        assertEquals(
            true,
            entries.all { it.tier == SavingChecklist.Tier.NORMAL },
        )
    }

    @Test fun evaluateLimit_returnsLimitItemsWhenAllReadable() {
        val input = baseInput().copy(
            mobileDataReadable = true, mobileDataEnabled = false,
            nfcPresent = true, nfcEnabled = false,
            dataSaverReadable = true, dataSaverRestricted = true,
        )
        val entries = SavingChecklist.evaluateLimit(input)
        assertEquals(
            listOf(
                SavingChecklist.Item.MOBILE_DATA,
                SavingChecklist.Item.LOCATION,
                SavingChecklist.Item.NFC,
                SavingChecklist.Item.BLUETOOTH,
                SavingChecklist.Item.HAPTIC_FEEDBACK,
                SavingChecklist.Item.DATA_SAVER,
            ),
            entries.map { it.item },
        )
        assertEquals(
            true,
            entries.all { it.tier == SavingChecklist.Tier.LIMIT },
        )
    }

    @Test fun tiersDoNotOverlap() {
        val input = baseInput().copy(
            mobileDataReadable = true, mobileDataEnabled = true,
            nfcPresent = true, nfcEnabled = true,
            dataSaverReadable = true, dataSaverRestricted = false,
        )
        val normal = SavingChecklist.evaluateNormal(input).map { it.item }.toSet()
        val limit = SavingChecklist.evaluateLimit(input).map { it.item }.toSet()
        assertEquals(true, normal.intersect(limit).isEmpty())
        // evaluate() is exactly the two tiers concatenated.
        assertEquals(
            SavingChecklist.evaluateNormal(input) + SavingChecklist.evaluateLimit(input),
            SavingChecklist.evaluate(input),
        )
    }

    @Test fun percentIsComputedPerTier() {
        // Normal tier all fine; limit tier has two NGs out of three.
        val input = baseInput().copy(locationEnabled = true, hapticEnabled = true)
        assertEquals(100, SavingChecklist.percent(SavingChecklist.evaluateNormal(input)))
        assertEquals(33, SavingChecklist.percent(SavingChecklist.evaluateLimit(input)))
        assertEquals(
            "7つとも できてる",
            SavingChecklist.summary(SavingChecklist.evaluateNormal(input)),
        )
        assertEquals(
            "3つ中1つ できてる",
            SavingChecklist.summary(SavingChecklist.evaluateLimit(input)),
        )
    }

    // ---- Dark theme (OLED premise rides in the item name) ----

    @Test fun darkThemeOn_isOk() {
        val e = entry(baseInput(), SavingChecklist.Item.DARK_THEME)
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("ダークテーマ（有機EL）", e.name)
        assertEquals("ON", e.valueText)
    }

    @Test fun darkThemeOff_isNg() {
        val e = entry(baseInput().copy(darkTheme = false), SavingChecklist.Item.DARK_THEME)
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("OFF", e.valueText)
    }

    // ---- Haptic feedback ----

    @Test fun hapticOn_isNg() {
        val e = entry(baseInput().copy(hapticEnabled = true), SavingChecklist.Item.HAPTIC_FEEDBACK)
        assertEquals(SavingChecklist.Status.NG, e.status)
        assertEquals("ON", e.valueText)
    }

    @Test fun hapticOff_isOk() {
        val e = entry(baseInput(), SavingChecklist.Item.HAPTIC_FEEDBACK)
        assertEquals(SavingChecklist.Status.OK, e.status)
        assertEquals("OFF", e.valueText)
    }

    @Test fun hapticAbsent_isUnknown() {
        val e = entry(baseInput().copy(hapticEnabled = null), SavingChecklist.Item.HAPTIC_FEEDBACK)
        assertEquals(SavingChecklist.Status.UNKNOWN, e.status)
    }

    // ---- Percent (donut score: judged rows only) ----

    @Test fun percentAllOk_is100() {
        assertEquals(100, SavingChecklist.percent(SavingChecklist.evaluate(baseInput())))
    }

    @Test fun percentHalf_is50() {
        // Hand-built: one OK and one NG among the judged rows.
        val entries = listOf(
            SavingChecklist.Entry(
                SavingChecklist.Item.WIFI, "Wi-Fi",
                SavingChecklist.Status.OK, "OFF",
            ),
            SavingChecklist.Entry(
                SavingChecklist.Item.LOCATION, "位置情報",
                SavingChecklist.Status.NG, "ON",
            ),
        )
        assertEquals(50, SavingChecklist.percent(entries))
    }

    @Test fun percentExcludesUnknownAndInfo() {
        val entries = listOf(
            SavingChecklist.Entry(
                SavingChecklist.Item.WIFI, "Wi-Fi",
                SavingChecklist.Status.OK, "OFF",
            ),
            SavingChecklist.Entry(
                SavingChecklist.Item.LOCATION, "位置情報",
                SavingChecklist.Status.UNKNOWN, "確認中",
            ),
            SavingChecklist.Entry(
                SavingChecklist.Item.BLUETOOTH, "Bluetooth",
                SavingChecklist.Status.INFO, "ON", countsTowardScore = false,
            ),
        )
        assertEquals(100, SavingChecklist.percent(entries))
    }

    @Test fun percentNothingJudged_isNull() {
        val entries = listOf(
            SavingChecklist.Entry(
                SavingChecklist.Item.WIFI, "Wi-Fi",
                SavingChecklist.Status.UNKNOWN, "確認中",
            ),
        )
        assertEquals(null, SavingChecklist.percent(entries))
    }

    // ---- Summary line ----

    @Test fun summaryAllOk_saysAll() {
        // 7 normal + 3 limit rows are judged in the baseline
        // (location, Bluetooth, haptics); the unreadable rows
        // (mobile data, NFC, data saver) are omitted, not counted.
        assertEquals("10つとも できてる", SavingChecklist.summary(SavingChecklist.evaluate(baseInput())))
    }

    @Test fun summaryMixed_countsOk() {
        val input = baseInput().copy(brightness = 255, autoSavingEnabled = false)
        assertEquals("10つ中8つ できてる", SavingChecklist.summary(SavingChecklist.evaluate(input)))
    }

    @Test fun summaryWithUnknown_mentionsChecking() {
        val input = baseInput().copy(brightness = -1, brightnessAuto = null)
        assertEquals(
            "9つ中9つ できてる（1つ確認中）",
            SavingChecklist.summary(SavingChecklist.evaluate(input)),
        )
    }

    @Test fun summaryIgnoresNonScoringRow() {
        // The non-scoring mechanism itself stays honest even though
        // no current row uses it (Bluetooth scored until v0.3.16 as
        // INFO; since v0.3.17 it scores on the limit tier).
        val entries = SavingChecklist.evaluate(baseInput()) + SavingChecklist.Entry(
            SavingChecklist.Item.BLUETOOTH, "Bluetooth",
            SavingChecklist.Status.INFO, "ON", countsTowardScore = false,
        )
        assertEquals("10つとも できてる", SavingChecklist.summary(entries))
    }

    // ---- Timeout formatting ----

    @Test fun formatTimeout_values() {
        assertEquals("15秒", SavingChecklist.formatTimeout(15_000))
        assertEquals("30秒", SavingChecklist.formatTimeout(30_000))
        assertEquals("2分", SavingChecklist.formatTimeout(120_000))
        assertEquals("1分30秒", SavingChecklist.formatTimeout(90_000))
    }

    @Test fun evaluate_returnsScoredItemsInOrder() {
        // Normal tier first (7), then the limit tier's judged rows
        // for the baseline (location, Bluetooth, haptics).
        val items = SavingChecklist.evaluate(baseInput()).map { it.item }
        assertEquals(
            listOf(
                SavingChecklist.Item.BRIGHTNESS,
                SavingChecklist.Item.SCREEN_TIMEOUT,
                SavingChecklist.Item.TEMPERATURE,
                SavingChecklist.Item.POWER_SAVER,
                SavingChecklist.Item.AUTO_SAVING,
                SavingChecklist.Item.WIFI,
                SavingChecklist.Item.DARK_THEME,
                SavingChecklist.Item.LOCATION,
                SavingChecklist.Item.BLUETOOTH,
                SavingChecklist.Item.HAPTIC_FEEDBACK,
            ),
            items,
        )
    }
}
