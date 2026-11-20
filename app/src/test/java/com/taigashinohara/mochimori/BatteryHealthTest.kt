package com.taigashinohara.mochimori

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHealthTest {
    @Test fun capacity_extrapolatesFromChargeCounter() {
        // 2,425,000 µAh remaining at 50% -> ~4,850 mAh full.
        assertEquals(4850, BatteryHealth.estimatedFullCapacityMah(2_425_000L, 50))
    }

    @Test fun capacity_unsupportedCounter_returnsNull() {
        assertNull(BatteryHealth.estimatedFullCapacityMah(Long.MIN_VALUE, 50))
        assertNull(BatteryHealth.estimatedFullCapacityMah(0L, 50))
    }

    @Test fun capacity_lowLevel_returnsNull() {
        // At 10% the whole-% rounding is amplified 10x; don't quote it.
        assertNull(BatteryHealth.estimatedFullCapacityMah(485_000L, 10))
    }

    @Test fun capacity_implausibleResult_returnsNull() {
        // 20,000,000 µAh at 50% would claim a 40,000 mAh phone battery.
        assertNull(BatteryHealth.estimatedFullCapacityMah(20_000_000L, 50))
    }

    @Test fun healthLabel_good() {
        assertEquals("良好", BatteryHealth.healthLabel(BatteryManager.BATTERY_HEALTH_GOOD))
    }

    @Test fun healthLabel_overheat() {
        val label = BatteryHealth.healthLabel(BatteryManager.BATTERY_HEALTH_OVERHEAT)
        assertTrue(label!!.contains("過熱"))
    }

    @Test fun healthLabel_unknownOrMissing_returnsNull() {
        assertNull(BatteryHealth.healthLabel(BatteryManager.BATTERY_HEALTH_UNKNOWN))
        assertNull(BatteryHealth.healthLabel(-1))
    }
}
