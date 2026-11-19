package com.taigashinohara.mochimori

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The big central battery, drawn entirely in code (no image assets).
 *
 * v0.3.6 redesign, at the user's explicit instruction ("バッテリーは縦に
 * する／中は液体状にアニメーション／できればスマホの角度に合わせて揺
 * れてほしい"):
 * - The body is UPRIGHT again — width : height ≈ 1 : 1.55 — but this is
 *   not the v0.3.3 shape that read as a can: the terminal cap now sits on
 *   TOP centre like a standing dry-cell battery, the proportions are
 *   slimmer, and the interior is a liquid, so the silhouette reads as a
 *   battery at a glance. (The wide 1.7:1 body of v0.3.4/0.3.5 is retired
 *   by the user's direct instruction; see ART_DIRECTION.md.)
 * - The charge is a LIQUID: it fills from the bottom, its surface waves,
 *   and bubbles rise through it — almost still when saving, a little
 *   livelier when burning, steadily rising when charging ("filling up").
 * - The surface SLOSHES with the device's tilt. The accelerometer needs
 *   no permission at all (verified against the official Sensors Overview:
 *   the only sensor permission in the platform is
 *   HIGH_SAMPLING_RATE_SENSORS, required only above the 200 Hz sampling
 *   cap; this view samples at SENSOR_DELAY_UI, ~15 Hz). A low-pass filter
 *   removes hand jitter, and a spring–damper model makes the surface lag,
 *   overshoot and settle like real liquid. The listener is registered
 *   only while this face is attached and its window is visible, and is
 *   unregistered the moment it leaves the screen. Devices without an
 *   accelerometer fall back to the time-based waves alone.
 * - State art is transplanted onto the upright body: cool liquid +
 *   snowflakes balanced around the body when saving; orange liquid +
 *   flames standing from the top around the cap + rougher waves when
 *   burning; the bolt over the liquid + rising bubbles + pulsing glow
 *   while charging.
 * - Haptics (v0.3.5 spec, unchanged): one very weak pulse per animation
 *   loop at the motion's peak — the same phase drives the liquid's waves
 *   and the flames' growth, so the buzz lands on the liquid's biggest
 *   sway. Stops on detach, on the rule switch OFF, on the OS haptic
 *   setting OFF, and when motion is reduced.
 *
 * v0.3.9 particle liquid (user: "パーティクルを使いよりリアルな液体の
 * 動きを表現して"):
 * - The surface is now a PARTICLE CHAIN: every surface sample point is
 *   a particle with its own offset and velocity, spring-coupled to its
 *   neighbours and pulled back to the analytic wave. Ripples therefore
 *   travel along the surface and interfere organically instead of the
 *   whole surface moving as one rigid formula. Splash energy injects
 *   impulses into random particles; droplet landings kick the nearby
 *   particles.
 * - Droplets are real particles from a fixed pool: gravity, velocity,
 *   wall bounces inside the shell, and a landing that feeds its
 *   momentum back into the surface chain as a ripple (plus a chance
 *   of a small secondary droplet). Nothing is allocated per frame.
 * - Bubbles are particles too: each has its own position, rise speed
 *   and wobble, and respawns at the bottom when it reaches the
 *   surface — no more lock-step deterministic cycle.
 *
 * v0.3.10 glass vessel (user: "UIアートはシンプルガラスにしよう" —
 * audit docs/GLASS_REVIEW_v0.3.9.md): until now the shell was an
 * OPAQUE tint (a coloured-plastic read) and this face was audited but
 * exempted. The body is now drawn as a glass vessel in the same
 * language as the app's GlassDrawable surfaces:
 * - the shell fill is a translucent role-tint glass (alpha
 *   240→230→220, the same weight family as the stage cards' tint
 *   glass), so the aura and the foundation's light show through the
 *   rim and the vessel reads as glass holding the liquid, not as a
 *   painted can. The cavity stays opaque — the level must read.
 * - a 1dp specular rim runs just inside the outline, with the
 *   skill's exact edge gradient (white 75%→15% light / 38%→6% dark),
 *   replacing the flat white strip that used to sit on the top edge.
 * - the left reflection band is now a soft gradient that melts into
 *   the glass (white 36 → 0) instead of a hard-edged flat band.
 * - the cavity gets a whisper of depth shading (a darkening at the
 *   top inside edge, a brightening at the bottom) so the interior
 *   reads as the inside of a vessel.
 * - the outline's top stop is lightened (white blend 0.30 → 0.45):
 *   the rim catches the light from above like glass, while the mid
 *   stop stays the pure state colour, so state discrimination —
 *   carried by the outline, the liquid and the flames/crystals —
 *   is unchanged.
 *
 * v0.3.8 liquid pass (user: "さらに滑らかに／器内をバシャバシャと"):
 * - Smoothness: the surface is sampled at 44 points and drawn as a
 *   midpoint-quadratic curve (no polyline facets), a third fine wave
 *   component rides on the two travelling waves, and the spring physics
 *   integrates in ≤8ms sub-steps on the animator's real frame interval,
 *   so the motion stays smooth even when frames arrive unevenly.
 * - Splashing: a shake or a sharp tilt injects energy (measured from
 *   the linear acceleration and the surface's own spring velocity).
 *   While the energy lasts, the waves grow, a counter-wave reflects
 *   back off the far wall, and small droplets leap from the surface
 *   and fall back — always inside the shell (the liquid's clip).
 *   Held quietly, everything settles back to the calm v0.3.6 motion.
 *   The cool face splashes at less than half strength: stillness
 *   stays its lead. Splash peaks also get a haptic pulse, its
 *   amplitude scaled by the splash energy (see maybeSplashHaptic).
 * - When the OS animator scale is 0, everything is a still image: the
 *   liquid surface is level and frozen, the tilt sensor is never
 *   registered, flames stand upright, bubbles/particles freeze mid-way.
 *
 * Colours are resolved from the theme's fixed state roles, so Dynamic
 * Color never changes what the faces mean. State is never carried by
 * colour alone: flames vs crystals differ in shape too.
 *
 * Performance: all Paints/Paths are fields and reused; gradients are
 * built in onSizeChanged only; particles are a fixed small set computed
 * from deterministic per-index offsets (no allocation in onDraw).
 */
class BatteryFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var saving: Boolean = true
        set(value) {
            field = value
            onStateChanged()
        }
    /** Charging face: cool colours + the bolt (the only state with a bolt). */
    var charging: Boolean = false
        set(value) {
            field = value
            onStateChanged()
        }
    /** Unknown state: neutral grey face, never a guessed verdict (R7). */
    var unknownState: Boolean = false
        set(value) {
            field = value
            onStateChanged()
        }
    var levelPercent: Int = 80
        set(value) {
            field = value.coerceIn(0, 100)
            updateDescription()
            invalidate()
        }

    /**
     * Motion-synced haptic feedback (v0.3.5, user request). Set from the
     * "haptics" rule pref by MainActivity; defaults to false here so the
     * view never buzzes unless the screen explicitly enables it.
     */
    var hapticsEnabled: Boolean = false

    private enum class FaceKind { SAVING, HOT, CHARGING, UNKNOWN }

    private fun currentKind(): FaceKind = when {
        unknownState -> FaceKind.UNKNOWN
        charging -> FaceKind.CHARGING
        saving -> FaceKind.SAVING
        else -> FaceKind.HOT
    }

    // ---- Motion state (M3: emphasized easing, medium durations) ----
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var animatorMode = -1 // 0 hot, 1 charging, 2 saving — restart when it changes

    private var targetKind = currentKind()
    private var fromKind: FaceKind? = null
    private var transT = 1f
    private var transAnimator: ValueAnimator? = null
    private var enterT = 1f
    private var enterAnimator: ValueAnimator? = null
    private var hasEntered = false

    // ---- Haptics state (v0.3.5): phase tracking for pulse timing. ----
    private var lastPhase = 0f
    private var pulseLoop = 0
    private var vibrator: Vibrator? = null
    private var vibratorChecked = false

    // ---- Tilt / sloshing state (v0.3.6) ----
    // The accelerometer needs no permission (see class doc). Readings are
    // low-pass filtered into a gravity estimate; the liquid surface angle
    // chases the tilt target through a spring–damper so it lags, overshoots
    // and settles. All angles in radians, clamped to ±0.6 (~35°).
    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var sensorChecked = false
    private var sensorRegistered = false
    private var gravX = 0f
    private var gravY = 9.81f
    private var gravZ = 0f
    private var tiltTarget = 0f
    private var sloshAngle = 0f
    private var sloshVel = 0f
    private var sloshEnergy = 0f // smoothed |motion| — drives extra wave height
    private var lastFrameNanos = 0L

    // ---- Splash state (v0.3.8): energy from shakes / sharp tilts. ----
    // linAccelMag is written by the sensor listener (main thread) and
    // read by the animator callback (also main thread).
    private var linAccelMag = 0f
    private var splashEnergy = 0f // 0 calm … 1 full splash (state-scaled at draw time)
    private var splashClock = 0f // seconds of animation time; drives particle timing
    private var splashHapticCoolNanos = 0L // refractory so splash pulses never machine-gun

    // ---- Particle liquid state (v0.3.9) ----
    // Surface chain: one particle per surface sample point. rippleY is
    // the particle's offset from the analytic wave surface, rippleV its
    // velocity; neighbours are spring-coupled so ripples travel.
    private val rippleY = FloatArray(SURFACE_STEPS + 1)
    private val rippleV = FloatArray(SURFACE_STEPS + 1)
    private val rippleF = FloatArray(SURFACE_STEPS + 1) // scratch forces
    private var particleSeed = 0 // deterministic pseudo-random counter

    /** One droplet particle (pooled; never allocated per frame). */
    private class Drop {
        var active = false
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var r = 1f
    }
    private val drops = Array(DROP_POOL) { Drop() }
    private var dropSpawnAcc = 0f

    /** One bubble particle (pooled): position kept as fractions of the
     *  liquid column so it survives level/size changes gracefully. */
    private class BubbleP {
        var init = false
        var xF = 0f; var yF = 0f; var r = 1f; var speed = 0f; var wob = 0f
    }
    private val bubblePs = Array(BUBBLE_POOL) { BubbleP() }
    private val dropRect = RectF() // scratch for droplet drawing

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Low-pass filter: keeps gravity, drops hand jitter. Kept tiny
            // on purpose — filtering/reduction happens here, physics and
            // drawing happen in the animator callback, never blocking this.
            val a = 0.12f
            gravX += a * (event.values[0] - gravX)
            gravY += a * (event.values[1] - gravY)
            gravZ += a * (event.values[2] - gravZ)
            // Linear acceleration (everything that is not gravity): a
            // shake or a sharp tilt shows up here and feeds the splash.
            val lx = event.values[0] - gravX
            val ly = event.values[1] - gravY
            val lz = event.values[2] - gravZ
            linAccelMag = sqrt(lx * lx + ly * ly + lz * lz)
            // In-plane magnitude factor: ~1 held upright, ~0 lying flat
            // (screen up), where "tilt" has no meaning for the surface.
            val mag = sqrt(gravX * gravX + gravY * gravY) / 9.81f
            val factor = min(1f, mag)
            tiltTarget = (atan2(-gravX.toDouble(), gravY.toDouble()).toFloat() * factor)
                .coerceIn(-0.6f, 0.6f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun ensureSensor() {
        if (sensorChecked) return
        sensorChecked = true
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } catch (e: Exception) {
            sensorManager = null
            accelSensor = null
        }
    }

    /** Registered only while the face is on screen; see lifecycle below. */
    private fun registerSensor() {
        if (sensorRegistered || motionReduced()) return
        ensureSensor()
        val sm = sensorManager ?: return
        val sensor = accelSensor ?: return // no accelerometer: time-based waves only
        try {
            // SENSOR_DELAY_UI (~15 Hz) is far below the 200 Hz platform cap,
            // so no HIGH_SAMPLING_RATE_SENSORS permission is involved.
            if (sm.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)) {
                sensorRegistered = true
            }
        } catch (e: Exception) {
            // Sensor is decoration: never let it break the face.
        }
    }

    private fun unregisterSensor() {
        if (!sensorRegistered) return
        sensorRegistered = false
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (e: Exception) {
            // Already gone: fine.
        }
        // Come to rest level instead of freezing mid-tilt.
        tiltTarget = 0f
    }

    /**
     * Physics step, driven by the animator's real frame interval
     * (vsync) and integrated in ≤8ms sub-steps (v0.3.8): the tilt
     * spring and the splash energy stay smooth and stable even when
     * frames arrive unevenly.
     */
    private fun stepSlosh(nowNanos: Long) {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = nowNanos
            return
        }
        val dt = ((nowNanos - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = nowNanos
        splashClock += dt
        val sub = maxOf(1, (dt / 0.008f).toInt() + 1)
        val h = dt / sub
        repeat(sub) {
            // Under-damped spring (ζ ≈ 0.5): the surface lags the tilt,
            // swings past level once or twice, then settles — like real
            // liquid.
            val accel = SLOSH_STIFFNESS * (tiltTarget - sloshAngle) - SLOSH_DAMPING * sloshVel
            sloshVel += accel * h
            sloshAngle += sloshVel * h
            // Splash excitation: linear acceleration beyond a quiet-hand
            // threshold, plus the surface spring's own speed (a hard
            // swing splashes even without a shake).
            val excite = (
                ((linAccelMag - 2.2f) / 8.5f).coerceIn(0f, 1f) +
                    (abs(sloshVel) / 2.4f).coerceIn(0f, 1f) * 0.7f
                ).coerceIn(0f, 1f)
            splashEnergy = if (excite > splashEnergy) {
                // Fast attack: a shake is felt in the liquid at once.
                splashEnergy + (excite - splashEnergy) * min(1f, h * 14f)
            } else {
                // Slow decay (~2 s): the bashing settles gradually.
                splashEnergy + (excite - splashEnergy) * min(1f, h * 1.4f)
            }
        }
        val e = abs(sloshVel) * 1.1f + abs(tiltTarget - sloshAngle) * 1.4f
        sloshEnergy += (e - sloshEnergy) * min(1f, dt * 3.5f)
        stepParticles(dt)
        maybeSplashHaptic(nowNanos)
    }

    // ---------------- Particle liquid (v0.3.9) ----------------

    /**
     * The wave parameters for the current frame, shared by the drawing
     * code and the particle physics (so droplets land on the same
     * surface that is drawn). Layout: [baseY, tanA, amp1, amp2, amp3,
     * ampR, kf1, kf2, wavePhase].
     */
    private fun computeWave(kind: FaceKind): FloatArray {
        val baseAmp = when (kind) {
            FaceKind.HOT -> dp(2.1f)
            FaceKind.CHARGING -> dp(1.5f)
            FaceKind.SAVING -> dp(1.1f)
            FaceKind.UNKNOWN -> 0f
        }
        val boost = min(dp(3.5f) * sloshEnergy, dp(5.5f))
        val splash = splashLevel()
        val amp1 = baseAmp + boost + dp(5.0f) * splash
        val waveSpeed = when (kind) {
            FaceKind.HOT -> 2.0f
            FaceKind.CHARGING -> 1.2f
            else -> 0.6f
        }
        val innerW = innerRect.width()
        return floatArrayOf(
            innerRect.bottom - innerRect.height() * (levelPercent / 100f),
            tan(sloshAngle.toDouble()).toFloat(),
            amp1,
            amp1 * 0.45f,
            dp(0.55f) + dp(0.8f) * splash,
            dp(3.2f) * splash,
            (2 * Math.PI / (innerW * 0.9f)).toFloat(),
            (2 * Math.PI / (innerW * 0.45f)).toFloat(),
            phase * waveSpeed,
        )
    }

    /** Analytic surface height at x, WITHOUT the particle ripple. */
    private fun analyticY(x: Float, wp: FloatArray): Float =
        surfaceY(x, wp[0], wp[1], wp[2], wp[3], wp[6], wp[7], wp[8], wp[4], wp[5])

    /** Particle ripple offset interpolated at x. */
    private fun rippleAt(x: Float): Float {
        val w = innerRect.width()
        if (w <= 0f) return 0f
        val f = ((x - innerRect.left) / w).coerceIn(0f, 1f) * SURFACE_STEPS
        val i = f.toInt().coerceAtMost(SURFACE_STEPS - 1)
        val t = f - i
        return rippleY[i] * (1f - t) + rippleY[i + 1] * t
    }

    /** Full surface height at x: analytic wave + particle ripple. */
    private fun surfaceAt(x: Float, wp: FloatArray): Float = analyticY(x, wp) + rippleAt(x)

    /** Deterministic pseudo-random in [0,1) from the particle clock. */
    private fun prand(salt: Float): Float {
        particleSeed++
        return frac(particleSeed * 0.6180339f + salt * 7.13f)
    }

    /** Kick the surface chain around x with a velocity impulse. */
    private fun rippleImpulse(x: Float, impulse: Float) {
        val w = innerRect.width()
        if (w <= 0f) return
        val f = ((x - innerRect.left) / w).coerceIn(0f, 1f) * SURFACE_STEPS
        val c = f.toInt()
        for (k in -2..2) {
            val i = c + k
            if (i in 0..SURFACE_STEPS) {
                rippleV[i] += impulse * (1f - abs(k) / 3f)
            }
        }
    }

    /**
     * Particle physics, stepped once per animator frame with the real
     * frame interval (the surface chain sub-steps for stability):
     *  - surface chain: each particle is pulled to the analytic wave
     *    (spring), damped, and coupled to its neighbours, so ripples
     *    travel and interfere; splash energy injects random impulses.
     *  - droplets: spawned by the splash, flown under gravity, bounced
     *    off the inner walls, and landed back into the surface — the
     *    landing momentum becomes a ripple, sometimes with a small
     *    secondary droplet.
     *  - bubbles: rise at their own speeds and respawn at the bottom.
     */
    private fun stepParticles(dt: Float) {
        if (innerRect.width() <= 0f) return
        val kind = targetKind
        val splash = splashLevel()

        // --- Surface chain ---
        if (splash > 0.15f) {
            val kicks = (splash * 3f).toInt() + if (prand(0.31f) < splash) 1 else 0
            repeat(kicks) {
                val i = (prand(0.77f) * SURFACE_STEPS).toInt().coerceIn(0, SURFACE_STEPS)
                rippleV[i] += (prand(0.53f) - 0.5f) * dp(300f) * splash
            }
        }
        val sub = 2
        val h = dt / sub
        repeat(sub) {
            for (i in 0..SURFACE_STEPS) {
                val l = rippleY[maxOf(0, i - 1)]
                val r = rippleY[minOf(SURFACE_STEPS, i + 1)]
                rippleF[i] = -RIPPLE_K * rippleY[i] - RIPPLE_C * rippleV[i] +
                    RIPPLE_COUPLE * (l + r - 2f * rippleY[i])
            }
            val maxRipple = dp(9f)
            for (i in 0..SURFACE_STEPS) {
                rippleV[i] += rippleF[i] * h
                rippleY[i] = (rippleY[i] + rippleV[i] * h).coerceIn(-maxRipple, maxRipple)
            }
        }

        // --- Droplets ---
        val wp = computeWave(kind)
        dropSpawnAcc += splash * dt * 20f
        while (dropSpawnAcc >= 1f) {
            dropSpawnAcc -= 1f
            spawnDrop(wp, splash)
        }
        val grav = dp(1500f)
        val left = innerRect.left; val right = innerRect.right
        val bottom = innerRect.bottom
        for (d in drops) {
            if (!d.active) continue
            d.vy += grav * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            if (d.x < left + d.r) { d.x = left + d.r; d.vx = -d.vx * 0.45f }
            if (d.x > right - d.r) { d.x = right - d.r; d.vx = -d.vx * 0.45f }
            if (d.y > bottom - d.r) { d.active = false; continue }
            if (d.vy > 0f && d.y >= surfaceAt(d.x, wp) - d.r * 0.3f) {
                // Landing: momentum goes back into the surface chain.
                rippleImpulse(d.x, d.vy * 0.38f)
                d.active = false
                if (prand(0.91f) < 0.3f) {
                    spawnDropAt(d.x, surfaceAt(d.x, wp), -d.vy * 0.35f)
                }
            }
        }

        // --- Bubbles ---
        val (count, rate) = when (kind) {
            FaceKind.HOT -> BUBBLE_COUNTS[0] to 1.4f
            FaceKind.CHARGING -> BUBBLE_COUNTS[1] to 0.9f
            FaceKind.SAVING -> BUBBLE_COUNTS[2] to 0.35f
            FaceKind.UNKNOWN -> 0 to 0f
        }
        for (i in 0 until count) {
            val b = bubblePs[i]
            initBubble(b, i)
            b.yF += b.speed * rate * dt * 2.2f
            if (b.yF >= 1f) {
                b.yF = 0f
                b.xF = frac(b.xF + 0.618f)
            }
        }
    }

    private fun initBubble(b: BubbleP, i: Int) {
        if (b.init) return
        b.init = true
        val seed = BUBBLE_SEEDS[i % BUBBLE_SEEDS.size]
        b.xF = frac(seed * 7.31f)
        b.yF = frac(seed * 3.97f)
        b.r = dp(0.8f) + dp(1.6f) * frac(seed * 3.7f)
        b.speed = 0.06f + 0.08f * frac(seed * 5.3f)
        b.wob = seed * 17f
    }

    private fun spawnDrop(wp: FloatArray, splash: Float) {
        val d = drops.firstOrNull { !it.active } ?: return
        val x = innerRect.left + innerRect.width() * (0.08f + 0.84f * prand(0.17f))
        val leapH = dp(5f + 13f * splash * (0.4f + 0.6f * prand(0.43f)))
        val vy = -sqrt(2f * dp(1500f) * leapH)
        spawnDropAt(x, surfaceAt(x, wp), vy, d)
    }

    private fun spawnDropAt(x: Float, y: Float, vy: Float, reuse: Drop? = null) {
        val d = reuse ?: drops.firstOrNull { !it.active } ?: return
        d.active = true
        d.x = x
        d.y = y - dp(1f)
        d.vx = sloshVel * dp(46f) * (prand(0.67f) - 0.5f) * 2f
        d.vy = vy
        d.r = dp(0.8f) + dp(1.3f) * prand(0.29f)
    }

    /** Splash strength for the current face: the cool face stays calm. */
    private fun splashScale(): Float = when (targetKind) {
        FaceKind.SAVING -> 0.45f
        FaceKind.CHARGING -> 0.7f
        FaceKind.HOT -> 1.0f
        FaceKind.UNKNOWN -> 0f
    }

    private fun splashLevel(): Float = splashEnergy * splashScale()

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    /** Night scheme, for the specular rim's edge alphas (v0.3.10).
     *  The theme follows the system night mode, so the configuration
     *  is the same source the resolved colours come from. */
    private val nightMode: Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // ---- State colours from the theme's fixed state roles (fallbacks are
    // the art-direction literals, used only if a theme lacks the attrs).
    private val cool = MaterialColors.getColor(context, R.attr.colorMochiStateCool, Color.rgb(0x02, 0x77, 0xBD))
    private val onCool = MaterialColors.getColor(context, R.attr.colorOnMochiStateCool, Color.WHITE)
    private val coolFill = MaterialColors.getColor(context, R.attr.colorMochiStateCoolContainer, Color.rgb(0xE0, 0xF2, 0xFB))
    private val hot = MaterialColors.getColor(context, R.attr.colorMochiWarning, Color.rgb(0xE6, 0x51, 0x00))
    private val onHot = MaterialColors.getColor(context, R.attr.colorOnMochiWarning, Color.WHITE)
    private val hotFill = MaterialColors.getColor(context, R.attr.colorMochiWarningContainer, Color.rgb(0xFF, 0xED, 0xD9))
    private val neutral = MaterialColors.getColor(
        context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.rgb(0x90, 0x90, 0x90),
    )
    private val neutralFill = MaterialColors.getColor(
        context, com.google.android.material.R.attr.colorSurfaceVariant, Color.rgb(0xF0, 0xF0, 0xF0),
    )
    /** Neutral base for the shell's cavity (see Palette.cavity). */
    private val cavityBase = MaterialColors.getColor(
        context, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.rgb(0xDF, 0xEA, 0xF2),
    )

    // Derived shades (computed once).
    private val hotOuter = ColorUtils.blendARGB(hot, Color.rgb(0xBF, 0x36, 0x00), 0.45f)
    private val hotInner = ColorUtils.blendARGB(hot, Color.WHITE, 0.35f)
    private val hotCore = ColorUtils.blendARGB(hot, Color.rgb(0xFF, 0xF3, 0xB0), 0.75f)
    private val emberColor = ColorUtils.blendARGB(hot, Color.rgb(0xFF, 0xD5, 0x4F), 0.55f)
    private val capLight = ColorUtils.blendARGB(neutral, Color.WHITE, 0.65f)
    private val capMid = ColorUtils.blendARGB(neutral, Color.WHITE, 0.20f)
    private val capDark = ColorUtils.blendARGB(neutral, Color.BLACK, 0.28f)

    /** One state's palette: everything onDraw needs, shaders built per size. */
    private inner class Palette(val accent: Int, val container: Int, val onAccent: Int) {
        /**
         * Interior of the shell (the unfilled part). A neutral surface
         * with only a whisper (10%) of the state colour tinted in, in both
         * themes (v0.3.5: the state container lightened with white turned
         * a muddy brown-grey on the dark theme).
         */
        val cavity: Int = ColorUtils.blendARGB(cavityBase, accent, 0.10f)
        var glass: Shader? = null
        var liquid: Shader? = null
        var outline: Shader? = null
        var aura: Shader? = null
        var bolt: Shader? = null
        var cavityShade: Shader? = null
        fun build(top: Float, bottom: Float, innerTop: Float, innerBottom: Float, cx: Float, cy: Float, auraR: Float) {
            // Glass vessel (v0.3.10): the shell is a translucent
            // role-tint glass, not an opaque tint — the same weight
            // family as the stage cards' tint glass (94%→88%), with a
            // gentle top-to-bottom step. What shows through is the
            // aura and the foundation's light, strongest at the rim
            // ring between the outline and the (opaque) cavity.
            glass = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(container, Color.WHITE, 0.50f), 240),
                    ColorUtils.setAlphaComponent(container, 230),
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(container, accent, 0.15f), 220),
                ),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
            // Interior depth shading (v0.3.10): a whisper of shadow
            // under the vessel's top inside edge and a brightening at
            // its floor, so the cavity reads as the inside of a glass
            // vessel rather than a flat panel. Drawn over the cavity,
            // under the liquid.
            cavityShade = LinearGradient(
                0f, innerTop, 0f, innerBottom,
                intArrayOf(
                    ColorUtils.setAlphaComponent(Color.BLACK, 20),
                    ColorUtils.setAlphaComponent(Color.BLACK, 0),
                    ColorUtils.setAlphaComponent(Color.WHITE, 0),
                    ColorUtils.setAlphaComponent(Color.WHITE, 12),
                ),
                floatArrayOf(0f, 0.30f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
            // Liquid (v0.3.6): anchored to the inner height, so whatever
            // part of it is visible reads light near the surface and deep
            // at the bottom — depth, not a flat fill.
            liquid = LinearGradient(
                0f, innerTop, 0f, innerBottom,
                intArrayOf(
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.50f),
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.16f),
                    accent,
                    ColorUtils.blendARGB(accent, Color.BLACK, 0.20f),
                ),
                floatArrayOf(0f, 0.35f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
            outline = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(
                    // Top stop lightened in v0.3.10 (0.30 -> 0.45): the
                    // rim catches light from above like a glass edge.
                    // The mid stop stays the pure state colour.
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.45f),
                    accent,
                    ColorUtils.blendARGB(accent, Color.BLACK, 0.22f),
                ),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            aura = RadialGradient(
                cx, cy, auraR,
                intArrayOf(ColorUtils.setAlphaComponent(accent, 100), ColorUtils.setAlphaComponent(accent, 0)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            bolt = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(Color.WHITE, ColorUtils.blendARGB(onAccent, Color.WHITE, 0.55f), onAccent),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
        }
    }

    private val coolPalette = Palette(cool, coolFill, onCool)
    private val hotPalette = Palette(hot, hotFill, onHot)
    private val neutralPalette = Palette(neutral, neutralFill, neutralFill)

    private fun paletteFor(kind: FaceKind): Palette = when (kind) {
        FaceKind.SAVING, FaceKind.CHARGING -> coolPalette
        FaceKind.HOT -> hotPalette
        FaceKind.UNKNOWN -> neutralPalette
    }

    // ---- Paints (reused).
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cavityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val liquidEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    // Glass vessel (v0.3.10): interior depth shading + the specular rim.
    private val cavityShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val capLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE
    }
    private val boltFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boltStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val boltGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val crystalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val crystalHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }

    // ---- Paths (reused; rewound each use, never allocated in onDraw).
    private val boltPath = Path()
    private val flamePath = Path()
    private val wavePath = Path()
    private val edgePath = Path()
    private val sparklePath = Path()
    private val flakePath = Path()
    private val clipPath = Path()
    private val glossPath = Path()

    // ---- Geometry cached in onSizeChanged.
    private val bodyRect = RectF()
    private val innerRect = RectF()
    private val capRect = RectF()
    private val shadowRect = RectF()
    private val layerRect = RectF()
    private val rimRect = RectF() // scratch for the specular rim (v0.3.10)
    private var capShader: Shader? = null
    private var shadowShader: Shader? = null
    private var rimShader: Shader? = null // specular edge gradient (v0.3.10)
    private var glossShader: Shader? = null // soft reflection band (v0.3.10)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Upright battery (v0.3.6, user's instruction): width : height
        // ≈ 1 : 1.55, cap on TOP centre like a standing dry cell. Headroom
        // above the body is reserved for flames / flakes, with the cap in
        // the middle of it; a ground shadow sits below.
        val topZone = dp(58f) // flames + cap zone
        val bottomZone = dp(20f) // ground shadow
        val availH = (h - topZone - bottomZone).coerceAtLeast(dp(120f))
        var bodyH = min(availH, dp(272f))
        var bodyW = bodyH / 1.55f
        val maxBodyW = w * 0.60f
        if (bodyW > maxBodyW) {
            bodyW = maxBodyW
            bodyH = bodyW * 1.55f
        }
        val left = (w - bodyW) / 2f
        val top = topZone + (availH - bodyH) * 0.35f
        bodyRect.set(left, top, left + bodyW, top + bodyH)
        val inset = dp(7f)
        innerRect.set(left + inset, top + inset, left + bodyW - inset, top + bodyH - inset)
        // Cap: centred on the top edge, overlapping it slightly so it reads
        // as one piece with the body. Metal, sized in proportion (~44% of
        // the body width, 13dp tall).
        val capW = bodyW * 0.44f
        val capH = dp(13f)
        capRect.set(
            bodyRect.centerX() - capW / 2f, top - capH + dp(2f),
            bodyRect.centerX() + capW / 2f, top + dp(2f),
        )
        shadowRect.set(left + bodyW * 0.08f, bodyRect.bottom + dp(5f), left + bodyW * 0.92f, bodyRect.bottom + dp(16f))
        layerRect.set(0f, 0f, w.toFloat(), h.toFloat())
        val cx = bodyRect.centerX(); val cy = bodyRect.centerY()
        coolPalette.build(top, bodyRect.bottom, innerRect.top, innerRect.bottom, cx, cy, bodyH * 0.62f)
        hotPalette.build(top, bodyRect.bottom, innerRect.top, innerRect.bottom, cx, cy, bodyH * 0.62f)
        neutralPalette.build(top, bodyRect.bottom, innerRect.top, innerRect.bottom, cx, cy, bodyH * 0.62f)
        capShader = LinearGradient(
            capRect.left, 0f, capRect.right, 0f,
            intArrayOf(capDark, capMid, capLight, capMid, capDark),
            floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f), Shader.TileMode.CLAMP,
        )
        shadowShader = RadialGradient(
            shadowRect.centerX(), shadowRect.centerY(), shadowRect.width() / 2f,
            intArrayOf(ColorUtils.setAlphaComponent(Color.BLACK, 46), ColorUtils.setAlphaComponent(Color.BLACK, 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        // Specular rim (v0.3.10): the Simple Glass edge, with the
        // skill's exact alphas — bright at the top (white 75% light /
        // 38% dark), faint at the bottom (15% / 6%) — the same numbers
        // GlassDrawable strokes every card and band with, so the face
        // and the surfaces around it speak one glass language.
        rimShader = LinearGradient(
            0f, bodyRect.top, 0f, bodyRect.bottom,
            ColorUtils.setAlphaComponent(Color.WHITE, if (nightMode) 97 else 191),
            ColorUtils.setAlphaComponent(Color.WHITE, if (nightMode) 15 else 38),
            Shader.TileMode.CLAMP,
        )
        // Reflection band (v0.3.10): a soft horizontal melt (white
        // 36 -> 0) across the band's slant, replacing the hard-edged
        // flat-alpha band — light on glass fades, it does not stop.
        glossShader = LinearGradient(
            bodyRect.left, 0f, bodyRect.left + bodyRect.width() * 0.24f, 0f,
            ColorUtils.setAlphaComponent(Color.WHITE, 36),
            ColorUtils.setAlphaComponent(Color.WHITE, 0),
            Shader.TileMode.CLAMP,
        )
    }

    /** Spoken description of the face itself (R9). */
    private fun updateDescription() {
        contentDescription = when {
            unknownState -> "バッテリー。状態を確認中です"
            charging -> "充電中のバッテリー。残量 $levelPercent パーセント"
            saving -> "ひんやりしたバッテリー。節電できています。残量 $levelPercent パーセント"
            else -> "燃えさかるバッテリー。節電できていません。残量 $levelPercent パーセント"
        }
    }

    /** True when the user asked the OS to reduce motion (animator scale 0). */
    private fun motionReduced(): Boolean = try {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    } catch (e: Exception) {
        false
    }

    /** M3 emphasized easing: cubic-bezier(0.2, 0.0, 0, 1.0). */
    private fun emphasized() = PathInterpolator(0.2f, 0f, 0f, 1f)

    private fun onStateChanged() {
        val newKind = currentKind()
        if (newKind != targetKind) {
            val old = targetKind
            targetKind = newKind
            if (isAttachedToWindow && hasEntered && !motionReduced()) {
                // Crossfade + slight scale between faces (M3 medium, emphasized).
                fromKind = old
                transT = 0f
                transAnimator?.cancel()
                transAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 350L
                    interpolator = emphasized()
                    addUpdateListener { transT = it.animatedValue as Float; invalidate() }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            fromKind = null; transT = 1f; invalidate()
                        }
                    })
                    start()
                }
            } else {
                fromKind = null; transT = 1f
            }
        }
        updateAnimator()
        updateDescription()
        invalidate()
    }

    private fun updateAnimator() {
        val mode = when (targetKind) {
            FaceKind.UNKNOWN -> -1
            FaceKind.HOT -> 0
            FaceKind.CHARGING -> 1
            FaceKind.SAVING -> 2
        }
        if (mode >= 0 && isAttachedToWindow && !motionReduced()) {
            if (animator == null || animatorMode != mode) {
                animator?.cancel()
                animatorMode = mode
                lastPhase = 0f
                pulseLoop = 0
                lastFrameNanos = 0L
                animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                    duration = when (mode) {
                        0 -> 1500L // flames: lively
                        1 -> 2600L // charging pulse: slow
                        else -> 6800L // cool face: one slow breath + sparkle twinkle
                    }
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { anim ->
                        val newPhase = anim.animatedValue as Float
                        maybeHapticPulse(newPhase)
                        phase = newPhase
                        stepSlosh(System.nanoTime())
                        invalidate()
                    }
                    start()
                }
            }
        } else {
            animator?.cancel(); animator = null; animatorMode = -1
        }
    }

    // ---------------- Haptics (v0.3.5, user request) ----------------
    //
    // One very weak pulse per animation loop, fired at the moment the
    // motion peaks. The same phase drives the liquid's surface waves and
    // the flames' growth, so the pulse lands on the liquid's biggest
    // sway / the flame's grow peak:
    //   flames (mode 0, ~1.5 s): at the flame "grow" peak (phase = π/4),
    //     with a small per-loop jitter so the rhythm never feels mechanical,
    //     and a slight per-loop amplitude variation for the same reason.
    //   charging (mode 1, ~2.6 s): at the glow pulse peak (phase = π/2).
    //   cool (mode 2, ~6.8 s): at the breath peak (phase = π), weakest.
    // Pulses only ever originate from the animator's update callback, so
    // they stop on their own when the animator stops: view detached (tab
    // change), motion reduced (animator never starts), or frames paused.
    // fireHapticPulse additionally re-checks visibility at fire time.

    private fun maybeHapticPulse(newPhase: Float) {
        if (newPhase < lastPhase) pulseLoop++ // wrapped into a new loop
        val threshold = pulseThreshold()
        if (threshold >= 0f && lastPhase < threshold && newPhase >= threshold) {
            fireHapticPulse()
        }
        lastPhase = newPhase
    }

    private fun pulseThreshold(): Float = when (animatorMode) {
        0 -> (Math.PI / 4 + (frac(pulseLoop * 0.618f) - 0.5f) * 0.6f).toFloat()
        1 -> (Math.PI / 2).toFloat()
        2 -> Math.PI.toFloat()
        else -> -1f
    }

    /**
     * Every haptic stop condition in one place (v0.3.5 spec): the rule
     * switch, reduced motion, the face actually being on screen
     * (attached, window visible, shown), a vibrator that exists, and
     * the OS haptic-feedback setting. Returns the vibrator when a
     * pulse may fire, null when it must stay silent.
     */
    private fun hapticsAllowed(): Vibrator? {
        if (!hapticsEnabled || motionReduced()) return null
        if (!isAttachedToWindow || windowVisibility != View.VISIBLE || !isShown) return null
        val vib = vibrator() ?: return null
        return try {
            if (!vib.hasVibrator()) return null
            // Respect the OS haptic-feedback setting: stay silent when off.
            val osHapticsOn = try {
                android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    1,
                ) == 1
            } catch (e: Exception) {
                true
            }
            if (osHapticsOn) vib else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fireHapticPulse() {
        val vib = hapticsAllowed() ?: return
        try {
            // Deliberately low amplitudes (of 255). Devices without
            // amplitude control get a much shorter duration instead, so the
            // pulse stays a whisper either way.
            val ampControl = vib.hasAmplitudeControl()
            val (durationMs, amplitude) = when (animatorMode) {
                0 -> if (ampControl) {
                    32L to 44 + (frac(pulseLoop * 0.618f) * 12).toInt() // 44..56
                } else {
                    18L to VibrationEffect.DEFAULT_AMPLITUDE
                }
                1 -> if (ampControl) 30L to 30 else 16L to VibrationEffect.DEFAULT_AMPLITUDE
                else -> if (ampControl) 24L to 20 else 12L to VibrationEffect.DEFAULT_AMPLITUDE
            }
            vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } catch (e: Exception) {
            // Haptics are decoration: never let them break the face.
        }
    }

    /**
     * Splash-synced haptics (v0.3.8): when the splash energy crosses
     * its peak band, one pulse lands on the liquid's biggest sway /
     * bounce-back moment. Amplitude scales with the (state-scaled)
     * splash energy and stays in the same whisper band as the loop
     * pulses (≤76 of 255, vs the flame loop's 44–56); a ~450ms
     * refractory keeps shakes from turning into a buzz-saw. Devices
     * without amplitude control get a short fixed pulse instead.
     */
    private fun maybeSplashHaptic(nowNanos: Long) {
        val level = splashLevel()
        if (level < 0.5f) return
        if (nowNanos - splashHapticCoolNanos < 450_000_000L) return
        val vib = hapticsAllowed() ?: return
        splashHapticCoolNanos = nowNanos
        try {
            if (vib.hasAmplitudeControl()) {
                val amp = (34f + 42f * level).toInt().coerceIn(34, 76)
                vib.vibrate(VibrationEffect.createOneShot(36L, amp))
            } else {
                vib.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // Haptics are decoration: never let them break the face.
        }
    }

    private fun vibrator(): Vibrator? {
        if (!vibratorChecked) {
            vibratorChecked = true
            vibrator = try {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } catch (e: Exception) {
                null
            }
        }
        return vibrator
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimator()
        registerSensor()
        if (!hasEntered) {
            hasEntered = true
            if (!motionReduced()) {
                // Gentle entrance: fade + slight scale (M3 short/medium, emphasized).
                enterT = 0f
                enterAnimator?.cancel()
                enterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 250L
                    interpolator = emphasized()
                    addUpdateListener { enterT = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else {
                enterT = 1f
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel(); animator = null; animatorMode = -1
        transAnimator?.cancel(); enterAnimator?.cancel()
        // Leaving the screen stops the tilt sensor and haptics immediately.
        unregisterSensor()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // Nothing to stop, or no vibrator: fine.
        }
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            registerSensor()
        } else {
            // Backgrounded: stop sampling (and haptics re-check this too).
            unregisterSensor()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = bodyRect.centerX()
        val cy = bodyRect.centerY()

        // Entrance transform (whole face).
        if (enterT < 1f) {
            val s = 0.98f + 0.02f * enterT
            canvas.save()
            canvas.scale(s, s, cx, cy)
            canvas.saveLayerAlpha(layerRect, (140 + 115 * enterT).toInt(), Canvas.ALL_SAVE_FLAG)
            drawFace(canvas, targetKind)
            canvas.restore()
            canvas.restore()
            return
        }

        val from = fromKind
        if (from != null && transT < 1f) {
            // Outgoing face fades out; incoming face fades in while growing
            // from 97% — a crossfade with a slight scale (M3 state change).
            canvas.saveLayerAlpha(layerRect, ((1f - transT) * 255).toInt(), Canvas.ALL_SAVE_FLAG)
            drawFace(canvas, from)
            canvas.restore()
            canvas.save()
            val s = 0.97f + 0.03f * transT
            canvas.scale(s, s, cx, cy)
            canvas.saveLayerAlpha(layerRect, (transT * 255).toInt(), Canvas.ALL_SAVE_FLAG)
            drawFace(canvas, targetKind)
            canvas.restore()
            canvas.restore()
        } else {
            drawFace(canvas, targetKind)
        }
    }

    private fun drawFace(canvas: Canvas, kind: FaceKind) {
        val palette = paletteFor(kind)
        val cx = bodyRect.centerX()
        val cy = bodyRect.centerY()
        val bodyTop = bodyRect.top

        // Aura + ground shadow: the battery floats a little. On the cool
        // face the aura breathes slowly (one breath per animator loop:
        // sin(phase/2) runs 0→1→0 over the loop, so it never jumps).
        auraPaint.shader = palette.aura
        val scaleY = (bodyRect.height() / bodyRect.width()) * 0.80f
        if (kind == FaceKind.SAVING) {
            val breath = sin(phase * 0.5).toFloat() // 0..1..0 per loop
            val r = bodyRect.width() * 0.92f * (0.97f + 0.05f * breath)
            auraPaint.alpha = (215 + 40 * breath).toInt()
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(1f, scaleY)
            canvas.translate(-cx, -cy)
            canvas.drawCircle(cx, cy, r, auraPaint)
            canvas.restore()
        } else {
            auraPaint.alpha = 255
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(1f, scaleY)
            canvas.translate(-cx, -cy)
            canvas.drawCircle(cx, cy, bodyRect.width() * 0.92f, auraPaint)
            canvas.restore()
        }
        auraPaint.shader = null
        auraPaint.alpha = 255
        shadowPaint.shader = shadowShader
        canvas.drawOval(shadowRect, shadowPaint)
        shadowPaint.shader = null

        // Flames sit behind the body so their bases tuck behind its top
        // edge, standing up around the cap.
        if (kind == FaceKind.HOT) {
            drawFlames(canvas, cx, bodyTop)
        }

        drawBody(canvas, palette, kind)

        when (kind) {
            FaceKind.UNKNOWN -> Unit // plain grey battery, no marks
            FaceKind.CHARGING -> {
                drawCrystals(canvas, palette.accent)
                drawBolt(canvas, palette)
            }
            FaceKind.SAVING -> drawCrystals(canvas, palette.accent)
            FaceKind.HOT -> Unit // flames already drawn behind the body
        }
    }

    /**
     * Liquid surface height (px) at x: tilt plane + two travelling
     * waves + a fine third wave (smoothness, v0.3.8) + a counter-wave
     * that only exists while splashing — the reflection bouncing back
     * off the far inner wall, phase-reversed against the main wave.
     */
    private fun surfaceY(
        x: Float, baseY: Float, tanA: Float,
        amp1: Float, amp2: Float, kf1: Float, kf2: Float, wavePhase: Float,
        amp3: Float, ampR: Float,
    ): Float {
        val dx = x - innerRect.left
        return baseY - (x - bodyRect.centerX()) * tanA +
            amp1 * sin((dx * kf1 - wavePhase).toDouble()).toFloat() +
            amp2 * sin((dx * kf2 + wavePhase * 0.63f + 1.7f).toDouble()).toFloat() +
            amp3 * sin((dx * kf1 * 2.63f - wavePhase * 1.71f + 0.6f).toDouble()).toFloat() +
            ampR * sin((dx * kf1 + wavePhase * 1.31f + 2.4f).toDouble()).toFloat()
    }

    // Surface sample buffers (fields, reused every frame — no onDraw
    // allocation). SURFACE_STEPS + 1 points across the inner width.
    private val surfX = FloatArray(SURFACE_STEPS + 1)
    private val surfY = FloatArray(SURFACE_STEPS + 1)

    /** Appends the smoothed surface curve (midpoint quadratics) to a path. */
    private fun traceSurface(path: Path) {
        var px = surfX[0]; var py = surfY[0]
        for (i in 1..SURFACE_STEPS) {
            val mx = (px + surfX[i]) / 2f; val my = (py + surfY[i]) / 2f
            path.quadTo(px, py, mx, my)
            px = surfX[i]; py = surfY[i]
        }
        path.lineTo(px, py)
    }

    /**
     * Droplet particles (v0.3.9, replacing the v0.3.8 formula-timed
     * droplets): each active particle from the pool is drawn at its
     * simulated position, stretched vertically by its speed like a
     * real droplet in flight. Drawn inside the liquid's clip, so a
     * droplet can never leave the shell. When the splash energy is
     * gone no droplets are active and nothing is drawn; when motion
     * is reduced the physics clock stops and the pool stays empty.
     */
    private fun drawDropParticles(canvas: Canvas, palette: Palette) {
        bubblePaint.color = ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.40f)
        for (d in drops) {
            if (!d.active) continue
            val stretch = 1f + min(abs(d.vy) / dp(900f), 0.55f)
            val wHalf = d.r / sqrt(stretch)
            val hHalf = d.r * sqrt(stretch)
            dropRect.set(d.x - wHalf, d.y - hHalf, d.x + wHalf, d.y + hHalf)
            bubblePaint.alpha = 205
            canvas.drawOval(dropRect, bubblePaint)
        }
        bubblePaint.alpha = 255
    }

    private fun drawBody(canvas: Canvas, palette: Palette, kind: FaceKind) {
        val radius = dp(22f)
        // Glass body.
        glassPaint.shader = palette.glass
        canvas.drawRoundRect(bodyRect, radius, radius, glassPaint)
        glassPaint.shader = null

        // Pale cavity: the unfilled part of the shell stays a visible
        // neutral surface in both themes, so the level can be read.
        cavityPaint.color = palette.cavity
        canvas.drawRoundRect(innerRect, dp(16f), dp(16f), cavityPaint)
        // Interior depth shading (v0.3.10): the inside of the vessel —
        // a whisper of shadow at the top inside edge, light at the
        // floor. Over the cavity, under the liquid.
        cavityShadePaint.shader = palette.cavityShade
        canvas.drawRoundRect(innerRect, dp(16f), dp(16f), cavityShadePaint)
        cavityShadePaint.shader = null

        // Liquid (v0.3.6; smoothed + splashing in v0.3.8): fills from
        // the BOTTOM by level. Its surface is a plane tilted by the
        // sloshing angle plus travelling waves; the wave height grows
        // with the sloshing energy, so a tilt makes the surface visibly
        // slosh and then calm down — and a shake makes it splash.
        val fillH = innerRect.height() * (levelPercent / 100f)
        if (fillH > 0.5f) {
            clipPath.reset()
            clipPath.addRoundRect(innerRect, dp(16f), dp(16f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            // Wave parameters are shared with the particle physics
            // (computeWave), so droplets land on the surface that is
            // actually drawn.
            val wp = computeWave(kind)
            val baseY = wp[0]; val tanA = wp[1]
            val amp1 = wp[2]; val amp2 = wp[3]; val amp3 = wp[4]; val ampR = wp[5]
            val kf1 = wp[6]; val kf2 = wp[7]; val wavePhase = wp[8]
            val innerW = innerRect.width()
            // 44 samples joined by midpoint quadratics: the surface is
            // a smooth curve, never a faceted polyline (v0.3.8). Each
            // sample also carries its surface-chain particle's ripple
            // offset (v0.3.9), so the drawn surface is the particles'
            // surface. Buffers are fields — no onDraw allocation.
            val steps = SURFACE_STEPS
            for (i in 0..steps) {
                surfX[i] = innerRect.left + innerW * (i / steps.toFloat())
                surfY[i] = surfaceY(surfX[i], baseY, tanA, amp1, amp2, kf1, kf2, wavePhase, amp3, ampR) +
                    rippleY[i]
            }
            // Liquid region: up the left edge, along the smoothed
            // surface, down the right edge, closed along the bottom.
            wavePath.reset()
            wavePath.moveTo(innerRect.left - dp(2f), innerRect.bottom + dp(2f))
            wavePath.lineTo(innerRect.left - dp(2f), surfY[0])
            traceSurface(wavePath)
            edgePath.reset()
            edgePath.moveTo(surfX[0], surfY[0])
            traceSurface(edgePath)
            wavePath.lineTo(innerRect.right + dp(2f), surfY[steps])
            wavePath.lineTo(innerRect.right + dp(2f), innerRect.bottom + dp(2f))
            wavePath.close()
            liquidPaint.shader = palette.liquid
            canvas.drawPath(wavePath, liquidPaint)
            liquidPaint.shader = null
            // Droplet particles leap from the surface and fall back,
            // inside this same clip — never outside the shell.
            drawDropParticles(canvas, palette)
            // Bubbles rise through the liquid, under its bright surface.
            drawBubbles(canvas, palette, kind, baseY, fillH)
            // Bright meniscus line along the surface — the clearest line
            // inside the shell.
            liquidEdgePaint.color = ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.65f)
            liquidEdgePaint.alpha = 235
            liquidEdgePaint.strokeWidth = dp(2.3f)
            canvas.drawPath(edgePath, liquidEdgePaint)
            canvas.restore()
        }

        // Glass gloss over the whole shell: one soft reflection band
        // hugging the left edge, clipped to the body outline (kept
        // quiet since v0.3.5 so it never fights the liquid). v0.3.10:
        // the band's light now melts horizontally into the glass via
        // a gradient shader, and the old flat top strip is gone — the
        // specular rim below does that job in the shared glass
        // language.
        clipPath.reset()
        clipPath.addRoundRect(bodyRect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        glossPaint.shader = glossShader
        val bh = bodyRect.height()
        glossPath.reset()
        glossPath.moveTo(bodyRect.left, bodyRect.top - bh * 0.04f)
        glossPath.lineTo(bodyRect.left + bodyRect.width() * 0.20f, bodyRect.top - bh * 0.04f)
        glossPath.lineTo(bodyRect.left + bodyRect.width() * 0.13f, bodyRect.bottom + bh * 0.04f)
        glossPath.lineTo(bodyRect.left, bodyRect.bottom + bh * 0.04f)
        glossPath.close()
        canvas.drawPath(glossPath, glossPaint)
        glossPaint.shader = null
        canvas.restore()

        // Terminal cap on top centre: metal gradient + a highlight line
        // down its middle. Drawn over the body's top edge so the two read
        // as one piece.
        capPaint.shader = capShader
        canvas.drawRoundRect(capRect, dp(4f), dp(4f), capPaint)
        capPaint.shader = null
        capLinePaint.alpha = 150
        capLinePaint.strokeWidth = dp(1.2f)
        canvas.drawLine(
            capRect.centerX(), capRect.top + dp(2.5f),
            capRect.centerX(), capRect.bottom - dp(1f), capLinePaint,
        )

        // Outline over everything, gradient-shaded.
        outlinePaint.shader = palette.outline
        outlinePaint.strokeWidth = dp(4.5f)
        canvas.drawRoundRect(bodyRect, radius, radius, outlinePaint)
        outlinePaint.shader = null

        // Specular rim (v0.3.10): a 1dp stroke just inside the
        // outline, bright at the top and faint at the bottom — the
        // Simple Glass edge, in the same numbers as every GlassDrawable
        // surface in the app. This is what makes the shell read as a
        // glass vessel rather than a coloured frame.
        rimPaint.shader = rimShader
        rimPaint.strokeWidth = dp(1f)
        val rimInset = dp(2.8f)
        rimRect.set(bodyRect)
        rimRect.inset(rimInset, rimInset)
        canvas.drawRoundRect(rimRect, (radius - rimInset).coerceAtLeast(0f), (radius - rimInset).coerceAtLeast(0f), rimPaint)
        rimPaint.shader = null
    }

    /**
     * Bubble particles rising through the liquid (particles since
     * v0.3.9; the motion itself dates to v0.3.6). Almost none when
     * saving (the liquid is nearly still), livelier when burning, a
     * steady rise when charging — "filling up". Each particle keeps
     * its own height / speed / wobble and respawns at the bottom;
     * frozen mid-rise when the animator is off (reduced motion).
     */
    private fun drawBubbles(canvas: Canvas, palette: Palette, kind: FaceKind, baseY: Float, fillH: Float) {
        val (count, alpha) = when (kind) {
            FaceKind.HOT -> BUBBLE_COUNTS[0] to 130
            FaceKind.CHARGING -> BUBBLE_COUNTS[1] to 120
            FaceKind.SAVING -> BUBBLE_COUNTS[2] to 70
            FaceKind.UNKNOWN -> return
        }
        if (count == 0 || fillH < dp(24f)) return
        bubblePaint.color = ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.55f)
        val bottom = innerRect.bottom - dp(3f)
        val span = fillH - dp(12f)
        if (span < dp(8f)) return
        for (i in 0 until count) {
            val b = bubblePs[i]
            initBubble(b, i)
            val x = innerRect.left + innerRect.width() * b.xF +
                sin(splashClock * 1.3f + b.wob).toFloat() * dp(1.5f)
            val y = bottom - b.yF * span
            val fade = sin(Math.PI * min(b.yF * 1.15f, 1f)).toFloat()
            bubblePaint.alpha = (alpha * fade).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, b.r, bubblePaint)
        }
        bubblePaint.alpha = 255
    }

    // ---------------- Charging bolt (only while charging) ----------------

    private fun drawBolt(canvas: Canvas, palette: Palette) {
        val cx = bodyRect.centerX()
        val cy = bodyRect.centerY()
        val bodyH = bodyRect.height()
        // Soft pulsing glow behind the bolt (frozen mid-glow when still).
        val pulse = 0.5f + 0.5f * sin(phase.toDouble()).toFloat()
        boltGlowPaint.color = ColorUtils.setAlphaComponent(palette.accent, (30 + 40 * pulse).toInt())
        canvas.drawCircle(cx, cy, bodyH * 0.20f + dp(4f) * pulse, boltGlowPaint)
        buildBoltPath(cx, cy, bodyH)
        boltFillPaint.shader = palette.bolt
        canvas.drawPath(boltPath, boltFillPaint)
        boltFillPaint.shader = null
        boltStrokePaint.color = ColorUtils.blendARGB(palette.accent, Color.BLACK, 0.28f)
        boltStrokePaint.strokeWidth = dp(1.1f)
        canvas.drawPath(boltPath, boltStrokePaint)
        // A whisper of light on the bolt's upper-left edge.
        canvas.save()
        canvas.translate(-dp(0.9f), -dp(0.9f))
        boltStrokePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 110)
        boltStrokePaint.strokeWidth = dp(0.8f)
        canvas.drawPath(boltPath, boltStrokePaint)
        canvas.restore()
    }

    private fun buildBoltPath(cx: Float, cy: Float, bodyH: Float) {
        // Bolt proportions follow the body's height now that it stands
        // upright: a tall bolt centred in the shell, over the liquid.
        val boltH = bodyH * 0.36f
        boltPath.reset()
        boltPath.moveTo(cx + dp(4.5f), cy - boltH * 0.5f)
        boltPath.lineTo(cx - dp(9f), cy + dp(3.5f))
        boltPath.lineTo(cx - dp(1f), cy + dp(3.5f))
        boltPath.lineTo(cx - dp(4.5f), cy + boltH * 0.5f)
        boltPath.lineTo(cx + dp(9f), cy - dp(3.5f))
        boltPath.lineTo(cx + dp(1f), cy - dp(3.5f))
        boltPath.close()
    }

    // ---------------- Cool face: snowflakes + sparkles ----------------

    private fun drawCrystals(canvas: Canvas, color: Int) {
        val l = bodyRect.left; val t = bodyRect.top
        val bw = bodyRect.width(); val bh = bodyRect.height()
        // Balanced around the upright body — beside the cap, down both
        // sides, two faint inside the glass — never bunched on top:
        snowflake(canvas, l - bw * 0.16f, t + bh * 0.02f, dp(13f), color, 235)
        snowflake(canvas, l + bw * 1.16f, t + bh * 0.02f, dp(11f), color, 235)
        snowflake(canvas, l - bw * 0.20f, t + bh * 0.42f, dp(8.5f), color, 200)
        snowflake(canvas, l + bw * 1.20f, t + bh * 0.55f, dp(7.5f), color, 200)
        snowflake(canvas, l + bw * 0.26f, t + bh * 0.22f, dp(7f), color, 110)
        snowflake(canvas, l + bw * 0.74f, t + bh * 0.68f, dp(6f), color, 110)
        drawSparkles(canvas)
    }

    /** Six arms, two branch chevrons per arm, small ring at the centre. */
    private fun snowflake(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        flakePath.reset()
        for (i in 0 until 6) {
            val a = Math.toRadians((i * 60 - 90).toDouble())
            val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
            flakePath.moveTo(cx, cy)
            flakePath.lineTo(cx + ux * r, cy + uy * r)
            for (b in BRANCHES.indices step 2) {
                val at = BRANCHES[b]; val lenF = BRANCHES[b + 1]
                val bx = cx + ux * r * at; val by = cy + uy * r * at
                for (s in -1..1 step 2) {
                    val ba = a + s * Math.toRadians(42.0)
                    flakePath.moveTo(bx, by)
                    flakePath.lineTo(
                        bx + cos(ba).toFloat() * r * lenF,
                        by + sin(ba).toFloat() * r * lenF,
                    )
                }
            }
        }
        crystalHaloPaint.color = ColorUtils.setAlphaComponent(color, (46 * alpha / 255).coerceIn(0, 255))
        crystalHaloPaint.strokeWidth = dp(3.4f)
        canvas.drawPath(flakePath, crystalHaloPaint)
        crystalPaint.color = ColorUtils.setAlphaComponent(color, alpha)
        crystalPaint.strokeWidth = dp(1.7f)
        canvas.drawPath(flakePath, crystalPaint)
        canvas.drawCircle(cx, cy, r * 0.16f, crystalPaint)
    }

    private fun drawSparkles(canvas: Canvas) {
        // Fixed spots around the body, deterministic twinkle offsets.
        // Alpha only — no motion of position; stillness stays the lead.
        val l = bodyRect.left; val t = bodyRect.top
        val bw = bodyRect.width(); val bh = bodyRect.height()
        val spots = arrayOf(
            floatArrayOf(l - dp(14f), t + bh * 0.24f, 6.0f, 0.0f),
            floatArrayOf(l + bw + dp(14f), t + bh * 0.30f, 5.0f, 2.1f),
            floatArrayOf(l + bw * 0.5f, t - dp(24f), 4.2f, 4.0f),
            floatArrayOf(l - dp(10f), t + bh * 0.72f, 5.2f, 1.2f),
            floatArrayOf(l + bw + dp(10f), t + bh * 0.82f, 4.6f, 3.2f),
        )
        for ((cx, cy, rDp, off) in spots) {
            val tw = 0.5f + 0.5f * sin(phase + off).toFloat()
            sparklePaint.alpha = (40 + 120 * tw).toInt()
            val r = dp(rDp) * (0.85f + 0.15f * tw)
            sparklePath.reset()
            sparklePath.moveTo(cx, cy - r)
            sparklePath.quadTo(cx, cy, cx + r, cy)
            sparklePath.quadTo(cx, cy, cx, cy + r)
            sparklePath.quadTo(cx, cy, cx - r, cy)
            sparklePath.quadTo(cx, cy, cx, cy - r)
            sparklePath.close()
            canvas.drawPath(sparklePath, sparklePaint)
        }
        sparklePaint.alpha = 255
    }

    // ---------------- Hot face: layered flames + embers ----------------

    private fun drawFlames(canvas: Canvas, cx: Float, bodyTop: Float) {
        val baseY = bodyTop + dp(7f)
        val bw = bodyRect.width()
        drawEmbers(canvas, cx, baseY)
        // Flames stand from the top of the upright body, around the cap:
        // five flames across the body's width on a centre-high envelope,
        // over a low wide bed. Heights are capped by the headroom above
        // the body so nothing is ever clipped by the view's top edge.
        val maxH = (baseY - dp(6f)).coerceAtLeast(dp(20f))
        fun h(frac: Float) = min(maxH, maxH * frac)
        // The bed: one wide, low flame mound the others rise from.
        flameLayer(canvas, cx, baseY + dp(4f), bw * 0.46f, h(0.36f), phase * 0.5f, hotOuter)
        // Outer flames first (behind), then mid, then the centre flame —
        // the centre one rises behind/around the cap.
        flame(canvas, cx - bw * 0.36f, baseY + dp(5f), bw * 0.105f, h(0.55f), phase + 4.1f)
        flame(canvas, cx + bw * 0.36f, baseY + dp(5f), bw * 0.105f, h(0.55f), phase + 1.3f)
        flame(canvas, cx - bw * 0.185f, baseY + dp(3f), bw * 0.125f, h(0.78f), phase + 2.4f)
        flame(canvas, cx + bw * 0.185f, baseY + dp(3f), bw * 0.125f, h(0.78f), phase + 5.3f)
        flame(canvas, cx, baseY, bw * 0.145f, h(1.0f), phase)
    }

    /** One flame in four layers; each layer bends/wobbles on its own phase. */
    private fun flame(canvas: Canvas, baseCx: Float, baseY: Float, halfW: Float, height: Float, ph: Float) {
        val grow = 1f + sin(ph * 2).toFloat() * 0.07f
        flameLayer(canvas, baseCx, baseY, halfW, height * grow, ph, hotOuter)
        // Inner layers are a little wider than a thin outline, so the
        // layering reads as thickness (kept from the v0.3.5 band pass).
        flameLayer(canvas, baseCx, baseY, halfW * 0.70f, height * 0.70f * grow, ph + 0.9f, hot)
        flameLayer(canvas, baseCx, baseY, halfW * 0.45f, height * 0.46f * grow, ph + 1.7f, hotInner)
        // Bright core, only on the way up from the base.
        flameLayer(canvas, baseCx, baseY, halfW * 0.25f, height * 0.27f * grow, ph + 2.3f, hotCore)
    }

    private fun flameLayer(
        canvas: Canvas, baseCx: Float, baseY: Float,
        halfW: Float, height: Float, ph: Float, color: Int,
    ) {
        val bend = sin(ph.toDouble()).toFloat() * halfW * 0.38f
        val wobble = sin(ph * 2 + 1.3).toFloat() * halfW * 0.13f
        flamePath.reset()
        flamePath.moveTo(baseCx - halfW, baseY)
        flamePath.cubicTo(
            baseCx - halfW * 1.08f, baseY - height * 0.36f,
            baseCx - halfW * 0.40f + wobble, baseY - height * 0.60f,
            baseCx + bend, baseY - height,
        )
        flamePath.cubicTo(
            baseCx + halfW * 0.46f + wobble * 0.6f, baseY - height * 0.58f,
            baseCx + halfW * 1.08f, baseY - height * 0.36f,
            baseCx + halfW, baseY,
        )
        flamePath.cubicTo(
            baseCx + halfW * 0.42f, baseY - height * 0.10f,
            baseCx - halfW * 0.42f, baseY - height * 0.10f,
            baseCx - halfW, baseY,
        )
        flamePath.close()
        flamePaint.color = color
        canvas.drawPath(flamePath, flamePaint)
    }

    /** A fixed set of embers rising and fading; frozen mid-rise when still. */
    private fun drawEmbers(canvas: Canvas, cx: Float, baseY: Float) {
        val cycle = (phase / (2 * Math.PI)).toFloat()
        val rise = min(dp(92f), (baseY - dp(4f)).coerceAtLeast(dp(24f)))
        val spread = bodyRect.width() * 1.05f
        for (i in 0 until EMBER_COUNT) {
            val seed = EMBER_SEEDS[i]
            val t = frac(cycle + seed)
            val x = cx + (seed * 97f % 1f - 0.5f) * spread + sin(t * 5f + i * 1.7).toFloat() * dp(4f)
            val y = baseY - dp(10f) - t * rise
            val fade = sin(Math.PI * t).toFloat()
            emberPaint.color = emberColor
            emberPaint.alpha = (185 * fade).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, dp(0.9f) + dp(1.9f) * (1f - t), emberPaint)
        }
        emberPaint.alpha = 255
    }

    private fun frac(v: Float): Float = v - kotlin.math.floor(v)

    companion object {
        // Snowflake branch positions along each arm: (fraction, length fraction) pairs.
        private val BRANCHES = floatArrayOf(0.52f, 0.34f, 0.78f, 0.24f)
        private const val EMBER_COUNT = 12
        // Deterministic per-ember offsets (golden-ratio-ish spread).
        private val EMBER_SEEDS = floatArrayOf(
            0.13f, 0.47f, 0.71f, 0.29f, 0.89f, 0.55f, 0.05f, 0.63f, 0.37f, 0.21f, 0.79f, 0.43f,
        )
        // Bubble counts per state: hot / charging / saving.
        private val BUBBLE_COUNTS = intArrayOf(8, 7, 3)
        private val BUBBLE_SEEDS = floatArrayOf(
            0.11f, 0.53f, 0.83f, 0.31f, 0.67f, 0.23f, 0.97f, 0.41f,
        )
        // Slosh spring: stiffness and damping (ζ ≈ 0.5, under-damped).
        private const val SLOSH_STIFFNESS = 30f
        private const val SLOSH_DAMPING = 5.5f
        // Surface sampling for the smoothed liquid curve (v0.3.8).
        private const val SURFACE_STEPS = 44
        // Particle liquid (v0.3.9): surface-chain spring (pull to the
        // analytic wave), its damping, and the neighbour coupling that
        // lets ripples travel along the chain.
        private const val RIPPLE_K = 120f
        private const val RIPPLE_C = 5.0f
        private const val RIPPLE_COUPLE = 260f
        // Fixed particle pools — sized once, never grown per frame.
        private const val DROP_POOL = 24
        private const val BUBBLE_POOL = 8
    }
}
