package com.taigashinohara.mochimori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryPaceTest {
    private val t0 = 1_700_000_000_000L
    private val min = 60_000L

    private fun s(atMin: Long, level: Int, charging: Boolean = false) =
        BatteryPace.Sample(t0 + atMin * min, level, charging)

    @Test fun noSamples_returnsNull() {
        assertNull(BatteryPace.estimateMinutesToEmpty(emptyList()))
    }

    @Test fun singleSample_returnsNull() {
        assertNull(BatteryPace.estimateMinutesToEmpty(listOf(s(0, 80))))
    }

    @Test fun spanTooShort_returnsNull() {
        // 10 minutes of observation cannot support a pace, however big the drop.
        assertNull(BatteryPace.estimateMinutesToEmpty(listOf(s(0, 90), s(10, 85))))
    }

    @Test fun dropTooSmall_returnsNull() {
        // 1% over an hour is quantisation noise, not a pace.
        assertNull(BatteryPace.estimateMinutesToEmpty(listOf(s(0, 90), s(60, 89))))
    }

    @Test fun steadyDrain_estimatesFromMeasuredRate() {
        // 100% -> 90% over 2 hours = 5%/h; at 90% that leaves 18 hours.
        val est = BatteryPace.estimateMinutesToEmpty(listOf(s(0, 100), s(120, 90)))
        assertEquals(18 * 60L, est)
    }

    @Test fun chargingBreaksTheSession() {
        // Before the charge the level was 50%; only the post-charge
        // discharge (78 -> 76 over 1 hour = 2%/h) may set the pace:
        // 76% / 2%/h = 38 hours.
        val est = BatteryPace.estimateMinutesToEmpty(
            listOf(s(0, 50), s(60, 80, charging = true), s(120, 78), s(180, 76)),
        )
        assertEquals(38 * 60L, est)
    }

    @Test fun stillCharging_returnsNull() {
        assertNull(
            BatteryPace.estimateMinutesToEmpty(
                listOf(s(0, 50, charging = true), s(60, 70, charging = true)),
            ),
        )
    }

    @Test fun levelRoseWhileDischarging_returnsNull() {
        // A rising level without a charging flag is noise, not a pace.
        assertNull(BatteryPace.estimateMinutesToEmpty(listOf(s(0, 50), s(60, 55))))
    }

    @Test fun samplesOutsideWindowAreIgnored() {
        // The 8-hour-old sample is outside the 6h window: the pace comes
        // from 60 -> 58 over the last hour (2%/h), so 58% lasts 29 hours.
        // Using the whole history instead would give a much larger number.
        val est = BatteryPace.estimateMinutesToEmpty(
            listOf(s(0, 100), s(480, 60), s(540, 58)),
        )
        assertEquals(29 * 60L, est)
    }

    @Test fun emptyBattery_isZeroMinutes() {
        assertEquals(0L, BatteryPace.estimateMinutesToEmpty(listOf(s(0, 3), s(60, 0))))
    }

    @Test fun encodeDecode_roundTrips() {
        val samples = listOf(s(0, 90), s(15, 89, charging = true), s(30, 88))
        assertEquals(samples, BatteryPace.decode(BatteryPace.encode(samples)))
    }

    @Test fun decode_skipsGarbage() {
        assertEquals(emptyList<BatteryPace.Sample>(), BatteryPace.decode(null))
        assertEquals(emptyList<BatteryPace.Sample>(), BatteryPace.decode(""))
        assertEquals(
            listOf(s(0, 90)),
            BatteryPace.decode("${t0}:90:0,broken,1:2,${t0}:140:0"),
        )
    }

    @Test fun prune_dropsOldAndCapsLength() {
        val now = t0 + 72 * 60 * min
        val many = (0 until 100).map { i -> BatteryPace.Sample(now - i * min, 50, false) }
        val pruned = BatteryPace.prune(many, now)
        assertEquals(BatteryPace.MAX_SAMPLES, pruned.size)
        // Sorted ascending, newest last.
        assertEquals(now, pruned.last().timeMs)
        val old = listOf(BatteryPace.Sample(now - 49 * 60 * min, 50, false))
        assertEquals(emptyList<BatteryPace.Sample>(), BatteryPace.prune(old, now))
    }

    @Test fun formatDuration_readsNaturally() {
        assertEquals("約0分", BatteryPace.formatDuration(0))
        assertEquals("約45分", BatteryPace.formatDuration(45))
        assertEquals("約1時間", BatteryPace.formatDuration(60))
        assertEquals("約2時間30分", BatteryPace.formatDuration(150))
    }
}
