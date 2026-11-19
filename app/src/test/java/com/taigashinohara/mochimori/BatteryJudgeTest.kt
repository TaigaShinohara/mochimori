package com.taigashinohara.mochimori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryJudgeTest {
    @Test fun coolAndDim_isSaving() {
        val r = BatteryJudge.judge(BatteryJudge.Input(80, 30f, 100, autoSavingEnabled = true))
        assertTrue(r.saving)
        assertEquals(BatteryJudge.State.SAVING, r.state)
    }

    @Test fun hotBattery_isNotSaving() {
        val r = BatteryJudge.judge(BatteryJudge.Input(80, 41f, 100, autoSavingEnabled = true))
        assertFalse(r.saving)
        assertEquals(BatteryJudge.FixKind.HEAT, r.fixKind)
        assertTrue(r.reason.contains("熱い"))
    }

    @Test fun maxBrightness_isNotSaving() {
        val r = BatteryJudge.judge(BatteryJudge.Input(80, 30f, 255, autoSavingEnabled = true))
        assertFalse(r.saving)
        assertEquals(BatteryJudge.FixKind.BRIGHTNESS, r.fixKind)
    }

    @Test fun lowLevelWithoutAutoSaving_isNotSaving() {
        val r = BatteryJudge.judge(BatteryJudge.Input(20, 30f, 100, autoSavingEnabled = false))
        assertFalse(r.saving)
        assertEquals(BatteryJudge.FixKind.AUTO_OFF, r.fixKind)
    }

    @Test fun lowLevelWithAutoSavingApplied_isSaving() {
        val r = BatteryJudge.judge(
            BatteryJudge.Input(20, 30f, 100, autoSavingEnabled = true, profileApplied = true),
        )
        assertTrue(r.saving)
        assertTrue(r.reason.contains("作動中"))
    }

    @Test fun lowLevelWithAutoSavingNotApplied_doesNotClaimActive() {
        val r = BatteryJudge.judge(
            BatteryJudge.Input(20, 30f, 100, autoSavingEnabled = true, profileApplied = false),
        )
        assertFalse(r.saving)
        assertFalse(r.reason.contains("作動中"))
        assertEquals(BatteryJudge.FixKind.APPLY_NOW, r.fixKind)
    }

    @Test fun lowLevelWithAutoSavingNoPermission_asksPermission() {
        val r = BatteryJudge.judge(
            BatteryJudge.Input(
                20, 30f, 100,
                autoSavingEnabled = true, profileApplied = false, canWriteSettings = false,
            ),
        )
        assertFalse(r.saving)
        assertEquals(BatteryJudge.FixKind.NEED_PERMISSION, r.fixKind)
    }

    @Test fun emergency_showsSaverActionNotSaving() {
        val r = BatteryJudge.judge(
            BatteryJudge.Input(10, 30f, 100, autoSavingEnabled = true, profileApplied = true),
        )
        assertEquals(BatteryJudge.State.EMERGENCY, r.state)
        assertEquals(BatteryJudge.FixKind.EMERGENCY_SAVER, r.fixKind)
        assertFalse(r.saving)
    }

    @Test fun charging_isChargingStateWithoutProfileWording() {
        val r = BatteryJudge.judge(
            BatteryJudge.Input(20, 30f, 100, autoSavingEnabled = true, charging = true),
        )
        assertEquals(BatteryJudge.State.CHARGING, r.state)
        assertTrue(r.reason.contains("充電中"))
        assertFalse(r.reason.contains("プロファイル"))
    }

    @Test fun chargingAbove80_suggestsUnplugging() {
        val r = BatteryJudge.judge(
            BatteryJudge.Input(85, 30f, 100, autoSavingEnabled = true, charging = true),
        )
        assertEquals(BatteryJudge.State.CHARGING, r.state)
        assertEquals(BatteryJudge.FixKind.CHARGING_FULL, r.fixKind)
        assertTrue(r.reason.contains("抜くと長持ち"))
    }

    @Test fun unknownLevel_isUnknownNotGuessed() {
        val r = BatteryJudge.judge(
            BatteryJudge.Input(0, 0f, -1, autoSavingEnabled = true, levelKnown = false),
        )
        assertEquals(BatteryJudge.State.UNKNOWN, r.state)
        assertEquals(BatteryJudge.FixKind.NONE, r.fixKind)
    }
}
