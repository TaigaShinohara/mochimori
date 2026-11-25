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
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The big central battery. Until v0.3.29 it was drawn in code
 * (the cooling objects became generated image assets in
 * v0.3.28); since v0.4.0 the whole face is a composition of
 * generated diorama parts — see the v0.4.0 note below and
 * [DioramaArt] / [DioramaScene].
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
 * v0.4.0 diorama composition (the user's decision, approving
 * the design drafted in proposals/diorama-integration-design.md:
 * 「この設計で実装して」): the drawn vessel is retired. The face
 * is now composed, back to front, from generated miniature-
 * diorama parts ([DioramaArt]): a painted backdrop board and a
 * ground chosen by the heat, the LIQUID (still this view's
 * particle liquid, unchanged — level, heat colour, waves,
 * droplets, bubbles, tilt and splash all survive), the body
 * photograph — whose window is a real hole, so the liquid shows
 * through it — the face parts as images (dot-eye beads and
 * mouths; blinking swaps the eye pair image on the same ~4.6 s
 * clock), then the state's cast: image flames (breathing via a
 * small scale/bob transform on the animation clock), the earned
 * cooling objects (the diorama re-cuts of the same seven; the
 * air conditioner moved to the upper LEFT and the fan kept at
 * the middle right, so the two no longer overlap), sweat and
 * steam at the top heat stage, the lightning part while
 * charging (it replaces the drawn bolt), and the reward
 * decorations at heat 0. Which parts appear is decided by the
 * pure [DioramaScene] from the unchanged [HeatScene] verdict;
 * the shell, cavity, rim band, cap and outline drawings are
 * gone — they are IN the body photograph now. The widget still
 * follows with the same parts (WidgetFaceRenderer).
 *
 * v0.4.1 minimal scene (the user's on-device verdict on
 * v0.4.0: 「ゴチャゴチャしてる。まずは装飾無しで本体と液体
 * だけ表示してみて」): while [MINIMAL_SCENE] is on, the face
 * draws ONLY the liquid and the body photograph over it — no
 * backdrop board, no ground, no face parts, no flames, cooling
 * objects, sweat, steam, decorations or lightning. The slot's
 * ground is the app's plain surface background again. The full
 * composition code and [DioramaScene] stay in place below the
 * flag so the cast can return in stages. The body photograph
 * itself was also regenerated (green-screen chroma key — see
 * [DioramaArt]) because its cut-out edge read jagged.
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
            liquidSim.setFillLevel(field / 100f)
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
            if (INTEGRATED_STAGE && lastW > 0 && lastH > 0) {
                val spec = DioramaArt.stageSpec(currentDecision(currentKind()).ground)
                if (spec != activeSpec) updateGeometry(lastW, lastH, spec)
            }
            invalidate()
        }

    private fun currentDecision(kind: FaceKind): DioramaScene.Decision = DioramaScene.of(
        heatScene,
        charging = kind == FaceKind.CHARGING,
        saving = kind == FaceKind.SAVING,
        unknown = kind == FaceKind.UNKNOWN,
    )

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

    // ---- Tilt / sloshing & shallow-water fluid engine ----
    // The accelerometer needs no permission (SENSOR_DELAY_GAME is ~50 Hz,
    // well below the 200 Hz HIGH_SAMPLING_RATE_SENSORS platform cap).
    // Readings feed [LiquidPhysics] (shallow-water solver + wall-impact
    // splash jets + airborne/wall-clinging droplets + advected bubbles).
    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var sensorChecked = false
    private var sensorRegistered = false
    private var gravX = 0f
    private var gravY = 9.81f
    private var gravZ = 0f
    private var tiltTarget = 0f
    private var sloshAngle = 0f
    private var lastFrameNanos = 0L

    private var linAccelX = 0f
    private var linAccelY = 0f
    private var splashEnergy = 0f // 0 calm … 1 full splash
    private var splashClock = 0f // seconds of animation time; drives diorama & blink
    private var splashHapticCoolNanos = 0L // refractory so splash pulses never machine-gun

    private val liquidSim = LiquidPhysics(SURFACE_STEPS).apply { reset(0.80f) }
    private val dropRect = RectF() // scratch for droplet drawing

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Low-pass filter: keeps gravity, drops hand jitter.
            val a = 0.14f
            gravX += a * (event.values[0] - gravX)
            gravY += a * (event.values[1] - gravY)
            gravZ += a * (event.values[2] - gravZ)
            // Linear acceleration (shakes & sharp lateral flicks) drives
            // inertial sloshing and wall-impact splashes in LiquidPhysics.
            linAccelX = event.values[0] - gravX
            linAccelY = event.values[1] - gravY
            // In-plane magnitude factor: ~1 held upright, ~0 lying flat.
            val mag = sqrt(gravX * gravX + gravY * gravY) / 9.81f
            val factor = min(1f, mag)
            tiltTarget = (atan2(-gravX.toDouble(), gravY.toDouble()).toFloat() * factor)
                .coerceIn(-0.95f, 0.95f)
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
        val sensor = accelSensor ?: return // no accelerometer: ambient waves only
        try {
            // SENSOR_DELAY_GAME (~50 Hz) is well below the 200 Hz platform cap,
            // so no HIGH_SAMPLING_RATE_SENSORS permission is involved, while
            // capturing quick shakes and wall slams cleanly.
            if (sm.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)) {
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
        linAccelX = 0f
        linAccelY = 0f
    }

    /**
     * Advances [LiquidPhysics] using the real animator frame interval.
     */
    private fun stepSlosh(nowNanos: Long) {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = nowNanos
            return
        }
        val dt = ((nowNanos - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = nowNanos
        splashClock += dt

        val kind = targetKind
        val heatActivity = heatScene?.takeIf { it.heat != null }?.particleActivity
        val (bubbleCount, bubbleRate) = when {
            kind == FaceKind.UNKNOWN -> 0 to 0f
            kind != FaceKind.CHARGING && heatActivity != null ->
                heatBubbleCount(heatActivity) to (0.35f + 1.05f * heatActivity)
            kind == FaceKind.HOT -> BUBBLE_COUNTS[0] to 1.4f
            kind == FaceKind.CHARGING -> BUBBLE_COUNTS[1] to 0.9f
            else -> BUBBLE_COUNTS[2] to 0.35f
        }
        val aspect = if (innerRect.width() > 0f) {
            (innerRect.height() / innerRect.width()).coerceIn(0.8f, 3.0f)
        } else {
            1.72f
        }
        liquidSim.step(
            dtSeconds = dt,
            tiltRad = tiltTarget,
            lateralAccelNorm = linAccelX,
            verticalAccelNorm = linAccelY,
            liveliness = splashScale(),
            bubbleCount = bubbleCount,
            bubbleRate = bubbleRate,
            aspect = aspect,
        )
        // Decay linear impulse between sensor callbacks
        val decay = (1f - min(1f, dt * 8f))
        linAccelX *= decay
        linAccelY *= decay
        sloshAngle = liquidSim.effectiveTilt
        splashEnergy = liquidSim.splashEnergy
        maybeSplashHaptic(nowNanos)
    }

    /** Splash strength for the current face: the cool face stays calm, hot splashes freely. */
    private fun splashScale(): Float = when (targetKind) {
        FaceKind.SAVING -> 0.55f
        FaceKind.CHARGING -> 0.75f
        FaceKind.HOT -> 1.0f
        FaceKind.UNKNOWN -> 0f
    }

    private fun splashLevel(): Float = splashEnergy

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    // ---- State colours from the theme's fixed state roles (fallbacks are
    // the art-direction literals, used only if a theme lacks the attrs).
    // Since v0.4.0 these colours feed ONLY the liquid (the shell,
    // cavity, cap and bolt they used to paint are in the body
    // photograph now).
    private val cool = MaterialColors.getColor(context, R.attr.colorMochiStateCool, Color.rgb(0x02, 0x77, 0xBD))
    private val hot = MaterialColors.getColor(context, R.attr.colorMochiWarning, Color.rgb(0xE6, 0x51, 0x00))
    private val neutral = MaterialColors.getColor(
        context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.rgb(0x90, 0x90, 0x90),
    )

    /** One state's palette: liquid depth gradient, 3D meniscus tints, and caustic glow. */
    private inner class Palette(var accent: Int) {
        var liquid: Shader? = null
        var caustic: Shader? = null
        var backLiquidColor: Int = Color.TRANSPARENT
        var meniscusFillColor: Int = Color.TRANSPARENT

        fun build(inner: RectF) {
            if (inner.width() <= 0f || inner.height() <= 0f) return
            // Warm liquids blend toward warm sunlight (#FFF2D8) instead of
            // pure white so deep reds stay amber-translucent rather than pink.
            val isWarm = Color.red(accent) > Color.blue(accent) + 25
            val lightTint = if (isWarm) Color.rgb(0xFF, 0xF2, 0xD8) else Color.rgb(0xE8, 0xF8, 0xFF)
            val topCol = ColorUtils.blendARGB(accent, lightTint, 0.42f)
            val upperMid = ColorUtils.blendARGB(accent, lightTint, 0.14f)
            val deepCol = ColorUtils.blendARGB(accent, Color.BLACK, 0.34f)
            liquid = LinearGradient(
                0f, inner.top, 0f, inner.bottom,
                intArrayOf(topCol, upperMid, accent, deepCol),
                floatArrayOf(0f, 0.32f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
            backLiquidColor = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(accent, Color.BLACK, 0.14f), 145,
            )
            meniscusFillColor = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(accent, lightTint, 0.56f), 110,
            )
            val cx = inner.left + inner.width() * 0.48f
            val cy = inner.top + inner.height() * 0.78f
            val rad = maxOf(inner.width() * 0.56f, 1f)
            caustic = RadialGradient(
                cx, cy, rad,
                intArrayOf(
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, lightTint, 0.72f), 85),
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, lightTint, 0.40f), 32),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
    }

    private val coolPalette = Palette(cool)
    private val hotPalette = Palette(hot)
    private val neutralPalette = Palette(neutral)

    /**
     * The heat palette (v0.3.27): while a heat verdict exists, the
     * saving / not-saving faces take their accent from the heat
     * (HeatScene.liquidColor — pale blue → pale peach → deep red).
     * The accent is cached so the liquid shader is rebuilt only
     * when the colour actually moves.
     */
    private val heatPalette = Palette(cool)
    private var heatAccentCache: Int? = null

    private fun paletteFor(kind: FaceKind): Palette {
        if (kind == FaceKind.SAVING || kind == FaceKind.HOT) {
            val heat = heatScene?.heat
            if (heat != null) {
                val accent = HeatScene.liquidColor(heat)
                if (accent != heatAccentCache) {
                    heatAccentCache = accent
                    heatPalette.accent = accent
                    if (innerRect.width() > 0f) {
                        heatPalette.build(innerRect)
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

    // ---- Shared optical shaders for the glass tube & cylinder shading ----
    private var cavityCylShader: Shader? = null
    private var liquidCylShader: Shader? = null
    private var glassLeftShader: Shader? = null
    private var glassRightShader: Shader? = null

    private fun buildGlassShaders(inner: RectF) {
        if (inner.width() <= 0f || inner.height() <= 0f) return
        cavityCylShader = LinearGradient(
            inner.left, 0f, inner.right, 0f,
            intArrayOf(
                Color.argb(38, 10, 24, 38),
                Color.argb(8, 10, 24, 38),
                Color.TRANSPARENT,
                Color.argb(10, 10, 24, 38),
                Color.argb(44, 10, 24, 38),
            ),
            floatArrayOf(0f, 0.14f, 0.50f, 0.86f, 1f), Shader.TileMode.CLAMP,
        )
        // Cylinder curvature shadow + thin bright glass-wall refraction rims
        liquidCylShader = LinearGradient(
            inner.left, 0f, inner.right, 0f,
            intArrayOf(
                Color.argb(72, 0, 10, 20),
                Color.argb(62, 255, 255, 255),
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(44, 255, 255, 255),
                Color.argb(82, 0, 10, 20),
            ),
            floatArrayOf(0f, 0.08f, 0.20f, 0.82f, 0.92f, 1f), Shader.TileMode.CLAMP,
        )
        glassLeftShader = LinearGradient(
            0f, inner.top, 0f, inner.bottom,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(86, 255, 255, 255),
                Color.argb(54, 255, 255, 255),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0.05f, 0.18f, 0.80f, 0.94f), Shader.TileMode.CLAMP,
        )
        glassRightShader = LinearGradient(
            0f, inner.top, 0f, inner.bottom,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(48, 255, 255, 255),
                Color.argb(30, 255, 255, 255),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0.08f, 0.24f, 0.78f, 0.92f), Shader.TileMode.CLAMP,
        )
    }

    // ---- Paints (reused). Only the liquid still paints with
    // these: since v0.4.0 everything else is a diorama image
    // (DioramaArt has its own bitmap paint).
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val liquidEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bubbleRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // ---- Paths (reused; rewound each use, never allocated in onDraw).
    private val wavePath = Path()
    private val backWavePath = Path()
    private val ribbonPath = Path()
    private val edgePath = Path()
    private val underEdgePath = Path()
    private val backEdgePath = Path()
    private val glintPath = Path()
    private val clipPath = Path()
    private val groundRect = RectF() // scratch rect (the ground band)
    private val glassRect = RectF() // scratch rect (glass highlight bar)

    // ---- Geometry cached in onSizeChanged.
    /** Slot height relative to the full-size 320dp slot (v0.3.19):
     *  1 at 320dp and above, shrinking with the slot; decorative
     *  sizes scale by it so the face keeps its proportions. */
    private var sizeScale = 1f
    private val bodyRect = RectF()
    private val stageRect = RectF()
    private val innerRect = RectF()
    private val layerRect = RectF()
    private var activeSpec: DioramaArt.StageSpec = DioramaArt.STAGE_GRASS
    private var lastW = 0
    private var lastH = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastW = w
        lastH = h
        val spec = DioramaArt.stageSpec(currentDecision(currentKind()).ground)
        updateGeometry(w, h, spec)
    }

    private fun updateGeometry(w: Int, h: Int, spec: DioramaArt.StageSpec) {
        sizeScale = (h / density / 320f).coerceIn(0.45f, 1f)
        layerRect.set(0f, 0f, w.toFloat(), h.toFloat())
        if (INTEGRATED_STAGE) {
            activeSpec = spec
            val stageDp = stageRectDp(w / density, h / density, spec.aspectRatio)
            stageRect.set(
                stageDp.left * density, stageDp.top * density,
                stageDp.right * density, stageDp.bottom * density,
            )
            bodyRect.set(stageRect)
            DioramaArt.computeWindowRect(stageRect, spec, innerRect)
        } else {
            val bodyDp = bodyRectDp(w / density, h / density)
            val left = bodyDp.left * density
            val top = bodyDp.top * density
            val bodyW = bodyDp.width() * density
            val bodyH = bodyDp.height() * density
            bodyRect.set(left, top, left + bodyW, top + bodyH)
            val pad = bodyW * 0.018f
            innerRect.set(
                left + bodyW * DioramaArt.WIN_L - pad, top + bodyH * DioramaArt.WIN_T - pad,
                left + bodyW * DioramaArt.WIN_R + pad, top + bodyH * DioramaArt.WIN_B + pad,
            )
        }
        buildGlassShaders(innerRect)
        coolPalette.build(innerRect)
        hotPalette.build(innerRect)
        neutralPalette.build(innerRect)
        heatPalette.build(innerRect)
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
        val level = maxOf(splashLevel(), liquidSim.lastWallImpact.coerceAtMost(1f))
        if (level < 0.42f) return
        if (nowNanos - splashHapticCoolNanos < 380_000_000L) return
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
        if (INTEGRATED_STAGE) {
            val dio = currentDecision(kind)
            val spec = DioramaArt.stageSpec(dio.ground)
            if (spec != activeSpec && lastW > 0 && lastH > 0) {
                updateGeometry(lastW, lastH, spec)
            }
            drawLiquid(canvas, palette, kind)
            DioramaArt.drawInto(canvas, resources, spec.bodyPart, stageRect)
            if (kind == FaceKind.CHARGING) {
                drawLightning(canvas)
            }
            val eyes = if (blinkScale() < 0.55f) DioramaArt.Part.EYES_CLOSED else DioramaArt.Part.EYES_OPEN
            DioramaArt.drawStageFace(canvas, resources, stageRect, spec, eyes, dio.mouth)
            return
        }
        if (MINIMAL_SCENE) {
            // v0.4.1 minimal scene: the liquid and the body
            // photograph, nothing else (see the class doc). The
            // liquid's clip is the new body's window (onSizeChanged
            // reads DioramaArt.WIN_*), so the still-animated liquid
            // shows through the real hole exactly as before.
            drawLiquid(canvas, palette, kind)
            DioramaArt.drawInto(canvas, resources, DioramaArt.Part.BODY, bodyRect)
            return
        }
        val dio = currentDecision(kind)

        // The diorama composition (v0.4.0), back to front, in the
        // approved design's order: backdrop board → ground →
        // liquid → body → face → the state's cast around the body.
        // DioramaScene decides which parts appear; this only
        // paints. (Until v0.3.29 the flames and objects stood
        // BEHIND the drawn body; in the composition they stand
        // in front of the photograph's base, as they did in the
        // approved composite tests.)
        drawBackdrop(canvas, dio)
        drawGround(canvas, dio)
        drawLiquid(canvas, palette, kind)
        DioramaArt.drawInto(canvas, resources, DioramaArt.Part.BODY, bodyRect)

        // The lightning part is the charging state's marker,
        // drawn over the window as the bolt was (charging only —
        // never a decoration).
        if (kind == FaceKind.CHARGING) {
            drawLightning(canvas)
        }
        drawFaceImages(canvas, dio)

        val scene = heatScene
        if (scene != null && kind != FaceKind.UNKNOWN) {
            if (dio.flame != DioramaScene.Flame.NONE) {
                drawFlames(canvas, dio.flame)
            }
            if (scene.objects.isNotEmpty()) {
                drawCoolObjects(canvas, scene)
            }
        }
        if (dio.sweat) drawSweat(canvas)
        if (dio.steam) drawSteam(canvas)
        if (dio.decorations) drawDecorations(canvas)
    }

    /** The backdrop board fills the whole face slot (cover-cropped). */
    private fun drawBackdrop(canvas: Canvas, dio: DioramaScene.Decision) {
        DioramaArt.drawCover(canvas, resources, DioramaArt.backdropPart(dio.backdrop), layerRect)
    }

    /**
     * The ground: the miniature base disc, stretched into a wide
     * band under the body (the squash is deliberate — the disc
     * photographs are near-square, the ground's job in the
     * composition is a floor line, exactly as in the approved
     * composite tests).
     */
    private fun drawGround(canvas: Canvas, dio: DioramaScene.Decision) {
        val w = layerRect.width() * 1.15f
        val h = layerRect.width() * 0.30f
        val cx = layerRect.centerX()
        val cy = bodyRect.bottom + layerRect.height() * 0.015f
        groundRect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        DioramaArt.drawInto(canvas, resources, DioramaArt.groundPart(dio.ground), groundRect)
    }

    /** The charging marker: the lightning part over the window,
     *  breathing very slightly on the animation clock. */
    private fun drawLightning(canvas: Canvas) {
        val h = if (INTEGRATED_STAGE) innerRect.height() * 0.58f else bodyRect.height() * 0.34f
        val pulse = 1f + 0.03f * sin(splashClock * 2.2f).toFloat()
        DioramaArt.drawCentered(
            canvas, resources, DioramaArt.Part.LIGHTNING,
            innerRect.centerX(), innerRect.centerY(), h, h, scale = pulse,
        )
    }

    /**
     * Sweat at the top heat stage: the big drop slides down the
     * body's left side, the small one sits up by the cap — both
     * bob gently on the animation clock (a still face freezes
     * them mid-bob, as it freezes everything).
     */
    private fun drawSweat(canvas: Canvas) {
        val bw = bodyRect.width()
        val bh = bodyRect.height()
        val bobA = sin(splashClock * 2.3f).toFloat() * dp(1.6f)
        val bobB = sin(splashClock * 2.3f + 1.4f).toFloat() * dp(1.6f)
        DioramaArt.drawCentered(
            canvas, resources, DioramaArt.Part.SWEAT_BIG,
            bodyRect.left - bw * 0.015f, bodyRect.top + bh * 0.42f + bobA, bh * 0.30f, bh * 0.30f,
        )
        DioramaArt.drawCentered(
            canvas, resources, DioramaArt.Part.SWEAT_SMALL,
            bodyRect.right + bw * 0.01f, bodyRect.top + bh * 0.16f + bobB, bh * 0.15f, bh * 0.15f,
        )
    }

    /** Steam curling off the terminal cap at the top heat stage:
     *  a slow rise loop with a soft alpha breathing. */
    private fun drawSteam(canvas: Canvas) {
        val bh = bodyRect.height()
        val rise = frac(splashClock * 0.11f)
        val alpha = (185f + 60f * sin(splashClock * 1.7f).toFloat()).toInt().coerceIn(0, 255)
        DioramaArt.drawCentered(
            canvas, resources, DioramaArt.Part.STEAM,
            bodyRect.centerX(), bodyRect.top - bh * 0.09f - rise * bh * 0.02f,
            bh * 0.30f, bh * 0.30f, alpha = alpha,
        )
    }

    /**
     * The heat-0 reward decorations (design §4): the penguin and
     * the watermelon stand on the ground in front of the body,
     * the sparkle twinkles and the snowflake drifts in the sky.
     * Only ever at heat exactly 0 — the middle stages stay bare.
     */
    private fun drawDecorations(canvas: Canvas) {
        val bw = bodyRect.width()
        val bh = bodyRect.height()
        val base = bodyRect.bottom
        DioramaArt.drawCentered(
            canvas, resources, DioramaArt.Part.PENGUIN,
            bodyRect.centerX() - bw * 0.50f, base - bh * 0.09f, bh * 0.20f, bh * 0.20f,
        )
        DioramaArt.drawCentered(
            canvas, resources, DioramaArt.Part.WATERMELON,
            bodyRect.centerX() + bw * 0.53f, base - bh * 0.055f, bw * 0.20f, bw * 0.20f,
        )
        val twinkle = 1f + 0.13f * sin(splashClock * 1.9f).toFloat()
        DioramaArt.drawCentered(
            canvas, resources, DioramaArt.Part.SPARKLE,
            layerRect.left + layerRect.width() * 0.15f, layerRect.top + layerRect.height() * 0.10f,
            bw * 0.12f, bw * 0.12f, scale = twinkle,
        )
        DioramaArt.drawCentered(
            canvas, resources, DioramaArt.Part.SNOWFLAKE,
            layerRect.left + layerRect.width() * 0.86f,
            layerRect.top + layerRect.height() * 0.09f + sin(splashClock * 1.3f).toFloat() * dp(1.5f),
            bw * 0.13f, bw * 0.13f,
        )
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

    /**
     * The face as images (v0.4.0): the diorama parts on the flat
     * resin band above the window (the band runs from the cap's
     * base, ~14% of the body height, to the window frame's top,
     * ~39% — the face is centred in it: eyes at 25%, mouth at
     * 34.5%).
     *
     * The eyes are the canonical pair canvas (880×320, centres at
     * 240/640 × 160 — see DioramaArt): drawn 0.50 × body width,
     * so the beads land 0.227 × body width apart and ~0.085 wide,
     * a step bolder than the drawn dots they replace (0.031
     * apart, in the mock-up the beads read larger than the
     * drawing). Blinking swaps the pair image when the blink
     * clock's dip (blinkScale, the v0.3.23 clock, unchanged)
     * passes halfway — a still face keeps its eyes open, as
     * before. The mouth is the one image DioramaScene chose,
     * drawn at a per-shape width so the four read at a similar
     * visual weight.
     */
    private fun drawFaceImages(canvas: Canvas, dio: DioramaScene.Decision) {
        val bw = bodyRect.width()
        val bh = bodyRect.height()
        val eyeW = bw * 0.50f
        val eyeH = eyeW * (320f / 880f)
        val eyes = if (blinkScale() < 0.55f) DioramaArt.Part.EYES_CLOSED else DioramaArt.Part.EYES_OPEN
        DioramaArt.drawCentered(
            canvas, resources, eyes,
            bodyRect.centerX(), bodyRect.top + bh * 0.25f, eyeW, eyeH,
        )
        val mouthW = when (dio.mouth) {
            DioramaScene.Mouth.SMILE -> bw * 0.22f
            DioramaScene.Mouth.FLAT -> bw * 0.20f
            DioramaScene.Mouth.OPEN -> bw * 0.14f
            DioramaScene.Mouth.ZIGZAG -> bw * 0.25f
        }
        DioramaArt.drawCentered(
            canvas, resources, DioramaArt.mouthPart(dio.mouth),
            bodyRect.centerX(), bodyRect.top + bh * 0.345f, mouthW, mouthW,
        )
    }

    // ---------------- Heat expression (v0.3.27; diorama parts since v0.4.0) ----------------
    //
    // The flames and cooling objects around the body. Until
    // v0.3.29 the flames were drawn shapes and the objects flat
    // image assets; since v0.4.0 both are diorama parts
    // (DioramaArt), still at the seats and counts the heat
    // expression has always used — only the rendering changed.
    // Sizes derive from the body's rect, so they scale with the
    // face like everything else. The only motion is the flames'
    // breathing and the sweat / steam / decoration transforms,
    // all on the existing animation clock (splashClock) — a
    // still face (reduced motion) gets the same picture, frozen.

    /** Bubble count for a heat activity: 5 (calm) … 22 (hottest). */
    private fun heatBubbleCount(activity: Float): Int =
        (5f + 17f * activity).roundToInt().coerceIn(0, BUBBLE_POOL)

    /**
     * The flames (v0.4.0): one diorama flame image per stage —
     * small / medium / large, chosen by DioramaScene — standing
     * at the same anchors, counts and sizes the drawn flames
     * used (FLAME_ANCHORS / FLAME_COUNTS / FLAME_SCALES, the
     * mock-up's seats). The drawn flame's sway becomes a gentle
     * scale breathing plus a small bob on the animation clock:
     * the photograph must not be sheared or leaned, so it pulses
     * instead. The images carry their own colours (the stage IS
     * the size), so the drawn flames' heat-blended palette is
     * retired with them.
     */
    private fun drawFlames(canvas: Canvas, flame: DioramaScene.Flame) {
        val part = DioramaArt.flamePart(flame) ?: return
        val level = when (flame) {
            DioramaScene.Flame.SMALL -> 1
            DioramaScene.Flame.MEDIUM -> 2
            DioramaScene.Flame.LARGE -> 3
            DioramaScene.Flame.NONE -> 0
        }
        val count = FLAME_COUNTS[level]
        val levelScale = FLAME_SCALES[level]
        val unit = bodyRect.width() * 0.26f
        for (i in 0 until count) {
            val a = FLAME_ANCHORS[i]
            val bx = bodyRect.left + bodyRect.width() * a[0]
            val by = bodyRect.top + bodyRect.height() * a[1]
            val h = unit * a[2] * levelScale
            val pulse = 1f + 0.05f * sin(splashClock * 2.6f + i * 1.7f).toFloat()
            val bob = sin(splashClock * 2.2f + i * 2.3f).toFloat() * unit * 0.02f
            DioramaArt.drawCentered(
                canvas, resources, part,
                bx, by - h / 2f + bob, h * 0.62f, h, scale = pulse,
            )
        }
    }

    private fun drawCoolObjects(canvas: Canvas, scene: HeatScene.Scene) {
        val s = bodyRect.width() * 0.26f
        // Fixed enum order, so overlapping neighbours layer the
        // same way on every frame. The objects are the diorama
        // re-cuts of the v0.3.28 set (DioramaArt), drawn at the
        // same seats and size as ever.
        for (obj in HeatScene.CoolObject.values()) {
            if (obj !in scene.objects) continue
            val a = objectAnchor(obj)
            val cx = bodyRect.left + bodyRect.width() * a[0]
            val cy = bodyRect.top + bodyRect.height() * a[1]
            DioramaArt.drawCentered(canvas, resources, DioramaArt.coolObjectPart(obj), cx, cy, s, s)
        }
    }

    /** Each object's fixed seat around the body (fractions of the
     *  body's width / height from its top-left). The ring stays in
     *  the body's neighbourhood — the minimal white around the
     *  face slot is not touched. v0.4.0: the air conditioner moved
     *  from the upper RIGHT to the upper LEFT and the parasol
     *  took the upper right (the design's overlap fix: at full
     *  set the air conditioner and the fan used to stack on the
     *  right side; now the left column reads aircon / wind
     *  chime / ice and the right parasol / fan / shaved ice). */
    private fun objectAnchor(obj: HeatScene.CoolObject): FloatArray = when (obj) {
        HeatScene.CoolObject.PARASOL -> floatArrayOf(1.19f, 0.06f)
        HeatScene.CoolObject.AIRCON -> floatArrayOf(-0.19f, 0.04f)
        HeatScene.CoolObject.WINDBELL -> floatArrayOf(-0.19f, 0.34f)
        HeatScene.CoolObject.FAN -> floatArrayOf(1.19f, 0.40f)
        HeatScene.CoolObject.ICE -> floatArrayOf(-0.19f, 0.68f)
        HeatScene.CoolObject.SHAVED_ICE -> floatArrayOf(1.19f, 0.72f)
        HeatScene.CoolObject.SNOWMAN -> floatArrayOf(-0.18f, 0.94f)
    }

    // Surface sample buffers (fields, reused every frame — no onDraw
    // allocation). SURFACE_STEPS + 1 points across the inner width.
    private val surfX = FloatArray(SURFACE_STEPS + 1)
    private val surfY = FloatArray(SURFACE_STEPS + 1)
    private val backSurfY = FloatArray(SURFACE_STEPS + 1)

    /** Appends a smoothed surface curve (midpoint quadratics) to a path. */
    private fun traceCurve(path: Path, ys: FloatArray) {
        var px = surfX[0]; var py = ys[0]
        for (i in 1..SURFACE_STEPS) {
            val mx = (px + surfX[i]) / 2f; val my = (py + ys[i]) / 2f
            path.quadTo(px, py, mx, my)
            px = surfX[i]; py = ys[i]
        }
        path.lineTo(px, py)
    }

    /** Appends a smoothed surface curve in reverse (right → left) to close a ribbon. */
    private fun traceCurveReverse(path: Path, ys: FloatArray) {
        var px = surfX[SURFACE_STEPS]; var py = ys[SURFACE_STEPS]
        for (i in SURFACE_STEPS - 1 downTo 0) {
            val mx = (px + surfX[i]) / 2f; val my = (py + ys[i]) / 2f
            path.quadTo(px, py, mx, my)
            px = surfX[i]; py = ys[i]
        }
        path.lineTo(px, py)
    }

    /**
     * Droplet particles from [LiquidPhysics]:
     *  - Airborne splashes stretch along their real 2D velocity vector and
     *    carry a specular glint dot.
     *  - Wall-clinging glass droplets elongate vertically as they slide
     *    slowly back down into the liquid.
     */
    private fun drawDropParticles(canvas: Canvas, palette: Palette) {
        val iw = innerRect.width()
        val ih = innerRect.height()
        if (iw <= 0f || ih <= 0f) return
        val bodyCol = ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.30f)
        val rimCol = ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.80f)
        bubbleRimPaint.strokeWidth = maxOf(dp(0.55f), dp(0.75f) * sizeScale)
        for (d in liquidSim.drops) {
            if (!d.active) continue
            val px = innerRect.left + d.x * iw
            val py = innerRect.bottom - d.y * ih
            val rad = (d.r * iw).coerceIn(dp(0.9f), dp(4.8f))
            if (d.clinging) {
                // Elongated teardrop clinging to the glass wall
                val wHalf = rad * 0.78f
                val hHalf = rad * 1.45f
                dropRect.set(px - wHalf, py - hHalf, px + wHalf, py + hHalf)
                bubblePaint.color = bodyCol
                bubblePaint.alpha = 195
                canvas.drawOval(dropRect, bubblePaint)
                bubbleRimPaint.color = rimCol
                bubbleRimPaint.alpha = 150
                canvas.drawOval(dropRect, bubbleRimPaint)
                bubblePaint.color = Color.WHITE
                bubblePaint.alpha = 220
                canvas.drawCircle(px - wHalf * 0.25f, py - hHalf * 0.32f, maxOf(dp(0.5f), rad * 0.28f), bubblePaint)
            } else {
                val speed = sqrt(d.vx * d.vx + d.vy * d.vy)
                val stretch = 1f + min(speed * 0.45f, 0.75f)
                val wHalf = rad / sqrt(stretch)
                val hHalf = rad * sqrt(stretch)
                val angleDeg = if (speed > 0.08f) {
                    Math.toDegrees(atan2(d.vx.toDouble(), d.vy.toDouble())).toFloat()
                } else {
                    0f
                }
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(angleDeg)
                dropRect.set(-wHalf, -hHalf, wHalf, hHalf)
                bubblePaint.color = bodyCol
                bubblePaint.alpha = 225
                canvas.drawOval(dropRect, bubblePaint)
                bubblePaint.color = Color.WHITE
                bubblePaint.alpha = 230
                canvas.drawCircle(-wHalf * 0.26f, -hHalf * 0.26f, maxOf(dp(0.55f), rad * 0.30f), bubblePaint)
                canvas.restore()
            }
        }
        bubblePaint.alpha = 255
        bubbleRimPaint.alpha = 255
    }

    /**
     * Realistic glass-enclosed shallow-water liquid driven by [LiquidPhysics]:
     *  1. Subtle cylindrical glass cavity shading (visible above liquid too)
     *  2. Lagged back surface (2.5D depth)
     *  3. Front shallow-water body with wall-impact splash crest runup:
     *     vertical depth gradient + cylindrical wall shading & rims + caustic glow
     *  4. 3D meniscus top ribbon + underside refraction shadow + bright edge + crest glint
     *  5. Velocity-stretched splash droplets, wall-clinging glass drops, & 3D bubbles
     *  6. Front glass tube vertical highlights shifting subtly with tilt
     */
    private fun drawLiquid(canvas: Canvas, palette: Palette, kind: FaceKind) {
        if (innerRect.width() <= 0f || innerRect.height() <= 0f) return
        val innerRadius = innerRect.width() * (if (INTEGRATED_STAGE) activeSpec.winRadiusRatio else 0.291f)

        clipPath.reset()
        clipPath.addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // Layer 0: Subtle glass cavity cylinder shading
        cavityCylShader?.let {
            liquidPaint.shader = it
            canvas.drawRect(innerRect, liquidPaint)
            liquidPaint.shader = null
        }

        val fillH = innerRect.height() * (levelPercent / 100f)
        if (fillH > 0.5f) {
            val iw = innerRect.width()
            val ih = innerRect.height()
            val steps = SURFACE_STEPS
            val depthH = iw * (0.062f + 0.022f * splashLevel())
            val meniscusClimb = dp(2.2f) * maxOf(sizeScale, 0.6f)
            val leftJetPx = liquidSim.leftJetH * ih
            val rightJetPx = liquidSim.rightJetH * ih
            // Subtle micro-ripple overlay so even calm water shimmers organically
            val microAmp = when (kind) {
                FaceKind.HOT -> dp(1.2f)
                FaceKind.CHARGING -> dp(0.8f)
                FaceKind.SAVING -> dp(0.55f)
                FaceKind.UNKNOWN -> 0f
            } * maxOf(sizeScale, 0.6f)
            val wavePhase = phase * (if (kind == FaceKind.HOT) 2.2f else 0.9f)

            for (i in 0..steps) {
                val u = i / steps.toFloat()
                val x = innerRect.left + iw * u
                surfX[i] = x
                // Surface tension slight upward climb right at the glass walls
                val edgeDist = min(u, 1f - u)
                val climb = -meniscusClimb * exp((-edgeDist / 0.055f).toDouble()).toFloat()
                // Wall-impact splash tongue rising sharply up the glass walls
                val jetLift = -(
                    leftJetPx * exp((-u / 0.068f).toDouble()).toFloat() +
                        rightJetPx * exp((-(1f - u) / 0.068f).toDouble()).toFloat()
                    )
                val microF = microAmp * sin((u * 6.283f * 1.4f - wavePhase).toDouble()).toFloat()
                val microB = microAmp * 0.8f * sin((u * 6.283f * 1.4f - wavePhase + 1.6f).toDouble()).toFloat()
                val hFront = liquidSim.surfaceAt(u)
                val hBack = liquidSim.backSurfaceAt(u)
                surfY[i] = (innerRect.bottom - hFront * ih + climb + jetLift + microF)
                    .coerceIn(innerRect.top - dp(4f), innerRect.bottom + dp(2f))
                backSurfY[i] = (innerRect.bottom - hBack * ih - depthH + climb * 0.75f + jetLift * 0.55f + microB)
                    .coerceIn(innerRect.top - dp(4f), innerRect.bottom + dp(2f))
            }

            // Layer 1: Back liquid surface
            backWavePath.reset()
            backWavePath.moveTo(innerRect.left - dp(2f), innerRect.bottom + dp(2f))
            backWavePath.lineTo(innerRect.left - dp(2f), backSurfY[0])
            traceCurve(backWavePath, backSurfY)
            backWavePath.lineTo(innerRect.right + dp(2f), backSurfY[steps])
            backWavePath.lineTo(innerRect.right + dp(2f), innerRect.bottom + dp(2f))
            backWavePath.close()
            liquidPaint.color = palette.backLiquidColor
            canvas.drawPath(backWavePath, liquidPaint)
            liquidPaint.alpha = 255

            // Layer 2: Front liquid body (depth + cylinder curvature + caustic)
            wavePath.reset()
            wavePath.moveTo(innerRect.left - dp(2f), innerRect.bottom + dp(2f))
            wavePath.lineTo(innerRect.left - dp(2f), surfY[0])
            traceCurve(wavePath, surfY)
            wavePath.lineTo(innerRect.right + dp(2f), surfY[steps])
            wavePath.lineTo(innerRect.right + dp(2f), innerRect.bottom + dp(2f))
            wavePath.close()

            liquidPaint.shader = palette.liquid
            canvas.drawPath(wavePath, liquidPaint)
            liquidCylShader?.let {
                liquidPaint.shader = it
                canvas.drawPath(wavePath, liquidPaint)
            }
            palette.caustic?.let {
                liquidPaint.shader = it
                canvas.drawPath(wavePath, liquidPaint)
            }
            liquidPaint.shader = null

            // Layer 3: 3D Meniscus top surface ribbon + refraction underside + bright edge
            ribbonPath.reset()
            ribbonPath.moveTo(surfX[0], backSurfY[0])
            traceCurve(ribbonPath, backSurfY)
            ribbonPath.lineTo(surfX[steps], surfY[steps])
            traceCurveReverse(ribbonPath, surfY)
            ribbonPath.close()
            liquidPaint.color = palette.meniscusFillColor
            canvas.drawPath(ribbonPath, liquidPaint)
            liquidPaint.alpha = 255

            // Back edge (subtle)
            backEdgePath.reset()
            backEdgePath.moveTo(surfX[0], backSurfY[0])
            traceCurve(backEdgePath, backSurfY)
            liquidEdgePaint.color = ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.55f)
            liquidEdgePaint.alpha = 115
            liquidEdgePaint.strokeWidth = dp(1.2f) * maxOf(sizeScale, 0.6f)
            canvas.drawPath(backEdgePath, liquidEdgePaint)

            // Front meniscus underside shadow (refraction thickness)
            val underDy = dp(1.6f) * maxOf(sizeScale, 0.6f)
            underEdgePath.reset()
            underEdgePath.moveTo(surfX[0], surfY[0] + underDy)
            var ux = surfX[0]; var uy = surfY[0] + underDy
            for (i in 1..steps) {
                val nx = surfX[i]; val ny = surfY[i] + underDy
                underEdgePath.quadTo(ux, uy, (ux + nx) / 2f, (uy + ny) / 2f)
                ux = nx; uy = ny
            }
            underEdgePath.lineTo(ux, uy)
            liquidEdgePaint.color = ColorUtils.blendARGB(palette.accent, Color.BLACK, 0.35f)
            liquidEdgePaint.alpha = 72
            liquidEdgePaint.strokeWidth = dp(2.0f) * maxOf(sizeScale, 0.6f)
            canvas.drawPath(underEdgePath, liquidEdgePaint)

            // Layer 4: Airborne splash droplets, wall-clinging drops & 3D bubbles
            drawDropParticles(canvas, palette)
            drawBubbles(canvas, palette, kind, fillH)

            // Crisp bright front meniscus line
            edgePath.reset()
            edgePath.moveTo(surfX[0], surfY[0])
            traceCurve(edgePath, surfY)
            liquidEdgePaint.color = ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.80f)
            liquidEdgePaint.alpha = 235
            liquidEdgePaint.strokeWidth = dp(2.0f) * maxOf(sizeScale, 0.6f)
            canvas.drawPath(edgePath, liquidEdgePaint)

            // Pure white specular glint across the left-center wave crest
            val gStart = steps / 6
            val gEnd = (steps * 5) / 11
            glintPath.reset()
            glintPath.moveTo(surfX[gStart], surfY[gStart])
            var gx = surfX[gStart]; var gy = surfY[gStart]
            for (i in (gStart + 1)..gEnd) {
                val nx = surfX[i]; val ny = surfY[i]
                glintPath.quadTo(gx, gy, (gx + nx) / 2f, (gy + ny) / 2f)
                gx = nx; gy = ny
            }
            glintPath.lineTo(gx, gy)
            liquidEdgePaint.color = Color.WHITE
            liquidEdgePaint.alpha = 210
            liquidEdgePaint.strokeWidth = dp(1.5f) * maxOf(sizeScale, 0.6f)
            canvas.drawPath(glintPath, liquidEdgePaint)
            liquidEdgePaint.alpha = 255
        }

        // Layer 5: Front glass tube highlights (subtle parallax with tilt)
        drawGlassHighlights(canvas)
        canvas.restore()
    }

    /** Vertical glass tube specular bars over the cavity & liquid. */
    private fun drawGlassHighlights(canvas: Canvas) {
        val iw = innerRect.width()
        val ih = innerRect.height()
        val tiltShift = (sloshAngle * iw * 0.032f).coerceIn(-iw * 0.04f, iw * 0.04f)
        glassLeftShader?.let { shader ->
            val glX = innerRect.left + iw * 0.10f + tiltShift
            val glW = iw * 0.078f
            glassRect.set(glX, innerRect.top + ih * 0.05f, glX + glW, innerRect.bottom - ih * 0.07f)
            liquidPaint.shader = shader
            canvas.drawRoundRect(glassRect, glW * 0.5f, glW * 0.5f, liquidPaint)
            // Narrower crisp specular core
            val coreW = glW * 0.36f
            glassRect.set(glX + glW * 0.24f, innerRect.top + ih * 0.08f, glX + glW * 0.24f + coreW, innerRect.bottom - ih * 0.11f)
            canvas.drawRoundRect(glassRect, coreW * 0.5f, coreW * 0.5f, liquidPaint)
        }
        glassRightShader?.let { shader ->
            val grX = innerRect.right - iw * 0.12f + tiltShift * 0.5f
            val grW = iw * 0.034f
            glassRect.set(grX, innerRect.top + ih * 0.09f, grX + grW, innerRect.bottom - ih * 0.09f)
            liquidPaint.shader = shader
            canvas.drawRoundRect(glassRect, grW * 0.5f, grW * 0.5f, liquidPaint)
        }
        liquidPaint.shader = null
    }

    /**
     * 3D rim-lit bubble particles advected by [LiquidPhysics]: translucent
     * interior, bright thin rim, and a tiny upper-left specular dot.
     */
    private fun drawBubbles(canvas: Canvas, palette: Palette, kind: FaceKind, fillH: Float) {
        val heatActivity = heatScene?.takeIf { it.heat != null }?.particleActivity
        val (baseCount, baseAlpha) = when {
            kind == FaceKind.UNKNOWN -> return
            kind != FaceKind.CHARGING && heatActivity != null ->
                heatBubbleCount(heatActivity) to (115f + 65f * heatActivity).toInt()
            kind == FaceKind.HOT -> BUBBLE_COUNTS[0] to 175
            kind == FaceKind.CHARGING -> BUBBLE_COUNTS[1] to 155
            else -> BUBBLE_COUNTS[2] to 115
        }
        val count = (baseCount + (liquidSim.aeration * 8f).toInt()).coerceIn(0, BUBBLE_POOL)
        if (count == 0 || fillH < dp(20f)) return
        val fillCol = ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.45f)
        val rimCol = ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.86f)
        bubbleRimPaint.strokeWidth = maxOf(dp(0.65f), dp(0.85f) * sizeScale)
        val iw = innerRect.width()
        val ih = innerRect.height()
        for (i in 0 until count) {
            val b = liquidSim.bubbles[i]
            if (!b.init) continue
            val r = (b.r * iw).coerceIn(dp(0.75f), dp(3.4f))
            val x = (innerRect.left + iw * b.x +
                sin(splashClock * 1.4f + b.wob).toFloat() * dp(1.2f))
                .coerceIn(innerRect.left + r + dp(2f), innerRect.right - r - dp(2f))
            val y = innerRect.bottom - b.y * ih
            val sIdx = (((x - innerRect.left) / iw).coerceIn(0f, 1f) * SURFACE_STEPS).toInt()
            val localSurf = surfY[sIdx] + r + dp(2f)
            if (y <= localSurf || y >= innerRect.bottom - r) continue
            val depthFrac = ((innerRect.bottom - y) / maxOf(1f, innerRect.bottom - localSurf)).coerceIn(0f, 1f)
            val fade = sin(Math.PI * min(depthFrac * 1.15f, 1f)).toFloat()
            val a = (baseAlpha * fade).toInt().coerceIn(0, 255)
            if (a < 8) continue
            bubblePaint.color = fillCol
            bubblePaint.alpha = (a * 0.35f).toInt()
            canvas.drawCircle(x, y, r, bubblePaint)
            bubbleRimPaint.color = rimCol
            bubbleRimPaint.alpha = a
            canvas.drawCircle(x, y, r, bubbleRimPaint)
            bubblePaint.color = Color.WHITE
            bubblePaint.alpha = (a * 1.15f).toInt().coerceAtMost(235)
            val sr = maxOf(dp(0.45f), r * 0.30f)
            canvas.drawCircle(x - r * 0.32f, y - r * 0.32f, sr, bubblePaint)
        }
        bubblePaint.alpha = 255
        bubbleRimPaint.alpha = 255
    }

    // The drawn charging bolt that stood here until v0.3.29 was
    // retired in v0.4.0: the diorama lightning part carries the
    // charging state now (drawLightning, in drawFace's helpers).
    // The snow crystals and layered flames that carried the state
    // around the body until v0.3.22 were removed in v0.3.23; the
    // heat expression of v0.3.27 (driven by the checklist's heat
    // rather than the judge's verdict) lives in the heat section
    // above. See the class doc.

    private fun frac(v: Float): Float = v - kotlin.math.floor(v)

    companion object {
        /**
         * Integrated battery + pedestal base with liquid, face parts
         * (split-eye blink + mouth) and charging lightning. Hotter
         * stages switch automatically once the "battery + ground"
         * spec is wired in [DioramaArt.stageSpec].
         * (WidgetFaceRenderer keeps its own copy — the two must agree.)
         */
        const val INTEGRATED_STAGE = true

        /** Legacy v0.4.1 minimal scene (used only when INTEGRATED_STAGE is false). */
        const val MINIMAL_SCENE = false

        /**
         * Rect (dp) for an integrated battery + pedestal base image inside
         * the face slot. Fits up to 90% of slot width (matching the approved
         * home screen vision) while leaving a small bottom margin above the
         * pulled-up level line.
         */
        fun stageRectDp(slotWDp: Float, slotHDp: Float, aspectRatio: Float): RectF {
            val zoneScale = (slotHDp / 320f).coerceAtMost(1f)
            val topZone = 10f * zoneScale
            val bottomZone = 14f * zoneScale
            val availH = (slotHDp - topZone - bottomZone).coerceAtLeast(30f)
            var stageH = min(availH, 296f)
            var stageW = stageH * aspectRatio
            val maxStageW = slotWDp * 0.90f
            if (stageW > maxStageW) {
                stageW = maxStageW
                stageH = stageW / aspectRatio
            }
            val left = (slotWDp - stageW) / 2f
            val top = topZone + (availH - stageH) * 0.50f
            return RectF(left, top, left + stageW, top + stageH)
        }

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
        private val BUBBLE_COUNTS = intArrayOf(20, 12, 5)
        // Surface sampling for the smoothed liquid curve.
        private const val SURFACE_STEPS = LiquidPhysics.DEFAULT_COLS
        private const val BUBBLE_POOL = LiquidPhysics.BUBBLE_POOL

        // Heat expression (v0.3.27): flame anchors around the body
        // (x / base-y as fractions of the body's width / height,
        // relative size), in the order they appear as the flame
        // level rises; counts and size scales per level 0…3. Since
        // v0.4.0 they seat the diorama flame images; the drawn
        // flames' colour axis (pale coral → deep red) is retired —
        // each stage's image carries its own colour now.
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
    }
}
