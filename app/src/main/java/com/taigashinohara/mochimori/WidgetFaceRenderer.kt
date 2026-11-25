package com.taigashinohara.mochimori

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import kotlin.math.exp
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
 *
 * v0.4.1: the still follows the live face's minimal scene —
 * while [MINIMAL_SCENE] is on it paints ONLY the liquid and
 * the (regenerated, chroma-keyed) body photograph, over the
 * widget panel's own background. The full composition below
 * stays in place so the cast can return in stages; this
 * switch and BatteryFaceView.MINIMAL_SCENE must agree.
 */
object WidgetFaceRenderer {

    private enum class Kind { SAVING, HOT, CHARGING, UNKNOWN }

    /** Integrated battery + pedestal base with liquid, face, and charging bolt.
     *  Must stay aligned with BatteryFaceView.INTEGRATED_STAGE. */
    private const val INTEGRATED_STAGE = true

    /** Legacy v0.4.1 minimal scene (used only when INTEGRATED_STAGE is false). */
    private const val MINIMAL_SCENE = false

    // Virtual canvas the face is composed in; the bitmap is this space
    // scaled up. Square 160x160 matches widget_mochi.xml's 76dp x 76dp slot.
    private const val VW = 160f
    private const val VH = 160f

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
        // 76dp x 76dp on screen, rendered at 2x for crispness.
        val pxW = (76f * density * 2f).toInt().coerceAtLeast(152)
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
        val heat = scene?.liquidHeat
        if (heat != null && (kind == Kind.SAVING || kind == Kind.HOT)) {
            accent = HeatScene.liquidColor(heat)
        }

        val dio = DioramaScene.of(
            scene,
            charging = kind == Kind.CHARGING,
            saving = kind == Kind.SAVING,
            unknown = kind == Kind.UNKNOWN,
        )

        if (INTEGRATED_STAGE) {
            val spec = DioramaArt.stageSpec(dio.stageBase)
            val margin = 4f
            val bounds = DioramaArt.computeStageBounds(
                availLeft = margin,
                availTop = margin,
                availRight = VW - margin,
                availBottom = VH - margin,
                spec = spec,
                includePropHeadroom = DioramaArt.STAGE_PROPS_READY,
            )
            val stage = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
            val win = RectF()
            DioramaArt.computeWindowRect(stage, spec, win)
            val bodyW = stage.width() * spec.bodyWidthFrac
            val k = (bodyW / 156f).coerceAtLeast(0.32f)
            val u: (Float) -> Float = { v -> v * k }
            val darkTheme = (res.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

            if (DioramaArt.STAGE_PROPS_READY) {
                DioramaArt.drawStagePedestalShadow(canvas, stage, spec, darkTheme)
                drawStageProps(canvas, res, dio, scene, kind, stage, DioramaArt.StageLayer.BACK, darkTheme)
            }
            drawLiquid(canvas, kind, level, accent, win, spec.winRadiusRatio, u)
            DioramaArt.drawInto(canvas, res, spec.bodyPart, stage)
            if (kind == Kind.CHARGING) {
                val h = win.height() * 0.58f
                DioramaArt.drawCentered(
                    canvas, res, DioramaArt.Part.LIGHTNING,
                    win.centerX(), win.centerY(), h, h,
                )
            }
            DioramaArt.drawStageFace(canvas, res, stage, spec, DioramaArt.Part.EYES_OPEN, dio.mouth)
            if (DioramaArt.STAGE_PROPS_READY) {
                drawStageProps(canvas, res, dio, scene, kind, stage, DioramaArt.StageLayer.FRONT, darkTheme)
                if (dio.steam) {
                    DioramaArt.drawGrounded(
                        canvas, res, DioramaArt.Part.STEAM, stage, DioramaArt.STEAM_SEAT,
                        alpha = 220, darkTheme = darkTheme,
                    )
                }
            }
            return
        }

        // ---- Geometry (legacy separate body) ----
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

        if (MINIMAL_SCENE) {
            drawLiquid(canvas, kind, level, accent, win, 0.291f, u)
            DioramaArt.drawInto(canvas, res, DioramaArt.Part.BODY, body)
            return
        }

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
        drawLiquid(canvas, kind, level, accent, win, 0.291f, u)
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
        win: RectF, radiusRatio: Float, u: (Float) -> Float,
    ) {
        if (win.width() <= 0f || win.height() <= 0f) return
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val radius = win.width() * radiusRatio
        val clip = Path().apply { addRoundRect(win, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        // Subtle glass cavity cylindrical shading (visible above liquid too)
        fill.shader = LinearGradient(
            win.left, 0f, win.right, 0f,
            intArrayOf(
                Color.argb(38, 10, 24, 38),
                Color.argb(8, 10, 24, 38),
                Color.TRANSPARENT,
                Color.argb(10, 10, 24, 38),
                Color.argb(44, 10, 24, 38),
            ),
            floatArrayOf(0f, 0.14f, 0.50f, 0.86f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(win, fill)
        fill.shader = null

        val fillH = win.height() * (level / 100f)
        if (fillH > 0.5f) {
            val isWarm = Color.red(accent) > Color.blue(accent) + 25
            val lightTint = if (isWarm) Color.rgb(0xFF, 0xF2, 0xD8) else Color.rgb(0xE8, 0xF8, 0xFF)
            val baseAmp = when (kind) {
                Kind.HOT -> u(2.1f)
                Kind.CHARGING -> u(1.5f)
                Kind.SAVING -> u(1.1f)
                Kind.UNKNOWN -> 0f
            }
            val phase0 = 0.9f
            val kf1 = (2 * Math.PI / (win.width() * 0.9f)).toFloat()
            val kf2 = (2 * Math.PI / (win.width() * 0.45f)).toFloat()
            val baseY = win.bottom - fillH
            val depthH = win.width() * 0.065f
            val climbMax = u(2.0f)

            val steps = 28
            val xs = FloatArray(steps + 1)
            val fy = FloatArray(steps + 1)
            val by = FloatArray(steps + 1)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = win.left + win.width() * t
                val dx = x - win.left
                val climb = -climbMax * exp((-min(t, 1f - t) / 0.06f).toDouble()).toFloat()
                xs[i] = x
                fy[i] = baseY +
                    baseAmp * sin((dx * kf1 - phase0).toDouble()).toFloat() +
                    baseAmp * 0.45f * sin((dx * kf2 + phase0 * 0.63f + 1.7f).toDouble()).toFloat() + climb
                by[i] = baseY - depthH +
                    baseAmp * 0.80f * sin((dx * kf1 - phase0 + 1.55f).toDouble()).toFloat() +
                    baseAmp * 0.35f * sin((dx * kf2 + phase0 * 0.63f + 3.2f).toDouble()).toFloat() + climb * 0.75f
            }

            // Back liquid surface
            val backWave = Path().apply {
                moveTo(win.left - 2f, win.bottom + 2f)
                lineTo(win.left - 2f, by[0])
                for (i in 1..steps) lineTo(xs[i], by[i])
                lineTo(win.right + 2f, win.bottom + 2f)
                close()
            }
            fill.color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, Color.BLACK, 0.14f), 145)
            canvas.drawPath(backWave, fill)
            fill.alpha = 255

            // Front liquid body (vertical depth + cylinder curvature + caustic glow)
            val wave = Path().apply {
                moveTo(win.left - 2f, win.bottom + 2f)
                lineTo(win.left - 2f, fy[0])
                for (i in 1..steps) lineTo(xs[i], fy[i])
                lineTo(win.right + 2f, win.bottom + 2f)
                close()
            }
            fill.shader = LinearGradient(
                0f, win.top, 0f, win.bottom,
                intArrayOf(
                    ColorUtils.blendARGB(accent, lightTint, 0.42f),
                    ColorUtils.blendARGB(accent, lightTint, 0.14f),
                    accent,
                    ColorUtils.blendARGB(accent, Color.BLACK, 0.34f),
                ),
                floatArrayOf(0f, 0.32f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(wave, fill)
            fill.shader = LinearGradient(
                win.left, 0f, win.right, 0f,
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
            canvas.drawPath(wave, fill)
            fill.shader = RadialGradient(
                win.left + win.width() * 0.48f, win.top + win.height() * 0.78f,
                maxOf(win.width() * 0.56f, 1f),
                intArrayOf(
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, lightTint, 0.72f), 85),
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, lightTint, 0.40f), 32),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(wave, fill)
            fill.shader = null

            // 3D Meniscus top surface ribbon
            val ribbon = Path().apply {
                moveTo(xs[0], by[0])
                for (i in 1..steps) lineTo(xs[i], by[i])
                for (i in steps downTo 0) lineTo(xs[i], fy[i])
                close()
            }
            fill.color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, lightTint, 0.56f), 110)
            canvas.drawPath(ribbon, fill)
            fill.alpha = 255

            // 3D rim-lit specular bubbles
            if (kind != Kind.UNKNOWN && fillH > u(18f)) {
                val fillCol = ColorUtils.blendARGB(accent, Color.WHITE, 0.45f)
                val rimCol = ColorUtils.blendARGB(accent, Color.WHITE, 0.86f)
                stroke.strokeWidth = maxOf(u(0.7f), 0.7f)
                val seeds = floatArrayOf(0.11f, 0.53f, 0.83f, 0.31f, 0.67f, 0.23f, 0.97f, 0.41f, 0.19f, 0.73f, 0.59f, 0.37f)
                val count = when (kind) {
                    Kind.HOT -> 12
                    Kind.CHARGING -> 8
                    else -> 4
                }
                for (i in 0 until count) {
                    val seed = seeds[i]
                    val xF = 0.08f + 0.84f * frac(seed * 7.31f + i * 0.3819f)
                    val yF = frac(seed * 3.97f + i * 0.618f)
                    val x = win.left + win.width() * xF
                    val idx = (xF * steps).toInt().coerceIn(0, steps)
                    val y = (win.bottom - u(4f)) - yF * (fillH - u(10f))
                    val r = u(0.9f + 1.4f * frac(seed * 3.7f))
                    if (y > fy[idx] + r + u(2f)) {
                        fill.color = fillCol
                        fill.alpha = 55
                        canvas.drawCircle(x, y, r, fill)
                        stroke.color = rimCol
                        stroke.alpha = 155
                        canvas.drawCircle(x, y, r, stroke)
                        fill.color = Color.WHITE
                        fill.alpha = 215
                        canvas.drawCircle(x - r * 0.32f, y - r * 0.32f, maxOf(0.5f, r * 0.30f), fill)
                    }
                }
                fill.alpha = 255
                stroke.alpha = 255
            }

            // Crisp bright front meniscus line
            val edge = Path().apply {
                moveTo(xs[0], fy[0])
                for (i in 1..steps) lineTo(xs[i], fy[i])
            }
            stroke.color = ColorUtils.blendARGB(accent, Color.WHITE, 0.80f)
            stroke.alpha = 235
            stroke.strokeWidth = u(2.0f)
            canvas.drawPath(edge, stroke)
            stroke.alpha = 255
        }

        // Front glass tube vertical highlights
        val glX = win.left + win.width() * 0.10f
        val glW = win.width() * 0.078f
        fill.shader = LinearGradient(
            0f, win.top, 0f, win.bottom,
            intArrayOf(Color.TRANSPARENT, Color.argb(84, 255, 255, 255), Color.argb(52, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0.05f, 0.18f, 0.80f, 0.94f), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(glX, win.top + win.height() * 0.05f, glX + glW, win.bottom - win.height() * 0.07f),
            glW * 0.5f, glW * 0.5f, fill,
        )
        val grX = win.right - win.width() * 0.12f
        val grW = win.width() * 0.034f
        fill.shader = LinearGradient(
            0f, win.top, 0f, win.bottom,
            intArrayOf(Color.TRANSPARENT, Color.argb(46, 255, 255, 255), Color.argb(28, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0.08f, 0.24f, 0.78f, 0.92f), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(grX, win.top + win.height() * 0.09f, grX + grW, win.bottom - win.height() * 0.09f),
            grW * 0.5f, grW * 0.5f, fill,
        )
        fill.shader = null
        canvas.restore()
    }

    /** Still-frame integrated-stage props matching BatteryFaceView's exact layer & seat specs. */
    private fun drawStageProps(
        canvas: Canvas,
        res: Resources,
        dio: DioramaScene.Decision,
        scene: HeatScene.Scene?,
        kind: Kind,
        stage: RectF,
        layer: DioramaArt.StageLayer,
        darkTheme: Boolean,
    ) {
        if (kind == Kind.UNKNOWN) return
        val flameLevel = DioramaArt.flameStageLevel(dio.flame)
        if (flameLevel > 0) {
            val flamePart = DioramaArt.flamePart(dio.flame)
            val levelScale = DioramaArt.flameStageScale(dio.flame)
            if (flamePart != null) {
                for (f in DioramaArt.STAGE_FLAME_SEATS) {
                    if (f.seat.layer != layer || flameLevel < f.minLevel) continue
                    DioramaArt.drawGrounded(
                        canvas, res, flamePart, stage, f.seat,
                        scale = levelScale * f.stageScale,
                        darkTheme = darkTheme,
                    )
                }
            }
        }
        if (scene != null && scene.objects.isNotEmpty()) {
            for (obj in HeatScene.CoolObject.values()) {
                if (obj !in scene.objects) continue
                val seat = DioramaArt.COOL_PROP_SEATS[obj] ?: continue
                if (seat.layer != layer) continue
                DioramaArt.drawGrounded(
                    canvas, res, DioramaArt.coolObjectPart(obj),
                    stage, seat, darkTheme = darkTheme,
                )
            }
        }
        if (layer == DioramaArt.StageLayer.FRONT && dio.decorations) {
            for ((part, seat) in DioramaArt.DECOR_SEATS) {
                DioramaArt.drawGrounded(
                    canvas, res, part, stage, seat, darkTheme = darkTheme,
                )
            }
        }
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
