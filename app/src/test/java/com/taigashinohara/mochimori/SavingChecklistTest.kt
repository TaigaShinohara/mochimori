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

    // ---- Summary line ----

    @Test fun summaryAllOk_saysAll() {
        assertEquals("5つとも できてる", SavingChecklist.summary(SavingChecklist.evaluate(baseInput())))
    }

    @Test fun summaryMixed_countsOk() {
        val input = baseInput().copy(brightness = 255, autoSavingEnabled = false)
        assertEquals("5つ中3つ できてる", SavingChecklist.summary(SavingChecklist.evaluate(input)))
    }

    @Test fun summaryWithUnknown_mentionsChecking() {
        val input = baseInput().copy(brightness = -1, brightnessAuto = null)
        assertEquals(
            "4つ中4つ できてる（1つ確認中）",
            SavingChecklist.summary(SavingChecklist.evaluate(input)),
        )
    }

    // ---- Timeout formatting ----

    @Test fun formatTimeout_values() {
        assertEquals("15秒", SavingChecklist.formatTimeout(15_000))
        assertEquals("30秒", SavingChecklist.formatTimeout(30_000))
        assertEquals("2分", SavingChecklist.formatTimeout(120_000))
        assertEquals("1分30秒", SavingChecklist.formatTimeout(90_000))
    }

    @Test fun evaluate_returnsFiveItemsInOrder() {
        val items = SavingChecklist.evaluate(baseInput()).map { it.item }
        assertEquals(
            listOf(
                SavingChecklist.Item.BRIGHTNESS,
                SavingChecklist.Item.SCREEN_TIMEOUT,
                SavingChecklist.Item.TEMPERATURE,
                SavingChecklist.Item.POWER_SAVER,
                SavingChecklist.Item.AUTO_SAVING,
            ),
            items,
        )
    }
}
