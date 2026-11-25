package com.taigashinohara.mochimori

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin 2D-ready fluid simulation engine for the battery window.
 *
 * All simulation runs in normalised vessel coordinates:
 *   x in [0, 1] (0 = left glass wall, 1 = right glass wall)
 *   y in [0, 1] (0 = bottom, 1 = top ceiling)
 *
 * Components:
 *  1. Staggered 1D Shallow-Water solver (cell heights [h] + face
 *     velocities [u]) — conserves liquid volume under tilt and
 *     accelerates horizontally with gravity and lateral shakes.
 *  2. Wall-impact detection: high-speed flow slamming into the left or
 *     right wall converts momentum into a rising wall crest jet
 *     ([leftJetH] / [rightJetH]) and fans splash droplets inward and
 *     upward ("バシャバシャ").
 *  3. Lagged back-surface height field ([backH]) for 2.5D depth slosh.
 *  4. Fixed-pool splash droplets ([drops]): airborne droplets obey the
 *     tilted gravity vector, splash back into the shallow-water grid on
 *     landing (spawning secondary crown drops), and can cling to the
 *     glass walls ([Drop.clinging]) and slide slowly down into the liquid.
 *  5. Fixed-pool bubbles ([bubbles]): carried horizontally by the local
 *     shallow-water velocity [u] while rising opposite gravity, with
 *     temporary aeration bursts after hard wall impacts.
 *
 * Zero per-step allocations; free of Android classes and unit-tested.
 */
class LiquidPhysics(
    val cols: Int = DEFAULT_COLS,
) {
    /** Cell-centre liquid heights in [0, 1.35], where 1.0 = window top. */
    val h = FloatArray(cols)

    /** Lagged back-surface heights for 2.5D meniscus depth. */
    val backH = FloatArray(cols)

    /** Face-centred horizontal velocities (u[0] = u[cols] = 0 at glass walls). */
    val u = FloatArray(cols + 1)

    private val flux = FloatArray(cols + 1)
    private val hTmp = FloatArray(cols)

    /** Wall-runup splash crest height (normalised) & vertical velocity on each wall. */
    var leftJetH: Float = 0f
        private set
    var leftJetVel: Float = 0f
        private set
    var rightJetH: Float = 0f
        private set
    var rightJetVel: Float = 0f
        private set

    /** Smoothed splash activity in [0, 1] (drives haptics & wave energy). */
    var splashEnergy: Float = 0f
        private set

    /** Smoothed slosh tilt proxy (radians) for subtle glass reflection parallax. */
    var effectiveTilt: Float = 0f
        private set

    /** Temporary extra bubbles entrained by hard wall slams (decays over ~2 s). */
    var aeration: Float = 0f
        private set

    /** Peak wall-impact impulse in the most recent [step] (for haptics / tests). */
    var lastWallImpact: Float = 0f
        private set

    var clock: Float = 0f
        private set

    private var fillLevel: Float = 0.72f
    private var initialized: Boolean = false
    private var randSeed: Int = 1
    private var leftSplashCool: Float = 0f
    private var rightSplashCool: Float = 0f

    class Drop {
        var active = false
        var clinging = false
        var clingRight = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var r = 0.02f
        var life = 0f
    }

    val drops: Array<Drop> = Array(DROP_POOL) { Drop() }

    class Bubble {
        var init = false
        var x = 0f
        var y = 0f
        var r = 0.015f
        var speed = 0.1f
        var wob = 0f
    }

    val bubbles: Array<Bubble> = Array(BUBBLE_POOL) { Bubble() }

    /** Resets the fluid to a calm horizontal level. */
    fun reset(levelFraction: Float) {
        val lvl = levelFraction.coerceIn(0f, 1f)
        fillLevel = lvl
        initialized = true
        for (i in 0 until cols) {
            h[i] = lvl
            backH[i] = lvl
        }
        for (i in 0..cols) u[i] = 0f
        leftJetH = 0f; leftJetVel = 0f
        rightJetH = 0f; rightJetVel = 0f
        splashEnergy = 0f
        effectiveTilt = 0f
        aeration = 0f
        lastWallImpact = 0f
        for (d in drops) d.active = false
    }

    /** Adjusts the target fill level while preserving live waves. */
    fun setFillLevel(levelFraction: Float) {
        val lvl = levelFraction.coerceIn(0f, 1f)
        if (!initialized) {
            reset(lvl)
            return
        }
        val delta = lvl - fillLevel
        fillLevel = lvl
        if (abs(delta) > 0.0001f) {
            for (i in 0 until cols) {
                h[i] = (h[i] + delta).coerceIn(0f, 1.25f)
                backH[i] = (backH[i] + delta).coerceIn(0f, 1.25f)
            }
        }
    }

    /**
     * Advances the fluid physics by [dtSeconds].
     *
     * @param tiltRad device tilt in radians (+ = right side down, - = left side down)
     * @param lateralAccelNorm horizontal linear acceleration (m/s², + = phone accelerated right,
     *        so fluid is pushed left by inertia)
     * @param verticalAccelNorm vertical linear acceleration (m/s², shakes inject vertical energy)
     * @param liveliness 0 = frozen/unknown, 0.45 = cool/saving, 0.7 = charging, 1.0 = hot
     * @param bubbleCount active bubble count in [0, BUBBLE_POOL]
     * @param bubbleRate bubble rise speed multiplier
     * @param aspect vessel height / width (used to scale gravity along y vs x)
     */
    fun step(
        dtSeconds: Float,
        tiltRad: Float,
        lateralAccelNorm: Float,
        verticalAccelNorm: Float,
        liveliness: Float,
        bubbleCount: Int,
        bubbleRate: Float,
        aspect: Float = 1.72f,
    ) {
        if (!initialized) reset(fillLevel)
        val dt = dtSeconds.coerceIn(0.0005f, 0.05f)
        clock += dt
        lastWallImpact = 0f
        if (leftSplashCool > 0f) leftSplashCool = max(0f, leftSplashCool - dt)
        if (rightSplashCool > 0f) rightSplashCool = max(0f, rightSplashCool - dt)

        val clampedTilt = tiltRad.coerceIn(-1.05f, 1.05f)
        effectiveTilt += (clampedTilt - effectiveTilt) * min(1f, dt * 6f)

        if (fillLevel <= 0.005f) {
            for (i in 0 until cols) { h[i] = 0f; backH[i] = 0f }
            for (i in 0..cols) u[i] = 0f
            return
        }

        val dx = 1f / cols
        // Sub-step for strict CFL stability even during violent shakes
        val subSteps = max(2, (dt / 0.0035f).toInt() + 1).coerceAtMost(14)
        val subDt = dt / subSteps

        // Gravity & inertial acceleration along the vessel's horizontal axis:
        // positive tiltRad means right side is lower -> fluid accelerates +x (right).
        // positive lateralAccelNorm (phone accelerates right) -> inertia pushes fluid -x (left).
        val gravityX = GRAVITY_X * sin(clampedTilt.toDouble()).toFloat()
        val inertiaX = (-lateralAccelNorm * INERTIA_GAIN).coerceIn(-14f, 14f)
        val totalForceX = gravityX + inertiaX

        // Vertical shake excites surface ripples
        val vertShake = max(0f, abs(verticalAccelNorm) - 2.4f) * liveliness
        if (vertShake > 0.25f) {
            val kicks = (vertShake * 0.7f).toInt().coerceIn(1, 3)
            repeat(kicks) {
                val idx = (prand() * (cols - 2)).toInt().coerceIn(1, cols - 2)
                u[idx] += (prand() - 0.5f) * vertShake * 0.9f
            }
        }

        // Gentle ambient wave excitation so calm/hot states stay alive even on a flat table
        val ambientDrive = when {
            liveliness <= 0.01f -> 0f
            else -> (0.14f + 0.52f * liveliness * liveliness)
        }
        val waveFreq = 1.6f + 2.4f * liveliness

        var peakImpact = 0f
        repeat(subSteps) {
            // 1. Update face velocities u[1..cols-1] from pressure gradient + external force
            val ambient = ambientDrive * sin((clock * waveFreq).toDouble()).toFloat()
            for (i in 1 until cols) {
                val hL = h[i - 1]
                val hR = h[i]
                val avgH = 0.5f * (hL + hR)
                if (avgH > 0.008f) {
                    val dhdx = (hR - hL) / dx
                    val waveAccel = -WAVE_C_SQ * dhdx + totalForceX + ambient
                    var nu = u[i] + waveAccel * subDt
                    // Viscous damping (slightly lighter when lively so waves slosh longer)
                    val damp = (2.15f - 0.45f * liveliness).coerceAtLeast(1.4f)
                    nu *= (1f - damp * subDt).coerceAtLeast(0f)
                    u[i] = nu.coerceIn(-MAX_U, MAX_U)
                } else {
                    u[i] *= 0.85f
                }
            }
            // Rigid glass walls: zero normal velocity
            u[0] = 0f
            u[cols] = 0f

            // 2. Detect wall slams BEFORE advection dissipates the incoming flow
            val leftSlam = max(0f, -(u[1] * 0.6f + u[2] * 0.4f))
            val rightSlam = max(0f, u[cols - 1] * 0.6f + u[cols - 2] * 0.4f)

            if (leftSlam > IMPACT_THRESH && h[0] > 0.04f) {
                val intensity = ((leftSlam - IMPACT_THRESH) / 1.45f).coerceIn(0f, 1.5f) * (0.45f + 0.55f * liveliness)
                if (intensity > peakImpact) peakImpact = intensity
                // Convert horizontal impact into upward wall crest jet velocity
                leftJetVel += intensity * 4.8f * subDt * 18f
                if (leftSplashCool <= 0f && intensity > 0.14f) {
                    spawnWallSplash(isRightWall = false, intensity = intensity, aspect = aspect)
                    leftSplashCool = (0.11f - 0.05f * min(1f, intensity)).coerceAtLeast(0.055f)
                }
            }
            if (rightSlam > IMPACT_THRESH && h[cols - 1] > 0.04f) {
                val intensity = ((rightSlam - IMPACT_THRESH) / 1.45f).coerceIn(0f, 1.5f) * (0.45f + 0.55f * liveliness)
                if (intensity > peakImpact) peakImpact = intensity
                rightJetVel += intensity * 4.8f * subDt * 18f
                if (rightSplashCool <= 0f && intensity > 0.14f) {
                    spawnWallSplash(isRightWall = true, intensity = intensity, aspect = aspect)
                    rightSplashCool = (0.11f - 0.05f * min(1f, intensity)).coerceAtLeast(0.055f)
                }
            }

            // 3. Conservative flux advection (upwind-blended for stability + sharp crests)
            flux[0] = 0f
            flux[cols] = 0f
            for (i in 1 until cols) {
                val ui = u[i]
                val hUp = if (ui >= 0f) h[i - 1] else h[i]
                val hMid = 0.5f * (h[i - 1] + h[i])
                val hFace = hUp * 0.65f + hMid * 0.35f
                flux[i] = ui * hFace
            }
            for (i in 0 until cols) {
                val nh = h[i] - (flux[i + 1] - flux[i]) / dx * subDt
                hTmp[i] = nh.coerceIn(0f, 1.28f)
            }

            // 4. Mild surface-tension smoothing & exact volume conservation
            var sum = 0f
            val smooth = 0.06f
            for (i in 0 until cols) {
                val l = hTmp[max(0, i - 1)]
                val r = hTmp[min(cols - 1, i + 1)]
                val s = hTmp[i] + smooth * (0.5f * (l + r) - hTmp[i])
                h[i] = s.coerceIn(0f, 1.22f)
                sum += h[i]
            }
            val targetSum = fillLevel * cols
            if (sum > 0.001f) {
                val corr = (targetSum - sum) / cols
                for (i in 0 until cols) {
                    h[i] = (h[i] + corr).coerceIn(0f, 1.22f)
                }
            }

            // 5. Ceiling slap: if water hits the top glass (h > 1.0), bounce droplets downward
            if (h[0] > 0.98f && leftSlam > IMPACT_THRESH * 0.8f && leftSplashCool <= 0f) {
                spawnCeilingSplash(0.12f, 0.35f * liveliness)
                leftSplashCool = 0.14f
            }
            if (h[cols - 1] > 0.98f && rightSlam > IMPACT_THRESH * 0.8f && rightSplashCool <= 0f) {
                spawnCeilingSplash(0.88f, 0.35f * liveliness)
                rightSplashCool = 0.14f
            }

            // 6. Wall crest jet ballistic integration (rises up wall, arcs back down)
            val jetGrav = 10.5f / max(0.8f, aspect)
            leftJetVel -= jetGrav * subDt
            leftJetH += leftJetVel * subDt
            if (leftJetH <= 0f) {
                leftJetH = 0f
                if (leftJetVel < 0f) leftJetVel = 0f
            } else if (leftJetH > 0.34f) {
                leftJetH = 0.34f
                leftJetVel *= -0.25f
            }
            rightJetVel -= jetGrav * subDt
            rightJetH += rightJetVel * subDt
            if (rightJetH <= 0f) {
                rightJetH = 0f
                if (rightJetVel < 0f) rightJetVel = 0f
            } else if (rightJetH > 0.34f) {
                rightJetH = 0.34f
                rightJetVel *= -0.25f
            }

            // 7. Lagged back-surface height field for 2.5D depth
            val backRate = min(1f, subDt * 6.8f)
            for (i in 0 until cols) {
                backH[i] += (h[i] - backH[i]) * backRate
            }
        }

        lastWallImpact = peakImpact
        // Update splash energy & aeration
        var flowEnergy = 0f
        for (i in 1 until cols) flowEnergy += abs(u[i])
        flowEnergy /= cols
        val targetSplash = (
            peakImpact * 0.95f +
                (flowEnergy / 0.85f).coerceIn(0f, 1f) * 0.65f +
                (vertShake / 8f).coerceIn(0f, 1f) * 0.5f
            ).coerceIn(0f, 1f) * liveliness
        splashEnergy = if (targetSplash > splashEnergy) {
            splashEnergy + (targetSplash - splashEnergy) * min(1f, dt * 15f)
        } else {
            splashEnergy + (targetSplash - splashEnergy) * min(1f, dt * 1.6f)
        }
        if (peakImpact > 0.2f) {
            aeration = (aeration + peakImpact * 0.55f).coerceAtMost(1f)
        } else {
            aeration = max(0f, aeration - dt * 0.45f)
        }

        stepDrops(dt, clampedTilt, aspect)
        stepBubbles(dt, clampedTilt, bubbleCount, bubbleRate)
    }

    /** Injects a velocity kick into the shallow-water grid at normalised [x]. */
    fun impulseAt(x: Float, dv: Float) {
        val c = (x.coerceIn(0f, 1f) * cols).toInt().coerceIn(1, cols - 1)
        for (k in -2..2) {
            val idx = c + k
            if (idx in 1 until cols) {
                val w = 1f - abs(k) / 3f
                // Outward ripple: push left of impact left, right of impact right
                val dir = when {
                    k < 0 -> -1f
                    k > 0 -> 1f
                    else -> 0f
                }
                u[idx] = (u[idx] + dir * dv * w).coerceIn(-MAX_U, MAX_U)
            }
        }
        // Small local dip at impact cell
        val hc = c.coerceIn(0, cols - 1)
        h[hc] = (h[hc] - abs(dv) * 0.02f).coerceIn(0f, 1.2f)
    }

    /** Samples interpolated front surface height at normalised [x] in [0, 1]. */
    fun surfaceAt(x: Float): Float {
        val f = x.coerceIn(0f, 1f) * (cols - 1)
        val i = f.toInt().coerceIn(0, cols - 2)
        val t = f - i
        return h[i] * (1f - t) + h[i + 1] * t
    }

    /** Samples interpolated back surface height at normalised [x] in [0, 1]. */
    fun backSurfaceAt(x: Float): Float {
        val f = x.coerceIn(0f, 1f) * (cols - 1)
        val i = f.toInt().coerceIn(0, cols - 2)
        val t = f - i
        return backH[i] * (1f - t) + backH[i + 1] * t
    }

    private fun spawnWallSplash(isRightWall: Boolean, intensity: Float, aspect: Float) {
        val wallH = if (isRightWall) h[cols - 1] else h[0]
        val baseY = (wallH + (if (isRightWall) rightJetH else leftJetH) * 0.55f).coerceIn(0.06f, 0.94f)
        val count = (2 + (intensity * 5.5f).toInt()).coerceIn(2, 8)
        val dir = if (isRightWall) -1f else 1f // spray inward away from wall
        val baseX = if (isRightWall) 0.96f else 0.04f
        repeat(count) { idx ->
            val d = acquireDrop() ?: return
            val spread = prand()
            d.active = true
            d.clinging = false
            d.clingRight = isRightWall
            d.x = (baseX + dir * (0.01f + 0.03f * prand())).coerceIn(0.02f, 0.98f)
            d.y = (baseY + (prand() - 0.25f) * 0.05f).coerceIn(0.04f, 0.96f)
            // Fan of velocities: some shoot high along the wall, others arc across the vessel
            val speedScale = 0.65f + 0.85f * min(1.3f, intensity)
            d.vx = dir * (0.35f + 1.45f * spread) * speedScale
            d.vy = (0.75f + 1.45f * (1f - spread * 0.55f)) * speedScale / max(0.9f, aspect * 0.72f)
            d.r = if (idx == 0) {
                0.022f + 0.012f * prand()
            } else {
                0.010f + 0.016f * prand()
            }
            d.life = 0f
        }
    }

    private fun spawnCeilingSplash(x: Float, intensity: Float) {
        repeat(3) {
            val d = acquireDrop() ?: return
            d.active = true
            d.clinging = false
            d.x = (x + (prand() - 0.5f) * 0.14f).coerceIn(0.05f, 0.95f)
            d.y = 0.95f
            d.vx = (prand() - 0.5f) * 0.9f * intensity
            d.vy = -(0.25f + 0.45f * prand()) * intensity
            d.r = 0.012f + 0.012f * prand()
            d.life = 0f
        }
    }

    private fun spawnSecondaryDrop(x: Float, y: Float, impactSpeed: Float) {
        val d = acquireDrop() ?: return
        d.active = true
        d.clinging = false
        d.x = x.coerceIn(0.04f, 0.96f)
        d.y = (y + 0.015f).coerceIn(0.04f, 0.96f)
        d.vx = (prand() - 0.5f) * 0.95f
        d.vy = (impactSpeed * (0.32f + 0.22f * prand())).coerceIn(0.25f, 1.15f)
        d.r = 0.009f + 0.010f * prand()
        d.life = 0f
    }

    private fun acquireDrop(): Drop? = drops.firstOrNull { !it.active }

    private fun stepDrops(dt: Float, tiltRad: Float, aspect: Float) {
        val g = 3.8f / max(0.8f, aspect * 0.72f)
        val gx = g * sin(tiltRad.toDouble()).toFloat() * 0.65f
        val gy = -g * cos(tiltRad.toDouble()).toFloat()
        for (d in drops) {
            if (!d.active) continue
            d.life += dt
            if (d.clinging) {
                // Wall-clinging glass droplet slides slowly down under gravity
                d.vy = (d.vy - 0.42f * dt).coerceAtLeast(-0.24f)
                d.y += d.vy * dt
                d.x = if (d.clingRight) 1f - d.r * 0.7f else d.r * 0.7f
                val surf = surfaceAt(d.x)
                if (d.y <= surf + 0.008f || d.life > 3.2f) {
                    d.active = false
                }
                continue
            }
            d.vx += gx * dt
            d.vy += gy * dt
            d.x += d.vx * dt
            d.y += d.vy * dt

            // Left / right glass wall collision: cling or rebound
            if (d.x < d.r) {
                d.x = d.r
                if (abs(d.vx) < 0.75f || prand() < 0.48f) {
                    d.clinging = true
                    d.clingRight = false
                    d.vx = 0f
                    d.vy = min(0f, d.vy * 0.15f)
                } else {
                    d.vx = -d.vx * 0.42f
                }
            } else if (d.x > 1f - d.r) {
                d.x = 1f - d.r
                if (abs(d.vx) < 0.75f || prand() < 0.48f) {
                    d.clinging = true
                    d.clingRight = true
                    d.vx = 0f
                    d.vy = min(0f, d.vy * 0.15f)
                } else {
                    d.vx = -d.vx * 0.42f
                }
            }

            // Ceiling bounce
            if (d.y > 1f - d.r) {
                d.y = 1f - d.r
                d.vy = -abs(d.vy) * 0.35f
            }

            // Landing back into the liquid surface
            val surf = surfaceAt(d.x)
            if (d.vy < 0f && d.y <= surf) {
                val impact = abs(d.vy)
                impulseAt(d.x, impact * 0.28f)
                d.active = false
                if (impact > 0.65f && prand() < 0.45f) {
                    spawnSecondaryDrop(d.x, surf, impact)
                }
            } else if (d.y < 0f || d.life > 3.5f) {
                d.active = false
            }
        }
    }

    private fun stepBubbles(dt: Float, tiltRad: Float, baseCount: Int, rate: Float) {
        val totalCount = (baseCount + (aeration * 8f).toInt()).coerceIn(0, BUBBLE_POOL)
        val sinTilt = sin(tiltRad.toDouble()).toFloat()
        for (i in 0 until totalCount) {
            val b = bubbles[i]
            if (!b.init) initBubble(b, i)
            val rise = b.speed * max(0.3f, rate) * dt * 1.45f
            b.y += rise
            // Advect horizontally with shallow-water flow + buoyancy tilt drift
            val uIdx = (b.x * cols).toInt().coerceIn(0, cols)
            b.x = (b.x + u[uIdx] * dt * 0.32f - sinTilt * rise * 0.35f).coerceIn(0.06f, 0.94f)
            val surf = surfaceAt(b.x)
            if (b.y >= surf - 0.015f || b.y >= 0.98f) {
                if (b.r > 0.022f && surf > 0.05f) {
                    impulseAt(b.x, 0.06f)
                }
                b.y = 0.02f + 0.06f * prand()
                b.x = 0.08f + 0.84f * prand()
            }
        }
    }

    private fun initBubble(b: Bubble, i: Int) {
        b.init = true
        val seed = frac(BUBBLE_SEEDS[i % BUBBLE_SEEDS.size] + i * 0.137f)
        b.x = 0.08f + 0.84f * frac(seed * 7.31f + i * 0.3819f)
        b.y = (0.04f + 0.85f * frac(seed * 3.97f + i * 0.618f)) * fillLevel
        val tier = i % 5
        b.r = when (tier) {
            0 -> 0.022f + 0.010f * frac(seed * 3.7f)
            1, 2 -> 0.013f + 0.008f * frac(seed * 5.1f)
            else -> 0.008f + 0.006f * frac(seed * 2.9f)
        }
        b.speed = 0.06f + 0.10f * frac(seed * 5.3f) + (if (tier == 0) 0.03f else 0f)
        b.wob = seed * 17f + i * 1.1f
    }

    private fun prand(): Float {
        randSeed++
        return frac(randSeed * 0.6180339f + 0.131f)
    }

    private fun frac(v: Float): Float = v - kotlin.math.floor(v)

    companion object {
        const val DEFAULT_COLS = 44
        const val DROP_POOL = 64
        const val BUBBLE_POOL = 24

        // Shallow-water wave speed squared, horizontal gravity scale, inertia gain
        private const val WAVE_C_SQ = 1.65f
        private const val GRAVITY_X = 6.2f
        private const val INERTIA_GAIN = 1.15f
        private const val MAX_U = 4.2f
        private const val IMPACT_THRESH = 0.42f

        private val BUBBLE_SEEDS = floatArrayOf(
            0.11f, 0.53f, 0.83f, 0.31f, 0.67f, 0.23f, 0.97f, 0.41f,
        )
    }
}
