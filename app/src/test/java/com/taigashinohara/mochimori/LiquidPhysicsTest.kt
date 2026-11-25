package com.taigashinohara.mochimori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-Kotlin [LiquidPhysics] shallow-water + wall-splash engine:
 * volume conservation, tilt flow, wall-impact splash jets & droplets, wall-clinging
 * drops, damping to rest, and numerical stability (no NaN/Inf under extreme shakes).
 */
class LiquidPhysicsTest {

    @Test
    fun resetInitializesFlatVolume() {
        val sim = LiquidPhysics()
        sim.reset(0.65f)
        val avg = sim.h.average().toFloat()
        assertEquals(0.65f, avg, 0.001f)
        assertTrue(sim.drops.none { it.active })
    }

    @Test
    fun tiltingRightFlowsWaterRightWhileConservingVolume() {
        val sim = LiquidPhysics()
        sim.reset(0.60f)
        repeat(60) {
            sim.step(
                dtSeconds = 1f / 60f,
                tiltRad = 0.45f,
                lateralAccelNorm = 0f,
                verticalAccelNorm = 0f,
                liveliness = 0.7f,
                bubbleCount = 8,
                bubbleRate = 0.8f,
            )
        }
        val leftMean = sim.h.slice(0 until 10).average().toFloat()
        val rightMean = sim.h.slice(sim.cols - 10 until sim.cols).average().toFloat()
        assertTrue("Right side ($rightMean) should rise above left ($leftMean)", rightMean > leftMean + 0.12f)
        assertEquals("Total liquid volume must stay conserved", 0.60f, sim.h.average().toFloat(), 0.01f)
    }

    @Test
    fun hardLateralShakeTriggersWallImpactJetAndSplashDroplets() {
        val sim = LiquidPhysics()
        sim.reset(0.60f)
        var maxJet = 0f
        var maxDrops = 0
        var maxImpact = 0f
        // Shake strongly right -> inertia slams liquid into left wall, then reverse
        repeat(90) { stepIdx ->
            val lat = when {
                stepIdx < 20 -> 11f
                stepIdx < 40 -> -11f
                stepIdx < 60 -> 10f
                else -> 0f
            }
            sim.step(
                dtSeconds = 1f / 60f,
                tiltRad = 0f,
                lateralAccelNorm = lat,
                verticalAccelNorm = 0f,
                liveliness = 1.0f,
                bubbleCount = 12,
                bubbleRate = 1.0f,
            )
            maxJet = maxOf(maxJet, sim.leftJetH, sim.rightJetH)
            maxDrops = maxOf(maxDrops, sim.drops.count { it.active })
            maxImpact = maxOf(maxImpact, sim.lastWallImpact)
        }
        assertTrue("Wall impact should be detected (maxImpact=$maxImpact)", maxImpact > 0.2f)
        assertTrue("Wall splash jet should rise on slam (maxJet=$maxJet)", maxJet > 0.03f)
        assertTrue("Splash droplets should spawn on wall slam (maxDrops=$maxDrops)", maxDrops >= 4)
    }

    @Test
    fun extremeShakesRemainFiniteAndSettleWhenStill() {
        val sim = LiquidPhysics()
        sim.reset(0.75f)
        repeat(60) { i ->
            val sign = if (i % 10 < 5) 1f else -1f
            sim.step(
                dtSeconds = 0.033f,
                tiltRad = sign * 1.0f,
                lateralAccelNorm = sign * 20f,
                verticalAccelNorm = sign * 15f,
                liveliness = 1.0f,
                bubbleCount = 20,
                bubbleRate = 1.4f,
            )
        }
        for (v in sim.h) assertFalse("Height must stay finite", v.isNaN() || v.isInfinite())
        for (v in sim.u) assertFalse("Velocity must stay finite", v.isNaN() || v.isInfinite())
        assertEquals(0.75f, sim.h.average().toFloat(), 0.02f)

        // Hold completely still — energy should damp down cleanly
        repeat(180) {
            sim.step(
                dtSeconds = 1f / 60f,
                tiltRad = 0f,
                lateralAccelNorm = 0f,
                verticalAccelNorm = 0f,
                liveliness = 0.45f,
                bubbleCount = 5,
                bubbleRate = 0.4f,
            )
        }
        assertTrue("Splash energy should decay when held still (${sim.splashEnergy})", sim.splashEnergy < 0.25f)
    }
}
