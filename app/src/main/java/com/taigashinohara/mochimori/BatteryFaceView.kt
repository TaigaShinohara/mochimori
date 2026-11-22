package com.taigashinohara.mochimori

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The big central battery, drawn in code — except the cooling
 * objects, which are generated image assets since v0.3.28
 * (see CoolObjectArt).
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
 * v0.3.19 variable size (user: "ホーム画面はスクロール無しでコンテンツ
 * が見れるようにしたい"): the home slot is no longer a fixed 320dp —
 * it flexes between a floor and 320dp so the whole home screen fits
 * without scrolling, and this view's geometry follows the slot it is
 * given. The body's rect comes from the shared bodyRectDp() formula
 * (the home screen's glass plate mirrored the same function until
 * v0.3.20; the plate is gone in v0.3.21 and the formula is now this
 * view's alone): the flame/cap headroom and the shadow zone scale
 * with the slot height instead of staying at their 320dp values,
 * and the decorative sizes that were tuned at full size
 * (cap height, shell inset and radii, outline and meniscus strokes,
 * crystal radii, wave amplitudes) scale by sizeScale so
 * a small face is the same face, smaller — never a clipped or
 * squashed one. At a 320dp slot every number reduces to exactly the
 * pre-v0.3.19 geometry.
 *
 * v0.3.10 glass vessel (user: "UIアートはシンプルガラスにしよう" —
 * audit docs/GLASS_REVIEW_v0.3.9.md): the body was drawn as a glass
 * vessel in the same language as the app's GlassDrawable surfaces —
 * a translucent role-tint shell, a 1dp specular rim, a soft left
 * reflection band, cavity depth shading and a lightened outline top.
 *
 * v0.3.21 plain vessel (user: "Material Design スキルで全体を見直
 * して。無駄な装飾を消して"; audit docs/M3_REVIEW_v0.3.20.md F-4):
 * the glass treatment and the decorative layer are removed with the
 * rest of the app's Simple Glass. The shell is the state container
 * colour as an opaque solid, the outline is the state colour as a
 * solid stroke, and the cavity is the same neutral it was. Gone:
 * the aura behind the body, the ground shadow, the sparkles around
 * the cool face, the embers above the flames, the gloss band, the
 * specular rim, the cavity shading, the bolt's glow and edge
 * highlight, the crystals' halo pass and the cap's highlight line.
 * Kept, because they ARE the state language: the liquid (its depth
 * gradient stays — it is how the level reads), the waves, ripples,
 * droplets and bubbles, the tilt, the layered flames, the snow
 * crystals, the bolt, and the cap's metal gradient. The bottom zone
 * of bodyRectDp stays in the geometry (the home layout's spacing
 * was tuned with it) even though nothing is drawn in it now.
 *
 * v0.3.23 simple face (user chose design plan E, "ミニマル白", from
 * generated mock-ups, and instructed the face directly: "バッテリー
 * をキャラにするのはいい。ただし顔は目を点にし、目と口だけのシン
 * プルな顔がいい"): the battery is now a CHARACTER with the
 * simplest possible face — two dot eyes and a mouth, nothing else.
 * No highlights in the eyes, no brows, no cheeks. The flames and
 * snow crystals that used to carry the state around the body are
 * retired with it: state is now carried by the liquid's colour and
 * motion (unchanged) plus the mouth's minimal shape change —
 * a calm smile arc while saving or charging, a small open mouth
 * while not saving, a short flat line while the state is unknown.
 * The eyes blink, briefly, on the animation clock (dots squash to
 * slits); with motion reduced they simply stay open. The bolt
 * stays as the charging state's marker (it was never a face part:
 * it appears only while charging, so it cannot be misread). The
 * haptics are untouched — their pulses were always timed to the
 * liquid's phase, not to the flames' drawing.
 *
 * v0.3.27 burning → cooling (user proposal, approved from the
 * generated mock-up board v4: 「節電できてないときは燃えている。
 * チェックリストを埋めるほど冷ますオブジェクトが増えていきバッ
 * テリーが冷めていく」): the state expression gains a second axis —
 * the checklist's heat, decided by the pure [HeatScene] from the
 * normal tier's verdicts and handed in via [heatScene].
 * - The body gains the battery read the mock-up asked for: a
 *   metal rim band across the top of the shell, under the terminal
 *   cap (the cap itself is unchanged).
 * - Geometric flames stand around the body again — flat vector
 *   shapes this time, not the layered beziers retired in v0.3.23 —
 *   growing in number, size and redness with the heat, swaying
 *   gently on the existing animation clock. A measurably hot
 *   battery (temperature NG) keeps a small flame even when the
 *   rest of the list is filled (HeatScene's honest floor).
 * - The liquid's colour follows the heat between three
 *   character-only anchors (pale blue → pale peach → deep red,
 *   HeatScene.liquidColor); its bubbles run faster and more
 *   numerous the hotter it is. The shell's container tint blends
 *   between the cool and warning containers on the same axis.
 * - One cooling object per OK checklist item gathers around the
 *   body (parasol / wind chime / ice / air conditioner / fan /
 *   shaved ice / snowman — the fixed mapping in HeatScene),
 *   drawn as small geometric shapes, kept to the body's
 *   neighbourhood so the plan-E minimal white around it is
 *   untouched. The fan's blades turn slowly.
 * - The mouth follows the heat too: a wavy trembling line when
 *   hot, a flat line on the way, the calm smile arc when cool.
 * Charging keeps the v0.3.23 expression untouched (no flames,
 * cool liquid, the bolt); with no heat verdict (heat == null)
 * everything falls back to the plain v0.3.23 face.
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
 * colour alone: the mouth's shape and the liquid's motion differ too.
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

    /**
     * The burning → cooling scene (v0.3.27), decided by [HeatScene]
     * from the normal checklist's verdicts and set by MainActivity.
     * Null (never set) keeps the plain v0.3.23 face; a scene whose
     * heat is null (nothing could be judged) does the same, except
     * that its earned cooling objects still show.
     */
    var heatScene: HeatScene.Scene? = null
        set(value) {
            field = value
            invalidate()
        }

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
        // Wave amplitudes scale with the face (v0.3.19): at a small
        // slot the full-size waves would fill the whole liquid column.
        val ampScale = maxOf(sizeScale, 0.65f)
        val baseAmp = when (kind) {
            FaceKind.HOT -> dp(2.1f)
            FaceKind.CHARGING -> dp(1.5f)
            FaceKind.SAVING -> dp(1.1f)
            FaceKind.UNKNOWN -> 0f
        } * ampScale
        val boost = min(dp(3.5f) * sloshEnergy, dp(5.5f)) * ampScale
        val splash = splashLevel()
        val amp1 = baseAmp + boost + dp(5.0f) * splash * ampScale
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
            (dp(0.55f) + dp(0.8f) * splash) * ampScale,
            dp(3.2f) * splash * ampScale,
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
            val maxRipple = dp(9f) * maxOf(sizeScale, 0.6f)
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
        // v0.3.27: while a heat verdict exists (and the face is
        // not charging), the count and the pace follow the heat —
        // more numerous and faster the hotter it is.
        val heatActivity = heatScene?.takeIf { it.heat != null }?.particleActivity
        val (count, rate) = when {
            kind == FaceKind.UNKNOWN -> 0 to 0f
            kind != FaceKind.CHARGING && heatActivity != null ->
                heatBubbleCount(heatActivity) to (0.35f + 1.05f * heatActivity)
            kind == FaceKind.HOT -> BUBBLE_COUNTS[0] to 1.4f
            kind == FaceKind.CHARGING -> BUBBLE_COUNTS[1] to 0.9f
            else -> BUBBLE_COUNTS[2] to 0.35f
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

    // Derived shades (computed once). (The flame shades that stood
    // here until v0.3.22 were retired with the flames in v0.3.23.)
    private val capLight = ColorUtils.blendARGB(neutral, Color.WHITE, 0.65f)
    private val capMid = ColorUtils.blendARGB(neutral, Color.WHITE, 0.20f)
    private val capDark = ColorUtils.blendARGB(neutral, Color.BLACK, 0.28f)

    /** One state's palette: everything onDraw needs, shaders built per size. */
    private inner class Palette(var accent: Int, var container: Int, val onAccent: Int) {
        /**
         * Interior of the shell (the unfilled part). A neutral surface
         * with only a whisper (10%) of the state colour tinted in, in both
         * themes (v0.3.5: the state container lightened with white turned
         * a muddy brown-grey on the dark theme). Computed per read since
         * v0.3.27: the heat palette's accent moves with the heat, and
         * the cavity tint must follow it.
         */
        val cavity: Int get() = ColorUtils.blendARGB(cavityBase, accent, 0.10f)
        /**
         * The bolt's fill (v0.3.21): the on-accent colour lightened
         * toward white — the value the middle stop of the old bolt
         * gradient carried, now the bolt's single flat colour.
         */
        val boltFill: Int get() = ColorUtils.blendARGB(onAccent, Color.WHITE, 0.55f)
        var liquid: Shader? = null
        fun build(innerTop: Float, innerBottom: Float) {
            // Liquid (v0.3.6): anchored to the inner height, so whatever
            // part of it is visible reads light near the surface and deep
            // at the bottom — depth, not a flat fill. This is the one
            // gradient that survives the v0.3.21 flattening: it is how
            // the level reads, not decoration. The shell, outline and
            // bolt that used to be gradients here are flat colours now
            // (the container role, the accent, boltFill).
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
        }
    }

    private val coolPalette = Palette(cool, coolFill, onCool)
    private val hotPalette = Palette(hot, hotFill, onHot)
    private val neutralPalette = Palette(neutral, neutralFill, neutralFill)

    /**
     * The heat palette (v0.3.27): while a heat verdict exists, the
     * saving / not-saving faces take their accent from the heat
     * (HeatScene.liquidColor — pale blue → pale peach → deep red)
     * and their shell tint blends between the cool and warning
     * containers on the same axis. The accent is cached so the
     * liquid shader is rebuilt only when the colour actually moves.
     */
    private val heatPalette = Palette(cool, coolFill, onCool)
    private var heatAccentCache: Int? = null

    private fun paletteFor(kind: FaceKind): Palette {
        if (kind == FaceKind.SAVING || kind == FaceKind.HOT) {
            val heat = heatScene?.heat
            if (heat != null) {
                val accent = HeatScene.liquidColor(heat)
                if (accent != heatAccentCache) {
                    heatAccentCache = accent
                    heatPalette.accent = accent
                    heatPalette.container = ColorUtils.blendARGB(coolFill, hotFill, heat)
                    if (innerRect.width() > 0f) {
                        heatPalette.build(innerRect.top, innerRect.bottom)
                    }
                }
                return heatPalette
            }
        }
        return when (kind) {
            FaceKind.SAVING, FaceKind.CHARGING -> coolPalette
            FaceKind.HOT -> hotPalette
            FaceKind.UNKNOWN -> neutralPalette
        }
    }

    // ---- Paints (reused). The decorative paints of the glass era
    // (aura, shadow, gloss, rim, cavity shading, bolt glow, embers,
    // crystal halo, sparkles) were removed in v0.3.21, and the flame
    // and crystal paints went with their drawings in v0.3.23.
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cavityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val liquidEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boltFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boltStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    // The simple face (v0.3.23): dot eyes are filled ovals, the
    // mouth is a stroked arc / line or a filled oval, by state.
    private val faceFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val faceStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    // The heat expression (v0.3.27): flat geometric flames are
    // filled shapes; the cooling objects are image assets since
    // v0.3.28 (see CoolObjectArt).
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // ---- Paths (reused; rewound each use, never allocated in onDraw).
    private val boltPath = Path()
    private val wavePath = Path()
    private val edgePath = Path()
    private val clipPath = Path()
    private val faceRect = RectF() // scratch for the eyes and mouth
    private val flamePath = Path() // scratch for one geometric flame
    private val objRect = RectF() // scratch rect (the rim band)

    // ---- Geometry cached in onSizeChanged.
    /** Slot height relative to the full-size 320dp slot (v0.3.19):
     *  1 at 320dp and above, shrinking with the slot; decorative
     *  sizes scale by it so the face keeps its proportions. */
    private var sizeScale = 1f
    private val bodyRect = RectF()
    private val innerRect = RectF()
    private val capRect = RectF()
    private val layerRect = RectF()
    private var capShader: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Upright battery (v0.3.6, user's instruction): width : height
        // ≈ 1 : 1.55, cap on TOP centre like a standing dry cell. Headroom
        // above the body is reserved for the cap (it held the flames
        // until v0.3.22), with the cap in
        // the middle of it; an empty zone sits below (it held the ground
        // shadow until v0.3.21 removed it; the zone stays in the
        // geometry). The rect itself
        // comes from the shared bodyRectDp() (v0.3.19): the headroom and
        // shadow zones scale with the slot height, so a flexed-down slot
        // shrinks the whole composition proportionally instead of the
        // zones eating the body.
        val bodyDp = bodyRectDp(w / density, h / density)
        sizeScale = (h / density / 320f).coerceIn(0.45f, 1f)
        val left = bodyDp.left * density
        val top = bodyDp.top * density
        val bodyW = bodyDp.width() * density
        val bodyH = bodyDp.height() * density
        bodyRect.set(left, top, left + bodyW, top + bodyH)
        val inset = dp(7f) * maxOf(sizeScale, 0.5f)
        innerRect.set(left + inset, top + inset, left + bodyW - inset, top + bodyH - inset)
        // Cap: centred on the top edge, overlapping it slightly so it reads
        // as one piece with the body. Metal, sized in proportion (~44% of
        // the body width, 13dp tall at full size, scaled down with the face).
        val capW = bodyW * 0.44f
        val capH = dp(13f) * maxOf(sizeScale, 0.5f)
        capRect.set(
            bodyRect.centerX() - capW / 2f, top - capH + dp(2f),
            bodyRect.centerX() + capW / 2f, top + dp(2f),
        )
        layerRect.set(0f, 0f, w.toFloat(), h.toFloat())
        coolPalette.build(innerRect.top, innerRect.bottom)
        hotPalette.build(innerRect.top, innerRect.bottom)
        neutralPalette.build(innerRect.top, innerRect.bottom)
        heatPalette.build(innerRect.top, innerRect.bottom)
        capShader = LinearGradient(
            capRect.left, 0f, capRect.right, 0f,
            intArrayOf(capDark, capMid, capLight, capMid, capDark),
            floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f), Shader.TileMode.CLAMP,
        )
    }

    /** Spoken description of the face itself (R9). */
    private fun updateDescription() {
        contentDescription = when {
            unknownState -> "バッテリー。状態を確認中です"
            charging -> "充電中のバッテリー。残量 $levelPercent パーセント"
            saving -> "バッテリー。節電できています。残量 $levelPercent パーセント"
            else -> "バッテリー。節電できていません。残量 $levelPercent パーセント"
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
                        0 -> 1500L // hot face: lively waves
                        1 -> 2600L // charging pulse: slow
                        else -> 6800L // cool face: one slow, quiet loop
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
    // the waves' growth, so the pulse lands on the liquid's biggest
    // sway / wave peak:
    //   hot face (mode 0, ~1.5 s): at the wave "grow" peak (phase = π/4),
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

        // No aura and no ground shadow since v0.3.21: the battery
        // stands on the flat surface, not floating over it.
        // v0.3.27: the heat expression stands around the body again
        // — geometric flames (never while charging, never without
        // a verdict) and the earned cooling objects — drawn first,
        // so the body overlaps their bases and they read as
        // gathered around it, never pasted over it.
        val scene = heatScene
        if (scene != null && kind != FaceKind.UNKNOWN) {
            if (kind != FaceKind.CHARGING && scene.flameLevel > 0) {
                drawFlames(canvas, scene)
            }
            if (scene.objects.isNotEmpty()) {
                drawCoolObjects(canvas, scene)
            }
        }
        drawBody(canvas, palette, kind)

        // The bolt is the charging state's marker, drawn over the
        // liquid as before (charging only — never a decoration).
        if (kind == FaceKind.CHARGING) {
            drawBolt(canvas, palette)
        }

        drawFaceMarks(canvas, kind, palette)
    }

    // ---------------- The simple face (v0.3.23) ----------------
    //
    // Two dot eyes and a mouth — the whole face, at the user's
    // direct instruction. No eye highlights, no brows, no cheeks.
    // The marks sit in the body's upper area and are drawn last,
    // over the cavity and the liquid alike, in the state colour
    // deepened toward black so they stay readable over the pale
    // liquid surface and the neutral cavity in both themes.
    // The mouth is the only part that changes with the state:
    //   saving / charging: a calm smile arc
    //   not saving:        a small open mouth (a plain oval)
    //   unknown:           a short flat line — no verdict, no face
    //                      pretending to one

    /** Vertical squash of the dot eyes: 1 open, ~0.12 mid-blink. */
    private fun blinkScale(): Float {
        // One blink every ~4.6 s of animation time, closed for a
        // ~0.28 s dip shaped by a sine so it eases shut and open.
        // splashClock only advances while the animator runs, so a
        // still face (reduced motion, unknown state) keeps its
        // eyes open.
        val cycle = 4.6f
        val t = splashClock % cycle
        val window = 0.28f
        if (t < cycle - window) return 1f
        val p = (t - (cycle - window)) / window
        return 1f - sin(p * Math.PI).toFloat() * 0.88f
    }

    private fun drawFaceMarks(canvas: Canvas, kind: FaceKind, palette: Palette) {
        val fs = maxOf(sizeScale, 0.55f)
        val cx = bodyRect.centerX()
        val bodyTop = bodyRect.top
        val bodyW = bodyRect.width()
        val bodyH = bodyRect.height()
        val faceColor = ColorUtils.blendARGB(palette.accent, Color.BLACK, 0.45f)
        faceFillPaint.color = faceColor
        faceFillPaint.alpha = 242
        faceStrokePaint.color = faceColor
        faceStrokePaint.alpha = 242

        // Dot eyes.
        val eyeR = dp(3.4f) * fs
        val eyeY = bodyTop + bodyH * 0.17f
        val eyeDX = bodyW * 0.155f
        val squash = blinkScale()
        for (side in -1..1 step 2) {
            val ex = cx + side * eyeDX
            faceRect.set(ex - eyeR, eyeY - eyeR * squash, ex + eyeR, eyeY + eyeR * squash)
            canvas.drawOval(faceRect, faceFillPaint)
        }

        // Mouth. v0.3.27: while a heat verdict exists, the mouth
        // follows the heat (HeatScene) — a wavy trembling line
        // when hot, a flat line on the way, the calm smile when
        // cool. Charging keeps its smile and the unknown face its
        // flat line, whatever the heat says; without a heat
        // verdict the v0.3.23 mouths apply unchanged (smile /
        // small open oval / flat line).
        val mouthY = bodyTop + bodyH * 0.28f
        val heatMouth = heatScene?.takeIf { it.heat != null }?.mouth
        val mouth: HeatScene.Mouth? = when {
            kind == FaceKind.UNKNOWN -> HeatScene.Mouth.FLAT
            kind == FaceKind.CHARGING -> HeatScene.Mouth.SMILE
            heatMouth != null -> heatMouth
            kind == FaceKind.SAVING -> HeatScene.Mouth.SMILE
            else -> null // legacy not-saving: the small open oval
        }
        when (mouth) {
            HeatScene.Mouth.SMILE -> {
                // Calm smile: the lower arc of a small ellipse.
                val mw = dp(16f) * fs
                val mh = dp(11f) * fs
                faceRect.set(cx - mw / 2f, mouthY - mh / 2f, cx + mw / 2f, mouthY + mh / 2f)
                faceStrokePaint.strokeWidth = dp(2.4f) * fs
                canvas.drawArc(faceRect, 25f, 130f, false, faceStrokePaint)
            }
            HeatScene.Mouth.WAVY -> {
                // Trembling mouth (v0.3.27): a small zigzag — the
                // geometric wave of the approved mock-up's hot face.
                val halfW = dp(8f) * fs
                val amp = dp(2.2f) * fs
                faceStrokePaint.strokeWidth = dp(2.4f) * fs
                val seg = (halfW * 2f) / 4f
                flamePath.reset()
                flamePath.moveTo(cx - halfW, mouthY)
                for (i in 1..4) {
                    val x = cx - halfW + seg * i
                    val y = if (i % 2 == 1) mouthY - amp else mouthY + amp
                    if (i == 4) flamePath.lineTo(x, mouthY) else flamePath.lineTo(x, y)
                }
                canvas.drawPath(flamePath, faceStrokePaint)
            }
            HeatScene.Mouth.FLAT -> {
                // Flat line: no expression while there is no verdict,
                // or a face only part-way cool.
                val half = dp(5.5f) * fs
                faceStrokePaint.strokeWidth = dp(2.4f) * fs
                canvas.drawLine(cx - half, mouthY, cx + half, mouthY, faceStrokePaint)
            }
            null -> {
                // Small open mouth: a plain filled oval.
                val mw = dp(7.5f) * fs
                val mh = dp(10f) * fs
                faceRect.set(cx - mw / 2f, mouthY - mh / 2f, cx + mw / 2f, mouthY + mh / 2f)
                canvas.drawOval(faceRect, faceFillPaint)
            }
        }
        faceFillPaint.alpha = 255
        faceStrokePaint.alpha = 255
    }

    // ---------------- Heat expression (v0.3.27) ----------------
    //
    // Geometric flames and cooling objects, drawn as flat shapes
    // in the language of the approved mock-up (board v4): simple
    // silhouettes, pale fills, one quiet line colour. Sizes derive
    // from the body's rect, so they scale with the face like
    // everything else. The only motion is the flames' gentle sway
    // and the fan's slow blades, both on the existing animation
    // clock (splashClock) — a still face (reduced motion) gets
    // the same picture, frozen.

    /** Bubble count for a heat activity: 3 (calm) … 8 (hottest). */
    private fun heatBubbleCount(activity: Float): Int =
        (3f + 5f * activity).roundToInt().coerceIn(0, BUBBLE_POOL)

    private fun drawFlames(canvas: Canvas, scene: HeatScene.Scene) {
        val heat = scene.heat ?: return
        val level = scene.flameLevel.coerceIn(0, 3)
        val count = FLAME_COUNTS[level]
        if (count == 0) return
        val levelScale = FLAME_SCALES[level]
        // The flames redden with the heat on their own axis (pale
        // coral → deep red): even a small flame is never blue.
        val outer = ColorUtils.blendARGB(FLAME_PALE, FLAME_DEEP, heat)
        val inner = ColorUtils.blendARGB(FLAME_INNER_PALE, FLAME_INNER_DEEP, heat)
        val unit = bodyRect.width() * 0.26f
        for (i in 0 until count) {
            val a = FLAME_ANCHORS[i]
            val bx = bodyRect.left + bodyRect.width() * a[0]
            val by = bodyRect.top + bodyRect.height() * a[1]
            val h = unit * a[2] * levelScale
            val w = h * 0.62f
            // A gentle sway on the animation clock — decoration
            // stays quiet; the flames never thrash.
            val sway = sin(splashClock * 2.4f + i * 1.7f).toFloat() * unit * 0.07f
            flamePaint.color = outer
            flamePathTo(flamePath, bx, by, w, h, sway)
            canvas.drawPath(flamePath, flamePaint)
            flamePaint.color = inner
            flamePathTo(flamePath, bx, by, w * 0.55f, h * 0.58f, sway * 0.5f)
            canvas.drawPath(flamePath, flamePaint)
            flamePaint.color = FLAME_CORE
            flamePathTo(flamePath, bx, by, w * 0.26f, h * 0.30f, sway * 0.3f)
            canvas.drawPath(flamePath, flamePaint)
        }
    }

    /** One geometric flame: a teardrop silhouette standing on
     *  (bx, by), w wide and h tall, its tip leaning by sway. */
    private fun flamePathTo(path: Path, bx: Float, by: Float, w: Float, h: Float, sway: Float) {
        path.reset()
        path.moveTo(bx - w / 2f, by)
        path.cubicTo(bx - w / 2f, by - h * 0.45f, bx - w * 0.18f, by - h * 0.62f, bx + sway, by - h)
        path.cubicTo(bx + w * 0.30f, by - h * 0.60f, bx + w / 2f, by - h * 0.42f, bx + w / 2f, by)
        path.quadTo(bx, by - h * 0.10f, bx - w / 2f, by)
        path.close()
    }

    private fun drawCoolObjects(canvas: Canvas, scene: HeatScene.Scene) {
        val s = bodyRect.width() * 0.26f
        // Fixed enum order, so overlapping neighbours layer the
        // same way on every frame. Since v0.3.28 the objects are
        // generated image assets (CoolObjectArt), drawn at the
        // same seats and size as the old Canvas shapes.
        for (obj in HeatScene.CoolObject.values()) {
            if (obj !in scene.objects) continue
            val a = objectAnchor(obj)
            val cx = bodyRect.left + bodyRect.width() * a[0]
            val cy = bodyRect.top + bodyRect.height() * a[1]
            CoolObjectArt.draw(canvas, resources, obj, cx, cy, s)
        }
    }

    /** Each object's fixed seat around the body (fractions of the
     *  body's width / height from its top-left). The ring stays in
     *  the body's neighbourhood — the minimal white around the
     *  face slot is not touched. */
    private fun objectAnchor(obj: HeatScene.CoolObject): FloatArray = when (obj) {
        HeatScene.CoolObject.PARASOL -> floatArrayOf(-0.19f, 0.06f)
        HeatScene.CoolObject.AIRCON -> floatArrayOf(1.19f, 0.04f)
        HeatScene.CoolObject.WINDBELL -> floatArrayOf(-0.19f, 0.34f)
        HeatScene.CoolObject.FAN -> floatArrayOf(1.19f, 0.40f)
        HeatScene.CoolObject.ICE -> floatArrayOf(-0.19f, 0.68f)
        HeatScene.CoolObject.SHAVED_ICE -> floatArrayOf(1.19f, 0.72f)
        HeatScene.CoolObject.SNOWMAN -> floatArrayOf(-0.18f, 0.94f)
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
        // Corner radii scale with the face (v0.3.19): the full-size
        // 22dp radius on a flexed-down body would round it into a pill.
        val radius = dp(22f) * maxOf(sizeScale, 0.5f)
        val innerRadius = dp(16f) * maxOf(sizeScale, 0.5f)
        // Shell: the state container colour as an opaque solid
        // (v0.3.21; it was a translucent glass gradient until
        // v0.3.20).
        shellPaint.color = palette.container
        canvas.drawRoundRect(bodyRect, radius, radius, shellPaint)

        // Pale cavity: the unfilled part of the shell stays a visible
        // neutral surface in both themes, so the level can be read.
        cavityPaint.color = palette.cavity
        canvas.drawRoundRect(innerRect, innerRadius, innerRadius, cavityPaint)

        // Liquid (v0.3.6; smoothed + splashing in v0.3.8): fills from
        // the BOTTOM by level. Its surface is a plane tilted by the
        // sloshing angle plus travelling waves; the wave height grows
        // with the sloshing energy, so a tilt makes the surface visibly
        // slosh and then calm down — and a shake makes it splash.
        val fillH = innerRect.height() * (levelPercent / 100f)
        if (fillH > 0.5f) {
            clipPath.reset()
            clipPath.addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CW)
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
            liquidEdgePaint.strokeWidth = dp(2.3f) * maxOf(sizeScale, 0.6f)
            canvas.drawPath(edgePath, liquidEdgePaint)
            canvas.restore()
        }

        // Rim band (v0.3.27): a metal band across the body's top,
        // under the terminal cap — together with the cap it gives
        // the shell the unmistakable battery read of the approved
        // mock-up, whose top section is an opaque metal band. Same
        // metal gradient as the cap, clipped to the body's own
        // round rect so the band's corners follow the shell. Drawn
        // over the cavity and the liquid: the band is the shell's
        // top section, not a window into it. It ends well above
        // the eyes (17% of the body height).
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(bodyRect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        capPaint.shader = capShader
        objRect.set(bodyRect.left, bodyRect.top, bodyRect.right, bodyRect.top + bodyRect.height() * 0.075f)
        canvas.drawRect(objRect, capPaint)
        capPaint.shader = null
        canvas.restore()

        // Terminal cap on top centre: metal gradient (kept — it is
        // what makes the cap read as metal; its white highlight line
        // was removed in v0.3.21). Drawn over the body's top edge so
        // the two read as one piece.
        capPaint.shader = capShader
        canvas.drawRoundRect(capRect, dp(4f), dp(4f), capPaint)
        capPaint.shader = null

        // Outline over everything: the state colour as a solid
        // stroke (v0.3.21; it was a lightened-top gradient until
        // v0.3.20, and the specular rim that ran inside it is gone).
        outlinePaint.color = palette.accent
        outlinePaint.strokeWidth = dp(4.5f) * maxOf(sizeScale, 0.55f)
        canvas.drawRoundRect(bodyRect, radius, radius, outlinePaint)
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
        val heatActivity = heatScene?.takeIf { it.heat != null }?.particleActivity
        val (count, alpha) = when {
            kind == FaceKind.UNKNOWN -> return
            kind != FaceKind.CHARGING && heatActivity != null ->
                heatBubbleCount(heatActivity) to (80f + 60f * heatActivity).toInt()
            kind == FaceKind.HOT -> BUBBLE_COUNTS[0] to 130
            kind == FaceKind.CHARGING -> BUBBLE_COUNTS[1] to 120
            else -> BUBBLE_COUNTS[2] to 70
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
        // Flat fill + a darker edge (v0.3.21: the pulsing glow behind
        // the bolt and the white edge highlight are removed; the fill
        // is the single colour Palette.boltFill).
        buildBoltPath(cx, cy, bodyH)
        boltFillPaint.color = palette.boltFill
        canvas.drawPath(boltPath, boltFillPaint)
        boltStrokePaint.color = ColorUtils.blendARGB(palette.accent, Color.BLACK, 0.28f)
        boltStrokePaint.strokeWidth = dp(1.1f)
        canvas.drawPath(boltPath, boltStrokePaint)
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

    // The snow crystals and layered flames that carried the state
    // around the body until v0.3.22 were removed in v0.3.23. The
    // geometric flames and cooling objects of v0.3.27 (a different
    // drawing in a different language, driven by the checklist's
    // heat rather than the judge's verdict) live in the heat
    // section above. See the class doc.

    private fun frac(v: Float): Float = v - kotlin.math.floor(v)

    companion object {
        /**
         * The battery body's rect (dp) inside a slot of the given
         * size — the single source for the face's geometry (v0.3.19).
         * onSizeChanged draws from it. (The home screen's glass plate
         * mirrored it too until the plate was removed in v0.3.21.)
         * The headroom above the body (cap; flames until v0.3.22)
         * and the shadow zone below scale with the slot height relative to the
         * full-size 320dp slot; at 320dp every value is exactly the
         * pre-v0.3.19 geometry (top zone 58dp, shadow zone 20dp,
         * body capped at 272dp tall and 60% of the slot wide, top
         * biased 0.35 into the leftover height).
         */
        fun bodyRectDp(slotWDp: Float, slotHDp: Float): RectF {
            val zoneScale = (slotHDp / 320f).coerceAtMost(1f)
            val topZone = 58f * zoneScale
            val bottomZone = 20f * zoneScale
            val availH = (slotHDp - topZone - bottomZone).coerceAtLeast(30f)
            var bodyH = min(availH, 272f)
            var bodyW = bodyH / 1.55f
            val maxBodyW = slotWDp * 0.60f
            if (bodyW > maxBodyW) {
                bodyW = maxBodyW
                bodyH = bodyW * 1.55f
            }
            val left = (slotWDp - bodyW) / 2f
            val top = topZone + (availH - bodyH) * 0.35f
            return RectF(left, top, left + bodyW, top + bodyH)
        }

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

        // Heat expression (v0.3.27): flame anchors around the body
        // (x / base-y as fractions of the body's width / height,
        // relative size), in the order they appear as the flame
        // level rises; counts and size scales per level 0…3.
        private val FLAME_ANCHORS = arrayOf(
            floatArrayOf(0.16f, 0.0f, 1.0f),
            floatArrayOf(0.84f, 0.0f, 1.0f),
            floatArrayOf(-0.04f, 0.32f, 0.85f),
            floatArrayOf(1.04f, 0.32f, 0.85f),
            floatArrayOf(0.50f, -0.03f, 1.15f),
            floatArrayOf(-0.05f, 0.66f, 0.75f),
            floatArrayOf(1.05f, 0.66f, 0.75f),
        )
        private val FLAME_COUNTS = intArrayOf(0, 3, 5, 7)
        private val FLAME_SCALES = floatArrayOf(0f, 0.55f, 0.8f, 1.05f)
        // Flame colours (character-expression only, recorded in
        // ART_DIRECTION): pale coral at low heat → deep red at
        // full heat; the inner flame and core follow the same axis.
        private val FLAME_PALE = Color.rgb(0xF2, 0x99, 0x7A)
        private val FLAME_DEEP = Color.rgb(0xD4, 0x2A, 0x1E)
        private val FLAME_INNER_PALE = Color.rgb(0xFF, 0xC9, 0x8A)
        private val FLAME_INNER_DEEP = Color.rgb(0xF0, 0x76, 0x2B)
        private val FLAME_CORE = Color.rgb(0xFF, 0xE3, 0xAE)
    }
}
