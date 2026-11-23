package com.taigashinohara.mochimori

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import kotlin.math.min
import kotlin.math.sin

/**
 * Static face for the home-screen widget (v0.3.12).
 *
 * A widget cannot host the animated custom View, so this renders ONE
 * still frame of the face into a Bitmap with plain Canvas calls.
 * State comes from [BatteryJudge] via the caller — this renderer never
 * judges anything itself.
 *
 * v0.3.21: the still followed the live face's M3 flattening.
 * v0.3.23: the still followed the simple-face redesign.
 * v0.3.27: the still followed the burning → cooling expression —
 * the liquid coloured by the checklist heat (HeatScene.liquidColor),
 * flames at the scene's flame level, the earned cooling objects,
 * and the mouth following the heat. The scene is decided by the
 * caller (WidgetUpdater) from the same checklist reads as the home
 * screen, via the shared ChecklistReader — the widget never judges
 * differently.
 * v0.3.28: the cooling objects became the same generated image
 * assets as the live face's.
 *
 * v0.4.0: the still follows the live face's diorama composition —
 * the same generated parts ([DioramaArt]), the same order
 * (backdrop board → ground → liquid → body photograph → lightning
 * while charging → face images → flames / cooling objects →
 * sweat / steam → the heat-0 decorations), and the same pure
 * state mapping ([DioramaScene]) fed by the same [HeatScene]
 * verdict, so the two faces never disagree about the state —
 * only about the motion: this frame is frozen at a representative
 * phase (the liquid's surface is a gentle still wave, the eyes
 * are open, the flames stand upright, no particles fly). The
 * liquid keeps this renderer's own still recipe — the live
 * face's gradient stops, a per-state wave amplitude, fixed-seed
 * bubbles and the bright meniscus line — now inside the body
 * photograph's real window instead of the drawn cavity.
 */
object WidgetFaceRenderer {

    private enum class Kind { SAVING, HOT, CHARGING, UNKNOWN }

    // Virtual canvas the face is composed in; the bitmap is this space
    // scaled up. Proportions mirror BatteryFaceView.onSizeChanged.
    private const val VW = 150f
    private const val VH = 190f

    fun render(
        context: Context,
        state: BatteryJudge.State,
        levelPercent: Int,
        scene: HeatScene.Scene? = null,
    ): Bitmap {
        val kind = when (state) {
            BatteryJudge.State.SAVING -> Kind.SAVING
            BatteryJudge.State.CHARGING -> Kind.CHARGING
            BatteryJudge.State.NOT_SAVING, BatteryJudge.State.EMERGENCY -> Kind.HOT
            BatteryJudge.State.UNKNOWN -> Kind.UNKNOWN
        }
        val density = context.resources.displayMetrics.density
        // 60dp x 76dp on screen, rendered at 2x for crispness.
        val pxW = (60f * density * 2f).toInt().coerceAtLeast(120)
        val pxH = (pxW * VH / VW).toInt()
        val bitmap = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(pxW / VW, pxH / VH)
        draw(canvas, context, kind, levelPercent.coerceIn(0, 100), scene)
        return bitmap
    }

    private fun draw(canvas: Canvas, context: Context, kind: Kind, level: Int, scene: HeatScene.Scene?) {
        val res = context.resources
        val themed = ContextThemeWrapper(context, R.style.Theme_MochiMori)

        fun attr(attrRes: Int, fallback: Int): Int =
            MaterialColors.getColor(themed, attrRes, fallback)

        // The liquid's accent: the theme's fixed state colours,
        // with the heat override the still has always had — while
        // a heat verdict exists, the liquid follows the heat
        // (HeatScene.liquidColor); charging keeps the plain cool
        // face. (The shell / cavity / outline colours this block
        // also resolved until v0.3.29 are retired: the body is a
        // photograph now.)
        var accent = when (kind) {
            Kind.SAVING, Kind.CHARGING ->
                attr(R.attr.colorMochiStateCool, Color.rgb(0x02, 0x77, 0xBD))
            Kind.HOT ->
                attr(R.attr.colorMochiWarning, Color.rgb(0xE6, 0x51, 0x00))
            Kind.UNKNOWN -> attr(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.rgb(0x90, 0x90, 0x90),
            )
        }
        val heat = scene?.heat
        if (heat != null && (kind == Kind.SAVING || kind == Kind.HOT)) {
            accent = HeatScene.liquidColor(heat)
        }

        // ---- Geometry (mirrors BatteryFaceView.onSizeChanged) ----
        val topZone = 34f
        val bottomZone = 12f
        val availH = VH - topZone - bottomZone
        var bodyH = min(availH, 150f)
        var bodyW = bodyH / 1.55f
        val maxBodyW = VW * 0.60f
        if (bodyW > maxBodyW) {
            bodyW = maxBodyW
            bodyH = bodyW * 1.55f
        }
        val k = bodyW / 156f // scale vs the live face's dp numbers
        val u: (Float) -> Float = { v -> v * k }
        val body = RectF(
            (VW - bodyW) / 2f, topZone + (availH - bodyH) * 0.35f,
            (VW + bodyW) / 2f, topZone + (availH - bodyH) * 0.35f + bodyH,
        )
        // The window: the body photograph's real hole (the live
        // face's fractions), expanded a touch so the liquid tucks
        // under the resin frame.
        val pad = bodyW * 0.018f
        val win = RectF(
            body.left + bodyW * DioramaArt.WIN_L - pad,
            body.top + bodyH * DioramaArt.WIN_T - pad,
            body.left + bodyW * DioramaArt.WIN_R + pad,
            body.top + bodyH * DioramaArt.WIN_B + pad,
        )

        val dio = DioramaScene.of(
            scene,
            charging = kind == Kind.CHARGING,
            saving = kind == Kind.SAVING,
            unknown = kind == Kind.UNKNOWN,
        )

        // ---- The diorama composition, back to front ----
        DioramaArt.drawCover(canvas, res, DioramaArt.backdropPart(dio.backdrop), RectF(0f, 0f, VW, VH))
        run {
            val gw = VW * 1.15f
            val gh = VW * 0.30f
            val gcy = body.bottom + VH * 0.015f
            DioramaArt.drawInto(
                canvas, res, DioramaArt.groundPart(dio.ground),
                RectF(VW / 2f - gw / 2f, gcy - gh / 2f, VW / 2f + gw / 2f, gcy + gh / 2f),
            )
        }
        drawLiquid(canvas, kind, level, accent, win, u)
        DioramaArt.drawInto(canvas, res, DioramaArt.Part.BODY, body)

        // Charging: the lightning part over the window.
        if (kind == Kind.CHARGING) {
            val h = bodyH * 0.34f
            DioramaArt.drawCentered(
                canvas, res, DioramaArt.Part.LIGHTNING,
                win.centerX(), win.centerY(), h, h,
            )
        }

        // The face: eyes open (a still never blinks), the mouth
        // DioramaScene chose — the live face's seats and sizes.
        val eyeW = bodyW * 0.50f
        DioramaArt.drawCentered(
            canvas, res, DioramaArt.Part.EYES_OPEN,
            body.centerX(), body.top + bodyH * 0.25f, eyeW, eyeW * (320f / 880f),
        )
        val mouthW = when (dio.mouth) {
            DioramaScene.Mouth.SMILE -> bodyW * 0.22f
            DioramaScene.Mouth.FLAT -> bodyW * 0.20f
            DioramaScene.Mouth.OPEN -> bodyW * 0.14f
            DioramaScene.Mouth.ZIGZAG -> bodyW * 0.25f
        }
        DioramaArt.drawCentered(
            canvas, res, DioramaArt.mouthPart(dio.mouth),
            body.centerX(), body.top + bodyH * 0.345f, mouthW, mouthW,
        )

        // The state's cast, frozen at the live face's seats.
        if (scene != null && kind != Kind.UNKNOWN) {
            DioramaArt.flamePart(dio.flame)?.let { part ->
                drawFlames(canvas, res, part, dio.flame, body)
            }
            for (obj in HeatScene.CoolObject.values()) {
                if (obj !in scene.objects) continue
                val a = objectAnchor(obj)
                val s = bodyW * 0.26f
                DioramaArt.drawCentered(
                    canvas, res, DioramaArt.coolObjectPart(obj),
                    body.left + bodyW * a[0], body.top + bodyH * a[1], s, s,
                )
            }
        }
        if (dio.sweat) {
            DioramaArt.drawCentered(
                canvas, res, DioramaArt.Part.SWEAT_BIG,
                body.left - bodyW * 0.015f, body.top + bodyH * 0.42f, bodyH * 0.30f, bodyH * 0.30f,
            )
            DioramaArt.drawCentered(
                canvas, res, DioramaArt.Part.SWEAT_SMALL,
                body.right + bodyW * 0.01f, body.top + bodyH * 0.16f, bodyH * 0.15f, bodyH * 0.15f,
            )
        }
        if (dio.steam) {
            DioramaArt.drawCentered(
                canvas, res, DioramaArt.Part.STEAM,
                body.centerX(), body.top - bodyH * 0.09f, bodyH * 0.30f, bodyH * 0.30f, alpha = 220,
            )
        }
        if (dio.decorations) {
            DioramaArt.drawCentered(
                canvas, res, DioramaArt.Part.PENGUIN,
                body.centerX() - bodyW * 0.50f, body.bottom - bodyH * 0.09f, bodyH * 0.20f, bodyH * 0.20f,
            )
            DioramaArt.drawCentered(
                canvas, res, DioramaArt.Part.WATERMELON,
                body.centerX() + bodyW * 0.53f, body.bottom - bodyH * 0.055f, bodyW * 0.20f, bodyW * 0.20f,
            )
            DioramaArt.drawCentered(
                canvas, res, DioramaArt.Part.SPARKLE,
                VW * 0.15f, VH * 0.10f, bodyW * 0.12f, bodyW * 0.12f,
            )
            DioramaArt.drawCentered(
                canvas, res, DioramaArt.Part.SNOWFLAKE,
                VW * 0.86f, VH * 0.09f, bodyW * 0.13f, bodyW * 0.13f,
            )
        }
    }

    /**
     * The still's liquid: fills the window from the bottom by
     * level, under one gentle still wave (per-state amplitude,
     * frozen phase — the recipe this renderer has always used),
     * with the live face's gradient stops, a few fixed-seed
     * bubbles and the bright meniscus line.
     */
    private fun drawLiquid(
        canvas: Canvas, kind: Kind, level: Int, accent: Int,
        win: RectF, u: (Float) -> Float,
    ) {
        val fillH = win.height() * (level / 100f)
        if (fillH <= 0.5f) return
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val baseAmp = when (kind) {
            Kind.HOT -> u(2.1f)
            Kind.CHARGING -> u(1.5f)
            Kind.SAVING -> u(1.1f)
            Kind.UNKNOWN -> 0f
        }
        val phase0 = 0.9f
        val kf1 = (2 * Math.PI / (win.width() * 0.9f)).toFloat()
        val kf2 = (2 * Math.PI / (win.width() * 0.45f)).toFloat()
        fun surfaceY(x: Float): Float {
            val dx = x - win.left
            val baseY = win.bottom - fillH
            return baseY +
                baseAmp * sin((dx * kf1 - phase0).toDouble()).toFloat() +
                baseAmp * 0.45f * sin((dx * kf2 + phase0 * 0.63f + 1.7f).toDouble()).toFloat()
        }
        val radius = win.width() * 0.26f
        val clip = Path().apply { addRoundRect(win, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        val wave = Path()
        wave.moveTo(win.left - 2f, win.bottom + 2f)
        val steps = 24
        for (i in 0..steps) {
            val x = win.left + win.width() * (i / steps.toFloat())
            wave.lineTo(x, surfaceY(x))
        }
        wave.lineTo(win.right + 2f, win.bottom + 2f)
        wave.close()
        fill.shader = LinearGradient(
            0f, win.top, 0f, win.bottom,
            intArrayOf(
                ColorUtils.blendARGB(accent, Color.WHITE, 0.50f),
                ColorUtils.blendARGB(accent, Color.WHITE, 0.16f),
                accent,
                ColorUtils.blendARGB(accent, Color.BLACK, 0.20f),
            ),
            floatArrayOf(0f, 0.35f, 0.72f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawPath(wave, fill)
        fill.shader = null
        // A few still bubbles (fixed seeds, only under the surface).
        if (kind != Kind.UNKNOWN && fillH > u(24f)) {
            fill.color = ColorUtils.blendARGB(accent, Color.WHITE, 0.55f)
            val seeds = floatArrayOf(0.11f, 0.53f, 0.83f, 0.31f, 0.67f, 0.23f, 0.97f, 0.41f)
            val count = when (kind) {
                Kind.HOT -> 8
                Kind.CHARGING -> 7
                else -> 3
            }
            for (i in 0 until count) {
                val seed = seeds[i]
                val xF = frac(seed * 7.31f)
                val yF = frac(seed * 3.97f)
                val x = win.left + win.width() * xF
                val y = (win.bottom - u(3f)) - yF * (fillH - u(12f))
                if (y > surfaceY(x) + u(2f)) {
                    fill.alpha = 110
                    canvas.drawCircle(x, y, u(0.8f) + u(1.6f) * frac(seed * 3.7f), fill)
                }
            }
            fill.alpha = 255
        }
        // Bright meniscus line along the surface.
        val edge = Path()
        for (i in 0..steps) {
            val x = win.left + win.width() * (i / steps.toFloat())
            if (i == 0) edge.moveTo(x, surfaceY(x)) else edge.lineTo(x, surfaceY(x))
        }
        stroke.color = ColorUtils.blendARGB(accent, Color.WHITE, 0.65f)
        stroke.alpha = 235
        stroke.strokeWidth = u(2.3f)
        canvas.drawPath(edge, stroke)
        stroke.alpha = 255
        canvas.restore()
    }

    /** The still's flames: the stage's image at the live face's
     *  anchors, counts and sizes, frozen upright (no breathing). */
    private fun drawFlames(
        canvas: Canvas, res: Resources,
        part: DioramaArt.Part, flame: DioramaScene.Flame, body: RectF,
    ) {
        val level = when (flame) {
            DioramaScene.Flame.SMALL -> 1
            DioramaScene.Flame.MEDIUM -> 2
            DioramaScene.Flame.LARGE -> 3
            DioramaScene.Flame.NONE -> 0
        }
        val unit = body.width() * 0.26f
        for (i in 0 until FLAME_COUNTS[level]) {
            val a = FLAME_ANCHORS[i]
            val h = unit * a[2] * FLAME_SCALES[level]
            DioramaArt.drawCentered(
                canvas, res, part,
                body.left + body.width() * a[0],
                body.top + body.height() * a[1] - h / 2f,
                h * 0.62f, h,
            )
        }
    }

    /** The cooling objects' seats — the live face's anchors
     *  (BatteryFaceView.objectAnchor), including the v0.4.0
     *  air-conditioner / parasol swap. */
    private fun objectAnchor(obj: HeatScene.CoolObject): FloatArray = when (obj) {
        HeatScene.CoolObject.PARASOL -> floatArrayOf(1.19f, 0.06f)
        HeatScene.CoolObject.AIRCON -> floatArrayOf(-0.19f, 0.04f)
        HeatScene.CoolObject.WINDBELL -> floatArrayOf(-0.19f, 0.34f)
        HeatScene.CoolObject.FAN -> floatArrayOf(1.19f, 0.40f)
        HeatScene.CoolObject.ICE -> floatArrayOf(-0.19f, 0.68f)
        HeatScene.CoolObject.SHAVED_ICE -> floatArrayOf(1.19f, 0.72f)
        HeatScene.CoolObject.SNOWMAN -> floatArrayOf(-0.18f, 0.94f)
    }

    private fun frac(v: Float): Float = v - kotlin.math.floor(v)

    // The flame seats — the live face's (BatteryFaceView's
    // companion), duplicated for the still as before.
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
