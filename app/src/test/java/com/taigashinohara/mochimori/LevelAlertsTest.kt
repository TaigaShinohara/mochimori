package com.taigashinohara.mochimori

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelAlertsTest {

    @Test fun noThresholdsSelected_neverFires() {
        val d = LevelAlerts.evaluate(emptySet(), emptySet(), levelPercent = 10, charging = false)
        assertEquals(emptyList<Int>(), d.toNotify)
        assertEquals(emptySet<Int>(), d.firedAfter)
    }

    @Test fun aboveAllThresholds_doesNotFire() {
        val d = LevelAlerts.evaluate(setOf(50, 30), emptySet(), levelPercent = 60, charging = false)
        assertEquals(emptyList<Int>(), d.toNotify)
        assertEquals(emptySet<Int>(), d.firedAfter)
    }

    @Test fun reachingThreshold_firesOnce() {
        val d = LevelAlerts.evaluate(setOf(30), emptySet(), levelPercent = 30, charging = false)
        assertEquals(listOf(30), d.toNotify)
        assertEquals(setOf(30), d.firedAfter)
    }

    @Test fun stayingBelowThreshold_doesNotRefire() {
        val first = LevelAlerts.evaluate(setOf(30), emptySet(), levelPercent = 29, charging = false)
        val second = LevelAlerts.evaluate(setOf(30), first.firedAfter, levelPercent = 25, charging = false)
        assertEquals(emptyList<Int>(), second.toNotify)
        assertEquals(setOf(30), second.firedAfter)
    }

    @Test fun bigDrop_firesEveryCrossedThresholdHighestFirst() {
        val d = LevelAlerts.evaluate(
            setOf(50, 30, 20, 10), emptySet(), levelPercent = 18, charging = false,
        )
        assertEquals(listOf(50, 30, 20), d.toNotify)
        assertEquals(setOf(50, 30, 20), d.firedAfter)
    }

    @Test fun charging_neverFiresAndDoesNotConsume() {
        // Plugged in at 45% with a 50% threshold: no notification,
        // and the threshold is NOT marked fired, so unplugging
        // still below 50% notifies at the next check.
        val charging = LevelAlerts.evaluate(setOf(50), emptySet(), levelPercent = 45, charging = true)
        assertEquals(emptyList<Int>(), charging.toNotify)
        assertEquals(emptySet<Int>(), charging.firedAfter)
        val unplugged = LevelAlerts.evaluate(setOf(50), charging.firedAfter, levelPercent = 45, charging = false)
        assertEquals(listOf(50), unplugged.toNotify)
    }

    @Test fun risingAboveThreshold_rearms() {
        // Fired at 30%, charged back up to 80%: the fired flag
        // clears, and the next discharge to 30% notifies again.
        val rearmed = LevelAlerts.evaluate(setOf(30), setOf(30), levelPercent = 80, charging = true)
        assertEquals(emptySet<Int>(), rearmed.firedAfter)
        val again = LevelAlerts.evaluate(setOf(30), rearmed.firedAfter, levelPercent = 30, charging = false)
        assertEquals(listOf(30), again.toNotify)
    }

    @Test fun rearmIsPerThreshold() {
        // 50% fired earlier and the level is at 45%: 50 stays
        // re-armed-pending (level still below it, so still fired),
        // while nothing new fires.
        val d = LevelAlerts.evaluate(setOf(50, 30), setOf(50), levelPercent = 45, charging = false)
        assertEquals(emptyList<Int>(), d.toNotify)
        assertEquals(setOf(50), d.firedAfter)
        // Rising to 55% clears only the 50% flag.
        val up = LevelAlerts.evaluate(setOf(50, 30), d.firedAfter, levelPercent = 55, charging = true)
        assertEquals(emptySet<Int>(), up.firedAfter)
    }

    @Test fun removedThreshold_dropsOutOfFiredSet() {
        val d = LevelAlerts.evaluate(setOf(30), setOf(50, 30), levelPercent = 90, charging = false)
        assertEquals(emptySet<Int>(), d.firedAfter)
    }

    @Test fun unknownLevel_neverFiresAndKeepsState() {
        // The snapshot's unknown sentinel (-1) is below every
        // threshold numerically; the guard keeps it from firing
        // and from disturbing the fired set.
        val d = LevelAlerts.evaluate(setOf(50), setOf(30), levelPercent = -1, charging = false)
        assertEquals(emptyList<Int>(), d.toNotify)
        assertEquals(setOf(30), d.firedAfter)
    }
}
